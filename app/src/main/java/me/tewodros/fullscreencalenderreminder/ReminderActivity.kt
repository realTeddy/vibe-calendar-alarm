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
import me.tewodros.vibecalendaralarm.wear.WearCommunicationManager

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

    @Inject
    lateinit var calendarRepository: CalendarRepository

    @Inject
    lateinit var wearCommunicationManager: WearCommunicationManager

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

    private var eventId: Long = -1
    private var eventTitle: String = ""
    private var eventStartTime: Long = 0
    private var reminderType: String = ""

    // Snooze options in minutes
    private val snoozeOptions = arrayOf(5, 10, 30, 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("ReminderActivity", "🚀 ReminderActivity onCreate() called")
        super.onCreate(savedInstanceState)

        Log.d("ReminderActivity", "📋 Intent details:")
        Log.d("ReminderActivity", "  Action: ${intent.action}")
        Log.d("ReminderActivity", "  Flags: ${intent.flags}")
        Log.d("ReminderActivity", "  Event ID: ${intent.getLongExtra("event_id", -1)}")
        Log.d("ReminderActivity", "  Event Title: ${intent.getStringExtra("event_title")}")
        Log.d("ReminderActivity", "  Reminder Type: ${intent.getStringExtra("reminder_type")}")

        // Create simple layout programmatically (no XML needed for MVP)
        createLayout()

        // Modern approach for full-screen and lock screen presentation (after content view is set)
        setupModernFullScreen()

        // Initialize components
        setupAlarmFeatures()

        // Get event details from intent
        extractEventDetails()
        displayEventInfo()

        // Start alarm sound and vibration
        startAlarmEffects()

        Log.d("ReminderActivity", "✅ ReminderActivity onCreate() completed successfully")
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
    private fun createLayout() {
        // Get theme-aware colors
        val theme = theme
        val typedArray = theme.obtainStyledAttributes(
            intArrayOf(
                com.google.android.material.R.attr.colorSurface,
                com.google.android.material.R.attr.colorOnSurface,
                com.google.android.material.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorOnPrimary,
                com.google.android.material.R.attr.colorPrimaryContainer,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                com.google.android.material.R.attr.colorSecondaryContainer,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                com.google.android.material.R.attr.colorTertiaryContainer,
                com.google.android.material.R.attr.colorOnTertiaryContainer,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
            ),
        )

        val surfaceColor = typedArray.getColor(0, getColor(R.color.md_theme_light_surface))
        val onSurfaceColor = typedArray.getColor(1, getColor(R.color.md_theme_light_onSurface))
        val primaryColor = typedArray.getColor(2, getColor(R.color.md_theme_light_primary))
        val onPrimaryColor = typedArray.getColor(3, getColor(R.color.md_theme_light_onPrimary))
        val primaryContainerColor = typedArray.getColor(
            4,
            getColor(R.color.md_theme_light_primaryContainer),
        )
        val onPrimaryContainerColor = typedArray.getColor(
            5,
            getColor(R.color.md_theme_light_onPrimaryContainer),
        )
        val secondaryContainerColor = typedArray.getColor(
            6,
            getColor(R.color.md_theme_light_secondaryContainer),
        )
        val onSecondaryContainerColor = typedArray.getColor(
            7,
            getColor(R.color.md_theme_light_onSecondaryContainer),
        )
        val tertiaryContainerColor = typedArray.getColor(
            8,
            getColor(R.color.md_theme_light_tertiaryContainer),
        )
        val onTertiaryContainerColor = typedArray.getColor(
            9,
            getColor(R.color.md_theme_light_onTertiaryContainer),
        )
        val onSurfaceVariantColor = typedArray.getColor(
            10,
            getColor(R.color.md_theme_light_onSurfaceVariant),
        )

        typedArray.recycle()

        // Main container with Material You styling
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            setPadding(32, 64, 32, 64)
            gravity = android.view.Gravity.CENTER
        }

        // Event title with Material You typography - Clean, no emoji, bigger and bolder
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 40f // Increased from 32f for more prominence
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            gravity = android.view.Gravity.CENTER
            setPadding(32, 48, 32, 16)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // Event time with Material You styling
        val timeText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 28f // Increased from 22f for better visibility
            setTextColor(onSurfaceColor)
            gravity = android.view.Gravity.CENTER
            setPadding(24, 8, 24, 8)
        }

        // Countdown text with Material You styling
        val countdownText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 18f
            setTextColor(onSurfaceVariantColor)
            gravity = android.view.Gravity.CENTER
            setPadding(24, 8, 24, 64)
        }

        // Action buttons container with Material You styling
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(48, 32, 48, 48)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        // Snooze button - Google Clock inspired, large and rounded
        val snoozeButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Snooze"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(72), // Bigger button height like Google Clock
                1f,
            ).apply {
                setMargins(0, 0, 12, 0)
            }
            cornerRadius = 36 // Fully rounded corners

            // Material You tonal button style
            backgroundTintList = android.content.res.ColorStateList.valueOf(secondaryContainerColor)
            setTextColor(onSecondaryContainerColor)

            elevation = 0f
            stateListAnimator = android.animation.StateListAnimator()

            setOnClickListener { toggleSnoozeOptions() }
        }

        // Dismiss button - Google Clock inspired, large and prominent
        val dismissButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Stop"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(72), // Bigger button height like Google Clock
                1f,
            ).apply {
                setMargins(12, 0, 0, 0)
            }
            cornerRadius = 36 // Fully rounded corners

            // Material You filled button style (primary color for emphasis)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            setTextColor(onPrimaryColor)

            elevation = 2f
            stateListAnimator = android.animation.StateListAnimator()

            setOnClickListener { dismissReminder() }
        }

        buttonContainer.addView(snoozeButton)
        buttonContainer.addView(dismissButton)

        // Snooze options container (initially hidden)
        val snoozeOptionsContainer = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(48, 0, 48, 32)
            visibility = View.GONE // Hidden by default
        }

        // Snooze header
        val snoozeHeader = TextView(this).apply {
            text = "Snooze for:"
            textSize = 18f
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )
            setTextColor(onSurfaceVariantColor)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        // First row of snooze options
        val snoozeRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        val snooze5Button = createSnoozeOptionButton("5 min", 5, secondaryContainerColor, onSecondaryContainerColor)
        val snooze10Button = createSnoozeOptionButton("10 min", 10, secondaryContainerColor, onSecondaryContainerColor)
        val snooze30Button = createSnoozeOptionButton("30 min", 30, secondaryContainerColor, onSecondaryContainerColor)

        snoozeRow1.addView(snooze5Button)
        snoozeRow1.addView(snooze10Button)
        snoozeRow1.addView(snooze30Button)

        // Second row of snooze options
        val snoozeRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val snooze1hButton = createSnoozeOptionButton("1 hr", 60, secondaryContainerColor, onSecondaryContainerColor)
        val snooze2hButton = createSnoozeOptionButton("2 hr", 120, secondaryContainerColor, onSecondaryContainerColor)
        val snooze3hButton = createSnoozeOptionButton("3 hr", 180, secondaryContainerColor, onSecondaryContainerColor)

        snoozeRow2.addView(snooze1hButton)
        snoozeRow2.addView(snooze2hButton)
        snoozeRow2.addView(snooze3hButton)

        snoozeOptionsContainer.addView(snoozeHeader)
        snoozeOptionsContainer.addView(snoozeRow1)
        snoozeOptionsContainer.addView(snoozeRow2)

        // Assemble the layout
        layout.addView(titleText)
        layout.addView(timeText)
        layout.addView(countdownText)
        layout.addView(buttonContainer)
        layout.addView(snoozeOptionsContainer)

        setContentView(layout)

        // Store references for later use
        findViewById<TextView>(titleText.id).tag = "title"
        findViewById<TextView>(timeText.id).tag = "time"
        findViewById<TextView>(countdownText.id).tag = "countdown"
        findViewById<LinearLayout>(snoozeOptionsContainer.id).tag = "snoozeOptions"
    }

    /**
     * Extract event details from the intent
     */
    private fun extractEventDetails() {
        eventId = intent.getLongExtra("event_id", -1)
        eventTitle = intent.getStringExtra("event_title") ?: "Reminder"
        eventStartTime = intent.getLongExtra("event_start_time", 0)
        reminderType = intent.getStringExtra("reminder_type") ?: "UNKNOWN"
    }

    /**
     * Display event information in the UI
     */
    private fun displayEventInfo() {
        // Find TextViews by tag
        val titleTextView = findViewById<View>(android.R.id.content)
            .findViewWithTag<TextView>("title")
        val timeTextView = findViewById<View>(android.R.id.content)
            .findViewWithTag<TextView>("time")
        val countdownTextView = findViewById<View>(android.R.id.content)
            .findViewWithTag<TextView>("countdown")

        // Display clean event title without emoji or reminder type
        titleTextView?.text = eventTitle

        // Format and display event time
        val actualEventTime = if (eventStartTime > 0) {
            eventStartTime
        } else {
            // For test reminders, use current time + 1 minute as fallback
            intent.getLongExtra("event_time", System.currentTimeMillis() + 60000)
        }

        if (actualEventTime > 0) {
            val currentTime = System.currentTimeMillis()
            val eventDate = Date(actualEventTime)
            val currentDate = Date(currentTime)

            // Check if event is today
            val eventCalendar = Calendar.getInstance().apply { time = eventDate }
            val currentCalendar = Calendar.getInstance().apply { time = currentDate }

            val isToday = eventCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                         eventCalendar.get(Calendar.DAY_OF_YEAR) == currentCalendar.get(Calendar.DAY_OF_YEAR)

            // Format time based on whether it's today
            val timeFormat = if (isToday) {
                SimpleDateFormat("h:mm a", Locale.getDefault())
            } else {
                SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            }
            timeTextView?.text = timeFormat.format(eventDate)

            // Calculate and display countdown
            val timeDiffMillis = actualEventTime - currentTime
            val countdownText = formatCountdown(timeDiffMillis)
            countdownTextView?.text = countdownText
        } else {
            timeTextView?.text = "No time specified"
            countdownTextView?.text = ""
        }
    }

    /**
     * Format time difference into a human-readable countdown
     */
    private fun formatCountdown(timeDiffMillis: Long): String {
        val absTimeDiff = Math.abs(timeDiffMillis)
        val isPast = timeDiffMillis < 0

        val seconds = absTimeDiff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        val countdownStr = when {
            days > 0 -> {
                if (days == 1L) "1 day" else "$days days"
            }
            hours > 0 -> {
                if (hours == 1L) "1 hour" else "$hours hours"
            }
            minutes > 0 -> {
                if (minutes == 1L) "1 minute" else "$minutes minutes"
            }
            else -> {
                "less than a minute"
            }
        }

        return if (isPast) {
            "Started $countdownStr ago"
        } else {
            "Starts in $countdownStr"
        }
    }

    /**
     * Dismiss the reminder and close activity
     */
    private fun dismissReminder() {
        stopAlarmEffects()

        // Notify Wear OS devices that reminder was dismissed on phone
        wearCommunicationManager.notifyWearOfDismissal(eventId)
        Log.d("ReminderActivity", "📱 Notified Wear OS of dismissal")

        Toast.makeText(this, "Reminder dismissed", Toast.LENGTH_SHORT).show()
        finish()
    }

    /**
     * Toggle snooze options visibility
     */
    private fun toggleSnoozeOptions() {
        val snoozeOptionsContainer = findViewById<View>(android.R.id.content)
            .findViewWithTag<LinearLayout>("snoozeOptions")

        snoozeOptionsContainer?.let { container ->
            if (container.visibility == View.GONE) {
                container.visibility = View.VISIBLE
            } else {
                container.visibility = View.GONE
            }
        }
    }

    /**
     * Helper method to create snooze option buttons with Material You design
     */
    private fun createSnoozeOptionButton(
        text: String,
        minutes: Int,
        backgroundColor: Int,
        textColor: Int
    ): com.google.android.material.button.MaterialButton {
        return com.google.android.material.button.MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            this.text = text
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(56),
                1f,
            ).apply {
                val margin = dpToPx(8)
                setMargins(margin, 0, margin, 0)
            }

            cornerRadius = 28
            backgroundTintList = android.content.res.ColorStateList.valueOf(backgroundColor)
            setTextColor(textColor)
            elevation = 0f
            isAllCaps = false

            setOnClickListener { snoozeReminder(minutes) }
        }
    }

    /**
     * Helper method to create snooze buttons with Material You design (deprecated - keeping for compatibility)
     */
    private fun createSnoozeButton(text: String, minutes: Int): com.google.android.material.button.MaterialButton {
        // Get theme colors
        val theme = theme
        val typedArray = theme.obtainStyledAttributes(
            intArrayOf(
                com.google.android.material.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorSecondaryContainer,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
            ),
        )

        val primaryColor = typedArray.getColor(0, getColor(R.color.md_theme_light_primary))
        val secondaryContainerColor = typedArray.getColor(
            1,
            getColor(R.color.md_theme_light_secondaryContainer),
        )
        val onSecondaryContainerColor = typedArray.getColor(
            2,
            getColor(R.color.md_theme_light_onSecondaryContainer),
        )

        typedArray.recycle()

        return com.google.android.material.button.MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            this.text = text
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(52), // Material You standard button height
                1f,
            ).apply {
                val margin = dpToPx(6)
                setMargins(margin, 0, margin, 0)
            }

            // Material You outlined button style
            cornerRadius = 26 // Half of height for rounded corners
            strokeWidth = dpToPx(1)
            strokeColor = android.content.res.ColorStateList.valueOf(primaryColor)

            // Material You tonal button colors
            backgroundTintList = android.content.res.ColorStateList.valueOf(secondaryContainerColor)
            setTextColor(onSecondaryContainerColor)

            // Icon styling
            icon = ContextCompat.getDrawable(this@ReminderActivity, R.drawable.ic_access_time_24)
            iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            iconSize = dpToPx(18)
            iconPadding = dpToPx(6)
            iconTint = android.content.res.ColorStateList.valueOf(onSecondaryContainerColor)

            // Material You state styling
            elevation = 0f // Outlined buttons have no elevation
            isAllCaps = false // Material You uses sentence case

            setOnClickListener { snoozeReminder(minutes) }
        }
    }

    /**
     * Helper method to convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * Snooze the reminder for specified minutes
     */
    private fun snoozeReminder(minutes: Int) {
        stopAlarmEffects()
        Toast.makeText(this, "Snoozed for $minutes minutes", Toast.LENGTH_SHORT).show()

        // Create a new alarm for the snooze time
        val snoozeTime = System.currentTimeMillis() + (minutes * 60 * 1000)

        // Use the calendar repository to schedule the snooze
        // For simplicity, create a temporary event for snoozing
        val snoozeEvent = me.tewodros.vibecalendaralarm.model.CalendarEvent(
            id = eventId,
            title = eventTitle,
            startTime = snoozeTime,
            reminderMinutes = emptyList(), // No reminder offset since we want it to fire at snoozeTime
        )

        lifecycleScope.launch {
            // Schedule a reminder 0 minutes before the snooze time (immediate)
            calendarRepository.scheduleReminder(snoozeEvent, 0)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmEffects()
    }
}
