package me.tewodros.vibecalendaralarm.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.tewodros.vibecalendaralarm.ReminderWorker
import me.tewodros.vibecalendaralarm.model.CalendarEvent
import me.tewodros.vibecalendaralarm.repository.CalendarRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Integration tests for ReminderWorker
 * Tests WorkManager integration with real Android context
 */
@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {

    private lateinit var context: Context
    private lateinit var mockRepository: CalendarRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockRepository = mock()
    }

    @Test
    fun testReminderWorkerSuccess() = runBlocking {
        // Given
        val testEvents = listOf(
            CalendarEvent(
                id = 1L,
                title = "Test Event 1",
                startTime = System.currentTimeMillis() + 3600000L,
                reminderMinutes = listOf(15, 5),
            ),
            CalendarEvent(
                id = 2L,
                title = "Test Event 2",
                startTime = System.currentTimeMillis() + 7200000L,
                reminderMinutes = listOf(30),
            ),
        )

        whenever(mockRepository.getUpcomingEventsWithReminders()).thenReturn(testEvents)
        whenever(mockRepository.getScheduledAlarms()).thenReturn(listOf("Alarm 1", "Alarm 2"))

        // Create worker with mocked repository
        val worker = ReminderWorker(
            context,
            TestListenableWorkerBuilder.create().build().params,
            mockRepository,
        )

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockRepository).getUpcomingEventsWithReminders()
        verify(mockRepository).scheduleAllReminders()
    }

    @Test
    fun testReminderWorkerWithNoEvents() = runBlocking {
        // Given
        whenever(mockRepository.getUpcomingEventsWithReminders()).thenReturn(emptyList())

        val worker = ReminderWorker(
            context,
            TestListenableWorkerBuilder.create().build().params,
            mockRepository,
        )

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockRepository).getUpcomingEventsWithReminders()
        verify(mockRepository, never()).scheduleAllReminders()
    }

    @Test
    fun testReminderWorkerWithException() = runBlocking {
        // Given
        whenever(mockRepository.getUpcomingEventsWithReminders()).thenThrow(
            RuntimeException("Test exception"),
        )

        val worker = ReminderWorker(
            context,
            TestListenableWorkerBuilder.create().build().params,
            mockRepository,
        )

        // When
        val result = worker.doWork()

        // Then
        assertEquals(ListenableWorker.Result.retry(), result)
        verify(mockRepository).getUpcomingEventsWithReminders()
    }
}
