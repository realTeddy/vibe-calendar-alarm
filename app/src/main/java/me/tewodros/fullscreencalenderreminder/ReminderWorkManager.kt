package me.tewodros.vibecalendaralarm

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Manages reliable background calendar monitoring using WorkManager
 * This is the most robust approach for modern Android versions
 * Now includes adaptive scheduling for better battery performance
 */
object ReminderWorkManager {

    private const val WORK_NAME = "ReminderBackgroundWork"
    private const val BACKGROUND_CHECK_INTERVAL_MINUTES = 5L // Balanced 5-minute intervals using OneTimeWork

    /**
     * Start frequent background monitoring using self-scheduling OneTimeWork
     * This bypasses WorkManager's 15-minute minimum for PeriodicWork
     */
    fun startPeriodicMonitoring(context: Context) {
        Log.d(
            "ReminderWorkManager",
            "Starting WorkManager frequent monitoring (bypassing 15min limit)",
        )
        Log.d(
            "ReminderWorkManager",
            "Using self-scheduling OneTimeWork for $BACKGROUND_CHECK_INTERVAL_MINUTES minute intervals",
        )

        // Cancel any existing work first
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        // Schedule the first execution
        scheduleNextWork(context)
    }

    /**
     * Internal method to schedule the next work execution
     * Called by the worker to continue the cycle
     */
    fun scheduleNextWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setConstraints(constraints)
            .setInitialDelay(BACKGROUND_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )

        Log.d(
            "ReminderWorkManager",
            "⏰ Next background check scheduled in $BACKGROUND_CHECK_INTERVAL_MINUTES minute(s)",
        )
    }

    /**
     * Stop frequent background monitoring
     */
    fun stopPeriodicMonitoring(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d("ReminderWorkManager", "❌ Frequent background monitoring stopped")
    }

    /**
     * Check if periodic monitoring is currently running
     */
    fun isMonitoringActive(context: Context): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WORK_NAME)
            .get()

        return workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }

    /**
     * Get status of the background monitoring
     */
    fun getMonitoringStatus(context: Context): String {
        return try {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()

            when {
                workInfos.isEmpty() -> "Not started"
                workInfos.any { it.state == WorkInfo.State.RUNNING } -> "Running"
                workInfos.any { it.state == WorkInfo.State.ENQUEUED } -> "Scheduled"
                workInfos.any { it.state == WorkInfo.State.SUCCEEDED } -> "Completed"
                workInfos.any { it.state == WorkInfo.State.FAILED } -> "Failed"
                workInfos.any { it.state == WorkInfo.State.CANCELLED } -> "Cancelled"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            Log.e("ReminderWorkManager", "Error getting monitoring status: ${e.message}")
            "Error"
        }
    }
}
