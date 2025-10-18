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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import me.tewodros.vibecalendaralarm.databinding.ActivityOnboardingBinding

/**
 * Modern onboarding activity with Material You design
 * Guides users through required permissions step-by-step
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    companion object {
        private const val REQUEST_CALENDAR_PERMISSION = 1001
        private const val REQUEST_NOTIFICATION_PERMISSION = 1002
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        fun isOnboardingCompleted(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }

        fun setOnboardingCompleted(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                .apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding is already completed AND this is the initial app launch (not from Permissions button)
        val isInitialLaunch = intent.getBooleanExtra("initial_launch", true)
        if (isOnboardingCompleted(this) && isInitialLaunch) {
            // Skip to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show back button if not initial launch
        if (!isInitialLaunch) {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            binding.toolbar.setNavigationOnClickListener {
                finish()
            }
        }

        setupUI()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupUI() {
        // Calendar permission card
        binding.calendarPermissionCard.setOnClickListener {
            if (!hasCalendarPermission()) {
                requestCalendarPermission()
            }
        }

        // Alarm permission card
        binding.alarmPermissionCard.setOnClickListener {
            if (!hasAlarmPermission()) {
                requestAlarmPermission()
            }
        }

        // Overlay permission card
        binding.overlayPermissionCard.setOnClickListener {
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
            }
        }

        // Continue button
        binding.continueButton.setOnClickListener {
            completeOnboarding()
        }

        // Skip button (only shown if at least calendar permission is granted)
        binding.skipButton.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun updatePermissionStatus() {
        val calendarGranted = hasCalendarPermission()
        val alarmGranted = hasAlarmPermission()
        val overlayGranted = hasOverlayPermission()

        // Update calendar permission status
        if (calendarGranted) {
            binding.calendarStatus.visibility = View.VISIBLE
            binding.calendarIcon.setColorFilter(
                ContextCompat.getColor(this, com.google.android.material.R.color.material_dynamic_primary50),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            binding.calendarPermissionCard.strokeWidth = 0
        } else {
            binding.calendarStatus.visibility = View.GONE
            binding.calendarIcon.setColorFilter(
                ContextCompat.getColor(this, com.google.android.material.R.color.material_dynamic_neutral50),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            binding.calendarPermissionCard.strokeWidth = 2
            binding.calendarPermissionCard.strokeColor = ContextCompat.getColor(this, com.google.android.material.R.color.material_dynamic_primary50)
        }

        // Update alarm permission status
        if (alarmGranted) {
            binding.alarmStatus.visibility = View.VISIBLE
            binding.alarmIcon.setColorFilter(
                ContextCompat.getColor(this, com.google.android.material.R.color.material_dynamic_primary50),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.alarmStatus.visibility = View.GONE
        }

        // Update overlay permission status
        if (overlayGranted) {
            binding.overlayStatus.visibility = View.VISIBLE
            binding.overlayIcon.setColorFilter(
                ContextCompat.getColor(this, com.google.android.material.R.color.material_dynamic_primary50),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            binding.overlayPermissionCard.strokeWidth = 0
        } else {
            binding.overlayStatus.visibility = View.GONE
            binding.overlayIcon.setColorFilter(
                ContextCompat.getColor(this, com.google.android.material.R.color.material_dynamic_neutral50),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            binding.overlayPermissionCard.strokeWidth = 2
            binding.overlayPermissionCard.strokeColor = ContextCompat.getColor(this, com.google.android.material.R.color.material_dynamic_primary50)
        }

        // Enable continue button only if ALL required permissions are granted
        val allPermissionsGranted = calendarGranted && alarmGranted && overlayGranted
        binding.continueButton.isEnabled = allPermissionsGranted

        // Hide skip button - all permissions are required
        binding.skipButton.visibility = View.GONE

        Log.d("OnboardingActivity", "Permissions - Calendar: $calendarGranted, Alarm: $alarmGranted, Overlay: $overlayGranted")
    }

    private fun completeOnboarding() {
        setOnboardingCompleted(this)

        // If this was launched from MainActivity (not initial launch), just finish
        val isInitialLaunch = intent.getBooleanExtra("initial_launch", true)
        if (isInitialLaunch) {
            // First time - navigate to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        finish()
    }

    // Permission checking methods
    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Not needed for older versions
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Not needed for older versions
        }
    }

    // Permission request methods
    private fun requestCalendarPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CALENDAR),
            REQUEST_CALENDAR_PERMISSION
        )
    }

    private fun requestAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStatus()
    }
}
