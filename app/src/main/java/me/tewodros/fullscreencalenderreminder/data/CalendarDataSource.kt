package me.tewodros.vibecalendaralarm.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tewodros.vibecalendaralarm.model.CalendarEvent

/**
 * Data source for calendar operations
 * Handles all direct interactions with Android's CalendarContract
 *
 * This class is responsible for:
 * - Querying calendar events from the system
 * - Retrieving reminder information for events
 * - Verifying event existence and validity
 * - Checking event deleted/canceled status
 */
@Singleton
class CalendarDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Check if calendar permission is granted
     */
    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Query upcoming calendar events for the next N days
     * Uses the Instances table to properly expand recurring events
     *
     * @param daysAhead Number of days to look ahead
     * @param ensureMinimumReminder If true, adds 1-minute reminder to events without reminders
     * @return List of calendar events
     */
    suspend fun queryUpcomingEvents(
        daysAhead: Int = Constants.QUERY_DAYS_AHEAD,
        ensureMinimumReminder: Boolean = true,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) {
            Log.w(TAG, "Calendar permission not granted")
            return@withContext emptyList()
        }

        val events = mutableListOf<CalendarEvent>()
        val startTime = System.currentTimeMillis()
        val endTime = startTime + (daysAhead * Constants.MILLIS_PER_DAY)

        Log.d(TAG, "Querying events for next $daysAhead days")

        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startTime.toString())
            .appendPath(endTime.toString())
            .build()

        val cursor: Cursor? = try {
            context.contentResolver.query(
                instancesUri,
                INSTANCE_PROJECTION,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC",
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException querying calendar", e)
            return@withContext emptyList()
        }

        cursor?.use { c ->
            // Get column indices with validation
            val eventIdIndex = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIndex = c.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIndex = c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val calendarNameIndex = c.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
            val statusIndex = c.getColumnIndex(CalendarContract.Instances.STATUS)

            // Validate required columns exist
            if (eventIdIndex < 0 || beginIndex < 0) {
                Log.e(TAG, "Required cursor columns not found: eventIdIndex=$eventIdIndex, beginIndex=$beginIndex")
                return@withContext emptyList()
            }

            var skippedCount = 0
            while (c.moveToNext()) {
                val eventId = c.getLong(eventIdIndex)
                val title = if (titleIndex >= 0) c.getString(titleIndex) ?: "Untitled Event" else "Untitled Event"
                val eventStartTime = c.getLong(beginIndex)
                val calendarName = if (calendarNameIndex >= 0) c.getString(calendarNameIndex) ?: "Unknown Calendar" else "Unknown Calendar"
                val status = if (statusIndex >= 0) c.getInt(statusIndex) else 0

                // Skip canceled events
                if (status == CalendarContract.Events.STATUS_CANCELED) {
                    skippedCount++
                    continue
                }

                // Skip deleted events
                if (isEventDeleted(eventId)) {
                    skippedCount++
                    continue
                }

                // Get reminders
                val reminderMinutes = getReminderMinutes(eventId)
                val finalReminders = if (ensureMinimumReminder) {
                    ensureOneMinuteReminder(reminderMinutes)
                } else {
                    reminderMinutes
                }

                events.add(
                    CalendarEvent(
                        id = eventId,
                        title = title,
                        startTime = eventStartTime,
                        reminderMinutes = finalReminders,
                        calendarName = calendarName,
                    ),
                )
            }

            if (skippedCount > 0) {
                Log.d(TAG, "Skipped $skippedCount canceled/deleted events")
            }
        }

        Log.d(TAG, "Found ${events.size} events")
        return@withContext events
    }

    /**
     * Get all reminder times for a specific event
     * @return List of reminder times in minutes before the event (sorted ascending)
     */
    suspend fun getReminderMinutes(eventId: Long): List<Int> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) {
            return@withContext emptyList()
        }

        val remindersList = mutableListOf<Int>()

        val cursor: Cursor? = context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(
                CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD,
            ),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            "${CalendarContract.Reminders.MINUTES} ASC",
        )

        cursor?.use { c ->
            val minutesIndex = c.getColumnIndex(CalendarContract.Reminders.MINUTES)
            val methodIndex = c.getColumnIndex(CalendarContract.Reminders.METHOD)

            // Validate required columns exist
            if (minutesIndex < 0 || methodIndex < 0) {
                Log.e(TAG, "Required columns not found in reminders cursor")
                return@withContext emptyList()
            }

            while (c.moveToNext()) {
                val minutes = c.getInt(minutesIndex)
                val method = c.getInt(methodIndex)

                // Only include notification/alert reminders
                if ((method == CalendarContract.Reminders.METHOD_ALERT ||
                        method == CalendarContract.Reminders.METHOD_DEFAULT) && minutes > 0
                ) {
                    remindersList.add(minutes)
                }
            }
        }

        return@withContext remindersList.sorted()
    }

    /**
     * Verify if an event still exists and is valid
     *
     * @param eventId The event ID to check
     * @param expectedStartTime The expected start time for time validation
     * @return true if event exists and is valid
     */
    suspend fun verifyEventExists(eventId: Long, expectedStartTime: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasCalendarPermission()) {
                Log.w(TAG, "Cannot verify event - no permission")
                return@withContext false
            }

            try {
                val cursor: Cursor? = context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    EVENT_VERIFICATION_PROJECTION,
                    "${CalendarContract.Events._ID} = ?",
                    arrayOf(eventId.toString()),
                    null,
                )

                cursor?.use { c ->
                    if (!c.moveToFirst()) {
                        Log.w(TAG, "Event $eventId not found")
                        return@withContext false
                    }

                    val deletedIndex = c.getColumnIndex(CalendarContract.Events.DELETED)
                    val statusIndex = c.getColumnIndex(CalendarContract.Events.STATUS)
                    val startTimeIndex = c.getColumnIndex(CalendarContract.Events.DTSTART)
                    val rruleIndex = c.getColumnIndex(CalendarContract.Events.RRULE)

                    // Check if deleted
                    if (deletedIndex >= 0 && c.getInt(deletedIndex) == 1) {
                        Log.w(TAG, "Event $eventId is deleted")
                        return@withContext false
                    }

                    // Check if canceled
                    if (statusIndex >= 0 &&
                        c.getInt(statusIndex) == CalendarContract.Events.STATUS_CANCELED
                    ) {
                        Log.w(TAG, "Event $eventId is canceled")
                        return@withContext false
                    }

                    // Check recurring event instance
                    val rrule = if (rruleIndex >= 0) c.getString(rruleIndex) else null
                    if (!rrule.isNullOrEmpty()) {
                        return@withContext verifyRecurringInstance(eventId, expectedStartTime)
                    }

                    // Verify time for non-recurring events
                    if (startTimeIndex >= 0) {
                        val actualStartTime = c.getLong(startTimeIndex)
                        val timeDiff = kotlin.math.abs(actualStartTime - expectedStartTime)
                        if (timeDiff > Constants.TIME_TOLERANCE_MS) {
                            Log.w(TAG, "Event $eventId time changed")
                            return@withContext false
                        }
                    }

                    Log.d(TAG, "Event $eventId verified")
                    return@withContext true
                }

                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying event $eventId", e)
                return@withContext false
            }
        }

    /**
     * Check if an event is marked as deleted
     */
    private fun isEventDeleted(eventId: Long): Boolean {
        try {
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.DELETED),
                "${CalendarContract.Events._ID} = ?",
                arrayOf(eventId.toString()),
                null,
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val deletedIndex = c.getColumnIndex(CalendarContract.Events.DELETED)
                    return if (deletedIndex >= 0) c.getInt(deletedIndex) == 1 else false
                }
                return true // Event doesn't exist
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking event deletion: ${e.message}")
            return false // Assume not deleted on error
        }
    }

    /**
     * Verify a specific instance of a recurring event exists
     */
    private fun verifyRecurringInstance(eventId: Long, instanceStartTime: Long): Boolean {
        try {
            val startWindow = instanceStartTime - Constants.TIME_TOLERANCE_MS
            val endWindow = instanceStartTime + Constants.TIME_TOLERANCE_MS

            val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(startWindow.toString())
                .appendPath(endWindow.toString())
                .build()

            val cursor: Cursor? = context.contentResolver.query(
                instancesUri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.BEGIN,
                ),
                "${CalendarContract.Instances.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                null,
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val beginIndex = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val instanceBegin = if (beginIndex >= 0) c.getLong(beginIndex) else 0
                    val timeDiff = kotlin.math.abs(instanceBegin - instanceStartTime)

                    if (timeDiff <= Constants.TIME_TOLERANCE_MS) {
                        Log.d(TAG, "Recurring instance verified for event $eventId")
                        return true
                    }
                }
                Log.w(TAG, "No instance found for recurring event $eventId")
                return false
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying recurring instance", e)
            return true // Fail-safe: allow if we can't verify
        }
    }

    /**
     * Log available calendars for debugging
     */
    suspend fun logAvailableCalendars() = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext

        val cursor: Cursor? = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS,
            ),
            null,
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )

        cursor?.use { c ->
            val nameIndex = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val visibleIndex = c.getColumnIndex(CalendarContract.Calendars.VISIBLE)
            val syncIndex = c.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)

            var calendarCount = 0
            var activeCount = 0
            while (c.moveToNext()) {
                calendarCount++
                val isVisible = c.getInt(visibleIndex) == 1
                val syncEvents = c.getInt(syncIndex) == 1
                if (isVisible && syncEvents) {
                    activeCount++
                }
            }

            Log.d(TAG, "Found $calendarCount calendars ($activeCount active)")
        }
    }

    /**
     * Ensure event has at least a 1-minute reminder
     */
    private fun ensureOneMinuteReminder(reminders: List<Int>): List<Int> {
        if (reminders.isEmpty()) {
            return listOf(1)
        }
        if (!reminders.contains(1)) {
            return (reminders + 1).sorted()
        }
        return reminders
    }

    companion object {
        private const val TAG = "CalendarDataSource"

        private val INSTANCE_PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.STATUS,
        )

        private val EVENT_VERIFICATION_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DELETED,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
        )
    }
}
