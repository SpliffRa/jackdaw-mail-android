package app.jackdaw.client.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.jackdaw.client.MainActivity
import app.jackdaw.client.R
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.data.local.JackdawDatabase
import app.jackdaw.client.data.network.OwaProtocolEngine
import app.jackdaw.client.data.repository.OfflineFirstMailRepository
import kotlinx.coroutines.flow.first

class MailSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting Jackdaw MailSyncWorker execution...")

        return try {
            val database = JackdawDatabase.getInstance(context)
            val networkEngine = OwaProtocolEngine()
            val repository = OfflineFirstMailRepository(database, networkEngine)

            // 1. Ensure sample data initialized
            repository.initializeSampleDataIfEmpty()

            val notificationHelper = app.jackdaw.client.core.notification.NotificationHelper.getInstance(context)

            // 2. Perform sync across all accounts
            val accounts = repository.getAccounts().first()
            val allUnmutedEmails = mutableListOf<app.jackdaw.client.core.model.EmailMessage>()
            for (account in accounts) {
                val syncResult = repository.syncAll(account.id)
                Log.d(TAG, "Sync finished for account ${account.email}: new=${syncResult.newMessagesCount}, sent=${syncResult.sentMessagesCount}")
                if (syncResult.newMessagesCount > 0) {
                    val latestEmails = database.emailDao().getRecentEmails(account.id, syncResult.newMessagesCount)
                    for (emailEntity in latestEmails) {
                        val folder = database.folderDao().getFolderById(emailEntity.folderId)
                        if (folder?.isMuted != true) {
                            allUnmutedEmails.add(emailEntity.toDomain(emptyList()))
                        }
                    }
                }
            }

            val totalUnread = repository.getUnmutedUnreadCount().first()
            val unmutedEmails = repository.getUnmutedUnreadEmails(10).first()
            if (totalUnread > 0) {
                val hasNew = allUnmutedEmails.isNotEmpty()
                notificationHelper.updateUnreadNotification(unmutedEmails, totalUnread, playSound = hasNew)
            } else {
                notificationHelper.clearMailNotifications()
            }

            // 3. Synchronize launcher icon badge with actual unmuted unread count
            app.jackdaw.client.core.notification.LauncherBadgeManager.setBadge(context, totalUnread)

            Log.d(TAG, "MailSyncWorker completed successfully")
            SyncScheduler.scheduleNextFastSync(context, 3)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "MailSyncWorker encountered an error", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "MailSyncWorker"
        private const val SLA_NOTIFICATION_ID = 1042
    }
}
