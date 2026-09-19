package app.jackdaw.client

import android.app.Application
import app.jackdaw.client.data.worker.SyncScheduler

class JackdawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register periodic background sync via WorkManager (15 min interval, battery-friendly)
        SyncScheduler.schedulePeriodicSync(this)
    }
}
