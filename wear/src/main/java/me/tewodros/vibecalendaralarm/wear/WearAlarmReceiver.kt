package me.tewodros.vibecalendaralarm.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Receiver that handles alarm broadcasts and launches the full-screen reminder activity
 */
class WearAlarmReceiver : BroadcastReceiver() {

    private val TAG = "WearAlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔔 Alarm received on watch: ${intent.action}")

        if (intent.action == "me.tewodros.vibecalendaralarm.wear.REMINDER_ALARM") {
            val eventId = intent.getLongExtra("event_id", -1)
            val eventTitle = intent.getStringExtra("event_title") ?: "Calendar Event"
            val eventStartTime = intent.getLongExtra("event_start_time", 0)
            val reminderTime = intent.getLongExtra("reminder_time", 0)

            Log.d(TAG, "📱 Launching full-screen reminder for: $eventTitle (ID: $eventId)")

            // Acquire wake lock to ensure screen turns on
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "VibeCal:WearAlarm"
            )
            wakeLock.acquire(5000) // 5 seconds to launch activity

            // Launch full-screen reminder activity
            val reminderIntent = Intent(context, WearReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("event_id", eventId)
                putExtra("event_title", eventTitle)
                putExtra("event_start_time", eventStartTime)
                putExtra("reminder_time", reminderTime)
            }

            try {
                context.startActivity(reminderIntent)
                Log.d(TAG, "✅ WearReminderActivity launched successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to launch WearReminderActivity: ${e.message}", e)
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }
}
