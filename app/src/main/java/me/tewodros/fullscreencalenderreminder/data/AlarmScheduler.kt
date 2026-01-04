package me.tewodros.vibecalendaralarm.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.tewodros.vibecalendaralarm.AlarmReceiver
import me.tewodros.vibecalendaralarm.model.CalendarEvent

/**
 * Handles all alarm scheduling operations
 * Manages creating, canceling, and verifying alarms via Android's AlarmManager
 *
 * Key responsibilities:
 * - Schedule exact alarms for calendar reminders
 * - Cancel alarms when events are removed
 * - Verify alarm registration status
 * - Handle retry logic for failed scheduling attempts
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
) {

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private val dateFormat = SimpleDateFormat("MMM dd, h:mm:ss a", Locale.getDefault())

    /**
     * Result of an alarm scheduling operation
     */
    sealed class ScheduleResult {
        data class Success(val alarmsScheduled: Int) : ScheduleResult()
        data class PartialSuccess(val scheduled: Int, val failed: Int) : ScheduleResult()
        data class Failure(val reason: String) : ScheduleResult()
        object Skipped : ScheduleResult()
    }

    /**
     * Schedule all reminders for a single event
     * Schedules both original reminders and the configurable final reminder
     *
     * @param event The calendar event to schedule reminders for
     * @return ScheduleResult indicating success/failure
     */
    suspend fun scheduleEventReminders(event: CalendarEvent): ScheduleResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Scheduling reminders for: ${event.title}")

            var scheduledCount = 0
            var failedCount = 0

            // Schedule all original reminders
            event.reminderMinutes.forEachIndexed { index, minutes ->
                val reminderTime = event.startTime - (minutes * Constants.MILLIS_PER_MINUTE)
                val reminderType = Constants.AlarmTypes.getOriginalType(index)

                val result = scheduleAlarm(event, reminderTime, reminderType)
                if (result) scheduledCount++ else failedCount++
            }

            // Schedule final reminder if not already covered
            val finalReminderMinutes = preferencesManager.getFinalReminderMinutes()
            val hasFinalReminderAlready = event.reminderMinutes.contains(finalReminderMinutes)

            if (!hasFinalReminderAlready) {
                val finalReminderTime =
                    event.startTime - (finalReminderMinutes * Constants.MILLIS_PER_MINUTE)
                val reminderType = Constants.AlarmTypes.getFinalReminderType(finalReminderMinutes)

                val result = scheduleAlarm(event, finalReminderTime, reminderType)
                if (result) scheduledCount++ else failedCount++
            }

            return@withContext when {
                failedCount == 0 && scheduledCount > 0 -> ScheduleResult.Success(scheduledCount)
                scheduledCount > 0 -> ScheduleResult.PartialSuccess(scheduledCount, failedCount)
                else -> ScheduleResult.Failure("All ${failedCount} alarms failed to schedule")
            }
        }

    /**
     * Schedule a single alarm with retry logic
     * Uses coroutine delay instead of Thread.sleep for non-blocking retry
     *
     * @return true if alarm was successfully scheduled and verified
     */
    private suspend fun scheduleAlarm(
        event: CalendarEvent,
        reminderTime: Long,
        type: String,
    ): Boolean {
        val now = System.currentTimeMillis()

        // Don't schedule alarms in the past
        if (reminderTime <= (now + Constants.ALARM_GRACE_PERIOD_MS)) {
            Log.w(TAG, "$type reminder time is in the past for: ${event.title}")
            return false
        }

        // Check exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "Cannot schedule exact alarms - permission not granted")
            return false
        }

        // Retry loop with exponential backoff
        var attempts = 0
        var delayMs = Constants.ALARM_RETRY_DELAY_MS

        while (attempts < Constants.MAX_ALARM_SCHEDULE_ATTEMPTS) {
            attempts++

            val success = attemptAlarmScheduling(event, reminderTime, type)
            if (success) {
                Log.d(TAG, "✅ $type alarm scheduled after $attempts attempt(s)")
                return true
            }

            if (attempts < Constants.MAX_ALARM_SCHEDULE_ATTEMPTS) {
                Log.w(TAG, "Attempt $attempts failed, retrying in ${delayMs}ms...")
                delay(delayMs)
                delayMs *= 2 // Exponential backoff
            }
        }

        Log.e(TAG, "❌ Failed to schedule $type alarm after $attempts attempts")
        return false
    }

    /**
     * Attempt to schedule an alarm and verify it was registered
     */
    private fun attemptAlarmScheduling(
        event: CalendarEvent,
        reminderTime: Long,
        type: String,
    ): Boolean {
        try {
            val alarmId = generateAlarmId(event.id, type)

            val intent = createAlarmIntent(event, type)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // Schedule with setExactAndAllowWhileIdle for Doze mode compatibility
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

            // Verify the alarm was actually registered
            return verifyAlarmRegistered(event, type, alarmId)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling $type alarm: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception scheduling $type alarm: ${e.message}")
            return false
        }
    }

    /**
     * Verify an alarm is registered in the system
     */
    private fun verifyAlarmRegistered(event: CalendarEvent, type: String, alarmId: Int): Boolean {
        val verifyIntent = createAlarmIntent(event, type)
        val verifyPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            verifyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

        return verifyPendingIntent != null
    }

    /**
     * Cancel all reminders for an event
     */
    suspend fun cancelEventReminders(event: CalendarEvent) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Canceling reminders for: ${event.title}")

        // Cancel all original reminder alarms
        event.reminderMinutes.forEachIndexed { index, _ ->
            cancelAlarm(event.id, Constants.AlarmTypes.getOriginalType(index))
        }

        // Cancel final reminder
        val finalReminderMinutes = preferencesManager.getFinalReminderMinutes()
        val finalReminderType = Constants.AlarmTypes.getFinalReminderType(finalReminderMinutes)
        cancelAlarm(event.id, finalReminderType)

        // Cancel legacy one-minute-before for compatibility
        cancelAlarm(event.id, Constants.AlarmTypes.ONE_MINUTE_BEFORE)
    }

    /**
     * Cancel all alarms for an event ID (used when event is deleted)
     *
     * @return Number of alarms cancelled
     */
    suspend fun cancelAllAlarmsForEvent(eventId: Long): Int = withContext(Dispatchers.IO) {
        var cancelledCount = 0

        // Cancel known alarm types
        val alarmTypes = listOf(
            Constants.AlarmTypes.AT_EVENT_TIME,
            Constants.AlarmTypes.ONE_MINUTE_BEFORE,
            "5_MINUTES_BEFORE",
            "10_MINUTES_BEFORE",
            "15_MINUTES_BEFORE",
            "30_MINUTES_BEFORE",
            "60_MINUTES_BEFORE",
            "1440_MINUTES_BEFORE",
        )

        // Add original type variants
        val allTypes = alarmTypes + (0..9).map { Constants.AlarmTypes.getOriginalType(it) }

        allTypes.forEach { type ->
            if (cancelAlarm(eventId, type)) {
                cancelledCount++
            }
        }

        Log.d(TAG, "Cancelled $cancelledCount alarms for event $eventId")
        return@withContext cancelledCount
    }

    /**
     * Cancel a specific alarm
     * @return true if an alarm was found and cancelled
     */
    private fun cancelAlarm(eventId: Long, type: String): Boolean {
        return try {
            val alarmId = generateAlarmId(eventId, type)
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "${Constants.ALARM_ACTION_PREFIX}${eventId}_$type"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled $type alarm for event $eventId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling $type for event $eventId: ${e.message}")
            false
        }
    }

    /**
     * Check if all required alarms are scheduled for an event
     */
    suspend fun areAllAlarmsScheduled(event: CalendarEvent): Boolean = withContext(Dispatchers.IO) {
        // Check all original reminders
        val allOriginalScheduled = event.reminderMinutes.mapIndexed { index, _ ->
            isAlarmScheduled(event, Constants.AlarmTypes.getOriginalType(index))
        }.all { it }

        // Check final reminder if needed
        val finalReminderMinutes = preferencesManager.getFinalReminderMinutes()
        val hasFinalReminder = event.reminderMinutes.contains(finalReminderMinutes)
        val finalReminderScheduled = if (!hasFinalReminder) {
            val type = Constants.AlarmTypes.getFinalReminderType(finalReminderMinutes)
            isAlarmScheduled(event, type)
        } else {
            true
        }

        return@withContext allOriginalScheduled && finalReminderScheduled
    }

    /**
     * Check if a specific alarm is scheduled
     */
    private fun isAlarmScheduled(event: CalendarEvent, type: String): Boolean {
        return try {
            val alarmId = generateAlarmId(event.id, type)
            val intent = createAlarmIntent(event, type)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pendingIntent != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking alarm status: ${e.message}")
            false
        }
    }

    /**
     * Create the intent for alarm broadcasts
     */
    private fun createAlarmIntent(event: CalendarEvent, type: String): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("event_title", event.title)
            putExtra("event_start_time", event.startTime)
            putExtra("calendar_name", event.calendarName)
            putExtra("reminder_type", type)
            action = "${Constants.ALARM_ACTION_PREFIX}${event.id}_$type"
        }
    }

    /**
     * Generate a unique alarm ID from event ID and type
     */
    private fun generateAlarmId(eventId: Long, type: String): Int {
        return "${eventId}_$type".hashCode()
    }

    /**
     * Format time for logging
     */
    private fun formatTime(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    companion object {
        private const val TAG = "AlarmScheduler"
    }
}
