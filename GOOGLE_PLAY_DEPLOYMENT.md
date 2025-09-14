# 🏪 Google Play Store Deployment Guide

## Overview
This guide explains how to set up automated Google Play Store deployment using GitHub Actions for your Full Screen Calendar Reminder app.

## 🎯 Deployment Tracks

Google Play offers different tracks for app deployment:

| Track | Purpose | Audience | Rollout |
|-------|---------|----------|---------|
| **Internal** | Testing with team | Up to 100 internal testers | Immediate |
| **Alpha** | Closed testing | Invited testers only | Controlled |
| **Beta** | Open/Closed testing | Broader testing group | Staged |
| **Production** | Live release | All users | Gradual rollout |

## 🔧 Setup Process

### Step 1: Google Play Console Setup

#### 1.1 Create App in Play Console
1. Go to [Google Play Console](https://play.google.com/console)
2. Create a new app or select existing
3. Complete app information (title, description, etc.)
4. Upload initial APK/AAB manually for first release

#### 1.2 Enable Google Play Developer API
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project or select existing
3. Enable "Google Play Developer API"
4. Go to "APIs & Services" → "Credentials"

#### 1.3 Create Service Account
```bash
# In Google Cloud Console:
# 1. APIs & Services → Credentials → Create Credentials → Service Account
# 2. Name: "github-actions-play-deploy"
# 3. Grant "Service Account User" role
# 4. Create and download JSON key file
```

#### 1.4 Grant Play Console Access
1. In Google Play Console → "Setup" → "API access"
2. Link your Google Cloud project
3. Grant access to your service account
4. Set permissions:
   - ✅ View app information and download bulk reports
   - ✅ Manage production releases
   - ✅ Manage testing track releases
   - ✅ Manage app content

### Step 2: GitHub Repository Setup

#### 2.1 Add Service Account Secret
```bash
# In your GitHub repository:
# Settings → Secrets and variables → Actions → New repository secret

# Secret name: SERVICE_ACCOUNT_JSON
# Secret value: [Paste the entire JSON content from downloaded file]
```

#### 2.2 Configure App Signing
Create or update `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("KEYSTORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    bundle {
        storeArchive {
            enable = false
        }
    }
}
```

### Step 3: Release Notes Setup

#### 3.1 Create Release Notes Directory
```bash
mkdir -p distribution/whatsnew
```

#### 3.2 Add Language-Specific Release Notes
```bash
# distribution/whatsnew/whatsnew-en-US
echo "• Fixed calendar sync issues
• Improved full-screen reminder display
• Enhanced battery optimization compatibility
• Bug fixes and performance improvements" > distribution/whatsnew/whatsnew-en-US
```

### Step 4: Advanced Configuration

#### 4.1 Create Multiple Track Deployment
```yaml
# .github/workflows/android-deploy-internal.yml
name: Deploy to Internal Testing

on:
  push:
    branches: [ develop ]

jobs:
  deploy-internal:
    runs-on: ubuntu-latest
    steps:
      # ... build steps ...
      
      - name: Deploy to Internal Track
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.SERVICE_ACCOUNT_JSON }}
          packageName: me.tewodros.fullscreencalenderreminder
          releaseFiles: app/build/outputs/bundle/release/app-release.aab
          track: internal
          status: completed
```

#### 4.2 Staged Production Deployment
```yaml
# For production releases with gradual rollout
- name: Deploy to Production (Staged)
  uses: r0adkll/upload-google-play@v1
  with:
    serviceAccountJsonPlainText: ${{ secrets.SERVICE_ACCOUNT_JSON }}
    packageName: me.tewodros.fullscreencalenderreminder
    releaseFiles: app/build/outputs/bundle/release/app-release.aab
    track: production
    status: inProgress  # Starts rollout
    userFraction: 0.1   # Start with 10% of users
    whatsNewDirectory: distribution/whatsnew
```

## 🚀 Deployment Workflows

### Automatic Deployment Strategy

```yaml
# Recommended deployment pipeline:

# 1. develop branch → Internal track (automatic)
on:
  push:
    branches: [ develop ]
  # Deploys to internal track for team testing

# 2. release branches → Beta track (automatic)
on:
  push:
    branches: [ release/* ]
  # Deploys to beta track for broader testing

# 3. Tags → Production track (automatic)
on:
  push:
    tags: [ 'v*' ]
  # Deploys to production with gradual rollout
```

### Manual Deployment Options

#### Option 1: GitHub Actions Manual Trigger
```yaml
on:
  workflow_dispatch:
    inputs:
      track:
        description: 'Deployment track'
        required: true
        default: 'internal'
        type: choice
        options:
        - internal
        - alpha
        - beta
        - production
      rollout_percentage:
        description: 'Rollout percentage (for production)'
        required: false
        default: '100'
```

#### Option 2: Draft Releases
```yaml
# Deploy only when GitHub release is published (not draft)
on:
  release:
    types: [ published ]
```

## 📱 App Bundle Requirements

### Enable App Bundles
Google Play requires Android App Bundles (AAB) for new apps:

```kotlin
// app/build.gradle.kts
android {
    bundle {
        storeArchive {
            enable = false  // Don't include original APK in bundle
        }
        language {
            enableSplit = true  // Enable language splits
        }
        density {
            enableSplit = true  // Enable density splits
        }
        abi {
            enableSplit = true  // Enable ABI splits
        }
    }
}
```

### Build Command
```bash
# Build release AAB
./gradlew bundleRelease

# Output location
app/build/outputs/bundle/release/app-release.aab
```

## 🔐 Security Best Practices

### Service Account Security
- 🔒 **Minimal Permissions**: Only grant necessary Play Console permissions
- 🔑 **Key Rotation**: Rotate service account keys regularly
- 📝 **Audit Logs**: Monitor API usage in Google Cloud Console
- 🚫 **No Local Storage**: Never commit service account JSON to code

### Repository Secrets
```bash
# Required secrets for deployment:
SERVICE_ACCOUNT_JSON={"type":"service_account",...}
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_release_key_alias
KEY_PASSWORD=your_key_password

# Optional for advanced features:
PLAY_CONSOLE_USER_EMAIL=your-email@domain.com
```

## 📊 Monitoring & Rollback

### Deployment Monitoring
```yaml
- name: Check Deployment Status
  run: |
    echo "Deployment completed to track: ${{ inputs.track }}"
    echo "Version: ${{ github.ref_name }}"
    echo "Release notes updated"

- name: Notify Team
  uses: 8398a7/action-slack@v3
  if: success()
  with:
    status: success
    text: "✅ App deployed to Play Store (${{ inputs.track }} track)"
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

### Emergency Rollback
```bash
# Manual rollback using gcloud CLI
gcloud alpha android-publisher editions rollback \
  --package-name=me.tewodros.fullscreencalenderreminder \
  --track=production
```

## 🐛 Troubleshooting

### Common Issues

#### 1. Authentication Errors
```
Error: The caller does not have permission
```
**Solution**: Check service account permissions in Play Console

#### 2. Version Code Issues
```
Error: Version code X has already been used
```
**Solution**: Ensure `versionCode` is incremented in `build.gradle.kts`

#### 3. App Bundle Validation
```
Error: App bundle contains invalid files
```
**Solution**: Check ProGuard rules and excluded files

#### 4. Release Notes Missing
```
Warning: No release notes provided
```
**Solution**: Add release notes in `distribution/whatsnew/whatsnew-en-US`

### Debug Commands
```bash
# Test AAB locally
bundletool build-apks --bundle=app-release.aab --output=app.apks

# Validate AAB
bundletool validate --bundle=app-release.aab

# Check AAB contents
unzip -l app-release.aab
```

## 📈 Advanced Features

### A/B Testing
```yaml
- name: Deploy A/B Test
  uses: r0adkll/upload-google-play@v1
  with:
    serviceAccountJsonPlainText: ${{ secrets.SERVICE_ACCOUNT_JSON }}
    packageName: me.tewodros.fullscreencalenderreminder
    releaseFiles: app/build/outputs/bundle/release/app-release.aab
    track: production
    status: inProgress
    userFraction: 0.5  # Split traffic 50/50
```

### In-App Updates
Enable in-app updates for better user experience:

```kotlin
// Add to dependencies
implementation 'com.google.android.play:app-update:2.1.0'
implementation 'com.google.android.play:app-update-ktx:2.1.0'
```

### Play Console Integration
- 📊 **Crash Reports**: Automatic crash reporting integration
- 📈 **Performance Monitoring**: ANR and performance metrics
- 💬 **User Reviews**: Automated review response workflows
- 📱 **Device Compatibility**: Automatic device exclusion rules

---

This complete setup enables fully automated Google Play Store deployment with proper security, monitoring, and rollback capabilities!