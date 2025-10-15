package me.tewodros.vibecalendaralarm.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import me.tewodros.vibecalendaralarm.CalendarManager
import me.tewodros.vibecalendaralarm.repository.CalendarRepository
import me.tewodros.vibecalendaralarm.repository.CalendarRepositoryImpl
import me.tewodros.vibecalendaralarm.wear.WearCommunicationManager

/**
 * Hilt module for providing application-wide dependencies
 * Provides repository and other singleton instances
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides singleton instance of CalendarRepository
     */
    @Provides
    @Singleton
    fun provideCalendarRepository(
        @ApplicationContext context: Context,
    ): CalendarRepository {
        return CalendarRepositoryImpl(context)
    }

    /**
     * Provides singleton instance of CalendarManager with Wear sync support
     */
    @Provides
    @Singleton
    fun provideCalendarManager(
        @ApplicationContext context: Context,
        wearCommunicationManager: WearCommunicationManager
    ): CalendarManager {
        return CalendarManager(context, wearCommunicationManager)
    }
}
