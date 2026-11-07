# Build BedBeam and install it on the projector over Wi-Fi ADB.
# Usage:  .\scripts\deploy.ps1                 (release build)
#         .\scripts\deploy.ps1 -Debug          (debug build, installs as com.bedbeam.browser.debug)
#         .\scripts\deploy.ps1 -Device 192.168.1.50:5555
param(
    [switch]$Debug,
    [string]$Device = "192.168.1.100:5555",
    [string]$Adb = "$env:USERPROFILE\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
)
# "Continue": Gradle and adb write warnings to stderr, which PowerShell 5.1 would treat as fatal.
# Failures are detected with $LASTEXITCODE instead.
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot

if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName } else { throw "JDK 17 not found; set JAVA_HOME." }
}
if (-not (Test-Path $Adb)) {
    $fallback = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $fallback) { $Adb = $fallback } else { throw "adb not found." }
}

$task = if ($Debug) { "assembleDebug" } else { "assembleRelease" }
$apk = if ($Debug) { "$root\app\build\outputs\apk\debug\app-debug.apk" } else { "$root\app\build\outputs\apk\release\app-release.apk" }
$pkg = if ($Debug) { "com.bedbeam.browser.debug" } else { "com.bedbeam.browser" }

Push-Location $root
try {
    & .\gradlew.bat $task --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed." }
} finally { Pop-Location }

"APK: $apk ($([math]::Round((Get-Item $apk).Length / 1KB)) KB)"
& $Adb connect $Device | Out-Null
& $Adb -s $Device install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Install failed." }
& $Adb -s $Device shell am start -n "$pkg/com.bedbeam.browser.MainActivity" | Out-Null
"Installed and launched $pkg on $Device"
