package me.tewodros.vibecalendaralarm.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import me.tewodros.vibecalendaralarm.data.Constants

/**
 * Manages audio fade-in and vibration effects for alarm reminders
 * Extracts audio logic from ReminderActivity for better separation of concerns
 */
class AudioFadeManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var maxVolumeForAlarm: Float = 1.0f

    // Volume fade-in control
    private val volumeFadeHandler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null

    // Configurable fade parameters
    private val fadeInDurationMs = Constants.AUDIO_FADE_DURATION_SECONDS * 1000L
    private val fadeStepMs = 300L // Update every 300ms

    /**
     * State listener for audio events
     */
    interface AudioStateListener {
        fun onAudioStarted()
        fun onAudioStopped()
        fun onAudioError(error: String)
    }

    private var listener: AudioStateListener? = null

    fun setListener(listener: AudioStateListener?) {
        this.listener = listener
    }

    /**
     * Initialize audio and vibration systems
     */
    fun initialize() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Calculate max volume based on system alarm volume
        val currentAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 0
        val maxAlarmVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 1
        maxVolumeForAlarm = if (maxAlarmVolume > 0) {
            currentAlarmVolume.toFloat() / maxAlarmVolume.toFloat()
        } else {
            1.0f
        }

        Log.d(
            TAG,
            "System alarm volume: $currentAlarmVolume/$maxAlarmVolume (max will be ${(maxVolumeForAlarm * 100).toInt()}%)",
        )

        // Initialize vibrator
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Start alarm sound with gradual volume fade-in
     */
    fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true

                // Start with very low volume
                setVolume(Constants.AUDIO_FADE_START_VOLUME, Constants.AUDIO_FADE_START_VOLUME)

                prepare()
                start()

                // Start gradual volume increase
                startVolumeGradualIncrease()
            }

            listener?.onAudioStarted()
            Log.d(TAG, "Alarm sound started with fade-in")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound: ${e.message}")
            listener?.onAudioError("Failed to play alarm: ${e.message}")
        }
    }

    /**
     * Start vibration pattern
     */
    fun startVibration() {
        vibrator?.let { vib ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                val vibrationEffect = VibrationEffect.createWaveform(pattern, amplitudes, 0)
                vib.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                vib.vibrate(pattern, 0)
            }
            Log.d(TAG, "Vibration started")
        }
    }

    /**
     * Start both alarm sound and vibration
     */
    fun startAlarmEffects() {
        initialize()
        startVibration()
        startAlarmSound()
    }

    /**
     * Stop all alarm effects
     */
    fun stopAlarmEffects() {
        // Stop volume fade-in
        volumeFadeRunnable?.let { volumeFadeHandler.removeCallbacks(it) }
        volumeFadeRunnable = null

        // Stop vibration
        vibrator?.cancel()

        // Stop and release media player
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping media player: ${e.message}")
            }
        }
        mediaPlayer = null

        listener?.onAudioStopped()
        Log.d(TAG, "Alarm effects stopped")
    }

    /**
     * Release all resources - call in onDestroy
     */
    fun release() {
        stopAlarmEffects()
        vibrator = null
        audioManager = null
        listener = null
    }

    /**
     * Check if alarm sound is currently playing
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    /**
     * Gradually increase volume using exponential curve
     */
    private fun startVolumeGradualIncrease() {
        val totalSteps = (fadeInDurationMs / fadeStepMs).toInt()
        var currentStep = 0

        volumeFadeRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        currentStep++

                        // Calculate volume with exponential curve for gradual start
                        val progress = currentStep.toFloat() / totalSteps.toFloat()

                        // Quadratic curve: starts very slowly, accelerates later
                        val curvedProgress = progress * progress

                        // Scale from start volume to max alarm volume
                        val volume = Constants.AUDIO_FADE_START_VOLUME +
                            ((maxVolumeForAlarm - Constants.AUDIO_FADE_START_VOLUME) * curvedProgress)

                        player.setVolume(volume, volume)

                        // Continue if not at max
                        if (currentStep < totalSteps && player.isPlaying) {
                            volumeFadeHandler.postDelayed(this, fadeStepMs)
                        } else {
                            Log.d(TAG, "Volume fade-in complete at ${(volume * 100).toInt()}%")
                        }
                    }
                }
            }
        }

        volumeFadeHandler.postDelayed(volumeFadeRunnable!!, fadeStepMs)
        Log.d(
            TAG,
            "Started ${Constants.AUDIO_FADE_DURATION_SECONDS}s fade-in to " +
                "${(maxVolumeForAlarm * 100).toInt()}%",
        )
    }

    companion object {
        private const val TAG = "AudioFadeManager"
    }
}
