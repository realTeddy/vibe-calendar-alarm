package me.tewodros.vibecalendaralarm

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for reliable background calendar monitoring
 * Simplified to use CalendarManager directly
 */
class ReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("ReminderWorker", "⏰ Starting background calendar check...")

            val calendarManager = CalendarManager(context)
            val events = calendarManager.getUpcomingEventsWithReminders()
            Log.d(
                "ReminderWorker",
                "Found ${events.size} upcoming events (ALL now have at least 1-minute reminders)",
            )

            if (events.isNotEmpty()) {
                // Always reschedule to ensure all reminders are current
                calendarManager.scheduleAllReminders()
                Log.d("ReminderWorker", "✅ Background scheduling completed successfully")
            } else {
                Log.d("ReminderWorker", "✅ No upcoming events with reminders found")
            }

            // Schedule the next work execution to continue the cycle
            ReminderWorkManager.scheduleNextWork(applicationContext)

            Result.success()
        } catch (e: SecurityException) {
            Log.e("ReminderWorker", "❌ Permission denied during background check: ${e.message}")
            // Still schedule next work even on permission error
            ReminderWorkManager.scheduleNextWork(applicationContext)
            Result.failure()
        } catch (e: Exception) {
            Log.e("ReminderWorker", "❌ Error during background check: ${e.message}")
            // Schedule next work even on general error
            ReminderWorkManager.scheduleNextWork(applicationContext)
            Result.retry()
        }
    }
}
