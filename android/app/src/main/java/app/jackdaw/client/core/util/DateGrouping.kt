package app.jackdaw.client.core.util

import java.util.Calendar

enum class DateGroup(val title: String) {
    TODAY("СЕГОДНЯ"),
    YESTERDAY("ВЧЕРА"),
    THIS_WEEK("НА ЭТОЙ НЕДЕЛЕ"),
    LAST_WEEK("НА ПРОШЛОЙ НЕДЕЛЕ"),
    EARLIER("РАНЕЕ")
}

object DateGrouping {
    fun getGroup(timestamp: Long): DateGroup {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (timestamp >= todayStart) {
            return DateGroup.TODAY
        }

        val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
        if (timestamp >= yesterdayStart) {
            return DateGroup.YESTERDAY
        }

        val thisWeekStart = todayStart - 6 * 24 * 60 * 60 * 1000L
        if (timestamp >= thisWeekStart) {
            return DateGroup.THIS_WEEK
        }

        val lastWeekStart = todayStart - 13 * 24 * 60 * 60 * 1000L
        if (timestamp >= lastWeekStart) {
            return DateGroup.LAST_WEEK
        }

        return DateGroup.EARLIER
    }
}
