# 🎯 Wear OS Quick Reference

## 🚀 Quick Commands

### Build Apps
```powershell
# Build phone app
./gradlew :app:assembleDebug

# Build watch app
./gradlew :wear:assembleDebug

# Build both (automated)
.\build-and-install-wear.ps1
```

### Install Apps
```powershell
# Install on phone
./gradlew :app:installDebug

# Install on watch
./gradlew :wear:installDebug
```

### Connect Watch
```powershell
# Via Wi-Fi (get IP from watch Developer Options)
adb connect 192.168.1.100:5555

# Via Bluetooth (phone must be connected via USB)
adb forward tcp:4444 localabstract:/adb-hub
adb connect 127.0.0.1:4444

# Verify connection
adb devices
```

### View Logs
```powershell
# Phone logs
adb -s <phone_serial> logcat | grep -E "WearCommunication|CalendarManager"

# Watch logs
adb -s <watch_serial> logcat | grep -E "Wear|Reminder"

# Both devices
adb logcat | grep -E "Wear|Reminder|Calendar"
```

## 📁 Key Files

### Phone App Integration
- `app/src/main/.../wear/WearCommunicationManager.kt` - Syncs to watch
- `app/src/main/.../wear/PhoneWearListenerService.kt` - Receives from watch
- `app/src/main/.../CalendarManager.kt` - Integrated sync calls
- `app/src/main/.../ReminderActivity.kt` - Dismissal sync

### Watch App
- `wear/src/main/.../WearReminderActivity.kt` - Full-screen UI
- `wear/src/main/.../WearAlarmManager.kt` - Alarm scheduling
- `wear/src/main/.../WearDataLayerListenerService.kt` - Data sync

## 🔧 Configuration

### Watch Fade-In Duration
**File:** `wear/.../WearReminderActivity.kt`
```kotlin
private val fadeInDurationMs = 20000L // 20 seconds
```

### Watch Vibration Pattern
**File:** `wear/.../WearReminderActivity.kt`
```kotlin
val pattern = longArrayOf(0, 500, 1000) // vibrate 500ms, pause 1s
```

### Auto-Dismiss Time
**File:** `wear/.../WearReminderActivity.kt`
```kotlin
private val autoDismissDelayMs = 120000L // 2 minutes
```

## 🐛 Common Issues

| Problem | Solution |
|---------|----------|
| Watch not found by ADB | Enable "ADB debugging" and "Debug over Wi-Fi" in watch Developer Options |
| No reminders on watch | Check phone-watch pairing in Wear OS app, verify Play Services |
| Full-screen not showing | Grant "Draw over apps" permission (SYSTEM_ALERT_WINDOW) |
| Alarms not waking watch | Exempt app from battery optimization, disable Do Not Disturb |
| Watch disconnects | Use USB connection for stable ADB, or ensure Wi-Fi is stable |

## ✅ Testing Checklist

- [ ] Build both apps successfully
- [ ] Install on both phone and watch
- [ ] Pair devices via Wear OS app
- [ ] Grant all permissions on both devices
- [ ] Schedule reminder 2 mins in future
- [ ] Verify alarm shows on phone
- [ ] Verify alarm shows on watch
- [ ] Test dismiss on phone → watch dismisses
- [ ] Test dismiss on watch → phone dismisses
- [ ] Test snooze on watch
- [ ] Test with phone in airplane mode
- [ ] Test after watch reboot

## 🎨 UI Customization

### Watch Colors
**File:** `wear/src/main/res/values/colors.xml`
```xml
<color name="red">#FFF44336</color>      <!-- Dismiss button -->
<color name="green">#FF4CAF50</color>    <!-- Snooze button -->
<color name="black">#FF000000</color>    <!-- Background -->
```

### Watch Button Text
**File:** `wear/src/main/res/values/strings.xml`
```xml
<string name="dismiss">Dismiss</string>
<string name="snooze">Snooze</string>
```

## 📊 Monitoring

### Check Wear Connection
```kotlin
// In phone app
wearCommunicationManager.isWearDeviceConnected()
```

### Get Connected Devices
```kotlin
// In phone app
val devices = wearCommunicationManager.getConnectedDevices()
```

### Verify Alarm Scheduled
```powershell
# Check scheduled alarms on watch
adb -s <watch_serial> shell dumpsys alarm | grep -A 5 "vibecalendaralarm"
```

## 🔄 Sync Flow Summary

```
Schedule → Phone Alarm + Watch Sync → Watch Alarm
Dismiss Phone → Notify Watch → Cancel Watch Alarm
Dismiss Watch → Notify Phone → Cancel Phone Alarm
```

## 🆘 Emergency Commands

```powershell
# Uninstall apps
adb -s <device> uninstall me.tewodros.vibecalendaralarm

# Clear app data
adb -s <device> shell pm clear me.tewodros.vibecalendaralarm

# Force stop app
adb -s <device> shell am force-stop me.tewodros.vibecalendaralarm

# Restart watch
adb -s <watch_serial> reboot

# Reset ADB
adb kill-server
adb start-server
```

## 📚 Documentation

- **Setup Guide:** `WEAR_OS_SETUP.md`
- **Integration Summary:** `WEAR_OS_INTEGRATION_SUMMARY.md`
- **Architecture:** `ARCHITECTURE.md`
- **Build Guide:** `BUILD.md`

## 💡 Pro Tips

1. **Use Wi-Fi ADB** for wireless development with watch
2. **Keep watch charged** during development (debugging uses battery)
3. **Test with real reminders** 2-5 minutes in future
4. **Check logs frequently** to catch sync issues early
5. **Pair watch with phone app** before testing sync

## 🎯 Success Indicators

✅ Both apps install without errors  
✅ Watch appears in `adb devices`  
✅ Logs show "Synced reminder to Wear"  
✅ Logs show "Scheduled reminder on watch"  
✅ Both devices show alerts simultaneously  
✅ Dismissal on one device dismisses on other  

---

**Quick Start:** `.\build-and-install-wear.ps1` → Grant permissions → Schedule test reminder! 🚀
