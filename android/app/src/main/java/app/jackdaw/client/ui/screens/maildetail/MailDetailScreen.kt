package app.jackdaw.client.ui.screens.maildetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import app.jackdaw.client.core.util.AttachmentHelper
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
    onReloadBody: (EmailMessage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("ru")) }
    val readStatusManager = remember { app.jackdaw.client.core.readstatus.ReadStatusManager.getInstance(context) }

    var selectedThreadEmailId by remember(email.id) { mutableStateOf<String?>(null) }
    val displayedEmail = if (selectedThreadEmailId != null && selectedThreadEmailId != email.id) {
        threadEmails.find { it.id == selectedThreadEmailId } ?: email
    } else {
        email
    }

    val isInSent = displayedEmail.folderId.contains("sent", ignoreCase = true)
    val cleanSenderEmail = displayedEmail.senderEmail.cleanDisplayEmail(
        isSentFolder = isInSent,
        accountEmail = currentAccount?.email
    )
    val cleanRecipients = displayedEmail.toRecipients.mapNotNull { rec ->
        val c = rec.cleanDisplayEmail(isSentFolder = false)
        c.ifBlank { null }
    }

    androidx.compose.runtime.LaunchedEffect(displayedEmail.id) {
        if (displayedEmail.bodyHtml.isNullOrBlank()) {
            onReloadBody(displayedEmail)
        }
    }

    androidx.compose.runtime.LaunchedEffect(displayedEmail.id) {
        if (!displayedEmail.isRead && readStatusManager.mode == app.jackdaw.client.core.readstatus.MarkAsReadMode.AFTER_DELAY) {
            kotlinx.coroutines.delay(readStatusManager.delaySeconds * 1000L)
            onToggleRead(true)
        }
    }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var forceLightMode by remember(displayedEmail.id) { mutableStateOf(false) }

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
                    IconButton(onClick = { forceLightMode = !forceLightMode }) {
                        Icon(
                            imageVector = if (forceLightMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            contentDescription = if (forceLightMode) "Тёмная тема" else "Светлая тема",
                            tint = if (forceLightMode) JackdawAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { onReloadBody(displayedEmail) }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Обновить письмо",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { onToggleRead(!displayedEmail.isRead) }) {
                        Icon(
                            imageVector = if (displayedEmail.isRead) Icons.Rounded.MarkEmailUnread else Icons.Rounded.MarkEmailRead,
                            contentDescription = if (displayedEmail.isRead) "Отметить как непрочитанное" else "Отметить как прочитанное",
                            tint = if (displayedEmail.isRead) MaterialTheme.colorScheme.onSurface else JackdawAmber
                        )
                    }
                    IconButton(onClick = { onToggleStar(!displayedEmail.isStarred) }) {
                        Icon(
                            imageVector = if (displayedEmail.isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Избранное",
                            tint = if (displayedEmail.isStarred) JackdawAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { onArchive(displayedEmail) }) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = "В архив",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (displayedEmail.slaInfo == null) {
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
                        onClick = { onReply(displayedEmail) },
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
                        onClick = { onReply(displayedEmail) },
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
        val isSystemDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val effectiveDark = isSystemDark && !forceLightMode
        val isBodyFetching = displayedEmail.bodyHtml.isNullOrBlank()

        val isSent = isInSent
        val currentSenderEmail = cleanSenderEmail
        val currentRecipients = cleanRecipients
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
        var selectedAttachmentForAction by remember { mutableStateOf<Attachment?>(null) }
        var pendingFileToSaveWithPicker by remember { mutableStateOf<Pair<File, Attachment>?>(null) }

        val createDocumentLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("*/*")
        ) { destinationUri ->
            val pair = pendingFileToSaveWithPicker
            pendingFileToSaveWithPicker = null
            if (destinationUri != null && pair != null) {
                val success = AttachmentHelper.saveToUri(context, pair.first, destinationUri)
                if (success) {
                    Toast.makeText(context, "Файл «${pair.second.fileName}» сохранен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Не удалось сохранить файл", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun ensureAttachmentFile(attachment: Attachment, onReady: (File) -> Unit) {
            val attachmentsDir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val safeAttId = attachment.id.replace("[^a-zA-Z0-9]".toRegex(), "_").take(16)
            val safeFileName = attachment.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(attachmentsDir, "${safeAttId}_$safeFileName")
            val localFile = if (!attachment.localUri.isNullOrBlank()) File(attachment.localUri) else null

            if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                onReady(localFile)
            } else if (targetFile.exists() && targetFile.length() > 0L) {
                onReady(targetFile)
            } else {
                if (downloadingAttachmentId == attachment.id) {
                    Toast.makeText(context, "Файл уже загружается, пожалуйста подождите...", Toast.LENGTH_SHORT).show()
                    return
                }
                downloadingAttachmentId = attachment.id
                Toast.makeText(context, "Загрузка вложения «${attachment.fileName}»...", Toast.LENGTH_SHORT).show()
                onDownloadAttachment(attachment) { file ->
                    downloadingAttachmentId = null
                    if (file != null && file.exists() && file.length() > 0L) {
                        onReady(file)
                    } else {
                        Toast.makeText(context, "Не удалось загрузить «${attachment.fileName}». Файл недоступен на сервере.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val handleOpenAttachment = { attachment: Attachment ->
            ensureAttachmentFile(attachment) { file ->
                AttachmentHelper.openFile(context, file, attachment.mimeType)
            }
        }

        val handleSaveToDownloads = { attachment: Attachment ->
            ensureAttachmentFile(attachment) { file ->
                val uri = AttachmentHelper.saveToDownloads(context, file, attachment.fileName, attachment.mimeType)
                if (uri != null) {
                    Toast.makeText(context, "Файл сохранен в Загрузки: «${attachment.fileName}»", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Не удалось сохранить «${attachment.fileName}» в Загрузки", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val handleSaveWithPicker = { attachment: Attachment ->
            ensureAttachmentFile(attachment) { file ->
                pendingFileToSaveWithPicker = Pair(file, attachment)
                try {
                    createDocumentLauncher.launch(attachment.fileName)
                } catch (e: Exception) {
                    Toast.makeText(context, "Не удалось открыть выбор папки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val handleSaveAllAttachmentsToDownloads = {
            val atts = displayedEmail.attachments
            if (atts.isNotEmpty()) {
                Toast.makeText(context, "Сохранение ${atts.size} вложений в Загрузки...", Toast.LENGTH_SHORT).show()
                var savedCount = 0
                for (att in atts) {
                    ensureAttachmentFile(att) { file ->
                        val uri = AttachmentHelper.saveToDownloads(context, file, att.fileName, att.mimeType)
                        if (uri != null) savedCount++
                        if (savedCount == atts.size) {
                            Toast.makeText(context, "Все вложения ($savedCount) сохранены в Загрузки", Toast.LENGTH_LONG).show()
                        }
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
                // Unified compact header: Avatar + [Subject + From + To + Date]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(JackdawAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayedEmail.senderName.firstOrNull()?.uppercase() ?: "J",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Тема письма: сжато, аккуратно и читаемо
                        Text(
                            text = displayedEmail.subject.cleanEmailSubject(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // От кого + дата
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = buildString {
                                    append(displayedEmail.senderName)
                                    if (currentSenderEmail.isNotBlank()) {
                                        append(" <$currentSenderEmail>")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentFormattedDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Кому
                        if (currentRecipients.isNotEmpty()) {
                            Text(
                                text = "кому: ${currentRecipients.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                                onClick = { selectedThreadEmailId = threadMsg.id },
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

                // Attachments section (header with count + horizontal scroll chips)
                if (displayedEmail.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                tint = JackdawAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Вложения (${displayedEmail.attachments.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (displayedEmail.attachments.size > 1) {
                            TextButton(
                                onClick = { handleSaveAllAttachmentsToDownloads() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = JackdawAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Сохранить все в Загрузки",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = JackdawAmber
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
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
                                onClick = { selectedAttachmentForAction = attachment },
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
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Сохранить",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (displayedEmail.hasAttachments && isBodyFetching) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = JackdawAmber
                            )
                            Text(
                                text = "Загрузка вложений...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            val rawBodyContent = remember(displayedEmail.id, displayedEmail.bodyHtml, displayedEmail.bodyText, displayedEmail.snippet) {
                if (!displayedEmail.bodyHtml.isNullOrBlank()) {
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

            val htmlDocument by produceState(
                initialValue = "",
                displayedEmail.id,
                effectiveDark,
                rawBodyContent
            ) {
                value = withContext(Dispatchers.Default) {
                    prepareEmailHtml(rawBodyContent, effectiveDark)
                }
            }

            val isPreparingHtml = htmlDocument.isBlank() && rawBodyContent.isNotBlank()
            if (isBodyFetching || isPreparingHtml) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = JackdawAmber,
                    trackColor = Color.Transparent
                )
            }

            val webViewTag = "${displayedEmail.id}_${effectiveDark}_${htmlDocument.hashCode()}"

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
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
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = false
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_NO_CACHE
                        }
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = true
                        setBackgroundColor(if (effectiveDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE)
                        if (htmlDocument.isNotBlank()) {
                            tag = webViewTag
                            loadDataWithBaseURL("https://outlook.office.com/", htmlDocument, "text/html", "UTF-8", null)
                        }
                    }
                },
                update = { view ->
                    if (htmlDocument.isNotBlank() && view.tag != webViewTag) {
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
        if (selectedAttachmentForAction != null) {
            val att = selectedAttachmentForAction
            if (att != null) {
                val sizeText = when {
                    att.sizeBytes > 1024 * 1024 -> "${att.sizeBytes / (1024 * 1024)} МБ"
                    att.sizeBytes > 1024 -> "${att.sizeBytes / 1024} КБ"
                    att.sizeBytes > 0 -> "${att.sizeBytes} Б"
                    else -> ""
                }

                AlertDialog(
                    onDismissRequest = { selectedAttachmentForAction = null },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = null,
                            tint = JackdawAmber,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = att.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (sizeText.isNotBlank()) {
                                Text(
                                    text = "Размер: $sizeText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // 1. Сохранить в Загрузки (по умолчанию)
                            Surface(
                                onClick = {
                                    val target = att
                                    selectedAttachmentForAction = null
                                    handleSaveToDownloads(target)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(JackdawAmber.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            tint = JackdawAmber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Сохранить в Загрузки",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Папка по умолчанию (Download)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 2. Выбрать папку для сохранения...
                            Surface(
                                onClick = {
                                    val target = att
                                    selectedAttachmentForAction = null
                                    handleSaveWithPicker(target)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Выбрать другую папку...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Сохранить через проводник Android",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 3. Открыть файл
                            Surface(
                                onClick = {
                                    val target = att
                                    selectedAttachmentForAction = null
                                    handleOpenAttachment(target)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Открыть файл",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Просмотр в установленном приложении",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { selectedAttachmentForAction = null }) {
                            Text("Отмена")
                        }
                    }
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

private val REGEX_BGCOLOR_ATTR = Regex("""\s+bgcolor=["'][^"']*["']""", RegexOption.IGNORE_CASE)
private val REGEX_LIGHT_BG = Regex("""background(-color)?\s*:\s*(white|#fff\b|#ffffff\b|#efefef\b|#d9d8d1\b|#f\w{2,5}\b|#e\w{2,5}\b)[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_DARK_TEXT = Regex("""color\s*:\s*(#0\w{2,5}|#1\w{2,5}|#2\w{2,5}|#3\w{2,5}|#4\w{2,5}|#5\w{2,5}|#6\w{2,5}|black|windowtext)[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_MARGIN_LEFT = Regex("""margin-left\s*:\s*36[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_TEXT_INDENT = Regex("""text-indent\s*:\s*-18[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_CONSECUTIVE_BR = Regex("""(<br\s*/?>\s*){3,}""", RegexOption.IGNORE_CASE)
private val REGEX_LINE_HEIGHT = Regex("""line-height\s*:\s*\d+(\.\d+)?pt[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_MSO_LINE_HEIGHT = Regex("""mso-line-height-rule\s*:\s*exactly[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_FONT_SIZE = Regex("""font-size\s*:\s*(2[0-9]|[3-9][0-9])(\.\d+)?pt[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_NEGATIVE_MARGIN = Regex("""margin-(left|right)\s*:\s*-[0-9.]+pt[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_FIXED_WIDTH_ATTR = Regex("""\s+width=["'](0|[1-9]\d{2,})["']""", RegexOption.IGNORE_CASE)
private val REGEX_WIDTH_PT = Regex("""width\s*:\s*\d+(\.\d+)?pt[^;}"']*""", RegexOption.IGNORE_CASE)
private val REGEX_FLOAT = Regex("""float\s*:\s*(left|right)[^;}"']*""", RegexOption.IGNORE_CASE)
private val HEAD_REGEX = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_REGEX = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)

private fun prepareEmailHtml(rawHtml: String, isDark: Boolean): String {
    val textColor = if (isDark) "#E6E1E5" else "#1C1B1F"
    val bgColor = if (isDark) "#121212" else "#FFFFFF"
    val linkColor = if (isDark) "#64B5F6" else "#1976D2"

    var processedHtml = rawHtml
    if (isDark) {
        if (processedHtml.contains("bgcolor=", ignoreCase = true)) {
            processedHtml = REGEX_BGCOLOR_ATTR.replace(processedHtml, "")
        }
        if (processedHtml.contains("background", ignoreCase = true)) {
            processedHtml = REGEX_LIGHT_BG.replace(processedHtml, "background: transparent")
        }
        if (processedHtml.contains("color", ignoreCase = true)) {
            processedHtml = REGEX_DARK_TEXT.replace(processedHtml, "color: #E6E1E5")
        }
    }
    if (processedHtml.contains("margin-left", ignoreCase = true)) {
        processedHtml = REGEX_MARGIN_LEFT.replace(processedHtml, "margin-left: 0")
    }
    if (processedHtml.contains("text-indent", ignoreCase = true)) {
        processedHtml = REGEX_TEXT_INDENT.replace(processedHtml, "text-indent: 0")
    }
    if (processedHtml.contains("<br", ignoreCase = true)) {
        processedHtml = REGEX_CONSECUTIVE_BR.replace(processedHtml, "<br/><br/>")
    }
    if (processedHtml.contains("line-height", ignoreCase = true)) {
        processedHtml = REGEX_LINE_HEIGHT.replace(processedHtml, "line-height: 1.55")
        processedHtml = REGEX_MSO_LINE_HEIGHT.replace(processedHtml, "")
    }
    if (processedHtml.contains("font-size", ignoreCase = true)) {
        processedHtml = REGEX_FONT_SIZE.replace(processedHtml, "font-size: 20px")
    }
    if (processedHtml.contains("margin-", ignoreCase = true)) {
        processedHtml = REGEX_NEGATIVE_MARGIN.replace(processedHtml, "margin-$1: 0")
    }
    if (processedHtml.contains("width=", ignoreCase = true)) {
        processedHtml = REGEX_FIXED_WIDTH_ATTR.replace(processedHtml, "")
    }
    if (processedHtml.contains("width:", ignoreCase = true)) {
        processedHtml = REGEX_WIDTH_PT.replace(processedHtml, "width: auto")
    }
    if (processedHtml.contains("float:", ignoreCase = true)) {
        processedHtml = REGEX_FLOAT.replace(processedHtml, "float: none")
    }

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
            box-sizing: border-box !important;
            width: 100% !important;
            max-width: 100% !important;
            overflow-x: auto;
            overflow-wrap: break-word;
            word-break: normal;
            -webkit-hyphens: manual;
            hyphens: manual;
          }
          *, *::before, *::after {
            box-sizing: border-box !important;
          }
          a, a:link, a:visited, a:hover, a:active {
            color: $linkColor !important;
            text-decoration: none !important;
          }
          a * {
            color: $linkColor !important;
            text-decoration: none !important;
          }
          u, u * {
            text-decoration: none !important;
          }
          u [style*="color:red" i], u [style*="color: red" i],
          u [style*="color:#dc0032" i], u [style*="color: #dc0032" i],
          u [style*="color:#ff0000" i], u [style*="color: #ff0000" i],
          [style*="color:red" i] u, [style*="color:#dc0032" i] u,
          font[color="red"], font[color="#DC0032"], font[color="#dc0032"], font[color="#ff0000"] {
            color: $linkColor !important;
            text-decoration: none !important;
          }
          img {
            max-width: 100% !important;
            height: auto !important;
            display: block;
            margin: 8px auto;
          }
          table, tbody, tr, td, th {
            float: none !important;
            max-width: 100% !important;
            box-sizing: border-box !important;
            margin-left: 0 !important;
            margin-right: 0 !important;
          }
          table {
            border-collapse: collapse !important;
            max-width: 100% !important;
            margin: 6px 0;
          }
          td, th {
            max-width: 100% !important;
            word-break: normal;
            overflow-wrap: break-word;
            padding: 4px !important;
          }
          p, li {
            line-height: 1.55 !important;
          }
          h1, h2, h3 {
            max-width: 100% !important;
            word-break: normal !important;
            overflow-wrap: break-word !important;
            hyphens: none !important;
            -webkit-hyphens: none !important;
          }
          h1, h1 * {
            font-size: 19px !important;
            line-height: 1.3 !important;
            letter-spacing: -0.3px;
          }
          h2, h2 * {
            font-size: 15px !important;
            line-height: 1.3 !important;
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
          p[style*="margin-left"], div[style*="margin-left"] {
            margin-left: 0 !important;
            text-indent: 0 !important;
            padding-left: 0 !important;
          }
          ${if (isDark) """
          /* Transparent canvas for dark mode matching theme */
          table, tbody, tr, td, th, div, p {
            background-color: transparent !important;
          }
          table, td, th {
            border-color: #555555 !important;
          }
          p, span, div, font, td, th, li {
            color: #E6E1E5;
          }
          [style*="background"] {
            background-color: transparent !important;
          }
          [style*="color:#0" i], [style*="color: #0" i],
          [style*="color:#1" i], [style*="color: #1" i],
          [style*="color:#2" i], [style*="color: #2" i],
          [style*="color:#3" i], [style*="color: #3" i],
          [style*="color:black" i], [style*="color: black" i],
          [style*="color:windowtext" i], [style*="color: windowtext" i] {
            color: #E6E1E5 !important;
          }
          """ else ""}
        </style>
        <script>
          document.addEventListener('DOMContentLoaded', function() {
            var spells = document.querySelectorAll('.SpellE, .GramE, [class*="Spell"], [class*="Gram"]');
            for (var i = 0; i < spells.length; i++) {
              spells[i].style.setProperty('text-decoration', 'none', 'important');
              spells[i].style.setProperty('border-bottom', 'none', 'important');
            }
          });
        </script>
    """.trimIndent()

    val headMatch = HEAD_REGEX.find(processedHtml)
    if (headMatch != null) {
        val insertPos = headMatch.range.last + 1
        return processedHtml.substring(0, insertPos) + "\n" + injectedHead + processedHtml.substring(insertPos)
    }
    val htmlMatch = HTML_REGEX.find(processedHtml)
    if (htmlMatch != null) {
        val insertPos = htmlMatch.range.last + 1
        return processedHtml.substring(0, insertPos) + "\n<head>" + injectedHead + "</head>" + processedHtml.substring(insertPos)
    }
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">$injectedHead</head><body>$processedHtml</body></html>"
}

private fun openFile(context: Context, file: File, mimeType: String) {
    AttachmentHelper.openFile(context, file, mimeType)
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

