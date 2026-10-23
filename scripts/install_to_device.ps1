param(
    [string]$ApkPath = "",
    [string]$PackageName = ""
)

if (-not $ApkPath) {
    $latestApk = Get-ChildItem -Path "app/build/outputs/apk" -Recurse -Filter "*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latestApk) {
        $ApkPath = $latestApk.FullName
    }
}

if (-not $PackageName) {
    if ($ApkPath -match "debug") {
        $PackageName = "com.hermes.mobile.debug"
    } else {
        $PackageName = "com.hermes.mobile"
    }
}

$adb = "adb"
if (Get-Command adb -ErrorAction SilentlyContinue) {
    $adb = "adb"
} elseif ($env:ANDROID_HOME -and (Test-Path "$env:ANDROID_HOME\platform-tools\adb.exe")) {
    $adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
} elseif ($env:ANDROID_SDK_ROOT -and (Test-Path "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe")) {
    $adb = "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
} elseif (Test-Path "D:\android-sdk\platform-tools\adb.exe") {
    $adb = "D:\android-sdk\platform-tools\adb.exe"
}

Write-Host ">>> [1/4] Checking connected devices..." -ForegroundColor Cyan
$devices = & $adb devices
$deviceList = $devices | Where-Object { $_ -match "\tdevice$" }
if (-not $deviceList) {
    Write-Error "No connected Android devices found via ADB!"
    exit 1
}

if (-not (Test-Path $ApkPath)) {
    Write-Error "APK file not found: $ApkPath"
    exit 1
}

Write-Host ">>> Target APK: $ApkPath" -ForegroundColor Yellow
Write-Host ">>> Target Package: $PackageName" -ForegroundColor Yellow

Write-Host ">>> [2/4] Installing APK STRICTLY to User 0 (Personal profile only)..." -ForegroundColor Cyan
& $adb install -r --user 0 $ApkPath
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to install APK to User 0"
    exit $LASTEXITCODE
}

Write-Host ">>> [3/4] Verifying profile isolation and cleaning up work profile / Island..." -ForegroundColor Cyan
$usersRaw = & $adb shell pm list users
foreach ($line in $usersRaw) {
    if ($line -match "UserInfo\{(\d+):") {
        $uid = [int]$matches[1]
        if ($uid -ne 0) {
            $pkgs = & $adb shell pm list packages --user $uid
            if ($pkgs -match $PackageName) {
                Write-Warning "Detected $PackageName in profile $uid! Immediately removing..."
                & $adb shell pm uninstall --user $uid $PackageName
            }
        }
    }
}

Write-Host ">>> [4/4] Launching application in User 0..." -ForegroundColor Cyan
& $adb shell am start -n "$PackageName/com.hermes.mobile.presentation.MainActivity"

Write-Host ">>> [OK] App installed and running EXCLUSIVELY in User 0 (Personal Profile)." -ForegroundColor Green
