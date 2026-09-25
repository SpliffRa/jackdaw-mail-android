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
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import app.jackdaw.client.core.util.cleanEmailPreview
import app.jackdaw.client.core.util.cleanEmailSubject
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

private val timeFormatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}

private fun formatTimestamp(timestamp: Long): String {
    return timeFormatThreadLocal.get()?.format(Date(timestamp)) ?: ""
}

@Composable
fun EmailCard(
    email: EmailMessage,
    onClick: () -> Unit,
    onToggleStar: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(email.timestamp) { formatTimestamp(email.timestamp) }
    val cleanSubject = remember(email.subject) { email.subject.cleanEmailSubject() }
    val cleanSnippet = remember(email.snippet) { email.snippet.cleanEmailPreview() }
    val initial = remember(email.senderName) { email.senderName.firstOrNull()?.uppercase() ?: "J" }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // 100% opaque solid card background so swipe background never bleeds through
    val cardBgColor = if (isDark) {
        if (!email.isRead) Color(0xFF19202A) else Color(0xFF131820)
    } else {
        if (!email.isRead) Color(0xFFFFFFFF) else Color(0xFFFAFAFB)
    }

    val cardBorder = if (!email.isRead) {
        BorderStroke(1.2.dp, JackdawAmber.copy(alpha = 0.55f))
    } else {
        BorderStroke(1.dp, if (isDark) Color(0xFF222B36) else Color(0xFFE2E8F0))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        border = cardBorder,
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
                    text = cleanSubject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!email.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = cleanSnippet,
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

                    if (email.hasAttachments || email.attachments.isNotEmpty()) {
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
                                tint = JackdawAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            val attCount = email.attachments.size
                            if (attCount > 0) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$attCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
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

@Composable
fun SwipeableEmailCard(
    email: EmailMessage,
    onClick: () -> Unit,
    onToggleStar: (Boolean) -> Unit,
    onSwipeArchive: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var layoutWidth by remember { mutableIntStateOf(0) }
    var wasThresholdReached by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Reset offset when email changes or is recycled
    LaunchedEffect(email.id) {
        offsetX.snapTo(0f)
        wasThresholdReached = false
    }

    val isInArchive = email.folderId.contains("archive", ignoreCase = true)
    val isInTrash = email.folderId.contains("trash", ignoreCase = true)

    val currentOffset = offsetX.value
    val threshold = if (layoutWidth > 0) layoutWidth * 0.5f else Float.MAX_VALUE
    val isThresholdCrossed = abs(currentOffset) >= threshold
    val isSwiping = abs(currentOffset) > 1f
    val isStartToEnd = currentOffset > 0

    val draggableState = rememberDraggableState { delta ->
        val current = offsetX.value
        // Apply elastic rubber-band resistance when dragging beyond container bounds
        val effectiveDelta = if (layoutWidth > 0 && abs(current) > layoutWidth) {
            delta * 0.35f
        } else {
            delta
        }
        val newOffset = current + effectiveDelta
        coroutineScope.launch {
            offsetX.snapTo(newOffset)
        }

        val crossed = abs(newOffset) >= threshold
        if (crossed != wasThresholdReached) {
            wasThresholdReached = crossed
            if (crossed) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val progress = if (threshold > 0f) (abs(currentOffset) / threshold).coerceIn(0f, 1f) else 0f
    val iconScale by animateFloatAsState(
        targetValue = if (isThresholdCrossed) 1.15f else (0.8f + progress * 0.2f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swipeIconScale"
    )

    val color = if (isStartToEnd) {
        if (isInArchive) Color(0xFF3B82F6) else Color(0xFF10B981)
    } else {
        Color(0xFFEF4444)
    }

    val bgAlpha = if (isThresholdCrossed) 1f else (progress * 0.85f).coerceIn(0.2f, 0.85f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onSizeChanged { layoutWidth = it.width }
    ) {
        // 1. Background action layer
        if (isSwiping) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(color.copy(alpha = bgAlpha))
                    .padding(horizontal = 24.dp),
                contentAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (isStartToEnd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                    ) {
                        Icon(
                            imageVector = if (isInArchive) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                            contentDescription = if (isInArchive) "Из архива" else "В архив",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isInArchive) "Из архива" else "В архив",
                            color = Color.White,
                            fontWeight = if (isThresholdCrossed) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = if (isThresholdCrossed) 15.sp else 14.sp
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                    ) {
                        Text(
                            text = if (isInTrash) "Удалить навсегда" else "В корзину",
                            color = Color.White,
                            fontWeight = if (isThresholdCrossed) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = if (isThresholdCrossed) 15.sp else 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isInTrash) Icons.Rounded.DeleteForever else Icons.Rounded.Delete,
                            contentDescription = if (isInTrash) "Удалить навсегда" else "В корзину",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 2. Foreground Card with smooth offset & dragging
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        coroutineScope.launch {
                            if (abs(offsetX.value) >= threshold) {
                                // Completed swipe: smoothly slide off-screen, trigger action, then reset
                                val target = if (offsetX.value > 0) layoutWidth.toFloat() + 120f else -layoutWidth.toFloat() - 120f
                                offsetX.animateTo(
                                    targetValue = target,
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                )
                                if (currentOffset > 0) {
                                    onSwipeArchive()
                                } else {
                                    onSwipeDelete()
                                }
                                offsetX.snapTo(0f)
                            } else {
                                // Cancelled swipe (less than 50% threshold): smooth silk spring return
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                            wasThresholdReached = false
                        }
                    }
                )
        ) {
            EmailCard(
                email = email,
                onClick = onClick,
                onToggleStar = onToggleStar
            )
        }
    }
}


