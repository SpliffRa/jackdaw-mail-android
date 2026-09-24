package app.jackdaw.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jackdaw.client.BuildConfig
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.designsystem.theme.SlaUrgentRed
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.FolderType
import app.jackdaw.client.core.model.MailAccount

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import android.widget.Toast

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderDrawer(
    currentAccount: MailAccount,
    accounts: List<MailAccount> = emptyList(),
    onSelectAccount: (MailAccount) -> Unit = {},
    folders: List<Folder>,
    selectedFolderId: String,
    onSelectFolder: (Folder) -> Unit,
    onReorderFolders: (List<String>) -> Unit = {},
    onToggleMuteFolder: (Folder) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAccountsExpanded by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 48.dp.toPx() }

    val regularFolders = remember(folders) {
        folders.filter { it.type != FolderType.SLA_ALERTS }
    }
    // Mutable local list for reordering, synced only when outside reorder mode to prevent Room emission resets during drag
    val reorderList = remember { mutableStateListOf<Folder>() }
    LaunchedEffect(regularFolders, isReorderMode) {
        if (!isReorderMode) {
            reorderList.clear()
            reorderList.addAll(regularFolders)
        }
    }

    androidx.compose.runtime.DisposableEffect(isReorderMode) {
        onDispose {
            if (isReorderMode) {
                onReorderFolders(reorderList.map { it.id })
            }
        }
    }

    ModalDrawerSheet(
        modifier = modifier
            .width(310.dp)
            .fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = draggedId == null)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // Header / Current Account Switcher
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAccountsExpanded = !isAccountsExpanded }
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(currentAccount.avatarColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentAccount.displayName.firstOrNull()?.uppercase() ?: "J",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentAccount.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentAccount.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            if (!currentAccount.isAuthorized) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SlaUrgentRed.copy(alpha = 0.15f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "Не авторизован",
                                        fontSize = 9.sp,
                                        color = SlaUrgentRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Icon(
                        imageVector = if (isAccountsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Сменить аккаунт",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expanded account list
                if (isAccountsExpanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Column(modifier = Modifier.padding(8.dp)) {
                        accounts.forEach { account ->
                            val isCurrent = account.id == currentAccount.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                    .clickable {
                                        onSelectAccount(account)
                                        isAccountsExpanded = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(account.avatarColorHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = account.displayName.firstOrNull()?.uppercase() ?: "A",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = account.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!account.isAuthorized) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SlaUrgentRed.copy(alpha = 0.15f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "!",
                                                    fontSize = 9.sp,
                                                    color = SlaUrgentRed,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${account.email} • ${account.protocol.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Выбран",
                                        tint = JackdawAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Add account option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    isAccountsExpanded = false
                                    onOpenSettings()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Добавить аккаунт",
                                tint = JackdawAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Добавить учетную запись",
                                style = MaterialTheme.typography.bodySmall,
                                color = JackdawAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Folders list (scrolled by outer Column)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Section header with reorder toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ПОЧТОВЫЕ ПАПКИ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isReorderMode) {
                            Surface(
                                onClick = {
                                    val sorted = reorderList.sortedWith(
                                        compareBy<Folder> {
                                            when (it.type) {
                                                FolderType.INBOX -> 0
                                                FolderType.SENT -> 1
                                                FolderType.DRAFTS -> 2
                                                FolderType.ARCHIVE -> 3
                                                FolderType.OUTBOX -> 4
                                                FolderType.TRASH -> 5
                                                FolderType.SLA_ALERTS -> 6
                                                FolderType.CUSTOM -> 100
                                            }
                                        }.thenBy { it.name }
                                    )
                                    reorderList.clear()
                                    reorderList.addAll(sorted)
                                    onReorderFolders(reorderList.map { it.id })
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Сброс",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Toggle reorder mode button
                        Surface(
                            onClick = {
                                if (isReorderMode) {
                                    onReorderFolders(reorderList.map { it.id })
                                }
                                isReorderMode = !isReorderMode
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isReorderMode) JackdawAmber.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isReorderMode) Icons.Rounded.Done else Icons.Rounded.Edit,
                                    contentDescription = if (isReorderMode) "Готово" else "Упорядочить",
                                    tint = if (isReorderMode) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isReorderMode) "Готово" else "Упорядочить",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isReorderMode) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isReorderMode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Folders list
                reorderList.forEachIndexed { index, folder ->
                    key(folder.id) {
                        val isSelected = folder.id == selectedFolderId
                        val icon = getFolderIcon(folder.type)
                        val isBeingDragged = folder.id == draggedId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isBeingDragged) 10f else 1f)
                                .graphicsLayer {
                                    translationY = if (isBeingDragged) dragOffsetY else 0f
                                    shadowElevation = if (isBeingDragged) 12f else 0f
                                    scaleX = if (isBeingDragged) 1.02f else 1.0f
                                    scaleY = if (isBeingDragged) 1.02f else 1.0f
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isBeingDragged -> MaterialTheme.colorScheme.surfaceVariant
                                        isSelected && !isReorderMode -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isReorderMode) {
                                        Modifier
                                    } else {
                                        Modifier.combinedClickable(
                                            onClick = { onSelectFolder(folder) },
                                            onLongClick = {
                                                onToggleMuteFolder(folder)
                                                val msg = if (!folder.isMuted) {
                                                    "Уведомления отключены: «${folder.name}»"
                                                } else {
                                                    "Уведомления включены: «${folder.name}»"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                )
                                .padding(
                                    start = if (isReorderMode) 8.dp else 12.dp,
                                    end = if (isReorderMode) 8.dp else 12.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // In reorder mode: show Mute toggle button on the left
                            if (isReorderMode) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (folder.isMuted) SlaUrgentRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        )
                                        .clickable {
                                            onToggleMuteFolder(folder)
                                            val msg = if (!folder.isMuted) {
                                                "Уведомления отключены: «${folder.name}»"
                                            } else {
                                                "Уведомления включены: «${folder.name}»"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (folder.isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                                        contentDescription = if (folder.isMuted) "Включить уведомления" else "Заглушить уведомления",
                                        tint = if (folder.isMuted) SlaUrgentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = folder.name,
                                tint = if (isSelected && !isReorderMode) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected && !isReorderMode) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected && !isReorderMode) JackdawAmber else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                // Show mute icon in normal mode if folder is muted
                                if (folder.isMuted && !isReorderMode) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsOff,
                                        contentDescription = "Без уведомлений",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Unread count badge
                            if (folder.unreadCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (folder.type == FolderType.SLA_ALERTS) SlaUrgentRed else JackdawAmber
                                        )
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${folder.unreadCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }

                            // In reorder mode: show clean Drag Handle on the right with immediate finger drag support
                            if (isReorderMode) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isBeingDragged) JackdawAmber.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        )
                                        .pointerInput(folder.id) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    draggedId = folder.id
                                                    dragOffsetY = 0f
                                                },
                                                onDragEnd = {
                                                    val finalOrder = reorderList.map { it.id }
                                                    draggedId = null
                                                    dragOffsetY = 0f
                                                    onReorderFolders(finalOrder)
                                                },
                                                onDragCancel = {
                                                    draggedId = null
                                                    dragOffsetY = 0f
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount
                                                    val currentIdx = reorderList.indexOfFirst { it.id == draggedId }
                                                    if (currentIdx != -1) {
                                                        val step = if (itemHeightPx > 0f) itemHeightPx else 48f
                                                        if (dragOffsetY > step * 0.5f && currentIdx < reorderList.size - 1) {
                                                            val item = reorderList.removeAt(currentIdx)
                                                            reorderList.add(currentIdx + 1, item)
                                                            dragOffsetY -= step
                                                        } else if (dragOffsetY < -step * 0.5f && currentIdx > 0) {
                                                            val item = reorderList.removeAt(currentIdx)
                                                            reorderList.add(currentIdx - 1, item)
                                                            dragOffsetY += step
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "Перетащить",
                                        tint = if (isBeingDragged) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Calendar item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenCalendar() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.DateRange,
                    contentDescription = "Календарь",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Календарь встреч",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Settings item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Настройки",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Настройки и учетные записи",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // App version badge (SemVer)
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Jackdaw Mail v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun getFolderIcon(type: FolderType): ImageVector {
    return when (type) {
        FolderType.INBOX -> Icons.Rounded.Inbox
        FolderType.SLA_ALERTS -> Icons.Rounded.NotificationImportant
        FolderType.SENT -> Icons.AutoMirrored.Rounded.Send
        FolderType.OUTBOX -> Icons.Rounded.Outbox
        FolderType.DRAFTS -> Icons.Rounded.Drafts
        FolderType.ARCHIVE -> Icons.Rounded.Archive
        FolderType.TRASH -> Icons.Rounded.Delete
        FolderType.CUSTOM -> Icons.Rounded.Folder
    }
}

