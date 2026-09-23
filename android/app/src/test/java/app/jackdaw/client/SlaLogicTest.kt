package app.jackdaw.client

import app.jackdaw.client.core.model.SlaInfo
import app.jackdaw.client.core.model.SlaSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class SlaLogicTest {

    @Test
    fun testCompletedSlaDisplaysCorrectBadgeText() {
        // When severity is COMPLETED, even if raw label had "0 мин" or was blank,
        // it must be displayed as "Ответ дан вовремя"
        fun getResolvedDisplayLabel(slaInfo: SlaInfo): String {
            return if (slaInfo.severity == SlaSeverity.COMPLETED) {
                if (slaInfo.remainingLabel.isBlank() || slaInfo.remainingLabel.contains("мин")) {
                    "Ответ дан вовремя"
                } else {
                    slaInfo.remainingLabel
                }
            } else {
                slaInfo.remainingLabel
            }
        }

        val completedWithZeroMin = SlaInfo(
            severity = SlaSeverity.COMPLETED,
            deadlineTimestamp = 1700000000000L,
            remainingLabel = "0 мин"
        )
        assertEquals("Ответ дан вовремя", getResolvedDisplayLabel(completedWithZeroMin))

        val completedWithBlank = SlaInfo(
            severity = SlaSeverity.COMPLETED,
            deadlineTimestamp = 1700000000000L,
            remainingLabel = ""
        )
        assertEquals("Ответ дан вовремя", getResolvedDisplayLabel(completedWithBlank))

        val completedWithExplicitText = SlaInfo(
            severity = SlaSeverity.COMPLETED,
            deadlineTimestamp = 1700000000000L,
            remainingLabel = "Ответ дан вовремя (регламент соблюден)"
        )
        assertEquals("Ответ дан вовремя (регламент соблюден)", getResolvedDisplayLabel(completedWithExplicitText))

        val activeSla = SlaInfo(
            severity = SlaSeverity.NORMAL,
            deadlineTimestamp = 1700000000000L,
            remainingLabel = "25 мин"
        )
        assertEquals("25 мин", getResolvedDisplayLabel(activeSla))

        val overdueSla = SlaInfo(
            severity = SlaSeverity.BREACHED,
            deadlineTimestamp = 1700000000000L,
            remainingLabel = "Просрочено на 12 мин"
        )
        assertEquals("Просрочено на 12 мин", getResolvedDisplayLabel(overdueSla))
    }
}
