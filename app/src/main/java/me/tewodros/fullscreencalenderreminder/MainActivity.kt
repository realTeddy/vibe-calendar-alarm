package me.tewodros.vibecalendaralarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.tewodros.vibecalendaralarm.data.ScreenshotEventData
import me.tewodros.vibecalendaralarm.databinding.ActivityMainBinding
import me.tewodros.vibecalendaralarm.ui.CalendarEventAdapter

/**
 * MainActivity serves as the primary interface for the Full Screen Calendar Reminder app.
 * * This activity handles:
 * - Permission management for calendar access, notifications, and system alerts
 * - Display of upcoming calendar events in a RecyclerView
 * - Automatic background alarm scheduling for all calendar events
 * - Battery optimization exemption requests for reliable alarm delivery
 * - Integration with WorkManager for continuous background monitoring
 * * Key Features:
 * - Auto-refreshing calendar event display
 * - One-tap alarm scheduling for all events
 * - Permission flow with user-friendly dialogs
 * - Battery optimization awareness
 * - Background WorkManager integration for 1-minute monitoring intervals
 * * The app automatically schedules 1-minute reminders for ALL calendar events,
 * regardless of their existing reminder settings, ensuring no events are missed.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var eventAdapter: CalendarEventAdapter
    private lateinit var calendarManager: CalendarManager

    companion object {
        private const val REQUEST_CALENDAR_PERMISSION = 1001
        private const val REQUEST_NOTIFICATION_PERMISSION = 1002
    }

    // Add flag to prevent recursive permission requests
    private var isRequestingPermissions = false
    // Track permissions that have been explicitly denied to avoid infinite requests
    private var calendarPermissionDenied = false
    // Track if overlay permission dialog has been shown to prevent re-showing
    private var overlayPermissionDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize CalendarManager without Wear support for now
        // (Wear sync happens via ReminderActivity which uses Hilt injection)
        calendarManager = CalendarManager(this)

        // Setup RecyclerView
        eventAdapter = CalendarEventAdapter()
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = eventAdapter
        }

        // Setup button click listeners
        setupClickListeners()

        // Check permissions and initialize
        checkPermissionsAndInitialize()
    }

    private fun refreshEventsDisplay() {
        Log.d("MainActivity", "Refreshing events display")

        // Check if screenshot mode is enabled
        if (SettingsActivity.isScreenshotModeEnabled(this)) {
            Log.d("MainActivity", "📸 Screenshot mode enabled - showing fake events")
            displayFakeEvents()
            return
        }

        if (hasCalendarPermission()) {
            lifecycleScope.launch {
                try {
                    binding.statusText.text = "Loading events..."
                    val events = calendarManager.getUpcomingEventsWithReminders()
                    eventAdapter.submitList(events)
                    binding.statusText.text = "Found ${events.size} upcoming events with reminders"
                    updateEventsCount(events.size)

                    // Only auto-schedule if it's been more than 5 minutes since last scheduling
                    // to prevent constant rescheduling on every app open
                    val lastScheduleTime = getSharedPreferences("alarm_prefs", MODE_PRIVATE)
                        .getLong("last_auto_schedule", 0)
                    val now = System.currentTimeMillis()
                    val fiveMinutes = 5 * 60 * 1000L

                    if (events.isNotEmpty() && hasAlarmPermission() && hasNotificationPermission() && (now - lastScheduleTime) > fiveMinutes) {
                        Log.d(
                            "MainActivity",
                            "Auto-scheduling alarms for ${events.size} events (last run: ${(now - lastScheduleTime) / 1000}s ago)",
                        )
                        try {
                            calendarManager.scheduleAllReminders()
                            // Save the time we did auto-scheduling
                            getSharedPreferences("alarm_prefs", MODE_PRIVATE)
                                .edit()
                                .putLong("last_auto_schedule", now)
                                .apply()
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Auto-scheduling failed: ${e.message}")
                        }
                    } else if (events.isNotEmpty() && (now - lastScheduleTime) <= fiveMinutes) {
                        Log.d(
                            "MainActivity",
                            "Skipping auto-schedule (too recent: ${(now - lastScheduleTime) / 1000}s ago)",
                        )
                    } else if (events.isNotEmpty()) {
                        Log.w(
                            "MainActivity",
                            "Events found but missing permissions for auto-scheduling",
                        )
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading events: ${e.message}")
                    binding.statusText.text = "Error loading events: ${e.message}"
                }
            }
        } else {
            Log.w("MainActivity", "⚠️ Cannot refresh events - missing calendar permission")
        }
    }

    private fun updateEventsCount(count: Int) {
        binding.eventsCountText.text = when (count) {
            0 -> "No upcoming events with reminders found"
            1 -> "1 event with reminders found"
            else -> "$count events with reminders found"
        }
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            refreshEventsDisplay()
        }

        binding.settingsChip.setOnClickListener {
            openSettings()
        }

        binding.calendarChip.setOnClickListener {
            openCalendarApp()
        }

        binding.permissionsChip.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun checkPermissionsAndInitialize() {
        // Prevent recursive calls during permission request flow
        if (isRequestingPermissions) {
            Log.d("MainActivity", "⏸️ Permission request already in progress, skipping...")
            return
        }

        when {
            !hasCalendarPermission() && !calendarPermissionDenied -> {
                isRequestingPermissions = true
                requestCalendarPermission()
            }
            !hasNotificationPermission() -> {
                isRequestingPermissions = true
                requestNotificationPermission()
            }
            !hasAlarmPermission() -> {
                isRequestingPermissions = true
                requestAlarmPermission()
            }
            !hasOverlayPermission() -> {
                isRequestingPermissions = true
                requestOverlayPermission()
            }
            else -> {
                // All permissions granted, reset flag and continue
                isRequestingPermissions = false

                // Check if we have critical permissions
                if (hasCalendarPermission()) {
                    // Check battery optimization after all permissions
                    checkAndOfferBatteryOptimizationFix()
                    initializeApp()
                } else {
                    Log.w("MainActivity", "⚠️ Critical calendar permission missing, cannot continue")
                    // Show UI to indicate missing critical permissions
                    // User needs to grant permissions manually
                }
            }
        }
    }

    private fun initializeApp() {
        Log.d("MainActivity", "Initializing app with all permissions granted")

        // Load and display events, which will trigger automatic scheduling
        refreshEventsDisplay()

        // Start background monitoring using the working CalendarManager approach
        ReminderWorkManager.startPeriodicMonitoring(this)

        binding.statusText.text = "✅ App ready - Calendar monitoring active"
    }

    /**
     * Open the settings activity
     */
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    /**
     * Open the default calendar app
     */
    private fun openCalendarApp() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to opening calendar via date
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("content://com.android.calendar/time")
            }
            try {
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "No calendar app found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Check and request all required permissions
     */
    private fun checkAndRequestPermissions() {
        // Prevent recursive calls during permission request flow
        if (isRequestingPermissions) {
            Log.d("MainActivity", "⏸️ Permission request already in progress, skipping...")
            return
        }

        when {
            !hasCalendarPermission() && !calendarPermissionDenied -> {
                isRequestingPermissions = true
                requestCalendarPermission()
            }
            !hasNotificationPermission() -> {
                isRequestingPermissions = true
                requestNotificationPermission()
            }
            !hasAlarmPermission() -> {
                isRequestingPermissions = true
                requestAlarmPermission()
            }
            else -> {
                // All permissions granted - no toast needed
                isRequestingPermissions = false
            }
        }
    }



    /**
     * Check battery optimization and guide user to whitelist the app
     */
    private fun checkAndOfferBatteryOptimizationFix() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(
                packageName,
            )

            if (!isIgnoringBatteryOptimizations) {
                // Show dialog explaining the issue and offering to fix it
                AlertDialog.Builder(this)
                    .setTitle("🚨 Critical: Battery Optimization Issue")
                    .setMessage(
                        """
                        ⚠️ IMPORTANT: Your device is preventing alarms from working!

                        Without fixing this, your reminders will NOT show up reliably.

                        This is the #1 cause of missed alarms. Please fix this now:
                        1. Tap 'Fix Now'
                        2. Find 'Vibe Calendar Alarm' in the list
                        3. Select 'Don't optimize' or 'Allow'

                        This is REQUIRED for the app to work properly!
                        """.trimIndent(),
                    )
                    .setPositiveButton("Fix Now") { _, _ ->
                        openBatteryOptimizationSettings()
                    }
                    .setNegativeButton("Skip (Not Recommended)") { _, _ ->
                        Toast.makeText(
                            this,
                            "⚠️ Reminders may not work reliably without fixing battery optimization",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    .setCancelable(false)
                    .show()
            } else {
                // Battery optimization already disabled - no toast needed
            }
        }
    }

    /**
     * Open battery optimization settings for the user to whitelist the app
     */
    private fun openBatteryOptimizationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Try to open directly to the app's battery optimization setting
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)

                Toast.makeText(
                    this,
                    "📱 Select 'Allow' to fix alarm reliability",
                    Toast.LENGTH_LONG,
                ).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening battery optimization settings: ${e.message}")

            // Fallback: Open general battery optimization settings
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
                Toast.makeText(
                    this,
                    "📱 Find this app and select 'Don't optimize'",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e2: Exception) {
                Log.e("MainActivity", "Error opening fallback battery settings: ${e2.message}")
                Toast.makeText(
                    this,
                    "❌ Please manually disable battery optimization in Settings > Apps",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    // Permission checking methods
    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed for older versions
        }
    }

    private fun hasAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Not needed for older versions
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(this)
            Log.d("MainActivity", "🔍 Overlay permission check: $hasPermission")
            hasPermission
        } else {
            Log.d("MainActivity", "🔍 Overlay permission not needed for API < 23")
            true // Not needed for older versions
        }
    }

    // Permission request methods
    private fun requestCalendarPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CALENDAR),
            REQUEST_CALENDAR_PERMISSION,
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION,
            )
        } else {
            checkPermissionsAndInitialize()
        }
    }

    private fun requestAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            checkPermissionsAndInitialize()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Check if user has already been asked multiple times
            val prefs = getSharedPreferences("permission_prefs", MODE_PRIVATE)
            val askCount = prefs.getInt("overlay_ask_count", 0)

            if (askCount >= 3) {
                // User has denied multiple times, don't keep asking
                Log.w(
                    "MainActivity",
                    "User has denied overlay permission multiple times, proceeding without it",
                )
                Toast.makeText(
                    this,
                    "⚠️ Alarms may not work reliably without display permission",
                    Toast.LENGTH_LONG,
                ).show()
                checkPermissionsAndInitialize()
                return
            }

            // Don't show dialog if it's already been shown in this session
            if (overlayPermissionDialogShown) {
                Log.d("MainActivity", "⏸️ Overlay permission dialog already shown in this session, skipping...")
                checkPermissionsAndInitialize()
                return
            }

            // Mark that we've shown the dialog in this session
            overlayPermissionDialogShown = true
            // Increment ask count
            prefs.edit().putInt("overlay_ask_count", askCount + 1).apply()

            AlertDialog.Builder(this)
                .setTitle("🚨 Critical: Display Permission Required")
                .setMessage(
                    """
                    ⚠️ IMPORTANT: This permission is REQUIRED for alarms to work!

                    Without "Display over other apps" permission, alarm reminders will NOT appear when your device is locked.

                    Steps:
                    1. Tap 'Grant Permission'
                    2. Find 'Vibe Calendar Alarm' in the list
                    3. Enable 'Allow display over other apps'
                    4. Press back to return to the app

                    This is essential for the app to function as an alarm!
                    """.trimIndent(),
                )
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setNegativeButton("Skip (Alarms Won't Work)") { _, _ ->
                    Toast.makeText(
                        this,
                        "⚠️ Alarms will NOT work reliably without display permission",
                        Toast.LENGTH_LONG,
                    ).show()
                    // Reset ask count if user grants other permissions
                    if (hasAlarmPermission() && hasCalendarPermission() && hasNotificationPermission()) {
                        checkAndOfferBatteryOptimizationFix()
                        initializeApp()
                    } else {
                        checkPermissionsAndInitialize()
                    }
                }
                .setCancelable(false)
                .show()
        } else {
            checkPermissionsAndInitialize()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Reset the flag when we receive a permission result
        isRequestingPermissions = false

        when (requestCode) {
            REQUEST_CALENDAR_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "✅ Calendar permission granted")
                    calendarPermissionDenied = false // Reset the denied flag
                    // Continue to check next permission only if granted
                    checkPermissionsAndInitialize()
                } else {
                    Log.e("MainActivity", "❌ Calendar permission denied")
                    calendarPermissionDenied = true // Mark as denied to prevent loops
                    Toast.makeText(
                        this,
                        "Calendar permission is required for this app to work. Please grant it in Settings.",
                        Toast.LENGTH_LONG,
                    ).show()
                    // Don't continue permission flow - calendar permission is critical
                    // User needs to grant it manually or restart the app
                }
            }
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "✅ Notification permission granted")
                } else {
                    Log.e("MainActivity", "❌ Notification permission denied")
                    Toast.makeText(
                        this,
                        "Notification permission is required for calendar reminders to work properly. You can enable it later in Settings.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                // Continue to check next permission (even if denied, as notifications are not critical)
                checkPermissionsAndInitialize()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "📱 App resumed - checking permissions and auto-scheduling")

        // Add a small delay to allow system to update permission status
        // This is especially important for overlay permission which is handled in Settings
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkPermissionStatusOnResume()
        }, 800) // 800ms delay to ensure system has time to register permission changes
    }

    private fun checkPermissionStatusOnResume() {
        // Reset permission request flag when returning from system settings
        isRequestingPermissions = false

        // Reset denied flags if permissions are now granted
        if (hasCalendarPermission()) {
            calendarPermissionDenied = false
        }

        // Check if all permissions were granted when returning from settings
        val hasCalendar = hasCalendarPermission()
        val hasAlarm = hasAlarmPermission()
        val hasNotification = hasNotificationPermission()
        val hasOverlay = hasOverlayPermission()

        Log.d("MainActivity", "⚡ Permission status check on resume:")
        Log.d("MainActivity", "  - Calendar: $hasCalendar")
        Log.d("MainActivity", "  - Alarm: $hasAlarm")
        Log.d("MainActivity", "  - Notification: $hasNotification")
        Log.d("MainActivity", "  - Overlay: $hasOverlay")

        if (hasAlarm && hasCalendar && hasNotification && hasOverlay) {
            Log.d("MainActivity", "🔄 All permissions available - initializing app")
            // Reset the overlay ask count and dialog flag since permission was granted
            val prefs = getSharedPreferences("permission_prefs", MODE_PRIVATE)
            prefs.edit().putInt("overlay_ask_count", 0).apply()
            overlayPermissionDialogShown = false // Reset dialog flag

            checkAndOfferBatteryOptimizationFix()
            initializeApp()
        } else {
            Log.w("MainActivity", "⚠️ Missing permissions on resume - continuing permission flow")
            // Only continue permission flow if we're missing critical permissions
            // Don't show the overlay permission dialog repeatedly if user keeps denying it
            if (!hasCalendar || !hasAlarm || !hasNotification) {
                checkPermissionsAndInitialize()
            } else if (!hasOverlay) {
                val prefs = getSharedPreferences("permission_prefs", MODE_PRIVATE)
                val askCount = prefs.getInt("overlay_ask_count", 0)
                if (askCount < 3 && !overlayPermissionDialogShown) {
                    Log.d("MainActivity", "🔄 Only overlay permission missing - requesting it")
                    requestOverlayPermission()
                } else {
                    Log.d("MainActivity", "🔄 Overlay permission denied too many times or dialog already shown - continuing without it")
                    checkAndOfferBatteryOptimizationFix()
                    initializeApp()
                }
            }
        }
    }

    /**
     * Display fake events for screenshot mode
     */
    private fun displayFakeEvents() {
        Log.d("MainActivity", "📸 Displaying fake events for screenshots")

        val fakeEvents = ScreenshotEventData.getFakeEvents()
        eventAdapter.submitList(fakeEvents)
        binding.statusText.text = "📸 Screenshot Mode: Showing ${fakeEvents.size} demo events"
        updateEventsCount(fakeEvents.size)
    }
}
