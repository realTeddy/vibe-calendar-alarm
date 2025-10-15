package me.tewodros.vibecalendaralarm.wear

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Full-screen reminder activity for Wear OS devices.
 * Displays un-missable alerts with gradual audio fade-in and vibration.
 * Optimized for small watch screens with simple, touch-friendly interface.
 */
@AndroidEntryPoint
class WearReminderActivity : ComponentActivity() {

    @Inject
    lateinit var wearAlarmManager: WearAlarmManager

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var maxVolumeForAlarm: Float = 1.0f

    // Volume fade-in control - Gradual 20-second fade for watch
    private val volumeFadeHandler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null
    private val fadeInDurationMs = 20000L // 20 seconds (shorter than phone)
    private val fadeStepMs = 250L // Update every 250ms

    private var eventId: Long = -1
    private var eventTitle: String = ""
    private var eventStartTime: Long = 0

    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissDelayMs = 120000L // 2 minutes

    private lateinit var messageClient: MessageClient

    private val TAG = "WearReminderActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 WearReminderActivity onCreate()")

        // Initialize Wearable Message Client for phone communication
        messageClient = Wearable.getMessageClient(this)

        // Extract event data from intent
        eventId = intent.getLongExtra("event_id", -1)
        eventTitle = intent.getStringExtra("event_title") ?: "Calendar Event"
        eventStartTime = intent.getLongExtra("event_start_time", 0)

        Log.d(TAG, "📅 Event: $eventTitle (ID: $eventId)")

        // Create UI optimized for watch
        createWearLayout()

        // Setup full-screen and lock screen display
        setupWearFullScreen()

        // Initialize audio and vibration
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupVibration()

        // Start audio with gradual fade-in
        startGradualAudio()

        // Start vibration pattern
        startVibration()

        // Auto-dismiss after 2 minutes
        scheduleAutoDismiss()
    }

    /**
     * Create simple, touch-friendly layout for Wear OS
     */
    private fun createWearLayout() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#000000"))
        }

        // Event title
        val titleView = TextView(this).apply {
            text = eventTitle
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 20, 0, 10)
            gravity = android.view.Gravity.CENTER
        }

        // Event time
        val timeView = TextView(this).apply {
            text = formatEventTime(eventStartTime)
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#B3FFFFFF"))
            setPadding(0, 0, 0, 30)
            gravity = android.view.Gravity.CENTER
        }

        // Dismiss button (large for easy touch)
        val dismissButton = Button(this).apply {
            text = "DISMISS"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(20, 15, 20, 15)
            setOnClickListener { dismissReminder() }
        }

        // Snooze button
        val snoozeButton = Button(this).apply {
            text = "SNOOZE 5 MIN"
            textSize = 14f
            setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(20, 15, 20, 15)
            setOnClickListener { snoozeReminder(5) }
        }

        layout.addView(titleView)
        layout.addView(timeView)
        layout.addView(dismissButton)
        layout.addView(snoozeButton)

        setContentView(layout)
    }

    /**
     * Setup full-screen display for Wear OS
     */
    private fun setupWearFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Keep screen on while reminder is showing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Setup vibration system
     */
    private fun setupVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Start continuous vibration pattern
     */
    private fun startVibration() {
        try {
            // Vibration pattern: vibrate for 500ms, pause for 1000ms, repeat
            val pattern = longArrayOf(0, 500, 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d(TAG, "📳 Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Vibration failed: ${e.message}")
        }
    }

    /**
     * Start audio with gradual volume increase
     */
    private fun startGradualAudio() {
        try {
            // Get current alarm volume
            audioManager?.let { am ->
                val currentVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                maxVolumeForAlarm = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0.5f
            }

            // Get default alarm sound
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, alarmUri)
                isLooping = true
                setVolume(0.01f, 0.01f) // Start at 1%
                prepare()
                start()
            }

            // Start gradual volume increase
            startVolumeGradualIncrease()

            Log.d(TAG, "🔊 Audio started with gradual fade-in")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start audio: ${e.message}")
        }
    }

    /**
     * Gradually increase volume over 20 seconds (exponential curve)
     */
    private fun startVolumeGradualIncrease() {
        val startTime = System.currentTimeMillis()

        volumeFadeRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < fadeInDurationMs) {
                    // Exponential curve for more natural fade
                    val progress = elapsed.toFloat() / fadeInDurationMs
                    val exponentialProgress = Math.pow(progress.toDouble(), 2.0).toFloat()
                    val targetVolume = 0.01f + (maxVolumeForAlarm - 0.01f) * exponentialProgress

                    mediaPlayer?.setVolume(targetVolume, targetVolume)
                    volumeFadeHandler.postDelayed(this, fadeStepMs)
                } else {
                    // Reached max volume
                    mediaPlayer?.setVolume(maxVolumeForAlarm, maxVolumeForAlarm)
                }
            }
        }
        volumeFadeHandler.post(volumeFadeRunnable!!)
    }

    /**
     * Dismiss the reminder
     */
    private fun dismissReminder() {
        Log.d(TAG, "✅ Reminder dismissed by user")

        // Notify phone that reminder was dismissed on watch
        notifyPhoneOfDismissal()

        stopAudioAndVibration()
        finish()
    }

    /**
     * Snooze the reminder
     */
    private fun snoozeReminder(minutes: Int) {
        Log.d(TAG, "⏰ Reminder snoozed for $minutes minutes")

        val snoozeTime = System.currentTimeMillis() + (minutes * 60 * 1000)
        wearAlarmManager.scheduleReminder(
            eventId,
            eventTitle,
            eventStartTime,
            snoozeTime
        )

        // Notify phone about snooze
        notifyPhoneOfSnooze(minutes)

        stopAudioAndVibration()
        finish()
    }

    /**
     * Send message to phone that reminder was dismissed on watch
     */
    private fun notifyPhoneOfDismissal() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@WearReminderActivity)
                    .connectedNodes
                    .await()

                val message = "dismiss:$eventId".toByteArray()

                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/reminder_dismissed",
                        message
                    ).await()
                    Log.d(TAG, "📤 Sent dismissal message to phone")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to notify phone: ${e.message}")
            }
        }
    }

    /**
     * Send snooze notification to phone
     */
    private fun notifyPhoneOfSnooze(minutes: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@WearReminderActivity)
                    .connectedNodes
                    .await()

                val message = "snooze:$eventId:$minutes".toByteArray()

                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/reminder_snoozed",
                        message
                    ).await()
                    Log.d(TAG, "📤 Sent snooze message to phone")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to notify phone: ${e.message}")
            }
        }
    }

    /**
     * Stop audio and vibration
     */
    private fun stopAudioAndVibration() {
        volumeFadeHandler.removeCallbacks(volumeFadeRunnable!!)

        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()

        Log.d(TAG, "🔇 Audio and vibration stopped")
    }

    /**
     * Schedule auto-dismiss after 2 minutes
     */
    private fun scheduleAutoDismiss() {
        autoDismissHandler.postDelayed({
            Log.d(TAG, "⏱️ Auto-dismissing after timeout")
            dismissReminder()
        }, autoDismissDelayMs)
    }

    /**
     * Format event time for display
     */
    private fun formatEventTime(timeMs: Long): String {
        val formatter = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
        return formatter.format(Date(timeMs))
    }

    override fun onDestroy() {
        super.onDestroy()
        autoDismissHandler.removeCallbacksAndMessages(null)
        stopAudioAndVibration()
        Log.d(TAG, "🛑 WearReminderActivity destroyed")
    }

    override fun onBackPressed() {
        // Prevent dismissal via back button - must use dismiss button
        Log.d(TAG, "⚠️ Back button pressed - ignored (use Dismiss button)")
    }
}
