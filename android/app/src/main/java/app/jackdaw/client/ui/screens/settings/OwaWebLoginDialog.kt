package app.jackdaw.client.ui.screens.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
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
import app.jackdaw.client.core.model.AccountProtocol
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.data.auth.OwaAuthManager
import java.net.URL

import androidx.compose.material.icons.rounded.Key
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OwaWebLoginDialog(
    initialOwaUrl: String,
    initialEmail: String = "",
    autoLoginUser: String = "",
    autoLoginPassword: String = "",
    existingAccountId: String? = null,
    onDismissRequest: () -> Unit,
    onAccountAuthorized: (MailAccount) -> Unit
) {
    val context = LocalContext.current
    val normalizedUrl = remember(initialOwaUrl) {
        OwaAuthManager.normalizeOwaUrl(initialOwaUrl)
    }

    var currentUrl by remember { mutableStateOf(normalizedUrl) }
    var pageTitle by remember { mutableStateOf("Вход в Outlook Web App") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var sessionDetected by remember { mutableStateOf(false) }
    var capturedCookies by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun injectCredentials(webView: WebView?) {
        val userVal = autoLoginUser.ifBlank { initialEmail }.trim()
        val passVal = autoLoginPassword
        if (userVal.isBlank() && passVal.isBlank()) return

        val escapedUser = JSONObject.quote(userVal)
        val escapedPass = JSONObject.quote(passVal)

        val js = """
            (function() {
                function setInputValue(sel, val) {
                    if (!val) return false;
                    var elements = document.querySelectorAll(sel);
                    var filled = false;
                    for (var i = 0; i < elements.length; i++) {
                        var el = elements[i];
                        if (el && (!el.value || el.value === '')) {
                            el.focus();
                            el.value = val;
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                            filled = true;
                        }
                    }
                    return filled;
                }
                setInputValue('input#username, input#userNameInput, input#loginfmt, input#email, input[name="username"], input[name="UserName"], input[name="login"], input[name="loginfmt"], input[type="email"]', $escapedUser);
                setInputValue('input#password, input#passwordInput, input#passwd, input[name="password"], input[name="Password"], input[name="passwd"], input[type="password"]', $escapedPass);
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    fun completeAuthorization() {
        val email = if (initialEmail.isNotBlank()) {
            initialEmail.trim()
        } else {
            val host = runCatching { URL(currentUrl).host }.getOrDefault("corp.mail")
            "user@$host"
        }
        val canary = OwaAuthManager.extractCanary(capturedCookies) ?: "canary_web_${System.currentTimeMillis()}"

        val account = MailAccount(
            id = existingAccountId ?: "acc_owa_${System.currentTimeMillis()}",
            email = email,
            displayName = email.substringBefore("@"),
            protocol = AccountProtocol.EXCHANGE_OWA,
            isDefault = false,
            avatarColorHex = 0xFFF59E0BL,
            serverHost = normalizedUrl,
            authSessionToken = canary,
            authSessionCookies = capturedCookies,
            loginUser = autoLoginUser,
            savedPassword = autoLoginPassword
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
                            IconButton(onClick = { injectCredentials(webViewRef) }) {
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
                            text = "Сессия OWA обнаружена! Нажмите «Завершить» для добавления.",
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
                                        injectCredentials(view)
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
