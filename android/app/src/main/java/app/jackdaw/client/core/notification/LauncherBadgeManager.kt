package app.jackdaw.client.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import app.jackdaw.client.MainActivity
import app.jackdaw.client.core.util.PluralRules

object LauncherBadgeManager {

    private const val TAG = "LauncherBadgeManager"
    private const val BADGE_CHANNEL_ID = "jackdaw_app_badge_channel_v1"
    private const val BADGE_NOTIFICATION_ID = 8888

    /**
     * Устанавливает число непрочитанных сообщений на ярлыке приложения.
     * Если count <= 0, бейдж очищается.
     */
    fun setBadge(context: Context, count: Int) {
        val safeCount = maxOf(0, count)
        Log.d(TAG, "Updating launcher icon badge: $safeCount")

        // 1. Android 8.0+ Native Notification Badging (Pixel, Motorola, Android One, Nothing OS, etc.)
        updateNotificationBadge(context, safeCount)

        // 2. OEM Launchers direct integrations (Samsung, Xiaomi, Huawei, Honor, Sony, Vivo, Oppo)
        applyOemBadges(context, safeCount)
    }

    fun clearBadge(context: Context) {
        setBadge(context, 0)
    }

    private fun updateNotificationBadge(context: Context, count: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BADGE_CHANNEL_ID,
                "Счетчик непрочитанных на ярлыке",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Отображение бейджа с количеством непрочитанных писем на значке приложения"
                setShowBadge(true)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (count > 0) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                BADGE_NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val unreadText = PluralRules.formatUnreadCount(count)
            val builder = NotificationCompat.Builder(context, BADGE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Jackdaw Mail")
                .setContentText(unreadText)
                .setNumber(count)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setOngoing(false)
                .setContentIntent(pendingIntent)

            val notification = builder.build()

            // Xiaomi MIUI notification reflection for badge number
            try {
                val field = notification.javaClass.getDeclaredField("extraNotification")
                val extraNotification = field.get(notification)
                val method = extraNotification.javaClass.getDeclaredMethod("setMessageCount", Int::class.javaPrimitiveType)
                method.invoke(extraNotification, count)
            } catch (_: Throwable) {}

            try {
                notificationManager.notify(BADGE_NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {}
        } else {
            notificationManager.cancel(BADGE_NOTIFICATION_ID)
        }
    }

    private fun applyOemBadges(context: Context, count: Int) {
        val packageName = context.packageName
        val className = MainActivity::class.java.name

        // Samsung One UI / TouchWiz
        try {
            val uri = Uri.parse("content://com.sec.badge/apps")
            val contentValues = ContentValues().apply {
                put("package", packageName)
                put("class", className)
                put("badgecount", count)
            }
            val inserted = context.contentResolver.insert(uri, contentValues)
            if (inserted == null) {
                context.contentResolver.update(uri, contentValues, "package=?", arrayOf(packageName))
            }
        } catch (_: Throwable) {}

        // Huawei / Honor (EMUI / MagicOS)
        try {
            val bundle = Bundle().apply {
                putString("package", packageName)
                putString("class", className)
                putInt("badgenumber", count)
            }
            context.contentResolver.call(
                Uri.parse("content://com.huawei.android.launcher.settings/badge/"),
                "change_badge",
                null,
                bundle
            )
        } catch (_: Throwable) {}

        // Xiaomi / MIUI / HyperOS Broadcast
        try {
            val intent = Intent("android.intent.action.APPLICATION_MESSAGE_UPDATE").apply {
                putExtra("update_components", "$packageName/$className")
                putExtra("extra_update_application_message_text", if (count > 0) count.toString() else "")
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {}

        // Sony Xperia
        try {
            val intent = Intent("com.sonyericsson.home.action.UPDATE_BADGE").apply {
                putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", packageName)
                putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", className)
                putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", count > 0)
                putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", count.toString())
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {}

        // Vivo
        try {
            val intent = Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM").apply {
                putExtra("packageName", packageName)
                putExtra("className", className)
                putExtra("notificationNum", count)
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {}

        // Oppo / ColorOS
        try {
            val bundle = Bundle().apply {
                putInt("app_badge_count", count)
            }
            context.contentResolver.call(
                Uri.parse("content://com.android.badge/badge"),
                "setAppBadgeCount",
                null,
                bundle
            )
        } catch (_: Throwable) {}

        // Apex / Nova / ADW / Asus / LG / Generic Intent
        try {
            val intent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", count)
                putExtra("badge_count_package_name", packageName)
                putExtra("badge_count_class_name", className)
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {}
    }
}
