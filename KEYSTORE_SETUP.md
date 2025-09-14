# 🔐 Keystore Setup for GitHub Actions

## Step-by-Step Instructions

### 1. Create Your Keystore (if you don't have one)

Run this command in your project directory:

```bash
keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release_key
```

**When prompted, provide:**
- **Keystore password**: Choose a strong password (remember this!)
- **Key password**: Choose a strong password (can be same as keystore password)
- **First and last name**: Your name or app name
- **Organizational unit**: Your company/team
- **Organization**: Your company name
- **City/Locality**: Your city
- **State/Province**: Your state
- **Country code**: Your country (e.g., US, UK, etc.)

**Example values to remember:**
```
KEYSTORE_PASSWORD=MySecurePassword123!
KEY_ALIAS=release_key
KEY_PASSWORD=MyKeyPassword456!
```

### 2. Convert Keystore to Base64

**On Windows (PowerShell):**
```powershell
cd "d:\repos\FullScreenCalenderReminer"
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore.jks")) | Out-File -Encoding ASCII keystore-base64.txt
Get-Content keystore-base64.txt
```

**On Linux/Mac:**
```bash
base64 -i keystore.jks -o keystore-base64.txt
cat keystore-base64.txt
```

### 3. Add GitHub Secrets

1. Go to: https://github.com/realTeddy/vibe-calendar-alarm/settings/secrets/actions
2. Click "New repository secret"
3. Add these 4 secrets:

| Secret Name | Value |
|-------------|-------|
| `KEYSTORE_BASE64` | Paste the entire base64 string from keystore-base64.txt |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | `release_key` (or whatever you used) |
| `KEY_PASSWORD` | Your key password |

### 4. Test the Setup

1. **Create a git tag:**
```bash
git tag v1.0.0
git push origin v1.0.0
```

2. **Check GitHub Actions:**
   - Go to: https://github.com/realTeddy/vibe-calendar-alarm/actions
   - Look for the "Android CD - Release" workflow
   - It should automatically build and create a release

### 5. Security Notes

- ✅ **DO NOT** commit your `keystore.jks` file to git
- ✅ **DO NOT** share your keystore passwords
- ✅ **DO** backup your keystore file securely
- ✅ **DO** store passwords in a password manager
- ✅ The GitHub secrets are encrypted and only accessible to your repository

### 6. File Cleanup

After setting up GitHub secrets, you can safely delete:
- `keystore-base64.txt`
- Keep `keystore.jks` as a backup (but DON'T commit it)

### 7. What Happens in GitHub Actions

When you push a tag (like `v1.0.0`):
1. GitHub Actions downloads your code
2. Decodes the base64 keystore to `app/keystore.jks`
3. Uses your stored passwords to sign the APK/AAB
4. Creates a GitHub release with the signed files
5. Optionally uploads to Google Play Store
6. Cleans up the keystore file for security

## ✅ You're All Set!

Your CI/CD pipeline will now automatically:
- Build signed release APKs and AABs
- Create GitHub releases
- Upload to Google Play Store (if configured)

Just push version tags like `v1.0.1`, `v1.1.0`, etc. to trigger releases!