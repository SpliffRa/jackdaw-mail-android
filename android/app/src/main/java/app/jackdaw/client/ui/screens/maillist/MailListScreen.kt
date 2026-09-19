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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.designsystem.theme.JackdawBackgroundDark
import app.jackdaw.client.core.designsystem.theme.JackdawSurfaceDark
import app.jackdaw.client.core.designsystem.theme.JackdawSurfaceElevatedDark
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
    SLA_URGENT("SLA контроль"),
    UNREAD("Непрочитанные"),
    ATTACHMENTS("С файлами"),
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
    val isSlaFolder = currentFolder.type == FolderType.SLA_ALERTS
    val isTrashFolder = currentFolder.type == FolderType.TRASH
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var selectedFilter by remember { mutableStateOf(MailFilter.ALL) }
    var selectedSlaFilter by remember(currentFolder.id) { mutableStateOf(SlaDashboardFilter.ALL) }
    var emailToDeletePending by remember { mutableStateOf<EmailMessage?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    val unreadCount = emails.count { !it.isRead }
    val slaUrgentCount = emails.count { it.slaInfo?.severity in listOf(SlaSeverity.URGENT, SlaSeverity.BREACHED) }
    val slaWarningCount = emails.count { it.slaInfo?.severity == SlaSeverity.WARNING }
    val slaNormalCount = emails.count { it.slaInfo?.severity == SlaSeverity.NORMAL }

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val syncRotation by if (isSyncing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sync_spin"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val filteredEmails = if (isSlaFolder) {
        emails.filter { email ->
            when (selectedSlaFilter) {
                SlaDashboardFilter.ALL -> true
                SlaDashboardFilter.URGENT -> email.slaInfo?.severity in listOf(SlaSeverity.URGENT, SlaSeverity.BREACHED)
                SlaDashboardFilter.WARNING -> email.slaInfo?.severity == SlaSeverity.WARNING
                SlaDashboardFilter.NORMAL -> email.slaInfo?.severity == SlaSeverity.NORMAL
                SlaDashboardFilter.UNREAD -> !email.isRead
            }
        }
    } else {
        emails.filter { email ->
            when (selectedFilter) {
                MailFilter.ALL -> true
                MailFilter.SLA_URGENT -> email.slaInfo?.severity in listOf(SlaSeverity.URGENT, SlaSeverity.WARNING, SlaSeverity.BREACHED)
                MailFilter.UNREAD -> !email.isRead
                MailFilter.ATTACHMENTS -> email.hasAttachments
                MailFilter.STARRED -> email.isStarred
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    if (isSlaFolder) "Поиск объекта SLA..." else "Поиск в Jackdaw (FTS)...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JackdawAmber,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = JackdawSurfaceElevatedDark,
                                unfocusedContainerColor = JackdawSurfaceElevatedDark
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    } else {
                        Column {
                            Text(
                                text = if (isSlaFolder) "SLA Мониторинг" else currentFolder.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isSyncing) {
                                    "Синхронизация..."
                                } else if (isSlaFolder) {
                                    "${filteredEmails.size} на контроле" + (if (slaUrgentCount > 0) " • $slaUrgentCount срочных" else "")
                                } else if (unreadCount > 0) {
                                    "${filteredEmails.size} писем • $unreadCount непрочитанных"
                                } else {
                                    "${filteredEmails.size} писем"
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
                        IconButton(onClick = onSyncClick) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Синхронизировать",
                                tint = if (isSyncing) JackdawAmber else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.rotate(syncRotation)
                            )
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
                    containerColor = JackdawBackgroundDark
                )
            )
        },
        floatingActionButton = {
            if (!isSlaFolder) {
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
            }
        },
        containerColor = JackdawBackgroundDark,
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
                    colors = CardDefaults.cardColors(containerColor = JackdawSurfaceElevatedDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Корзина (${filteredEmails.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Удаленные сообщения",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showEmptyTrashDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444).copy(alpha = 0.18f),
                                contentColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Очистить все",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (isSlaFolder) {
                // Executive SLA KPI summary tiles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SlaMetricTile(
                        title = "Срочные",
                        count = slaUrgentCount,
                        color = SlaUrgentRed,
                        bgColor = SlaUrgentContainerDark,
                        isSelected = selectedSlaFilter == SlaDashboardFilter.URGENT,
                        onClick = {
                            selectedSlaFilter = if (selectedSlaFilter == SlaDashboardFilter.URGENT) SlaDashboardFilter.ALL else SlaDashboardFilter.URGENT
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SlaMetricTile(
                        title = "Внимание",
                        count = slaWarningCount,
                        color = SlaWarningAmber,
                        bgColor = SlaWarningContainerDark,
                        isSelected = selectedSlaFilter == SlaDashboardFilter.WARNING,
                        onClick = {
                            selectedSlaFilter = if (selectedSlaFilter == SlaDashboardFilter.WARNING) SlaDashboardFilter.ALL else SlaDashboardFilter.WARNING
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SlaMetricTile(
                        title = "В норме",
                        count = slaNormalCount,
                        color = SlaGoodGreen,
                        bgColor = SlaGoodContainerDark,
                        isSelected = selectedSlaFilter == SlaDashboardFilter.NORMAL,
                        onClick = {
                            selectedSlaFilter = if (selectedSlaFilter == SlaDashboardFilter.NORMAL) SlaDashboardFilter.ALL else SlaDashboardFilter.NORMAL
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // SLA Filter chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SlaDashboardFilter.values()) { filter ->
                        val isSelected = filter == selectedSlaFilter
                        val filterLabel = when (filter) {
                            SlaDashboardFilter.ALL -> "Все (${emails.size})"
                            SlaDashboardFilter.URGENT -> "Срочные ($slaUrgentCount)"
                            SlaDashboardFilter.WARNING -> "Внимание ($slaWarningCount)"
                            SlaDashboardFilter.NORMAL -> "В норме ($slaNormalCount)"
                            SlaDashboardFilter.UNREAD -> "Непрочитанные ($unreadCount)"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSlaFilter = filter },
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
                                containerColor = JackdawSurfaceElevatedDark,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = null,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            } else {
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
                                containerColor = JackdawSurfaceElevatedDark,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = null,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Emails / SLA LazyColumn
            if (filteredEmails.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isSlaFolder) "Нет объектов контроля" else "Писем не найдено",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isSlaFolder) "Все регламентные обязательства под контролем" else "В этой папке нет сообщений",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSlaFolder) {
                        // Dedicated SLA monitoring cards without delete capability
                        items(filteredEmails, key = { it.id }) { email ->
                            SlaMonitoringCard(
                                email = email,
                                onClick = { onEmailClick(email) },
                                onToggleStar = { isStarred -> onToggleStar(email.id, isStarred) }
                            )
                        }
                    } else {
                        items(filteredEmails, key = { it.id }) { email ->
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

    if (emailToDeletePending != null) {
        val email = emailToDeletePending!!
        AlertDialog(
            onDismissRequest = { emailToDeletePending = null },
            containerColor = JackdawSurfaceElevatedDark,
            title = {
                Text(
                    text = "Удалить письмо?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Вы действительно хотите переместить письмо «${email.subject}» в корзину?",
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
                    Text("Удалить", fontWeight = FontWeight.Bold)
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
            containerColor = JackdawSurfaceElevatedDark,
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
private fun SlaMetricTile(
    title: String,
    count: Int,
    color: Color,
    bgColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.22f) else bgColor
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else color.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

