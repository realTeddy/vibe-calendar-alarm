package me.tewodros.vibecalendaralarm.repository

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.tewodros.vibecalendaralarm.data.AlarmScheduler
import me.tewodros.vibecalendaralarm.data.CalendarDataSource
import me.tewodros.vibecalendaralarm.data.Constants
import me.tewodros.vibecalendaralarm.data.EventCache
import me.tewodros.vibecalendaralarm.data.PreferencesManager
import me.tewodros.vibecalendaralarm.model.CalendarEvent

/**
 * Unified calendar repository implementation
 * Coordinates CalendarDataSource, AlarmScheduler, EventCache, and PreferencesManager
 *
 * This is the single source of truth for calendar operations, eliminating
 * the duplication between the old CalendarManager and CalendarRepositoryImpl
 */
@Singleton
class CalendarRepositoryV2 @Inject constructor(
    private val calendarDataSource: CalendarDataSource,
    private val alarmScheduler: AlarmScheduler,
    private val eventCache: EventCache,
    private val preferencesManager: PreferencesManager,
) {

    // region State

    private val _schedulingState = MutableStateFlow<SchedulingState>(SchedulingState.Idle)
    val schedulingState: StateFlow<SchedulingState> = _schedulingState.asStateFlow()

    /**
     * State of scheduling operations
     */
    sealed class SchedulingState {
        object Idle : SchedulingState()
        object Loading : SchedulingState()
        data class Success(
            val eventsProcessed: Int,
            val alarmsScheduled: Int,
            val alarmsSkipped: Int,
            val deletedEventsCleaned: Int,
        ) : SchedulingState()
        data class Error(val message: String, val exception: Throwable? = null) : SchedulingState()
    }

    // endregion

    // region Public API

    /**
     * Check if calendar permission is granted
     */
    fun hasCalendarPermission(): Boolean = calendarDataSource.hasCalendarPermission()

    /**
     * Get upcoming events with reminders
     * Uses caching for performance
     */
    suspend fun getUpcomingEventsWithReminders(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        // Check cache first
        eventCache.getCachedEvents()?.let { return@withContext it }

        // Fetch fresh data
        val events = calendarDataSource.queryUpcomingEvents(
            daysAhead = Constants.QUERY_DAYS_AHEAD,
            ensureMinimumReminder = true,
        )

        // Update cache
        eventCache.cacheEvents(events)

        // Log calendars if needed
        if (eventCache.shouldRefreshCalendarList()) {
            calendarDataSource.logAvailableCalendars()
            eventCache.markCalendarListCached()
        }

        return@withContext events
    }

    /**
     * Force refresh events by invalidating cache first
     * Use this when user explicitly requests a refresh
     */
    suspend fun forceRefreshEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Force refreshing events - invalidating cache")
        invalidateCache()
        getUpcomingEventsWithReminders()
    }

    /**
     * Get reminder minutes for a specific event
     */
    suspend fun getReminderMinutes(eventId: Long): List<Int> {
        return calendarDataSource.getReminderMinutes(eventId)
    }

    /**
     * Schedule all reminders for upcoming events
     * Handles cleanup of deleted events and skips already-scheduled alarms
     */
    suspend fun scheduleAllReminders(): SchedulingState = withContext(Dispatchers.IO) {
        _schedulingState.value = SchedulingState.Loading

        try {
            Log.d(TAG, "=== STARTING TO SCHEDULE ALL REMINDERS ===")

            // Invalidate cache to get fresh data
            invalidateCache()

            val events = getUpcomingEventsWithReminders()
            Log.d(TAG, "Found ${events.size} events with reminders")

            if (events.isEmpty()) {
                val result = SchedulingState.Success(
                    eventsProcessed = 0,
                    alarmsScheduled = 0,
                    alarmsSkipped = 0,
                    deletedEventsCleaned = 0,
                )
                _schedulingState.value = result
                return@withContext result
            }

            // Clean up alarms for deleted events
            val cleanedCount = cleanupDeletedEventAlarms(events)
            Log.d(TAG, "Cleaned up $cleanedCount alarms for deleted events")

            var scheduledCount = 0
            var skippedCount = 0

            for (event in events) {
                // Skip if all alarms already scheduled
                if (alarmScheduler.areAllAlarmsScheduled(event)) {
                    Log.d(TAG, "⏭ All alarms already scheduled for: ${event.title}")
                    skippedCount++
                    continue
                }

                // Skip events with all reminders in the past
                // Must check both original reminders AND the final reminder
                val currentTime = System.currentTimeMillis()
                val finalReminderMinutes = preferencesManager.getFinalReminderMinutes()

                val hasValidOriginalReminder = event.reminderMinutes.any { minutes ->
                    val reminderTime = event.startTime - (minutes * Constants.MILLIS_PER_MINUTE)
                    reminderTime > currentTime
                }

                val finalReminderTime =
                    event.startTime - (finalReminderMinutes * Constants.MILLIS_PER_MINUTE)
                val hasValidFinalReminder = finalReminderTime > currentTime

                // Debug logging to understand timing issues
                Log.d(
                    TAG,
                    "Event: ${event.title}, startTime=${event.startTime}, " +
                        "currentTime=$currentTime, diff=${event.startTime - currentTime}ms, " +
                        "finalReminderMinutes=$finalReminderMinutes, " +
                        "finalReminderTime=$finalReminderTime, " +
                        "hasValidFinal=$hasValidFinalReminder, " +
                        "hasValidOriginal=$hasValidOriginalReminder",
                )

                // Skip only if BOTH original reminders AND final reminder are in the past
                if (!hasValidOriginalReminder && !hasValidFinalReminder) {
                    Log.w(TAG, "⚠ All reminders in past for: ${event.title}")
                    continue
                }

                // Schedule reminders
                when (val result = alarmScheduler.scheduleEventReminders(event)) {
                    is AlarmScheduler.ScheduleResult.Success -> {
                        scheduledCount += result.alarmsScheduled
                        Log.d(TAG, "✓ Scheduled ${result.alarmsScheduled} alarms for: ${event.title}")
                    }
                    is AlarmScheduler.ScheduleResult.PartialSuccess -> {
                        scheduledCount += result.scheduled
                        Log.w(
                            TAG,
                            "⚠ Partial success for ${event.title}: " +
                                "${result.scheduled} scheduled, ${result.failed} failed",
                        )
                    }
                    is AlarmScheduler.ScheduleResult.Failure -> {
                        Log.e(TAG, "❌ Failed to schedule: ${event.title} - ${result.reason}")
                    }
                    AlarmScheduler.ScheduleResult.Skipped -> {
                        skippedCount++
                    }
                }
            }

            val result = SchedulingState.Success(
                eventsProcessed = events.size,
                alarmsScheduled = scheduledCount,
                alarmsSkipped = skippedCount,
                deletedEventsCleaned = cleanedCount,
            )

            Log.d(TAG, "=== SCHEDULING COMPLETE ===")
            Log.d(TAG, "Events: ${events.size}, Scheduled: $scheduledCount, Skipped: $skippedCount, Cleaned: $cleanedCount")

            _schedulingState.value = result
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling reminders", e)
            val error = SchedulingState.Error("Failed to schedule reminders: ${e.message}", e)
            _schedulingState.value = error
            return@withContext error
        }
    }

    /**
     * Schedule reminders for a single event
     */
    suspend fun scheduleReminder(event: CalendarEvent): AlarmScheduler.ScheduleResult {
        return alarmScheduler.scheduleEventReminders(event)
    }

    /**
     * Cancel all alarms for a specific event
     */
    suspend fun cancelReminder(eventId: Long) {
        val events = getUpcomingEventsWithReminders()
        val event = events.find { it.id == eventId }

        if (event != null) {
            alarmScheduler.cancelEventReminders(event)
        } else {
            // Event not found, try to cancel common patterns
            alarmScheduler.cancelAllAlarmsForEvent(eventId)
        }
    }

    /**
     * Cancel all scheduled alarms
     */
    suspend fun cancelAllAlarms() = withContext(Dispatchers.IO) {
        val events = getUpcomingEventsWithReminders()
        var cancelledCount = 0

        for (event in events) {
            alarmScheduler.cancelEventReminders(event)
            cancelledCount++
        }

        Log.d(TAG, "Cancelled alarms for $cancelledCount events")
    }

    /**
     * Get list of scheduled alarm descriptions
     */
    suspend fun getScheduledAlarms(): List<String> = withContext(Dispatchers.IO) {
        val events = getUpcomingEventsWithReminders()
        val descriptions = mutableListOf<String>()
        val currentTime = System.currentTimeMillis()

        for (event in events) {
            for (reminderMinutes in event.reminderMinutes) {
                val reminderTime = event.startTime - (reminderMinutes * Constants.MILLIS_PER_MINUTE)
                if (reminderTime > currentTime) {
                    descriptions.add(
                        "'${event.title}' - ${reminderMinutes}min before",
                    )
                }
            }
        }

        return@withContext descriptions
    }

    /**
     * Verify if an event still exists
     */
    suspend fun verifyEventExists(eventId: Long, expectedStartTime: Long): Boolean {
        return calendarDataSource.verifyEventExists(eventId, expectedStartTime)
    }

    /**
     * Check if alarm is scheduled for an event
     */
    suspend fun isAlarmScheduled(eventId: Long): Boolean {
        val events = getUpcomingEventsWithReminders()
        val event = events.find { it.id == eventId } ?: return false
        return alarmScheduler.areAllAlarmsScheduled(event)
    }

    /**
     * Invalidate all cached data
     */
    fun invalidateCache() {
        eventCache.invalidate()
    }

    // endregion

    // region Private Methods

    /**
     * Clean up alarms for events that no longer exist
     */
    private suspend fun cleanupDeletedEventAlarms(currentEvents: List<CalendarEvent>): Int {
        val previousEventIds = preferencesManager.getScheduledEventIds()
        val currentEventIds = currentEvents.map { it.id.toString() }.toSet()

        val deletedEventIds = previousEventIds - currentEventIds
        var cleanedCount = 0

        for (eventIdString in deletedEventIds) {
            try {
                val eventId = eventIdString.toLong()
                val cleaned = alarmScheduler.cancelAllAlarmsForEvent(eventId)
                cleanedCount += cleaned
                Log.d(TAG, "Cleaned $cleaned alarms for deleted event $eventId")
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up event $eventIdString: ${e.message}")
            }
        }

        // Update stored event IDs
        preferencesManager.setScheduledEventIds(currentEventIds)

        return cleanedCount
    }

    // endregion

    companion object {
        private const val TAG = "CalendarRepositoryV2"
    }
}
