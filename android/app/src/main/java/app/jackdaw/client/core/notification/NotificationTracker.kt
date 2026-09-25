package app.jackdaw.client.core.notification

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks email notification states to prevent duplicate sounds, redundant alerts,
 * and handles "seen but unread" email UX.
 */
class NotificationTracker private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Timestamp (ms) when the user last viewed the mailbox list in the UI.
     * Any email with timestamp <= lastViewedTimestamp is considered already seen by the user,
     * even if left unread (isRead = false).
     */
    var lastViewedTimestamp: Long
        get() {
            val ts = prefs.getLong(KEY_LAST_VIEWED_TS, 0L)
            if (ts == 0L) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_VIEWED_TS, now).apply()
                return now
            }
            return ts
        }
        set(value) = prefs.edit().putLong(KEY_LAST_VIEWED_TS, value).apply()

    fun markViewed(timestamp: Long = System.currentTimeMillis()) {
        lastViewedTimestamp = timestamp
    }

    /**
     * Checks if a notification sound/alert was already emitted for this email.
     */
    fun isNotified(emailId: String): Boolean {
        val set = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()) ?: emptySet()
        return set.contains(emailId)
    }

    /**
     * An email should trigger sound/alert only if:
     * 1. It arrived AFTER the user last viewed the mailbox.
     * 2. It has NOT been notified before.
     */
    fun shouldNotify(emailId: String, emailTimestamp: Long): Boolean {
        if (isNotified(emailId)) return false
        if (emailTimestamp <= lastViewedTimestamp) return false
        return true
    }

    /**
     * Record email IDs that have triggered a notification sound.
     */
    fun markNotified(emailIds: Collection<String>) {
        if (emailIds.isEmpty()) return
        val current = (prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()) ?: emptySet()).toMutableSet()
        current.addAll(emailIds)
        // Keep capped to avoid SharedPreferences bloating
        val bounded = if (current.size > 500) {
            current.toList().takeLast(350).toSet()
        } else {
            current
        }
        prefs.edit().putStringSet(KEY_NOTIFIED_IDS, bounded).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "jackdaw_notification_tracker"
        private const val KEY_LAST_VIEWED_TS = "key_last_viewed_ts"
        private const val KEY_NOTIFIED_IDS = "key_notified_email_ids"

        @Volatile
        private var instance: NotificationTracker? = null

        fun getInstance(context: Context): NotificationTracker {
            return instance ?: synchronized(this) {
                instance ?: NotificationTracker(context.applicationContext).also { instance = it }
            }
        }
    }
}
