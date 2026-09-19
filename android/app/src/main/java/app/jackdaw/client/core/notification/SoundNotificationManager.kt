package app.jackdaw.client.core.notification

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SoundNotificationManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jackdaw_sound_prefs", Context.MODE_PRIVATE)

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (_: Exception) {
            toneGenerator = null
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

    fun playIncomingMailSound() {
        if (!isIncomingSoundEnabled) return
        try {
            val sampleRate = 44100
            val durationMs = 38
            val numSamples = (sampleRate * durationMs) / 1000
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                // Pop effect: frequency sweeps down from 520Hz to 160Hz with fast exponential damping
                val freq = 520.0 - 360.0 * progress
                val t = i.toDouble() / sampleRate
                val envelope = Math.exp(-7.5 * progress) * Math.sin(Math.PI * Math.min(1.0, progress * 6.0))
                val sample = (Math.sin(2.0 * Math.PI * freq * t) * envelope * 8500).toInt()
                buffer[i] = sample.toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.setNotificationMarkerPosition(buffer.size)
            audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onPeriodicNotification(track: AudioTrack?) {}
                override fun onMarkerReached(track: AudioTrack?) {
                    try {
                        track?.stop()
                        track?.release()
                    } catch (_: Exception) {}
                }
            })
            audioTrack.play()
        } catch (_: Exception) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 70)
            } catch (_: Exception) {}
        }
        vibrate(subtleVibe)
    }

    fun playSentMailSound() {
        if (!isSentSoundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        } catch (_: Exception) {}
        vibrate(subtleVibe)
    }

    fun playSlaAlertSound() {
        if (!isSlaSoundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 260)
        } catch (_: Exception) {}
        vibrate(urgentVibe)
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
