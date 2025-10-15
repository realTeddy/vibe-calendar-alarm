package me.tewodros.vibecalendaralarm.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.tewodros.vibecalendaralarm.CalendarManager
import javax.inject.Inject

/**
 * Service that listens for messages from Wear OS devices.
 * Handles dismissal and snooze actions initiated on the watch.
 */
@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {

    @Inject
    lateinit var wearCommunicationManager: WearCommunicationManager

    @Inject
    lateinit var calendarManager: CalendarManager

    private val TAG = "PhoneWearListener"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎧 PhoneWearListenerService created")
    }

    /**
     * Called when a Wear device connects
     */
    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        Log.d(TAG, "🔗 Wear device connected: ${peer.displayName} (${peer.id})")

        // Trigger initial sync of all calendar events
        scope.launch {
            try {
                Log.d(TAG, "📅 Triggering calendar sync to newly connected watch...")
                calendarManager.scheduleAllReminders()
                Log.d(TAG, "✅ Initial sync completed for watch: ${peer.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to sync calendar to watch: ${e.message}", e)
            }
        }
    }

    /**
     * Called when a Wear device disconnects
     */
    override fun onPeerDisconnected(peer: Node) {
        super.onPeerDisconnected(peer)
        Log.d(TAG, "🔌 Wear device disconnected: ${peer.displayName}")
    }

    /**
     * Called when messages are received from watch
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "💬 Message received from watch: ${messageEvent.path}")

        when (messageEvent.path) {
            "/reminder_dismissed" -> {
                val data = String(messageEvent.data)
                handleWearDismissal(data)
            }
            "/reminder_snoozed" -> {
                val data = String(messageEvent.data)
                handleWearSnooze(data)
            }
        }
    }

    /**
     * Handle reminder dismissal from watch
     */
    private fun handleWearDismissal(data: String) {
        try {
            val eventId = data.removePrefix("dismiss:").toLong()
            Log.d(TAG, "✅ Reminder dismissed on watch, dismissing on phone: Event ID $eventId")

            // Broadcast to phone app to dismiss the reminder
            val intent = Intent("me.tewodros.vibecalendaralarm.DISMISS_FROM_WEAR").apply {
                putExtra("event_id", eventId)
            }
            sendBroadcast(intent)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to handle wear dismissal: ${e.message}", e)
        }
    }

    /**
     * Handle snooze from watch
     */
    private fun handleWearSnooze(data: String) {
        try {
            val parts = data.removePrefix("snooze:").split(":")
            val eventId = parts[0].toLong()
            val minutes = parts[1].toInt()

            Log.d(TAG, "⏰ Reminder snoozed on watch for $minutes minutes: Event ID $eventId")

            // Broadcast to phone app to snooze the reminder
            val intent = Intent("me.tewodros.vibecalendaralarm.SNOOZE_FROM_WEAR").apply {
                putExtra("event_id", eventId)
                putExtra("snooze_minutes", minutes)
            }
            sendBroadcast(intent)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to handle wear snooze: ${e.message}", e)
        }
    }
}
