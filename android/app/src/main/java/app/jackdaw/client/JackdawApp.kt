package app.jackdaw.client

import android.app.Application
import app.jackdaw.client.data.worker.SyncScheduler

class JackdawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize notification channels and sound system
        app.jackdaw.client.core.notification.NotificationHelper.getInstance(this)
        // Register periodic background sync via WorkManager (15 min interval, battery-friendly)
        SyncScheduler.schedulePeriodicSync(this)
    }

    companion object {
        lateinit var instance: JackdawApp
            private set
    }
}
