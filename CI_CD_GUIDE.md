# 🚀 CI/CD Pipeline Documentation

## Overview
This project uses **GitHub Actions** for Continuous Integration and Continuous Deployment (CI/CD). The pipeline automatically builds, tests, and analyzes code quality for every push and pull request.

## 📋 Pipeline Structure

### 🔧 Workflows

#### 1. Android CI (`android-ci.yml`)
**Triggers**: Push/PR to `main` or `develop` branches
- **Lint Analysis**: Runs Android lint checks
- **Unit Tests**: Executes all unit tests with coverage
- **Build**: Compiles debug APK
- **Security Scan**: Vulnerability scanning with Trivy
- **Artifacts**: Uploads test reports and APKs

#### 2. Code Quality (`code-quality.yml`)
**Triggers**: Push/PR to `main` or `develop` branches
- **ktlint**: Kotlin code style checking
- **Detekt**: Static code analysis for Kotlin
- **SonarCloud**: Code quality and security analysis
- **Dependency Check**: Vulnerability scanning for dependencies

#### 3. Android CD (`android-cd.yml`)
**Triggers**: Git tags starting with `v*` (e.g., `v1.0.0`)
- **Release Build**: Creates signed release APK and AAB
- **GitHub Release**: Automatically creates GitHub release
- **Play Store**: Optional automatic deployment to Google Play

## 🛠️ Setup Instructions

### 1. Repository Secrets
Configure these secrets in your GitHub repository settings:

#### For Release Signing:
```
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

#### For Code Quality (Optional):
```
SONAR_TOKEN=your_sonarcloud_token
```

#### For Play Store Deployment:
```
SERVICE_ACCOUNT_JSON=your_google_play_service_account_json
```
📖 **Detailed Setup**: See [GOOGLE_PLAY_DEPLOYMENT.md](GOOGLE_PLAY_DEPLOYMENT.md) for complete Google Play setup instructions.

### 2. Keystore Setup
Place your release keystore file in the project root or configure signing in `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("KEYSTORE_FILE") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
}
```

### 3. SonarCloud Integration (Optional)
1. Sign up at [SonarCloud.io](https://sonarcloud.io)
2. Create a new project for your repository
3. Generate a SonarCloud token
4. Add the token as `SONAR_TOKEN` secret
5. Create `sonar-project.properties` in root:

```properties
sonar.projectKey=your_organization_your_project
sonar.organization=your_organization
sonar.host.url=https://sonarcloud.io
sonar.login=${SONAR_TOKEN}
sonar.sources=app/src/main
sonar.tests=app/src/test
sonar.android.lint.reportPaths=app/build/reports/lint-results-debug.xml
sonar.coverage.jacoco.xmlReportPaths=app/build/reports/jacoco/test/jacocoTestReport.xml
```

## 📱 Local Development

### Running Quality Checks Locally
```bash
# Run all checks
./gradlew check

# Individual checks
./gradlew ktlintCheck
./gradlew detekt
./gradlew lintDebug
./gradlew testDebugUnitTest

# Auto-fix formatting issues
./gradlew ktlintFormat
```

### Test Coverage
```bash
# Generate coverage report
./gradlew jacocoTestReport

# View report
open app/build/reports/jacoco/test/jacocoTestReport.html
```

## 🎯 Workflow Details

### Build Matrix
The CI pipeline runs on:
- **OS**: Ubuntu Latest
- **JDK**: OpenJDK 17 (Temurin distribution)
- **Android SDK**: Latest stable

### Caching Strategy
- **Gradle**: Caches dependencies and build cache
- **Android SDK**: Cached between builds
- **Cache Key**: Based on Gradle files hash

### Artifact Management
- **Debug APKs**: Available for 30 days
- **Test Reports**: HTML and XML formats
- **Lint Reports**: Detailed analysis results
- **Coverage Reports**: JaCoCo XML/HTML format

## 🚀 Release Process

### Automated Releases
1. **Tag Creation**: Create and push a git tag
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. **Automatic Process**:
   - Builds release APK and AAB
   - Signs with release keystore
   - Extracts changelog from CHANGELOG.md
   - Creates GitHub release with artifacts
   - Optionally deploys to Play Store

### Manual Release Steps
If you prefer manual releases:
1. Disable the CD workflow
2. Build locally: `./gradlew assembleRelease bundleRelease`
3. Test the APK thoroughly
4. Create GitHub release manually
5. Upload to Play Store console

## 📊 Quality Gates

### Required Checks
- ✅ All unit tests pass
- ✅ Lint checks pass
- ✅ Code coverage > 70% (configurable)
- ✅ No security vulnerabilities
- ✅ Code style compliance (ktlint)
- ✅ Static analysis passes (detekt)

### Branch Protection
Recommended branch protection rules for `main`:
- Require status checks to pass
- Require branches to be up to date
- Require review from code owners
- Restrict pushes to administrators

## 🔍 Monitoring & Debugging

### Build Logs
- **GitHub Actions**: Check the Actions tab for detailed logs
- **Failed Builds**: Review individual job logs for errors
- **Artifacts**: Download test reports and APKs from build artifacts

### Common Issues
1. **Signing Failures**: Check keystore secrets configuration
2. **Test Failures**: Review test reports in artifacts
3. **Lint Errors**: Check lint reports for specific issues
4. **Coverage Drop**: Review coverage reports for missing tests

### Performance Monitoring
- **Build Time**: Monitored automatically
- **Cache Hit Rate**: Visible in build logs
- **Test Execution Time**: Tracked per test suite

## 🎨 Customization

### Adding New Checks
1. Modify workflow files in `.github/workflows/`
2. Add new Gradle tasks or tools
3. Update quality gates accordingly

### Environment-Specific Builds
```yaml
strategy:
  matrix:
    environment: [staging, production]
    api-level: [21, 34]
```

### Custom Notifications
Add Slack/Discord notifications:
```yaml
- name: Notify on failure
  if: failure()
  uses: 8398a7/action-slack@v3
  with:
    status: failure
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

## 📈 Metrics & Analytics

### Available Metrics
- **Build Success Rate**: Track via GitHub Actions
- **Test Coverage**: JaCoCo reports
- **Code Quality**: SonarCloud dashboard
- **Security Score**: Dependency and code vulnerability scans
- **Performance**: Build time trends

### Badges
Add these badges to your README:
```markdown
![Build Status](https://github.com/username/repo/actions/workflows/android-ci.yml/badge.svg)
![Code Coverage](https://codecov.io/gh/username/repo/branch/main/graph/badge.svg)
![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=key&metric=alert_status)
```

## 🔐 Security Considerations

### Secret Management
- Use GitHub Secrets for sensitive data
- Rotate keys regularly
- Use environment-specific secrets
- Audit secret access logs

### Dependency Security
- Automated vulnerability scanning
- Regular dependency updates
- Security-focused linting rules
- SAST (Static Application Security Testing)

### Access Control
- Limit who can create releases
- Require signed commits
- Use branch protection rules
- Enable 2FA for maintainers

---

This CI/CD setup provides enterprise-grade automation for your Android project with comprehensive testing, quality assurance, and deployment capabilities.