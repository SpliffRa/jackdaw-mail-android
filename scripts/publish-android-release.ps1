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
- 📅 **Полноценный Календарь встреч и мероприятий (Outlook-style)**:
  - Внедрена фирменная нижняя панель навигации (Outlook Bottom Navigation) с переключением между **Почтой** (с бейджем непрочитанных) и **Календарем**.
  - **Интерактивная лента дат:** горизонтальная прокрутка дней недели с точками-индикаторами событий и кнопка быстрого перехода «Сегодня».
  - **Повестка дня (Agenda):** карточки запланированных встреч с временным интервалом, цветовой категорией, местом или ссылкой на видеоконференцию (Microsoft Teams, Zoom, Яндекс Телемост).
  - **Быстрый ответ (RSVP как в Outlook):** кнопки «Принять», «Под вопросом» и «Отклонить» для каждого приглашения.
  - **Планирование и создание встреч (+ FAB):** форма добавления события с указанием темы, интервала времени, флага «Весь день», ссылок, участников и цветовой категории.
  - **Offline-first хранение:** локальная база данных Room с быстрой индексацией и автономной работой.
- 🔒 **Чистый профиль без демо-данных**:
  - Полная безопасность и приватность — приложение работает исключительно с учетными данными и событиями пользователя.
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
