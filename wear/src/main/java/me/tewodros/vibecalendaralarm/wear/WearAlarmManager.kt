package me.tewodros.vibecalendaralarm.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scheduling and canceling alarms on Wear OS device.
 * Works independently of phone connection.
 */
@Singleton
class WearAlarmManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val TAG = "WearAlarmManager"

    /**
     * Schedule an exact alarm for a calendar event reminder
     */
    fun scheduleReminder(eventId: Long, eventTitle: String, eventStartTime: Long, reminderTimeMs: Long) {
        try {
            val intent = Intent(context, WearAlarmReceiver::class.java).apply {
                action = "me.tewodros.vibecalendaralarm.wear.REMINDER_ALARM"
                putExtra("event_id", eventId)
                putExtra("event_title", eventTitle)
                putExtra("event_start_time", eventStartTime)
                putExtra("reminder_time", reminderTimeMs)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                eventId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule exact alarm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTimeMs,
                        pendingIntent
                    )
                    Log.d(TAG, "✅ Scheduled exact alarm for event $eventId at $reminderTimeMs")
                } else {
                    Log.w(TAG, "⚠️ Cannot schedule exact alarms - permission needed")
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTimeMs,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderTimeMs,
                    pendingIntent
                )
            }

            Log.d(TAG, "📅 Scheduled reminder on watch: '$eventTitle' for ${java.util.Date(reminderTimeMs)}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule reminder: ${e.message}", e)
        }
    }

    /**
     * Cancel a scheduled reminder
     */
    fun cancelReminder(eventId: Long) {
        try {
            val intent = Intent(context, WearAlarmReceiver::class.java).apply {
                action = "me.tewodros.vibecalendaralarm.wear.REMINDER_ALARM"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                eventId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()

            Log.d(TAG, "🗑️ Cancelled reminder for event $eventId on watch")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel reminder: ${e.message}", e)
        }
    }

    /**
     * Check if we can schedule exact alarms
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
