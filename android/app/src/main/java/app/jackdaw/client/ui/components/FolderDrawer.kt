package app.jackdaw.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Divider
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
import app.jackdaw.client.BuildConfig
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.designsystem.theme.JackdawAmberLight
import app.jackdaw.client.core.designsystem.theme.SlaUrgentRed
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.FolderType
import app.jackdaw.client.core.model.MailAccount

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun FolderDrawer(
    currentAccount: MailAccount,
    accounts: List<MailAccount> = emptyList(),
    onSelectAccount: (MailAccount) -> Unit = {},
    folders: List<Folder>,
    selectedFolderId: String,
    onSelectFolder: (Folder) -> Unit,
    onOpenCalendar: () -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAccountsExpanded by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = modifier
            .width(310.dp)
            .fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                                    Text(
                                        text = account.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
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

            val regularFolders = folders.filter { it.type != FolderType.SLA_ALERTS }

            Text(
                text = "ПОЧТОВЫЕ ПАПКИ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Regular Folders List
            regularFolders.forEach { folder ->
                val isSelected = folder.id == selectedFolderId
                val icon = getFolderIcon(folder.type)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                        )
                        .clickable { onSelectFolder(folder) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = folder.name,
                        tint = if (isSelected) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) JackdawAmber else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (folder.unreadCount > 0) {
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
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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

