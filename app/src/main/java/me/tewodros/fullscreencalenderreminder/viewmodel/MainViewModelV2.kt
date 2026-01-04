package me.tewodros.vibecalendaralarm.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.tewodros.vibecalendaralarm.data.Constants
import me.tewodros.vibecalendaralarm.data.PreferencesManager
import me.tewodros.vibecalendaralarm.model.CalendarEvent
import me.tewodros.vibecalendaralarm.repository.CalendarRepositoryV2

/**
 * ViewModel for MainActivity using proper MVVM with Hilt DI
 * Manages UI state reactively with StateFlow
 */
@HiltViewModel
class MainViewModelV2 @Inject constructor(
    private val calendarRepository: CalendarRepositoryV2,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    // region State

    private val _uiState = MutableStateFlow(MainUiStateV2())
    val uiState: StateFlow<MainUiStateV2> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    // One-time UI events (Toast, Navigation, etc.)
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // endregion

    // region UI Events (sealed class for type-safe event handling)

    sealed class UiEvent {
        data class ShowToast(val message: String, val isLong: Boolean = false) : UiEvent()
        data class ShowError(val message: String, val actionLabel: String? = null) : UiEvent()
        object NavigateToOnboarding : UiEvent()
        object NavigateToSettings : UiEvent()
        object OpenCalendarApp : UiEvent()
    }

    // endregion

    // region Public API

    /**
     * Initialize the ViewModel
     * Should be called in onCreate after permission check
     */
    fun initialize(hasCalendarPermission: Boolean, hasOverlayPermission: Boolean) {
        _uiState.update {
            it.copy(
                hasCalendarPermission = hasCalendarPermission,
                hasOverlayPermission = hasOverlayPermission,
            )
        }

        if (hasCalendarPermission) {
            refreshEvents()
        }
    }

    /**
     * Refresh calendar events
     * @param forceRefresh If true, invalidates cache before fetching
     */
    fun refreshEvents(forceRefresh: Boolean = false) {
        if (!_uiState.value.hasCalendarPermission) {
            emitEvent(UiEvent.ShowToast("Calendar permission required"))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val events = if (forceRefresh) {
                    Log.d(TAG, "Force refreshing events (user requested)")
                    calendarRepository.forceRefreshEvents()
                } else {
                    calendarRepository.getUpcomingEventsWithReminders()
                }

                // Create a new list instance to ensure StateFlow emits even if contents are same
                // This forces the collector to receive the update
                val eventsList = events.take(Constants.MAX_EVENTS_TO_DISPLAY).toMutableList().toList()
                Log.d(TAG, "Setting _events with ${eventsList.size} events (forceRefresh=$forceRefresh)")
                _events.value = eventsList

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        eventCount = events.size,
                        statusMessage = if (events.isEmpty()) {
                            "No upcoming events found"
                        } else {
                            "Found ${events.size} upcoming events"
                        },
                    )
                }

                Log.d(TAG, "Loaded ${events.size} events")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing events", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasError = true,
                        errorMessage = "Failed to load events: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Schedule all reminders for upcoming events
     */
    fun scheduleAllReminders() {
        if (!_uiState.value.hasCalendarPermission) {
            emitEvent(UiEvent.ShowToast("Calendar permission required"))
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    statusMessage = "Scheduling reminders...",
                )
            }

            try {
                when (val result = calendarRepository.scheduleAllReminders()) {
                    is CalendarRepositoryV2.SchedulingState.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = buildSuccessMessage(result),
                            )
                        }
                        emitEvent(UiEvent.ShowToast(buildSuccessMessage(result)))
                    }
                    is CalendarRepositoryV2.SchedulingState.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                hasError = true,
                                errorMessage = result.message,
                            )
                        }
                        emitEvent(UiEvent.ShowError(result.message))
                    }
                    else -> {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }

                // Force refresh events after scheduling to show updated list
                refreshEvents(forceRefresh = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling reminders", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasError = true,
                        errorMessage = "Failed to schedule: ${e.message}",
                    )
                }
                emitEvent(UiEvent.ShowError("Failed to schedule reminders"))
            }
        }
    }

    /**
     * Update permission state (call after permission request result)
     */
    fun updatePermissions(hasCalendarPermission: Boolean, hasOverlayPermission: Boolean) {
        _uiState.update {
            it.copy(
                hasCalendarPermission = hasCalendarPermission,
                hasOverlayPermission = hasOverlayPermission,
            )
        }

        if (hasCalendarPermission && _events.value.isEmpty()) {
            refreshEvents()
        }
    }

    /**
     * Invalidate cache and refresh
     */
    fun invalidateCacheAndRefresh() {
        calendarRepository.invalidateCache()
        refreshEvents()
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.update { it.copy(hasError = false, errorMessage = null) }
    }

    /**
     * Navigate to onboarding
     */
    fun navigateToOnboarding() {
        emitEvent(UiEvent.NavigateToOnboarding)
    }

    /**
     * Navigate to settings
     */
    fun navigateToSettings() {
        emitEvent(UiEvent.NavigateToSettings)
    }

    /**
     * Open calendar app
     */
    fun openCalendarApp() {
        emitEvent(UiEvent.OpenCalendarApp)
    }

    // endregion

    // region Private Methods

    private fun emitEvent(event: UiEvent) {
        viewModelScope.launch {
            _uiEvents.emit(event)
        }
    }

    private fun buildSuccessMessage(result: CalendarRepositoryV2.SchedulingState.Success): String {
        return buildString {
            if (result.alarmsScheduled > 0) {
                append("Scheduled ${result.alarmsScheduled} reminders")
            } else if (result.alarmsSkipped > 0) {
                append("All ${result.alarmsSkipped} reminders already scheduled")
            } else {
                append("No reminders to schedule")
            }

            if (result.deletedEventsCleaned > 0) {
                append(" (cleaned ${result.deletedEventsCleaned} orphaned)")
            }
        }
    }

    // endregion

    companion object {
        private const val TAG = "MainViewModelV2"
    }
}

/**
 * UI state for MainActivity
 */
data class MainUiStateV2(
    val hasCalendarPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String = "Ready",
    val eventCount: Int = 0,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
)
