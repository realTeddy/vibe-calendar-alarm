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

        // Don't show toast here as it can interfere with full-screen activity
        // The full-screen activity will handle all user interaction

        // DO NOT CANCEL ALL ALARMS - let other alarms for this event fire independently
        // Each alarm is a one-time alarm and will be automatically cleaned up by Android
        Log.d(
            "AlarmReceiver",
            "✓ Preserving other alarms for event $eventId - this was just the $reminderType alarm",
        )

        // Create intent to launch full-screen reminder activity
        val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
            // Use aggressive flags to ensure activity shows over lock screen and other apps
            // FLAG_ACTIVITY_NEW_TASK: Required for launching from BroadcastReceiver
            // FLAG_ACTIVITY_CLEAR_TOP: Clear any existing instances
            // FLAG_ACTIVITY_SINGLE_TOP: Don't create multiple instances
            // FLAG_ACTIVITY_NO_USER_ACTION: Don't trigger user action events
            // FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS: Don't show in recent apps
            // FLAG_ACTIVITY_NO_HISTORY: Don't keep in back stack
            // FLAG_ACTIVITY_BROUGHT_TO_FRONT: Force bring to front even over other apps
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT

            // Pass event details to the activity
            putExtra("event_id", eventId)
            putExtra("event_title", eventTitle)
            putExtra("event_start_time", eventStartTime)
            putExtra("reminder_type", reminderType) // Include alarm type for display
        }

        try {
            // Launch the reminder activity
            Log.d("AlarmReceiver", "🚀 Attempting to launch ReminderActivity...")
            Log.d("AlarmReceiver", "Intent flags: ${reminderIntent.flags}")
            Log.d(
                "AlarmReceiver",
                "Intent extras: eventId=$eventId, title='$eventTitle', type=$reminderType",
            )

            context.startActivity(reminderIntent)
            Log.d(
                "AlarmReceiver",
                "✅ startActivity() call completed successfully for $reminderType alarm of '$eventTitle'",
            )

            // Give it a moment and then check if it actually launched
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Log.d("AlarmReceiver", "🔍 ReminderActivity should have launched by now...")
            }, 1000,)
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
