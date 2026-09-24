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
    $updatePayload = @{
        make_latest = "true"
        prerelease = $false
        draft = $false
    } | ConvertTo-Json
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoOwner/$repoName/releases/$($release.id)" -Headers $headers -Method Patch -Body $updatePayload -ContentType "application/json; charset=utf-8"
} catch {
    $releaseNotes = @"
## Что нового в ${Tag}:
- 📄 **Полный и читаемый текст писем**:
  - Нативный скроллинг без обрезки по высоте.
  - Автоматическая коррекция контрастности для темной темы (устранен невидимый черный текст на темном фоне).
  - Переключатель темной/светлой подложки для писем со сложной версткой и поддержка зума.
- 📎 **Точные вложения и файлы**:
  - Устранены синтетические заглушки «Вложение 24 КБ» — теперь отображаются точные имена и реальный размер файлов из Exchange.
  - Надёжное скачивание и открытие файлов из вложений.
- 🖼️ **Встроенные изображения и логотипы подписи**:
  - Автоматический резолвинг CID-изображений (логотипы SMART DS, DPD и др.) непосредственно в тело письма.
  - Встроенные изображения подписей больше не засоряют список вложений.
- ⭐ **Двусторонняя синхронизация избранного**:
  - Отметка письма звездочкой/флагом синхронизируется с сервером Exchange и видна на десктопе.
- 🗂️ **Эргономика и фильтры**:
  - Удобные фильтры «Входящие», «Непрочитанные», «Избранное».
- ⚡ **Фоновая синхронизация**:
  - Усилена стабильность фоновой работы WorkManager и предотвращение засыпания службы синхронизации.
"@

    $payload = @{
        tag_name = $Tag
        name = "Jackdaw Mail $Tag"
        body = $releaseNotes
        draft = $false
        prerelease = $false
        make_latest = "true"
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
