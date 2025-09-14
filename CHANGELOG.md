# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### 📋 Planned
- Enhanced notification settings and customization options
- Multiple reminder time configurations (beyond 1-minute default)
- Calendar-specific reminder settings
- Dark mode theme improvements
- Performance optimizations for large calendar datasets

## [1.0.0] - 2025-09-20

### 🎉 Initial Release
This is the first open source release of Full Screen Calendar Reminder.

### ✨ Added
- **Core Functionality**
  - Automatic 1-minute reminders for all calendar events
  - Full-screen reminder alerts with gradual audio fade-in
  - Support for all calendar providers (Google, Exchange, Samsung, etc.)
  - Proper handling of recurring events through Android's Instances API
  - Background monitoring with 1-minute intervals using WorkManager

- **Calendar Integration**
  - Read access to all calendar events across multiple accounts
  - Efficient caching system (30-second event cache, 5-minute calendar list cache)
  - 30-day lookahead for upcoming events
  - Compatible with Android's CalendarContract API

- **User Interface**
  - Clean, Material Design-compliant interface
  - Event list display with calendar color coding
  - Permission management and setup guidance
  - Battery optimization guidance for reliable operation

- **Background Processing**
  - WorkManager-based reliable background monitoring
  - AlarmManager integration for precise reminder scheduling
  - Boot receiver for automatic restart after device reboot
  - Doze mode and battery optimization compatibility

- **Security & Privacy**
  - Local-only data processing (no network access)
  - Minimal permission requirements
  - Transparent open source codebase
  - No data collection or external transmission

### 🛠️ Technical Features
- **Architecture**: MVVM pattern with dependency injection concepts
- **Language**: Kotlin 2.0.21 with modern coroutines
- **Compatibility**: Android 5.0+ (API 21) to Android 14+ (API 34)
- **Dependencies**: Minimal external dependencies focused on Android Jetpack
- **Build System**: Gradle with Kotlin DSL

### 📱 Supported Platforms
- **Android Versions**: 5.0 (API 21) through 14+ (API 34)
- **Calendar Providers**: Google Calendar, Microsoft Exchange, Samsung Calendar, any CalendarContract-compatible provider
- **Device Types**: Phones and tablets with calendar access

### 🔧 Development Environment
- **Build Tools**: Gradle 8.7, Android Gradle Plugin 8.5.2
- **Target SDK**: 34 (Android 14)
- **Minimum SDK**: 21 (Android 5.0)
- **Compile SDK**: 34

### 📚 Documentation
- Comprehensive README with installation and usage instructions
- Contributing guidelines for developers
- Architecture documentation explaining system design
- Build and deployment guide
- Privacy policy and security documentation
- GitHub issue templates and pull request guidelines

---

## Version History Format

### Types of Changes
- **Added** for new features
- **Changed** for changes in existing functionality
- **Deprecated** for soon-to-be removed features
- **Removed** for now removed features
- **Fixed** for any bug fixes
- **Security** for vulnerability fixes

### Semantic Versioning
- **MAJOR** version for incompatible API changes
- **MINOR** version for backwards-compatible functionality additions
- **PATCH** version for backwards-compatible bug fixes

### Release Links
[Unreleased]: https://github.com/realTeddy/FullScreenCalenderReminer/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/realTeddy/FullScreenCalenderReminer/releases/tag/v1.0.0