param(
    [switch]$Install = $false,
    [switch]$Clean = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "     Hermes Any AI -- Build Release APK On Demand         " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Parse version from app/build.gradle.kts
$versionName = "unknown"
$versionCode = "unknown"

$lines = Get-Content "app/build.gradle.kts"
foreach ($line in $lines) {
    if ($line -match 'versionName\s*=\s*"([^"]+)"') {
        $versionName = $matches[1]
    }
    if ($line -match 'versionCode\s*=\s*(\d+)') {
        $versionCode = $matches[1]
    }
}

Write-Host ">>> Target Release: v$versionName (versionCode: $versionCode)" -ForegroundColor Yellow

# 2. Build
if ($Clean) {
    Write-Host ">>> Running clean..." -ForegroundColor Gray
    .\gradlew.bat clean
}

Write-Host ">>> Running Gradle assembleRelease..." -ForegroundColor Cyan
.\gradlew.bat assembleRelease

if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle assembleRelease failed!"
    exit $LASTEXITCODE
}

# 3. Locate generated APK
$releaseDir = "app/build/outputs/apk/release"
$apk = Get-ChildItem -Path $releaseDir -Filter "HermesAnyAI_v*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $apk) {
    $apk = Get-ChildItem -Path $releaseDir -Filter "*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

if (-not $apk) {
    Write-Error "Release APK not found in $releaseDir!"
    exit 1
}

$apkSizeMb = [math]::Round($apk.Length / 1MB, 2)
Write-Host ">>> [OK] Release APK built successfully:" -ForegroundColor Green
Write-Host "    File: $($apk.FullName)" -ForegroundColor White
Write-Host "    Size: $apkSizeMb MB" -ForegroundColor White

# 4. Copy to dist
$distDir = "dist"
if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}
Copy-Item $apk.FullName -Destination "$distDir/$($apk.Name)" -Force
Write-Host ">>> Copied to: $distDir/$($apk.Name)" -ForegroundColor Green

# 5. Optional install
if ($Install) {
    Write-Host ">>> Installing release to device via scripts/install_to_device.ps1..." -ForegroundColor Cyan
    & powershell -ExecutionPolicy Bypass -File "scripts/install_to_device.ps1" -ApkPath $apk.FullName -PackageName "com.hermes.mobile"
} else {
    Write-Host ""
    Write-Host "Tip: To test-install this release on connected phone:" -ForegroundColor Yellow
    Write-Host "  .\scripts\build_release.ps1 -Install" -ForegroundColor White
}
