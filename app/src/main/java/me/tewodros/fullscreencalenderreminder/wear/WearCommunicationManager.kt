package me.tewodros.vibecalendaralarm.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for communicating with Wear OS devices.
 * Handles syncing calendar events and reminders to connected watches.
 */
@Singleton
class WearCommunicationManager @Inject constructor(
    @ApplicationContext context: Context
) {
    // Secondary constructor for non-Hilt usage
    constructor(context: Context, unused: Unit = Unit) : this(context)

    private val context: Context = context.applicationContext
    private val dataClient: DataClient = Wearable.getDataClient(this.context)
    private val messageClient: MessageClient = Wearable.getMessageClient(this.context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(this.context)

    private val TAG = "WearCommunicationMgr"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        Log.d(TAG, "🚀 WearCommunicationManager initialized")
    }

    /**
     * Check if any Wear OS devices are connected
     */
    suspend fun isWearDeviceConnected(): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val connected = nodes.isNotEmpty()
            Log.d(TAG, "🔗 Wear devices connected: $connected (${nodes.size} devices)")
            connected
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to check Wear connection: ${e.message}")
            false
        }
    }

    /**
     * Sync a single reminder to all connected watches
     */
    fun syncReminderToWear(
        eventId: Long,
        eventTitle: String,
        eventStartTime: Long,
        reminderTime: Long
    ) {
        Log.d(TAG, "🔄 syncReminderToWear called for: $eventTitle")
        scope.launch {
            try {
                Log.d(TAG, "🔍 Checking for connected Wear devices...")
                if (!isWearDeviceConnected()) {
                    Log.w(TAG, "⏭️ No Wear devices connected, skipping sync for: $eventTitle")
                    return@launch
                }
                Log.d(TAG, "✅ Wear device found, proceeding with sync...")

                // Send via message for immediate delivery
                val json = JSONObject().apply {
                    put("event_id", eventId)
                    put("event_title", eventTitle)
                    put("event_start_time", eventStartTime)
                    put("reminder_time", reminderTime)
                }

                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/schedule_reminder",
                        json.toString().toByteArray()
                    ).await()

                    Log.d(TAG, "📤 Sent reminder to watch ${node.displayName}: $eventTitle")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to sync reminder to Wear: ${e.message}", e)
            }
        }
    }

    /**
     * Sync multiple calendar events to watch
     */
    fun syncCalendarEventsToWear(events: List<CalendarEventData>) {
        scope.launch {
            try {
                if (!isWearDeviceConnected()) {
                    Log.d(TAG, "⏭️ No Wear devices connected, skipping sync")
                    return@launch
                }

                // Build JSON array of events
                val eventsJson = JSONArray()
                events.forEach { event ->
                    eventsJson.put(
                        JSONObject().apply {
                            put("id", event.id)
                            put("title", event.title)
                            put("start_time", event.startTime)
                            put("reminder_time", event.reminderTime)
                        }
                    )
                }

                // Send via Data API for reliable delivery
                val putDataRequest = PutDataMapRequest.create("/calendar_events").apply {
                    dataMap.putString("events", eventsJson.toString())
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                dataClient.putDataItem(putDataRequest).await()

                Log.d(TAG, "✅ Synced ${events.size} calendar events to Wear")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to sync calendar events to Wear: ${e.message}", e)
            }
        }
    }

    /**
     * Cancel a reminder on all connected watches
     */
    fun cancelReminderOnWear(eventId: Long) {
        scope.launch {
            try {
                if (!isWearDeviceConnected()) {
                    return@launch
                }

                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/cancel_reminder",
                        eventId.toString().toByteArray()
                    ).await()

                    Log.d(TAG, "📤 Sent cancel command to watch ${node.displayName} for event $eventId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to cancel reminder on Wear: ${e.message}", e)
            }
        }
    }

    /**
     * Notify watch that a reminder was dismissed on phone
     */
    fun notifyWearOfDismissal(eventId: Long) {
        scope.launch {
            try {
                if (!isWearDeviceConnected()) {
                    return@launch
                }

                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/dismiss_reminder",
                        eventId.toString().toByteArray()
                    ).await()

                    Log.d(TAG, "📤 Notified watch ${node.displayName} of dismissal for event $eventId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to notify Wear of dismissal: ${e.message}", e)
            }
        }
    }

    /**
     * Request full sync of all reminders
     */
    fun requestFullSyncToWear() {
        scope.launch {
            try {
                if (!isWearDeviceConnected()) {
                    Log.d(TAG, "⏭️ No Wear devices connected, skipping full sync")
                    return@launch
                }

                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        "/sync_all_reminders",
                        ByteArray(0)
                    ).await()

                    Log.d(TAG, "📤 Requested full sync from watch ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to request full sync: ${e.message}", e)
            }
        }
    }

    /**
     * Get list of connected Wear devices
     */
    suspend fun getConnectedDevices(): List<String> {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            nodes.map { it.displayName }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get connected devices: ${e.message}")
            emptyList()
        }
    }

    /**
     * Data class for calendar event sync
     */
    data class CalendarEventData(
        val id: Long,
        val title: String,
        val startTime: Long,
        val reminderTime: Long
    )

    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
    }
}
