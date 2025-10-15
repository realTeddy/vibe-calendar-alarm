# 🎯 Wear OS Full-Screen Reminders Setup Guide

## Overview

Your **Vibe Calendar Alarm** app now supports **un-missable full-screen reminders on Wear OS watches**! This guide will help you set up, build, and deploy the Wear OS companion app.

## ✨ Features

### Full-Screen Reminders on Watch
- ⌚ **Immersive full-screen alerts** on your Wear OS watch
- 🔊 **Gradual audio fade-in** (20 seconds, optimized for watch)
- 📳 **Continuous vibration** patterns
- 🔄 **Independent alarm system** - works even if phone disconnects
- 🔗 **Bidirectional sync** - dismiss on phone = dismiss on watch, and vice versa
- ⏰ **Snooze support** from watch
- 🎨 **Watch-optimized UI** - large buttons, clear text

### Synchronization
- 📱 **Real-time sync** of calendar events from phone to watch
- 🔄 **Automatic re-sync** after device boot
- ⚡ **Reliable delivery** using Google's Wearable Data Layer API
- 🔌 **Works offline** - alarms scheduled on watch independently

## 📋 Requirements

### Hardware
- **Wear OS 2.0+ device** (API 26+) or emulator
- **Android phone** with the Vibe Calendar Alarm app installed
- **Paired connection** between phone and watch via Wear OS app

### Software
- Android Studio Arctic Fox or newer
- Wear OS emulator or physical Wear OS device
- Google Play Services on both phone and watch

## 🏗️ Project Structure

```
FullScreenCalenderReminer/
├── app/                           # Phone app
│   └── src/main/java/.../wear/
│       ├── WearCommunicationManager.kt    # Phone-side Wear sync
│       └── PhoneWearListenerService.kt    # Receives messages from watch
│
└── wear/                          # Wear OS app
    ├── build.gradle.kts           # Wear OS module configuration
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        └── java/.../wear/
            ├── WearCalendarReminderApplication.kt
            ├── WearReminderActivity.kt          # Full-screen reminder UI
            ├── WearAlarmManager.kt              # Watch alarm scheduling
            ├── WearAlarmReceiver.kt             # Alarm broadcast receiver
            ├── WearBootReceiver.kt              # Boot receiver
            └── WearDataLayerListenerService.kt  # Receives data from phone
```

## 🚀 Building & Installation

### Step 1: Build the Phone App

```powershell
# Navigate to project root
cd d:\repos\FullScreenCalenderReminer

# Build phone app APK
./gradlew :app:assembleDebug

# Install on phone (phone must be connected)
./gradlew :app:installDebug
```

### Step 2: Build the Wear OS App

```powershell
# Build Wear OS APK
./gradlew :wear:assembleDebug

# Install on watch (watch must be connected or emulator running)
./gradlew :wear:installDebug
```

### Step 3: Pair Phone and Watch

1. **Install Wear OS app** on your phone from Google Play Store
2. **Open Wear OS app** and follow pairing instructions
3. **Enable Developer Options** on watch:
   - Go to Settings > System > About
   - Tap "Build number" 7 times
4. **Enable ADB debugging**:
   - Settings > Developer options > ADB debugging: ON
   - Settings > Developer options > Debug over Wi-Fi: ON (optional)

### Step 4: Connect Watch via ADB

#### For Physical Watch:

**Option A: ADB over Wi-Fi**
```powershell
# On watch, go to Developer Options > Debug over Wi-Fi
# Note the IP address shown (e.g., 192.168.1.100:5555)

# Connect from computer
adb connect 192.168.1.100:5555

# Verify connection
adb devices
```

**Option B: ADB over Bluetooth**
```powershell
# Ensure phone is connected via USB
adb devices  # Verify phone is connected

# Enable port forwarding
adb forward tcp:4444 localabstract:/adb-hub
adb connect 127.0.0.1:4444

# Verify watch is connected
adb devices  # Should show both phone and watch
```

#### For Wear OS Emulator:
```powershell
# Emulator connects automatically
# Verify with:
adb devices
```

### Step 5: Install Both Apps

```powershell
# Install phone app
adb -s <phone_serial> install app/build/outputs/apk/debug/app-debug.apk

# Install watch app
adb -s <watch_serial> install wear/build/outputs/apk/debug/wear-debug.apk
```

## ⚙️ Configuration

### Phone App Setup

1. **Open Vibe Calendar Alarm** on phone
2. **Grant all permissions**:
   - Calendar access
   - Notifications
   - Exact alarms
   - System alert window
   - Battery optimization exemption
3. **Schedule reminders** as usual

### Watch App Setup

1. **Open Vibe Calendar Alarm** on watch
2. **Grant permissions**:
   - Exact alarms (if prompted)
   - Battery optimization exemption
3. **Wait for sync** - calendar events will sync automatically from phone

## 🔄 How It Works

### Event Flow

```
1. Phone App schedules calendar reminder
   ↓
2. Phone sends event data to watch via Wearable Data Layer
   ↓
3. Watch receives data and schedules local alarm
   ↓
4. At reminder time:
   - Phone: Shows full-screen ReminderActivity
   - Watch: Shows full-screen WearReminderActivity (simultaneously)
   ↓
5. User dismisses on either device
   ↓
6. Dismissal syncs to other device automatically
```

### Synchronization

- **Initial sync**: When phone app schedules reminders, automatically syncs to watch
- **Real-time updates**: New calendar events sync immediately
- **Dismissal sync**: Dismissing on phone dismisses on watch (and vice versa)
- **Boot recovery**: Watch re-syncs all reminders after device restart

## 🐛 Troubleshooting

### Watch Not Receiving Reminders

1. **Check phone-watch connection**:
   ```powershell
   # On phone, run:
   adb shell am broadcast -a com.google.android.gms.wearable.ACTION_CHECK_DEVICE_CONNECTION
   ```

2. **Verify Google Play Services**:
   - Both phone and watch must have Play Services installed
   - Check in Settings > Apps > Google Play Services

3. **Check watch battery optimization**:
   - Settings > Apps > Vibe Calendar Alarm > Battery > Unrestricted

4. **Check exact alarm permission** (Android 12+):
   - Settings > Apps > Vibe Calendar Alarm > Alarms & reminders > Allow

### Watch Shows "Waiting for phone connection"

- **Ensure watch is paired** with phone in Wear OS app
- **Check Bluetooth connection** between devices
- **Restart both devices** and wait for pairing to complete

### Full-Screen Not Showing on Watch

1. **Check system alert window permission**:
   - Some Wear OS versions restrict this
   - Alternative: Reminders will show as notifications

2. **Disable battery saver** on watch:
   - Settings > Battery > Battery Saver: OFF

### Alarms Not Waking Watch

- **Check Do Not Disturb** settings on watch
- **Ensure alarm volume** is not muted:
  - Settings > Sound > Alarm volume

## 📱 Testing

### Manual Testing Checklist

- [ ] Schedule a reminder 2 minutes in the future on phone
- [ ] Wait for sync (check logs: `adb logcat | grep "Wear"`)
- [ ] Verify alarm shows on both phone and watch simultaneously
- [ ] Test dismissing on phone - verify watch dismisses
- [ ] Test dismissing on watch - verify phone dismisses
- [ ] Test snooze on watch
- [ ] Test with phone disconnected (airplane mode)
- [ ] Test after watch reboot

### View Logs

**Phone logs:**
```powershell
adb -s <phone_serial> logcat | grep -E "WearCommunication|CalendarManager"
```

**Watch logs:**
```powershell
adb -s <watch_serial> logcat | grep -E "Wear|Reminder"
```

## 🎨 Customization

### Adjust Watch Fade-In Duration

Edit `wear/src/main/java/.../WearReminderActivity.kt`:

```kotlin
private val fadeInDurationMs = 20000L // Change to desired milliseconds
```

### Adjust Vibration Pattern

Edit `wear/src/main/java/.../WearReminderActivity.kt`:

```kotlin
val pattern = longArrayOf(0, 500, 1000) // [delay, vibrate, pause]
```

### Change Watch UI Colors

Edit `wear/src/main/res/values/colors.xml`:

```xml
<color name="red">#FFF44336</color>        <!-- Dismiss button -->
<color name="green">#FF4CAF50</color>      <!-- Snooze button -->
```

## 🔐 Privacy & Security

- **All data stays local**: Event data only syncs between YOUR paired devices
- **No cloud storage**: Data never leaves your phone-watch connection
- **Encrypted communication**: Uses Google's secure Wearable Data Layer
- **No internet required**: Works completely offline after initial pairing

## 📚 Additional Resources

- [Wear OS Developer Guide](https://developer.android.com/training/wearables)
- [Wearable Data Layer API](https://developers.google.com/android/reference/com/google/android/gms/wearable/DataClient)
- [Testing Wear OS Apps](https://developer.android.com/training/wearables/apps/debugging)

## 💡 Tips & Best Practices

1. **Keep apps updated**: Update both phone and watch apps simultaneously
2. **Battery optimization**: Exempt both apps from battery optimization for best reliability
3. **Regular syncing**: Open phone app occasionally to trigger re-sync
4. **Watch placement**: Keep watch on wrist with screen accessible for quick dismissal
5. **Testing**: Always test with real reminders before relying on them

## 🆘 Support

If you encounter issues:
1. Check the [Troubleshooting](#-troubleshooting) section
2. Review logs from both phone and watch
3. Verify all permissions are granted
4. Try uninstalling and reinstalling both apps
5. Ensure devices are fully charged during testing

## 🎉 Enjoy Your Un-Missable Reminders!

Your calendar reminders will now wake you up on both phone and watch with gradual audio, vibration, and full-screen alerts. Never miss an important event again! ⏰🎯
