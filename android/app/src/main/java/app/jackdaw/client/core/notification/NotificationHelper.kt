package app.jackdaw.client.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.jackdaw.client.MainActivity
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.core.util.cleanEmailPreview
import app.jackdaw.client.core.util.cleanEmailSubject
import java.util.concurrent.ConcurrentLinkedDeque

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private data class NotificationItem(
        val id: String,
        val sender: String,
        val subject: String,
        val snippet: String
    )

    private val recentEmails = ConcurrentLinkedDeque<NotificationItem>()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete legacy channels that might have system notification sounds bound by Android OS
            try {
                notificationManager.deleteNotificationChannel("jackdaw_mail_incoming")
                notificationManager.deleteNotificationChannel("jackdaw_sla_alerts")
                notificationManager.deleteNotificationChannel("jackdaw_mail_incoming_v2")
                notificationManager.deleteNotificationChannel("jackdaw_sla_alerts_v2")
                notificationManager.deleteNotificationChannel("jackdaw_mail_incoming_v3")
                notificationManager.deleteNotificationChannel("jackdaw_sla_alerts_v3")
                notificationManager.deleteNotificationChannel("jackdaw_incoming_v4")
                notificationManager.deleteNotificationChannel("jackdaw_sla_alerts_v4")
                notificationManager.deleteNotificationChannel("jackdaw_incoming_v5")
                notificationManager.deleteNotificationChannel("jackdaw_sla_alerts_v5")
            } catch (_: Exception) {}

            // 1. Incoming Mail Channel - completely silent so only our soft acoustic pop plays
            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_MAIL,
                "Входящие письма Jackdaw",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о поступлении новых входящих писем"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
            }

            // 2. SLA Alerts Channel
            val slaChannel = NotificationChannel(
                CHANNEL_SLA_ALERTS,
                "SLA Мониторинг и дедлайны",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Критические оповещения об истечении 30-минутного регламента ответа"
                enableVibration(true)
                setSound(null, null)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(incomingChannel)
            notificationManager.createNotificationChannel(slaChannel)
        }
    }

    fun showNewEmailsNotification(emails: List<EmailMessage>, totalUnread: Int = 1) {
        val soundManager = SoundNotificationManager.getInstance(context)
        if (!soundManager.isNotificationsEnabled || emails.isEmpty()) return

        for (email in emails) {
            val cleanSubj = email.subject.cleanEmailSubject()
            val cleanSnip = email.snippet.cleanEmailPreview()
            val sender = email.senderName.ifBlank { email.senderEmail }

            recentEmails.removeAll { it.id == email.id }
            recentEmails.addFirst(NotificationItem(email.id, sender, cleanSubj, cleanSnip))
        }

        while (recentEmails.size > 10) {
            recentEmails.removeLast()
        }

        val totalDisplayCount = totalUnread.coerceAtLeast(recentEmails.size)
        val latest = recentEmails.firstOrNull() ?: return

        val notification = if (totalDisplayCount <= 1 || recentEmails.size == 1) {
            // Single email: expandable BigTextStyle showing subject & snippet, clicking opens this email
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("EXTRA_EMAIL_ID", latest.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                latest.id.hashCode(),
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            NotificationCompat.Builder(context, CHANNEL_INCOMING_MAIL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(latest.sender)
                .setContentText(latest.subject)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(latest.sender)
                        .bigText("${latest.subject}\n\n${latest.snippet}")
                        .setSummaryText("Jackdaw Mail")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setNumber(1)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            // Multiple emails: expandable InboxStyle showing list of senders & subjects
            val summaryLaunchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val summaryPendingIntent = PendingIntent.getActivity(
                context,
                0,
                summaryLaunchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("Входящие ($totalDisplayCount)")
                .setSummaryText("Jackdaw Mail")

            recentEmails.take(6).forEach { item ->
                inboxStyle.addLine("${item.sender}: ${item.subject}")
            }

            NotificationCompat.Builder(context, CHANNEL_INCOMING_MAIL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Jackdaw Mail")
                .setContentText("$totalDisplayCount новых писем")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSilent(true)
                .setNumber(totalDisplayCount)
                .setContentIntent(summaryPendingIntent)
                .build()
        }

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(SUMMARY_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission not yet granted
        }

        soundManager.playIncomingMailSound()
    }

    fun showNewEmailNotification(email: EmailMessage, unreadCount: Int = 1) {
        showNewEmailsNotification(listOf(email), unreadCount)
    }

    fun clearMailNotifications() {
        recentEmails.clear()
        try {
            NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID)
        } catch (_: SecurityException) {}
    }

    fun showSlaAlertNotification(email: EmailMessage) {
        val soundManager = SoundNotificationManager.getInstance(context)
        if (!soundManager.isNotificationsEnabled) return

        val sla = email.slaInfo ?: return
        if (sla.severity == SlaSeverity.NONE || sla.severity == SlaSeverity.COMPLETED) return

        val title = when (sla.severity) {
            SlaSeverity.BREACHED -> "⚠️ SLA Просрочено!"
            SlaSeverity.URGENT -> "🔴 Срочный SLA дедлайн: ${sla.remainingLabel}"
            SlaSeverity.WARNING -> "🟡 Внимание по SLA: осталось ${sla.remainingLabel}"
            SlaSeverity.NORMAL -> "🟢 На контроле SLA: ${sla.remainingLabel}"
            SlaSeverity.NONE, SlaSeverity.COMPLETED -> return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_EMAIL_ID", email.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("sla_" + email.id).hashCode(),
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SLA_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("${email.senderName}: ${email.subject}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${email.subject}\nРегламент ответа: 30 минут с момента получения.\nОтправитель: ${email.senderEmail}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(("sla_" + email.id).hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }

        soundManager.playSlaAlertSound()
    }

    companion object {
        const val CHANNEL_INCOMING_MAIL = "jackdaw_incoming_v6"
        const val CHANNEL_SLA_ALERTS = "jackdaw_sla_alerts_v6"
        const val GROUP_KEY_INCOMING_EMAILS = "app.jackdaw.client.EMAILS"
        const val SUMMARY_NOTIFICATION_ID = 99901

        @Volatile
        private var instance: NotificationHelper? = null

        fun getInstance(context: Context): NotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: NotificationHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
