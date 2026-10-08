param([string]$ApkPath = "$PSScriptRoot/artifacts/LibrusApp-android-0.7.0-debug.apk", [string]$SdkPath = "$PSScriptRoot/.tools/android-sdk")
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ApkPath)) { throw 'Nie znaleziono APK. Najpierw uruchom Build.ps1.' }
$adbPath = Join-Path $SdkPath 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { throw 'Nie znaleziono adb.exe. Podaj -SdkPath do Android SDK.' }
& $adbPath -d get-state
if ($LASTEXITCODE -ne 0) { throw 'Podlacz jeden telefon USB, wlacz Debugowanie USB i zaakceptuj komputer na telefonie.' }
& $adbPath -d install -r $ApkPath
if ($LASTEXITCODE -ne 0) { throw 'Instalacja nie powiodla sie. Sprawdz komunikat adb.' }
& $adbPath -d shell am start -n pl.librushome.android/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'APK zainstalowano, ale nie udalo sie uruchomic aplikacji.' }
