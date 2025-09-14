package me.tewodros.fullscreencalenderreminder

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import me.tewodros.fullscreencalenderreminder.di.HiltWorkerFactory

/**
 * Application class for Hilt dependency injection
 * This annotation tells Hilt to generate components for the entire app
 * Also configures WorkManager with custom factory for dependency injection
 */
@HiltAndroidApp
class CalendarReminderApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("CalendarReminderApp", "Application created with Hilt support")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
