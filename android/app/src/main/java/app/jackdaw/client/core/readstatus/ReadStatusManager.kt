package app.jackdaw.client.core.readstatus

import android.content.Context
import android.content.SharedPreferences

enum class MarkAsReadMode(val id: String, val title: String, val description: String) {
    IMMEDIATELY(
        id = "immediately",
        title = "Сразу при открытии",
        description = "Мгновенно помечать прочитанным при нажатии на письмо"
    ),
    AFTER_DELAY(
        id = "after_delay",
        title = "С задержкой по времени",
        description = "Помечать после просмотра в течение заданного времени"
    ),
    ON_REPLY(
        id = "on_reply",
        title = "Только после ответа",
        description = "Оставлять непрочитанным до отправки ответа"
    ),
    MANUAL(
        id = "manual",
        title = "Вручную (не помечать)",
        description = "Не помечать при просмотре (только вручную по кнопке)"
    );

    companion object {
        fun fromId(id: String): MarkAsReadMode =
            entries.find { it.id == id } ?: IMMEDIATELY
    }
}

class ReadStatusManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jackdaw_read_status_prefs", Context.MODE_PRIVATE)

    var mode: MarkAsReadMode
        get() = MarkAsReadMode.fromId(
            prefs.getString(KEY_MODE, MarkAsReadMode.IMMEDIATELY.id) ?: MarkAsReadMode.IMMEDIATELY.id
        )
        set(value) = prefs.edit().putString(KEY_MODE, value.id).apply()

    var delaySeconds: Int
        get() = prefs.getInt(KEY_DELAY_SECONDS, 5)
        set(value) = prefs.edit().putInt(KEY_DELAY_SECONDS, value).apply()

    companion object {
        private const val KEY_MODE = "mark_as_read_mode"
        private const val KEY_DELAY_SECONDS = "mark_as_read_delay_seconds"

        @Volatile
        private var instance: ReadStatusManager? = null

        fun getInstance(context: Context): ReadStatusManager {
            return instance ?: synchronized(this) {
                instance ?: ReadStatusManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
