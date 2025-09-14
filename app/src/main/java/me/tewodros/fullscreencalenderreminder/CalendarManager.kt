package me.tewodros.vibecalendaralarm

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
import me.tewodros.vibecalendaralarm.model.CalendarEvent

/**
 * CalendarManager handles calendar event retrieval and alarm scheduling.
 * * This class provides functionality to:
 * - Query calendar events from the device's calendar providers
 * - Automatically schedule 1-minute reminders for ALL calendar events
 * - Handle recurring events properly through the Instances table
 * - Cache calendar data for performance optimization
 * - Manage alarm cleanup for deleted events
 * * Key Features:
 * - Universal 1-minute reminders for all events (regardless of existing reminders)
 * - Intelligent caching to reduce database queries
 * - Support for recurring events via CalendarContract.Instances
 * - Automatic cleanup of orphaned alarms
 * - Performance optimized with 2-day lookahead window
 * * @param context The application context for system service access
 */
class CalendarManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Performance optimization: Cache calendar events to reduce database queries
    private var cachedEvents: List<CalendarEvent>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 30 * 1000L // 30 seconds cache duration

    // Calendar list caching to avoid repeated calendar enumeration
    private var calendarListCached: Boolean = false
    private var calendarListTimestamp: Long = 0
    private val CALENDAR_LIST_CACHE_DURATION = 5 * 60 * 1000L // 5 minutes cache duration

    companion object {
        // Event query constants
        private const val QUERY_DAYS_AHEAD = 30 // Days to look ahead for events (one month)
        private const val HOURS_PER_DAY = 24
        private const val MINUTES_PER_HOUR = 60
        private const val SECONDS_PER_MINUTE = 60
        private const val MILLIS_PER_SECOND = 1000L

        // Alarm scheduling constants
        private const val DEFAULT_REMINDER_MINUTES = 1 // Default 1-minute reminder for all events
        private const val MAX_ALARM_SCHEDULE_ATTEMPTS = 3 // Retry limit for alarm scheduling

        // Audio fade-in constants (used in ReminderActivity)
        const val AUDIO_FADE_DURATION_SECONDS = 30 // Gradual volume increase duration
        const val AUDIO_FADE_START_VOLUME = 0.01f // Starting volume (1%)
        const val AUDIO_FADE_END_VOLUME = 1.0f // Ending volume (100%)
    }

    /**
     * Retrieves upcoming calendar events and ensures all have 1-minute reminders.
     * * This method performs the following operations:
     * 1. Checks for calendar permissions
     * 2. Uses intelligent caching to improve performance
     * 3. Queries calendar events for the next 30 days using the Instances table
     * 4. Properly handles recurring events by expanding them through CalendarContract.Instances
     * 5. Ensures ALL events get an automatic 1-minute reminder, regardless of existing reminders
     * * Performance optimizations:
     * - Uses 30-second cache for calendar events
     * - Uses 5-minute cache for calendar list enumeration
     * - Queries up to 30 days ahead to pick up reminders set a month in advance
     * - Queries Instances table for efficient recurring event handling
     * * @return List of CalendarEvent objects with reminder information
     * @throws SecurityException if calendar permissions are not granted
     */
    fun getUpcomingEventsWithReminders(): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CalendarManager", "Calendar permission not granted")
            return emptyList()
        }

        // Check cache first for performance
        val currentTime = System.currentTimeMillis()
        if (cachedEvents != null && (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
            Log.d(
                "CalendarManager",
                "📦 Returning cached events (${cachedEvents!!.size} events) - cache age: ${(currentTime - cacheTimestamp) / 1000}s",
            )
            return cachedEvents!!
        }

        Log.d("CalendarManager", "🔄 Cache miss or expired - fetching fresh calendar data")

        // Log available calendars (but cache this too to avoid repeated queries)
        if (!calendarListCached || (currentTime - calendarListTimestamp) > CALENDAR_LIST_CACHE_DURATION) {
            logAvailableCalendars()
            calendarListCached = true
            calendarListTimestamp = currentTime
        }

        val events = mutableListOf<CalendarEvent>()

        // Query for events in the next month using INSTANCES table for recurring event support
        // Extended to 30 days to pick up reminders set a month in advance
        val startTime = System.currentTimeMillis()
        val endTime = startTime + (QUERY_DAYS_AHEAD * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLIS_PER_SECOND)

        Log.d(
            "CalendarManager",
            "Querying events for next $QUERY_DAYS_AHEAD days (${formatTime(startTime)} to ${formatTime(
                endTime,
            )})",
        )

        // Build URI for instances within time range - this expands recurring events
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startTime.toString())
            .appendPath(endTime.toString())
            .build()

        val instanceCursor: Cursor? = context.contentResolver.query(
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

        instanceCursor?.use { cursor ->
            val eventIdIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val calendarIdIndex = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
            val calendarNameIndex = cursor.getColumnIndex(
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            )
            val rruleIndex = cursor.getColumnIndex(CalendarContract.Instances.RRULE)

            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(eventIdIndex)
                val title = cursor.getString(titleIndex) ?: "Untitled Event"
                val eventStartTime = cursor.getLong(beginIndex)
                val calendarId = cursor.getLong(calendarIdIndex)
                val calendarName = cursor.getString(calendarNameIndex) ?: "Unknown Calendar"
                val rrule = cursor.getString(rruleIndex)

                val isRecurring = !rrule.isNullOrEmpty()

                // Include ALL events, automatically adding 1-minute reminder if none exist
                val reminderMinutes = getAllReminderMinutes(eventId)
                val finalReminderMinutes = if (reminderMinutes.isEmpty()) {
                    // No existing reminders - add automatic 1-minute reminder
                    listOf(1)
                } else {
                    // Has existing reminders - ensure 1-minute reminder is included
                    val updatedReminders = reminderMinutes.toMutableList()
                    if (!updatedReminders.contains(1)) {
                        updatedReminders.add(1)
                        updatedReminders.sort() // Keep sorted (closest to event first)
                    }
                    updatedReminders
                }

                events.add(CalendarEvent(eventId, title, eventStartTime, finalReminderMinutes))
            }
        }

        // Cache the results for performance
        cachedEvents = events
        cacheTimestamp = currentTime

        Log.d(
            "CalendarManager",
            "Found ${events.size} events with 1-minute reminders (cached for ${CACHE_DURATION_MS / 1000}s)",
        )
        return events
    }

    /**
     * Invalidates all cached calendar data, forcing fresh queries on next access.
     * * This method clears both the events cache and calendar list cache, ensuring that
     * the next call to getUpcomingEventsWithReminders() will fetch fresh data from
     * the calendar provider.
     * * Use this method when:
     * - Calendar data may have changed externally
     * - Before scheduling operations to ensure up-to-date event data
     * - After permission changes that might affect data access
     * - When debugging cache-related issues
     * * The method is automatically called by scheduleAllReminders() to ensure
     * accurate alarm scheduling with the latest event information.
     */
    fun invalidateCache() {
        Log.d("CalendarManager", "🗑️ Invalidating calendar cache")
        cachedEvents = null
        cacheTimestamp = 0
        calendarListCached = false
        calendarListTimestamp = 0
    }

    /**
     * Log all available calendars to help debug why family calendar might not be showing
     */
    private fun logAvailableCalendars() {
        Log.d("CalendarManager", "Scanning available calendars...")

        val calendarCursor: Cursor? = context.contentResolver.query(
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

        calendarCursor?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val visibleIndex = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
            val syncIndex = cursor.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)

            var calendarCount = 0
            var activeCount = 0
            while (cursor.moveToNext()) {
                calendarCount++
                val calendarName = cursor.getString(nameIndex) ?: "Unnamed"
                val isVisible = cursor.getInt(visibleIndex) == 1
                val syncEvents = cursor.getInt(syncIndex) == 1

                if (isVisible && syncEvents) {
                    activeCount++
                }
            }

            Log.d("CalendarManager", "Found $calendarCount calendars ($activeCount active)")
        }
    }

    /**
     * Get all reminder minutes for a specific event
     */
    private fun getAllReminderMinutes(eventId: Long): List<Int> {
        val remindersList = mutableListOf<Int>()

        val reminderCursor: Cursor? = context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            "${CalendarContract.Reminders.MINUTES} ASC", // Sort by time (closest to event first)
        )

        reminderCursor?.use { cursor ->
            val minutesIndex = cursor.getColumnIndex(CalendarContract.Reminders.MINUTES)
            while (cursor.moveToNext()) {
                val minutes = cursor.getInt(minutesIndex)
                if (minutes > 0) { // Only positive reminder times
                    remindersList.add(minutes)
                }
            }
        }

        return remindersList
    }

    /**
     * Schedules multiple alarms for a single calendar event.
     * * This method creates alarms for:
     * 1. All original reminder times configured in the calendar event
     * 2. An additional configurable "final reminder" (default 1 minute before event)
     *    if not already covered by existing reminders
     * * Each alarm is scheduled with a unique identifier to prevent conflicts.
     * The method uses Android's AlarmManager.setExactAndAllowWhileIdle() for
     * precise timing that works even in Doze mode.
     * * @param event The calendar event to schedule reminders for
     * @throws SecurityException if alarm scheduling permissions are not granted
     * * @see scheduleSpecificReminder for individual alarm scheduling logic
     * @see SettingsActivity.getFinalReminderMinutes for configurable reminder time
     */
    fun scheduleReminder(event: CalendarEvent) {
        Log.d(
            "CalendarManager",
            "Scheduling alarms for event: ${event.title} (${event.reminderMinutes.size} reminders)",
        )

        // Schedule all original reminders
        event.reminderMinutes.forEachIndexed { index, minutes ->
            val reminderTime = event.startTime - (minutes * 60 * 1000L)
            val reminderType = "ORIGINAL_$index" // Make each original reminder unique
            scheduleSpecificReminder(event, reminderTime, reminderType)
        }

        // Schedule configurable final reminder
        val finalReminderMinutes = SettingsActivity.getFinalReminderMinutes(context)
        val hasFinalReminderAlready = event.reminderMinutes.contains(finalReminderMinutes)

        if (!hasFinalReminderAlready) {
            val finalReminderTime = event.startTime - (finalReminderMinutes * 60 * 1000L)
            val reminderType = if (finalReminderMinutes == 0) "AT_EVENT_TIME" else "FINAL_REMINDER_${finalReminderMinutes}MIN"
            scheduleSpecificReminder(event, finalReminderTime, reminderType)
            Log.d(
                "CalendarManager",
                "⏰ Scheduled final reminder: $finalReminderMinutes minutes before event",
            )
        } else {
            Log.d(
                "CalendarManager",
                "⏭ Skipping final reminder - already covered by existing $finalReminderMinutes-minute reminder",
            )
        }

        val totalAlarms = event.reminderMinutes.size + (if (hasFinalReminderAlready) 0 else 1)
        Log.d("CalendarManager", "=== MULTIPLE ALARM SCHEDULING COMPLETE ===")
        Log.d(
            "CalendarManager",
            "Total alarms scheduled: $totalAlarms (${event.reminderMinutes.size} original${if (hasFinalReminderAlready) "" else " + 1 final reminder"})",
        )
    }

    /**
     * Schedule a specific reminder with a given time and type - with retry logic
     */
    private fun scheduleSpecificReminder(event: CalendarEvent, reminderTime: Long, type: String) {
        val dateFormat = SimpleDateFormat("MMM dd, h:mm:ss a", Locale.getDefault())
        val now = System.currentTimeMillis()

        Log.d("CalendarManager", "=== SCHEDULING $type REMINDER ===")
        Log.d("CalendarManager", "Event: ${event.title} (ID: ${event.id})")
        Log.d("CalendarManager", "Event start time: ${dateFormat.format(Date(event.startTime))}")
        Log.d("CalendarManager", "Reminder time: ${dateFormat.format(Date(reminderTime))} ($type)")
        Log.d("CalendarManager", "Current time: ${dateFormat.format(Date(now))}")
        Log.d("CalendarManager", "Time until reminder: ${(reminderTime - now) / 1000} seconds")

        // Don't schedule reminders in the past (with 5 second grace period)
        if (reminderTime <= (now + 5000)) {
            Log.w(
                "CalendarManager",
                "$type reminder time is in the past or too soon, skipping: ${event.title}",
            )
            Toast.makeText(
                context,
                "⚠️ Skipped $type reminder (too soon): ${event.title}",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        // Check if we can schedule exact alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("CalendarManager", "❌ Cannot schedule exact alarms - permission not granted")
                Toast.makeText(
                    context,
                    "❌ Exact alarm permission required for precise reminders",
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
        }

        // Try to schedule with retry logic
        var attempts = 0
        val maxAttempts = 3
        var success = false

        while (attempts < maxAttempts && !success) {
            attempts++
            Log.d("CalendarManager", "Scheduling attempt $attempts/$maxAttempts for $type")

            success = attemptAlarmScheduling(event, reminderTime, type, attempts)

            if (!success && attempts < maxAttempts) {
                Log.w("CalendarManager", "Attempt $attempts failed, waiting before retry...")
                try {
                    Thread.sleep(500) // Wait 500ms before retry
                } catch (e: InterruptedException) {
                    Log.e("CalendarManager", "Sleep interrupted: ${e.message}")
                    break
                }
            }
        }

        if (success) {
            Log.d(
                "CalendarManager",
                "✅ $type alarm scheduled successfully after $attempts attempts",
            )
        } else {
            Log.e("CalendarManager", "❌ Failed to schedule $type alarm after $maxAttempts attempts")
            Toast.makeText(context, "❌ Failed to schedule $type: ${event.title}", Toast.LENGTH_LONG).show()
        }
    }

    private fun attemptAlarmScheduling(
        event: CalendarEvent,
        reminderTime: Long,
        type: String,
        attempt: Int,
    ): Boolean {
        try {
            // Create simple, unique alarm ID using hash of event ID + type
            val alarmId = "${event.id}_$type".hashCode()

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("event_id", event.id)
                putExtra("event_title", event.title)
                putExtra("event_start_time", event.startTime)
                putExtra("reminder_type", type) // Add type for receiver to handle differently if needed

                // Add action to make intent unique and help with debugging
                action = "REMINDER_ALARM_${event.id}_$type"
            }

            Log.d(
                "CalendarManager",
                "Creating PendingIntent with action: ${intent.action}, alarmId: $alarmId",
            )

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId, // Use unique alarm ID
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            Log.d("CalendarManager", "PendingIntent created: $pendingIntent")

            // Schedule exact alarm
            try {
                Log.d("CalendarManager", "=== STARTING ALARM SCHEDULING ===")
                Log.d("CalendarManager", "Event: ${event.title} (ID: ${event.id})")
                Log.d("CalendarManager", "Alarm Type: $type, Alarm ID: $alarmId")
                Log.d(
                    "CalendarManager",
                    "Current time: ${System.currentTimeMillis()} (${formatTime(
                        System.currentTimeMillis(),
                    )})",
                )
                Log.d(
                    "CalendarManager",
                    "Reminder time: $reminderTime (${formatTime(reminderTime)})",
                )
                Log.d(
                    "CalendarManager",
                    "Time until alarm: ${(reminderTime - System.currentTimeMillis()) / 1000} seconds",
                )

                // Use setExactAndAllowWhileIdle() for reliable background alarms
                // This bypasses doze mode while not creating system clock alarms
                Log.d(
                    "CalendarManager",
                    "Using setExactAndAllowWhileIdle() for reliable background alarm...",
                )

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
                Log.d("CalendarManager", "✓ setAlarmClock() call completed with highest priority")

                // IMMEDIATE VERIFICATION: Check if alarm is actually scheduled
                Log.d("CalendarManager", "=== VERIFYING ALARM REGISTRATION ===")
                val verifyIntent = PendingIntent.getBroadcast(
                    context,
                    alarmId, // Use same unique alarm ID
                    Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("event_id", event.id)
                        putExtra("event_title", event.title)
                        putExtra("event_start_time", event.startTime)
                        putExtra("reminder_type", type)
                        action = "REMINDER_ALARM_${event.id}_$type"
                    },
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )

                if (verifyIntent != null) {
                    Log.d(
                        "CalendarManager",
                        "✅ VERIFICATION SUCCESS: $type alarm is registered in system (attempt $attempt)",
                    )
                    if (attempt == 1) {
                        Toast.makeText(
                            context,
                            "✅ Scheduled $type: ${event.title}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return true // Success!
                } else {
                    Log.e(
                        "CalendarManager",
                        "❌ VERIFICATION FAILED: $type alarm NOT found in system after scheduling (attempt $attempt)",
                    )
                    Log.e("CalendarManager", "This means AlarmManager silently rejected the alarm!")
                    if (attempt == 1) {
                        Toast.makeText(
                            context,
                            "❌ FAILED $type: ${event.title} - Check battery optimization",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    return false // Failed verification
                }
            } catch (e: SecurityException) {
                Log.e(
                    "CalendarManager",
                    "❌ SecurityException scheduling $type alarm (attempt $attempt): ${e.message}",
                )
                Log.e("CalendarManager", "This usually means exact alarm permission is missing")
                if (attempt == 1) {
                    Toast.makeText(
                        context,
                        "Permission denied $type: ${event.title}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return false
            } catch (e: Exception) {
                Log.e(
                    "CalendarManager",
                    "❌ Unexpected exception scheduling $type alarm (attempt $attempt): ${e.message}",
                )
                if (attempt == 1) {
                    Toast.makeText(
                        context,
                        "Error scheduling $type: ${event.title}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return false
            }
        } catch (e: Exception) {
            Log.e(
                "CalendarManager",
                "❌ Failed to create PendingIntent for $type alarm (attempt $attempt): ${e.message}",
            )
            return false
        }
    }

    /**
     * Schedules alarms for all upcoming calendar events.
     * * This is the main entry point for bulk alarm scheduling. The method:
     * 1. Retrieves all upcoming events with reminders (up to 30 days ahead)
     * 2. Cleans up alarms for events that have been deleted from the calendar
     * 3. Schedules new alarms for events that don't already have them
     * 4. Skips events that already have all required alarms scheduled
     * * The method includes comprehensive logging for debugging and shows
     * a toast notification with the results.
     * * Performance considerations:
     * - Invalidates the events cache to ensure fresh data
     * - Uses batch processing to handle multiple events efficiently
     * - Includes retry logic for alarm scheduling failures
     * * @throws SecurityException if calendar or alarm permissions are not granted
     * * @see getUpcomingEventsWithReminders for event retrieval
     * @see cleanupDeletedEventAlarms for orphaned alarm cleanup
     * @see scheduleReminder for individual event scheduling
     */
    fun scheduleAllReminders() {
        Log.d("CalendarManager", "=== STARTING TO SCHEDULE ALL REMINDERS ===")

        // Invalidate cache since we're about to modify alarm state
        invalidateCache()

        val events = getUpcomingEventsWithReminders()
        Log.d("CalendarManager", "Found ${events.size} events with reminders")

        // FIRST: Clean up alarms for deleted events
        val cleanedCount = cleanupDeletedEventAlarms(events)
        if (cleanedCount > 0) {
            Log.d("CalendarManager", "🧹 Cleaned up $cleanedCount alarms for deleted events")
        }

        if (events.isEmpty()) {
            Log.w("CalendarManager", "No events found with reminders")
            Toast.makeText(context, "No upcoming events with reminders found", Toast.LENGTH_LONG).show()
            return
        }

        var scheduledCount = 0
        var alreadyScheduledCount = 0
        events.forEach { event ->
            Log.d("CalendarManager", "=== PROCESSING REAL CALENDAR EVENT ===")
            Log.d("CalendarManager", "Event: ${event.title} (ID: ${event.id})")
            Log.d(
                "CalendarManager",
                "Start time: ${event.startTime} (${formatTime(event.startTime)})",
            )
            Log.d(
                "CalendarManager",
                "Reminder minutes: ${event.reminderMinutes.joinToString(", ")}",
            )
            Log.d(
                "CalendarManager",
                "Current time: ${System.currentTimeMillis()} (${formatTime(
                    System.currentTimeMillis(),
                )})",
            )

            // Check if any reminder times are in the past
            val currentTime = System.currentTimeMillis()
            val validReminders = event.reminderMinutes.filter { minutes ->
                val reminderTime = event.startTime - (minutes * 60 * 1000)
                reminderTime > currentTime
            }

            if (validReminders.isEmpty() && (event.startTime - 60 * 1000) <= currentTime) {
                Log.w("CalendarManager", "⚠ All reminder times are in the past for: ${event.title}")
                return@forEach
            }

            // Check if ALL alarms are already scheduled (more detailed check)
            val allAlarmsScheduled = areAllAlarmsScheduled(event)
            if (allAlarmsScheduled) {
                Log.d(
                    "CalendarManager",
                    "⏭ All alarms already scheduled for: ${event.title} - skipping",
                )
                alreadyScheduledCount++
                return@forEach
            }

            Log.d("CalendarManager", "✓ Scheduling new reminders for: ${event.title}")

            try {
                scheduleReminder(event)
                scheduledCount++
                Log.d("CalendarManager", "✓ Successfully processed event: ${event.title}")
            } catch (e: Exception) {
                Log.e(
                    "CalendarManager",
                    "❌ Failed to schedule reminder for ${event.title}: ${e.message}",
                )
            }
        }

        Log.d("CalendarManager", "=== SCHEDULING COMPLETE ===")
        Log.d("CalendarManager", "New alarms scheduled: $scheduledCount")
        Log.d("CalendarManager", "Already scheduled (skipped): $alreadyScheduledCount")
        Log.d("CalendarManager", "Total events processed: ${events.size}")
        Log.d("CalendarManager", "Deleted event alarms cleaned: $cleanedCount")

        val cleanupMessage = if (cleanedCount > 0) " (cleaned $cleanedCount orphaned alarms)" else ""

        if (scheduledCount > 0) {
            Toast.makeText(
                context,
                "Scheduled $scheduledCount new reminders$cleanupMessage",
                Toast.LENGTH_LONG,
            ).show()
        } else if (alreadyScheduledCount > 0) {
            Toast.makeText(
                context,
                "All $alreadyScheduledCount reminders already scheduled$cleanupMessage",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(
                context,
                "No new reminders to schedule$cleanupMessage",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Clean up alarms for events that no longer exist in the calendar
     * This prevents orphaned alarms from triggering for deleted events
     */
    private fun cleanupDeletedEventAlarms(currentEvents: List<CalendarEvent>): Int {
        Log.d("CalendarManager", "🧹 Starting cleanup of deleted event alarms...")

        // More efficient approach: use SharedPreferences to track previously scheduled events
        val prefs = context.getSharedPreferences("scheduled_events", Context.MODE_PRIVATE)
        val previousEventIds = prefs.getStringSet("event_ids", emptySet()) ?: emptySet()
        val currentEventIds = currentEvents.map { it.id.toString() }.toSet()

        var cleanedCount = 0

        // Find events that were previously scheduled but are no longer in the calendar
        val deletedEventIds = previousEventIds - currentEventIds

        deletedEventIds.forEach { eventIdString ->
            try {
                val eventId = eventIdString.toLong()
                val cleaned = cancelAllAlarmsForEvent(eventId)
                cleanedCount += cleaned
                Log.d("CalendarManager", "🗑 Cleaned up $cleaned alarms for deleted event $eventId")
            } catch (e: Exception) {
                Log.w("CalendarManager", "Error cleaning up event $eventIdString: ${e.message}")
            }
        }

        // Update the stored event IDs to the current set
        prefs.edit().putStringSet("event_ids", currentEventIds).apply()

        Log.d(
            "CalendarManager",
            "🧹 Cleanup complete: cleaned $cleanedCount alarms for ${deletedEventIds.size} deleted events",
        )
        return cleanedCount
    }

    /**
     * Cancel all types of alarms for a specific event
     * Returns the number of alarms cancelled
     */
    private fun cancelAllAlarmsForEvent(eventId: Long): Int {
        var cancelledCount = 0

        // List of all possible alarm types we might have scheduled
        val alarmTypes = listOf(
            "AT_EVENT_TIME",
            "ONE_MINUTE_BEFORE",
            "5_MINUTES_BEFORE",
            "10_MINUTES_BEFORE",
            "15_MINUTES_BEFORE",
            "30_MINUTES_BEFORE",
            "60_MINUTES_BEFORE",
            "1440_MINUTES_BEFORE", // 1 day
        )

        alarmTypes.forEach { alarmType ->
            try {
                // Create the alarm ID and cancel the alarm directly using AlarmManager
                val alarmId = (eventId.toString() + alarmType).hashCode()

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("event_id", eventId)
                    putExtra("reminder_type", alarmType)
                    action = "REMINDER_ALARM_${eventId}_$alarmType"
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarmId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                alarmManager.cancel(pendingIntent)
                cancelledCount++
                Log.d("CalendarManager", "Cancelled $alarmType alarm for deleted event $eventId")
            } catch (e: Exception) {
                Log.w(
                    "CalendarManager",
                    "Error cancelling $alarmType for event $eventId: ${e.message}",
                )
            }
        }

        return cancelledCount
    }

    /**
     * Cancel all reminders for an event (both original and one-minute-before)
     */
    fun cancelReminder(eventId: Long) {
        // For real calendar events, cancel all alarm types
        val events = getUpcomingEventsWithReminders()
        val event = events.find { it.id == eventId }

        if (event != null) {
            // Cancel all original reminder alarms
            event.reminderMinutes.forEachIndexed { index, _ ->
                cancelSpecificReminder(event.id, "ORIGINAL_$index", event.title, event.startTime)
            }

            // Cancel final reminder alarm (configurable)
            val finalReminderMinutes = SettingsActivity.getFinalReminderMinutes(context)
            val finalReminderType = if (finalReminderMinutes == 0) "AT_EVENT_TIME" else "FINAL_REMINDER_${finalReminderMinutes}MIN"
            cancelSpecificReminder(event.id, finalReminderType, event.title, event.startTime)

            // Also cancel legacy one-minute-before for compatibility
            cancelSpecificReminder(event.id, "ONE_MINUTE_BEFORE", event.title, event.startTime)

            Log.d(
                "CalendarManager",
                "Cancelled all ${event.reminderMinutes.size + 1} alarms for event $eventId '${event.title}'",
            )
        } else {
            // Fallback for unknown events - try to cancel common alarm patterns
            Log.w(
                "CalendarManager",
                "Event $eventId not found, attempting to cancel common alarm patterns",
            )

            // Try to cancel up to 10 possible original reminders + one-minute-before
            for (index in 0..9) {
                cancelSpecificReminder(eventId, "ORIGINAL_$index", "Unknown Event", 0L)
            }

            // Cancel final reminder alarm
            val finalReminderMinutes = SettingsActivity.getFinalReminderMinutes(context)
            val finalReminderType = if (finalReminderMinutes == 0) "AT_EVENT_TIME" else "FINAL_REMINDER_${finalReminderMinutes}MIN"
            cancelSpecificReminder(eventId, finalReminderType, "Unknown Event", 0L)

            // Also cancel legacy one-minute-before for compatibility
            cancelSpecificReminder(eventId, "ONE_MINUTE_BEFORE", "Unknown Event", 0L)

            Log.d(
                "CalendarManager",
                "Attempted to cancel multiple alarm patterns for unknown event $eventId",
            )
        }
    }

    /**
     * Cancel a specific alarm type for an event
     */
    private fun cancelSpecificReminder(eventId: Long, type: String, title: String, startTime: Long) {
        // Use same simple ID logic as scheduling
        val alarmId = "${eventId}_$type".hashCode()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("event_title", title)
            putExtra("event_start_time", startTime)
            if (type != "TEST") {
                putExtra("reminder_type", type)
                action = "REMINDER_ALARM_${eventId}_$type"
            } else {
                action = "REMINDER_ALARM_$eventId"
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.cancel(pendingIntent)
        Log.d("CalendarManager", "Cancelled $type alarm for event $eventId (alarmId: $alarmId)")
    }

    /**
     * Check if a specific alarm is currently scheduled for the correct time
     * Returns false if event time changed and alarm needs rescheduling
     */
    fun isAlarmScheduled(eventId: Long): Boolean {
        return try {
            // Get the current event details to check if time changed
            val events = getUpcomingEventsWithReminders()
            val event = events.find { it.id == eventId }

            if (event == null) {
                Log.d("CalendarManager", "Event $eventId not found - alarm not needed")
                return false
            }

            // Check all alarm types
            val originalAlarmsScheduled = event.reminderMinutes.mapIndexed { index, _ ->
                isSpecificAlarmScheduled(event, "ORIGINAL_$index")
            }

            // Check final reminder (configurable)
            val finalReminderMinutes = SettingsActivity.getFinalReminderMinutes(context)
            val finalReminderType = if (finalReminderMinutes == 0) "AT_EVENT_TIME" else "FINAL_REMINDER_${finalReminderMinutes}MIN"
            val finalReminderScheduled = isSpecificAlarmScheduled(event, finalReminderType)

            // Legacy check for old one-minute-before alarms
            val legacyOneMinuteScheduled = isSpecificAlarmScheduled(event, "ONE_MINUTE_BEFORE")

            // Consider event as "scheduled" if at least one alarm exists
            val anyOriginalScheduled = originalAlarmsScheduled.any { it }
            val anyScheduled = anyOriginalScheduled || finalReminderScheduled || legacyOneMinuteScheduled

            Log.d("CalendarManager", "Alarm status for event $eventId '${event.title}':")
            event.reminderMinutes.forEachIndexed { index, minutes ->
                val scheduled = originalAlarmsScheduled[index]
                Log.d(
                    "CalendarManager",
                    "  - Original reminder #$index ($minutes min): ${if (scheduled) "✓ scheduled" else "✗ missing"}",
                )
            }
            Log.d(
                "CalendarManager",
                "  - Final reminder ($finalReminderMinutes min): ${if (finalReminderScheduled) "✓ scheduled" else "✗ missing"}",
            )
            if (legacyOneMinuteScheduled) {
                Log.d("CalendarManager", "  - Legacy one-minute reminder: ✓ scheduled")
            }
            Log.d(
                "CalendarManager",
                "  - Overall status: ${if (anyScheduled) "✓ at least one alarm" else "✗ no alarms"}",
            )

            anyScheduled
        } catch (e: Exception) {
            Log.e("CalendarManager", "Error checking alarm status for event $eventId: ${e.message}")
            false
        }
    }

    /**
     * Check if a specific alarm type is scheduled for an event
     */
    private fun isSpecificAlarmScheduled(event: CalendarEvent, type: String): Boolean {
        return try {
            // Use same simple ID logic as scheduling
            val alarmId = "${event.id}_$type".hashCode()

            Log.d(
                "CalendarManager",
                "Checking alarm: type=$type, alarmId=$alarmId for event ${event.id}",
            )

            // Create intent that EXACTLY matches what we use for scheduling
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("event_id", event.id)
                putExtra("event_title", event.title)
                putExtra("event_start_time", event.startTime)
                putExtra("reminder_type", type)
                action = "REMINDER_ALARM_${event.id}_$type"
            }

            Log.d("CalendarManager", "Checking for PendingIntent with action: ${intent.action}")

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )

            val exists = pendingIntent != null
            Log.d(
                "CalendarManager",
                "Alarm check result for $type: ${if (exists) "EXISTS" else "NOT FOUND"}",
            )

            exists
        } catch (e: Exception) {
            Log.e(
                "CalendarManager",
                "Error checking $type alarm for event ${event.id}: ${e.message}",
            )
            false
        }
    }

    /**
     * Check if ALL required alarms are scheduled for a specific event
     * This includes all original reminders + the one-minute-before alarm
     */
    private fun areAllAlarmsScheduled(event: CalendarEvent): Boolean {
        return try {
            Log.d(
                "CalendarManager",
                "Checking if ALL alarms are scheduled for event ${event.id} '${event.title}'",
            )

            // Check all original reminders
            val allOriginalScheduled = event.reminderMinutes.mapIndexed { index, minutes ->
                val scheduled = isSpecificAlarmScheduled(event, "ORIGINAL_$index")
                Log.d(
                    "CalendarManager",
                    "  - Original reminder #$index ($minutes min): ${if (scheduled) "✓ scheduled" else "✗ missing"}",
                )
                scheduled
            }.all { it }

            // Check final reminder ONLY if there isn't already a matching reminder
            val finalReminderMinutes = SettingsActivity.getFinalReminderMinutes(context)
            val hasFinalReminder = event.reminderMinutes.contains(finalReminderMinutes)
            val finalReminderNeeded = !hasFinalReminder
            val finalReminderScheduled = if (finalReminderNeeded) {
                val finalReminderType = if (finalReminderMinutes == 0) "AT_EVENT_TIME" else "FINAL_REMINDER_${finalReminderMinutes}MIN"
                isSpecificAlarmScheduled(event, finalReminderType)
            } else {
                true // Consider it "scheduled" if not needed
            }

            if (finalReminderNeeded) {
                Log.d(
                    "CalendarManager",
                    "  - Final reminder ($finalReminderMinutes min): ${if (finalReminderScheduled) "✓ scheduled" else "✗ missing"}",
                )
            } else {
                Log.d(
                    "CalendarManager",
                    "  - Final reminder: ⏭ skipped (already covered by $finalReminderMinutes-minute reminder)",
                )
            }

            val allScheduled = allOriginalScheduled && finalReminderScheduled
            val totalRequired = event.reminderMinutes.size + (if (finalReminderNeeded) 1 else 0)
            Log.d(
                "CalendarManager",
                "  - Result: ${if (allScheduled) "✓ ALL $totalRequired alarms scheduled" else "✗ NOT all alarms scheduled"}",
            )

            allScheduled
        } catch (e: Exception) {
            Log.e(
                "CalendarManager",
                "Error checking all alarms for event ${event.id}: ${e.message}",
            )
            false
        }
    }

    /**
     * Helper function to format timestamps for debugging
     */
    private fun formatTime(timestamp: Long): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat(
                "MM/dd/yyyy HH:mm:ss",
                java.util.Locale.getDefault(),
            )
            dateFormat.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            "Invalid time"
        }
    }
}
