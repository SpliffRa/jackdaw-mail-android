package app.jackdaw.client.core.notification

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.jackdaw.client.R

class SoundNotificationManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jackdaw_sound_prefs", Context.MODE_PRIVATE)

    private var soundPool: SoundPool? = null
    private var chpokSoundId: Int = 0
    @Volatile
    private var isSoundLoaded: Boolean = false

    init {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == chpokSoundId) {
                    isSoundLoaded = true
                }
            }

            chpokSoundId = pool.load(context, R.raw.chpok, 1)
            soundPool = pool
        } catch (_: Exception) {
            soundPool = null
        }
    }

    var isIncomingSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_INCOMING_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_INCOMING_SOUND, value).apply()

    var isSentSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SENT_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SENT_SOUND, value).apply()

    var isSlaSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SLA_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SLA_SOUND, value).apply()

    var isNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    var soundVolume: Float
        get() = prefs.getFloat(KEY_SOUND_VOLUME, 0.85f)
        set(value) = prefs.edit().putFloat(KEY_SOUND_VOLUME, value.coerceIn(0.05f, 1.0f)).apply()

    @Volatile
    private var lastSoundTimestamp = 0L

    fun playIncomingMailSound() {
        if (!isIncomingSoundEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastSoundTimestamp < 250L) return
        lastSoundTimestamp = now

        playSound(volume = soundVolume, rate = 1.0f)
        vibrate(subtleVibe)
    }

    fun playSentMailSound() {
        if (!isSentSoundEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastSoundTimestamp < 250L) return
        lastSoundTimestamp = now

        playSound(volume = (soundVolume * 0.75f).coerceIn(0.05f, 1.0f), rate = 1.25f)
        vibrate(subtleVibe)
    }

    fun playSlaAlertSound() {
        if (!isSlaSoundEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastSoundTimestamp < 300L) return
        lastSoundTimestamp = now

        playSound(volume = (soundVolume * 1.1f).coerceIn(0.05f, 1.0f), rate = 0.9f)
        vibrate(urgentVibe)
    }

    fun playPreviewSound(volume: Float = soundVolume) {
        playSound(volume = volume.coerceIn(0.05f, 1.0f), rate = 1.0f)
    }

    private fun playSound(volume: Float, rate: Float) {
        try {
            val pool = soundPool
            if (pool != null && isSoundLoaded && chpokSoundId != 0) {
                val streamId = pool.play(chpokSoundId, volume, volume, 1, 0, rate)
                if (streamId == 0) {
                    playViaMediaPlayer(volume)
                }
            } else {
                playViaMediaPlayer(volume)
            }
        } catch (_: Exception) {
            playViaMediaPlayer(volume)
        }
    }

    private fun playViaMediaPlayer(volume: Float) {
        try {
            val mp = MediaPlayer.create(context, R.raw.chpok) ?: return
            mp.setOnCompletionListener { it.release() }
            mp.setVolume(volume, volume)
            mp.start()
        } catch (_: Exception) {}
    }

    private fun vibrate(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_INCOMING_SOUND = "incoming_sound_enabled"
        private const val KEY_SENT_SOUND = "sent_sound_enabled"
        private const val KEY_SLA_SOUND = "sla_sound_enabled"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_SOUND_VOLUME = "notification_sound_volume"

        private val subtleVibe = longArrayOf(0, 30)
        private val shortVibe = longArrayOf(0, 50, 40, 60)
        private val urgentVibe = longArrayOf(0, 100, 60, 120)

        @Volatile
        private var instance: SoundNotificationManager? = null

        fun getInstance(context: Context): SoundNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: SoundNotificationManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

