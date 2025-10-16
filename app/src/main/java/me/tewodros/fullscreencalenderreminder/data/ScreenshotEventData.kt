package me.tewodros.vibecalendaralarm.data

import java.util.*
import me.tewodros.vibecalendaralarm.model.CalendarEvent

/**
 * Provides hardcoded fake events for screenshot mode
 * These events look realistic and professional for app store screenshots
 */
object ScreenshotEventData {

    /**
     * Returns a list of fake events that look great for screenshots
     * Events are spread across today and the next few days
     */
    fun getFakeEvents(): List<CalendarEvent> {
        val now = Calendar.getInstance()
        val events = mutableListOf<CalendarEvent>()

        // Today's events
        events.add(
            createFakeEvent(
                id = 1001,
                title = "Team Meeting",
                description = "Weekly team sync and project updates",
                location = "Conference Room A",
                startTime = getTimeFromNow(now, 2, 0), // 2 hours from now
                endTime = getTimeFromNow(now, 3, 0),
                reminderMinutes = 15,
            ),
        )

        events.add(
            createFakeEvent(
                id = 1002,
                title = "Doctor Appointment",
                description = "Annual health checkup",
                location = "City Medical Center",
                startTime = getTimeFromNow(now, 5, 30), // 5.5 hours from now
                endTime = getTimeFromNow(now, 6, 0),
                reminderMinutes = 30,
            ),
        )

        // Tomorrow's events
        events.add(
            createFakeEvent(
                id = 1003,
                title = "Coffee with Sarah",
                description = "Catch up and discuss the new project",
                location = "Downtown Café",
                startTime = getTimeFromNow(now, 26, 0), // Tomorrow morning
                endTime = getTimeFromNow(now, 27, 30),
                reminderMinutes = 15,
            ),
        )

        events.add(
            createFakeEvent(
                id = 1004,
                title = "Project Deadline",
                description = "Submit final report and presentation materials",
                location = "Office",
                startTime = getTimeFromNow(now, 34, 0), // Tomorrow afternoon
                endTime = getTimeFromNow(now, 36, 0),
                reminderMinutes = 60,
            ),
        )

        events.add(
            createFakeEvent(
                id = 1005,
                title = "Gym Session",
                description = "Cardio and strength training",
                location = "Fitness Center",
                startTime = getTimeFromNow(now, 42, 0), // Tomorrow evening
                endTime = getTimeFromNow(now, 43, 30),
                reminderMinutes = 10,
            ),
        )

        // Day after tomorrow
        events.add(
            createFakeEvent(
                id = 1006,
                title = "Family Dinner",
                description = "Monthly family gathering",
                location = "Mom's House",
                startTime = getTimeFromNow(now, 66, 0), // Day after tomorrow
                endTime = getTimeFromNow(now, 69, 0),
                reminderMinutes = 30,
            ),
        )

        events.add(
            createFakeEvent(
                id = 1007,
                title = "Client Presentation",
                description = "Present Q4 strategy and roadmap",
                location = "Corporate Office Building",
                startTime = getTimeFromNow(now, 74, 0), // Day after tomorrow
                endTime = getTimeFromNow(now, 76, 0),
                reminderMinutes = 45,
            ),
        )

        // Weekend
        events.add(
            createFakeEvent(
                id = 1008,
                title = "Weekend Getaway",
                description = "Relaxing weekend at the beach resort",
                location = "Oceanview Beach Resort",
                startTime = getTimeFromNow(now, 98, 0), // Weekend
                endTime = getTimeFromNow(now, 122, 0), // 24 hour event
                reminderMinutes = 120,
            ),
        )

        return events.sortedBy { it.startTime }
    }

    /**
     * Helper function to create a fake event with demo data
     */
    private fun createFakeEvent(
        id: Long,
        title: String,
        description: String,
        location: String,
        startTime: Long,
        endTime: Long,
        reminderMinutes: Int,
    ): CalendarEvent {
        // Assign different calendar names to different events for variety
        val calendarName = when (id % 3) {
            0L -> "Work"
            1L -> "Personal"
            else -> "Family"
        }

        return CalendarEvent(
            id = id,
            title = "$title - $location",
            startTime = startTime,
            reminderMinutes = listOf(reminderMinutes),
            calendarName = calendarName
        )
    }

    /**
     * Helper to get time from now + specified hours and minutes
     */
    private fun getTimeFromNow(baseCalendar: Calendar, hours: Int, minutes: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = baseCalendar.timeInMillis
            add(Calendar.HOUR_OF_DAY, hours)
            add(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
