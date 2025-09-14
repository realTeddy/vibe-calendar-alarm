package me.tewodros.fullscreencalenderreminder.model

/**
 * Data class representing a calendar event with reminders
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val reminderMinutes: List<Int>, // List of all reminder times in minutes before event
)
