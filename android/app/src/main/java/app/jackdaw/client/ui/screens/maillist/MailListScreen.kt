package app.jackdaw.client.ui.screens.maillist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.sp
import app.jackdaw.client.core.util.DateGroup
import app.jackdaw.client.core.util.DateGrouping
import app.jackdaw.client.core.util.PluralRules
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.designsystem.theme.SlaGoodContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaGoodGreen
import app.jackdaw.client.core.designsystem.theme.SlaUrgentContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaUrgentRed
import app.jackdaw.client.core.designsystem.theme.SlaWarningAmber
import app.jackdaw.client.core.designsystem.theme.SlaWarningContainerDark
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.FolderType
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.ui.components.EmailCard
import app.jackdaw.client.ui.components.SlaMonitoringCard
import app.jackdaw.client.ui.components.SwipeableEmailCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.Sync

enum class MailFilter(val label: String) {
    ALL("Все"),
    UNREAD("Непрочитанные"),
    STARRED("Избранное")
}

enum class SlaDashboardFilter(val label: String) {
    ALL("Все объекты"),
    URGENT("Срочные"),
    WARNING("Внимание"),
    NORMAL("В норме"),
    UNREAD("Непрочитанные")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailListScreen(
    currentAccount: MailAccount,
    currentFolder: Folder,
    emails: List<EmailMessage>,
    searchQuery: String = "",
    isSyncing: Boolean = false,
    onSyncClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onOpenDrawer: () -> Unit,
    onEmailClick: (EmailMessage) -> Unit,
    onComposeClick: () -> Unit,
    onToggleStar: (String, Boolean) -> Unit,
    onSwipeArchive: (EmailMessage) -> Unit = {},
    onSwipeDelete: (EmailMessage) -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    val isTrashFolder = currentFolder.type == FolderType.TRASH
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var selectedFilter by remember { mutableStateOf(MailFilter.ALL) }
    var emailToDeletePending by remember { mutableStateOf<EmailMessage?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    val unreadCount = emails.count { !it.isRead }


    val filteredEmails = emails.filter { email ->
        when (selectedFilter) {
            MailFilter.ALL -> true
            MailFilter.UNREAD -> !email.isRead
            MailFilter.STARRED -> email.isStarred
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    "Поиск в Jackdaw (FTS)...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JackdawAmber,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    } else {
                        Column {
                            Text(
                                text = currentFolder.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isSyncing) {
                                    "Синхронизация..."
                                } else if (unreadCount > 0) {
                                    "${PluralRules.formatEmailCount(filteredEmails.size)} • ${PluralRules.formatUnreadCount(unreadCount)}"
                                } else {
                                    PluralRules.formatEmailCount(filteredEmails.size)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSyncing) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "Меню папок",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 10.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(JackdawAmber)
                            )
                        }
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            onSearchQueryChange("")
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть поиск", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        if (isTrashFolder && filteredEmails.isNotEmpty()) {
                            IconButton(onClick = { showEmptyTrashDialog = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = "Очистить корзину",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Поиск", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(JackdawAmber),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentAccount.displayName.firstOrNull()?.uppercase() ?: "J",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onComposeClick,
                containerColor = JackdawAmber,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Написать"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Написать",
                        fontWeight = FontWeight.Bold
                    )
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
        ) {
            if (isTrashFolder && filteredEmails.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Письма в корзине",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showEmptyTrashDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = SlaUrgentRed)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Очистить корзину",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Standard Mail filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MailFilter.values()) { filter ->
                    val isSelected = filter == selectedFilter
                    val filterLabel = if (filter == MailFilter.UNREAD && unreadCount > 0) {
                        "${filter.label} ($unreadCount)"
                    } else {
                        filter.label
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filterLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JackdawAmber,
                            selectedLabelColor = Color.Black,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = null,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Emails LazyColumn with Pull-to-Refresh
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = onSyncClick,
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredEmails.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Писем не найдено",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "В этой папке нет сообщений",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val groupedEmails = remember(filteredEmails) {
                        filteredEmails.groupBy { DateGrouping.getGroup(it.timestamp) }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DateGroup.values().forEach { group ->
                            val emailsInGroup = groupedEmails[group]
                            if (!emailsInGroup.isNullOrEmpty()) {
                                item(key = "header_${group.name}") {
                                    DateSectionHeader(title = group.title, count = emailsInGroup.size)
                                }
                                items(emailsInGroup, key = { it.id }) { email ->
                                    SwipeableEmailCard(
                                        email = email,
                                        onClick = { onEmailClick(email) },
                                        onToggleStar = { isStarred -> onToggleStar(email.id, isStarred) },
                                        onSwipeArchive = { onSwipeArchive(email) },
                                        onSwipeDelete = { emailToDeletePending = email }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (emailToDeletePending != null) {
        val email = emailToDeletePending!!
        val isInTrash = isTrashFolder || email.folderId.contains("trash", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { emailToDeletePending = null },
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
                        val toDelete = email
                        emailToDeletePending = null
                        onSwipeDelete(toDelete)
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
                    onClick = { emailToDeletePending = null }
                ) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Очистить корзину?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Все письма (${filteredEmails.size}) будут безвозвратно удалены из корзины.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashDialog = false
                        onEmptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text("Очистить все", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@Composable
private fun SlaStatusPill(
    title: String,
    count: Int,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accentColor else Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DateSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = JackdawAmber
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(JackdawAmber.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = JackdawAmber
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            thickness = 0.5.dp,
            color = Color.White.copy(alpha = 0.12f)
        )
    }
}

