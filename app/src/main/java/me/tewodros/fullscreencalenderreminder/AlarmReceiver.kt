package me.tewodros.vibecalendaralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Receives alarm broadcasts and launches the full-screen reminder activity
 * Keeps it simple - just launch the activity with event details
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "=== ALARM FIRED ===")

        // Extract event details from the alarm intent
        val eventId = intent.getLongExtra("event_id", -1)
        val eventTitle = intent.getStringExtra("event_title") ?: "Reminder"
        val eventStartTime = intent.getLongExtra("event_start_time", 0)
        val reminderType = intent.getStringExtra("reminder_type") ?: "UNKNOWN"
        val action = intent.action ?: "NO_ACTION"

        Log.d(
            "AlarmReceiver",
            "Event details: ID=$eventId, Title='$eventTitle', StartTime=$eventStartTime",
        )
        Log.d("AlarmReceiver", "Alarm type: $reminderType, Action: $action")
        Log.d("AlarmReceiver", "Current time: ${System.currentTimeMillis()}")

        // Create pending alarm object
        val pendingAlarm = PendingAlarmsManager.PendingAlarm(
            eventId = eventId,
            eventTitle = eventTitle,
            eventStartTime = eventStartTime,
            reminderType = reminderType
        )

        // Add to pending alarms queue
        PendingAlarmsManager.addAlarm(pendingAlarm)

        // Check if ReminderActivity is already active
        if (PendingAlarmsManager.isActivityActive()) {
            Log.d("AlarmReceiver", "✓ ReminderActivity is already active, alarm added to queue")
            // Activity will be notified automatically through callback
            return
        }

        // Launch ReminderActivity if not already active
        Log.d("AlarmReceiver", "🚀 Launching new ReminderActivity...")

        // Create intent to launch full-screen reminder activity
        val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
            // Simplified flags - allow multiple events in one activity
            // FLAG_ACTIVITY_NEW_TASK: Required for launching from BroadcastReceiver
            // FLAG_ACTIVITY_NO_USER_ACTION: Don't trigger user action events
            // FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS: Don't show in recent apps
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

            // Pass event details to the activity
            putExtra("event_id", eventId)
            putExtra("event_title", eventTitle)
            putExtra("event_start_time", eventStartTime)
            putExtra("reminder_type", reminderType)
        }

        try {
            // Launch the reminder activity
            Log.d("AlarmReceiver", "🚀 Attempting to launch ReminderActivity...")
            Log.d("AlarmReceiver", "Intent flags: ${reminderIntent.flags}")

            context.startActivity(reminderIntent)
            Log.d(
                "AlarmReceiver",
                "✅ startActivity() call completed successfully for $reminderType alarm of '$eventTitle'",
            )
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "❌ Failed to launch ReminderActivity: ${e.message}")
            Log.e("AlarmReceiver", "Exception type: ${e.javaClass.simpleName}")
            Log.e("AlarmReceiver", "Stack trace:", e)
            // Show a more persistent notification as fallback
            Toast.makeText(
                context,
                "⚠️ ALARM: $eventTitle - Check app settings for 'Display over other apps'",
                Toast.LENGTH_LONG,
            ).show()
        }
        Log.d("AlarmReceiver", "=== ALARM PROCESSING COMPLETE ===")
    }
}
