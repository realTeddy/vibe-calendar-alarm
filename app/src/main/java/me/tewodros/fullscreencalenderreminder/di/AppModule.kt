package me.tewodros.vibecalendaralarm.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import me.tewodros.vibecalendaralarm.data.AlarmScheduler
import me.tewodros.vibecalendaralarm.data.CalendarDataSource
import me.tewodros.vibecalendaralarm.data.EventCache
import me.tewodros.vibecalendaralarm.data.PreferencesManager
import me.tewodros.vibecalendaralarm.repository.CalendarRepository
import me.tewodros.vibecalendaralarm.repository.CalendarRepositoryImpl
import me.tewodros.vibecalendaralarm.repository.CalendarRepositoryV2

/**
 * Hilt module for providing application-wide dependencies
 * Provides repository and other singleton instances
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides singleton instance of PreferencesManager
     */
    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context,
    ): PreferencesManager {
        return PreferencesManager(context)
    }

    /**
     * Provides singleton instance of EventCache
     */
    @Provides
    @Singleton
    fun provideEventCache(): EventCache {
        return EventCache()
    }

    /**
     * Provides singleton instance of CalendarDataSource
     */
    @Provides
    @Singleton
    fun provideCalendarDataSource(
        @ApplicationContext context: Context,
    ): CalendarDataSource {
        return CalendarDataSource(context)
    }

    /**
     * Provides singleton instance of AlarmScheduler
     */
    @Provides
    @Singleton
    fun provideAlarmScheduler(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager,
    ): AlarmScheduler {
        return AlarmScheduler(context, preferencesManager)
    }

    /**
     * Provides singleton instance of CalendarRepositoryV2 (new unified repository)
     */
    @Provides
    @Singleton
    fun provideCalendarRepositoryV2(
        calendarDataSource: CalendarDataSource,
        alarmScheduler: AlarmScheduler,
        eventCache: EventCache,
        preferencesManager: PreferencesManager,
    ): CalendarRepositoryV2 {
        return CalendarRepositoryV2(
            calendarDataSource,
            alarmScheduler,
            eventCache,
            preferencesManager,
        )
    }

    /**
     * Provides singleton instance of CalendarRepository (legacy interface)
     * TODO: Migrate all usages to CalendarRepositoryV2, then remove this
     */
    @Provides
    @Singleton
    fun provideCalendarRepository(
        @ApplicationContext context: Context,
    ): CalendarRepository {
        return CalendarRepositoryImpl(context)
    }
}
