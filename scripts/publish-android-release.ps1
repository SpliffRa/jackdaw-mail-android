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
- ✉️ **100% отображение писем и всей ветки переписки**:
  - Полное отображение всего письма, всех цитат, старых ответов, вёрстки и таблиц от первого до последнего символа.
  - Устранена первопричина обрезки: если тело ещё не загрузилось из сети (в списке папок только 255-символьный превью), клиент гарантированно загружает полный `item:Body` со всей историей ответов.
  - Автоматическая подгрузка тела письма при переключении между сообщениями в цепочке.
  - Естественный перенос слов: убран `hyphens: auto`, предотвращая разрезание слов посреди слога.
- ⚡ **Оптимизация производительности (120 FPS, 0 зависаний)**:
  - Ликвидация Layout Thrashing: полностью удалены тяжелые циклы JavaScript (`getComputedStyle`), вызывавшие зависания и ANR. Оформление тёмной темы работает на нативном CSS с GPU-ускорением.
  - Фоновая подготовка HTML: обработка разметки вынесена на `Dispatchers.Default` через `produceState`.
  - Буфер SQLite расширен до 32 МБ в `JackdawApp` для защиты от `CursorWindowAllocationException`.
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
