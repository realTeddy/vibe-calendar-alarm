# Build and Install Wear OS Apps
# This script builds and installs both phone and watch apps

Write-Host "🚀 Building Vibe Calendar Alarm - Phone + Wear OS" -ForegroundColor Cyan
Write-Host ""

# Build phone app
Write-Host "📱 Building phone app..." -ForegroundColor Yellow
./gradlew :app:assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Phone app build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Phone app built successfully" -ForegroundColor Green
Write-Host ""

# Build watch app
Write-Host "⌚ Building watch app..." -ForegroundColor Yellow
./gradlew :wear:assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Watch app build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Watch app built successfully" -ForegroundColor Green
Write-Host ""

# Check connected devices
Write-Host "🔍 Checking connected devices..." -ForegroundColor Yellow
$devices = adb devices | Select-String -Pattern "^\w+\s+device$"
$deviceCount = ($devices | Measure-Object).Count

if ($deviceCount -eq 0) {
    Write-Host "⚠️  No devices connected!" -ForegroundColor Red
    Write-Host "   Please connect phone and/or watch via ADB" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "   For Wear OS watch:" -ForegroundColor Cyan
    Write-Host "   adb connect <watch-ip>:5555" -ForegroundColor White
    exit 1
}

Write-Host "✅ Found $deviceCount device(s)" -ForegroundColor Green
adb devices
Write-Host ""

# Install phone app
Write-Host "📱 Installing phone app..." -ForegroundColor Yellow
$phoneInstalled = $false
foreach ($device in $devices) {
    $serial = ($device -split '\s+')[0]
    Write-Host "   Installing on device: $serial" -ForegroundColor Cyan
    adb -s $serial install -r app\build\outputs\apk\debug\app-debug.apk
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✅ Installed on $serial" -ForegroundColor Green
        $phoneInstalled = $true
    } else {
        Write-Host "   ⚠️  Failed to install on $serial" -ForegroundColor Yellow
    }
}
Write-Host ""

# Install watch app
Write-Host "⌚ Installing watch app..." -ForegroundColor Yellow
$watchInstalled = $false
foreach ($device in $devices) {
    $serial = ($device -split '\s+')[0]
    Write-Host "   Installing on device: $serial" -ForegroundColor Cyan
    adb -s $serial install -r wear\build\outputs\apk\debug\wear-debug.apk
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✅ Installed on $serial" -ForegroundColor Green
        $watchInstalled = $true
    } else {
        Write-Host "   ⚠️  Failed to install on $serial (may not be a watch)" -ForegroundColor Yellow
    }
}
Write-Host ""

# Summary
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "📊 Installation Summary" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Cyan
if ($phoneInstalled) {
    Write-Host "✅ Phone app installed" -ForegroundColor Green
} else {
    Write-Host "⚠️  Phone app not installed" -ForegroundColor Yellow
}

if ($watchInstalled) {
    Write-Host "✅ Watch app installed" -ForegroundColor Green
} else {
    Write-Host "⚠️  Watch app not installed" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "🎯 Next Steps:" -ForegroundColor Cyan
Write-Host "   1. Open Vibe Calendar Alarm on phone" -ForegroundColor White
Write-Host "   2. Grant all permissions" -ForegroundColor White
Write-Host "   3. Schedule a test reminder" -ForegroundColor White
Write-Host "   4. Check watch for synchronized reminder" -ForegroundColor White
Write-Host ""
Write-Host "📖 For detailed setup, see: WEAR_OS_SETUP.md" -ForegroundColor Yellow
Write-Host ""
Write-Host "🎉 Build complete!" -ForegroundColor Green
