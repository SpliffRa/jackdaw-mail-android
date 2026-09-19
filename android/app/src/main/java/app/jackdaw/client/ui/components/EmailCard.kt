package app.jackdaw.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.remember
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.designsystem.theme.JackdawBorderDark
import app.jackdaw.client.core.designsystem.theme.JackdawSurfaceDark
import app.jackdaw.client.core.designsystem.theme.JackdawSurfaceElevatedDark
import app.jackdaw.client.core.designsystem.theme.SlaGoodContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaGoodGreen
import app.jackdaw.client.core.designsystem.theme.SlaUrgentContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaUrgentRed
import app.jackdaw.client.core.designsystem.theme.SlaWarningAmber
import app.jackdaw.client.core.designsystem.theme.SlaWarningContainerDark
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.core.model.EmailMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmailCard(
    email: EmailMessage,
    onClick: () -> Unit,
    onToggleStar: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(email.timestamp))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!email.isRead) JackdawSurfaceElevatedDark else JackdawSurfaceDark
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!email.isRead) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Unread accent indicator / Avatar with dot
            Box(
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (!email.isRead) JackdawAmber else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = email.senderName.firstOrNull()?.uppercase() ?: "J"
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (!email.isRead) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!email.isRead) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(JackdawAmber)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!email.isRead) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(JackdawAmber)
                            )
                        }
                        Text(
                            text = email.senderName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!email.isRead) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = email.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!email.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = email.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                // 30-min SLA countdown timer under email in Inbox
                SlaDeadlineTimer(
                    email = email,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom chips row (attachments, related emails thread count)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    if (email.hasAttachments) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = "Вложения",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${email.attachments.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (email.relatedEmailsCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = "Связанная цепочка",
                                tint = JackdawAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${email.relatedEmailsCount + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = JackdawAmber
                            )
                        }
                    }

                    if (email.deliveryStatus != app.jackdaw.client.core.model.DeliveryStatus.SENT) {
                        val (statusText, statusBg, statusColor) = when (email.deliveryStatus) {
                            app.jackdaw.client.core.model.DeliveryStatus.QUEUED ->
                                Triple("В очереди", JackdawAmber.copy(alpha = 0.2f), JackdawAmber)
                            app.jackdaw.client.core.model.DeliveryStatus.SENDING ->
                                Triple("Отправка...", Color(0xFF3B82F6).copy(alpha = 0.2f), Color(0xFF60A5FA))
                            app.jackdaw.client.core.model.DeliveryStatus.FAILED ->
                                Triple("Сбой", Color(0xFFEF4444).copy(alpha = 0.2f), Color(0xFFEF4444))
                            else -> Triple("Черновик", Color.Gray.copy(alpha = 0.2f), Color.LightGray)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { onToggleStar(!email.isStarred) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (email.isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Избранное",
                            tint = if (email.isStarred) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableEmailCard(
    email: EmailMessage,
    onClick: () -> Unit,
    onToggleStar: (Boolean) -> Unit,
    onSwipeArchive: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.65f },
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeArchive()
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeDelete()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF10B981) // Emerald Green
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFEF4444) // Red
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }

            val progress = dismissState.progress
            val iconScale = (0.75f + progress * 0.4f).coerceIn(0.75f, 1.15f)
            val bgAlpha = (progress * 1.5f).coerceIn(0.35f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = bgAlpha))
                    .padding(horizontal = 24.dp),
                contentAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (isStartToEnd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = "Архив",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("В архив", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                    ) {
                        Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Удалить",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        content = {
            EmailCard(
                email = email,
                onClick = onClick,
                onToggleStar = onToggleStar
            )
        }
    )
}

private data class SlaTimerUi(
    val bgColor: Color,
    val textColor: Color,
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun SlaDeadlineTimer(email: EmailMessage, modifier: Modifier = Modifier) {
    val sla = email.slaInfo
    // If explicitly marked NONE, SLA was completed/fulfilled
    if (sla?.severity == SlaSeverity.NONE) return
    // Only show if email has SLA or is incoming in inbox (not sent, not trash, not drafts)
    val isIncomingInbox = email.folderId.contains("inbox", ignoreCase = true)
    if (sla == null && !isIncomingInbox) return

    val now = remember { System.currentTimeMillis() }
    val deadline = if (sla != null && sla.deadlineTimestamp > 0L) {
        sla.deadlineTimestamp
    } else {
        email.timestamp + 30 * 60 * 1000L
    }
    val remainingMs = deadline - now
    val deadlineTime = remember(deadline) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(deadline))
    }

    val timerUi = when {
        remainingMs <= 0 -> {
            val overdueMins = (Math.abs(remainingMs) / (60 * 1000L)).coerceAtLeast(1)
            SlaTimerUi(
                bgColor = SlaUrgentContainerDark,
                textColor = SlaUrgentRed,
                text = "Регламент ответа (30 мин) просрочен на $overdueMins мин",
                icon = Icons.Rounded.WarningAmber
            )
        }
        remainingMs <= 10 * 60 * 1000L -> {
            val mins = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
            SlaTimerUi(
                bgColor = SlaUrgentContainerDark,
                textColor = SlaUrgentRed,
                text = "Срочно: до конца ответа осталось $mins мин (до $deadlineTime)",
                icon = Icons.Rounded.WarningAmber
            )
        }
        remainingMs <= 20 * 60 * 1000L -> {
            val mins = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
            SlaTimerUi(
                bgColor = SlaWarningContainerDark,
                textColor = SlaWarningAmber,
                text = "Внимание: до конца ответа осталось $mins мин (до $deadlineTime)",
                icon = Icons.Rounded.AccessTime
            )
        }
        else -> {
            val mins = (remainingMs / (60 * 1000L)).coerceIn(1, 30)
            SlaTimerUi(
                bgColor = SlaGoodContainerDark,
                textColor = SlaGoodGreen,
                text = "SLA 30 мин: до конца ответа осталось $mins мин (до $deadlineTime)",
                icon = Icons.Rounded.AccessTime
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(timerUi.bgColor)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = timerUi.icon,
            contentDescription = null,
            tint = timerUi.textColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = timerUi.text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = timerUi.textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


