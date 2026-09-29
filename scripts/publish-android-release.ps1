param(
    [string]$Tag = "",
    [string]$ApkPath = "android\app\build\outputs\apk\debug\app-debug.apk"
)

$ErrorActionPreference = "Stop"

# Get credentials
$credOutput = "protocol=https`nhost=github.com`n" | git credential fill
$token = ""
foreach ($line in ($credOutput -split "`n")) {
    if ($line -match "^password=(.+)$") {
        $token = $matches[1].Trim()
        break
    }
}

if (-not $token) {
    Write-Error "No token found in Git Credential Manager"
    exit 1
}

# Auto-detect version if not provided
if (-not $Tag) {
    $gradleContent = Get-Content "android\app\build.gradle.kts" -Raw
    if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') {
        $Tag = "v" + $matches[1]
    } else {
        $Tag = "v1.3.1"
    }
}

$repoOwner = "SpliffRa"
$repoName = "jackdaw-mail-android"
$headers = @{
    "Authorization" = "Bearer $token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "Jackdaw-Release-Uploader"
}

Write-Host "Publishing GitHub Release $Tag for $repoOwner/$repoName..." -ForegroundColor Cyan

# Check if release exists
$release = $null
$releaseNotes = @"
## Что нового в ${Tag}:
- ⚡ **Ликвидация зависаний и вылетов на больших письмах**:
  - Полностью исключены циклы `window.getComputedStyle()`, приводившие к 5–15-секундным фризам рендерера Chromium при открытии объемных писем с тысячами ячеек и тегов.
  - Мгновенная адаптация таблиц и шрифтов через аппаратный CSS на уровне GPU.
- 🛡️ **Расширение буфера CursorWindow до 32 МБ**:
  - Размер буфера чтения SQLite увеличен в 16 раз (с 2 МБ до 32 МБ), что навсегда устраняет фатальный крэш `CursorWindowAllocationException: Row too big to fit into CursorWindow`.
- 🚀 **Фоновая подготовка HTML (120 FPS)**:
  - Обработка HTML-разметки больших сообщений вынесена в фоновый пул `Dispatchers.Default` с быстрыми проверками наличия тегов, экран открывается плавно и без микрофризов.
- 📦 **Оптимизация inline-изображений**:
  - Изображения размером более 150 КБ больше не раздувают HTML-код письма Base64-строками, а сохраняются в списке вложений для скачивания в оригинальном качестве.
"@

try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$Tag" -Headers $headers -Method Get
    Write-Host "Found existing release $Tag (ID: $($release.id))" -ForegroundColor Green
    $updatePayload = @{
        name = "Jackdaw Mail $Tag"
        body = $releaseNotes
        make_latest = "true"
        prerelease = $false
        draft = $false
    } | ConvertTo-Json -Depth 5
    $updateBytes = [System.Text.Encoding]::UTF8.GetBytes($updatePayload)
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/$($release.id)" -Headers $headers -Method Patch -Body $updateBytes -ContentType "application/json; charset=utf-8"
} catch {
    $payload = @{
        tag_name = $Tag
        name = "Jackdaw Mail $Tag"
        body = $releaseNotes
        draft = $false
        prerelease = $false
        make_latest = "true"
    } | ConvertTo-Json -Depth 5
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Headers $headers -Method Post -Body $payloadBytes -ContentType "application/json; charset=utf-8"
}

# Upload APK
$fileName = "JackdawMail-$Tag.apk"
$uploadUrl = $release.upload_url -replace '\{\?name,label\}', "?name=$fileName"

foreach ($asset in $release.assets) {
    if ($asset.name -eq $fileName) {
        Write-Host "Removing previous asset $($asset.id)..."
        Invoke-RestMethod -Uri $asset.url -Headers $headers -Method Delete
    }
}

Write-Host "Uploading $ApkPath as $fileName..." -ForegroundColor Cyan
$apkBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $ApkPath).Path)
$uploadHeaders = @{
    "Authorization" = "Bearer $token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "Jackdaw-Release-Uploader"
    "Content-Type" = "application/vnd.android.package-archive"
}

$result = Invoke-RestMethod -Uri $uploadUrl -Headers $uploadHeaders -Method Post -Body $apkBytes
Write-Host "Successfully published release: $($release.html_url)" -ForegroundColor Green
Write-Host "Asset direct download: $($result.browser_download_url)" -ForegroundColor Green
