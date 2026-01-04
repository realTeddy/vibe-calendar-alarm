package me.tewodros.vibecalendaralarm

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton manager for handling multiple pending alarm events.
 * Uses ConcurrentHashMap for thread-safe access without synchronized blocks.
 * Emits updates via StateFlow to avoid callback-related deadlocks.
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
        val calendarName: String = "Unknown Calendar",
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        /**
         * Generate a unique key for this alarm
         */
        val key: String get() = "${eventId}_$reminderType"
    }

    // Thread-safe map using ConcurrentHashMap - no synchronized blocks needed
    private val pendingAlarmsMap = ConcurrentHashMap<String, PendingAlarm>()

    // StateFlow for reactive updates - observers collect this instead of using callbacks
    private val _alarmsFlow = MutableStateFlow<List<PendingAlarm>>(emptyList())
    val alarmsFlow: StateFlow<List<PendingAlarm>> = _alarmsFlow.asStateFlow()

    // Legacy callback support (for gradual migration)
    @Volatile
    private var legacyCallback: ((List<PendingAlarm>) -> Unit)? = null

    /**
     * Add a new alarm to the queue
     */
    fun addAlarm(alarm: PendingAlarm) {
        Log.d(TAG, "Adding alarm: ${alarm.eventTitle} (ID: ${alarm.eventId})")

        // putIfAbsent returns null if key didn't exist (alarm was added)
        val existing = pendingAlarmsMap.putIfAbsent(alarm.key, alarm)

        if (existing == null) {
            Log.d(TAG, "Alarm added. Total pending: ${pendingAlarmsMap.size}")
            emitUpdate()
        } else {
            Log.d(TAG, "Alarm already exists, skipping duplicate")
        }
    }

    /**
     * Remove an alarm from the queue
     */
    fun removeAlarm(eventId: Long, reminderType: String) {
        val key = "${eventId}_$reminderType"
        Log.d(TAG, "Removing alarm: eventId=$eventId, type=$reminderType")

        val removed = pendingAlarmsMap.remove(key)

        if (removed != null) {
            Log.d(TAG, "Alarm removed. Remaining: ${pendingAlarmsMap.size}")
            emitUpdate()
        }
    }

    /**
     * Get all pending alarms (snapshot)
     */
    fun getAllAlarms(): List<PendingAlarm> {
        return pendingAlarmsMap.values.toList()
    }

    /**
     * Check if there are any pending alarms
     */
    fun hasPendingAlarms(): Boolean {
        return pendingAlarmsMap.isNotEmpty()
    }

    /**
     * Clear all pending alarms
     */
    fun clearAll() {
        Log.d(TAG, "Clearing all ${pendingAlarmsMap.size} pending alarms")
        pendingAlarmsMap.clear()
        emitUpdate()
    }

    /**
     * Register callback for legacy support
     * @deprecated Use alarmsFlow.collect() instead
     */
    @Deprecated("Use alarmsFlow.collect() instead for reactive updates")
    fun registerActivityCallback(callback: (List<PendingAlarm>) -> Unit) {
        Log.d(TAG, "Legacy activity callback registered")
        legacyCallback = callback
        // Immediately send current alarms
        callback(getAllAlarms())
    }

    /**
     * Unregister the legacy activity callback
     * @deprecated Use alarmsFlow.collect() instead
     */
    @Deprecated("Use alarmsFlow.collect() instead for reactive updates")
    fun unregisterActivityCallback() {
        Log.d(TAG, "Legacy activity callback unregistered")
        legacyCallback = null
    }

    /**
     * Check if a legacy activity callback is registered
     */
    fun isActivityActive(): Boolean {
        return legacyCallback != null
    }

    /**
     * Emit update to StateFlow and legacy callback
     */
    private fun emitUpdate() {
        // Take snapshot immediately to avoid TOCTOU race condition
        val alarms = pendingAlarmsMap.values.toList()

        // Update StateFlow (main reactive mechanism) - use direct assignment for atomicity
        _alarmsFlow.value = alarms

        // Also notify legacy callback if registered
        legacyCallback?.invoke(alarms)
    }

    private const val TAG = "PendingAlarmsManager"
}

