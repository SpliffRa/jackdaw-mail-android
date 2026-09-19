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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!email.isRead) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Unread accent indicator / Avatar with dot
            Box(
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                            .size(9.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(JackdawAmber)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

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
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(JackdawAmber)
                            )
                        }
                        Text(
                            text = email.senderName,
                            style = MaterialTheme.typography.titleSmall,
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

                Spacer(modifier = Modifier.height(1.dp))

                Text(
                    text = email.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!email.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = email.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom chips row (SLA pill, attachments, related emails thread count, delivery status, star)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Inline compact SLA pill (e.g. 🕒 SLA: 25 мин)
                    SlaBadge(email = email)

                    if (email.hasAttachments) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = "Вложения",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
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
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = "Связанная цепочка",
                                tint = JackdawAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
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
                                .padding(horizontal = 5.dp, vertical = 2.dp)
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
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = if (email.isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Избранное",
                            tint = if (email.isStarred) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
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
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
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


