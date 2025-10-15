package me.tewodros.vibecalendaralarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.launch
import me.tewodros.vibecalendaralarm.repository.CalendarRepository
import me.tewodros.vibecalendaralarm.viewmodel.ReminderViewModel
import me.tewodros.vibecalendaralarm.PendingAlarmsManager

/**
 * ReminderActivity displays full-screen alarm notifications for calendar events.
 * * This activity provides an immersive alarm experience with:
 * - Full-screen display that appears over the lock screen
 * - Gradual volume increase (30-second exponential fade-in from 1% to system alarm volume)
 * - Vibration patterns for silent devices
 * - Multiple interaction options: Dismiss, Snooze (5min), Acknowledge
 * - Auto-dismiss after 2 minutes if not interacted with
 * - MVVM architecture with Hilt dependency injection
 * * Audio Features:
 * - Uses system default alarm sound
 * - Gradual volume fade-in to avoid jarring wake-ups
 * - Respects system alarm volume (max volume = current alarm volume setting)
 * - Stops audio immediately on any user interaction
 * * UI Features:
 * - Hide system bars for immersive experience
 * - Large, clear event information display
 * - Accessible button controls
 * - Event time and details prominently shown
 * * @see ReminderViewModel for business logic
 * @see CalendarRepository for data access
 */
@AndroidEntryPoint
class ReminderActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReminderActivity"
    }

    @Inject
    lateinit var calendarRepository: CalendarRepository

    private lateinit var viewModel: ReminderViewModel

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var maxVolumeForAlarm: Float = 1.0f // Will be calculated based on system alarm volume

    // Volume fade-in control - Very gradual 30-second fade
    private val volumeFadeHandler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null
    private val fadeInDurationMs = 30000L // 30 seconds to reach full volume (much more gradual)
    private val fadeStepMs = 300L // Update volume every 300ms (100 steps over 30 seconds)

    // Track if user has interacted with the alarm
    private var userHasInteracted = false

    // Container for event cards
    private var eventsContainer: LinearLayout? = null
    private var headerText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "🚀 ReminderActivity onCreate() called")
        super.onCreate(savedInstanceState)

        try {
            // Register with PendingAlarmsManager
            PendingAlarmsManager.registerActivityCallback { alarms ->
                runOnUiThread {
                    updateEventsList(alarms)
                }
            }

            // Create simple layout programmatically (no XML needed for MVP)
            Log.d(TAG, "Creating layout...")
            createLayout()

            // Modern approach for full-screen and lock screen presentation (after content view is set)
            Log.d(TAG, "Setting up full screen...")
            setupModernFullScreen()

            // Initialize components
            Log.d(TAG, "Setting up alarm features...")
            setupAlarmFeatures()

            // Start alarm sound and vibration
            Log.d(TAG, "Starting alarm effects...")
            startAlarmEffects()

            // Initial display of events
            Log.d(TAG, "Getting pending alarms...")
            val pendingAlarms = PendingAlarmsManager.getAllAlarms()
            Log.d(TAG, "Found ${pendingAlarms.size} pending alarms")
            updateEventsList(pendingAlarms)

            Log.d(TAG, "✅ ReminderActivity onCreate() completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate: ${e.message}", e)
            e.printStackTrace()
            // Don't finish - try to show something
        }
    }

    /**
     * Setup modern full-screen presentation for Android 14+
     */
    private fun setupModernFullScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Modern approach for Android 11+ (API 30+)
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
                )
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            // Modern lock screen and wake-up flags
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                )
            }
        } else {
            // Fallback for older Android versions
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    /**
     * Setup alarm-like features (sound and vibration)
     */
    private fun setupAlarmFeatures() {
        // Initialize vibrator
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Start alarm sound and vibration with modern APIs
     */
    private fun startAlarmEffects() {
        // Initialize AudioManager to get system alarm volume
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Calculate max volume based on system alarm volume
        val currentAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 0
        val maxAlarmVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 1
        maxVolumeForAlarm = if (maxAlarmVolume > 0) {
            currentAlarmVolume.toFloat() / maxAlarmVolume.toFloat()
        } else {
            1.0f
        }

        Log.d("ReminderActivity", "System alarm volume: $currentAlarmVolume/$maxAlarmVolume (max will be ${(maxVolumeForAlarm * 100).toInt()}%)")

        // Modern vibration with better performance
        vibrator?.let { vib ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Use modern VibrationEffect for better performance and control
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255) // Max amplitude
                val vibrationEffect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    VibrationEffect.createWaveform(pattern, amplitudes, 0)
                } else {
                    VibrationEffect.createWaveform(pattern, 0)
                }
                vib.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                vib.vibrate(pattern, 0)
            }
        }

        // Start alarm sound with gradual volume increase
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@ReminderActivity, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true

                // Start with extremely low volume (barely audible - 1%)
                setVolume(0.01f, 0.01f)

                prepare()
                start()

                // Start gradual volume increase
                startVolumeGradualIncrease()
            }
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Error playing alarm sound: ${e.message}")
        }
    }

    /**
     * Stop alarm effects
     */
    private fun stopAlarmEffects() {
        // Stop volume fade-in
        volumeFadeRunnable?.let { volumeFadeHandler.removeCallbacks(it) }
        volumeFadeRunnable = null

        vibrator?.cancel()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    /**
     * Gradually increase alarm volume from almost silent to system alarm volume over 30 seconds
     */
    private fun startVolumeGradualIncrease() {
        val totalSteps = (fadeInDurationMs / fadeStepMs).toInt()
        var currentStep = 0

        volumeFadeRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        currentStep++

                        // Calculate volume with exponential curve for very gradual start
                        val progress = currentStep.toFloat() / totalSteps.toFloat()

                        // Use exponential curve: starts very slowly, accelerates later
                        // This makes the first 15 seconds barely noticeable, then builds more noticeably
                        val curvedProgress = progress * progress // Quadratic curve for gentle start

                        // Scale to system alarm volume: 0.01 (1%) to maxVolumeForAlarm
                        val volume = 0.01f + ((maxVolumeForAlarm - 0.01f) * curvedProgress)

                        // Set volume (left and right channels)
                        player.setVolume(volume, volume)

                        // Continue if not at max volume yet
                        if (currentStep < totalSteps && player.isPlaying) {
                            volumeFadeHandler.postDelayed(this, fadeStepMs)
                        }
                    }
                }
            }
        }

        // Start the fade-in process
        volumeFadeHandler.postDelayed(volumeFadeRunnable!!, fadeStepMs)
        Log.d("ReminderActivity", "Started 30-second volume fade-in to ${(maxVolumeForAlarm * 100).toInt()}% (system alarm volume)")
    }

    /**
     * Create modern Material You layout programmatically
     */
    /**
     * Create modern Material You layout for multiple events
     */
    private fun createLayout() {
        // Get theme-aware colors
        val theme = theme
        val typedArray = theme.obtainStyledAttributes(
            intArrayOf(
                com.google.android.material.R.attr.colorSurface,
                com.google.android.material.R.attr.colorOnSurface,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
            ),
        )

        val surfaceColor = typedArray.getColor(0, getColor(R.color.md_theme_light_surface))
        val onSurfaceColor = typedArray.getColor(1, getColor(R.color.md_theme_light_onSurface))
        val onSurfaceVariantColor = typedArray.getColor(2, getColor(R.color.md_theme_light_onSurfaceVariant))
        typedArray.recycle()

        // Main container with gradient background
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header with reminder count - Material You style
        headerText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 22f  // Larger, more prominent
            setTextColor(onSurfaceColor)  // Higher contrast
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            setPadding(24, 80, 24, 32)  // More breathing room
            letterSpacing = 0.0f  // Material You uses tighter spacing
        }

        mainLayout.addView(headerText)

        // ScrollView for events
        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Take remaining space
            )
            isVerticalScrollBarEnabled = false
        }

        // Container for event cards
        eventsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(24, 0, 24, 32)  // More padding for Material You
        }

        scrollView.addView(eventsContainer)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)
    }

    /**
     * Update the events list display
     */
    private fun updateEventsList(events: List<PendingAlarmsManager.PendingAlarm>) {
        runOnUiThread {
            try {
                Log.d(TAG, "updateEventsList called with ${events.size} events")

                // If no events, close the activity
                if (events.isEmpty()) {
                    Log.d(TAG, "No events, closing activity")
                    finish()
                    return@runOnUiThread
                }

                // Update header
                val count = events.size
                headerText?.text = if (count == 1) "1 REMINDER" else "$count REMINDERS"
                Log.d(TAG, "Updated header: ${headerText?.text}")

                // Clear existing event cards
                eventsContainer?.removeAllViews()

                // Create card for each event
                events.forEach { alarm ->
                    Log.d(TAG, "Creating card for: ${alarm.eventTitle}")
                    val eventCard = createEventCard(alarm)
                    eventsContainer?.addView(eventCard)
                }

                Log.d(TAG, "Successfully updated events list")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating events list: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Create an event card with Material You styling
     */
    private fun createEventCard(alarm: PendingAlarmsManager.PendingAlarm): View {
        // Get theme colors
        val theme = theme
        val typedArray = theme.obtainStyledAttributes(
            intArrayOf(
                com.google.android.material.R.attr.colorSurfaceVariant,
                com.google.android.material.R.attr.colorOnSurface,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                com.google.android.material.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorOnPrimary,
                com.google.android.material.R.attr.colorSecondaryContainer,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
            )
        )

        val cardColor = typedArray.getColor(0, getColor(R.color.md_theme_light_surfaceVariant))
        val onSurfaceColor = typedArray.getColor(1, getColor(R.color.md_theme_light_onSurface))
        val onSurfaceVariantColor = typedArray.getColor(2, getColor(R.color.md_theme_light_onSurfaceVariant))
        val primaryColor = typedArray.getColor(3, getColor(R.color.md_theme_light_primary))
        val onPrimaryColor = typedArray.getColor(4, getColor(R.color.md_theme_light_onPrimary))
        val secondaryContainerColor = typedArray.getColor(5, getColor(R.color.md_theme_light_secondaryContainer))
        val onSecondaryContainerColor = typedArray.getColor(6, getColor(R.color.md_theme_light_onSecondaryContainer))
        typedArray.recycle()

        // Card container with Material You styling
        val cardView = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)  // More spacing between cards
            }
            radius = dpToPx(28).toFloat()  // Larger radius for Material You
            cardElevation = 0f
            setCardBackgroundColor(cardColor)
        }

        // Card content
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 24)  // More generous padding
        }

        // Event title - Material You headline style
        val titleText = TextView(this).apply {
            text = alarm.eventTitle
            textSize = 24f  // Larger for better hierarchy
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)  // Regular weight for Material You
            setTextColor(onSurfaceColor)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            letterSpacing = 0.0f  // Material You uses natural spacing
        }

        // Time and countdown container
        val timeInfoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 24)  // More spacing
        }

        // Event time - Material You body large
        val timeText = TextView(this).apply {
            val currentTime = System.currentTimeMillis()
            val eventDate = Date(alarm.eventStartTime)
            val currentDate = Date(currentTime)

            // Check if event is today
            val eventCalendar = Calendar.getInstance().apply { time = eventDate }
            val currentCalendar = Calendar.getInstance().apply { time = currentDate }

            val isToday = eventCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                    eventCalendar.get(Calendar.DAY_OF_YEAR) == currentCalendar.get(Calendar.DAY_OF_YEAR)

            val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateTimeFormatter = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

            text = if (isToday) {
                timeFormatter.format(eventDate)
            } else {
                dateTimeFormatter.format(eventDate)
            }

            textSize = 18f  // Slightly larger
            setTextColor(onSurfaceColor)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        // Countdown - Material You label large
        val countdownText = TextView(this).apply {
            text = formatCountdown(alarm.eventStartTime)
            textSize = 14f  // Slightly larger
            setTextColor(onSurfaceVariantColor)
            setPadding(0, 6, 0, 0)
        }

        timeInfoContainer.addView(timeText)
        timeInfoContainer.addView(countdownText)

        // Buttons container
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Snooze button - Material You tonal style (secondary action)
        val snoozeButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Snooze"
            textSize = 16f  // Slightly larger
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(52),  // Taller buttons
                1f
            ).apply {
                setMargins(0, 0, 8, 0)  // More spacing
            }
            cornerRadius = dpToPx(100)  // Full rounded - Material You style
            backgroundTintList = android.content.res.ColorStateList.valueOf(secondaryContainerColor)
            setTextColor(onSecondaryContainerColor)
            elevation = 0f
            stateListAnimator = null  // Remove elevation animation

            setOnClickListener {
                showSnoozeDialog(alarm)
            }
        }

        // Dismiss button - Material You filled tonal style (primary action)
        val dismissButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Dismiss"
            textSize = 16f  // Slightly larger
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(52),  // Taller buttons
                1f
            ).apply {
                setMargins(8, 0, 0, 0)  // More spacing
            }
            cornerRadius = dpToPx(100)  // Full rounded - Material You style
            backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            setTextColor(onPrimaryColor)
            elevation = 0f
            stateListAnimator = null  // Remove elevation animation

            setOnClickListener {
                dismissEvent(alarm)
            }
        }

        buttonContainer.addView(snoozeButton)
        buttonContainer.addView(dismissButton)

        cardContent.addView(titleText)
        cardContent.addView(timeInfoContainer)
        cardContent.addView(buttonContainer)

        cardView.addView(cardContent)

        return cardView
    }

    /**
     * Show snooze dialog for a specific event
     */
    private fun showSnoozeDialog(alarm: PendingAlarmsManager.PendingAlarm) {
        // Stop alarm effects immediately when user taps Snooze button
        if (!userHasInteracted) {
            stopAlarmEffects()
            userHasInteracted = true
        }

        val options = arrayOf("5 minutes", "10 minutes", "30 minutes", "1 hour", "2 hours", "3 hours")
        val minutes = arrayOf(5, 10, 30, 60, 120, 180)

        android.app.AlertDialog.Builder(this)
            .setTitle("Snooze for")
            .setItems(options) { _, which ->
                snoozeEvent(alarm, minutes[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Dismiss a specific event
     */
    private fun dismissEvent(alarm: PendingAlarmsManager.PendingAlarm) {
        // Stop alarm effects on first user interaction
        if (!userHasInteracted) {
            stopAlarmEffects()
            userHasInteracted = true
        }

        PendingAlarmsManager.removeAlarm(alarm.eventId, alarm.reminderType)
        // Activity callback will handle UI update
    }

    /**
     * Snooze a specific event
     */
    private fun snoozeEvent(alarm: PendingAlarmsManager.PendingAlarm, minutes: Int) {
        lifecycleScope.launch {
            try {
                // Create a snooze time
                val snoozeTime = System.currentTimeMillis() + (minutes * 60 * 1000)

                // Create a temporary event for the snooze
                val snoozeEvent = me.tewodros.vibecalendaralarm.model.CalendarEvent(
                    id = alarm.eventId,
                    title = alarm.eventTitle,
                    startTime = snoozeTime,
                    reminderMinutes = emptyList() // No reminder offset since we want it to fire at snoozeTime
                )

                // Schedule a reminder 0 minutes before the snooze time (immediate)
                calendarRepository.scheduleReminder(snoozeEvent, 0)

                // Remove from pending queue
                PendingAlarmsManager.removeAlarm(alarm.eventId, alarm.reminderType)
                // Activity callback will handle UI update

                Toast.makeText(this@ReminderActivity, "Snoozed for $minutes minutes", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to snooze event ${alarm.eventId}", e)
                Toast.makeText(this@ReminderActivity, "Failed to snooze reminder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Format countdown text
     */
    private fun formatCountdown(eventTime: Long): String {
        val currentTime = System.currentTimeMillis()
        val diff = eventTime - currentTime

        return when {
            diff < 0 -> "Started ${formatTimeDifference(-diff)} ago"
            diff < 60000 -> "Starting now"
            else -> "In ${formatTimeDifference(diff)}"
        }
    }

    /**
     * Format time difference in human-readable form
     */
    private fun formatTimeDifference(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days day${if (days != 1L) "s" else ""}"
            hours > 0 -> "$hours hour${if (hours != 1L) "s" else ""}"
            minutes > 0 -> "$minutes minute${if (minutes != 1L) "s" else ""}"
            else -> "$seconds second${if (seconds != 1L) "s" else ""}"
        }
    }

    /**
     * Helper method to convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmEffects()
        PendingAlarmsManager.unregisterActivityCallback()
        Log.d("ReminderActivity", "Activity destroyed, callback unregistered")
    }
}
