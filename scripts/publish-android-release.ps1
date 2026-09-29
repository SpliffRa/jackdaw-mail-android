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
- 🧵 **Умное сворачивание переписки (в стиле Google Gmail и Яндекс.Почты)**:
  - Автоматическое распознавание цитируемой истории переписки и цепочек ответов (Exchange OWA `divRplyFwdMsg`, `stopSpelling`, `border-top`, `gmail_quote`, `blockquote type="cite"`, `-----Исходное сообщение-----`, `-----Original Message-----`, plain-text `> `).
  - Цитаты сворачиваются в нативный интерактивный спойлер `<details>` с фирменной плашкой `··· История переписки ▼`.
  - Вложенное сворачивание для цепочек из нескольких сообщений (`··· Предыдущие сообщения ▼`).
  - При раскрытии исходный вид, стили, таблицы и форматирование сохраняются на 100%.
- ⚡ **Ликвидация зависаний и Layout Thrashing в WebView**:
  - Полностью исключены блокировки главного потока и падения WebView (ANR/OOM) на больших письмах.
  - Удалён цикл `document.querySelectorAll('*')` с `window.getComputedStyle()`, приводивший к перерасчету геометрии тысяч DOM-элементов.
  - Стили темной темы и очистка форматирования переведены на чистый аппаратный CSS.
- 🚀 **Асинхронная подготовка писем (120 FPS)**:
  - Обработка HTML и компиляция регулярных выражений вынесены в фоновый поток (`Dispatchers.Default` через `produceState`).
  - Экран открывается мгновенно, во время подготовки отображается плавный индикатор загрузки.
- 🛡️ **Защита буфера CursorWindow в SQLite**:
  - Запрос связанных писем цепочки `getEmailsInThread` ограничен `LIMIT 25` для предотвращения переполнения буфера SQLite при аномально больших ветках.
- 🔄 **Пагинация папок в протокольном движке**:
  - Реализован метод `fetchOlderEmails` в `OwaProtocolEngine` для плавной дозагрузки старых сообщений.
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
