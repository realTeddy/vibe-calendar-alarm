package me.tewodros.fullscreencalenderreminder.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.tewodros.fullscreencalenderreminder.model.CalendarEvent
import me.tewodros.fullscreencalenderreminder.repository.CalendarRepository
import me.tewodros.fullscreencalenderreminder.util.ErrorHandler
import me.tewodros.fullscreencalenderreminder.util.FallbackStrategy

/**
 * ViewModel for MainActivity that manages UI state and business logic
 * Provides reactive state management with StateFlow
 * Now includes comprehensive error handling and user guidance
 */
class MainViewModel(
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Events list
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    // Scheduled alarms
    private val _scheduledAlarms = MutableStateFlow<List<String>>(emptyList())
    val scheduledAlarms: StateFlow<List<String>> = _scheduledAlarms.asStateFlow()

    // User guidance
    private val _userGuidance = MutableStateFlow<List<String>>(emptyList())
    val userGuidance: StateFlow<List<String>> = _userGuidance.asStateFlow()

    /**
     * Check if required permissions are granted
     */
    fun checkPermissions(context: Context) {
        val hasCalendarPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)

        val statusMessage = when {
            !hasCalendarPermission -> {
                _userGuidance.value = FallbackStrategy.generateUserGuidance(
                    SecurityException("Calendar permission denied"),
                    context,
                )
                "Calendar permission required"
            }
            !hasOverlayPermission -> {
                _userGuidance.value = listOf(
                    "Enable overlay permission for full-screen alerts",
                    "Go to Settings > Apps > Special Access > Display over other apps",
                    "Allow Calendar Reminders to display over other apps",
                )
                "Overlay permission required"
            }
            else -> {
                _userGuidance.value = emptyList()
                "All permissions granted ✓"
            }
        }

        _uiState.value = _uiState.value.copy(
            hasCalendarPermission = hasCalendarPermission,
            hasOverlayPermission = hasOverlayPermission,
            statusMessage = statusMessage,
        )

        Log.d(
            "MainViewModel",
            "Permissions - Calendar: $hasCalendarPermission, Overlay: $hasOverlayPermission",
        )
    }

    /**
     * Setup reminders by scheduling all calendar events
     */
    fun setupReminders() {
        if (!_uiState.value.hasCalendarPermission) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Calendar permission required before setup",
                hasError = true,
            )
            _userGuidance.value = listOf(
                "Grant calendar permission to access your events",
                "Go to Settings > Apps > Calendar Reminders > Permissions",
                "Enable Calendar permission",
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = "Setting up calendar reminders...",
                    hasError = false,
                )
                _userGuidance.value = emptyList()

                calendarRepository.scheduleAllReminders()

                // Refresh data after setup
                refreshData()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Reminders setup complete ✓",
                )
            } catch (e: SecurityException) {
                val guidance = FallbackStrategy.generateUserGuidance(
                    e,
                    _events.value.firstOrNull()?.let { calendarRepository as Context } ?: return@launch,
                )
                handleError("Error setting up reminders", e, guidance)
            } catch (e: Exception) {
                val context = _events.value.firstOrNull()?.let { calendarRepository as? Context }
                val errorMessage = context?.let {
                    ErrorHandler.handleGeneralError(
                        it,
                        "reminder setup",
                        e,
                        showToast = false,
                    )
                }
                    ?: "Error setting up reminders: ${e.message}"

                val guidance = context?.let { FallbackStrategy.generateUserGuidance(e, it) } ?: listOf(
                    "Try restarting the app",
                    "Check that you have calendar events with reminders set",
                    "Ensure calendar app is working properly",
                )

                handleError(errorMessage, e, guidance)
            }
        }
    }

    /**
     * Handle errors with user guidance
     */
    private fun handleError(message: String, error: Throwable, guidance: List<String>) {
        Log.e("MainViewModel", message, error)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            statusMessage = message,
            hasError = true,
        )
        _userGuidance.value = guidance
    }

    /**
     * Refresh calendar events and scheduled alarms
     */
    fun refreshData() {
        if (!_uiState.value.hasCalendarPermission) {
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Load events and alarms in parallel
                val events = calendarRepository.getUpcomingEventsWithReminders()
                val alarms = calendarRepository.getScheduledAlarms()

                _events.value = events
                _scheduledAlarms.value = alarms

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Found ${events.size} events with ${alarms.size} scheduled reminders",
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error refreshing data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Error loading data: ${e.message}",
                    hasError = true,
                )
            }
        }
    }

    /**
     * Create a test reminder for debugging
     */
    fun createTestReminder() {
        viewModelScope.launch {
            try {
                val testEvent = CalendarEvent(
                    id = -1,
                    title = "Test Reminder",
                    startTime = System.currentTimeMillis() + (2 * 60 * 1000L), // 2 minutes from now
                    reminderMinutes = listOf(1), // 1 minute before
                )

                calendarRepository.scheduleReminder(testEvent, 1)

                _uiState.value = _uiState.value.copy(
                    statusMessage = "Test reminder scheduled for 1 minute from now",
                )

                // Refresh to show the new alarm
                refreshData()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error creating test reminder", e)
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Error creating test reminder: ${e.message}",
                    hasError = true,
                )
            }
        }
    }

    /**
     * Initialize the app
     */
    fun initialize() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = "Initializing app...",
                )

                refreshEvents()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    hasError = true,
                    errorMessage = "Failed to initialize: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Refresh events from calendar
     */
    fun refreshEvents() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = "Loading calendar events...",
                )

                val events = calendarRepository.getUpcomingEventsWithReminders()
                _uiState.value = _uiState.value.copy(
                    upcomingEvents = events,
                    statusMessage = "Found ${events.size} upcoming events",
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    hasError = true,
                    errorMessage = "Failed to load events: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Schedule all reminders
     */
    fun scheduleAllReminders() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = "Scheduling reminders...",
                )

                calendarRepository.scheduleAllReminders()

                _uiState.value = _uiState.value.copy(
                    statusMessage = "All reminders scheduled successfully",
                    isLoading = false,
                    userGuidance = "Reminders have been set for all upcoming events",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    hasError = true,
                    errorMessage = "Failed to schedule reminders: ${e.message}",
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(hasError = false, errorMessage = null)
    }

    /**
     * Clear user guidance
     */
    fun clearGuidance() {
        _uiState.value = _uiState.value.copy(userGuidance = null)
    }

    /**
     * Invalidate repository cache
     */
    fun invalidateCache() {
        calendarRepository.invalidateCache()
        refreshData()
    }
}

/**
 * UI state data class for MainActivity
 */
data class MainUiState(
    val hasCalendarPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String = "Checking permissions...",
    val hasError: Boolean = false,
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val errorMessage: String? = null,
    val userGuidance: String? = null,
)

/**
 * Factory for MainViewModel to handle dependency injection
 */
class MainViewModelFactory(
    private val calendarRepository: CalendarRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(calendarRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
