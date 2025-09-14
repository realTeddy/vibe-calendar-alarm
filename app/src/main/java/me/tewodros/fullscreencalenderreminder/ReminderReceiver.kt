package me.tewodros.vibecalendaralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling alarm reminders
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ReminderReceiver", "🔔 Reminder alarm received")

        val eventTitle = intent.getStringExtra("eventTitle") ?: "Calendar Event"
        val eventId = intent.getLongExtra("eventId", -1L)

        // Launch the full-screen reminder activity with flags to show over lock screen
        val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("eventTitle", eventTitle)
            putExtra("eventId", eventId)
            putExtra("reminderType", "alarm")
        }

        try {
            context.startActivity(reminderIntent)
            Log.d("ReminderReceiver", "✅ Launched reminder activity for: $eventTitle")
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "❌ Failed to launch reminder activity: ${e.message}")
        }
    }
}
