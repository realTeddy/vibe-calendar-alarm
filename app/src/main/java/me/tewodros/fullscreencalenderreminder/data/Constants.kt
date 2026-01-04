package me.tewodros.vibecalendaralarm.data

/**
 * Application-wide constants
 * Centralizes all magic numbers and configuration values
 */
object Constants {

    // Event query constants
    const val QUERY_DAYS_AHEAD = 30 // Days to look ahead for events (one month)
    const val HOURS_PER_DAY = 24
    const val MINUTES_PER_HOUR = 60
    const val SECONDS_PER_MINUTE = 60
    const val MILLIS_PER_SECOND = 1000L
    const val MILLIS_PER_MINUTE = SECONDS_PER_MINUTE * MILLIS_PER_SECOND
    const val MILLIS_PER_HOUR = MINUTES_PER_HOUR * MILLIS_PER_MINUTE
    const val MILLIS_PER_DAY = HOURS_PER_DAY * MILLIS_PER_HOUR

    // Background monitoring interval
    const val BACKGROUND_CHECK_INTERVAL_MINUTES = 1L // Check every 1 minute for new events

    // Cache durations
    const val EVENT_CACHE_DURATION_MS = 30 * 1000L // 30 seconds
    const val CALENDAR_LIST_CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    // Alarm scheduling constants
    const val DEFAULT_FINAL_REMINDER_MINUTES = 1
    const val MAX_ALARM_SCHEDULE_ATTEMPTS = 3
    const val ALARM_RETRY_DELAY_MS = 500L
    const val ALARM_GRACE_PERIOD_MS = 5000L // 5 seconds grace period for past alarms
    const val TIME_TOLERANCE_MS = 60000L // 1 minute tolerance for time comparisons

    // Audio fade-in constants (used in ReminderActivity)
    const val AUDIO_FADE_DURATION_SECONDS = 30
    const val AUDIO_FADE_START_VOLUME = 0.01f
    const val AUDIO_FADE_END_VOLUME = 1.0f

    // UI constants
    const val MAX_EVENTS_TO_DISPLAY = 20

    // Default reminder options in minutes
    val REMINDER_OPTIONS_MINUTES = intArrayOf(0, 1, 5, 10, 15, 30, 60, 120, 180, 1440)

    // Alarm type constants for pending intent identification
    object AlarmTypes {
        const val AT_EVENT_TIME = "AT_EVENT_TIME"
        const val ONE_MINUTE_BEFORE = "ONE_MINUTE_BEFORE"
        const val FINAL_REMINDER_PREFIX = "FINAL_REMINDER_"
        const val ORIGINAL_PREFIX = "ORIGINAL_"

        fun getFinalReminderType(minutes: Int): String {
            return if (minutes == 0) AT_EVENT_TIME else "${FINAL_REMINDER_PREFIX}${minutes}MIN"
        }

        fun getOriginalType(index: Int): String = "${ORIGINAL_PREFIX}$index"
    }

    // Intent action prefix
    const val ALARM_ACTION_PREFIX = "REMINDER_ALARM_"

    // SharedPreferences keys
    object Prefs {
        const val SCHEDULED_EVENTS = "scheduled_events"
        const val ALARM_PREFS = "alarm_prefs"
        const val PERMISSION_PREFS = "permission_prefs"
        const val ONBOARDING_PREFS = "onboarding_prefs"
        const val SETTINGS_PREFS = "settings_prefs"

        // Keys within preferences
        const val EVENT_IDS = "event_ids"
        const val FINAL_REMINDER_MINUTES = "final_reminder_minutes"
        const val ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
