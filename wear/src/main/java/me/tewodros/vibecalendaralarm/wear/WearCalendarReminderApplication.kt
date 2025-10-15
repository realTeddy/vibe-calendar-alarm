package me.tewodros.vibecalendaralarm.wear

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Wear OS app with Hilt dependency injection
 */
@HiltAndroidApp
class WearCalendarReminderApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private lateinit var instance: WearCalendarReminderApplication

        fun getContext(): Context = instance.applicationContext
    }
}
