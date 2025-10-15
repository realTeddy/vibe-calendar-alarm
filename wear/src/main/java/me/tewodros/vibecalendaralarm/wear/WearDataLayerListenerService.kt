package me.tewodros.vibecalendaralarm.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.json.JSONObject

/**
 * Service that listens for data and messages from the phone app.
 * Handles:
 * - Calendar event data sync from phone
 * - Reminder scheduling requests
 * - Dismissal/snooze commands from phone
 */
@AndroidEntryPoint
class WearDataLayerListenerService : WearableListenerService() {

    @Inject
    lateinit var wearAlarmManager: WearAlarmManager

    private val TAG = "WearDataLayerListener"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎧 WearDataLayerListenerService created")
    }

    /**
     * Called when data items are added, changed, or deleted
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "📦 Data changed event received")

        dataEvents.forEach { event ->
            when (event.type) {
                DataEvent.TYPE_CHANGED, DataEvent.TYPE_DELETED -> {
                    val dataItem = event.dataItem
                    when (dataItem.uri.path) {
                        "/calendar_events" -> handleCalendarEventSync(dataItem, event.type)
                        "/reminder_schedule" -> handleReminderSchedule(dataItem)
                        "/reminder_cancel" -> handleReminderCancel(dataItem)
                    }
                }
            }
        }
    }

    /**
     * Called when messages are received from phone
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "💬 Message received: ${messageEvent.path}")

        when (messageEvent.path) {
            "/schedule_reminder" -> {
                val data = String(messageEvent.data)
                scheduleReminderFromMessage(data)
            }
            "/cancel_reminder" -> {
                val data = String(messageEvent.data)
                cancelReminderFromMessage(data)
            }
            "/dismiss_reminder" -> {
                val data = String(messageEvent.data)
                dismissReminderFromPhone(data)
            }
            "/sync_all_reminders" -> {
                Log.d(TAG, "🔄 Full sync requested from phone")
                // Will be handled via data items
            }
        }
    }

    /**
     * Handle calendar event synchronization from phone
     */
    private fun handleCalendarEventSync(dataItem: DataItem, type: Int) {
        try {
            if (type == DataEvent.TYPE_DELETED) {
                Log.d(TAG, "🗑️ Calendar events deleted")
                return
            }

            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            val eventsJson = dataMap.getString("events") ?: return

            Log.d(TAG, "📅 Syncing calendar events from phone...")

            // Parse and schedule reminders for each event
            val events = parseEventsJson(eventsJson)
            events.forEach { event ->
                scheduleReminderForEvent(event)
            }

            Log.d(TAG, "✅ Synced ${events.size} calendar events to watch")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to sync calendar events: ${e.message}", e)
        }
    }

    /**
     * Handle reminder scheduling from data item
     */
    private fun handleReminderSchedule(dataItem: DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            val eventId = dataMap.getLong("event_id")
            val eventTitle = dataMap.getString("event_title") ?: "Calendar Event"
            val eventStartTime = dataMap.getLong("event_start_time")
            val reminderTime = dataMap.getLong("reminder_time")

            wearAlarmManager.scheduleReminder(eventId, eventTitle, eventStartTime, reminderTime)

            Log.d(TAG, "✅ Scheduled reminder from data item: $eventTitle")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule reminder: ${e.message}", e)
        }
    }

    /**
     * Handle reminder cancellation
     */
    private fun handleReminderCancel(dataItem: DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            val eventId = dataMap.getLong("event_id")

            wearAlarmManager.cancelReminder(eventId)

            Log.d(TAG, "✅ Cancelled reminder from data item: Event ID $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel reminder: ${e.message}", e)
        }
    }

    /**
     * Schedule reminder from message data
     */
    private fun scheduleReminderFromMessage(data: String) {
        try {
            val json = JSONObject(data)
            val eventId = json.getLong("event_id")
            val eventTitle = json.getString("event_title")
            val eventStartTime = json.getLong("event_start_time")
            val reminderTime = json.getLong("reminder_time")

            wearAlarmManager.scheduleReminder(eventId, eventTitle, eventStartTime, reminderTime)

            Log.d(TAG, "✅ Scheduled reminder from message: $eventTitle")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse reminder message: ${e.message}", e)
        }
    }

    /**
     * Cancel reminder from message data
     */
    private fun cancelReminderFromMessage(data: String) {
        try {
            val eventId = data.toLong()
            wearAlarmManager.cancelReminder(eventId)

            Log.d(TAG, "✅ Cancelled reminder from message: Event ID $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse cancel message: ${e.message}", e)
        }
    }

    /**
     * Handle dismissal command from phone
     */
    private fun dismissReminderFromPhone(data: String) {
        try {
            val eventId = data.toLong()
            wearAlarmManager.cancelReminder(eventId)

            Log.d(TAG, "✅ Dismissed reminder from phone: Event ID $eventId")

            // If the reminder activity is currently showing, finish it
            // This would require additional state management or broadcast
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to dismiss reminder: ${e.message}", e)
        }
    }

    /**
     * Parse events JSON and extract event data
     */
    private fun parseEventsJson(json: String): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        try {
            val jsonArray = org.json.JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val eventJson = jsonArray.getJSONObject(i)
                events.add(
                    CalendarEvent(
                        id = eventJson.getLong("id"),
                        title = eventJson.getString("title"),
                        startTime = eventJson.getLong("start_time"),
                        reminderTime = eventJson.getLong("reminder_time")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse events JSON: ${e.message}", e)
        }
        return events
    }

    /**
     * Schedule reminder for a calendar event
     */
    private fun scheduleReminderForEvent(event: CalendarEvent) {
        // Only schedule if reminder time is in the future
        if (event.reminderTime > System.currentTimeMillis()) {
            wearAlarmManager.scheduleReminder(
                event.id,
                event.title,
                event.startTime,
                event.reminderTime
            )
        } else {
            Log.d(TAG, "⏭️ Skipping past reminder: ${event.title}")
        }
    }

    /**
     * Data class for calendar events
     */
    data class CalendarEvent(
        val id: Long,
        val title: String,
        val startTime: Long,
        val reminderTime: Long
    )
}
