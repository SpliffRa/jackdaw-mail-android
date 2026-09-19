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
import app.jackdaw.client.data.network.MockNetworkSyncEngine
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
            val networkEngine = MockNetworkSyncEngine()
            val repository = OfflineFirstMailRepository(database, networkEngine)

            // 1. Ensure sample data initialized
            repository.initializeSampleDataIfEmpty()

            // 2. Perform sync across all accounts
            val accounts = repository.getAccounts().first()
            for (account in accounts) {
                val syncResult = repository.syncAll(account.id)
                Log.d(TAG, "Sync finished for account ${account.email}: new=${syncResult.newMessagesCount}, sent=${syncResult.sentMessagesCount}")
            }

            // 3. Check for urgent SLA emails and trigger system notification if necessary
            checkAndNotifyUrgentSla(database)

            Log.d(TAG, "MailSyncWorker completed successfully")
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

    private suspend fun checkAndNotifyUrgentSla(database: JackdawDatabase) {
        val urgentEmails = database.emailDao().getUrgentSlaEmails()
        if (urgentEmails.isEmpty()) return

        val mostUrgent = urgentEmails.firstOrNull { it.slaSeverity == SlaSeverity.URGENT } ?: urgentEmails.first()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "jackdaw_sla_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SLA Дедлайны Jackdaw",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Предупреждения об истечении сроков SLA по важным письмам"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SLA Дедлайн: ${mostUrgent.slaRemainingLabel} осталось!")
            .setContentText("${mostUrgent.senderName}: ${mostUrgent.subject}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(mostUrgent.snippet))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(SLA_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "MailSyncWorker"
        private const val SLA_NOTIFICATION_ID = 1042
    }
}
