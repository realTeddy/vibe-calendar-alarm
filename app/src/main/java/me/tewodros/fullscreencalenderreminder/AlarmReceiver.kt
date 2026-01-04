package me.tewodros.vibecalendaralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Receives alarm broadcasts and launches the full-screen reminder activity
 * Includes input validation to prevent crashes from malformed intents
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "=== ALARM FIRED ===")

        // Extract and validate event details from the alarm intent
        val eventId = intent.getLongExtra("event_id", INVALID_EVENT_ID)
        val eventTitle = intent.getStringExtra("event_title")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TITLE
        val eventStartTime = intent.getLongExtra("event_start_time", INVALID_TIME)
        val calendarName = intent.getStringExtra("calendar_name")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_CALENDAR
        val reminderType = intent.getStringExtra("reminder_type")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TYPE
        val action = intent.action ?: "NO_ACTION"

        // Validate required fields
        if (!validateAlarmData(eventId, eventTitle, action)) {
            Log.e(TAG, "❌ Invalid alarm data - aborting")
            return
        }

        Log.d(
            TAG,
            "Event details: ID=$eventId, Title='$eventTitle', StartTime=$eventStartTime, Calendar='$calendarName'",
        )
        Log.d(TAG, "Alarm type: $reminderType, Action: $action")
        Log.d(TAG, "Current time: ${System.currentTimeMillis()}")

        // Verify the event still exists in the calendar before showing reminder
        // Skip verification for snoozed alarms or alarms without valid start time
        val isSnoozeAlarm = reminderType?.startsWith("SNOOZE_") == true
        val shouldVerify = eventStartTime > 0 && reminderType != DEFAULT_TYPE && !isSnoozeAlarm

        if (shouldVerify) {
            val calendarManager = CalendarManager(context)
            if (!calendarManager.verifyEventExists(eventId, eventStartTime)) {
                Log.w(
                    TAG,
                    "⚠️ Event $eventId no longer exists or has been modified - skipping reminder",
                )
                // Cancel any remaining alarms for this event
                calendarManager.cancelReminder(eventId)
                return
            }
            Log.d(TAG, "✓ Event verified - continuing with reminder")
        } else {
            Log.d(TAG, "⏰ Skipping verification (snoozed or invalid start time)")
        }

        // Create pending alarm object
        val pendingAlarm = PendingAlarmsManager.PendingAlarm(
            eventId = eventId,
            eventTitle = eventTitle,
            eventStartTime = eventStartTime,
            reminderType = reminderType,
            calendarName = calendarName,
        )

        // Add to pending alarms queue
        PendingAlarmsManager.addAlarm(pendingAlarm)

        // Check if ReminderActivity is already active
        if (PendingAlarmsManager.isActivityActive()) {
            Log.d(TAG, "✓ ReminderActivity is already active, alarm added to queue")
            // Activity will be notified automatically through callback/StateFlow
            return
        }

        // Launch ReminderActivity if not already active
        launchReminderActivity(context, eventId, eventTitle, eventStartTime, reminderType)

        Log.d(TAG, "=== ALARM PROCESSING COMPLETE ===")
    }

    /**
     * Validate alarm data to prevent crashes from malformed intents
     */
    private fun validateAlarmData(eventId: Long, eventTitle: String, action: String): Boolean {
        // Event ID must be valid (not -1)
        if (eventId == INVALID_EVENT_ID) {
            Log.e(TAG, "Invalid event ID: $eventId")
            return false
        }

        // Event title should exist (we have a fallback but log if missing)
        if (eventTitle == DEFAULT_TITLE) {
            Log.w(TAG, "Event title was missing, using default")
        }

        // Action should match our expected pattern
        if (!action.startsWith("REMINDER_ALARM_") && action != "NO_ACTION") {
            Log.w(TAG, "Unexpected action format: $action")
            // Still allow - could be a snoozed alarm
        }

        return true
    }

    /**
     * Launch the ReminderActivity with proper flags
     */
    private fun launchReminderActivity(
        context: Context,
        eventId: Long,
        eventTitle: String,
        eventStartTime: Long,
        reminderType: String,
    ) {
        Log.d(TAG, "🚀 Launching new ReminderActivity...")

        val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
            // FLAG_ACTIVITY_NEW_TASK: Required for launching from BroadcastReceiver
            // FLAG_ACTIVITY_NO_USER_ACTION: Don't trigger user action events
            // FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS: Don't show in recent apps
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

            putExtra("event_id", eventId)
            putExtra("event_title", eventTitle)
            putExtra("event_start_time", eventStartTime)
            putExtra("reminder_type", reminderType)
        }

        try {
            context.startActivity(reminderIntent)
            Log.d(
                TAG,
                "✅ ReminderActivity launched for '$eventTitle' ($reminderType)",
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to launch ReminderActivity: ${e.message}", e)
            // Show fallback notification
            Toast.makeText(
                context,
                "⚠️ ALARM: $eventTitle - Enable 'Display over other apps' in settings",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val INVALID_EVENT_ID = -1L
        private const val INVALID_TIME = 0L
        private const val DEFAULT_TITLE = "Reminder"
        private const val DEFAULT_CALENDAR = "Unknown Calendar"
        private const val DEFAULT_TYPE = "UNKNOWN"
    }
}
