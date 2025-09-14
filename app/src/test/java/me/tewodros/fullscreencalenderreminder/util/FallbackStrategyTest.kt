package me.tewodros.fullscreencalenderreminder.util

import android.content.Context
import io.mockk.*
import me.tewodros.fullscreencalenderreminder.model.CalendarEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for FallbackStrategy utility class
 * Tests fallback mechanisms and alternative strategies
 */
class FallbackStrategyTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mockk<Context>(relaxed = true)

        // Mock static Log methods
        mockkStatic("android.util.Log")
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic("android.util.Log")
    }

    @Test
    fun `handleCalendarAccessFallback returns empty list`() {
        // When
        val events = FallbackStrategy.handleCalendarAccessFallback(mockContext)

        // Then
        assertTrue("Should return empty event list", events.isEmpty())
        assertNotNull("Should not return null", events)
    }

    @Test
    fun `handleAlarmSchedulingFallback returns false for failed scheduling`() {
        // Given
        val event = CalendarEvent(
            id = 123L,
            title = "Test Event",
            startTime = System.currentTimeMillis(),
            reminderMinutes = listOf(15),
        )
        val reminderMinutes = 15

        // When
        val result = FallbackStrategy.handleAlarmSchedulingFallback(
            mockContext,
            event,
            reminderMinutes,
        )

        // Then
        assertFalse("Should return false indicating fallback couldn't schedule alarm", result)
    }

    @Test
    fun `handleAlarmSchedulingFallback handles various reminder times`() {
        val reminderTimes = listOf(1, 5, 15, 30, 60)

        reminderTimes.forEach { minutes ->
            // Given
            val event = CalendarEvent(
                id = 100L + minutes,
                title = "Test Event $minutes",
                startTime = System.currentTimeMillis(),
                reminderMinutes = listOf(minutes),
            )

            // When
            val result = FallbackStrategy.handleAlarmSchedulingFallback(mockContext, event, minutes)

            // Then
            assertFalse("Should return false for $minutes minute reminder", result)
        }
    }

    @Test
    fun `handleAlarmSchedulingFallback handles long event titles`() {
        // Given
        val longTitle = "This is a very long event title that exceeds normal length to test edge cases"
        val event = CalendarEvent(
            id = 456L,
            title = longTitle,
            startTime = System.currentTimeMillis(),
            reminderMinutes = listOf(30),
        )
        val reminderMinutes = 30

        // When
        val result = FallbackStrategy.handleAlarmSchedulingFallback(
            mockContext,
            event,
            reminderMinutes,
        )

        // Then
        assertFalse("Should handle long titles gracefully", result)
    }

    @Test
    fun `handleAlarmSchedulingFallback handles empty event title`() {
        // Given
        val event = CalendarEvent(
            id = 789L,
            title = "",
            startTime = System.currentTimeMillis(),
            reminderMinutes = listOf(5),
        )
        val reminderMinutes = 5

        // When
        val result = FallbackStrategy.handleAlarmSchedulingFallback(
            mockContext,
            event,
            reminderMinutes,
        )

        // Then
        assertFalse("Should handle empty title gracefully", result)
    }

    @Test
    fun `handleAlarmSchedulingFallback handles special characters in title`() {
        // Given
        val specialTitle = "Meeting: @#$%^&*()_+{}|:<>?[]\\;'\",./"
        val event = CalendarEvent(
            id = 999L,
            title = specialTitle,
            startTime = System.currentTimeMillis(),
            reminderMinutes = listOf(10),
        )
        val reminderMinutes = 10

        // When
        val result = FallbackStrategy.handleAlarmSchedulingFallback(
            mockContext,
            event,
            reminderMinutes,
        )

        // Then
        assertFalse("Should handle special characters gracefully", result)
    }

    @Test
    fun `handleAlarmSchedulingFallback handles past events`() {
        // Given
        val pastEvent = CalendarEvent(
            id = 111L,
            title = "Past Event",
            startTime = System.currentTimeMillis() - 60 * 60 * 1000, // 1 hour ago
            reminderMinutes = listOf(15),
        )
        val reminderMinutes = 15

        // When
        val result = FallbackStrategy.handleAlarmSchedulingFallback(
            mockContext,
            pastEvent,
            reminderMinutes,
        )

        // Then
        assertFalse("Should handle past events", result)
    }

    @Test
    fun `handleAlarmSchedulingFallback handles future events`() {
        // Given
        val futureEvent = CalendarEvent(
            id = 222L,
            title = "Future Event",
            startTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000, // 24 hours from now
            reminderMinutes = listOf(60),
        )
        val reminderMinutes = 60

        // When
        val result = FallbackStrategy.handleAlarmSchedulingFallback(
            mockContext,
            futureEvent,
            reminderMinutes,
        )

        // Then
        assertFalse("Should handle future events", result)
    }

    @Test
    fun `handleAlarmSchedulingFallback handles zero reminder minutes`() {
        // Given
        val event = CalendarEvent(
            id = 333L,
            title = "Immediate Event",
            startTime = System.currentTimeMillis(),
            reminderMinutes = listOf(0),
        )
        val reminderMinutes = 0

        // When
        val result = FallbackStrategy.handleAlarmSchedulingFallback(
            mockContext,
            event,
            reminderMinutes,
        )

        // Then
        assertFalse("Should handle zero reminder minutes", result)
    }

    @Test
    fun `handleAlarmSchedulingFallback handles negative reminder minutes`() {
        // Given
        val event = CalendarEvent(
            id = 444L,
            title = "Event with negative reminder",
            startTime = System.currentTimeMillis(),
            reminderMinutes = listOf(-5),
        )
        val reminderMinutes = -5

        // When
        val result = FallbackStrategy.handleAlarmSchedulingFallback(
            mockContext,
            event,
            reminderMinutes,
        )

        // Then
        assertFalse("Should handle negative reminder minutes", result)
    }
}
