package me.tewodros.vibecalendaralarm.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for PreferencesManager
 *
 * Tests preference storage and retrieval using mocked SharedPreferences.
 */
class PreferencesManagerTest {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var mockContext: Context
    private lateinit var mockScheduledPrefs: SharedPreferences
    private lateinit var mockSettingsPrefs: SharedPreferences
    private lateinit var mockOnboardingPrefs: SharedPreferences
    private lateinit var mockScheduledEditor: SharedPreferences.Editor
    private lateinit var mockSettingsEditor: SharedPreferences.Editor
    private lateinit var mockOnboardingEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockContext = mockk()
        mockScheduledPrefs = mockk()
        mockSettingsPrefs = mockk()
        mockOnboardingPrefs = mockk()

        // Create editors with proper chaining behavior
        mockScheduledEditor = mockk {
            every { putStringSet(any(), any()) } returns this
            every { remove(any()) } returns this
            every { clear() } returns this
            every { apply() } returns Unit
        }
        mockSettingsEditor = mockk {
            every { putInt(any(), any()) } returns this
            every { clear() } returns this
            every { apply() } returns Unit
        }
        mockOnboardingEditor = mockk {
            every { putBoolean(any(), any()) } returns this
            every { clear() } returns this
            every { apply() } returns Unit
        }

        // Setup SharedPreferences returns for each name
        every {
            mockContext.getSharedPreferences(Constants.Prefs.SCHEDULED_EVENTS, Context.MODE_PRIVATE)
        } returns mockScheduledPrefs

        every {
            mockContext.getSharedPreferences(Constants.Prefs.SETTINGS_PREFS, Context.MODE_PRIVATE)
        } returns mockSettingsPrefs

        every {
            mockContext.getSharedPreferences(Constants.Prefs.ONBOARDING_PREFS, Context.MODE_PRIVATE)
        } returns mockOnboardingPrefs

        // Each prefs has its own editor
        every { mockScheduledPrefs.edit() } returns mockScheduledEditor
        every { mockSettingsPrefs.edit() } returns mockSettingsEditor
        every { mockOnboardingPrefs.edit() } returns mockOnboardingEditor

        preferencesManager = PreferencesManager(mockContext)
    }

    // region Scheduled Events Tests

    @Test
    fun `getScheduledEventIds returns empty set when nothing stored`() {
        // Given
        every {
            mockScheduledPrefs.getStringSet(Constants.Prefs.EVENT_IDS, emptySet())
        } returns emptySet()

        // When
        val result = preferencesManager.getScheduledEventIds()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `getScheduledEventIds returns stored values`() {
        // Given
        val eventIds = setOf("event1", "event2", "event3")
        every {
            mockScheduledPrefs.getStringSet(Constants.Prefs.EVENT_IDS, emptySet())
        } returns eventIds

        // When
        val result = preferencesManager.getScheduledEventIds()

        // Then
        assertThat(result).containsExactlyElementsIn(eventIds)
    }

    @Test
    fun `setScheduledEventIds stores values correctly`() {
        // Given
        val eventIds = setOf("event1", "event2")

        // When
        preferencesManager.setScheduledEventIds(eventIds)

        // Then
        verify { mockScheduledEditor.putStringSet(Constants.Prefs.EVENT_IDS, eventIds) }
        verify { mockScheduledEditor.apply() }
    }

    @Test
    fun `clearScheduledEventIds removes all event IDs`() {
        // When
        preferencesManager.clearScheduledEventIds()

        // Then
        verify { mockScheduledEditor.remove(Constants.Prefs.EVENT_IDS) }
        verify { mockScheduledEditor.apply() }
    }

    // endregion

    // region Settings Tests

    @Test
    fun `getFinalReminderMinutes returns default when not set`() {
        // Given
        every {
            mockSettingsPrefs.getInt(
                Constants.Prefs.FINAL_REMINDER_MINUTES,
                Constants.DEFAULT_FINAL_REMINDER_MINUTES,
            )
        } returns Constants.DEFAULT_FINAL_REMINDER_MINUTES

        // When
        val result = preferencesManager.getFinalReminderMinutes()

        // Then
        assertThat(result).isEqualTo(Constants.DEFAULT_FINAL_REMINDER_MINUTES)
    }

    @Test
    fun `getFinalReminderMinutes returns stored value`() {
        // Given
        every {
            mockSettingsPrefs.getInt(
                Constants.Prefs.FINAL_REMINDER_MINUTES,
                Constants.DEFAULT_FINAL_REMINDER_MINUTES,
            )
        } returns 15

        // When
        val result = preferencesManager.getFinalReminderMinutes()

        // Then
        assertThat(result).isEqualTo(15)
    }

    @Test
    fun `setFinalReminderMinutes stores value`() {
        // When
        preferencesManager.setFinalReminderMinutes(30)

        // Then
        verify { mockSettingsEditor.putInt(Constants.Prefs.FINAL_REMINDER_MINUTES, 30) }
        verify { mockSettingsEditor.apply() }
    }

    // endregion

    // region Onboarding Tests

    @Test
    fun `isOnboardingCompleted returns false by default`() {
        // Given
        every {
            mockOnboardingPrefs.getBoolean(Constants.Prefs.ONBOARDING_COMPLETED, false)
        } returns false

        // When
        val result = preferencesManager.isOnboardingCompleted()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `isOnboardingCompleted returns true when set`() {
        // Given
        every {
            mockOnboardingPrefs.getBoolean(Constants.Prefs.ONBOARDING_COMPLETED, false)
        } returns true

        // When
        val result = preferencesManager.isOnboardingCompleted()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `setOnboardingCompleted stores true by default`() {
        // When
        preferencesManager.setOnboardingCompleted()

        // Then
        verify { mockOnboardingEditor.putBoolean(Constants.Prefs.ONBOARDING_COMPLETED, true) }
        verify { mockOnboardingEditor.apply() }
    }

    @Test
    fun `setOnboardingCompleted stores specified value`() {
        // When
        preferencesManager.setOnboardingCompleted(false)

        // Then
        verify { mockOnboardingEditor.putBoolean(Constants.Prefs.ONBOARDING_COMPLETED, false) }
        verify { mockOnboardingEditor.apply() }
    }

    // endregion

    // region Utility Tests

    @Test
    fun `clearAll clears all preference files`() {
        // When
        preferencesManager.clearAll()

        // Then - each editor's clear() should be called
        verify { mockScheduledEditor.clear() }
        verify { mockSettingsEditor.clear() }
        verify { mockOnboardingEditor.clear() }
        verify { mockScheduledEditor.apply() }
        verify { mockSettingsEditor.apply() }
        verify { mockOnboardingEditor.apply() }
    }

    // endregion
}
