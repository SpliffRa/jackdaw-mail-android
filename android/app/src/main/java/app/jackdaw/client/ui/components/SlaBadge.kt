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
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "SLA Alert",
            tint = textColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "SLA: ${slaInfo.remainingLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
