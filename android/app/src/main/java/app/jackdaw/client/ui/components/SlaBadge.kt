package app.jackdaw.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import app.jackdaw.client.core.model.SlaInfo
import app.jackdaw.client.core.model.SlaSeverity

@Composable
fun SlaBadge(
    slaInfo: SlaInfo,
    modifier: Modifier = Modifier
) {
    if (slaInfo.severity == SlaSeverity.NONE) return
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    if (slaInfo.severity == SlaSeverity.COMPLETED) {
        val isLate = slaInfo.remainingLabel.contains("опоздан", ignoreCase = true)
        val bgColor = if (isLate) {
            if (isDark) SlaWarningContainerDark else SlaWarningContainerLight
        } else {
            if (isDark) SlaGoodContainerDark else SlaGoodContainerLight
        }
        val textColor = if (isLate) {
            if (isDark) SlaWarningAmber else Color(0xFFB45309)
        } else {
            if (isDark) SlaGoodGreen else Color(0xFF047857)
        }

        val completedText = if (isLate) {
            "Ответ дан с опозданием"
        } else if (slaInfo.remainingLabel.isBlank() || slaInfo.remainingLabel.contains("мин", ignoreCase = true)) {
            "Ответ дан вовремя"
        } else {
            slaInfo.remainingLabel
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Ответ дан",
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = completedText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
        return
    }

    val (bgColor, textColor, icon) = when (slaInfo.severity) {
        SlaSeverity.BREACHED, SlaSeverity.URGENT -> Triple(
            if (isDark) SlaUrgentContainerDark else SlaUrgentContainerLight,
            if (isDark) SlaUrgentRed else Color(0xFFB91C1C),
            Icons.Rounded.WarningAmber
        )
        SlaSeverity.WARNING -> Triple(
            if (isDark) SlaWarningContainerDark else SlaWarningContainerLight,
            if (isDark) SlaWarningAmber else Color(0xFFB45309),
            Icons.Rounded.AccessTime
        )
        SlaSeverity.NORMAL -> Triple(
            if (isDark) SlaGoodContainerDark else SlaGoodContainerLight,
            if (isDark) SlaGoodGreen else Color(0xFF047857),
            Icons.Rounded.AccessTime
        )
        SlaSeverity.NONE, SlaSeverity.COMPLETED -> return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "SLA Alert",
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "SLA: ${slaInfo.remainingLabel}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun SlaBadge(
    email: EmailMessage,
    modifier: Modifier = Modifier
) {
    val sla = email.slaInfo
    if (sla == null || sla.severity == SlaSeverity.NONE) return

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    if (sla.severity == SlaSeverity.COMPLETED) {
        val isLate = sla.remainingLabel.contains("опоздан", ignoreCase = true)
        val bgColor = if (isLate) {
            if (isDark) SlaWarningContainerDark else SlaWarningContainerLight
        } else {
            if (isDark) SlaGoodContainerDark else SlaGoodContainerLight
        }
        val textColor = if (isLate) {
            if (isDark) SlaWarningAmber else Color(0xFFB45309)
        } else {
            if (isDark) SlaGoodGreen else Color(0xFF047857)
        }

        val completedText = if (isLate) {
            "Ответ дан с опозданием"
        } else if (sla.remainingLabel.isBlank() || sla.remainingLabel.contains("мин", ignoreCase = true)) {
            "Ответ дан вовремя"
        } else {
            sla.remainingLabel
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Ответ дан",
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = completedText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
        return
    }

    val now = androidx.compose.runtime.remember { System.currentTimeMillis() }
    val deadline = if (sla.deadlineTimestamp > 0L) {
        sla.deadlineTimestamp
    } else {
        email.timestamp + 30 * 60 * 1000L
    }
    val remainingMs = deadline - now

    val (bgColor, textColor, icon, label) = when {
        remainingMs <= 0 -> {
            Quad(
                if (isDark) SlaUrgentContainerDark else SlaUrgentContainerLight,
                if (isDark) SlaUrgentRed else Color(0xFFB91C1C),
                Icons.Rounded.WarningAmber,
                "Ответ просрочен"
            )
        }
        remainingMs <= 10 * 60 * 1000L -> {
            val mins = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
            Quad(
                if (isDark) SlaUrgentContainerDark else SlaUrgentContainerLight,
                if (isDark) SlaUrgentRed else Color(0xFFB91C1C),
                Icons.Rounded.WarningAmber,
                "До ответа: $mins мин"
            )
        }
        remainingMs <= 20 * 60 * 1000L -> {
            val mins = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
            Quad(
                if (isDark) SlaWarningContainerDark else SlaWarningContainerLight,
                if (isDark) SlaWarningAmber else Color(0xFFB45309),
                Icons.Rounded.AccessTime,
                "До ответа: $mins мин"
            )
        }
        else -> {
            val mins = (remainingMs / (60 * 1000L)).coerceIn(1, 30)
            Quad(
                if (isDark) SlaGoodContainerDark else SlaGoodContainerLight,
                if (isDark) SlaGoodGreen else Color(0xFF047857),
                Icons.Rounded.AccessTime,
                "До ответа: $mins мин"
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "SLA Alert",
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

