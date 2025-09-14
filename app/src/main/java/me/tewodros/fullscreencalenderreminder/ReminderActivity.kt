package me.tewodros.fullscreencalenderreminder

import android.content.Context
import android.media.AudioAttributes
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
import me.tewodros.fullscreencalenderreminder.repository.CalendarRepository
import me.tewodros.fullscreencalenderreminder.viewmodel.ReminderViewModel

/**
 * ReminderActivity displays full-screen alarm notifications for calendar events.
 * * This activity provides an immersive alarm experience with:
 * - Full-screen display that appears over the lock screen
 * - Gradual volume increase (30-second exponential fade-in from 1% to 100%)
 * - Vibration patterns for silent devices
 * - Multiple interaction options: Dismiss, Snooze (5min), Acknowledge
 * - Auto-dismiss after 2 minutes if not interacted with
 * - MVVM architecture with Hilt dependency injection
 * * Audio Features:
 * - Uses system default alarm sound
 * - Gradual volume fade-in to avoid jarring wake-ups
 * - Respects system volume settings
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

    private lateinit var viewModel: ReminderViewModel

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

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
     * Gradually increase alarm volume from almost silent to full volume over 10 seconds
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
                        val volume = 0.01f + (0.99f * curvedProgress) // 0.01 (1%) to 1.0 (100%) range

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
        Log.d("ReminderActivity", "Started 30-second volume fade-in")
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

        // Event title with Material You typography
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceColor)
            gravity = android.view.Gravity.CENTER
            setPadding(24, 32, 24, 16)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // Event time with Material You styling
        val timeText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 20f
            setTextColor(onSurfaceVariantColor)
            gravity = android.view.Gravity.CENTER
            setPadding(24, 8, 24, 48)
        }

        // Action buttons container with Material You styling
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        // Primary action - Dismiss button with Material You elevated style
        val dismissButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Dismiss Reminder"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(56), // Standard Material You button height
            ).apply {
                setMargins(0, 0, 0, 24)
            }
            cornerRadius = 28 // Material You rounded corners
            iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            icon = ContextCompat.getDrawable(this@ReminderActivity, R.drawable.ic_check_circle_24)

            // Material You filled button style
            backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            setTextColor(onPrimaryColor)
            iconTint = android.content.res.ColorStateList.valueOf(onPrimaryColor)

            // Material You elevation
            elevation = 6f
            stateListAnimator = android.animation.StateListAnimator()

            iconSize = dpToPx(24)
            iconPadding = dpToPx(12)
            setOnClickListener { dismissReminder() }
        }

        buttonContainer.addView(dismissButton)

        // Show snooze options only if this is the final reminder
        val eventTimeMs = intent.getLongExtra("event_time", 0)
        val currentTimeMs = System.currentTimeMillis()
        val minutesUntilEvent = (eventTimeMs - currentTimeMs) / (1000 * 60)

        // Check if this is a final reminder or very close to event time
        val isFinalReminder = reminderType.startsWith("FINAL_REMINDER_") || reminderType == "AT_EVENT_TIME" || reminderType == "ONE_MINUTE_BEFORE" || minutesUntilEvent <= 1

        if (isFinalReminder) { // Final reminder - show snooze options
            // Snooze options container
            val snoozeContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 16, 0, 0)
                }
            }

            // Snooze header with Material You typography
            val snoozeHeader = TextView(this).apply {
                text = "Snooze for:"
                textSize = 16f
                typeface = android.graphics.Typeface.create(
                    "sans-serif-medium",
                    android.graphics.Typeface.NORMAL,
                )
                setTextColor(onSurfaceVariantColor)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 0, 0, 20)
                }
            }

            // First row of snooze buttons
            val snoozeRow1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 0, 0, 12)
                }
            }

            // 5 minutes snooze
            val snooze5Button = createSnoozeButton("5 min", 5)
            // 10 minutes snooze
            val snooze10Button = createSnoozeButton("10 min", 10)
            // 30 minutes snooze
            val snooze30Button = createSnoozeButton("30 min", 30)

            snoozeRow1.addView(snooze5Button)
            snoozeRow1.addView(snooze10Button)
            snoozeRow1.addView(snooze30Button)

            // Second row of snooze buttons
            val snoozeRow2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }

            // 1 hour snooze
            val snooze1hButton = createSnoozeButton("1 hr", 60)
            // 2 hours snooze
            val snooze2hButton = createSnoozeButton("2 hr", 120)
            // 3 hours snooze
            val snooze3hButton = createSnoozeButton("3 hr", 180)

            snoozeRow2.addView(snooze1hButton)
            snoozeRow2.addView(snooze2hButton)
            snoozeRow2.addView(snooze3hButton)

            snoozeContainer.addView(snoozeHeader)
            snoozeContainer.addView(snoozeRow1)
            snoozeContainer.addView(snoozeRow2)

            buttonContainer.addView(snoozeContainer)
        }

        // Assemble the layout
        layout.addView(titleText)
        layout.addView(timeText)
        layout.addView(buttonContainer)

        setContentView(layout)

        // Store references for later use
        findViewById<TextView>(titleText.id).tag = "title"
        findViewById<TextView>(timeText.id).tag = "time"
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

        // Include alarm type in the title display
        val displayTitle = when {
            reminderType.startsWith("FINAL_REMINDER_") -> {
                val minutes = reminderType.substringAfter("FINAL_REMINDER_").substringBefore("MIN")
                "🚨 FINAL REMINDER ($minutes min): $eventTitle"
            }
            reminderType == "AT_EVENT_TIME" -> "🚨 EVENT NOW: $eventTitle"
            reminderType == "ONE_MINUTE_BEFORE" -> "🚨 1-MIN BEFORE: $eventTitle"
            reminderType == "TEST" -> "🧪 TEST: $eventTitle"
            else -> {
                if (reminderType.startsWith("ORIGINAL_")) {
                    val index = reminderType.substringAfter("ORIGINAL_").toIntOrNull()
                    "🔔 REMINDER #${(index ?: 0) + 1}: $eventTitle"
                } else {
                    "🔔 REMINDER: $eventTitle"
                }
            }
        }

        titleTextView?.text = displayTitle

        if (eventStartTime > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            timeTextView?.text = dateFormat.format(Date(eventStartTime))
        } else {
            timeTextView?.text = "No time specified"
        }
    }

    /**
     * Dismiss the reminder and close activity
     */
    private fun dismissReminder() {
        stopAlarmEffects()
        Toast.makeText(this, "Reminder dismissed", Toast.LENGTH_SHORT).show()
        finish()
    }

    /**
     * Helper method to create snooze buttons with Material You design
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
        val snoozeEvent = me.tewodros.fullscreencalenderreminder.model.CalendarEvent(
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
