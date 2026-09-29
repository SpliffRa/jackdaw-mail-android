package app.jackdaw.client

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedActivities = 0

            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                isAppInForeground = true
                app.jackdaw.client.core.notification.NotificationTracker.getInstance(this@JackdawApp).markViewed()
                notificationHelper.clearMailNotifications()
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivities = maxOf(0, resumedActivities - 1)
                isAppInForeground = resumedActivities > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Reactive notification updates on any change: new email, mark read, delete, mute toggle
        appScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.getUnmutedUnreadEmails(10),
                repository.getUnmutedUnreadCount()
            ) { emails, count ->
                Pair(emails, count)
            }.collect { (emails, count) ->
                if (!isAppInForeground) {
                    notificationHelper.updateUnreadNotification(emails, count)
                } else if (count == 0) {
                    notificationHelper.clearMailNotifications()
                }
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

        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }
}
