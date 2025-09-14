package me.tewodros.vibecalendaralarm.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.tewodros.vibecalendaralarm.repository.CalendarRepository

/**
 * ViewModel for ReminderActivity that manages reminder display logic
 * Handles snooze functionality and reminder state management
 */
class ReminderViewModel(
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    /**
     * Initialize the reminder with event details
     */
    fun initializeReminder(eventTitle: String, eventId: Long, reminderMinutes: Int) {
        _uiState.value = _uiState.value.copy(
            eventTitle = eventTitle,
            eventId = eventId,
            reminderMinutes = reminderMinutes,
            isInitialized = true,
        )

        Log.d(
            "ReminderViewModel",
            "Initialized reminder for: $eventTitle (${reminderMinutes}min before)",
        )
    }

    /**
     * Dismiss the reminder
     */
    fun dismissReminder() {
        _uiState.value = _uiState.value.copy(
            isDismissed = true,
        )

        Log.d("ReminderViewModel", "Reminder dismissed for: ${_uiState.value.eventTitle}")
    }

    /**
     * Snooze the reminder for a specified duration
     */
    fun snoozeReminder(snoozeMinutes: Int) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                )

                // Create a new reminder for the snooze time
                val currentState = _uiState.value
                val snoozeEvent = me.tewodros.vibecalendaralarm.model.CalendarEvent(
                    id = currentState.eventId,
                    title = "${currentState.eventTitle} (Snoozed)",
                    startTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L),
                    reminderMinutes = listOf(0), // Trigger immediately at the snooze time
                )

                calendarRepository.scheduleReminder(snoozeEvent, 0)

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isSnoozed = true,
                    snoozeMinutes = snoozeMinutes,
                )

                Log.d("ReminderViewModel", "Snoozed reminder for $snoozeMinutes minutes")
            } catch (e: Exception) {
                Log.e("ReminderViewModel", "Error snoozing reminder", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    hasError = true,
                    errorMessage = "Failed to snooze: ${e.message}",
                )
            }
        }
    }

    /**
     * Mark reminder as completed/acknowledged
     */
    fun acknowledgeReminder() {
        _uiState.value = _uiState.value.copy(
            isAcknowledged = true,
        )

        Log.d("ReminderViewModel", "Reminder acknowledged for: ${_uiState.value.eventTitle}")
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            hasError = false,
            errorMessage = null,
        )
    }

    /**
     * Check if the reminder should auto-dismiss after a timeout
     */
    fun checkAutoDismiss(timeoutMillis: Long) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(timeoutMillis)

            val currentState = _uiState.value
            if (!currentState.isDismissed && !currentState.isSnoozed && !currentState.isAcknowledged) {
                _uiState.value = currentState.copy(
                    isAutoDismissed = true,
                )
                Log.d("ReminderViewModel", "Auto-dismissed reminder after timeout")
            }
        }
    }
}

/**
 * UI state data class for ReminderActivity
 */
data class ReminderUiState(
    val eventTitle: String = "",
    val eventId: Long = -1,
    val reminderMinutes: Int = 0,
    val isInitialized: Boolean = false,
    val isProcessing: Boolean = false,
    val isDismissed: Boolean = false,
    val isSnoozed: Boolean = false,
    val isAcknowledged: Boolean = false,
    val isAutoDismissed: Boolean = false,
    val snoozeMinutes: Int = 0,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
) {
    /**
     * Helper property to check if the reminder should be closed
     */
    val shouldClose: Boolean
        get() = isDismissed || isAcknowledged || isAutoDismissed

    /**
     * Helper property to get status text
     */
    val statusText: String
        get() = when {
            isProcessing -> "Processing..."
            isSnoozed -> "Snoozed for $snoozeMinutes minutes"
            isAcknowledged -> "Acknowledged"
            isDismissed -> "Dismissed"
            isAutoDismissed -> "Auto-dismissed"
            hasError -> errorMessage ?: "Error occurred"
            else -> "Reminder: $reminderMinutes minutes before event"
        }
}

/**
 * Factory for ReminderViewModel to handle dependency injection
 */
class ReminderViewModelFactory(
    private val calendarRepository: CalendarRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReminderViewModel::class.java)) {
            return ReminderViewModel(calendarRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
