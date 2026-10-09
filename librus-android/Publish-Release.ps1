param([string]$Version = '0.12.1', [switch]$Draft)
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Nieprawidlowa wersja.' }
if (-not $env:GITHUB_TOKEN) { throw 'Ustaw GITHUB_TOKEN z prawem zapisu Contents do slq/librus-home. Nie wklejaj tokenu do kodu ani czatu.' }
$artifactDir = Join-Path $PSScriptRoot 'artifacts'
$names = @("LibrusApp-android-$Version.apk", 'librus-android-update.json', "LibrusApp-android-$Version-source.zip")
foreach ($name in $names) { if (-not (Test-Path -LiteralPath (Join-Path $artifactDir $name))) { throw "Brak pliku $name. Uruchom Build.ps1 -Release." } }
$manifest = Get-Content -LiteralPath (Join-Path $artifactDir 'librus-android-update.json') -Raw | ConvertFrom-Json
if ($manifest.versionName -ne $Version) { throw 'Wersja metadanych nie pasuje do APK.' }
$hash = (Get-FileHash -LiteralPath (Join-Path $artifactDir $names[0]) -Algorithm SHA256).Hash.ToLower()
if ($hash -ne $manifest.sha256) { throw 'Suma APK nie pasuje do metadanych.' }
$headers = @{ Authorization = "Bearer $env:GITHUB_TOKEN"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28'; 'User-Agent' = 'LibrusApp-Publisher' }
$tag = "android-v$Version"
$release = Invoke-RestMethod -Method Post -Uri 'https://api.github.com/repos/slq/librus-home/releases' -Headers $headers -ContentType 'application/json; charset=utf-8' -Body (@{tag_name=$tag; name="LibrusApp Android $Version"; body=$manifest.notes; draft=$true; prerelease=$false} | ConvertTo-Json)
$uploadBase = $release.upload_url -replace '\{.*$',''
if (-not $uploadBase.StartsWith('https://uploads.github.com/repos/slq/librus-home/releases/')) { throw 'Nieoczekiwany adres uploadu.' }
foreach ($name in $names) {
    $type = if ($name.EndsWith('.json')) { 'application/json' } elseif ($name.EndsWith('.apk')) { 'application/vnd.android.package-archive' } else { 'application/zip' }
    $encoded = [Uri]::EscapeDataString($name)
    Invoke-RestMethod -Method Post -Uri ($uploadBase + '?name=' + $encoded) -Headers $headers -ContentType $type -InFile (Join-Path $artifactDir $name) | Out-Null
}
if (-not $Draft) { $release = Invoke-RestMethod -Method Patch -Uri $release.url -Headers $headers -ContentType 'application/json' -Body (@{draft=$false; make_latest='true'} | ConvertTo-Json) }
Write-Output $release.html_url
