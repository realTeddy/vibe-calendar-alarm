package me.tewodros.fullscreencalenderreminder.repository

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tewodros.fullscreencalenderreminder.AlarmReceiver
import me.tewodros.fullscreencalenderreminder.model.CalendarEvent
import me.tewodros.fullscreencalenderreminder.util.ErrorHandler
import me.tewodros.fullscreencalenderreminder.util.FallbackStrategy

/**
 * Implementation of CalendarRepository that handles calendar data access
 * and alarm scheduling with intelligent caching for performance
 * Now includes comprehensive error handling and fallback strategies
 */
class CalendarRepositoryImpl(private val context: Context) : CalendarRepository {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Performance cache for calendar events
    private var cachedEvents: List<CalendarEvent>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 30 * 1000L // 30 seconds cache

    // Cache for calendar list to avoid repeated queries
    private var calendarListCached: Boolean = false
    private var calendarListTimestamp: Long = 0
    private val CALENDAR_LIST_CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    override suspend fun getUpcomingEventsWithReminders(): List<CalendarEvent> = withContext(
        Dispatchers.IO,
    ) {
        if (!hasCalendarPermission()) {
            ErrorHandler.handleCalendarPermissionError(context, showToast = false)
            Log.w("CalendarRepository", "Calendar permission not granted")
            return@withContext FallbackStrategy.handleCalendarAccessFallback(context)
        }

        try {
            // Check cache first for performance
            val currentTime = System.currentTimeMillis()
            if (cachedEvents != null && (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
                Log.d(
                    "CalendarRepository",
                    "📦 Returning cached events (${cachedEvents!!.size} events) - cache age: ${(currentTime - cacheTimestamp) / 1000}s",
                )
                return@withContext cachedEvents!!
            }

            Log.d("CalendarRepository", "🔄 Cache miss or expired - fetching fresh calendar data")

            // Log available calendars (but cache this too to avoid repeated queries)
            if (!calendarListCached || (currentTime - calendarListTimestamp) > CALENDAR_LIST_CACHE_DURATION) {
                try {
                    logAvailableCalendars()
                    calendarListCached = true
                    calendarListTimestamp = currentTime
                } catch (e: Exception) {
                    Log.w("CalendarRepository", "Failed to log available calendars", e)
                    // Continue anyway, this is just for debugging
                }
            }

            val events = mutableListOf<CalendarEvent>()

            // Query for events in the next 30 days using INSTANCES table for recurring event support
            val startTime = System.currentTimeMillis()
            val endTime = startTime + (30 * 24 * 60 * 60 * 1000L) // 30 days from now (one month)

            Log.d(
                "CalendarRepository",
                "=== QUERYING EVENT INSTANCES (includes recurring events) ===",
            )
            Log.d(
                "CalendarRepository",
                "Time range: ${formatTime(startTime)} to ${formatTime(endTime)} (30 days)",
            )

            // Build URI for instances within time range - this expands recurring events
            val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(startTime.toString())
                .appendPath(endTime.toString())
                .build()

            val instanceCursor: Cursor? = try {
                context.contentResolver.query(
                    instancesUri,
                    arrayOf(
                        CalendarContract.Instances.EVENT_ID,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.CALENDAR_ID,
                        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Instances.RRULE, // To identify recurring events
                    ),
                    null, // No additional WHERE clause needed - time range is in URI
                    null,
                    CalendarContract.Instances.BEGIN + " ASC",
                )
            } catch (e: SecurityException) {
                ErrorHandler.handleCalendarDataError(context, e, showToast = false)
                return@withContext FallbackStrategy.handleCalendarAccessFallback(context)
            } catch (e: Exception) {
                ErrorHandler.handleCalendarDataError(context, e, showToast = false)
                return@withContext FallbackStrategy.handleCalendarAccessFallback(context)
            }

            instanceCursor?.use { cursor ->
                try {
                    val eventIdIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                    val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                    val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val calendarIdIndex = cursor.getColumnIndex(
                        CalendarContract.Instances.CALENDAR_ID,
                    )
                    val calendarNameIndex = cursor.getColumnIndex(
                        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                    )
                    val rruleIndex = cursor.getColumnIndex(CalendarContract.Instances.RRULE)

                    while (cursor.moveToNext()) {
                        try {
                            val eventId = cursor.getLong(eventIdIndex)
                            val title = cursor.getString(titleIndex) ?: "Untitled Event"
                            val eventStartTime = cursor.getLong(beginIndex)
                            val calendarId = cursor.getLong(calendarIdIndex)
                            val calendarName = cursor.getString(calendarNameIndex) ?: "Unknown Calendar"
                            val rrule = cursor.getString(rruleIndex)

                            val isRecurring = !rrule.isNullOrEmpty()
                            val recurringStatus = if (isRecurring) "🔄 RECURRING" else "📅 SINGLE"

                            Log.d(
                                "CalendarRepository",
                                "Found event '$title' $recurringStatus in calendar '$calendarName' (ID: $calendarId)",
                            )
                            Log.d(
                                "CalendarRepository",
                                "  - Instance time: ${formatTime(eventStartTime)}",
                            )
                            if (isRecurring) {
                                Log.d("CalendarRepository", "  - Recurrence rule: $rrule")
                            }

                            // Check if this event instance has reminders
                            val reminderMinutes = getAllReminderMinutes(eventId)
                            if (reminderMinutes.isNotEmpty()) {
                                events.add(
                                    CalendarEvent(eventId, title, eventStartTime, reminderMinutes),
                                )
                                Log.d(
                                    "CalendarRepository",
                                    "✓ Event '$title' $recurringStatus from '$calendarName' has reminders: ${reminderMinutes.joinToString(
                                        ", ",
                                    )} min before",
                                )
                            } else {
                                Log.d(
                                    "CalendarRepository",
                                    "✗ Event '$title' $recurringStatus from '$calendarName' has no reminders - skipping",
                                )
                            }
                        } catch (e: Exception) {
                            Log.w("CalendarRepository", "Error processing event row", e)
                            // Continue with next event
                        }
                    }
                } catch (e: Exception) {
                    ErrorHandler.handleCalendarDataError(context, e, showToast = false)
                    Log.e("CalendarRepository", "Error processing calendar cursor", e)
                }
            }

            // Cache the results for performance
            cachedEvents = events
            cacheTimestamp = currentTime

            Log.d(
                "CalendarRepository",
                "Found ${events.size} events with reminders across all calendars (2-day range) - cached for ${CACHE_DURATION_MS / 1000}s",
            )
            return@withContext events
        } catch (e: Exception) {
            val errorMessage = ErrorHandler.handleCalendarDataError(context, e, showToast = false)
            Log.e("CalendarRepository", "Failed to get upcoming events: $errorMessage", e)
            return@withContext FallbackStrategy.handleCalendarAccessFallback(context)
        }
    }

    override suspend fun getAllReminderMinutes(eventId: Long): List<Int> = withContext(
        Dispatchers.IO,
    ) {
        if (!hasCalendarPermission()) {
            return@withContext emptyList()
        }

        val reminderMinutes = mutableListOf<Int>()

        // Query for all reminders for this event
        val reminderCursor: Cursor? = context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(
                CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD,
            ),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )

        reminderCursor?.use { cursor ->
            val minutesIndex = cursor.getColumnIndex(CalendarContract.Reminders.MINUTES)
            val methodIndex = cursor.getColumnIndex(CalendarContract.Reminders.METHOD)

            while (cursor.moveToNext()) {
                val minutes = cursor.getInt(minutesIndex)
                val method = cursor.getInt(methodIndex)

                // Only include notification/alert reminders (method 1 = alert)
                if (method == CalendarContract.Reminders.METHOD_ALERT || method == CalendarContract.Reminders.METHOD_DEFAULT) {
                    reminderMinutes.add(minutes)
                    Log.d(
                        "CalendarRepository",
                        "  - Found reminder: $minutes minutes before (method: $method)",
                    )
                }
            }
        }

        return@withContext reminderMinutes.sorted() // Sort ascending (earliest first)
    }

    override suspend fun scheduleReminder(event: CalendarEvent, reminderMinutes: Int) = withContext(
        Dispatchers.IO,
    ) {
        try {
            val reminderTime = event.startTime - (reminderMinutes * 60 * 1000L)
            val currentTime = System.currentTimeMillis()

            if (reminderTime <= currentTime) {
                Log.w(
                    "CalendarRepository",
                    "⚠️ Reminder time has already passed for '${event.title}' (${reminderMinutes}min before) - skipping",
                )
                return@withContext
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("event_title", event.title)
                putExtra("event_id", event.id)
                putExtra("reminder_minutes", reminderMinutes)
            }

            // Create unique request ID for each reminder (event_id + reminder_minutes)
            val requestId = (event.id.toString() + reminderMinutes.toString()).hashCode()

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent,
                    )
                }

                Log.d(
                    "CalendarRepository",
                    "✅ Scheduled alarm for '${event.title}' - ${reminderMinutes}min before (${formatTime(
                        reminderTime,
                    )})",
                )
            } catch (e: SecurityException) {
                val errorMessage = ErrorHandler.handleAlarmSchedulingError(
                    context,
                    event.title,
                    e,
                    showToast = false,
                )
                Log.e("CalendarRepository", "Security error scheduling alarm: $errorMessage", e)

                // Try fallback strategy
                val fallbackSuccess = FallbackStrategy.handleAlarmSchedulingFallback(
                    context,
                    event,
                    reminderMinutes,
                )
                if (!fallbackSuccess) {
                    throw e // Re-throw if fallback also failed
                }
            } catch (e: Exception) {
                val errorMessage = ErrorHandler.handleAlarmSchedulingError(
                    context,
                    event.title,
                    e,
                    showToast = false,
                )
                Log.e("CalendarRepository", "Error scheduling alarm: $errorMessage", e)

                // Try fallback strategy
                val fallbackSuccess = FallbackStrategy.handleAlarmSchedulingFallback(
                    context,
                    event,
                    reminderMinutes,
                )
                if (!fallbackSuccess) {
                    throw e // Re-throw if fallback also failed
                }
            }
        } catch (e: Exception) {
            ErrorHandler.handleAlarmSchedulingError(context, event.title, e, showToast = true)
            Log.e(
                "CalendarRepository",
                "❌ Failed to schedule alarm for '${event.title}': ${e.message}",
                e,
            )
        }
    }

    override suspend fun scheduleAllReminders() = withContext(Dispatchers.IO) {
        Log.d("CalendarRepository", "🔔 Starting to schedule all calendar reminders...")

        val events = getUpcomingEventsWithReminders()
        Log.d("CalendarRepository", "Found ${events.size} events with reminders to schedule")

        // First cancel all existing alarms to avoid duplicates
        cancelAllAlarms()

        var totalRemindersScheduled = 0

        for (event in events) {
            Log.d(
                "CalendarRepository",
                "Processing event: '${event.title}' at ${formatTime(event.startTime)}",
            )

            for (reminderMinutes in event.reminderMinutes) {
                scheduleReminder(event, reminderMinutes)
                totalRemindersScheduled++
            }
        }

        Log.d(
            "CalendarRepository",
            "✅ Scheduled $totalRemindersScheduled total reminders for ${events.size} events",
        )

        // Invalidate cache after scheduling to ensure fresh data next time
        invalidateCache()

        Toast.makeText(
            context,
            "Scheduled $totalRemindersScheduled reminders for ${events.size} events",
            Toast.LENGTH_LONG,
        ).show()
    }

    override suspend fun cancelAllAlarms() = withContext(Dispatchers.IO) {
        Log.d("CalendarRepository", "🚫 Cancelling all existing calendar alarms...")

        val events = getUpcomingEventsWithReminders()
        var cancelledCount = 0

        for (event in events) {
            for (reminderMinutes in event.reminderMinutes) {
                val intent = Intent(context, AlarmReceiver::class.java)
                val requestId = (event.id.toString() + reminderMinutes.toString()).hashCode()

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                alarmManager.cancel(pendingIntent)
                cancelledCount++
            }
        }

        Log.d("CalendarRepository", "✅ Cancelled $cancelledCount alarms")
        return@withContext
    }

    override suspend fun getScheduledAlarms(): List<String> = withContext(Dispatchers.IO) {
        val events = getUpcomingEventsWithReminders()
        val alarmDescriptions = mutableListOf<String>()

        for (event in events) {
            for (reminderMinutes in event.reminderMinutes) {
                val reminderTime = event.startTime - (reminderMinutes * 60 * 1000L)
                val currentTime = System.currentTimeMillis()

                if (reminderTime > currentTime) {
                    alarmDescriptions.add(
                        "'${event.title}' - ${reminderMinutes}min before (${formatTime(reminderTime)})",
                    )
                }
            }
        }

        return@withContext alarmDescriptions
    }

    override fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    override fun invalidateCache() {
        cachedEvents = null
        cacheTimestamp = 0
        calendarListCached = false
        calendarListTimestamp = 0
        Log.d("CalendarRepository", "🗑️ Cache invalidated")
    }

    // Helper methods
    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    private suspend fun logAvailableCalendars() = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext

        Log.d("CalendarRepository", "=== AVAILABLE CALENDARS ===")

        val calendarCursor: Cursor? = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            ),
            null,
            null,
            null,
        )

        calendarCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val calendarId = cursor.getLong(0)
                val displayName = cursor.getString(1) ?: "Unknown"
                val accountName = cursor.getString(2) ?: "Unknown Account"
                val accountType = cursor.getString(3) ?: "Unknown Type"
                val accessLevel = cursor.getInt(4)

                val accessLevelText = when (accessLevel) {
                    CalendarContract.Calendars.CAL_ACCESS_NONE -> "NONE"
                    CalendarContract.Calendars.CAL_ACCESS_READ -> "READ"
                    CalendarContract.Calendars.CAL_ACCESS_RESPOND -> "RESPOND"
                    CalendarContract.Calendars.CAL_ACCESS_OVERRIDE -> "OVERRIDE"
                    CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR -> "CONTRIBUTOR"
                    CalendarContract.Calendars.CAL_ACCESS_EDITOR -> "EDITOR"
                    CalendarContract.Calendars.CAL_ACCESS_OWNER -> "OWNER"
                    else -> "UNKNOWN($accessLevel)"
                }

                Log.d("CalendarRepository", "📅 Calendar: '$displayName' (ID: $calendarId)")
                Log.d(
                    "CalendarRepository",
                    "   Account: $accountName ($accountType) - Access: $accessLevelText",
                )
            }
        }
    }
}
