package me.tewodros.fullscreencalenderreminder.repository

import me.tewodros.fullscreencalenderreminder.model.CalendarEvent

/**
 * Repository interface for calendar data access
 * Abstracts the data layer for better testability and separation of concerns
 */
interface CalendarRepository {

    /**
     * Get ALL upcoming calendar events and ensure they have at least a 1-minute reminder
     * Events without existing reminders get an automatic 1-minute reminder added
     * Events with existing reminders get a 1-minute reminder added if not already present
     * @return List of ALL calendar events with at least 1-minute reminders guaranteed
     */
    suspend fun getUpcomingEventsWithReminders(): List<CalendarEvent>

    /**
     * Get all reminder minutes for a specific event
     * @param eventId The event ID to get reminders for
     * @return List of reminder times in minutes before the event
     */
    suspend fun getAllReminderMinutes(eventId: Long): List<Int>

    /**
     * Schedule an alarm for a specific reminder
     * @param event The calendar event
     * @param reminderMinutes Minutes before event to trigger reminder
     */
    suspend fun scheduleReminder(event: CalendarEvent, reminderMinutes: Int)

    /**
     * Schedule all reminders for upcoming events
     */
    suspend fun scheduleAllReminders()

    /**
     * Cancel all scheduled alarms
     */
    suspend fun cancelAllAlarms()

    /**
     * Get list of currently scheduled alarms for debugging
     * @return List of alarm descriptions
     */
    suspend fun getScheduledAlarms(): List<String>

    /**
     * Check if calendar permission is granted
     * @return true if permission is granted
     */
    fun hasCalendarPermission(): Boolean

    /**
     * Invalidate any cached data
     */
    fun invalidateCache()
}
