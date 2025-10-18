# Permission Requirements

This document explains why each permission is required for the Calendar Alarm app to function properly.

## Required Permissions

### 1. Calendar Access (`READ_CALENDAR`)
**Why it's required:**
- Read calendar events from your device
- Get event titles, times, and existing reminder settings
- Monitor upcoming events for scheduling alarms

**Without this permission:**
- The app cannot access your calendar events
- No alarms can be scheduled

---

### 2. Exact Alarms (`SCHEDULE_EXACT_ALARM`)
**Why it's required:**
- Schedule alarms to trigger at precise times
- Ensure reminders fire exactly when needed
- Required for Android 12+ (API 31+)

**Without this permission:**
- Alarms may be delayed or imprecise
- Reminders won't trigger at the exact event time

---

### 3. Display Over Apps (`SYSTEM_ALERT_WINDOW`)
**Why it's required:**
- Show full-screen alarm UI on lock screen
- Display alarms over other apps when device is unlocked
- Enable alarm visibility regardless of device state

**Without this permission:**
- Alarms only show when you open the app manually
- No lock screen alarms
- Alarms won't appear while using other apps
- **The app will not function as intended**

## How Full-Screen Intents Work

On modern Android (12+), full-screen intents require the `SYSTEM_ALERT_WINDOW` permission to display properly in these scenarios:

1. **Lock Screen:** Permission allows the alarm to wake the screen and show over the lock screen
2. **Over Other Apps:** Permission allows the alarm to interrupt what you're doing
3. **Background:** Permission allows the alarm to launch the full-screen UI from the background

## Testing Permissions

The app includes debug tools in Settings to test that permissions are working:

1. **Test Immediate Reminder** - Tests the alarm UI immediately
2. **Test 1 Minute Reminder** - Tests PendingAlarmsManager queue
3. **Test Multiple Events** - Tests multi-event queue
4. **Test Scheduled Alarm (10s)** - Tests the full alarm pipeline:
   - AlarmManager scheduling
   - Exact alarm permission
   - AlarmReceiver triggering
   - Full-screen intent launching
   - Lock screen display (if device is locked)

## Onboarding Flow

The app's onboarding screen guides users through granting all three required permissions:

1. ✅ **Calendar Access** - Tap to grant
2. ✅ **Exact Alarms** - Tap to open settings
3. ✅ **Display Over Apps** - Tap to open settings

All three permissions must be granted before the "Continue" button is enabled.

## Permission State in MainActivity

If you've already completed onboarding but revoked the "Display Over Apps" permission:

- A warning toast will appear when you open the app
- The app will still load but alarms won't show on lock screen
- You must re-grant the permission in Android Settings

## Re-enabling Permissions

If you need to re-grant permissions after revoking them:

1. Open Android Settings
2. Go to Apps → Calendar Alarm
3. Tap "Permissions"
4. Re-enable the required permissions

Or simply:
1. Clear app data
2. Re-open the app
3. Go through onboarding again
