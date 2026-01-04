package me.tewodros.vibecalendaralarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.tewodros.vibecalendaralarm.data.Constants
import me.tewodros.vibecalendaralarm.data.ScreenshotEventData
import me.tewodros.vibecalendaralarm.databinding.ActivityMainBinding
import me.tewodros.vibecalendaralarm.ui.CalendarEventAdapter
import me.tewodros.vibecalendaralarm.viewmodel.MainUiStateV2
import me.tewodros.vibecalendaralarm.viewmodel.MainViewModelV2

/**
 * MainActivityRefactored - Refactored version of MainActivity using proper MVVM
 *
 * Uses Hilt for dependency injection and proper MVVM pattern with ViewModel.
 * All business logic is handled by MainViewModelV2, this Activity only handles
 * UI rendering and user interactions.
 *
 * TODO: Once migration is complete, rename this to MainActivity and delete the old one
 */
@AndroidEntryPoint
class MainActivityRefactored : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivityRefactored"
        private const val REQUEST_CALENDAR_PERMISSION = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var eventAdapter: CalendarEventAdapter

    private val viewModel: MainViewModelV2 by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupToolbar()
        setupSwipeRefresh()
        observeViewModel()

        // Initialize with current permission state
        viewModel.initialize(
            hasCalendarPermission = hasCalendarPermission(),
            hasOverlayPermission = hasOverlayPermission(),
        )

        // Start background monitoring if permissions granted
        if (hasCalendarPermission()) {
            ReminderWorkManager.startPeriodicMonitoring(this)
        }
    }

    private fun setupRecyclerView() {
        eventAdapter = CalendarEventAdapter()
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivityRefactored)
            adapter = eventAdapter
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    handleRefresh()
                    true
                }
                R.id.action_calendar -> {
                    viewModel.openCalendarApp()
                    true
                }
                R.id.action_permissions -> {
                    viewModel.navigateToOnboarding()
                    true
                }
                R.id.action_settings -> {
                    viewModel.navigateToSettings()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            handleRefresh()
        }
        // Set swipe refresh colors to match theme
        binding.swipeRefreshLayout.setColorSchemeResources(
            com.google.android.material.R.color.design_default_color_primary
        )
    }

    /**
     * Handle refresh action - either schedule alarms or show screenshot data
     */
    private fun handleRefresh() {
        if (SettingsActivity.isScreenshotModeEnabled(this)) {
            displayFakeEvents()
            binding.swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Screenshot mode active", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.scheduleAllReminders()
        }
    }

    /**
     * Observe ViewModel state flows
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe UI state
                launch {
                    viewModel.uiState.collect { state ->
                        updateUiFromState(state)
                    }
                }

                // Observe events list
                launch {
                    viewModel.events.collect { events ->
                        Log.d("MainActivityV2", "Received ${events.size} events from ViewModel")
                        val eventsToDisplay = events.take(Constants.MAX_EVENTS_TO_DISPLAY)

                        // Force adapter update by submitting a new list copy
                        eventAdapter.submitList(eventsToDisplay.toList()) {
                            // Callback when diff is computed and applied
                            Log.d("MainActivityV2", "Adapter submitList completed for ${eventsToDisplay.size} events")
                        }

                        // Show "view more" if needed
                        if (events.size > Constants.MAX_EVENTS_TO_DISPLAY) {
                            binding.viewMoreText.visibility = View.VISIBLE
                            binding.viewMoreText.text =
                                "Showing ${Constants.MAX_EVENTS_TO_DISPLAY} of ${events.size} events • View all in your calendar app"
                        } else {
                            binding.viewMoreText.visibility = View.GONE
                        }
                    }
                }

                // Observe one-time UI events
                launch {
                    viewModel.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUiFromState(state: MainUiStateV2) {
        // Update status text
        binding.statusText.text = state.statusMessage

        // Stop refresh indicator when loading completes
        if (!state.isLoading) {
            binding.swipeRefreshLayout.isRefreshing = false
        }

        // Update events count
        binding.eventsCountText.text = when (state.eventCount) {
            0 -> "No upcoming events with reminders found"
            1 -> "1 event with reminders found"
            else -> "${state.eventCount} events with reminders found"
        }

        // Update loading state (could show/hide progress indicator)
        // binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        // Handle error state
        if (state.hasError && state.errorMessage != null) {
            Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    private fun handleUiEvent(event: MainViewModelV2.UiEvent) {
        when (event) {
            is MainViewModelV2.UiEvent.ShowToast -> {
                val duration = if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(this, event.message, duration).show()
            }
            is MainViewModelV2.UiEvent.ShowError -> {
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
            MainViewModelV2.UiEvent.NavigateToOnboarding -> {
                val intent = Intent(this, OnboardingActivity::class.java)
                intent.putExtra("initial_launch", false)
                startActivity(intent)
            }
            MainViewModelV2.UiEvent.NavigateToSettings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            MainViewModelV2.UiEvent.OpenCalendarApp -> {
                openCalendarApp()
            }
        }
    }

    // region Permission Methods

    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestCalendarPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CALENDAR),
            REQUEST_CALENDAR_PERMISSION,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CALENDAR_PERMISSION -> {
                val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                viewModel.updatePermissions(
                    hasCalendarPermission = granted,
                    hasOverlayPermission = hasOverlayPermission(),
                )

                if (granted) {
                    ReminderWorkManager.startPeriodicMonitoring(this)
                } else {
                    Toast.makeText(
                        this,
                        "Calendar permission is required. Grant it in Settings.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    // endregion

    override fun onResume() {
        super.onResume()

        // Update permission state when returning from settings
        viewModel.updatePermissions(
            hasCalendarPermission = hasCalendarPermission(),
            hasOverlayPermission = hasOverlayPermission(),
        )

        // Warn about overlay permission if missing
        if (!hasOverlayPermission()) {
            Log.w(TAG, "Overlay permission not granted - alarms may not show on lock screen")
        }
    }

    // region Helper Methods

    private fun openCalendarApp() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("content://com.android.calendar/time")
                }
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "No calendar app found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayFakeEvents() {
        Log.d(TAG, "📸 Displaying fake events for screenshots")

        val fakeEvents = ScreenshotEventData.getFakeEvents()
        val eventsToDisplay = fakeEvents.take(Constants.MAX_EVENTS_TO_DISPLAY)

        eventAdapter.submitList(eventsToDisplay)
        binding.statusText.text = "📸 Screenshot Mode: Showing ${fakeEvents.size} demo events"
        binding.eventsCountText.text = "${fakeEvents.size} demo events"

        if (fakeEvents.size > Constants.MAX_EVENTS_TO_DISPLAY) {
            binding.viewMoreText.visibility = View.VISIBLE
            binding.viewMoreText.text =
                "Showing ${Constants.MAX_EVENTS_TO_DISPLAY} of ${fakeEvents.size} events"
        } else {
            binding.viewMoreText.visibility = View.GONE
        }
    }

    // endregion
}
