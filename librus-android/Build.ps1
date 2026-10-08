param([string]$SdkPath = "$PSScriptRoot/.tools/android-sdk", [string]$PythonPath = '')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path -LiteralPath "$SdkPath/platforms/android-35/android.jar")) {
    throw 'Brak Android SDK 35. Podaj -SdkPath lub skonfiguruj SDK w Android Studio. Szczegoly: README.md.'
}
if (-not $PythonPath) {
    $hostPython = Get-ChildItem -LiteralPath "$PSScriptRoot/.tools/python-host" -Filter python.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($hostPython) { $PythonPath = $hostPython.FullName }
}
if (-not $PythonPath -or -not (Test-Path -LiteralPath $PythonPath)) {
    throw 'Brak Pythona 3.13. Podaj -PythonPath wskazujacy python.exe wersji 3.13.'
}
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'), ('sdk.dir=' + $SdkPath.Replace('\', '/').Replace(':', '\:') + "`n"), [System.Text.Encoding]::ASCII)
& $PythonPath tools/sync_shared.py --check
if ($LASTEXITCODE -ne 0) { throw 'Wspolne zrodla nie sa zgodne z desktopem.' }
& ./gradlew.bat "-PbuildPython=$PythonPath" '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Kompilacja lub weryfikacja nie powiodla sie.' }
New-Item -ItemType Directory -Force -Path artifacts | Out-Null
Copy-Item -LiteralPath app/build/outputs/apk/debug/app-debug.apk -Destination artifacts/LibrusApp-android-0.7.0-debug.apk
$hash = (Get-FileHash -LiteralPath artifacts/LibrusApp-android-0.7.0-debug.apk -Algorithm SHA256).Hash.ToLower()
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'artifacts/LibrusApp-android-0.7.0-debug.apk.sha256'), "$hash  LibrusApp-android-0.7.0-debug.apk`n")
Write-Output "APK: $PSScriptRoot/artifacts/LibrusApp-android-0.7.0-debug.apk"
