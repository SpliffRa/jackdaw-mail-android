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

    private val recentEmailSubjects = ConcurrentLinkedDeque<Pair<String, String>>()

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

    fun showNewEmailNotification(email: EmailMessage, unreadCount: Int = 1) {
        val soundManager = SoundNotificationManager.getInstance(context)
        if (!soundManager.isNotificationsEnabled) return

        val cleanSubj = email.subject.cleanEmailSubject()
        val cleanSnip = email.snippet.cleanEmailPreview()
        val sender = email.senderName.ifBlank { email.senderEmail }

        recentEmailSubjects.addFirst(sender to cleanSubj)
        while (recentEmailSubjects.size > 8) {
            recentEmailSubjects.removeLast()
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_EMAIL_ID", email.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            email.id.hashCode(),
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_MAIL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(cleanSubj)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$cleanSubj\n\n$cleanSnip"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY_INCOMING_EMAILS)
            .setNumber(unreadCount)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        val summaryLaunchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val summaryPendingIntent = PendingIntent.getActivity(
            context,
            0,
            summaryLaunchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val totalDisplayCount = unreadCount.coerceAtLeast(recentEmailSubjects.size)
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Входящие ($totalDisplayCount)")
            .setSummaryText("Jackdaw Mail")

        recentEmailSubjects.take(6).forEach { (s, sub) ->
            inboxStyle.addLine("$s: $sub")
        }

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_INCOMING_MAIL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Jackdaw Mail")
            .setContentText(if (totalDisplayCount > 1) "$totalDisplayCount новых писем" else "$sender: $cleanSubj")
            .setStyle(inboxStyle)
            .setGroup(GROUP_KEY_INCOMING_EMAILS)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSilent(true)
            .setNumber(totalDisplayCount)
            .setContentIntent(summaryPendingIntent)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(email.id.hashCode(), notification)
            manager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
        } catch (_: SecurityException) {
            // Permission not yet granted
        }

        // Also play audio chime
        soundManager.playIncomingMailSound()
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
