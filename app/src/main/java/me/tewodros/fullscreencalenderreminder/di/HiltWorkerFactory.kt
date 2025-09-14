package me.tewodros.fullscreencalenderreminder.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Singleton
import me.tewodros.fullscreencalenderreminder.ReminderWorker
import me.tewodros.fullscreencalenderreminder.repository.CalendarRepository

/**
 * Custom WorkerFactory for Hilt dependency injection in Workers
 * Allows workers to receive injected dependencies
 */
@Singleton
class HiltWorkerFactory @Inject constructor(
    private val calendarRepository: CalendarRepository,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            ReminderWorker::class.java.name -> {
                ReminderWorker(appContext, workerParameters)
            }
            else -> null
        }
    }
}
