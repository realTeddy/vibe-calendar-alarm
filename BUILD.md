# 🔨 Build and Deployment Guide

## Overview

This document provides comprehensive instructions for building, testing, and deploying the Full Screen Calendar Reminder application. Whether you're a contributor setting up a development environment or a maintainer preparing a release, this guide covers all necessary steps.

## 📋 Prerequisites

### System Requirements
- **Operating System**: Windows 10/11, macOS 10.14+, or Linux (Ubuntu 18.04+)
- **RAM**: Minimum 8GB, recommended 16GB for optimal Android Studio performance
- **Storage**: At least 10GB free space for Android SDK and project files
- **Internet**: Required for downloading dependencies and SDK components

### Required Software

#### 1. Java Development Kit (JDK)
```bash
# Check if JDK 11+ is installed
java -version
javac -version

# Download from: https://adoptium.net/
# Ensure JAVA_HOME environment variable is set
```

#### 2. Android Studio
- **Download**: [Android Studio](https://developer.android.com/studio)
- **Version**: Arctic Fox (2020.3.1) or newer recommended
- **Components Required**:
  - Android SDK Platform API 21 (Android 5.0) - Minimum
  - Android SDK Platform API 34 (Android 14) - Target
  - Android SDK Build-Tools 34.0.0+
  - Android Emulator (for testing)

#### 3. Git Version Control
```bash
# Install Git
# Windows: https://git-scm.com/download/win
# macOS: brew install git
# Linux: sudo apt install git

# Verify installation
git --version
```

#### 4. Android SDK Configuration
```bash
# Set environment variables (add to ~/.bashrc or ~/.zshrc)
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

## 🚀 Development Environment Setup

### 1. Clone the Repository
```bash
# Clone the main repository
git clone https://github.com/realTeddy/FullScreenCalenderReminer.git
cd FullScreenCalenderReminer

# Verify project structure
ls -la
# Should see: app/, gradle/, build.gradle.kts, etc.
```

### 2. Android Studio Setup
```bash
# Open Android Studio
# File → Open → Select FullScreenCalenderReminer folder
# Wait for Gradle sync to complete

# Install missing SDK components if prompted
# Tools → SDK Manager → Install required platforms and tools
```

### 3. Gradle Configuration
```bash
# Verify Gradle wrapper
./gradlew --version

# Download dependencies
./gradlew dependencies

# Verify project builds
./gradlew clean build
```

### 4. Device/Emulator Setup
```bash
# Connect physical device
adb devices

# Or create virtual device
# Tools → AVD Manager → Create Virtual Device
# Choose device with API 21+ (recommended: Pixel 4, API 30+)
```

## 🔧 Build Configuration

### Build Types
The project supports multiple build configurations:

#### Debug Build
```gradle
buildTypes {
    debug {
        applicationIdSuffix ".debug"
        debuggable true
        minifyEnabled false
        // Test-friendly configuration
    }
}
```

#### Release Build
```gradle
buildTypes {
    release {
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        // Optimized for production
    }
}
```

### Build Commands

#### Basic Build Commands
```bash
# Clean build (removes all build artifacts)
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing configuration)
./gradlew assembleRelease

# Build all variants
./gradlew assemble

# Install debug APK to connected device
./gradlew installDebug
```

#### Advanced Build Commands
```bash
# Build with verbose logging
./gradlew assembleDebug --info

# Build with performance profiling
./gradlew assembleDebug --profile

# Build specific ABI (for size optimization)
./gradlew assembleDebug -PsplitApk=true

# Generate build reports
./gradlew assembleDebug --scan
```

## 🧪 Testing

### Unit Testing
```bash
# Run all unit tests
./gradlew test

# Run tests with coverage report
./gradlew testDebugUnitTestCoverage

# Run specific test class
./gradlew test --tests CalendarManagerTest

# Run tests with detailed output
./gradlew test --info

# Generate HTML test report
# Results available at: app/build/reports/tests/testDebugUnitTest/index.html
```

### Instrumented Testing
```bash
# Ensure device/emulator is connected
adb devices

# Run all instrumented tests
./gradlew connectedAndroidTest

# Run tests on specific device
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.SpecificTest

# Run UI tests with screenshots
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.screenshot=true
```

### Lint and Code Quality
```bash
# Run Android Lint
./gradlew lint

# Generate lint report
# Report available at: app/build/reports/lint-results.html

# Run static analysis
./gradlew check

# Verify code style (if configured)
./gradlew ktlintCheck
```

### Test Coverage
```bash
# Generate test coverage report
./gradlew createDebugCoverageReport

# Open coverage report
# File: app/build/reports/coverage/debug/index.html

# Coverage goals:
# - Minimum: 70% line coverage
# - Target: 85% overall coverage
# - Critical components: 90%+ coverage
```

## 📦 Release Preparation

### 1. Version Management
Update version information in `app/build.gradle.kts`:
```gradle
android {
    defaultConfig {
        versionCode = 10001 // Increment for each release
        versionName = "1.0.1" // Semantic versioning
    }
}
```

### 2. Signing Configuration
Create keystore for release signing:
```bash
# Generate release keystore (one-time setup)
keytool -genkey -v -keystore fullscreen-calendar-reminder.keystore \
        -alias release-key -keyalg RSA -keysize 2048 -validity 10000

# Add to app/build.gradle.kts
android {
    signingConfigs {
        release {
            storeFile file('fullscreen-calendar-reminder.keystore')
            storePassword = project.hasProperty('KEYSTORE_PASSWORD') ? KEYSTORE_PASSWORD : ''
            keyAlias = 'release-key'
            keyPassword = project.hasProperty('KEY_PASSWORD') ? KEY_PASSWORD : ''
        }
    }
}
```

### 3. ProGuard/R8 Optimization
Configure code optimization in `proguard-rules.pro`:
```proguard
# Keep calendar-related classes
-keep class me.tewodros.vibecalendaralarm.model.** { *; }
-keep class me.tewodros.vibecalendaralarm.CalendarManager { *; }

# Keep alarm receiver for system callbacks
-keep class me.tewodros.vibecalendaralarm.AlarmReceiver { *; }

# Keep activity classes
-keep class me.tewodros.vibecalendaralarm.*Activity { *; }
```

### 4. Release Build Process
```bash
# Set signing credentials (don't commit these!)
export KEYSTORE_PASSWORD=your_keystore_password
export KEY_PASSWORD=your_key_password

# Clean previous builds
./gradlew clean

# Run all tests
./gradlew test connectedAndroidTest

# Generate release APK
./gradlew assembleRelease

# Verify APK
./gradlew verifyReleaseResources

# APK location: app/build/outputs/apk/release/app-release.apk
```

## 🚀 Deployment

### APK Distribution
```bash
# Verify APK size and contents
ls -lh app/build/outputs/apk/release/
aapt dump badging app/build/outputs/apk/release/app-release.apk

# Test installation on clean device
adb install app/build/outputs/apk/release/app-release.apk

# Generate APK for GitHub releases
cp app/build/outputs/apk/release/app-release.apk FullScreenCalendarReminder-v1.0.1.apk
```

### GitHub Release Process
1. **Tag the Release**:
```bash
git tag -a v1.0.1 -m "Release version 1.0.1"
git push origin v1.0.1
```

2. **Create GitHub Release**:
- Go to GitHub repository → Releases → New Release
- Select the version tag
- Add release notes
- Upload APK file
- Mark as latest release

3. **Release Notes Template**:
```markdown
## 🚀 Full Screen Calendar Reminder v1.0.1

### ✨ New Features
- Feature 1 description
- Feature 2 description

### 🐛 Bug Fixes
- Fixed issue with alarm scheduling
- Resolved permission handling edge case

### 🔧 Improvements
- Performance optimizations
- UI/UX enhancements

### 📱 Compatibility
- Android 5.0+ (API 21+)
- Tested on Android 14

### 📥 Installation
1. Download the APK below
2. Enable "Install from unknown sources"
3. Install and grant required permissions

**Full Changelog**: https://github.com/user/repo/compare/v1.0.0...v1.0.1
```

## 🔍 Quality Assurance

### Pre-Release Testing Checklist
- [ ] **Build Quality**
  - [ ] Clean build successful
  - [ ] All unit tests pass
  - [ ] All instrumented tests pass
  - [ ] Lint checks pass with no critical issues
  - [ ] APK size within reasonable limits

- [ ] **Functionality Testing**
  - [ ] Calendar events load correctly
  - [ ] Alarm scheduling works on multiple devices
  - [ ] Full-screen reminders display properly
  - [ ] Permission flows work as expected
  - [ ] Battery optimization guidance functional

- [ ] **Device Compatibility**
  - [ ] Test on Android 5.0 (minimum supported)
  - [ ] Test on Android 14 (target version)
  - [ ] Test on different screen sizes
  - [ ] Test with different calendar providers

- [ ] **Performance Testing**
  - [ ] App startup time < 3 seconds
  - [ ] Calendar query time < 5 seconds
  - [ ] Memory usage within acceptable limits
  - [ ] Battery usage optimized

### Post-Release Monitoring
```bash
# Monitor crash reports (if integrated)
# Monitor user feedback on GitHub issues
# Track download statistics
# Monitor performance metrics
```

## 🛠️ Troubleshooting

### Common Build Issues

#### Gradle Sync Failures
```bash
# Clear Gradle cache
./gradlew clean
rm -rf ~/.gradle/caches/

# Restart Gradle daemon
./gradlew --stop
./gradlew build
```

#### SDK Issues
```bash
# Update SDK components
# Android Studio → Tools → SDK Manager → Update All

# Check SDK path
echo $ANDROID_HOME
# Should point to Android SDK directory
```

#### Dependency Conflicts
```bash
# View dependency tree
./gradlew app:dependencies

# Resolve conflicts in build.gradle.kts
dependencies {
    implementation("com.example:library:1.0.0") {
        exclude group: 'conflicting.group', module: 'conflicting-module'
    }
}
```

#### Memory Issues
```bash
# Increase Gradle memory in gradle.properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
```

### Testing Issues

#### Emulator Problems
```bash
# Cold boot emulator
emulator -avd YourAVD -cold-boot

# Wipe emulator data
emulator -avd YourAVD -wipe-data

# Check emulator resources
emulator -list-avds
```

#### Device Connection Issues
```bash
# Restart ADB
adb kill-server
adb start-server

# Check device authorization
adb devices
# Should show "device" not "unauthorized"
```

## 📚 Additional Resources

### Documentation
- [Android Developer Guide](https://developer.android.com/guide)
- [Gradle Build Tool](https://gradle.org/guides/)
- [Kotlin Documentation](https://kotlinlang.org/docs/)

### Tools and Utilities
- [APK Analyzer](https://developer.android.com/studio/debug/apk-analyzer)
- [Memory Profiler](https://developer.android.com/studio/profile/memory-profiler)
- [Network Profiler](https://developer.android.com/studio/profile/network-profiler)

### Community Support
- [Stack Overflow - Android](https://stackoverflow.com/questions/tagged/android)
- [Reddit - Android Dev](https://reddit.com/r/androiddev)
- [Android Developers Community](https://developer.android.com/community)

---

This build and deployment guide ensures consistent, reliable builds and releases. For questions or issues not covered here, please refer to the [Contributing Guide](CONTRIBUTING.md) or create an issue on GitHub.
