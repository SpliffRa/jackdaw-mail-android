package app.jackdaw.client

import android.app.Application
import app.jackdaw.client.data.worker.SyncScheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class JackdawApp : Application() {
    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        val notificationHelper = app.jackdaw.client.core.notification.NotificationHelper.getInstance(this)
        val database = app.jackdaw.client.data.local.JackdawDatabase.getInstance(this)
        val repository = app.jackdaw.client.data.repository.OfflineFirstMailRepository(database)

        // Reactive notification updates on any change: new email, mark read, delete, mute toggle
        appScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.getUnmutedUnreadEmails(10),
                repository.getUnmutedUnreadCount()
            ) { emails, count ->
                Pair(emails, count)
            }.collect { (emails, count) ->
                notificationHelper.updateUnreadNotification(emails, count)
                app.jackdaw.client.core.notification.LauncherBadgeManager.setBadge(this@JackdawApp, count)
            }
        }

        // Register periodic background sync via WorkManager (fast 3 min chaining + 15 min fallback)
        SyncScheduler.schedulePeriodicSync(this)

        // Real-time background sync loop while application process is alive (every 60s)
        appScope.launch {
            while (isActive) {
                delay(60_000L)
                try {
                    SyncScheduler.triggerImmediateSync(this@JackdawApp)
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        lateinit var instance: JackdawApp
            private set
    }
}
