package me.tewodros.vibecalendaralarm.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import me.tewodros.vibecalendaralarm.repository.CalendarRepository
import me.tewodros.vibecalendaralarm.repository.CalendarRepositoryImpl

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
}
