# Copilot Instructions for Full Screen Calendar Reminder

## Architecture Overview

This is an **Android Kotlin** app that displays full-screen reminders for calendar events. It uses:
- **MVVM pattern** with ViewModels + StateFlow for reactive UI
- **Hilt** for dependency injection (annotate Activities with `@AndroidEntryPoint`)
- **WorkManager** for reliable 1-minute background monitoring (bypasses 15-min limit via self-scheduling)
- **CalendarContract.Instances API** for recurring event expansion

### Core Data Flow
```
CalendarManager → CalendarRepository → ViewModel → Activity (via StateFlow)
     ↓
AlarmManager → AlarmReceiver → ReminderActivity (full-screen)
```

## Key Patterns

### Package Structure
All source code is under `me.tewodros.vibecalendaralarm` (note: folder is `fullscreencalenderreminder` but package is `vibecalendaralarm`).

```
app/src/main/java/me/tewodros/fullscreencalenderreminder/
├── CalendarManager.kt       # Core alarm scheduling & calendar queries (1255 lines)
├── repository/              # CalendarRepository interface + impl (testable)
├── viewmodel/               # MainViewModel, ReminderViewModel with StateFlow
├── di/AppModule.kt          # Hilt @Module with @Provides
├── model/CalendarEvent.kt   # Data class: id, title, startTime, reminderMinutes
```

### Repository Pattern
Always inject `CalendarRepository` (not `CalendarManager` directly) into ViewModels/Activities for testability:
```kotlin
@Inject lateinit var calendarRepository: CalendarRepository
```

### Alarm Scheduling
- Uses `setExactAndAllowWhileIdle()` for Doze mode compatibility
- 30-day lookahead window for event retrieval
- Caching: 30-second event cache, 5-minute calendar list cache
- Call `calendarManager.invalidateCache()` before scheduling operations

### Full-Screen Reminder
`ReminderActivity` uses gradual 30-second audio fade-in (1%→100%). Key constants in `CalendarManager.companion`:
- `AUDIO_FADE_DURATION_SECONDS = 30`
- `AUDIO_FADE_START_VOLUME = 0.01f`

## Build & Development

```bash
# Build debug APK
./gradlew assembleDebug

# Run all checks (lint + ktlint + detekt + tests)
./gradlew check

# Unit tests only
./gradlew testDebugUnitTest

# Format code
./gradlew ktlintFormat

# Install on device
./gradlew installDebug
```

### Versioning
Version code/name auto-derive from git tags (`v*`). Override via:
- Gradle property: `-PversionCode=X -PversionName=Y`
- Environment: `VERSION_CODE`, `VERSION_NAME`

### Code Quality
- **ktlint**: Kotlin style enforcement (auto-fix with `ktlintFormat`)
- **detekt**: Static analysis config at [config/detekt/detekt.yml](../config/detekt/detekt.yml)
- Max complexity threshold: 15, max method length: 60 lines, max class: 600 lines

## Permissions Model

Declare in manifest, request at runtime in `OnboardingActivity`:
- `READ_CALENDAR` – Calendar access
- `SCHEDULE_EXACT_ALARM` – Precise alarm timing  
- `SYSTEM_ALERT_WINDOW` – Lock screen overlay
- `RECEIVE_BOOT_COMPLETED` – Boot persistence

Check permissions with:
```kotlin
ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
Settings.canDrawOverlays(context)  // For overlay
```

## Testing Considerations

- Grant permissions via adb for instrumented tests:
  ```bash
  adb shell pm grant me.tewodros.vibecalendaralarm android.permission.READ_CALENDAR
  ```
- Test workers with `TestWorkerBuilder` (see `ReminderWorkerTest`)
- Mock `CalendarRepository` interface for ViewModel tests

## Common Gotchas

1. **Package vs folder mismatch**: Code is in folder `fullscreencalenderreminder` but package is `vibecalendaralarm`
2. **WorkManager initialization**: Disabled default init in manifest; app uses `Configuration.Provider`
3. **Background monitoring**: `ReminderWorker` self-schedules via `ReminderWorkManager.scheduleNextWork()` to achieve 1-minute intervals
4. **UI thread**: Use `lifecycleScope.launch` for coroutines; DB queries must use `Dispatchers.IO`
