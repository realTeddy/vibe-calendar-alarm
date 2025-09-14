package me.tewodros.vibecalendaralarm.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import me.tewodros.vibecalendaralarm.repository.CalendarRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

/**
 * Basic unit tests for MainViewModel
 * Tests core functionality with mocked dependencies
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var mockRepository: CalendarRepository

    private lateinit var viewModel: MainViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial UI state is correct`() = runTest {
        // When
        val uiState = viewModel.uiState.first()

        // Then
        assertFalse("Should not have calendar permission initially", uiState.hasCalendarPermission)
        assertFalse("Should not have overlay permission initially", uiState.hasOverlayPermission)
        assertFalse("Should not be loading initially", uiState.isLoading)
        assertEquals(
            "Initial status should be checking permissions",
            "Checking permissions...",
            uiState.statusMessage,
        )
        assertFalse("Should not have error initially", uiState.hasError)
    }

    @Test
    fun `events list is empty initially`() = runTest {
        // When
        val events = viewModel.events.first()

        // Then - should be empty initially
        assertTrue("Events should be empty initially", events.isEmpty())
    }

    @Test
    fun `scheduled alarms list is empty initially`() = runTest {
        // When
        val alarms = viewModel.scheduledAlarms.first()

        // Then - should be empty initially
        assertTrue("Scheduled alarms should be empty initially", alarms.isEmpty())
    }
}
