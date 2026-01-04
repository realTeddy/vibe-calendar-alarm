package me.tewodros.vibecalendaralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Receives boot completed broadcast and starts the reminder scheduler service
 * This ensures the background service starts automatically when device boots
 *
 * Uses goAsync() to perform work on a background thread to prevent ANR
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            -> {
                Log.d(TAG, "Starting background alarm rescheduling after boot/update")

                // Use goAsync() to extend the broadcast receiver's lifetime
                // This allows us to do background work without ANR
                val pendingResult = goAsync()

                // Create a scoped coroutine that will be cleaned up properly
                val job = SupervisorJob()
                val scope = CoroutineScope(Dispatchers.IO + job)

                scope.launch {
                    try {
                        // Timeout after 30 seconds to prevent ANR and resource leak
                        withTimeout(BOOT_TIMEOUT_MS) {
                            // Start WorkManager-based periodic checking
                            ReminderWorkManager.startPeriodicMonitoring(context)
                            Log.d(TAG, "WorkManager monitoring started")

                            // Reschedule all alarms - alarms are lost on reboot
                            // Using CalendarManager for now, will migrate to repository later
                            val calendarManager = CalendarManager(context)
                            calendarManager.scheduleAllReminders()
                            Log.d(TAG, "✅ All alarms rescheduled successfully after boot")
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "❌ Permission denied during boot scheduling: ${e.message}")
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        Log.e(TAG, "❌ Boot scheduling timed out after ${BOOT_TIMEOUT_MS}ms")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to reschedule alarms after boot: ${e.message}")
                    } finally {
                        // Clean up the coroutine scope to prevent leaks
                        scope.cancel()
                        // Signal that we're done with the broadcast
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_TIMEOUT_MS = 30_000L // 30 seconds max for boot scheduling
    }
}

