# 🏗️ Architecture Documentation

## Overview

Full Screen Calendar Reminder follows a layered architecture pattern designed for reliability, maintainability, and performance. The app is built using modern Android development practices with Kotlin and follows the MVVM (Model-View-ViewModel) pattern where applicable.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Presentation Layer                        │
├─────────────────────────────────────────────────────────────────┤
│ MainActivity            │ ReminderActivity    │ SettingsActivity │
│ - UI Logic             │ - Full-screen UI    │ - Configuration  │
│ - Permission handling  │ - User interaction  │ - Preferences    │
│ - Event display        │ - Audio/vibration   │ - Settings UI    │
└─────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Business Layer                          │
├─────────────────────────────────────────────────────────────────┤
│ CalendarManager                                                 │
│ - Calendar event retrieval                                      │
│ - Alarm scheduling & management                                 │
│ - Caching and performance optimization                          │
│ - Event lifecycle management                                    │
└─────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Service Layer                           │
├─────────────────────────────────────────────────────────────────┤
│ AlarmReceiver           │ BootReceiver        │ WorkManager     │
│ - Alarm broadcasts     │ - Boot handling     │ - Background    │
│ - ReminderActivity     │ - Service restart   │   monitoring    │
│   launching            │ - Auto-start        │ - Event sync    │
└─────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                          Data Layer                             │
├─────────────────────────────────────────────────────────────────┤
│ Android Calendar Provider │ AlarmManager      │ SharedPreferences│
│ - CalendarContract.Events │ - System alarms   │ - App settings  │
│ - CalendarContract.       │ - Exact timing    │ - User prefs    │
│   Instances               │ - Doze mode       │ - Configuration │
│ - Recurring events        │   compatibility   │                 │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 📱 Presentation Layer

#### MainActivity
**Purpose**: Primary user interface and application entry point

**Responsibilities**:
- Display upcoming calendar events in a RecyclerView
- Handle user permissions (calendar, notifications, system overlay)
- Provide manual refresh and bulk scheduling controls
- Guide users through battery optimization exemption
- Display app status and event counts

**Key Features**:
- Permission flow management with user-friendly dialogs
- Real-time event display with automatic updates
- Integration with system settings for battery optimization
- Error handling and user feedback

#### ReminderActivity
**Purpose**: Full-screen reminder display for calendar events

**Responsibilities**:
- Display immersive, full-screen event reminders
- Handle user interactions (dismiss, snooze, view event)
- Manage audio alerts with gradual volume increase
- Work over lock screen and other applications

**Key Features**:
- Lock screen overlay capability
- Gradual audio fade-in (1% to 100% over 30 seconds)
- Multiple user interaction options
- Auto-dismiss after 2 minutes if no interaction
- Vibration patterns for silent devices

#### SettingsActivity
**Purpose**: App configuration and user preferences

**Responsibilities**:
- Configure final reminder timing (default 1 minute before event)
- Manage app preferences and settings
- Provide access to system permission settings
- Display app information and troubleshooting guides

### 🧠 Business Layer

#### CalendarManager
**Purpose**: Core business logic for calendar operations and alarm management

**Responsibilities**:
- Query calendar events from Android's CalendarContract API
- Schedule precise alarms using AlarmManager
- Manage event caching for performance optimization
- Handle recurring events through the Instances API
- Clean up orphaned alarms for deleted events

**Key Features**:
- 30-day lookahead window for event retrieval
- Intelligent caching (30-second cache for events, 5-minute cache for calendar list)
- Support for multiple calendar providers (Google, Exchange, etc.)
- Automatic alarm deduplication and conflict resolution
- Graceful handling of system limitations and permissions

**Architecture Details**:
```kotlin
class CalendarManager(private val context: Context) {
    // Core alarm scheduling
    private val alarmManager: AlarmManager
    
    // Performance optimization
    private var cachedEvents: List<CalendarEvent>?
    private var cacheTimestamp: Long
    
    // Public API
    fun getUpcomingEventsWithReminders(): List<CalendarEvent>
    fun scheduleAllReminders()
    fun scheduleReminder(event: CalendarEvent)
    fun cancelReminder(eventId: Long)
    fun isAlarmScheduled(eventId: Long): Boolean
}
```

### 🔄 Service Layer

#### AlarmReceiver
**Purpose**: Handle alarm broadcasts and trigger reminder display

**Responsibilities**:
- Receive alarm broadcasts from AlarmManager
- Launch ReminderActivity for event display
- Handle alarm intent data processing
- Manage alarm lifecycle and cleanup

**Implementation**:
```kotlin
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Extract event information from intent
        // Launch ReminderActivity with event data
        // Handle edge cases and error scenarios
    }
}
```

#### BootReceiver
**Purpose**: Restart monitoring after device reboot

**Responsibilities**:
- Detect device boot completion
- Restart WorkManager background monitoring
- Re-schedule any missed alarms
- Ensure service continuity across reboots

#### WorkManager
**Purpose**: Background monitoring for calendar changes

**Responsibilities**:
- Monitor calendar changes every minute
- Detect new events and schedule alarms automatically
- Handle system limitations (doze mode, battery optimization)
- Provide reliable background execution

**Configuration**:
- 1-minute periodic monitoring interval
- Self-scheduling to bypass Android's 15-minute WorkManager limitations
- Battery-efficient implementation
- Automatic restart after app updates or system changes

### 📊 Data Layer

#### Android Calendar Provider
**Primary Data Source**: CalendarContract API

**Data Access Patterns**:
```kotlin
// Event retrieval using Instances API for recurring events
val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
    .appendPath(startTime.toString())
    .appendPath(endTime.toString())
    .build()

// Query with optimized projection for performance
val projection = arrayOf(
    CalendarContract.Instances.EVENT_ID,
    CalendarContract.Instances.TITLE,
    CalendarContract.Instances.BEGIN,
    CalendarContract.Instances.END
)
```

**Features**:
- Support for multiple calendar accounts
- Recurring event expansion via Instances API
- Efficient querying with time-range filtering
- Automatic handling of calendar provider variations

#### AlarmManager Integration
**Purpose**: System-level alarm scheduling

**Implementation Details**:
- Uses `setExactAndAllowWhileIdle()` for Doze mode compatibility
- Unique PendingIntent for each alarm to prevent conflicts
- Automatic alarm persistence across device reboots
- Fallback strategies for permission-restricted scenarios

#### SharedPreferences
**Purpose**: App configuration and user preferences

**Stored Data**:
- Final reminder timing preference
- User onboarding completion status
- App settings and configuration flags
- Battery optimization exemption status

## Data Flow

### 1. Event Retrieval Flow
```
User Action (Refresh) → MainActivity → CalendarManager → Android Calendar Provider
                                            ↓
                     Cache Check ← CalendarManager ← Query Results
                                            ↓
                     UI Update ← MainActivity ← Processed Events
```

### 2. Alarm Scheduling Flow
```
Manual/Auto Schedule → CalendarManager → Alarm Validation → AlarmManager
                                            ↓                    ↓
                     Permission Check ← Deduplication ← PendingIntent Creation
                                            ↓
                     User Feedback ← MainActivity ← Scheduling Result
```

### 3. Reminder Trigger Flow
```
System Alarm → AlarmReceiver → Intent Processing → ReminderActivity
                    ↓                                    ↓
            Background Work ← User Interaction ← Full-screen Display
                    ↓                                    ↓
            Cleanup Tasks ← Activity Result ← Alarm Resolution
```

## Performance Optimizations

### Caching Strategy
- **Events Cache**: 30-second duration to reduce calendar queries
- **Calendar List Cache**: 5-minute duration for account enumeration
- **Automatic Invalidation**: Cache cleared before alarm scheduling operations

### Database Query Optimization
```kotlin
// Efficient projection - only required columns
val projection = arrayOf(
    CalendarContract.Instances.EVENT_ID,
    CalendarContract.Instances.TITLE,
    CalendarContract.Instances.BEGIN
)

// Time-based filtering in URI for better performance
val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
    .appendPath(startTime.toString())
    .appendPath(endTime.toString())
    .build()
```

### Memory Management
- Proper cursor resource cleanup with `use {}` blocks
- Minimal object allocation in critical paths
- Efficient data structures for event storage
- Cache size limits to prevent memory bloat

### Background Processing
- WorkManager for reliable background execution
- Coroutines for asynchronous operations
- IO dispatcher for database operations
- Main dispatcher for UI updates

## Security Considerations

### Permission Model
- **Runtime Permissions**: All sensitive permissions requested at runtime
- **Graceful Degradation**: App functions with limited permissions
- **User Education**: Clear explanations for permission requirements

### Data Privacy
- **Local Processing**: All calendar data remains on device
- **Minimal Data Access**: Only reads required event fields
- **No Network Transmission**: No external data sharing
- **Temporary Storage**: Event data cached briefly in memory only

### System Integration
- **AlarmManager Security**: Uses exact alarms only when necessary
- **Overlay Permissions**: System overlay only for reminder display
- **Background Limitations**: Respects Android's background execution limits

## Error Handling

### Exception Management
```kotlin
try {
    val events = calendarManager.getUpcomingEventsWithReminders()
    // Process events
} catch (e: SecurityException) {
    // Handle permission issues
    showPermissionError()
} catch (e: IllegalStateException) {
    // Handle system state issues
    showServiceUnavailableError()
}
```

### Fallback Strategies
- **Permission Denied**: Provide manual alternatives
- **System Limitations**: Graceful degradation of features
- **Calendar Unavailable**: Clear user guidance for resolution
- **Alarm Failures**: Retry logic with exponential backoff

### User Feedback
- **Clear Error Messages**: User-friendly error descriptions
- **Actionable Guidance**: Specific steps for issue resolution
- **Status Indicators**: Visual feedback for app state
- **Comprehensive Logging**: Detailed logs for debugging support

## Testing Strategy

### Unit Testing
- **Business Logic**: CalendarManager methods and algorithms
- **Data Models**: CalendarEvent and utility classes
- **Error Handling**: Exception scenarios and edge cases

### Integration Testing
- **Calendar Provider**: Real calendar data interaction
- **AlarmManager**: Alarm scheduling and triggering
- **Permission Flows**: Runtime permission handling

### UI Testing
- **User Interactions**: Button clicks and navigation
- **Permission Dialogs**: Permission request flows
- **Error States**: Error message display and handling

## Build and Deployment

### Build Configuration
- **Minimum SDK**: Android 5.0 (API 21) for wide compatibility
- **Target SDK**: Android 14 (API 34) for latest features
- **Kotlin**: 2.0.21 with coroutines support
- **Gradle**: 8.13.0 with build optimization

### Release Process
1. **Code Review**: Thorough review of all changes
2. **Testing**: Comprehensive testing on multiple devices
3. **Version Bump**: Semantic versioning for releases
4. **APK Generation**: Signed release builds
5. **Documentation**: Release notes and changelog updates

### Continuous Integration
- **Automated Testing**: Unit tests run on every commit
- **Code Quality**: Static analysis and lint checks
- **Build Verification**: Successful builds for all variants
- **Dependency Updates**: Regular dependency security updates

This architecture provides a solid foundation for reliable calendar reminder functionality while maintaining code quality, performance, and user experience standards.