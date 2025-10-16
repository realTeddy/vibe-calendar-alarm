package me.tewodros.vibecalendaralarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings Activity with Material You design
 * Manages app configuration including final reminder timing and debug options
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var finalReminderSlider: Slider
    private lateinit var finalReminderValueText: TextView
    private lateinit var debugModeSwitch: SwitchMaterial
    private lateinit var screenshotModeSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createMaterialYouLayout()
        loadSettings()
    }

    /**
     * Create the settings UI with Material You design
     */
    private fun createMaterialYouLayout() {
        // Handle system bars properly
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Get theme-aware colors
        val theme = theme
        val typedArray = theme.obtainStyledAttributes(
            intArrayOf(
                com.google.android.material.R.attr.colorSurface,
                com.google.android.material.R.attr.colorOnSurface,
                com.google.android.material.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorOnPrimary,
                com.google.android.material.R.attr.colorSurfaceVariant,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                com.google.android.material.R.attr.colorSecondaryContainer,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
            ),
        )

        val surfaceColor = typedArray.getColor(0, getColor(R.color.md_theme_light_surface))
        val onSurfaceColor = typedArray.getColor(1, getColor(R.color.md_theme_light_onSurface))
        val primaryColor = typedArray.getColor(2, getColor(R.color.md_theme_light_primary))
        val onPrimaryColor = typedArray.getColor(3, getColor(R.color.md_theme_light_onPrimary))
        val surfaceVariantColor = typedArray.getColor(
            4,
            getColor(R.color.md_theme_light_surfaceVariant),
        )
        val onSurfaceVariantColor = typedArray.getColor(
            5,
            getColor(R.color.md_theme_light_onSurfaceVariant),
        )
        val secondaryContainerColor = typedArray.getColor(
            6,
            getColor(R.color.md_theme_light_secondaryContainer),
        )
        val onSecondaryContainerColor = typedArray.getColor(
            7,
            getColor(R.color.md_theme_light_onSecondaryContainer),
        )

        typedArray.recycle()

        // Main coordinator layout
        val coordinatorLayout = CoordinatorLayout(this).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(surfaceColor)
            fitsSystemWindows = true
        }

        // App bar layout
        val appBarLayout = AppBarLayout(this).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
            )
            fitsSystemWindows = true
        }

        // Toolbar
        val toolbar = MaterialToolbar(this).apply {
            title = "Settings"
            setNavigationIcon(R.drawable.ic_arrow_back_24)
            setNavigationOnClickListener { finish() }
            layoutParams = AppBarLayout.LayoutParams(
                AppBarLayout.LayoutParams.MATCH_PARENT,
                AppBarLayout.LayoutParams.WRAP_CONTENT,
            )
            setBackgroundColor(surfaceColor)
            setTitleTextColor(onSurfaceColor)
        }

        appBarLayout.addView(toolbar)

        // Scrollable content
        val scrollView = ScrollView(this).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
            ).apply {
                behavior = AppBarLayout.ScrollingViewBehavior()
            }
            setPadding(16, 16, 16, 16)
        }

        // Main content container
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        // Add settings sections (pass theme colors)
        contentLayout.addView(
            createRemindersSection(onSurfaceColor, onSurfaceVariantColor, primaryColor),
        )
        contentLayout.addView(createDeveloperSection(onSurfaceColor, onSurfaceVariantColor))

        scrollView.addView(contentLayout)
        coordinatorLayout.addView(appBarLayout)
        coordinatorLayout.addView(scrollView)

        setContentView(coordinatorLayout)
    }

    /**
     * Create the reminders configuration section
     */
    private fun createRemindersSection(
        onSurfaceColor: Int,
        onSurfaceVariantColor: Int,
        primaryColor: Int,
    ): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 2f
            radius = 16f
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 24)
        }

        // Section header
        val headerText = TextView(this).apply {
            text = "Reminder Settings"
            textSize = 20f
            setTextColor(onSurfaceColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Final reminder timing
        val finalReminderLabel = TextView(this).apply {
            text = "Final Reminder Timing"
            textSize = 16f
            setTextColor(onSurfaceColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        val finalReminderDescription = TextView(this).apply {
            text = "Set when the final reminder should appear (in minutes before the event)"
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }

        finalReminderSlider = Slider(this).apply {
            valueFrom = 0f
            valueTo = 10f
            stepSize = 1f
            value = 1f // Default to 1 minute before event
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            addOnChangeListener { _, value, _ ->
                updateFinalReminderValueText(value.toInt())
                saveFinalReminderSetting(value.toInt())
            }
        }

        finalReminderValueText = TextView(this).apply {
            text = "1 minute before event"
            textSize = 14f
            setTextColor(primaryColor)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        cardContent.addView(headerText)
        cardContent.addView(finalReminderLabel)
        cardContent.addView(finalReminderDescription)
        cardContent.addView(finalReminderSlider)
        cardContent.addView(finalReminderValueText)

        card.addView(cardContent)
        return card
    }

    /**
     * Create the developer/debug section
     */
    private fun createDeveloperSection(onSurfaceColor: Int, onSurfaceVariantColor: Int): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 2f
            radius = 16f
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 24)
        }

        // Section header
        val headerText = TextView(this).apply {
            text = "Developer Options"
            textSize = 20f
            setTextColor(onSurfaceColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Debug mode toggle
        val debugContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        val debugLabel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }

        val debugTitle = TextView(this).apply {
            text = "Debug Mode"
            textSize = 16f
            setTextColor(onSurfaceColor)
        }

        val debugDescription = TextView(this).apply {
            text = "Enable test reminders and debug features"
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
        }

        debugLabel.addView(debugTitle)
        debugLabel.addView(debugDescription)

        debugModeSwitch = SwitchMaterial(this).apply {
            isChecked = false
            setOnCheckedChangeListener { _, isChecked ->
                saveDebugModeSetting(isChecked)
            }
        }

        debugContainer.addView(debugLabel)
        debugContainer.addView(debugModeSwitch)

        // Screenshot mode toggle
        val screenshotContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        val screenshotLabel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }

        val screenshotTitle = TextView(this).apply {
            text = "Screenshot Mode"
            textSize = 16f
            setTextColor(onSurfaceColor)
        }

        val screenshotDescription = TextView(this).apply {
            text = "Show fake events for perfect screenshots"
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
        }

        screenshotLabel.addView(screenshotTitle)
        screenshotLabel.addView(screenshotDescription)

        val screenshotModeSwitch = SwitchMaterial(this).apply {
            isChecked = false
            setOnCheckedChangeListener { _, isChecked ->
                saveScreenshotModeSetting(isChecked)
                if (isChecked) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "📸 Screenshot mode enabled - showing fake events",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        "📅 Showing real calendar events",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        this@SettingsActivity.screenshotModeSwitch = screenshotModeSwitch

        screenshotContainer.addView(screenshotLabel)
        screenshotContainer.addView(screenshotModeSwitch)

        // Test buttons container (shown only when debug mode is enabled)
        val testButtonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            visibility = android.view.View.GONE
        }

        // Test reminder buttons
        val testImmediateButton = MaterialButton(this).apply {
            text = "Test Immediate Reminder"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            // cornerRadius inherited from global style
            setOnClickListener { testImmediateReminder() }
        }

        val test1MinButton = MaterialButton(this).apply {
            text = "Test 1-Minute Reminder"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            // cornerRadius inherited from global style
            setOnClickListener { test1MinuteReminder() }
        }

        val testMultipleButton = MaterialButton(this).apply {
            text = "Test Multiple Events (3)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            // cornerRadius inherited from global style
            setOnClickListener { testMultipleEvents() }
        }

        testButtonsContainer.addView(testImmediateButton)
        testButtonsContainer.addView(test1MinButton)
        testButtonsContainer.addView(testMultipleButton)

        // Update test buttons visibility when debug mode changes
        debugModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveDebugModeSetting(isChecked)
            testButtonsContainer.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        cardContent.addView(headerText)
        cardContent.addView(debugContainer)
        cardContent.addView(screenshotContainer)
        cardContent.addView(testButtonsContainer)

        card.addView(cardContent)
        return card
    }

    /**
     * Update the final reminder value text display
     */
    private fun updateFinalReminderValueText(minutes: Int) {
        finalReminderValueText.text = when (minutes) {
            0 -> "At event time"
            1 -> "1 minute before event"
            else -> "$minutes minutes before event"
        }
    }

    /**
     * Load saved settings from SharedPreferences
     */
    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val finalReminderMinutes = prefs.getInt("final_reminder_minutes", 1) // Default to 1 minute before event
        finalReminderSlider.value = finalReminderMinutes.toFloat()
        updateFinalReminderValueText(finalReminderMinutes)

        val debugMode = prefs.getBoolean("debug_mode", false)
        debugModeSwitch.isChecked = debugMode

        val screenshotMode = prefs.getBoolean("screenshot_mode", false)
        screenshotModeSwitch.isChecked = screenshotMode
    }

    /**
     * Save final reminder setting
     */
    private fun saveFinalReminderSetting(minutes: Int) {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("final_reminder_minutes", minutes).apply()
    }

    /**
     * Save debug mode setting
     */
    private fun saveDebugModeSetting(enabled: Boolean) {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("debug_mode", enabled).apply()
    }

    /**
     * Save screenshot mode setting
     */
    private fun saveScreenshotModeSetting(enabled: Boolean) {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("screenshot_mode", enabled).apply()
    }

    /**
     * Test immediate reminder
     */
    private fun testImmediateReminder() {
        // Create a pending alarm and add it to the queue
        val pendingAlarm = me.tewodros.vibecalendaralarm.PendingAlarmsManager.PendingAlarm(
            eventId = System.currentTimeMillis(), // Use timestamp as unique ID for test
            eventTitle = "Test Event - Immediate",
            eventStartTime = System.currentTimeMillis(),
            reminderType = "TEST",
            calendarName = "Test Calendar"
        )

        // Add to queue
        me.tewodros.vibecalendaralarm.PendingAlarmsManager.addAlarm(pendingAlarm)

        // Launch activity if not already active
        if (!me.tewodros.vibecalendaralarm.PendingAlarmsManager.isActivityActive()) {
            val intent = Intent(this, ReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            startActivity(intent)
        }

        Toast.makeText(this, "Test reminder launched", Toast.LENGTH_SHORT).show()
    }

    /**
     * Test 1-minute reminder
     */
    private fun test1MinuteReminder() {
        // Create a pending alarm and add it to the queue
        val pendingAlarm = me.tewodros.vibecalendaralarm.PendingAlarmsManager.PendingAlarm(
            eventId = System.currentTimeMillis() + 1, // Use timestamp + 1 as unique ID for test
            eventTitle = "Test Event - 1 Minute",
            eventStartTime = System.currentTimeMillis() + 60000, // 1 minute from now
            reminderType = "ONE_MINUTE_BEFORE",
            calendarName = "Test Calendar"
        )

        // Add to queue
        me.tewodros.vibecalendaralarm.PendingAlarmsManager.addAlarm(pendingAlarm)

        // Launch activity if not already active
        if (!me.tewodros.vibecalendaralarm.PendingAlarmsManager.isActivityActive()) {
            val intent = Intent(this, ReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            startActivity(intent)
        }

        Toast.makeText(this, "1-minute test reminder launched", Toast.LENGTH_SHORT).show()
    }

    /**
     * Test multiple simultaneous events
     */
    private fun testMultipleEvents() {
        val baseTime = System.currentTimeMillis()

        // Create 3 test events with different calendar names
        val events = listOf(
            Triple("Team Meeting", baseTime + 5000, "FIVE_MINUTES_BEFORE"),
            Triple("Doctor Appointment", baseTime + 10000, "TEN_MINUTES_BEFORE"),
            Triple("Lunch with Client", baseTime + 15000, "FIFTEEN_MINUTES_BEFORE")
        )

        val calendarNames = listOf("Work", "Personal", "Family")

        // Add all events to the queue
        events.forEachIndexed { index, (title, startTime, type) ->
            val pendingAlarm = me.tewodros.vibecalendaralarm.PendingAlarmsManager.PendingAlarm(
                eventId = baseTime + index + 100, // Unique IDs
                eventTitle = title,
                eventStartTime = startTime,
                reminderType = type,
                calendarName = calendarNames[index]
            )
            me.tewodros.vibecalendaralarm.PendingAlarmsManager.addAlarm(pendingAlarm)
        }

        // Launch activity if not already active
        if (!me.tewodros.vibecalendaralarm.PendingAlarmsManager.isActivityActive()) {
            val intent = Intent(this, ReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            startActivity(intent)
        }

        Toast.makeText(this, "3 test events added to queue", Toast.LENGTH_SHORT).show()
    }

    companion object {
        /**
         * Get the configured final reminder minutes from settings
         */
        fun getFinalReminderMinutes(context: Context): Int {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return prefs.getInt("final_reminder_minutes", 1) // Default to 1 minute before event
        }

        /**
         * Check if debug mode is enabled
         */
        fun isDebugModeEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return prefs.getBoolean("debug_mode", false)
        }

        /**
         * Check if screenshot mode is enabled
         */
        fun isScreenshotModeEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return prefs.getBoolean("screenshot_mode", false)
        }
    }
}
