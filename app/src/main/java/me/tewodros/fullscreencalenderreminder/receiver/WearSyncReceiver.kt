package me.tewodros.vibecalendaralarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.tewodros.vibecalendaralarm.CalendarManager
import me.tewodros.vibecalendaralarm.wear.WearCommunicationManager

/**
 * Receiver that triggers calendar sync to Wear when phone boots
 * or when explicitly requested via broadcast.
 */
class WearSyncReceiver : BroadcastReceiver() {

    private val TAG = "WearSyncReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📡 Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "me.tewodros.vibecalendaralarm.SYNC_TO_WEAR" -> {
                Log.d(TAG, "🔄 Triggering calendar sync to Wear devices...")

                try {
                    // Create managers and trigger sync
                    val wearManager = WearCommunicationManager(context.applicationContext)
                    val calendarManager = CalendarManager(context, wearManager)
                    calendarManager.scheduleAllReminders()
                    Log.d(TAG, "✅ Wear sync completed")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Wear sync failed: ${e.message}", e)
                }
            }
        }
    }
}
