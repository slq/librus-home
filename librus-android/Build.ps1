param([string]$SdkPath = "$PSScriptRoot/.tools/android-sdk", [string]$PythonPath = '', [switch]$Release)
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
if ($Release) { $assembleTask = ":app:assembleRelease" } else { $assembleTask = ":app:assembleDebug" }
& ./gradlew.bat "-PbuildPython=$PythonPath" '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' $assembleTask :app:testDebugUnitTest :app:lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Kompilacja lub weryfikacja nie powiodla sie.' }
New-Item -ItemType Directory -Force -Path artifacts | Out-Null
if ($Release) { $builtApk = 'app/build/outputs/apk/release/app-release.apk'; $artifactName = 'LibrusApp-android-0.12.1.apk' }
else { $builtApk = 'app/build/outputs/apk/debug/app-debug.apk'; $artifactName = 'LibrusApp-android-0.12.1-debug.apk' }
$destination = Join-Path (Join-Path $PSScriptRoot 'artifacts') $artifactName
Copy-Item -LiteralPath $builtApk -Destination $destination
$hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLower()
[System.IO.File]::WriteAllText(($destination + '.sha256'), "$hash  $artifactName`n")
if ($Release) {
    & $PythonPath tools/prepare_update_release.py --apk $destination --notes RELEASE_NOTES.md
    if ($LASTEXITCODE -ne 0) { throw 'Nie przygotowano metadanych aktualizacji.' }
}
Write-Output "APK: $destination"
