package app.jackdaw.client.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.model.AccountProtocol
import app.jackdaw.client.core.model.DeliveryStatus
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.FolderType
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.model.SlaInfo
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.data.auth.OwaAuthManager
import app.jackdaw.client.data.local.JackdawDatabase
import app.jackdaw.client.data.local.entity.EmailEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL

private const val TAG = "OwaWebLoginDialog"

class JackdawBridge(
    private val onCanaryFound: (String) -> Unit,
    private val onSessionActive: () -> Unit,
    private val onItemsExtracted: (String) -> Unit,
    private val onCredentialsCaptured: (String, String) -> Unit = { _, _ -> }
) {
    @JavascriptInterface
    fun postCanary(canary: String) {
        if (canary.isNotBlank()) onCanaryFound(canary)
    }

    @JavascriptInterface
    fun postSession() {
        onSessionActive()
    }

    @JavascriptInterface
    fun postItems(json: String) {
        if (json.isNotBlank()) onItemsExtracted(json)
    }

    @JavascriptInterface
    fun postCredentials(user: String, pass: String) {
        if (pass.isNotBlank()) onCredentialsCaptured(user, pass)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OwaWebLoginDialog(
    initialOwaUrl: String,
    initialEmail: String = "",
    initialDisplayName: String = "",
    autoLoginUser: String = "",
    autoLoginPassword: String = "",
    existingAccountId: String? = null,
    onDismissRequest: () -> Unit,
    onAccountAuthorized: (MailAccount) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generatedAccountId = remember { "acc_owa_${System.currentTimeMillis()}" }
    val effectiveAccountId = existingAccountId ?: generatedAccountId

    val normalizedUrl = remember(initialOwaUrl) {
        OwaAuthManager.normalizeOwaUrl(initialOwaUrl)
    }

    var currentUrl by remember { mutableStateOf(normalizedUrl) }
    var pageTitle by remember { mutableStateOf("Вход в Outlook Web App") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var sessionDetected by remember { mutableStateOf(false) }
    var capturedCookies by remember { mutableStateOf("") }
    var capturedCanary by remember { mutableStateOf("") }
    var capturedUser by remember { mutableStateOf(autoLoginUser) }
    var capturedPass by remember { mutableStateOf(autoLoginPassword) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var extractedCount by remember { mutableStateOf(0) }

    fun injectAutofillScript(webView: WebView?) {
        val userVal = autoLoginUser.ifBlank { initialEmail }.trim()
        val passVal = autoLoginPassword
        if (userVal.isBlank() && passVal.isBlank()) return

        val escapedUser = JSONObject.quote(userVal)
        val escapedPass = JSONObject.quote(passVal)

        val js = """
            (function() {
                var userVal = $escapedUser;
                var passVal = $escapedPass;

                function setNativeValue(el, val) {
                    if (!el || !val) return false;
                    try {
                        el.focus();
                        var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                        if (setter && setter.set) {
                            setter.set.call(el, val);
                        } else {
                            el.value = val;
                        }
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
                        el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                        return true;
                    } catch(e) {
                        try { el.value = val; return true; } catch(err) { return false; }
                    }
                }

                function doAutofill() {
                    if (userVal) {
                        var userSelectors = [
                            'input#username', 'input#userNameInput', 'input#loginfmt', 'input#email',
                            'input#cred_userid_inputtext', 'input#user', 'input[name="username" i]',
                            'input[name="UserName" i]', 'input[name="login" i]', 'input[name="loginfmt" i]',
                            'input[name="user" i]', 'input[name="email" i]', 'input[autocomplete="username"]',
                            'input[autocomplete="email"]', 'input[type="email"]', 'input[type="text"]:not([readonly])'
                        ];
                        for (var i = 0; i < userSelectors.length; i++) {
                            var el = document.querySelector(userSelectors[i]);
                            if (el && el.offsetWidth > 0 && el.offsetHeight > 0 && !el.disabled && el.type !== 'password' && el.type !== 'hidden') {
                                if (el.value !== userVal) {
                                    setNativeValue(el, userVal);
                                }
                                break;
                            }
                        }
                    }

                    if (passVal) {
                        var passSelectors = [
                            'input[type="password"]', 'input#password', 'input#passwordInput',
                            'input#passwd', 'input#i0118', 'input#cred_password_inputtext',
                            'input#pass', 'input[name="password" i]', 'input[name="Password" i]',
                            'input[name="passwd" i]', 'input[name="pass" i]', 'input[name="pword" i]',
                            'input[autocomplete="current-password"]', 'input[autocomplete="password"]'
                        ];
                        for (var j = 0; j < passSelectors.length; j++) {
                            var pel = document.querySelector(passSelectors[j]);
                            if (pel && pel.offsetWidth > 0 && pel.offsetHeight > 0 && !pel.disabled) {
                                if (pel.value !== passVal) {
                                    setNativeValue(pel, passVal);
                                }
                                break;
                            }
                        }
                    }
                }

                function captureCredentials() {
                    try {
                        var pInput = document.querySelector('input[type="password"]');
                        var uInput = document.querySelector('input#username, input#userNameInput, input#loginfmt, input#email, input#user, input[name="username" i], input[name="UserName" i], input[type="email"], input[type="text"]');
                        if (pInput && pInput.value) {
                            var u = (uInput && uInput.value) ? uInput.value : '';
                            if (window.JackdawBridge && window.JackdawBridge.postCredentials) {
                                window.JackdawBridge.postCredentials(u, pInput.value);
                            }
                        }
                    } catch(e) {}
                }
                document.addEventListener('submit', captureCredentials, true);
                var forms = document.querySelectorAll('form');
                for (var f = 0; f < forms.length; f++) {
                    forms[f].addEventListener('submit', captureCredentials, true);
                }
                var buttons = document.querySelectorAll('button, input[type="submit"], [role="button"]');
                for (var b = 0; b < buttons.length; b++) {
                    buttons[b].addEventListener('click', captureCredentials, true);
                }
                var pField = document.querySelector('input[type="password"]');
                if (pField) {
                    pField.addEventListener('blur', captureCredentials, true);
                    pField.addEventListener('change', captureCredentials, true);
                }

                doAutofill();

                if (!window.__jackdaw_fill_timer) {
                    var attempts = 0;
                    window.__jackdaw_fill_timer = setInterval(function() {
                        doAutofill();
                        attempts++;
                        if (attempts > 30) {
                            clearInterval(window.__jackdaw_fill_timer);
                            window.__jackdaw_fill_timer = null;
                        }
                    }, 400);

                    try {
                        var obs = new MutationObserver(function() {
                            doAutofill();
                        });
                        obs.observe(document.documentElement || document.body, { childList: true, subtree: true });
                    } catch(e){}
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    fun injectExtractionScript(webView: WebView?) {
        val js = """
            (function() {
                var c = '';
                try { if (window.g_canary) c = window.g_canary; } catch(e){}
                if (!c) {
                    try { if (window.UserContext && window.UserContext.Canary) c = window.UserContext.Canary; } catch(e){}
                }
                if (!c) {
                    var m = document.cookie.match(/X-OWA-CANARY=([^;]+)/);
                    if (m) c = m[1];
                }
                if (c && window.JackdawBridge) {
                    window.JackdawBridge.postCanary(c);
                }

                var isAuthed = (c.length > 0) || (document.cookie.indexOf('cadata') !== -1) || (document.cookie.indexOf('sessionid') !== -1);
                if (isAuthed && window.JackdawBridge) {
                    window.JackdawBridge.postSession();

                    var reqPayload = {
                        "__type": "FindItemJsonRequest:#Exchange",
                        "Header": { "__type": "JsonRequestHeaders:#Exchange", "RequestServerVersion": "Exchange2013" },
                        "Body": {
                            "__type": "FindItemRequest:#Exchange",
                            "ItemShape": {
                                "__type": "ItemResponseShape:#Exchange",
                                "BaseShape": "IdOnly",
                                "AdditionalProperties": [
                                    { "__type": "PropertyUri:#Exchange", "FieldURI": "item:Subject" },
                                    { "__type": "PropertyUri:#Exchange", "FieldURI": "message:IsRead" },
                                    { "__type": "PropertyUri:#Exchange", "FieldURI": "item:DateTimeReceived" },
                                    { "__type": "PropertyUri:#Exchange", "FieldURI": "item:HasAttachments" },
                                    { "__type": "PropertyUri:#Exchange", "FieldURI": "item:Importance" }
                                ]
                            },
                            "ParentFolderIds": [{ "__type": "DistinguishedFolderId:#Exchange", "Id": "inbox" }],
                            "Traversal": "Shallow",
                            "Paging": { "__type": "IndexedPageView:#Exchange", "BasePoint": "Beginning", "Offset": 0, "MaxEntriesReturned": 50 }
                        }
                    };

                    var endpoints = ['/owa/service.svc?action=FindItem&EP=1', '/owa/service.svc?action=FindItem', '/service.svc?action=FindItem&EP=1'];
                    function tryFetch(i) {
                        if (i >= endpoints.length) {
                            scrapeDom();
                            return;
                        }
                        fetch(endpoints[i], {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json; charset=utf-8', 'Action': 'FindItem', 'X-OWA-CANARY': c },
                            body: JSON.stringify(reqPayload)
                        })
                        .then(function(r) { return r.json(); })
                        .then(function(json) {
                            if (json && window.JackdawBridge) {
                                window.JackdawBridge.postItems(JSON.stringify(json));
                            }
                        })
                        .catch(function() {
                            tryFetch(i + 1);
                        });
                    }
                    tryFetch(0);
                } else {
                    scrapeDom();
                }

                function scrapeDom() {
                    var items = [];
                    var rows = document.querySelectorAll('div[role="option"], div[role="listitem"], tr.Item, div.Item, div[aria-label*="@"], div[data-convid]');
                    for (var idx = 0; idx < Math.min(rows.length, 30); idx++) {
                        var el = rows[idx];
                        var txt = el.innerText || '';
                        if (!txt || txt.length < 5) continue;
                        var subEl = el.querySelector('[title], span[id*="subject"], div[id*="subject"], b, strong');
                        var sub = subEl ? (subEl.getAttribute('title') || subEl.innerText) : '';
                        var fromEl = el.querySelector('span[id*="from"], span[id*="sender"], span[title*="@"], span.persona');
                        var sender = fromEl ? (fromEl.getAttribute('title') || fromEl.innerText) : '';
                        var isUnread = el.classList.contains('unread') || el.querySelector('.unread, [aria-label*="unread" i], [aria-label*="непрочит" i]') !== null;
                        if (sub || sender) {
                            items.push({
                                subject: sub || 'Без темы',
                                sender: sender || 'Коллега',
                                text: txt.replace(/\\s+/g, ' ').substring(0, 150),
                                isUnread: isUnread
                            });
                        }
                    }
                    if (items.length > 0 && window.JackdawBridge) {
                        window.JackdawBridge.postItems(JSON.stringify({ domScraped: items }));
                    }
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    fun saveExtractedJson(jsonString: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject(jsonString)
                val db = JackdawDatabase.getInstance(context)
                val targetFolderId = "${effectiveAccountId}_inbox"
                val emailEntities = mutableListOf<EmailEntity>()

                if (root.has("domScraped")) {
                    val array = root.getJSONArray("domScraped")
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val subject = item.optString("subject", "(Без темы)")
                        val sender = item.optString("sender", "Коллега")
                        val text = item.optString("text", "")
                        val isUnread = item.optBoolean("isUnread", false)
                        val timestamp = System.currentTimeMillis() - (i * 1800000L)
                        val id = "dom_${effectiveAccountId}_${subject.hashCode()}_${i}"

                        val email = EmailMessage(
                            id = id,
                            accountId = effectiveAccountId,
                            folderId = targetFolderId,
                            threadId = "th_${id.takeLast(10)}",
                            senderName = sender,
                            senderEmail = if (sender.contains("@")) sender else "$sender@corp.mail",
                            toRecipients = listOf(initialEmail.ifBlank { "me@corp.mail" }),
                            subject = subject,
                            snippet = text.take(150),
                            bodyText = text,
                            timestamp = timestamp,
                            isRead = !isUnread,
                            isStarred = false,
                            hasAttachments = false,
                            slaInfo = SlaInfo(
                                severity = if (isUnread) SlaSeverity.NORMAL else SlaSeverity.COMPLETED,
                                deadlineTimestamp = timestamp + (30 * 60 * 1000L),
                                remainingLabel = if (isUnread) "30 мин" else "Выполнено"
                            ),
                            deliveryStatus = DeliveryStatus.SENT
                        )
                        emailEntities.add(EmailEntity.fromDomain(email))
                    }
                } else {
                    val itemsArray = extractFindItemArray(root)
                    if (itemsArray != null) {
                        for (i in 0 until itemsArray.length()) {
                            val itemObj = itemsArray.optJSONObject(i) ?: continue
                            val itemId = itemObj.optJSONObject("ItemId")?.optString("Id") ?: "msg_${System.currentTimeMillis()}_$i"
                            val subject = itemObj.optString("Subject", "(Без темы)")
                            val fromObj = itemObj.optJSONObject("From")?.optJSONObject("Mailbox")
                            val senderName = fromObj?.optString("Name", "").orEmpty()
                            val senderEmail = fromObj?.optString("EmailAddress", "").orEmpty()
                            val isRead = itemObj.optBoolean("IsRead", false)
                            val hasAtt = itemObj.optBoolean("HasAttachments", false)
                            val dateStr = itemObj.optString("DateTimeReceived", "")
                            val timestamp = runCatching { java.time.Instant.parse(dateStr).toEpochMilli() }.getOrDefault(System.currentTimeMillis())

                            val email = EmailMessage(
                                id = itemId,
                                accountId = effectiveAccountId,
                                folderId = targetFolderId,
                                threadId = "th_${itemId.takeLast(10)}",
                                senderName = senderName.ifBlank { senderEmail.substringBefore("@").ifBlank { "Коллега" } },
                                senderEmail = senderEmail.ifBlank { "unknown@corp.mail" },
                                toRecipients = listOf(initialEmail.ifBlank { "me@corp.mail" }),
                                subject = subject,
                                snippet = subject,
                                bodyText = subject,
                                timestamp = timestamp,
                                isRead = isRead,
                                isStarred = false,
                                hasAttachments = hasAtt,
                                slaInfo = SlaInfo(
                                    severity = if (!isRead) SlaSeverity.NORMAL else SlaSeverity.COMPLETED,
                                    deadlineTimestamp = timestamp + (30 * 60 * 1000L),
                                    remainingLabel = if (!isRead) "30 мин" else "Выполнено"
                                ),
                                deliveryStatus = DeliveryStatus.SENT
                            )
                            emailEntities.add(EmailEntity.fromDomain(email))
                        }
                    }
                }

                if (emailEntities.isNotEmpty()) {
                    db.emailDao().insertEmails(emailEntities)
                    val unread = db.emailDao().getFolderUnreadCount(targetFolderId)
                    val total = db.emailDao().getFolderTotalCount(targetFolderId)
                    db.folderDao().updateCounts(targetFolderId, unread, total)
                    extractedCount = emailEntities.size
                    Log.d(TAG, "Successfully extracted and saved ${emailEntities.size} emails to $targetFolderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving extracted items: ${e.message}", e)
            }
        }
    }

    fun completeAuthorization() {
        val cm = CookieManager.getInstance()
        cm.flush()

        val cookieList = mutableListOf<String>()
        capturedCookies.takeIf { it.isNotBlank() }?.let { cookieList.add(it) }
        cm.getCookie(currentUrl)?.let { if (it.isNotBlank()) cookieList.add(it) }
        cm.getCookie(normalizedUrl)?.let { if (it.isNotBlank()) cookieList.add(it) }
        runCatching { URL(currentUrl).host }.getOrNull()?.let { host ->
            cm.getCookie("https://$host/owa/")?.let { if (it.isNotBlank()) cookieList.add(it) }
            cm.getCookie("https://$host/")?.let { if (it.isNotBlank()) cookieList.add(it) }
        }

        val cookieMap = mutableMapOf<String, String>()
        for (str in cookieList) {
            for (p in str.split(";")) {
                val kv = p.split("=", limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].trim()
                    val v = kv[1].trim()
                    if (k.isNotBlank()) cookieMap[k] = v
                }
            }
        }
        val finalCookies = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val email = if (initialEmail.isNotBlank()) {
            initialEmail.trim()
        } else {
            val host = runCatching { URL(currentUrl).host }.getOrDefault("corp.mail")
            val userPart = autoLoginUser.substringBefore("@").ifBlank { "user" }
            "$userPart@$host"
        }

        val finalCanary = capturedCanary.ifBlank {
            OwaAuthManager.extractCanary(finalCookies).orEmpty()
        }

        val finalDisplayName = initialDisplayName.trim().ifBlank {
            if (email.isNotBlank()) email.substringBefore("@") else "Почта"
        }

        val account = MailAccount(
            id = effectiveAccountId,
            email = email,
            displayName = finalDisplayName,
            protocol = AccountProtocol.EXCHANGE_OWA,
            isDefault = false,
            avatarColorHex = 0xFFF59E0BL,
            serverHost = normalizedUrl,
            authSessionToken = finalCanary,
            authSessionCookies = finalCookies,
            loginUser = capturedUser.ifBlank { autoLoginUser }.trim(),
            savedPassword = capturedPass.ifBlank { autoLoginPassword }
        )
        onAccountAuthorized(account)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top AppBar
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = JackdawAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Авторизация OWA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Закрыть",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (autoLoginUser.isNotBlank() || autoLoginPassword.isNotBlank()) {
                            IconButton(onClick = { injectAutofillScript(webViewRef) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = "Автозаполнение",
                                    tint = JackdawAmber
                                )
                            }
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Обновить",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { completeAuthorization() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sessionDetected) JackdawAmber else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (sessionDetected) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Завершить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Loading Bar
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadProgress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = JackdawAmber,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Success notification banner
                AnimatedVisibility(
                    visible = sessionDetected,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(JackdawAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = JackdawAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (extractedCount > 0)
                                "Сессия активна! Загружено писем: $extractedCount. Нажмите «Завершить»."
                            else
                                "Сессия OWA обнаружена! Нажмите «Завершить» для сохранения.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                addJavascriptInterface(
                                    JackdawBridge(
                                        onCanaryFound = { canary ->
                                            capturedCanary = canary
                                        },
                                        onSessionActive = {
                                            sessionDetected = true
                                        },
                                        onItemsExtracted = { json ->
                                            sessionDetected = true
                                            saveExtractedJson(json)
                                        },
                                        onCredentialsCaptured = { user, pass ->
                                            if (user.isNotBlank()) capturedUser = user
                                            if (pass.isNotBlank()) capturedPass = pass
                                        }
                                    ),
                                    "JackdawBridge"
                                )

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress / 100f
                                        isLoading = newProgress < 100
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            pageTitle = title
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: SslErrorHandler?,
                                        error: SslError?
                                    ) {
                                        // Allow corporate internal CA and self-signed certificates
                                        handler?.proceed()
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        url?.let {
                                            currentUrl = it
                                            val cookies = OwaAuthManager.getCookiesFromManager(it)
                                            capturedCookies = cookies
                                            if (OwaAuthManager.isSessionAuthenticated(cookies, it)) {
                                                sessionDetected = true
                                            }
                                        }
                                        injectAutofillScript(view)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        url?.let {
                                            currentUrl = it
                                            val cookies = OwaAuthManager.getCookiesFromManager(it)
                                            capturedCookies = cookies
                                            if (OwaAuthManager.isSessionAuthenticated(cookies, it)) {
                                                sessionDetected = true
                                            }
                                        }
                                        injectAutofillScript(view)
                                        injectExtractionScript(view)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val reqUrl = request?.url?.toString()
                                        reqUrl?.let {
                                            currentUrl = it
                                            val cookies = OwaAuthManager.getCookiesFromManager(it)
                                            capturedCookies = cookies
                                            if (OwaAuthManager.isSessionAuthenticated(cookies, it)) {
                                                sessionDetected = true
                                            }
                                        }
                                        return false
                                    }
                                }

                                webViewRef = this
                                loadUrl(normalizedUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef = null
        }
    }
}

private fun extractFindItemArray(response: JSONObject): org.json.JSONArray? {
    val body = response.optJSONObject("Body") ?: response
    val responseMessages = body.optJSONObject("ResponseMessages")
    if (responseMessages != null) {
        val items = responseMessages.optJSONArray("Items")
        if (items != null && items.length() > 0) {
            val first = items.optJSONObject(0)
            val rootFolder = first?.optJSONObject("RootFolder")
            return rootFolder?.optJSONArray("Items") ?: first?.optJSONArray("Items")
        }
    }
    return body.optJSONArray("Items")
}
