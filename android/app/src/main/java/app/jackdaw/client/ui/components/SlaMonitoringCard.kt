package app.jackdaw.client.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SlaMonitoringCard(
    email: EmailMessage,
    onClick: () -> Unit,
    onToggleStar: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val sla = email.slaInfo
    val severity = sla?.severity ?: SlaSeverity.NORMAL

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val (statusColor, statusBg, statusDotColor) = when (severity) {
        SlaSeverity.BREACHED, SlaSeverity.URGENT -> Triple(
            if (isDark) SlaUrgentRed else Color(0xFFB91C1C),
            if (isDark) SlaUrgentContainerDark else SlaUrgentContainerLight,
            if (isDark) SlaUrgentRed else Color(0xFFDC2626)
        )
        SlaSeverity.WARNING -> Triple(
            if (isDark) SlaWarningAmber else Color(0xFFB45309),
            if (isDark) SlaWarningContainerDark else SlaWarningContainerLight,
            if (isDark) SlaWarningAmber else Color(0xFFD97706)
        )
        SlaSeverity.NORMAL, SlaSeverity.COMPLETED -> Triple(
            if (isDark) SlaGoodGreen else Color(0xFF047857),
            if (isDark) SlaGoodContainerDark else SlaGoodContainerLight,
            if (isDark) SlaGoodGreen else Color(0xFF16A34A)
        )
        SlaSeverity.NONE -> Triple(Color.Gray, MaterialTheme.colorScheme.surfaceVariant, Color.Gray)
    }

    val deadlineText = if (sla != null && sla.deadlineTimestamp > 0) {
        val dateFormat = SimpleDateFormat("HH:mm", Locale("ru"))
        "до " + dateFormat.format(Date(sla.deadlineTimestamp))
    } else {
        ""
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!email.isRead) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Row 1: Status Dot + Subject + Remaining Countdown Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = email.subject,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (sla != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = sla.remainingLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Sender & Deadline & Snippet
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = email.senderName.ifBlank { email.senderEmail },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (deadlineText.isNotBlank()) {
                        Text(
                            text = " • $deadlineText",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (email.hasAttachments) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "Вложения",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { onToggleStar(!email.isStarred) },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (email.isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = "Избранное",
                        tint = if (email.isStarred) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
