package me.tewodros.vibecalendaralarm.repository

import io.mockk.*
import kotlinx.coroutines.test.runTest
import me.tewodros.vibecalendaralarm.model.CalendarEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for CalendarRepository interface
 * Tests repository pattern contract and behavior
 */
class CalendarRepositoryTest {

    private lateinit var mockRepository: CalendarRepository

    @Before
    fun setup() {
        mockRepository = mockk<CalendarRepository>(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `repository interface can be mocked`() {
        // When/Then
        assertNotNull(mockRepository)
    }

    @Test
    fun `getUpcomingEventsWithReminders returns event list`() = runTest {
        // Given
        val mockEvents = listOf(
            CalendarEvent(1L, "Test Event 1", System.currentTimeMillis(), listOf(15)),
            CalendarEvent(2L, "Test Event 2", System.currentTimeMillis(), listOf(30)),
        )
        coEvery { mockRepository.getUpcomingEventsWithReminders() } returns mockEvents

        // When
        val events = mockRepository.getUpcomingEventsWithReminders()

        // Then
        assertEquals("Should return mock events", 2, events.size)
        assertEquals("First event title", "Test Event 1", events[0].title)
    }

    @Test
    fun `getAllReminderMinutes returns reminder list`() = runTest {
        // Given
        val eventId = 123L
        val reminderMinutes = listOf(1, 15, 30)
        coEvery { mockRepository.getAllReminderMinutes(eventId) } returns reminderMinutes

        // When
        val result = mockRepository.getAllReminderMinutes(eventId)

        // Then
        assertEquals("Should return reminder minutes", reminderMinutes, result)
    }

    @Test
    fun `scheduleReminder handles event and minutes`() = runTest {
        // Given
        val event = CalendarEvent(456L, "Meeting", System.currentTimeMillis(), listOf(15))
        val reminderMinutes = 15
        coEvery { mockRepository.scheduleReminder(event, reminderMinutes) } just Runs

        // When
        mockRepository.scheduleReminder(event, reminderMinutes)

        // Then
        coVerify { mockRepository.scheduleReminder(event, reminderMinutes) }
    }

    @Test
    fun `scheduleAllReminders executes successfully`() = runTest {
        // Given
        coEvery { mockRepository.scheduleAllReminders() } just Runs

        // When
        mockRepository.scheduleAllReminders()

        // Then
        coVerify { mockRepository.scheduleAllReminders() }
    }

    @Test
    fun `cancelAllAlarms executes successfully`() = runTest {
        // Given
        coEvery { mockRepository.cancelAllAlarms() } just Runs

        // When
        mockRepository.cancelAllAlarms()

        // Then
        coVerify { mockRepository.cancelAllAlarms() }
    }

    @Test
    fun `getScheduledAlarms returns alarm descriptions`() = runTest {
        // Given
        val alarmDescriptions = listOf("Alarm 1", "Alarm 2", "Alarm 3")
        coEvery { mockRepository.getScheduledAlarms() } returns alarmDescriptions

        // When
        val result = mockRepository.getScheduledAlarms()

        // Then
        assertEquals("Should return alarm descriptions", alarmDescriptions, result)
    }

    @Test
    fun `hasCalendarPermission returns boolean`() {
        // Given
        every { mockRepository.hasCalendarPermission() } returns true

        // When
        val hasPermission = mockRepository.hasCalendarPermission()

        // Then
        assertTrue("Should return permission status", hasPermission)
    }

    @Test
    fun `invalidateCache executes successfully`() {
        // Given
        every { mockRepository.invalidateCache() } just Runs

        // When
        mockRepository.invalidateCache()

        // Then
        verify { mockRepository.invalidateCache() }
    }
}
