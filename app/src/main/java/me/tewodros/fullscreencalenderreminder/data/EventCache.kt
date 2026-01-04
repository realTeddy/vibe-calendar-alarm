package me.tewodros.vibecalendaralarm.data

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import me.tewodros.vibecalendaralarm.model.CalendarEvent

/**
 * Thread-safe cache for calendar events
 * Manages event caching with configurable TTL
 */
@Singleton
class EventCache @Inject constructor() {

    private val lock = Any()

    @Volatile
    private var cachedEvents: List<CalendarEvent>? = null

    @Volatile
    private var eventsCacheTimestamp: Long = 0

    @Volatile
    private var calendarListCached: Boolean = false

    @Volatile
    private var calendarListTimestamp: Long = 0

    /**
     * Get cached events if valid
     * @return cached events or null if cache is expired/empty
     */
    fun getCachedEvents(): List<CalendarEvent>? {
        synchronized(lock) {
            val currentTime = System.currentTimeMillis()
            val events = cachedEvents

            if (events != null && (currentTime - eventsCacheTimestamp) < Constants.EVENT_CACHE_DURATION_MS) {
                Log.d(
                    TAG,
                    "📦 Cache hit: ${events.size} events, age: ${(currentTime - eventsCacheTimestamp) / 1000}s",
                )
                return events
            }

            Log.d(TAG, "🔄 Cache miss or expired")
            return null
        }
    }

    /**
     * Update the events cache
     */
    fun cacheEvents(events: List<CalendarEvent>) {
        synchronized(lock) {
            cachedEvents = events.toList() // Defensive copy
            eventsCacheTimestamp = System.currentTimeMillis()
            Log.d(TAG, "📦 Cached ${events.size} events")
        }
    }

    /**
     * Check if calendar list should be refreshed
     */
    fun shouldRefreshCalendarList(): Boolean {
        synchronized(lock) {
            val currentTime = System.currentTimeMillis()
            return !calendarListCached ||
                (currentTime - calendarListTimestamp) > Constants.CALENDAR_LIST_CACHE_DURATION_MS
        }
    }

    /**
     * Mark calendar list as cached
     */
    fun markCalendarListCached() {
        synchronized(lock) {
            calendarListCached = true
            calendarListTimestamp = System.currentTimeMillis()
        }
    }

    /**
     * Invalidate all caches
     */
    fun invalidate() {
        synchronized(lock) {
            Log.d(TAG, "🗑️ Invalidating all caches")
            cachedEvents = null
            eventsCacheTimestamp = 0
            calendarListCached = false
            calendarListTimestamp = 0
        }
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): CacheStats {
        synchronized(lock) {
            val currentTime = System.currentTimeMillis()
            return CacheStats(
                eventCount = cachedEvents?.size ?: 0,
                eventCacheAgeMs = if (cachedEvents != null) currentTime - eventsCacheTimestamp else -1,
                isEventCacheValid = cachedEvents != null &&
                    (currentTime - eventsCacheTimestamp) < Constants.EVENT_CACHE_DURATION_MS,
                isCalendarListCached = calendarListCached &&
                    (currentTime - calendarListTimestamp) < Constants.CALENDAR_LIST_CACHE_DURATION_MS,
            )
        }
    }

    data class CacheStats(
        val eventCount: Int,
        val eventCacheAgeMs: Long,
        val isEventCacheValid: Boolean,
        val isCalendarListCached: Boolean,
    )

    companion object {
        private const val TAG = "EventCache"
    }
}
