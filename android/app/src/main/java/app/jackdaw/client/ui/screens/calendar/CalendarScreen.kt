package app.jackdaw.client.ui.screens.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.model.CalendarEvent
import app.jackdaw.client.core.model.EventRsvpStatus
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.ui.viewmodel.CalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    currentAccount: MailAccount,
    viewModel: CalendarViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.selectedDateEvents.collectAsState()
    val datesWithEvents by viewModel.datesWithEvents.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentAccount.id) {
        viewModel.setAccountId(currentAccount.id)
    }

    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")) }
    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru")) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Новая встреча")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Встреча", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header: Drawer button, Month & Year, "Сегодня" button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Rounded.Menu,
                            contentDescription = "Меню",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedDate.format(monthYearFormatter).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Quick Jump to Today Button
                val isTodaySelected = selectedDate == LocalDate.now()
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isTodaySelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { viewModel.jumpToToday() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Today,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isTodaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Сегодня",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTodaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Horizontal Date Strip (Outlook Style Week Row)
            CalendarDateStrip(
                selectedDate = selectedDate,
                datesWithEvents = datesWithEvents,
                onDateSelected = { viewModel.selectDate(it) }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // Selected Day Title Subheader
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedDate.format(fullDateFormatter).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (events.isEmpty()) "0 встреч" else "${events.size} " + when {
                        events.size % 10 == 1 && events.size % 100 != 11 -> "встреча"
                        events.size % 10 in 2..4 && (events.size % 100 < 10 || events.size % 100 >= 20) -> "встречи"
                        else -> "встреч"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Agenda List of Events
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "На этот день ничего не запланировано",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Запланируйте новую встречу или онлайн-конференцию",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Запланировать встречу")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        CalendarEventCard(
                            event = event,
                            onUpdateRsvp = { status -> viewModel.updateRsvpStatus(event.id, status) },
                            onDelete = { viewModel.deleteEvent(event.id) },
                            onJoinMeeting = { link ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateEditEventDialog(
            initialDate = selectedDate,
            onDismiss = { showCreateDialog = false },
            onSave = { title, desc, loc, link, start, end, allDay, attendees, color ->
                viewModel.saveEvent(
                    title = title,
                    description = desc,
                    location = loc,
                    meetingLink = link,
                    startTimestamp = start,
                    endTimestamp = end,
                    isAllDay = allDay,
                    attendees = attendees,
                    colorHex = color
                )
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun CalendarDateStrip(
    selectedDate: LocalDate,
    datesWithEvents: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit
) {
    // Generate dates: 2 weeks back, 4 weeks forward (6 weeks total for smooth scrolling)
    val today = remember { LocalDate.now() }
    val startDate = remember { today.minusDays(14) }
    val dates = remember { (0..42).map { startDate.plusDays(it.toLong()) } }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        // Scroll roughly to middle
        scrollState.scrollTo(14 * 56)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dates.forEach { date ->
            val isSelected = date == selectedDate
            val isToday = date == today
            val hasEvent = datesWithEvents.contains(date)

            val dayOfWeekName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
                .take(2).uppercase()

            Column(
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onDateSelected(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dayOfWeekName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Event indicator dot
                if (hasEvent) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                } else {
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
fun CalendarEventCard(
    event: CalendarEvent,
    onUpdateRsvp: (EventRsvpStatus) -> Unit,
    onDelete: () -> Unit,
    onJoinMeeting: (String) -> Unit
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale("ru")) }

    val startTimeStr = remember(event.startTimestamp) {
        Instant.ofEpochMilli(event.startTimestamp).atZone(zoneId).format(timeFormatter)
    }
    val endTimeStr = remember(event.endTimestamp) {
        Instant.ofEpochMilli(event.endTimestamp).atZone(zoneId).format(timeFormatter)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent Color Left Stripe
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(if (event.description.isNotBlank() || event.attendees.isNotEmpty()) 170.dp else 120.dp)
                    .background(Color(event.colorHex))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Time & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color(event.colorHex)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (event.isAllDay) "Весь день" else "$startTimeStr – $endTimeStr",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(event.colorHex)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Удалить встречу",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Title
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Location or Meeting Link
                if (event.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Join Meeting Button if link present
                if (!event.meetingLink.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onJoinMeeting(event.meetingLink) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.VideoCall,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Присоединиться к видеовстрече",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Attendees list
                if (event.attendees.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.People,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Участники: ${event.attendees.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Description
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Outlook-style RSVP Actions
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.8.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ответ:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Accept
                    RsvpChip(
                        label = "Принять",
                        icon = Icons.Rounded.Check,
                        isSelected = event.rsvpStatus == EventRsvpStatus.ACCEPTED,
                        activeColor = Color(0xFF10B981),
                        onClick = { onUpdateRsvp(EventRsvpStatus.ACCEPTED) }
                    )

                    // Tentative
                    RsvpChip(
                        label = "Под вопросом",
                        icon = Icons.Rounded.QuestionMark,
                        isSelected = event.rsvpStatus == EventRsvpStatus.TENTATIVE,
                        activeColor = Color(0xFFF59E0B),
                        onClick = { onUpdateRsvp(EventRsvpStatus.TENTATIVE) }
                    )

                    // Decline
                    RsvpChip(
                        label = "Отклонить",
                        icon = Icons.Rounded.Close,
                        isSelected = event.rsvpStatus == EventRsvpStatus.DECLINED,
                        activeColor = Color(0xFFEF4444),
                        onClick = { onUpdateRsvp(EventRsvpStatus.DECLINED) }
                    )
                }
            }
        }
    }
}

@Composable
fun RsvpChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.18f) else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
