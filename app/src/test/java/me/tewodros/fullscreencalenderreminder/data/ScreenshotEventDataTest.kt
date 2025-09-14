package me.tewodros.fullscreencalenderreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ScreenshotEventData
 * Tests fake data generation for screenshots and demo purposes
 */
class ScreenshotEventDataTest {

    @Test
    fun `getFakeEvents returns non-empty list`() {
        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        assertNotNull("Events list should not be null", events)
        assertTrue("Events list should not be empty", events.isNotEmpty())
    }

    @Test
    fun `getFakeEvents returns events with valid data`() {
        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        events.forEach { event ->
            assertTrue("Event ID should be positive", event.id > 0)
            assertNotNull("Event title should not be null", event.title)
            assertTrue("Event title should not be empty", event.title.isNotEmpty())
            assertTrue("Event start time should be positive", event.startTime > 0)
            assertNotNull("Reminder minutes should not be null", event.reminderMinutes)
        }
    }

    @Test
    fun `getFakeEvents returns events with future start times`() {
        // Given
        val currentTime = System.currentTimeMillis()

        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        val futureEvents = events.filter { it.startTime > currentTime }
        assertTrue("Should contain future events for screenshots", futureEvents.isNotEmpty())
    }

    @Test
    fun `getFakeEvents returns events with realistic titles`() {
        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        val titles = events.map { it.title }
        assertTrue(
            "Should contain professional titles",
            titles.any {
                it.contains("Meeting") || it.contains("Appointment") || it.contains(
                    "Coffee",
                )
            },
        )
    }

    @Test
    fun `getFakeEvents returns events with reminders`() {
        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        val eventsWithReminders = events.filter { it.reminderMinutes.isNotEmpty() }
        assertTrue(
            "Most events should have reminders for demo purposes",
            eventsWithReminders.size >= events.size / 2,
        )
    }

    @Test
    fun `getFakeEvents returns diverse event types`() {
        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        val titles = events.map { it.title }
        val uniqueTitles = titles.toSet()
        assertTrue("Should have diverse event types", uniqueTitles.size >= 3)
    }

    @Test
    fun `getFakeEvents generates consistent data across calls`() {
        // When
        val events1 = ScreenshotEventData.getFakeEvents()
        val events2 = ScreenshotEventData.getFakeEvents()

        // Then
        assertEquals("Should return same number of events", events1.size, events2.size)

        // Check that events have consistent IDs and titles (should be deterministic)
        events1.zip(events2).forEach { (event1, event2) ->
            assertEquals("Event IDs should be consistent", event1.id, event2.id)
            assertEquals("Event titles should be consistent", event1.title, event2.title)
        }
    }

    @Test
    fun `getFakeEvents returns events spread across multiple days`() {
        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        val startTimes = events.map { it.startTime }
        val timeRange = startTimes.maxOrNull()!! - startTimes.minOrNull()!!
        val oneDayInMillis = 24 * 60 * 60 * 1000L

        assertTrue(
            "Events should span multiple days for variety",
            timeRange > oneDayInMillis,
        )
    }

    @Test
    fun `getFakeEvents includes events with different reminder times`() {
        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        val allReminderTimes = events.flatMap { it.reminderMinutes }.toSet()
        assertTrue("Should have variety in reminder times", allReminderTimes.size >= 2)
        assertTrue(
            "Should include common reminder times",
            allReminderTimes.any { it in listOf(5, 10, 15, 30, 60) },
        )
    }

    @Test
    fun `getFakeEvents returns events with reasonable time windows`() {
        // When
        val events = ScreenshotEventData.getFakeEvents()

        // Then
        val currentTime = System.currentTimeMillis()
        val oneWeekFromNow = currentTime + (7 * 24 * 60 * 60 * 1000L)

        events.forEach { event ->
            assertTrue(
                "Event should be within reasonable future timeframe",
                event.startTime <= oneWeekFromNow,
            )
        }
    }
}
