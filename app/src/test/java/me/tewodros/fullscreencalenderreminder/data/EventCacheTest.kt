package me.tewodros.vibecalendaralarm.data

import me.tewodros.vibecalendaralarm.model.CalendarEvent
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for EventCache
 *
 * Tests thread-safe caching behavior and cache statistics tracking.
 */
class EventCacheTest {

    private lateinit var eventCache: EventCache

    @Before
    fun setUp() {
        eventCache = EventCache()
    }

    @Test
    fun `cacheEvents stores events in cache`() {
        // Given
        val events = listOf(
            createTestEvent(1, "Event 1"),
            createTestEvent(2, "Event 2"),
        )

        // When
        eventCache.cacheEvents(events)

        // Then
        val cached = eventCache.getCachedEvents()
        assertThat(cached).isNotNull()
        assertThat(cached).hasSize(2)
        assertThat(cached!![0].title).isEqualTo("Event 1")
    }

    @Test
    fun `getCachedEvents returns null when cache is empty`() {
        // When
        val result = eventCache.getCachedEvents()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `invalidate clears the cache`() {
        // Given
        val events = listOf(createTestEvent(1, "Event"))
        eventCache.cacheEvents(events)

        // When
        eventCache.invalidate()

        // Then
        assertThat(eventCache.getCachedEvents()).isNull()
    }

    @Test
    fun `getCacheStats returns correct event count`() {
        // Given
        val events = listOf(
            createTestEvent(1, "Event 1"),
            createTestEvent(2, "Event 2"),
        )
        eventCache.cacheEvents(events)

        // When
        val stats = eventCache.getCacheStats()

        // Then
        assertThat(stats.eventCount).isEqualTo(2)
        assertThat(stats.isEventCacheValid).isTrue()
    }

    @Test
    fun `getCacheStats returns zero count when empty`() {
        // When
        val stats = eventCache.getCacheStats()

        // Then
        assertThat(stats.eventCount).isEqualTo(0)
        assertThat(stats.isEventCacheValid).isFalse()
        assertThat(stats.eventCacheAgeMs).isEqualTo(-1)
    }

    @Test
    fun `shouldRefreshCalendarList returns true initially`() {
        // When
        val result = eventCache.shouldRefreshCalendarList()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `markCalendarListCached updates cache state`() {
        // When
        eventCache.markCalendarListCached()

        // Then
        assertThat(eventCache.shouldRefreshCalendarList()).isFalse()
    }

    @Test
    fun `concurrent access is thread-safe`() {
        // Given
        val events = listOf(createTestEvent(1, "Event"))

        // When - multiple threads accessing cache
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) {
                    eventCache.cacheEvents(events)
                    eventCache.getCachedEvents()
                    if (threadId % 2 == 0) {
                        eventCache.invalidate()
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then - no exceptions thrown, cache in consistent state
        // (either has events or is null)
        val result = eventCache.getCachedEvents()
        if (result != null) {
            assertThat(result).hasSize(1)
        }
    }

    // Helper function
    private fun createTestEvent(id: Long, title: String): CalendarEvent {
        return CalendarEvent(
            id = id,
            title = title,
            startTime = System.currentTimeMillis() + 3600000,
            reminderMinutes = listOf(15),
        )
    }
}
