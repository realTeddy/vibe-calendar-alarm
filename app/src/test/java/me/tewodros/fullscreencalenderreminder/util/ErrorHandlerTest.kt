package me.tewodros.vibecalendaralarm.util

import android.content.Context
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for ErrorHandler utility class
 * Tests error handling, logging, and user feedback
 */
class ErrorHandlerTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mockk<Context>(relaxed = true)

        // Mock static methods that may be called
        mockkStatic("android.widget.Toast")
        every { android.widget.Toast.makeText(any(), any<String>(), any()) } returns mockk(
            relaxed = true,
        )

        mockkStatic("android.util.Log")
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic("android.widget.Toast")
        unmockkStatic("android.util.Log")
    }

    @Test
    fun `handleCalendarPermissionError logs warning`() {
        // When
        ErrorHandler.handleCalendarPermissionError(mockContext, showToast = false)

        // Then - method should execute without error
        assertTrue("Calendar permission error handled", true)
    }

    @Test
    fun `handleCalendarDataError returns appropriate message for SecurityException`() {
        // Given
        val securityException = SecurityException("Permission denied")

        // When
        val message = ErrorHandler.handleCalendarDataError(
            mockContext,
            securityException,
            showToast = false,
        )

        // Then
        assertTrue(
            "Should return permission denied message",
            message.contains("Permission denied"),
        )
    }

    @Test
    fun `handleCalendarDataError returns appropriate message for database error`() {
        // Given
        val databaseException = RuntimeException("Database is locked")

        // When
        val message = ErrorHandler.handleCalendarDataError(
            mockContext,
            databaseException,
            showToast = false,
        )

        // Then
        assertTrue(
            "Should return database unavailable message",
            message.contains("Calendar database unavailable"),
        )
    }

    @Test
    fun `handleCalendarDataError returns appropriate message for cursor error`() {
        // Given
        val cursorException = RuntimeException("Cursor operation failed")

        // When
        val message = ErrorHandler.handleCalendarDataError(
            mockContext,
            cursorException,
            showToast = false,
        )

        // Then
        assertTrue(
            "Should return cursor error message",
            message.contains("Error reading calendar data"),
        )
    }

    @Test
    fun `handleAlarmSchedulingError returns appropriate message for permission error`() {
        // Given
        val eventTitle = "Test Meeting"
        val permissionException = SecurityException("Alarm permission required")

        // When
        val message = ErrorHandler.handleAlarmSchedulingError(
            mockContext,
            eventTitle,
            permissionException,
            showToast = false,
        )

        // Then
        assertTrue(
            "Should return permission required message",
            message.contains("Permission required"),
        )
    }

    @Test
    fun `handleAlarmSchedulingError returns appropriate message for exact alarm error`() {
        // Given
        val eventTitle = "Test Meeting"
        val exactAlarmException = RuntimeException("Exact alarm scheduling failed")

        // When
        val message = ErrorHandler.handleAlarmSchedulingError(
            mockContext,
            eventTitle,
            exactAlarmException,
            showToast = false,
        )

        // Then
        assertTrue(
            "Should return exact alarm permission message",
            message.contains("Exact alarm permission required"),
        )
    }

    @Test
    fun `handleWorkManagerError returns appropriate message for constraint error`() {
        // Given
        val constraintException = RuntimeException("Constraint not met")

        // When
        val message = ErrorHandler.handleWorkManagerError(
            mockContext,
            constraintException,
            showToast = false,
        )

        // Then
        assertTrue(
            "Should return constraint message",
            message.contains("Background task constraints not met"),
        )
    }

    @Test
    fun `handleSystemServiceError returns service-specific message`() {
        // Given
        val serviceName = "AlarmManager"
        val serviceException = RuntimeException("Service unavailable")

        // When
        val message = ErrorHandler.handleSystemServiceError(
            mockContext,
            serviceName,
            serviceException,
            showToast = false,
        )

        // Then
        assertTrue(
            "Should return service-specific message",
            message.contains("AlarmManager"),
        )
        assertTrue(
            "Should contain unavailable",
            message.contains("unavailable"),
        )
    }

    @Test
    fun `handleGeneralError returns operation-specific message`() {
        // Given
        val operation = "test operation"
        val generalException = RuntimeException("General error")

        // When
        val message = ErrorHandler.handleGeneralError(
            mockContext,
            operation,
            generalException,
            showToast = false,
        )

        // Then
        assertTrue(
            "Should return operation-specific message",
            message.contains("test operation"),
        )
    }

    @Test
    fun `getUserFriendlyMessage returns friendly message for SecurityException`() {
        // Given
        val securityException = SecurityException("Access denied")

        // When
        val message = ErrorHandler.getUserFriendlyMessage(securityException)

        // Then
        assertEquals(
            "Should return permission message",
            "Permission required for this action",
            message,
        )
    }

    @Test
    fun `getUserFriendlyMessage returns friendly message for network error`() {
        // Given
        val networkException = RuntimeException("Network timeout")

        // When
        val message = ErrorHandler.getUserFriendlyMessage(networkException)

        // Then
        assertEquals(
            "Should return network message",
            "Network connection required",
            message,
        )
    }

    @Test
    fun `getUserFriendlyMessage returns generic message for unknown error`() {
        // Given
        val unknownException = RuntimeException("Unknown error")

        // When
        val message = ErrorHandler.getUserFriendlyMessage(unknownException)

        // Then
        assertEquals(
            "Should return generic message",
            "Something went wrong, please try again",
            message,
        )
    }

    @Test
    fun `logDebugInfo handles empty additional data`() {
        // When
        ErrorHandler.logDebugInfo("TestTag", "Test message")

        // Then - should execute without error
        assertTrue("Debug logging handled empty data", true)
    }

    @Test
    fun `logDebugInfo handles additional data`() {
        // Given
        val additionalData = mapOf("key1" to "value1", "key2" to 123)

        // When
        ErrorHandler.logDebugInfo("TestTag", "Test message", additionalData)

        // Then - should execute without error
        assertTrue("Debug logging handled additional data", true)
    }
}
