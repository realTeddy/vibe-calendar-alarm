package me.tewodros.fullscreencalenderreminder.util

import android.content.Context
import android.util.Log
import me.tewodros.fullscreencalenderreminder.model.CalendarEvent

/**
 * Fallback strategies for when primary operations fail
 * Provides alternative approaches to maintain app functionality
 */
object FallbackStrategy {

    private const val TAG = "FallbackStrategy"

    /**
     * Fallback when calendar access fails completely
     */
    fun handleCalendarAccessFallback(context: Context): List<CalendarEvent> {
        Log.w(TAG, "Using fallback: returning empty event list due to calendar access failure")

        // Could implement:
        // 1. Cached events from previous successful queries
        // 2. Manual event input by user
        // 3. Default test events for demo purposes

        return emptyList()
    }

    /**
     * Fallback when alarm scheduling fails
     */
    fun handleAlarmSchedulingFallback(context: Context, event: CalendarEvent, reminderMinutes: Int): Boolean {
        Log.w(TAG, "Alarm scheduling failed for '${event.title}', attempting fallback strategies")

        return try {
            // Strategy 1: Try scheduling with less precise timing
            Log.d(TAG, "Fallback: Attempting less precise alarm scheduling")
            // Implementation would use setInexactRepeating or similar

            // Strategy 2: Create notification-based reminder instead of alarm
            Log.d(TAG, "Fallback: Could create notification-based reminder")
            // Implementation would schedule a notification instead

            // Strategy 3: Add to system's default reminder/alarm app
            Log.d(TAG, "Fallback: Could integrate with system alarm app")

            // For now, just log the attempt
            false
        } catch (e: Exception) {
            Log.e(TAG, "All fallback strategies failed for '${event.title}'", e)
            false
        }
    }

    /**
     * Fallback when WorkManager fails to schedule
     */
    fun handleWorkManagerFallback(context: Context): Boolean {
        Log.w(TAG, "WorkManager scheduling failed, attempting fallback")

        return try {
            // Strategy 1: Use AlarmManager for periodic checks instead
            Log.d(TAG, "Fallback: Could use AlarmManager for periodic background checks")

            // Strategy 2: Increase check frequency when app is active
            Log.d(TAG, "Fallback: Could increase foreground refresh frequency")

            // Strategy 3: Prompt user to manually refresh more often
            Log.d(TAG, "Fallback: Could show user guidance for manual refresh")

            false
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager fallback strategies failed", e)
            false
        }
    }

    /**
     * Fallback when device is in battery saver mode
     */
    fun handleBatterySaverFallback(context: Context): String {
        Log.w(TAG, "Device in battery saver mode, adjusting behavior")

        // Reduce background activity
        // Increase manual refresh prompts
        // Show user explanation about battery optimization

        return "Battery saver detected. Reminders may be delayed. Consider disabling battery optimization for this app."
    }

    /**
     * Fallback when permissions are permanently denied
     */
    fun handlePermissionDeniedFallback(context: Context, permission: String): String {
        Log.w(TAG, "Permission permanently denied: $permission")

        return when (permission) {
            android.Manifest.permission.READ_CALENDAR -> {
                "Calendar access denied. You can manually add events or enable permission in app settings."
            }
            android.Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                "Overlay permission denied. Reminders will show as notifications instead of full-screen alerts."
            }
            "android.permission.SCHEDULE_EXACT_ALARM" -> {
                "Exact alarm permission denied. Reminders may not be precisely timed."
            }
            else -> {
                "Permission denied for $permission. Some features may not work properly."
            }
        }
    }

    /**
     * Provide alternative reminder methods when primary methods fail
     */
    fun getAlternativeReminderMethods(context: Context): List<String> {
        return listOf(
            "Manual refresh of the app",
            "Device's built-in calendar notifications",
            "Third-party reminder apps",
            "System alarm clock for important events",
            "Phone calendar widget on home screen",
        )
    }

    /**
     * Create a degraded but functional experience
     */
    fun createDegradedModeExperience(context: Context): Map<String, String> {
        Log.i(TAG, "Entering degraded mode for limited functionality")

        return mapOf(
            "status" to "Limited functionality mode",
            "explanation" to "Some features are unavailable due to system limitations",
            "available_features" to "Manual event viewing, basic settings",
            "unavailable_features" to "Automatic reminders, background monitoring",
            "user_action" to "Check app permissions and battery optimization settings",
        )
    }

    /**
     * Generate helpful guidance for users when things go wrong
     */
    fun generateUserGuidance(error: Throwable, context: Context): List<String> {
        val guidance = mutableListOf<String>()

        when {
            error is SecurityException -> {
                guidance.addAll(
                    listOf(
                        "Check that calendar permission is granted",
                        "Go to Settings > Apps > Calendar Reminders > Permissions",
                        "Enable all required permissions",
                    ),
                )
            }
            error.message?.contains("battery", ignoreCase = true) == true -> {
                guidance.addAll(
                    listOf(
                        "Disable battery optimization for this app",
                        "Go to Settings > Battery > Battery Optimization",
                        "Select 'Don't optimize' for Calendar Reminders",
                    ),
                )
            }
            error.message?.contains("alarm", ignoreCase = true) == true -> {
                guidance.addAll(
                    listOf(
                        "Enable exact alarm scheduling",
                        "Go to Settings > Apps > Special Access > Alarms & Reminders",
                        "Allow this app to schedule exact alarms",
                    ),
                )
            }
            else -> {
                guidance.addAll(
                    listOf(
                        "Try restarting the app",
                        "Check your device's calendar app is working",
                        "Ensure you have calendar events with reminders set",
                    ),
                )
            }
        }

        return guidance
    }
}
