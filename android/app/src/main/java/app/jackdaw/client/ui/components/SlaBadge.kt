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
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jackdaw.client.core.designsystem.theme.SlaGoodContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaGoodGreen
import app.jackdaw.client.core.designsystem.theme.SlaUrgentContainerDark
import app.jackdaw.client.core.designsystem.theme.SlaUrgentRed
import app.jackdaw.client.core.designsystem.theme.SlaWarningAmber
import app.jackdaw.client.core.designsystem.theme.SlaWarningContainerDark
import app.jackdaw.client.core.model.SlaInfo
import app.jackdaw.client.core.model.SlaSeverity

@Composable
fun SlaBadge(
    slaInfo: SlaInfo,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (slaInfo.severity) {
        SlaSeverity.BREACHED, SlaSeverity.URGENT -> Triple(
            SlaUrgentContainerDark,
            SlaUrgentRed,
            Icons.Rounded.WarningAmber
        )
        SlaSeverity.WARNING -> Triple(
            SlaWarningContainerDark,
            SlaWarningAmber,
            Icons.Rounded.AccessTime
        )
        SlaSeverity.NORMAL -> Triple(
            SlaGoodContainerDark,
            SlaGoodGreen,
            Icons.Rounded.AccessTime
        )
        SlaSeverity.NONE -> return
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
    email: app.jackdaw.client.core.model.EmailMessage,
    modifier: Modifier = Modifier
) {
    val sla = email.slaInfo
    if (sla?.severity == SlaSeverity.NONE) return
    val isIncoming = email.folderId.contains("inbox", ignoreCase = true)
    if (sla == null && !isIncoming) return

    val now = androidx.compose.runtime.remember { System.currentTimeMillis() }
    val deadline = if (sla != null && sla.deadlineTimestamp > 0L) {
        sla.deadlineTimestamp
    } else {
        email.timestamp + 30 * 60 * 1000L
    }
    val remainingMs = deadline - now

    val (bgColor, textColor, icon, label) = when {
        remainingMs <= 0 -> {
            Quad(SlaUrgentContainerDark, SlaUrgentRed, Icons.Rounded.WarningAmber, "SLA: Просрочено")
        }
        remainingMs <= 10 * 60 * 1000L -> {
            val mins = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
            Quad(SlaUrgentContainerDark, SlaUrgentRed, Icons.Rounded.WarningAmber, "SLA: $mins мин")
        }
        remainingMs <= 20 * 60 * 1000L -> {
            val mins = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
            Quad(SlaWarningContainerDark, SlaWarningAmber, Icons.Rounded.AccessTime, "SLA: $mins мин")
        }
        else -> {
            val mins = (remainingMs / (60 * 1000L)).coerceIn(1, 30)
            Quad(SlaGoodContainerDark, SlaGoodGreen, Icons.Rounded.AccessTime, "SLA: $mins мин")
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
