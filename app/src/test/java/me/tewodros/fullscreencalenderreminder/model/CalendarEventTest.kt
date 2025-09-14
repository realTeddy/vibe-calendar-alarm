package me.tewodros.fullscreencalenderreminder.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Basic unit tests for CalendarEvent data class
 */
class CalendarEventTest {

    @Test
    fun `CalendarEvent creation works correctly`() {
        // Given
        val id = 123L
        val title = "Test Event"
        val startTime = System.currentTimeMillis()
        val reminderMinutes = listOf(1, 15, 60)

        // When
        val event = CalendarEvent(id, title, startTime, reminderMinutes)

        // Then
        assertEquals(id, event.id)
        assertEquals(title, event.title)
        assertEquals(startTime, event.startTime)
        assertEquals(reminderMinutes, event.reminderMinutes)
    }

    @Test
    fun `CalendarEvent toString contains relevant information`() {
        // Given
        val event = CalendarEvent(1, "Meeting", System.currentTimeMillis(), listOf(1))

        // When
        val result = event.toString()

        // Then
        assertTrue("Should contain event title", result.contains("Meeting"))
        assertTrue("Should contain event ID", result.contains("1"))
    }

    @Test
    fun `CalendarEvent with empty reminders works`() {
        // Given
        val event = CalendarEvent(1, "Event", System.currentTimeMillis(), emptyList())

        // When & Then
        assertTrue("Should have empty reminder list", event.reminderMinutes.isEmpty())
    }
}
