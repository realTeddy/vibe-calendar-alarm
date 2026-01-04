package me.tewodros.vibecalendaralarm.data

import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for Constants
 *
 * Ensures all constant values are within expected ranges
 * and maintain semantic consistency.
 */
class ConstantsTest {

    @Test
    fun `cache durations are positive`() {
        assertThat(Constants.EVENT_CACHE_DURATION_MS).isGreaterThan(0L)
        assertThat(Constants.CALENDAR_LIST_CACHE_DURATION_MS).isGreaterThan(0L)
    }

    @Test
    fun `calendar list cache is longer than event cache`() {
        // Calendar list changes less frequently than events
        assertThat(Constants.CALENDAR_LIST_CACHE_DURATION_MS)
            .isGreaterThan(Constants.EVENT_CACHE_DURATION_MS)
    }

    @Test
    fun `query days ahead is reasonable`() {
        assertThat(Constants.QUERY_DAYS_AHEAD).isGreaterThan(0)
        assertThat(Constants.QUERY_DAYS_AHEAD).isLessThan(365)
    }

    @Test
    fun `alarm scheduling constants are valid`() {
        assertThat(Constants.MAX_ALARM_SCHEDULE_ATTEMPTS).isGreaterThan(0)
        assertThat(Constants.ALARM_RETRY_DELAY_MS).isGreaterThan(0L)
        assertThat(Constants.ALARM_GRACE_PERIOD_MS).isGreaterThan(0L)
    }

    @Test
    fun `audio fade constants are reasonable`() {
        assertThat(Constants.AUDIO_FADE_DURATION_SECONDS).isGreaterThan(0)
        assertThat(Constants.AUDIO_FADE_DURATION_SECONDS).isLessThan(120) // Max 2 minutes

        assertThat(Constants.AUDIO_FADE_START_VOLUME).isGreaterThan(0f)
        assertThat(Constants.AUDIO_FADE_START_VOLUME).isLessThan(1f)
    }

    @Test
    fun `UI constants are valid`() {
        assertThat(Constants.MAX_EVENTS_TO_DISPLAY).isGreaterThan(0)
    }

    @Test
    fun `time constants are mathematically correct`() {
        assertThat(Constants.MILLIS_PER_MINUTE).isEqualTo(60 * 1000L)
        assertThat(Constants.MILLIS_PER_HOUR).isEqualTo(60 * 60 * 1000L)
        assertThat(Constants.MILLIS_PER_DAY).isEqualTo(24 * 60 * 60 * 1000L)
    }

    @Test
    fun `alarm types are properly prefixed`() {
        assertThat(Constants.AlarmTypes.FINAL_REMINDER_PREFIX).isNotEmpty()
        assertThat(Constants.AlarmTypes.ORIGINAL_PREFIX).isNotEmpty()
        assertThat(Constants.AlarmTypes.AT_EVENT_TIME).isNotEmpty()
        assertThat(Constants.AlarmTypes.ONE_MINUTE_BEFORE).isNotEmpty()
    }

    @Test
    fun `getFinalReminderType returns correct format`() {
        // At event time
        assertThat(Constants.AlarmTypes.getFinalReminderType(0))
            .isEqualTo(Constants.AlarmTypes.AT_EVENT_TIME)

        // 15 minutes before
        assertThat(Constants.AlarmTypes.getFinalReminderType(15))
            .contains("15")
    }

    @Test
    fun `getOriginalType returns indexed format`() {
        assertThat(Constants.AlarmTypes.getOriginalType(0)).contains("0")
        assertThat(Constants.AlarmTypes.getOriginalType(5)).contains("5")
    }

    @Test
    fun `preferences keys are unique`() {
        val prefKeys = listOf(
            Constants.Prefs.SCHEDULED_EVENTS,
            Constants.Prefs.ALARM_PREFS,
            Constants.Prefs.PERMISSION_PREFS,
            Constants.Prefs.ONBOARDING_PREFS,
            Constants.Prefs.SETTINGS_PREFS,
        )
        assertThat(prefKeys.toSet().size).isEqualTo(prefKeys.size)
    }

    @Test
    fun `reminder options are sorted ascending`() {
        val options = Constants.REMINDER_OPTIONS_MINUTES.toList()
        assertThat(options).isEqualTo(options.sorted())
    }

    @Test
    fun `reminder options includes zero for at event time`() {
        assertThat(Constants.REMINDER_OPTIONS_MINUTES.toList()).contains(0)
    }
}
