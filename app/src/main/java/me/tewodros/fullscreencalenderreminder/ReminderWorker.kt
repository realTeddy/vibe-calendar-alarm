package me.tewodros.vibecalendaralarm

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tewodros.vibecalendaralarm.repository.CalendarRepositoryV2

/**
 * WorkManager worker for reliable background calendar monitoring
 * Uses injected CalendarRepositoryV2 for proper dependency injection
 */
class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val calendarRepository: CalendarRepositoryV2,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "⏰ Starting background calendar check...")

            val events = calendarRepository.getUpcomingEventsWithReminders()
            Log.d(
                TAG,
                "Found ${events.size} upcoming events (ALL now have at least 1-minute reminders)",
            )

            if (events.isNotEmpty()) {
                // Schedule all reminders using the repository
                when (val result = calendarRepository.scheduleAllReminders()) {
                    is CalendarRepositoryV2.SchedulingState.Success -> {
                        Log.d(
                            TAG,
                            "✅ Background scheduling completed: ${result.alarmsScheduled} alarms scheduled",
                        )
                    }
                    is CalendarRepositoryV2.SchedulingState.Error -> {
                        Log.e(TAG, "❌ Scheduling error: ${result.message}")
                    }
                    else -> {
                        Log.d(TAG, "✅ Background scheduling completed")
                    }
                }
            } else {
                Log.d(TAG, "✅ No upcoming events with reminders found")
            }

            // Schedule the next work execution to continue the cycle
            ReminderWorkManager.scheduleNextWork(applicationContext)

            Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permission denied during background check: ${e.message}")
            // Do NOT schedule next work on permission error - prevents infinite fail loop
            // User needs to grant permissions before background checks can resume
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during background check: ${e.message}")
            // Schedule next work even on general error
            ReminderWorkManager.scheduleNextWork(applicationContext)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ReminderWorker"
    }
}
