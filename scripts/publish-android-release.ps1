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
try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/$Tag" -Headers $headers -Method Get
    Write-Host "Found existing release $Tag (ID: $($release.id))" -ForegroundColor Green
} catch {
    $releaseNotes = @"
## Что нового в ${Tag}:
- 📐 **Исправление отступов экрана**:
  - Устранен лишний черный отступ в верхней части экрана (входящие, календарь, настройки, просмотр писем).
  - Идеальное бесшовное примыкание панелей навигации к системному статус-бару.
- 📂 **Эргономика списка папок**:
  - Удалены громоздкие кнопки-стрелочки.
  - Полнострочный вертикальный жест перетаскивания (свайп пальцем) для быстрого упорядочивания папок.
  - Автоматическое сохранение нового порядка папок при тапе на свободное пространство или закрытии меню.
- ⏱️ **Точный контроль SLA писем**:
  - Восстановлен статус и бейдж «Ответ дан вовремя» для выполненных писем взамен некорректных «0 мин».
- 📅 **Компактная шапка Календаря**:
  - Кнопки переключения режима («Месяц» / «Неделя») и возврата «Сегодня» уменьшены и оформлены в аккуратные микро-чипы.
- 🛡️ **Чистые адреса Exchange в отправленных письмах**:
  - Устранено отображение громоздких путей Exchange X.500 (`/O=.../OU=.../CN=...`).
  - В отправленных письмах гарантированно отображается чистый и понятный email отправителя.
"@
    $payload = @{
        tag_name = $Tag
        name = "Jackdaw Mail $Tag"
        body = $releaseNotes
        draft = $false
        prerelease = $false
    } | ConvertTo-Json
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases" -Headers $headers -Method Post -Body $payload -ContentType "application/json; charset=utf-8"
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
