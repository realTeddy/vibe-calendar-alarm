package me.tewodros.vibecalendaralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives boot completed broadcast and starts the reminder scheduler service
 * This ensures the background service starts automatically when device boots
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            -> {
                Log.d(
                    "BootReceiver",
                    "Starting WorkManager monitoring and rescheduling alarms after boot/update",
                )

                // Start WorkManager-based periodic checking
                ReminderWorkManager.startPeriodicMonitoring(context)

                // CRITICAL: Reschedule all alarms immediately after boot
                // Alarms are lost on reboot and need to be recreated
                try {
                    val calendarManager = CalendarManager(context)
                    calendarManager.scheduleAllReminders()
                    Log.d("BootReceiver", "✅ All alarms rescheduled successfully after boot")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "❌ Failed to reschedule alarms after boot: ${e.message}")
                }

                Log.d("BootReceiver", "WorkManager monitoring started successfully")
            }
        }
    }
}
