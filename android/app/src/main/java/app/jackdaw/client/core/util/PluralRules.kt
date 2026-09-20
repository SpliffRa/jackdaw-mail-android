package app.jackdaw.client.core.util

import kotlin.math.abs

object PluralRules {
    /**
     * Возвращает числительное с правильной формой русского существительного:
     * pluralize(1, "письмо", "письма", "писем") -> "1 письмо"
     * pluralize(2, "письмо", "письма", "писем") -> "2 письма"
     * pluralize(23, "письмо", "письма", "писем") -> "23 письма"
     * pluralize(10, "письмо", "письма", "писем") -> "10 писем"
     */
    fun pluralize(count: Int, one: String, few: String, many: String): String {
        val absCount = abs(count)
        val mod10 = absCount % 10
        val mod100 = absCount % 100
        val word = when {
            mod100 in 11..19 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
        return "$count $word"
    }

    /**
     * Форматирует количество писем:
     * 1 -> "1 письмо"
     * 2, 3, 4, 22, 23, 24 -> "X письма" (например, "23 письма")
     * 0, 5..20, 25..30 -> "X писем" (например, "10 писем")
     */
    fun formatEmailCount(count: Int): String {
        return pluralize(count, "письмо", "письма", "писем")
    }

    /**
     * Форматирует количество непрочитанных:
     * 1, 21 -> "X непрочитанное"
     * 2, 3, 4, 23 -> "X непрочитанных"
     * 5..20, 10 -> "X непрочитанных"
     */
    fun formatUnreadCount(count: Int): String {
        return pluralize(count, "непрочитанное", "непрочитанных", "непрочитанных")
    }
}
