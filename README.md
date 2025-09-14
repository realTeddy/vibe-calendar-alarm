# 📅 Full Screen Calendar Reminder

<div align="center">

![Android](https://img.shields.io/badge/Android-5.0%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Build Status](https://github.com/realTeddy/FullScreenCalenderReminer/actions/workflows/android-ci.yml/badge.svg)
![Code Quality](https://img.shields.io/badge/Code%20Quality-A-brightgreen.svg)

*Never miss another important event with reliable, full-screen reminders for all your calendar events*

[📱 Download](#installation) • [🚀 Features](#features) • [🔧 Setup](#setup-instructions) • [🤝 Contributing](CONTRIBUTING.md) • [🚀 CI/CD](CI_CD_GUIDE.md)

</div>

## 🌟 Overview

**Full Screen Calendar Reminder** is a lightweight Android application that ensures you never miss important calendar events by providing reliable, impossible-to-miss full-screen reminders. Unlike traditional calendar notifications that can be easily dismissed or overlooked, this app creates immersive alert experiences that capture your full attention.

### Key Highlights
- 🔔 **Universal Reminders**: Automatically adds 1-minute reminders to ALL calendar events
- 📱 **Full-Screen Alerts**: Immersive reminders that display over the lock screen
- 🔊 **Gradual Audio**: 30-second fade-in from 1% to 100% volume for gentle awakening
- 🔄 **Smart Background Monitoring**: Continuous monitoring with 1-minute intervals
- 📅 **Multi-Calendar Support**: Works with Google Calendar, Exchange, and all calendar providers
- ♻️ **Recurring Events**: Proper handling of repeating events through Android's Instances API

## ✨ Features

### Core Functionality
- **Automatic Reminder Addition**: Adds 1-minute reminders to ALL calendar events, regardless of existing reminder settings
- **Full-Screen Alert Display**: Shows immersive, lock-screen-friendly reminders with event details
- **Background Event Monitoring**: Uses WorkManager for reliable background execution every minute
- **Multi-Calendar Integration**: Automatically detects and works with all calendar accounts on your device
- **Recurring Event Support**: Properly handles repeating events through Android's CalendarContract.Instances API
- **Automatic Cleanup**: Removes alarms for deleted calendar events to prevent orphaned notifications

### User Experience
- **Gradual Audio Alerts**: 30-second volume fade-in from 1% to 100% to avoid jarring wake-ups
- **Smart Permission Management**: Guided permission flow with user-friendly explanations
- **Battery Optimization Awareness**: Helps users exempt the app from battery optimization for reliable operation
- **Real-time Event Display**: Shows upcoming events with reminder status in an intuitive list
- **One-tap Operations**: Simple buttons for refreshing events and scheduling all reminders

### Technical Features
- **Performance Optimized**: 30-day lookahead window with intelligent caching to reduce database queries
- **Reliable Alarm Scheduling**: Uses Android's AlarmManager with exact timing for precise reminders
- **Fallback Strategies**: Graceful degradation when permissions are limited or system restrictions apply
- **Memory Efficient**: Smart caching strategies and cleanup routines to minimize resource usage

## 📱 Screenshots

> **Note**: Screenshots will be added in a future update

| Main Interface | Full-Screen Reminder | Settings |
|:--------------:|:-------------------:|:--------:|
| *Coming Soon*  | *Coming Soon*       | *Coming Soon* |

## 🔧 Requirements

### System Requirements
- **Android Version**: 5.0 (API level 21) or higher
- **Storage**: ~10 MB available space
- **RAM**: Minimal memory footprint (< 50 MB active usage)

### Dependencies
- Calendar app with events (Google Calendar, Samsung Calendar, Outlook, etc.)
- Device calendar providers properly configured

### Required Permissions
- **Calendar Access** (`READ_CALENDAR`): To read your calendar events
- **Exact Alarms** (`SCHEDULE_EXACT_ALARM`): For precise reminder timing
- **System Alert Window** (`SYSTEM_ALERT_WINDOW`): For full-screen display over lock screen
- **Notifications** (`POST_NOTIFICATIONS`): For Android 13+ notification support
- **Wake Lock** (`WAKE_LOCK`): To wake device from sleep for alarms
- **Battery Optimization Exemption**: For reliable background operation

## 📥 Installation

### Method 1: Download APK (Recommended)
1. Download the latest APK from the [Releases](../../releases) page
2. Enable "Install from unknown sources" in your device settings if prompted
3. Install the APK file
4. Follow the [Setup Instructions](#setup-instructions) below

### Method 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/realTeddy/FullScreenCalenderReminer.git
cd FullScreenCalenderReminer

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

For detailed development setup, see [CONTRIBUTING.md](CONTRIBUTING.md).

## 🚀 Setup Instructions

### Initial Setup
1. **Install and Launch**: Open the app after installation
2. **Grant Permissions**: The app will request several permissions:
   - **Calendar Access**: Essential for reading your events
   - **Exact Alarm Scheduling**: Required for precise reminder timing
   - **System Alert Window**: Needed for full-screen display
   - **Notifications**: For Android 13+ compatibility

3. **Battery Optimization**: For reliable operation, exempt the app from battery optimization:
   - The app will guide you through this process automatically
   - This ensures alarms work even when the device is in deep sleep
   - Navigate to: Settings → Battery → Battery Optimization → Select app → Don't optimize

### First Use
1. **Automatic Detection**: The app automatically discovers all calendar accounts
2. **Event Synchronization**: Initial sync may take a few seconds for large calendars
3. **Background Setup**: WorkManager begins monitoring for new/changed events
4. **Verification**: Check the main screen to see upcoming events and their reminder status

### Daily Usage
- **Automatic Operation**: The app works silently in the background
- **Manual Refresh**: Tap "Refresh Events" to immediately sync calendar changes
- **Bulk Scheduling**: Use "Schedule All Reminders" to ensure all events have alarms
- **Status Monitoring**: Main screen shows upcoming events and system status

## 🔍 How It Works
### Architecture Overview
The app uses a multi-layered architecture designed for reliability and performance:

#### Background Monitoring System
- **WorkManager Integration**: Uses Android's WorkManager for reliable background execution
- **Smart Scheduling**: Self-scheduling approach bypasses Android's 15-minute WorkManager limitations
- **Event Detection**: Monitors calendar changes every minute for real-time responsiveness
- **Battery Efficiency**: Optimized to minimize battery drain while maintaining reliability

#### Calendar Integration
- **Universal Compatibility**: Works with all Android calendar providers (Google, Exchange, Samsung, etc.)
- **Lookahead Window**: Scans events for the next 30 days with intelligent caching
- **Recurring Events**: Proper handling through Android's CalendarContract.Instances API
- **Performance Optimized**: Smart caching reduces database queries and improves responsiveness

#### Reminder System
- **Automatic Addition**: Adds 1-minute reminders to ALL events, regardless of existing reminders
- **Alarm Precision**: Uses AlarmManager.setExactAndAllowWhileIdle() for reliable timing
- **Event Preservation**: Preserves existing reminders while adding mandatory 1-minute alerts
- **Cleanup Management**: Automatically removes alarms for deleted calendar events

### Full-Screen Reminder Experience
- **Lock Screen Display**: Shows over lock screen and all other applications
- **Rich Event Information**: Displays event title, time, and duration clearly
- **Interactive Options**: Multiple interaction choices:
  - **Dismiss**: Mark reminder as acknowledged
  - **Snooze**: Postpone reminder for 5 minutes
  - **View Event**: Open calendar app to event details
- **Auto-Dismiss**: Automatically dismisses after 2 minutes if no interaction
- **Gradual Audio**: 30-second volume fade-in from 1% to 100% for gentle awakening

## 🏗️ Technical Architecture

### Design Patterns
- **MVVM (Model-View-ViewModel)**: Clean separation of business logic from UI
- **Repository Pattern**: Centralized data access layer for calendar operations
- **Observer Pattern**: Real-time UI updates through LiveData and StateFlow
- **Dependency Injection**: Hilt for testable, modular, and maintainable code

### Key Components

#### Core Classes
- **`CalendarManager`**: Central hub for calendar operations and alarm scheduling
- **`MainActivity`**: Primary interface for user interaction and permission management
- **`ReminderActivity`**: Full-screen reminder display with user interaction handling
- **`AlarmReceiver`**: Broadcast receiver for alarm events and system notifications
- **`ReminderWorkManager`**: Background service for continuous calendar monitoring

#### Utility Classes
- **`FallbackStrategy`**: Graceful degradation when system limitations are encountered
- **`ErrorHandler`**: Centralized error handling and user-friendly messaging
- **`CalendarEvent`**: Data model representing calendar events with reminder information

### Performance Optimizations
- **Intelligent Caching**: 30-second cache duration for calendar queries to reduce database load
- **Efficient Queries**: Optimized cursor operations and minimal data retrieval
- **Memory Management**: Proper resource cleanup and memory-conscious data structures
- **Background Efficiency**: Minimal background CPU usage while maintaining reliability

## 🔐 Privacy & Security

### Data Handling
- **Local Processing**: All calendar data remains on your device - no data is transmitted externally
- **Minimal Data Access**: Only reads event title, time, and reminder information
- **No Personal Information**: No access to event descriptions, locations, or attendee information
- **Temporary Storage**: Event data is cached briefly in memory only for performance

### Permissions Explained
| Permission | Purpose | Required |
|------------|---------|----------|
| `READ_CALENDAR` | Read calendar events to create reminders | ✅ Essential |
| `SCHEDULE_EXACT_ALARM` | Set precise alarm times for reminders | ✅ Essential |
| `SYSTEM_ALERT_WINDOW` | Display full-screen reminders over lock screen | ✅ Essential |
| `POST_NOTIFICATIONS` | Show notifications on Android 13+ | ✅ Essential |
| `WAKE_LOCK` | Wake device from sleep for alarms | ✅ Essential |
| `VIBRATE` | Provide vibration feedback | ⚪ Optional |
| `RECEIVE_BOOT_COMPLETED` | Auto-start monitoring after device restart | ⚪ Optional |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Request battery optimization exemption | ⚪ Recommended |

## 🛠️ Troubleshooting

### Common Issues

#### Reminders Not Appearing
1. **Verify Permissions**: Settings → Apps → Full Screen Calendar Reminder → Permissions
2. **Check Battery Optimization**: Settings → Battery → Battery Optimization → Don't optimize
3. **Disable Do Not Disturb**: Ensure DND settings allow alarms
4. **Manual Refresh**: Use "Refresh Events" and "Schedule All Reminders" buttons
5. **Restart App**: Force close and reopen the application

#### Events Not Displaying
1. **Calendar Synchronization**: Ensure your calendar app is syncing properly with accounts
2. **Account Status**: Verify calendar accounts are active and properly authenticated
3. **Event Timeframe**: App shows events for the next 30 days only
4. **Cache Refresh**: Force close app and restart to clear cache
5. **Permission Reset**: Revoke and re-grant calendar permission in system settings

#### Background Monitoring Issues
1. **Auto-Start Management**: Enable auto-start for the app (varies by device manufacturer)
2. **App Hibernation**: Disable app hibernation in device settings
3. **WorkManager Status**: Check if WorkManager is functioning properly
4. **Battery Saver Mode**: Disable aggressive battery saving modes
5. **Recent Apps**: Don't swipe away the app from recent apps list

### Device-Specific Solutions

#### Samsung Devices
- Enable "Allow background activity" in Battery settings
- Add app to "Never sleeping apps" list
- Disable "Put app to sleep" option

#### Xiaomi/MIUI Devices
- Enable "Autostart" in Security app
- Set battery saver to "No restrictions"
- Add to "Battery optimization" whitelist

#### Huawei/EMUI Devices
- Enable "Manual launch" in Phone Manager
- Add to "Protected apps" list
- Disable "Close apps after screen lock"

## 🤝 Contributing

We welcome contributions from the community! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines on:

- 🔧 Development setup and environment
- 📝 Code style and conventions
- 🧪 Testing procedures and requirements
- 📋 Issue reporting and feature requests
- 🔄 Pull request process and reviews

### Quick Start for Contributors
```bash
# Fork the repository and clone your fork
git clone https://github.com/realTeddy/FullScreenCalenderReminer.git
cd FullScreenCalenderReminer

# Create a new feature branch
git checkout -b feature/your-feature-name

# Make your changes and test thoroughly
./gradlew test
./gradlew assembleDebug

# Submit a pull request with detailed description
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Android Open Source Project**: For providing the robust calendar and alarm APIs
- **Material Design**: For the beautiful and intuitive UI components
- **WorkManager**: For reliable background task execution
- **Community Contributors**: For bug reports, feature suggestions, and code contributions

## 📞 Support

### Getting Help
- **Documentation**: Check this README and [CONTRIBUTING.md](CONTRIBUTING.md) first
- **Issues**: Report bugs and request features via [GitHub Issues](../../issues)
- **Discussions**: Join community discussions in the [Discussions](../../discussions) tab

### Reporting Issues
Please include the following information when reporting issues:
- Device model and Android version
- App version number
- Steps to reproduce the issue
- Error messages or logs (if any)
- Screenshots (if applicable)

### Feature Requests
We welcome feature suggestions! Please:
- Check existing issues to avoid duplicates
- Provide clear use cases and benefits
- Consider implementation complexity
- Be open to community discussion

---

<div align="center">

**Built with ❤️ for the Android community**

*Never miss an important moment again*

[⬆️ Back to Top](#-full-screen-calendar-reminder)

</div>
2. **Recent Apps**: Avoid swiping away the app from recent apps
3. **Power Saving**: Disable aggressive power saving modes

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Development Setup
1. Fork the repository
2. Create a feature branch: `git checkout -b feature-name`
3. Make your changes with proper testing
4. Submit a pull request

### Code Style
- Follow Kotlin coding conventions
- Add KDoc comments for public APIs
- Write unit tests for new features
- Ensure all tests pass before submitting

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Privacy Policy

This app:
- **Only reads** calendar data locally on your device
- **Does not collect** any personal information
- **Does not transmit** any data to external servers
- **Does not use** analytics or tracking services

All calendar data remains private and local to your device.

## Support

- **Issues**: Report bugs or request features via [GitHub Issues](../../issues)
- **Discussions**: General questions in [GitHub Discussions](../../discussions)
- **Email**: [CONTACT-EMAIL@example.com](mailto:CONTACT-EMAIL@example.com)

## Acknowledgments

- Built with Android's modern development stack
- Uses material design principles
- Inspired by the need for truly reliable calendar reminders

---

**Note**: This app is designed to complement, not replace, your existing calendar apps. It works alongside Google Calendar, Samsung Calendar, and other calendar applications to ensure you never miss important events.