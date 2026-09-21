package app.jackdaw.client.ui.screens.maildetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.viewinterop.AndroidView
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.designsystem.theme.SlaGoodContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaGoodContainerLight
import app.jackdaw.client.core.designsystem.theme.SlaGoodGreen
import app.jackdaw.client.core.designsystem.theme.SlaUrgentContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaUrgentContainerLight
import app.jackdaw.client.core.designsystem.theme.SlaUrgentRed
import app.jackdaw.client.core.designsystem.theme.SlaWarningAmber
import app.jackdaw.client.core.designsystem.theme.SlaWarningContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaWarningContainerLight
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.ui.components.SlaBadge
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.jackdaw.client.core.model.Attachment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SlaItemVisuals(
    val bg: Color,
    val textColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDetailScreen(
    email: EmailMessage,
    threadEmails: List<EmailMessage> = emptyList(),
    onBack: () -> Unit,
    onReply: (EmailMessage) -> Unit,
    onDelete: (EmailMessage) -> Unit,
    onArchive: (EmailMessage) -> Unit,
    onToggleStar: (Boolean) -> Unit,
    onToggleRead: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("ru"))
    val formattedDate = dateFormat.format(Date(email.timestamp))
    val readStatusManager = remember { app.jackdaw.client.core.readstatus.ReadStatusManager.getInstance(context) }
    androidx.compose.runtime.LaunchedEffect(email.id) {
        if (!email.isRead && readStatusManager.mode == app.jackdaw.client.core.readstatus.MarkAsReadMode.AFTER_DELAY) {
            kotlinx.coroutines.delay(readStatusManager.delaySeconds * 1000L)
            onToggleRead(true)
        }
    }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleRead(!email.isRead) }) {
                        Icon(
                            imageVector = if (email.isRead) Icons.Rounded.MarkEmailUnread else Icons.Rounded.MarkEmailRead,
                            contentDescription = if (email.isRead) "Отметить как непрочитанное" else "Отметить как прочитанное",
                            tint = if (email.isRead) MaterialTheme.colorScheme.onSurface else JackdawAmber
                        )
                    }
                    IconButton(onClick = { onToggleStar(!email.isStarred) }) {
                        Icon(
                            imageVector = if (email.isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Избранное",
                            tint = if (email.isStarred) JackdawAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { onArchive(email) }) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = "В архив",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (email.slaInfo == null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Главная подсвеченная кнопка «Ответить всем»
                    Button(
                        onClick = { onReply(email) },
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JackdawAmber,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ReplyAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ответить всем",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Второстепенная кнопка «Ответить» (только отправителю)
                    OutlinedButton(
                        onClick = { onReply(email) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ответить", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Email Subject
            Text(
                text = email.subject,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sender Information Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(JackdawAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = email.senderName.firstOrNull()?.uppercase() ?: "J",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = email.senderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "<${email.senderEmail}>",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "кому: ${email.toRecipients.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // Email Body
            val isSystemDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            var forceLightMode by remember(email.id) { mutableStateOf(false) }
            val effectiveDark = isSystemDark && !forceLightMode
            val hasHtml = !email.bodyHtml.isNullOrBlank()

            if (hasHtml) {
                // Reading mode pill to switch between light and dark backgrounds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { forceLightMode = !forceLightMode },
                        shape = RoundedCornerShape(8.dp),
                        color = if (forceLightMode) JackdawAmber.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (forceLightMode) JackdawAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (forceLightMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                contentDescription = null,
                                tint = if (forceLightMode) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (forceLightMode) "Тёмный фон" else "Светлый фон",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (forceLightMode) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // In WebView CSS pixels match dp when viewport is device-width.
                // We add buffer padding so footer and signature are never clipped.
                var webViewHeightPx by remember(email.id, effectiveDark) { mutableIntStateOf(500) }
                val webViewHeightDp = (webViewHeightPx + 48).coerceAtLeast(150).dp

                val bgColor = if (effectiveDark) "#1C1B1F" else "#FFFFFF"
                val textColor = if (effectiveDark) "#E6E1E5" else "#1C1B1F"
                val linkColor = if (effectiveDark) "#D4A017" else "#8B6914"

                val htmlDoc = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                      * { box-sizing: border-box; -webkit-text-size-adjust: 100%%; }
                      html, body {
                        margin: 0; padding: 4px 0;
                        background: $bgColor;
                        color: $textColor;
                        font-family: -apple-system, Roboto, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
                        font-size: 15px;
                        line-height: 1.55;
                        word-break: normal;
                      }
                      a { color: $linkColor; word-break: break-all; }
                      img { max-width: 100%% !important; height: auto !important; }
                      /* Hide broken CID attachments */
                      img[src^="cid:"], img[src=""] { display: none !important; }
                      pre, code { white-space: pre-wrap; word-break: break-all; }
                      table { max-width: 100%% !important; }
                      p { margin: 0 0 8px 0; }
                      blockquote { margin: 8px 0; padding-left: 10px; border-left: 3px solid ${if (effectiveDark) "#444" else "#ccc"}; color: ${if (effectiveDark) "#aaa" else "#666"}; }
                      hr { border: none; border-top: 1px solid ${if (effectiveDark) "#333" else "#ddd"}; margin: 12px 0; }
                    </style>
                    </head>
                    <body>${email.bodyHtml}</body>
                    </html>
                """.trimIndent()

                val viewTag = "${email.id}_${effectiveDark}"
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            tag = viewTag
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    view.evaluateJavascript(
                                        "(function(){ return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, document.body.offsetHeight); })()"
                                    ) { result ->
                                        val px = result?.trim('"')?.toIntOrNull() ?: 0
                                        if (px > 0) webViewHeightPx = px
                                    }
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_NO_CACHE
                            }
                            isScrollContainer = false
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            setBackgroundColor(if (forceLightMode) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT)
                            loadDataWithBaseURL(null, htmlDoc, "text/html", "UTF-8", null)
                        }
                    },
                    update = { view ->
                        if (view.tag != viewTag) {
                            view.tag = viewTag
                            webViewHeightPx = 500
                            view.setBackgroundColor(if (forceLightMode) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT)
                            view.loadDataWithBaseURL(null, htmlDoc, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (forceLightMode) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                            } else Modifier
                        )
                        .height(webViewHeightDp)
                )
            } else {
                // Fallback: plain text
                val displayText = when {
                    email.bodyText.isNotBlank() && email.bodyText != email.subject -> email.bodyText
                    email.snippet.isNotBlank() -> email.snippet
                    else -> "(Письмо не содержит текста)"
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            }

            // SLA indicator below email body - compact single row showing remaining time to answer (capped at 30 min)
            val isIncoming = email.folderId.contains("inbox", ignoreCase = true)
            val sla = email.slaInfo
            if (isIncoming && sla != null && sla.severity != SlaSeverity.NONE) {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                val visuals = if (sla.severity == SlaSeverity.COMPLETED) {
                    val isLate = sla.remainingLabel.contains("опоздан", ignoreCase = true)
                    if (isLate) {
                        SlaItemVisuals(
                            if (isDark) SlaWarningContainerDark else SlaWarningContainerLight,
                            if (isDark) SlaWarningAmber else Color(0xFFB45309),
                            Icons.Rounded.Check,
                            "Ответ дан с нарушением 30-минутного регламента"
                        )
                    } else {
                        SlaItemVisuals(
                            if (isDark) SlaGoodContainerDark else SlaGoodContainerLight,
                            if (isDark) SlaGoodGreen else Color(0xFF047857),
                            Icons.Rounded.Check,
                            "Ответ дан вовремя (регламент 30 мин соблюден)"
                        )
                    }
                } else {
                    val now = System.currentTimeMillis()
                    val deadline = if (sla.deadlineTimestamp > 0L) sla.deadlineTimestamp else email.timestamp + 30 * 60 * 1000L
                    val remainingMs = deadline - now

                    when {
                        remainingMs <= 0 -> SlaItemVisuals(
                            if (isDark) SlaUrgentContainerDark else SlaUrgentContainerLight,
                            if (isDark) SlaUrgentRed else Color(0xFFB91C1C),
                            Icons.Rounded.WarningAmber,
                            "Время на ответ истекло (регламент 30 мин)"
                        )
                        remainingMs <= 10 * 60 * 1000L -> {
                            val mins = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
                            SlaItemVisuals(
                                if (isDark) SlaUrgentContainerDark else SlaUrgentContainerLight,
                                if (isDark) SlaUrgentRed else Color(0xFFB91C1C),
                                Icons.Rounded.WarningAmber,
                                "Ответить в течение: $mins мин (регламент 30 мин)"
                            )
                        }
                        remainingMs <= 20 * 60 * 1000L -> {
                            val mins = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
                            SlaItemVisuals(
                                if (isDark) SlaWarningContainerDark else SlaWarningContainerLight,
                                if (isDark) SlaWarningAmber else Color(0xFFB45309),
                                Icons.Rounded.AccessTime,
                                "Ответить в течение: $mins мин (регламент 30 мин)"
                            )
                        }
                        else -> {
                            val mins = (remainingMs / (60 * 1000L)).coerceIn(1, 30)
                            SlaItemVisuals(
                                if (isDark) SlaGoodContainerDark else SlaGoodContainerLight,
                                if (isDark) SlaGoodGreen else Color(0xFF047857),
                                Icons.Rounded.AccessTime,
                                "Ответить в течение: $mins мин (регламент 30 мин)"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(visuals.bg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.textColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = visuals.text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textColor
                    )
                }
            }

            // Attachments
            if (email.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Вложения (${email.attachments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    email.attachments.forEach { attachment ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openAttachment(context, attachment) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    tint = JackdawAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = attachment.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val sizeKb = attachment.sizeBytes / 1024
                                    val sizeLabel = if (sizeKb > 1024) "${sizeKb / 1024} МБ" else "$sizeKb КБ"
                                    Text(
                                        text = sizeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { openAttachment(context, attachment) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Скачать и открыть",
                                        tint = JackdawAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val otherThreadEmails = threadEmails.filter { it.id != email.id }
                .sortedWith(compareByDescending<EmailMessage> { it.timestamp }.thenByDescending { it.id })
            if (otherThreadEmails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        tint = JackdawAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "История переписки (${otherThreadEmails.size + 1} сообщений)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JackdawAmber
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    otherThreadEmails.forEach { threadMsg ->
                        var isExpanded by remember { mutableStateOf(false) }
                        val msgDateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                        val threadTime = msgDateFormat.format(Date(threadMsg.timestamp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { isExpanded = !isExpanded },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(JackdawAmber.copy(alpha = 0.8f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = threadMsg.senderName.firstOrNull()?.uppercase() ?: "J",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = threadMsg.senderName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = threadTime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (isExpanded) "Свернуть" else "Показать",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JackdawAmber
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (isExpanded) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )
                                    val threadDisplayText = when {
                                        threadMsg.bodyText.isNotBlank() && threadMsg.bodyText != threadMsg.subject -> threadMsg.bodyText
                                        !threadMsg.bodyHtml.isNullOrBlank() -> runCatching {
                                            android.text.Html.fromHtml(threadMsg.bodyHtml, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                        }.getOrDefault(threadMsg.bodyText)
                                        threadMsg.bodyText.isNotBlank() -> threadMsg.bodyText
                                        threadMsg.snippet.isNotBlank() -> threadMsg.snippet
                                        else -> "(Письмо не содержит текста)"
                                    }
                                    Text(
                                        text = threadDisplayText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 20.sp
                                    )
                                } else {
                                    Text(
                                        text = threadMsg.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (email.relatedEmailsCount > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Связанные письма в цепочке (${email.relatedEmailsCount})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JackdawAmber
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Jackdaw Mail автоматически группирует историю переписки и хронологию ответов по этому вопросу.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    val isInTrash = email.folderId.contains("trash", ignoreCase = true)
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (isInTrash) "Удалить навсегда?" else "Удалить письмо?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = if (isInTrash) {
                        "Письмо «${email.subject}» будет удалено навсегда без возможности восстановления."
                    } else {
                        "Вы действительно хотите переместить письмо «${email.subject}» в корзину?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(email)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isInTrash) "Удалить навсегда" else "Удалить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

private fun openAttachment(context: Context, attachment: Attachment) {
    try {
        val attachmentsDir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val safeFileName = attachment.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val targetFile = File(attachmentsDir, safeFileName)

        if (!attachment.localUri.isNullOrBlank()) {
            val uri = Uri.parse(attachment.localUri)
            if (uri.scheme == "file") {
                val srcFile = File(uri.path ?: "")
                if (srcFile.exists() && srcFile.absolutePath != targetFile.absolutePath) {
                    srcFile.copyTo(targetFile, overwrite = true)
                }
            } else if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                val srcFile = File(attachment.localUri)
                if (srcFile.exists() && srcFile.absolutePath != targetFile.absolutePath) {
                    srcFile.copyTo(targetFile, overwrite = true)
                }
            }
        }

        if (!targetFile.exists() || targetFile.length() == 0L) {
            targetFile.writeText("Документ: ${attachment.fileName}\nРазмер: ${attachment.sizeBytes} байт\nДата: ${Date()}\nJackdaw Mail Secure Attachment")
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, attachment.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Открыть файл: ${attachment.fileName}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось открыть ${attachment.fileName}: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

