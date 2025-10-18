# Versioning Strategy

## Version Code and Version Name

This project uses an automated versioning strategy for Google Play Store releases.

### Version Code
- **Automatically calculated** from the number of git tags
- Each new tag increments the version code
- Required by Google Play Store to be unique and incrementing
- Example: 
  - First tag (v1.0.0) → versionCode = 1
  - Second tag (v1.1.0) → versionCode = 2
  - Third tag (v2.0.0) → versionCode = 3

### Version Name
- **Extracted from git tag** (removes 'v' prefix)
- User-facing version displayed in Play Store
- Example: tag `v1.2.3` → versionName = "1.2.3"

## Creating a New Release

1. **Update CHANGELOG.md** with release notes for the new version

2. **Create and push a new tag:**
   ```bash
   git tag -a v1.1.0 -m "Release version 1.1.0"
   git push origin v1.1.0
   ```

3. **GitHub Actions will automatically:**
   - Calculate the version code based on tag count
   - Extract version name from the tag
   - Build release APK and AAB
   - Create GitHub release
   - Upload to Google Play Store (internal track)

## Version Naming Convention

Follow semantic versioning: `vMAJOR.MINOR.PATCH`

- **MAJOR**: Incompatible API changes or major redesign
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

Examples:
- `v1.0.0` - Initial release
- `v1.1.0` - New feature added
- `v1.1.1` - Bug fix
- `v2.0.0` - Major update with breaking changes

## Troubleshooting

### "Version code X has already been used"
This error occurs when:
- You're trying to re-upload the same tag
- The version code hasn't incremented

**Solution:**
- Delete the old tag locally and remotely
- Create a new tag with a higher version number

### Manual Version Override (Advanced)
In CI/CD, you can set environment variables:
```yaml
env:
  VERSION_CODE: 10
  VERSION_NAME: "1.5.0"
```

## Local Development

When building locally without tags:
- Version code defaults to 1
- Version name defaults to "1.0.0"

To test versioning locally:
```bash
./gradlew assembleRelease
```

The build will use the latest git tag or defaults if no tags exist.

## Testing the Versioning System

Three PowerShell test scripts are provided to validate the versioning system:

### 1. Basic Versioning Tests
```powershell
.\test-versioning.ps1
```
Tests version code/name handling with different environment variable scenarios.

### 2. GitHub Actions Simulation
```powershell
.\test-github-actions.ps1
```
Simulates the complete GitHub Actions CD workflow locally, including:
- Version extraction from git tags
- Environment variable setup
- APK and AAB builds

### 3. Build Artifact Verification
```powershell
.\verify-build.ps1
```
Verifies the version information in built APK/AAB files and displays git tag status.

**Run all tests before pushing a new tag** to ensure the workflow will succeed.
