package app.jackdaw.client.ui.screens.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val EVENT_COLORS = listOf(
    0xFF2563EBL, // Outlook Corporate Blue
    0xFF10B981L, // Emerald Green
    0xFFF59E0BL, // Jackdaw Amber
    0xFFEF4444L, // Crimson Red
    0xFF8B5CF6L, // Violet
    0xFF06B6D4L  // Cyan
)

@Composable
fun CreateEditEventDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        location: String,
        meetingLink: String?,
        startTimestamp: Long,
        endTimestamp: Long,
        isAllDay: Boolean,
        attendees: List<String>,
        colorHex: Long
    ) -> Unit
) {
    val context = LocalContext.current
    val zoneId = ZoneId.systemDefault()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var meetingLink by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(false) }
    var attendeesText by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(EVENT_COLORS[0]) }

    var startDate by remember { mutableStateOf(initialDate) }
    var startTime by remember {
        val now = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        mutableStateOf(now)
    }
    var endDate by remember { mutableStateOf(initialDate) }
    var endTime by remember {
        val nowEnd = LocalTime.now().plusHours(2).withMinute(0).withSecond(0).withNano(0)
        mutableStateOf(nowEnd)
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru")) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale("ru")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Новая встреча",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Тема встречи") },
                    placeholder = { Text("Например: Синхронизация по проекту") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Title, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // All day switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Весь день",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = { isAllDay = it },
                        colors = app.jackdaw.client.core.designsystem.theme.jackdawSwitchColors()
                    )
                }

                // Start date / time
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Начало",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date picker button
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d -> startDate = LocalDate.of(y, m + 1, d) },
                                        startDate.year,
                                        startDate.monthValue - 1,
                                        startDate.dayOfMonth
                                    ).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(startDate.format(dateFormatter), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }

                        if (!isAllDay) {
                            // Time picker button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        TimePickerDialog(
                                            context,
                                            { _, h, min -> startTime = LocalTime.of(h, min) },
                                            startTime.hour,
                                            startTime.minute,
                                            true
                                        ).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(startTime.format(timeFormatter), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // End date / time
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Окончание",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // End date picker button
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d -> endDate = LocalDate.of(y, m + 1, d) },
                                        endDate.year,
                                        endDate.monthValue - 1,
                                        endDate.dayOfMonth
                                    ).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(endDate.format(dateFormatter), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }

                        if (!isAllDay) {
                            // End time picker button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        TimePickerDialog(
                                            context,
                                            { _, h, min -> endTime = LocalTime.of(h, min) },
                                            endTime.hour,
                                            endTime.minute,
                                            true
                                        ).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(endTime.format(timeFormatter), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Место проведения") },
                    placeholder = { Text("Переговорная №2 / Офис") },
                    leadingIcon = {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Online meeting link
                OutlinedTextField(
                    value = meetingLink,
                    onValueChange = { meetingLink = it },
                    label = { Text("Ссылка на видеовстречу") },
                    placeholder = { Text("https://teams.microsoft.com/... или Zoom") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Participants / Attendees
                OutlinedTextField(
                    value = attendeesText,
                    onValueChange = { attendeesText = it },
                    label = { Text("Участники (email через запятую)") },
                    placeholder = { Text("colleague@company.com, boss@company.com") },
                    leadingIcon = {
                        Icon(Icons.Rounded.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Повестка / описание встречи") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4
                )

                // Color Tag Picker
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Категория / Цвет",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EVENT_COLORS.forEach { colorVal ->
                            val isSelected = selectedColor == colorVal
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .clickable { selectedColor = colorVal },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button

                    val startDt = if (isAllDay) {
                        startDate.atStartOfDay()
                    } else {
                        LocalDateTime.of(startDate, startTime)
                    }

                    val endDt = if (isAllDay) {
                        endDate.atTime(23, 59, 59)
                    } else {
                        LocalDateTime.of(endDate, endTime)
                    }

                    val startMillis = startDt.atZone(zoneId).toInstant().toEpochMilli()
                    val endMillis = endDt.atZone(zoneId).toInstant().toEpochMilli()

                    val attendeesList = attendeesText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    onSave(
                        title.trim(),
                        description.trim(),
                        location.trim(),
                        meetingLink.trim().takeIf { it.isNotBlank() },
                        startMillis,
                        if (endMillis >= startMillis) endMillis else startMillis + 3600000L,
                        isAllDay,
                        attendeesList,
                        selectedColor
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Запланировать", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
