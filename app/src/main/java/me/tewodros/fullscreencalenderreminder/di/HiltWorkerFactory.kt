package me.tewodros.vibecalendaralarm.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Singleton
import me.tewodros.vibecalendaralarm.ReminderWorker
import me.tewodros.vibecalendaralarm.repository.CalendarRepositoryV2

/**
 * Custom WorkerFactory for Hilt dependency injection in Workers
 * Properly injects CalendarRepositoryV2 into ReminderWorker
 */
@Singleton
class HiltWorkerFactory @Inject constructor(
    private val calendarRepository: CalendarRepositoryV2,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            ReminderWorker::class.java.name -> {
                // Properly inject the repository into the worker
                ReminderWorker(appContext, workerParameters, calendarRepository)
            }
            else -> null
        }
    }
}

