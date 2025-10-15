package me.tewodros.vibecalendaralarm

import android.util.Log

/**
 * Singleton manager for handling multiple pending alarm events.
 * Allows multiple alarms to be displayed in a single ReminderActivity.
 */
object PendingAlarmsManager {

    /**
     * Data class representing a pending alarm event
     */
    data class PendingAlarm(
        val eventId: Long,
        val eventTitle: String,
        val eventStartTime: Long,
        val reminderType: String,
        val timestamp: Long = System.currentTimeMillis() // When alarm was triggered
    )

    private val pendingAlarms = mutableListOf<PendingAlarm>()
    private var activeActivityCallback: ((List<PendingAlarm>) -> Unit)? = null

    /**
     * Add a new alarm to the queue
     */
    @Synchronized
    fun addAlarm(alarm: PendingAlarm) {
        Log.d("PendingAlarmsManager", "Adding alarm: ${alarm.eventTitle} (ID: ${alarm.eventId})")

        // Check if this alarm already exists (prevent duplicates)
        val exists = pendingAlarms.any {
            it.eventId == alarm.eventId && it.reminderType == alarm.reminderType
        }

        if (!exists) {
            pendingAlarms.add(alarm)
            Log.d("PendingAlarmsManager", "Alarm added. Total pending: ${pendingAlarms.size}")

            // Notify active activity if present
            activeActivityCallback?.invoke(getAllAlarms())
        } else {
            Log.d("PendingAlarmsManager", "Alarm already exists, skipping duplicate")
        }
    }

    /**
     * Remove an alarm from the queue
     */
    @Synchronized
    fun removeAlarm(eventId: Long, reminderType: String) {
        Log.d("PendingAlarmsManager", "Removing alarm: eventId=$eventId, type=$reminderType")

        val removed = pendingAlarms.removeAll {
            it.eventId == eventId && it.reminderType == reminderType
        }

        if (removed) {
            Log.d("PendingAlarmsManager", "Alarm removed. Remaining: ${pendingAlarms.size}")

            // Notify active activity
            activeActivityCallback?.invoke(getAllAlarms())
        }
    }

    /**
     * Get all pending alarms
     */
    @Synchronized
    fun getAllAlarms(): List<PendingAlarm> {
        return pendingAlarms.toList()
    }

    /**
     * Check if there are any pending alarms
     */
    @Synchronized
    fun hasPendingAlarms(): Boolean {
        return pendingAlarms.isNotEmpty()
    }

    /**
     * Clear all pending alarms
     */
    @Synchronized
    fun clearAll() {
        Log.d("PendingAlarmsManager", "Clearing all ${pendingAlarms.size} pending alarms")
        pendingAlarms.clear()
        activeActivityCallback?.invoke(emptyList())
    }

    /**
     * Register callback for when the active ReminderActivity needs updates
     */
    @Synchronized
    fun registerActivityCallback(callback: (List<PendingAlarm>) -> Unit) {
        Log.d("PendingAlarmsManager", "Activity callback registered")
        activeActivityCallback = callback
        // Immediately send current alarms to the activity
        callback(getAllAlarms())
    }

    /**
     * Unregister the activity callback
     */
    @Synchronized
    fun unregisterActivityCallback() {
        Log.d("PendingAlarmsManager", "Activity callback unregistered")
        activeActivityCallback = null
    }

    /**
     * Check if an activity is currently active
     */
    fun isActivityActive(): Boolean {
        return activeActivityCallback != null
    }
}
