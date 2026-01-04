package me.tewodros.vibecalendaralarm.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized SharedPreferences manager
 * Handles all app preferences in one place for consistency
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scheduledEventsPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(Constants.Prefs.SCHEDULED_EVENTS, Context.MODE_PRIVATE)
    }

    private val settingsPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(Constants.Prefs.SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

    private val onboardingPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(Constants.Prefs.ONBOARDING_PREFS, Context.MODE_PRIVATE)
    }

    // region Scheduled Events

    /**
     * Get previously scheduled event IDs
     */
    fun getScheduledEventIds(): Set<String> {
        return scheduledEventsPrefs.getStringSet(Constants.Prefs.EVENT_IDS, emptySet()) ?: emptySet()
    }

    /**
     * Update the set of scheduled event IDs
     */
    fun setScheduledEventIds(eventIds: Set<String>) {
        scheduledEventsPrefs.edit()
            .putStringSet(Constants.Prefs.EVENT_IDS, eventIds)
            .apply()
        Log.d(TAG, "Updated scheduled event IDs: ${eventIds.size} events")
    }

    /**
     * Clear all scheduled event IDs
     */
    fun clearScheduledEventIds() {
        scheduledEventsPrefs.edit()
            .remove(Constants.Prefs.EVENT_IDS)
            .apply()
        Log.d(TAG, "Cleared all scheduled event IDs")
    }

    // endregion

    // region Settings

    /**
     * Get the final reminder timing in minutes before event
     */
    fun getFinalReminderMinutes(): Int {
        return settingsPrefs.getInt(
            Constants.Prefs.FINAL_REMINDER_MINUTES,
            Constants.DEFAULT_FINAL_REMINDER_MINUTES,
        )
    }

    /**
     * Set the final reminder timing in minutes before event
     */
    fun setFinalReminderMinutes(minutes: Int) {
        settingsPrefs.edit()
            .putInt(Constants.Prefs.FINAL_REMINDER_MINUTES, minutes)
            .apply()
        Log.d(TAG, "Set final reminder minutes to: $minutes")
    }

    // endregion

    // region Onboarding

    /**
     * Check if onboarding has been completed
     */
    fun isOnboardingCompleted(): Boolean {
        return onboardingPrefs.getBoolean(Constants.Prefs.ONBOARDING_COMPLETED, false)
    }

    /**
     * Mark onboarding as completed
     */
    fun setOnboardingCompleted(completed: Boolean = true) {
        onboardingPrefs.edit()
            .putBoolean(Constants.Prefs.ONBOARDING_COMPLETED, completed)
            .apply()
        Log.d(TAG, "Set onboarding completed: $completed")
    }

    // endregion

    // region Utility

    /**
     * Clear all preferences (for debugging/reset)
     */
    fun clearAll() {
        scheduledEventsPrefs.edit().clear().apply()
        settingsPrefs.edit().clear().apply()
        onboardingPrefs.edit().clear().apply()
        Log.d(TAG, "Cleared all preferences")
    }

    // endregion

    companion object {
        private const val TAG = "PreferencesManager"
    }
}
