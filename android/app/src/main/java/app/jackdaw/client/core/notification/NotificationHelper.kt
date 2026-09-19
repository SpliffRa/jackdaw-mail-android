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

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // 1. Incoming Mail Channel
            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_MAIL,
                "Входящие письма Jackdaw",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о поступлении новых входящих писем"
                enableVibration(false)
                setSound(null, null)
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
            }

            notificationManager.createNotificationChannel(incomingChannel)
            notificationManager.createNotificationChannel(slaChannel)
        }
    }

    fun showNewEmailNotification(email: EmailMessage) {
        val soundManager = SoundNotificationManager.getInstance(context)
        if (!soundManager.isNotificationsEnabled) return

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
            .setContentTitle(email.senderName.ifBlank { email.senderEmail })
            .setContentText(email.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${email.subject}\n\n${email.snippet}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(email.id.hashCode(), notification)
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
        if (sla.severity == SlaSeverity.NONE) return

        val title = when (sla.severity) {
            SlaSeverity.BREACHED -> "⚠️ SLA Просрочено!"
            SlaSeverity.URGENT -> "🔴 Срочный SLA дедлайн: ${sla.remainingLabel}"
            SlaSeverity.WARNING -> "🟡 Внимание по SLA: осталось ${sla.remainingLabel}"
            SlaSeverity.NORMAL -> "🟢 На контроле SLA: ${sla.remainingLabel}"
            SlaSeverity.NONE -> return
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
        const val CHANNEL_INCOMING_MAIL = "jackdaw_mail_incoming"
        const val CHANNEL_SLA_ALERTS = "jackdaw_sla_alerts"

        @Volatile
        private var instance: NotificationHelper? = null

        fun getInstance(context: Context): NotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: NotificationHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
