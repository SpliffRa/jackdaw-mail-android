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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jackdaw.client.core.util.cleanEmailPreview
import app.jackdaw.client.core.util.cleanEmailSubject
import app.jackdaw.client.core.util.cleanDisplayEmail
import app.jackdaw.client.core.model.MailAccount
import androidx.compose.ui.graphics.luminance
import android.webkit.WebResourceRequest
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
    currentAccount: MailAccount? = null,
    onBack: () -> Unit,
    onReply: (EmailMessage) -> Unit,
    onDelete: (EmailMessage) -> Unit,
    onArchive: (EmailMessage) -> Unit,
    onToggleStar: (Boolean) -> Unit,
    onToggleRead: (Boolean) -> Unit = {},
    onDownloadAttachment: (Attachment, (java.io.File?) -> Unit) -> Unit = { _, cb -> cb(null) },
    onReloadBody: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("ru"))
    val formattedDate = dateFormat.format(Date(email.timestamp))
    val readStatusManager = remember { app.jackdaw.client.core.readstatus.ReadStatusManager.getInstance(context) }

    val isInSent = email.folderId.contains("sent", ignoreCase = true)
    val cleanSenderEmail = email.senderEmail.cleanDisplayEmail(
        isSentFolder = isInSent,
        accountEmail = currentAccount?.email
    )
    val cleanRecipients = email.toRecipients.mapNotNull { rec ->
        val c = rec.cleanDisplayEmail(isSentFolder = false)
        c.ifBlank { null }
    }
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
        var displayedEmail by remember(email.id) { mutableStateOf(email) }
        val isSystemDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        var forceLightMode by remember(displayedEmail.id) { mutableStateOf(false) }
        val effectiveDark = isSystemDark && !forceLightMode

        val isSent = displayedEmail.folderId.contains("sent", ignoreCase = true)
        val currentSenderEmail = displayedEmail.senderEmail.cleanDisplayEmail(
            isSentFolder = isSent,
            accountEmail = currentAccount?.email
        )
        val currentRecipients = displayedEmail.toRecipients.mapNotNull { rec ->
            val c = rec.cleanDisplayEmail(isSentFolder = false)
            c.ifBlank { null }
        }
        val currentFormattedDate = remember(displayedEmail.timestamp) {
            dateFormat.format(Date(displayedEmail.timestamp))
        }

        val allThreadMessages = remember(email.id, threadEmails) {
            val list = if (threadEmails.any { it.id == email.id }) {
                threadEmails
            } else {
                listOf(email) + threadEmails
            }
            list.sortedByDescending { it.timestamp }
        }

        var downloadingAttachmentId by remember { mutableStateOf<String?>(null) }
        val handleOpenAttachment = { attachment: Attachment ->
            val attachmentsDir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val safeFileName = attachment.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(attachmentsDir, "${attachment.id.take(8)}_$safeFileName")
            val localFile = if (!attachment.localUri.isNullOrBlank()) File(attachment.localUri) else null

            if (downloadingAttachmentId == attachment.id) {
                Toast.makeText(context, "Файл уже загружается, пожалуйста подождите...", Toast.LENGTH_SHORT).show()
            } else if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                openFile(context, localFile, attachment.mimeType)
            } else if (targetFile.exists() && targetFile.length() > 0L) {
                openFile(context, targetFile, attachment.mimeType)
            } else {
                downloadingAttachmentId = attachment.id
                Toast.makeText(context, "Загрузка вложения «${attachment.fileName}»...", Toast.LENGTH_SHORT).show()
                onDownloadAttachment(attachment) { file ->
                    downloadingAttachmentId = null
                    if (file != null && file.exists() && file.length() > 0L) {
                        openFile(context, file, attachment.mimeType)
                    } else {
                        Toast.makeText(context, "Не удалось загрузить «${attachment.fileName}». Файл недоступен на сервере.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Header: Subject, Reading mode toggle, Sender, SLA, Thread selector, Attachments
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Row with Subject and reading mode pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = displayedEmail.subject.cleanEmailSubject(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

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
                                text = if (forceLightMode) "Тёмный" else "Светлый",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (forceLightMode) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sender Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(JackdawAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayedEmail.senderName.firstOrNull()?.uppercase() ?: "J",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayedEmail.senderName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentSenderEmail.isNotBlank()) {
                            Text(
                                text = "<$currentSenderEmail>",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (currentRecipients.isNotEmpty()) {
                            Text(
                                text = "кому: ${currentRecipients.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = currentFormattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // SLA Indicator (if incoming and SLA active)
                val isIncoming = displayedEmail.folderId.contains("inbox", ignoreCase = true)
                val sla = displayedEmail.slaInfo
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
                        val deadline = if (sla.deadlineTimestamp > 0L) sla.deadlineTimestamp else displayedEmail.timestamp + 30 * 60 * 1000L
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(visuals.bg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = visuals.icon,
                            contentDescription = null,
                            tint = visuals.textColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = visuals.text,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = visuals.textColor
                        )
                    }
                }

                // Thread switcher chips (if more than 1 email in conversation)
                if (allThreadMessages.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Цепочка писем (${allThreadMessages.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        color = JackdawAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val threadTimeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allThreadMessages, key = { it.id }) { threadMsg ->
                            val isSelected = threadMsg.id == displayedEmail.id
                            Surface(
                                onClick = { displayedEmail = threadMsg },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) JackdawAmber.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) JackdawAmber else Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = threadTimeFormat.format(Date(threadMsg.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) JackdawAmber else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = threadMsg.senderName.take(15),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Attachments row (horizontal scroll chips)
                if (displayedEmail.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedEmail.attachments, key = { it.id }) { attachment ->
                            val isDownloading = downloadingAttachmentId == attachment.id
                            val sizeLabel = when {
                                attachment.sizeBytes > 1024 * 1024 -> "${attachment.sizeBytes / (1024 * 1024)} МБ"
                                attachment.sizeBytes > 1024 -> "${attachment.sizeBytes / 1024} КБ"
                                attachment.sizeBytes > 0 -> "${attachment.sizeBytes} Б"
                                else -> ""
                            }

                            Surface(
                                onClick = { handleOpenAttachment(attachment) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = JackdawAmber
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.AttachFile,
                                            contentDescription = null,
                                            tint = JackdawAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = attachment.fileName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                    if (sizeLabel.isNotBlank()) {
                                        Text(
                                            text = sizeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            val hasFullHtml = !displayedEmail.bodyHtml.isNullOrBlank()
            val hasFullText = displayedEmail.bodyText.length > 300 && 
                              displayedEmail.bodyText != displayedEmail.snippet && 
                              !displayedEmail.bodyText.endsWith("...")
            val isBodyLoading = !hasFullHtml && !hasFullText

            if (isBodyLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = JackdawAmber,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Загрузка полного письма...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            onClick = { onReloadBody() },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Обновить письмо",
                                style = MaterialTheme.typography.labelMedium,
                                color = JackdawAmber,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            } else {
                val rawBodyContent = remember(displayedEmail.id, displayedEmail.bodyHtml, displayedEmail.bodyText, displayedEmail.snippet) {
                    if (hasFullHtml) {
                        displayedEmail.bodyHtml!!
                    } else {
                        val displayText = when {
                            displayedEmail.bodyText.isNotBlank() && displayedEmail.bodyText != displayedEmail.subject -> displayedEmail.bodyText
                            displayedEmail.snippet.isNotBlank() -> displayedEmail.snippet
                            else -> "(Письмо не содержит текста)"
                        }
                        plainTextToHtml(displayText)
                    }
                }

                val htmlDocument = remember(displayedEmail.id, effectiveDark, rawBodyContent.hashCode()) {
                    prepareEmailHtml(rawBodyContent, effectiveDark)
                }

                val webViewTag = "${displayedEmail.id}_${effectiveDark}_${rawBodyContent.hashCode()}"

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            tag = webViewTag
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val uri = request?.url ?: return false
                                    return handleUriRedirect(ctx, uri)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    val uri = url?.let { Uri.parse(it) } ?: return false
                                    return handleUriRedirect(ctx, uri)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript("fixDarkColors();", null)
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_NO_CACHE
                            }
                            isVerticalScrollBarEnabled = true
                            isHorizontalScrollBarEnabled = true
                            setBackgroundColor(if (effectiveDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE)
                            loadDataWithBaseURL("https://outlook.office.com/", htmlDocument, "text/html", "UTF-8", null)
                        }
                    },
                    update = { view ->
                        if (view.tag != webViewTag) {
                            view.tag = webViewTag
                            view.setBackgroundColor(if (effectiveDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE)
                            view.loadDataWithBaseURL("https://outlook.office.com/", htmlDocument, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
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

private fun prepareEmailHtml(rawHtml: String, isDark: Boolean): String {
    val textColor = if (isDark) "#E6E1E5" else "#1C1B1F"
    val bgColor = if (isDark) "#121212" else "#FFFFFF"
    val linkColor = if (isDark) "#64B5F6" else "#1976D2"

    val injectedHead = """
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
        <style>
          html, body {
            margin: 0;
            padding: 12px 14px 24px 14px;
            background-color: $bgColor !important;
            color: $textColor !important;
            font-family: -apple-system, Roboto, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
            font-size: 15px;
            line-height: 1.55;
            word-break: normal;
            overflow-wrap: break-word;
          }
          a, a:link, a:visited {
            color: $linkColor !important;
            text-decoration: underline !important;
          }
          img {
            max-width: 100% !important;
            height: auto !important;
          }
          table {
            max-width: 100% !important;
          }
          pre, code {
            white-space: pre-wrap;
            word-break: break-all;
          }
          .SpellE, .GramE, [class*="Spell"], [class*="Gram"],
          span[style*="border-bottom: 1px"], span[style*="text-decoration: underline wavy"] {
            text-decoration: none !important;
            border-bottom: none !important;
            box-shadow: none !important;
          }
          blockquote {
            margin: 8px 0;
            padding-left: 10px;
            border-left: 3px solid ${if (isDark) "#444" else "#ccc"};
            color: ${if (isDark) "#aaa" else "#666"};
          }
          hr {
            border: none;
            border-top: 1px solid ${if (isDark) "#333" else "#ddd"};
            margin: 12px 0;
          }
          ${if (isDark) """
          /* In dark mode, ensure any text styled with black or dark colors is readable */
          [style*="color:black"], [style*="color: black"],
          [style*="color:#000"], [style*="color: #000"],
          [style*="color:#1"], [style*="color: #1"],
          [style*="color:#2"], [style*="color: #2"],
          [style*="color:#3"], [style*="color: #3"],
          [style*="color:windowtext"], [style*="color: windowtext"],
          font[color="#000000"], font[color="black"], font[color="#000"] {
            color: #E6E1E5 !important;
          }
          /* In dark mode, transparentize forced white backgrounds so they don't blindingly flash */
          [style*="background-color:white"], [style*="background-color: white"],
          [style*="background-color:#fff"], [style*="background-color: #fff"],
          [style*="background-color:#FFF"], [style*="background-color: #FFF"],
          [style*="background:white"], [style*="background: white"],
          [style*="background:#fff"], [style*="background: #fff"] {
            background-color: transparent !important;
          }
          p, span, div, font, td, th, li {
            color: #E6E1E5;
          }
          """ else ""}
        </style>
        <script>
          function fixDarkColors() {
            var spells = document.querySelectorAll('.SpellE, .GramE, [class*="Spell"], [class*="Gram"]');
            for (var i = 0; i < spells.length; i++) {
              spells[i].style.setProperty('text-decoration', 'none', 'important');
              spells[i].style.setProperty('border-bottom', 'none', 'important');
            }
            if ($isDark) {
              var all = document.querySelectorAll('*');
              for (var j = 0; j < all.length; j++) {
                var el = all[j];
                if (el.tagName === 'A' || el.closest('a')) continue;
                var cs = window.getComputedStyle(el);
                var c = cs.color;
                var match = c.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)/);
                if (match) {
                  var r = parseInt(match[1]), g = parseInt(match[2]), b = parseInt(match[3]);
                  var brightness = (r * 299 + g * 587 + b * 114) / 1000;
                  if (brightness < 130) {
                    el.style.setProperty('color', '#E6E1E5', 'important');
                  }
                }
                var bg = cs.backgroundColor;
                if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') {
                  var bgMatch = bg.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)/);
                  if (bgMatch) {
                    var br = parseInt(bgMatch[1]), bgCol = parseInt(bgMatch[2]), bb = parseInt(bgMatch[3]);
                    var bgBrightness = (br * 299 + bgCol * 587 + bb * 114) / 1000;
                    if (bgBrightness > 160) {
                      el.style.setProperty('background-color', 'transparent', 'important');
                    }
                  }
                }
              }
            }
          }
          document.addEventListener('DOMContentLoaded', fixDarkColors);
          window.addEventListener('load', fixDarkColors);
          fixDarkColors();
          setTimeout(fixDarkColors, 50);
          setTimeout(fixDarkColors, 250);
          setTimeout(fixDarkColors, 800);
        </script>
    """.trimIndent()

    val headRegex = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE)
    val htmlRegex = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)

    val headMatch = headRegex.find(rawHtml)
    if (headMatch != null) {
        val insertPos = headMatch.range.last + 1
        return rawHtml.substring(0, insertPos) + "\n" + injectedHead + rawHtml.substring(insertPos)
    }
    val htmlMatch = htmlRegex.find(rawHtml)
    if (htmlMatch != null) {
        val insertPos = htmlMatch.range.last + 1
        return rawHtml.substring(0, insertPos) + "\n<head>" + injectedHead + "</head>" + rawHtml.substring(insertPos)
    }
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">$injectedHead</head><body>$rawHtml</body></html>"
}

private fun openFile(context: Context, file: File, mimeType: String) {
    if (!file.exists() || file.length() <= 0L) {
        Toast.makeText(context, "Файл не найден или пуст", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Открыть: ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось открыть файл: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private val URL_REGEX = Regex("""(?i)\b(https?://[^\s<>"]+)""")

private fun plainTextToHtml(text: String): String {
    val escaped = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
    val linked = URL_REGEX.replace(escaped) { matchResult ->
        val url = matchResult.value
        """<a href="$url">$url</a>"""
    }
    return linked.replace("\n", "<br>")
}

private fun handleUriRedirect(context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase()
    if (scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel") {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            android.util.Log.e("MailDetailScreen", "Failed to open link: $uri", e)
        }
    }
    return false
}

