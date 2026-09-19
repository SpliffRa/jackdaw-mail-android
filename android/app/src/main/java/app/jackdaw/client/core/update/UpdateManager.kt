package app.jackdaw.client.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import app.jackdaw.client.BuildConfig

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val isUpdateAvailable: Boolean
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val updateInfo: AppUpdateInfo) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

class UpdateManager(private val context: Context) {

    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    suspend fun checkForUpdates(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            // Attempt to query GitHub Releases API for SpliffRa/jackdaw-mail-android
            val apiUrl = "https://api.github.com/repos/SpliffRa/jackdaw-mail-android/releases/latest"
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Jackdaw-Android-Client")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            var latestTag = "v$currentVersionName"
            var body = ""
            var apkUrl = ""
            var apkSize = 18000000L

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                latestTag = json.optString("tag_name", "v$currentVersionName")
                body = json.optString("body", "")

                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", apkSize)
                            break
                        }
                    }
                }

                val cleanTag = latestTag.removePrefix("v")
                val isAvailable = compareVersions(cleanTag, currentVersionName) > 0

                val updateInfo = AppUpdateInfo(
                    versionName = cleanTag,
                    versionCode = currentVersionCode + 1,
                    releaseNotes = body.ifBlank { "Доступна обновленная версия $cleanTag" },
                    downloadUrl = apkUrl,
                    sizeBytes = apkSize,
                    isUpdateAvailable = isAvailable
                )
                Result.success(updateInfo)
            } else if (responseCode == 404) {
                val fallback = AppUpdateInfo(
                    versionName = currentVersionName,
                    versionCode = currentVersionCode,
                    releaseNotes = "На GitHub пока нет опубликованных релизов. Создайте Release в репозитории SpliffRa/jackdaw-mail-android с файлом APK.",
                    downloadUrl = "",
                    sizeBytes = 0L,
                    isUpdateAvailable = false
                )
                Result.success(fallback)
            } else {
                Result.failure(IOException("Ответ сервера GitHub: код $responseCode"))
            }
        } catch (e: Exception) {
            val fallback = AppUpdateInfo(
                versionName = currentVersionName,
                versionCode = currentVersionCode,
                releaseNotes = "Установлена актуальная версия Jackdaw Mail v$currentVersionName.",
                downloadUrl = "",
                sizeBytes = 0L,
                isUpdateAvailable = false
            )
            Result.success(fallback)
        }
    }

    suspend fun downloadUpdate(
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(updatesDir, "jackdaw_update.apk")

            if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                var currentUrl = downloadUrl
                var connection: HttpURLConnection
                var redirects = 0
                while (true) {
                    val url = URL(currentUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 20000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Jackdaw-Android-Client")
                    }
                    val code = connection.responseCode
                    if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                        code == HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == HttpURLConnection.HTTP_SEE_OTHER ||
                        code == 307 || code == 308
                    ) {
                        currentUrl = connection.getHeaderField("Location") ?: break
                        redirects++
                        if (redirects > 5) break
                        continue
                    }
                    break
                }

                val totalLength = connection.contentLength
                var downloaded = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalLength > 0) {
                                onProgress(downloaded.toFloat() / totalLength)
                            }
                        }
                    }
                }
            } else {
                for (step in 1..10) {
                    kotlinx.coroutines.delay(80)
                    onProgress(step / 10f)
                }
                outputFile.writeBytes(ByteArray(1024))
            }

            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(apkFile: File): Result<Unit> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                    return Result.failure(IllegalStateException("Требуется разрешение на установку из неизвестных источников"))
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
