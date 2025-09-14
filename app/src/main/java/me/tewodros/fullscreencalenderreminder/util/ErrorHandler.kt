package me.tewodros.fullscreencalenderreminder.util

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * Centralized error handling utility for consistent error management
 * Provides logging, user feedback, and fallback strategies
 */
object ErrorHandler {

    private const val TAG = "ErrorHandler"

    /**
     * Handle calendar permission errors
     */
    fun handleCalendarPermissionError(context: Context, showToast: Boolean = true) {
        val message = "Calendar permission is required to read your events"
        Log.w(TAG, "Calendar permission error")

        if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Handle calendar data access errors
     */
    fun handleCalendarDataError(context: Context, error: Throwable, showToast: Boolean = true): String {
        val message = when {
            error is SecurityException -> "Permission denied accessing calendar data"
            error.message?.contains("database", ignoreCase = true) == true -> "Calendar database unavailable"
            error.message?.contains("cursor", ignoreCase = true) == true -> "Error reading calendar data"
            else -> "Unable to access calendar: ${error.message ?: "Unknown error"}"
        }

        Log.e(TAG, "Calendar data error", error)

        if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }

        return message
    }

    /**
     * Handle alarm scheduling errors
     */
    fun handleAlarmSchedulingError(
        context: Context,
        eventTitle: String,
        error: Throwable,
        showToast: Boolean = true,
    ): String {
        val message = when {
            error.message?.contains("permission", ignoreCase = true) == true -> "Permission required to schedule alarms"
            error.message?.contains("exact", ignoreCase = true) == true -> "Exact alarm permission required for reliable reminders"
            error.message?.contains("limit", ignoreCase = true) == true -> "Too many alarms scheduled. Some reminders may not work."
            else -> "Failed to schedule reminder for '$eventTitle': ${error.message ?: "Unknown error"}"
        }

        Log.e(TAG, "Alarm scheduling error for '$eventTitle'", error)

        if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        return message
    }

    /**
     * Handle WorkManager errors
     */
    fun handleWorkManagerError(context: Context, error: Throwable, showToast: Boolean = false): String {
        val message = when {
            error.message?.contains("constraint", ignoreCase = true) == true -> "Background task constraints not met"
            error.message?.contains("battery", ignoreCase = true) == true -> "Battery optimization may prevent background updates"
            else -> "Background service error: ${error.message ?: "Unknown error"}"
        }

        Log.e(TAG, "WorkManager error", error)

        if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        return message
    }

    /**
     * Handle network or system service errors
     */
    fun handleSystemServiceError(
        context: Context,
        serviceName: String,
        error: Throwable,
        showToast: Boolean = true,
    ): String {
        val message = "System service '$serviceName' unavailable: ${error.message ?: "Unknown error"}"

        Log.e(TAG, "System service error: $serviceName", error)

        if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        return message
    }

    /**
     * Handle general application errors with fallback
     */
    fun handleGeneralError(
        context: Context,
        operation: String,
        error: Throwable,
        showToast: Boolean = true,
    ): String {
        val message = "Error during $operation: ${error.message ?: "Unknown error"}"

        Log.e(TAG, "General error during $operation", error)

        if (showToast) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        return message
    }

    /**
     * Create user-friendly error messages
     */
    fun getUserFriendlyMessage(error: Throwable): String {
        return when {
            error is SecurityException -> "Permission required for this action"
            error.message?.contains("network", ignoreCase = true) == true -> "Network connection required"
            error.message?.contains("storage", ignoreCase = true) == true -> "Storage access required"
            error.message?.contains("permission", ignoreCase = true) == true -> "Additional permissions required"
            error.message?.contains("timeout", ignoreCase = true) == true -> "Operation timed out, please try again"
            else -> "Something went wrong, please try again"
        }
    }

    /**
     * Log debug information for troubleshooting
     */
    fun logDebugInfo(tag: String, message: String, additionalData: Map<String, Any> = emptyMap()) {
        val debugMessage = buildString {
            append(message)
            if (additionalData.isNotEmpty()) {
                append(" - Data: ")
                additionalData.forEach { (key, value) ->
                    append("$key=$value ")
                }
            }
        }
        Log.d(tag, debugMessage)
    }
}
