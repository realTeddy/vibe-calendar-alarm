# 🤝 Contributing to Full Screen Calendar Reminder

Thank you for your interest in contributing to Full Screen Calendar Reminder! This document provides comprehensive guidelines and information for contributors to help maintain code quality and streamline the development process.

## 📋 Table of Contents

- [Quick Start](#-quick-start)
- [How to Contribute](#-how-to-contribute)
- [Development Environment](#-development-environment)
- [Project Structure](#-project-structure)
- [Development Guidelines](#-development-guidelines)
- [Testing](#-testing)
- [Commit Guidelines](#-commit-guidelines)
- [Pull Request Process](#-pull-request-process)
- [Issue Reporting](#-issue-reporting)
- [Getting Help](#-getting-help)

## 🚀 Quick Start

### For First-Time Contributors
```bash
# 1. Fork the repository on GitHub
# 2. Clone your fork locally
git clone https://github.com/realTeddy/FullScreenCalenderReminer.git
cd FullScreenCalenderReminer

# 3. Create a feature branch
git checkout -b feature/your-feature-name

# 4. Set up development environment
./gradlew build

# 5. Make your changes and test
./gradlew test
./gradlew assembleDebug

# 6. Commit and push your changes
git add .
git commit -m "feat: add your feature description"
git push origin feature/your-feature-name

# 7. Create a Pull Request on GitHub
```

### Prerequisites
- **Android Studio**: Arctic Fox or newer
- **JDK**: 11 or higher
- **Android SDK**: API 21+ (Android 5.0+)
- **Git**: For version control
- **Device/Emulator**: For testing with calendar permissions

## 💡 How to Contribute

### Types of Contributions We Welcome

#### 🐛 Bug Fixes
- Fix existing functionality that isn't working as expected
- Improve error handling and edge case management
- Performance optimizations and memory leak fixes

#### ✨ New Features
- Calendar integration improvements
- User interface enhancements
- Accessibility features
- Notification and reminder system improvements

#### 📚 Documentation
- Code documentation and KDoc improvements
- README updates and clarifications
- API documentation and architecture guides
- User guides and troubleshooting documentation

#### 🧪 Testing
- Unit test coverage improvements
- Integration tests for calendar functionality
- UI/UX testing and automation
- Performance testing and benchmarking

#### 🎨 UI/UX Improvements
- Material Design compliance
- Accessibility enhancements
- User experience optimizations
- Dark mode and theming improvements

## 🛠️ Development Environment

### Setting Up Your Development Environment

#### 1. Install Required Tools
```bash
# Install Android Studio from https://developer.android.com/studio
# Install Git from https://git-scm.com/downloads
# Ensure JDK 11+ is installed
```

#### 2. Clone and Setup
```bash
git clone https://github.com/realTeddy/FullScreenCalenderReminer.git
cd FullScreenCalenderReminer

# Verify build works
./gradlew clean build

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

#### 3. IDE Configuration
- **Import Project**: Open in Android Studio using "Import Project"
- **SDK Configuration**: Ensure Android SDK 34+ is installed
- **Build Tools**: Verify Gradle wrapper is using the correct version
- **Code Style**: Import the project's code style settings (if available)

#### 4. Device Setup for Testing
```bash
# Connect physical device or start emulator
adb devices

# Install debug version
./gradlew installDebug

# Grant necessary permissions for testing
adb shell pm grant me.tewodros.vibecalendaralarm android.permission.READ_CALENDAR
```

### Development Dependencies
The project uses the following key dependencies:
- **Kotlin**: 2.0.21
- **Android Gradle Plugin**: 8.13.0
- **Target SDK**: 36
- **Minimum SDK**: 21
- **WorkManager**: 2.9.0
- **Material Components**: 1.12.0

## 📁 Project Structure

```
app/src/main/java/me/tewodros/fullscreencalenderreminder/
├── MainActivity.kt                 # Main app interface and permission management
├── CalendarManager.kt             # Core calendar and alarm logic
├── ReminderActivity.kt            # Full-screen reminder display
├── SettingsActivity.kt            # App settings and configuration
├── AlarmReceiver.kt              # Alarm broadcast handling
├── BootReceiver.kt               # Boot-time initialization
├── CalendarReminderApplication.kt # Application class and initialization
├── model/                        # Data models and entities
│   └── CalendarEvent.kt          # Calendar event data representation
├── ui/                          # UI components and adapters
│   └── CalendarEventAdapter.kt   # RecyclerView adapter for events
├── util/                       # Utility classes and helpers
│   ├── ErrorHandler.kt         # Centralized error handling
│   └── FallbackStrategy.kt     # Graceful degradation strategies
└── viewmodel/                  # MVVM ViewModels (future expansion)
```

### Key Architectural Components

#### Core Classes
- **`CalendarManager`**: Central hub for all calendar operations, alarm scheduling, and event management
- **`MainActivity`**: Primary user interface, handles permissions and displays events
- **`ReminderActivity`**: Full-screen reminder display with user interaction options
- **`AlarmReceiver`**: Processes alarm broadcasts and triggers reminder activities

#### Data Flow
1. **CalendarManager** queries device calendars for events
2. **Events** are cached and displayed in **MainActivity**
3. **Alarms** are scheduled via Android's AlarmManager
4. **AlarmReceiver** triggers **ReminderActivity** when alarms fire
5. **User interactions** are processed and logged appropriately

## 📝 Development Guidelines

### Code Style and Standards

#### Kotlin Coding Conventions
```kotlin
// Use meaningful variable names
val upcomingEvents = calendarManager.getUpcomingEventsWithReminders()

// Prefer data classes for models
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val reminderMinutes: List<Int>
)

// Use extension functions for utility operations
fun Context.hasCalendarPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}

// Proper coroutine usage
class CalendarManager(private val context: Context) {
    suspend fun getEventsAsync(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        // Database operations on IO thread
        queryCalendarEvents()
    }
}
```

#### Architecture Guidelines

##### MVVM Pattern Implementation
```kotlin
// ViewModel for business logic
class MainViewModel : ViewModel() {
    private val _events = MutableLiveData<List<CalendarEvent>>()
    val events: LiveData<List<CalendarEvent>> = _events
    
    fun refreshEvents() {
        viewModelScope.launch {
            _events.value = calendarManager.getUpcomingEventsWithReminders()
        }
    }
}

// Activity focuses on UI logic only
class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.events.observe(this) { events ->
            eventAdapter.submitList(events)
        }
    }
}
```

##### Dependency Injection Best Practices
```kotlin
// Use constructor injection
class CalendarManager @Inject constructor(
    private val context: Context,
    private val alarmManager: AlarmManager
) {
    // Implementation
}

// Provide appropriate scopes
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideCalendarManager(context: Context): CalendarManager {
        return CalendarManager(context)
    }
}
```

##### Coroutines Guidelines
```kotlin
// Use appropriate dispatchers
class CalendarManager {
    suspend fun getEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        // I/O operations
    }
    
    suspend fun updateUI() = withContext(Dispatchers.Main) {
        // UI updates
    }
}

// Handle exceptions properly
viewModelScope.launch {
    try {
        val events = calendarManager.getEvents()
        _events.value = events
    } catch (e: SecurityException) {
        _error.value = "Calendar permission required"
    }
}
```

### Error Handling Standards
```kotlin
// Centralized error handling
class ErrorHandler {
    fun handleCalendarError(error: Throwable): String {
        return when (error) {
            is SecurityException -> "Calendar permission required"
            is IllegalStateException -> "Calendar service unavailable"
            else -> "An unexpected error occurred: ${error.message}"
        }
    }
}

// Graceful degradation
class FallbackStrategy {
    fun handlePermissionDenied(context: Context): String {
        return "Calendar access denied. You can manually add events or enable permission in app settings."
    }
}
```

### Performance Guidelines
```kotlin
// Cache expensive operations
class CalendarManager {
    private var cachedEvents: List<CalendarEvent>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 30 * 1000L
    
    fun getUpcomingEventsWithReminders(): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        if (cachedEvents != null && (now - cacheTimestamp) < CACHE_DURATION_MS) {
            return cachedEvents!!
        }
        
        // Expensive database operation
        val events = queryCalendarDatabase()
        cachedEvents = events
        cacheTimestamp = now
        return events
    }
}

// Efficient database queries
private fun queryCalendarEvents(): List<CalendarEvent> {
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN
    )
    // Only query required columns to minimize data transfer
}
```

## 🧪 Testing

### Testing Strategy
We follow a comprehensive testing approach to ensure reliability:

#### Unit Tests
- **Business Logic**: Test ViewModels and utility classes
- **Data Models**: Verify CalendarEvent and other models
- **Utility Functions**: Test helper methods and extensions
- **Error Handling**: Verify error scenarios and fallback strategies

```kotlin
@Test
fun `getUpcomingEventsWithReminders should return cached events when cache is valid`() = runTest {
    // Given
    val calendarManager = CalendarManager(context)
    val mockEvents = listOf(CalendarEvent(1, "Test Event", System.currentTimeMillis(), listOf(1)))
    
    // When - First call populates cache
    calendarManager.setTestEvents(mockEvents)
    val firstResult = calendarManager.getUpcomingEventsWithReminders()
    
    // Clear mock to verify cache is used
    calendarManager.clearTestEvents()
    val secondResult = calendarManager.getUpcomingEventsWithReminders()
    
    // Then
    assertEquals(mockEvents, firstResult)
    assertEquals(mockEvents, secondResult) // Should return cached events
}

@Test
fun `scheduleReminder should return true when alarm is scheduled successfully`() = runTest {
    // Given
    val event = CalendarEvent(1, "Test Event", System.currentTimeMillis() + 60000, listOf(1))
    val mockAlarmManager = mockk<AlarmManager>()
    every { mockAlarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } returns Unit
    
    val calendarManager = CalendarManager(context, mockAlarmManager)
    
    // When
    val result = calendarManager.scheduleReminder(event)
    
    // Then
    assertTrue(result)
    verify { mockAlarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
}
```

#### Integration Tests
```kotlin
@Test
fun `app should handle calendar permission grant gracefully`() {
    // Test calendar integration with real calendar provider
    composeTestRule.onNodeWithText("Grant Calendar Permission").performClick()
    composeTestRule.onNodeWithText("Events Found").assertIsDisplayed()
}

@Test
fun `full screen reminder should display and respond to user interaction`() {
    // Test reminder activity lifecycle and user interactions
    val intent = Intent(context, ReminderActivity::class.java)
    activityRule.launchActivity(intent)
    
    onView(withId(R.id.dismissButton)).perform(click())
    // Verify activity finishes appropriately
}
```

#### Running Tests
```bash
# Run all unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests CalendarManagerTest

# Generate test coverage report
./gradlew testDebugUnitTestCoverage
```

### Test Coverage Goals
- **Minimum**: 70% line coverage for core functionality
- **Target**: 85% line coverage overall
- **Critical Components**: 90%+ coverage for CalendarManager and AlarmReceiver

## 📝 Commit Guidelines

### Commit Message Format
We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

#### Types
- **feat**: New features
- **fix**: Bug fixes
- **docs**: Documentation changes
- **style**: Code style changes (formatting, missing semicolons, etc.)
- **refactor**: Code refactoring without changing functionality
- **test**: Adding or updating tests
- **chore**: Maintenance tasks, dependency updates

#### Examples
```bash
# Feature addition
feat(calendar): add support for recurring event exceptions

# Bug fix
fix(alarm): resolve issue with alarm not firing in doze mode

# Documentation
docs(readme): update installation instructions for Android 14

# Refactoring
refactor(calendar): extract event query logic into separate method

# Test addition
test(calendar): add unit tests for event caching mechanism
```

#### Best Practices
- **Keep commits atomic**: One logical change per commit
- **Write clear descriptions**: Explain what and why, not how
- **Reference issues**: Include issue numbers when applicable
- **Test before committing**: Ensure tests pass before committing

```bash
# Good commit workflow
git add .
./gradlew test                    # Ensure tests pass
git commit -m "feat(ui): add dark mode support

- Implemented theme switching in settings
- Added dark theme colors and styles
- Updated all activities to respect theme preference
- Closes #42"
```

## 🔄 Pull Request Process

### Before Submitting a Pull Request

#### 1. Preparation Checklist
- [ ] Branch is up to date with main
- [ ] All tests pass locally
- [ ] Code follows project style guidelines
- [ ] Documentation is updated (if applicable)
- [ ] No merge conflicts exist

```bash
# Update your branch
git checkout main
git pull upstream main
git checkout your-feature-branch
git rebase main

# Verify everything works
./gradlew clean build test
```

#### 2. Pull Request Template
```markdown
## Description
Brief description of changes and motivation.

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed
- [ ] Tested on physical device

## Screenshots (if applicable)
Include screenshots for UI changes.

## Checklist
- [ ] My code follows the project's coding standards
- [ ] I have performed a self-review of my code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
```

### Review Process
1. **Automated Checks**: CI/CD pipeline runs tests and checks
2. **Code Review**: Maintainers review code quality and design
3. **Testing**: Reviewers test functionality on various devices
4. **Approval**: At least one maintainer approval required
5. **Merge**: Squash and merge to maintain clean history

### Review Criteria
- **Functionality**: Does the code work as intended?
- **Code Quality**: Is the code readable, maintainable, and efficient?
- **Testing**: Are there adequate tests for the changes?
- **Documentation**: Is documentation updated appropriately?
- **Performance**: Does the change impact app performance?
- **Security**: Are there any security implications?

## 🐛 Issue Reporting

### Before Creating an Issue
1. **Search existing issues**: Check if the issue already exists
2. **Update to latest version**: Ensure you're using the latest release
3. **Check documentation**: Review README and troubleshooting guides
4. **Test on different devices**: Verify the issue isn't device-specific

### Bug Report Template
```markdown
**Bug Description**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected Behavior**
A clear and concise description of what you expected to happen.

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Device Information**
- Device: [e.g. Samsung Galaxy S21]
- OS: [e.g. Android 12]
- App Version: [e.g. 1.2.0]
- Calendar App: [e.g. Google Calendar]

**Additional Context**
Add any other context about the problem here.

**Logs**
If possible, include relevant logcat output:
```
adb logcat | grep "FullScreenCalender"
```
```

### Feature Request Template
```markdown
**Is your feature request related to a problem?**
A clear and concise description of what the problem is.

**Describe the solution you'd like**
A clear and concise description of what you want to happen.

**Describe alternatives you've considered**
A clear and concise description of any alternative solutions or features you've considered.

**Additional context**
Add any other context or screenshots about the feature request here.

**Implementation Suggestions**
If you have ideas about how this could be implemented, please share them.
```

## 🆘 Getting Help

### Support Channels
- **GitHub Issues**: For bug reports and feature requests
- **GitHub Discussions**: For questions, ideas, and community support
- **Documentation**: Check README.md and this contributing guide first

### Community Guidelines
- **Be respectful**: Treat all community members with respect
- **Be patient**: Maintainers and contributors are often volunteers
- **Be helpful**: Help others when you can
- **Be specific**: Provide detailed information when asking for help

### Development Questions
For development-related questions:
1. **Check existing issues**: Look for similar development questions
2. **Search documentation**: Review code comments and documentation
3. **Create discussion**: Use GitHub Discussions for open-ended questions
4. **Provide context**: Include relevant code snippets and error messages

---

## 🙏 Recognition

Contributors will be recognized in the following ways:
- **README Credits**: Contributors listed in the main README
- **Release Notes**: Contributors mentioned in release announcements
- **Hall of Fame**: Special recognition for significant contributions

### Contributor Levels
- **First-time Contributors**: Welcome badge and guidance
- **Regular Contributors**: Recognition in project documentation
- **Core Contributors**: Commit access and review privileges
- **Maintainers**: Full project access and decision-making authority

Thank you for contributing to Full Screen Calendar Reminder! Your efforts help make this app better for everyone in the Android community.

---

*For any questions about contributing, please don't hesitate to reach out through GitHub Issues or Discussions.*
