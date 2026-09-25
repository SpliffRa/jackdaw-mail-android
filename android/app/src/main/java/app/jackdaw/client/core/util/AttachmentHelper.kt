package app.jackdaw.client.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object AttachmentHelper {

    fun getMimeType(fileName: String, fallbackMime: String? = null): String {
        if (!fallbackMime.isNullOrBlank() && fallbackMime != "application/octet-stream" && fallbackMime != "*/*") {
            return fallbackMime
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            val mimeFromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!mimeFromMap.isNullOrBlank()) return mimeFromMap
        }
        return when (ext) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "eml" -> "message/rfc822"
            else -> "*/*"
        }
    }

    /**
     * Сохраняет файл в системную папку "Download" (Загрузки) устройства.
     * Возвращает Uri сохраненного файла или null при ошибке.
     */
    fun saveToDownloads(context: Context, sourceFile: File, displayName: String, mimeType: String): Uri? {
        val effectiveMime = getMimeType(displayName, mimeType)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, effectiveMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                uri
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                var destFile = File(downloadsDir, displayName)
                val base = displayName.substringBeforeLast('.')
                val ext = if (displayName.contains('.')) ".${displayName.substringAfterLast('.')}" else ""
                var counter = 1
                while (destFile.exists()) {
                    destFile = File(downloadsDir, "$base ($counter)$ext")
                    counter++
                }
                sourceFile.copyTo(destFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(effectiveMime),
                    null
                )
                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            android.util.Log.e("AttachmentHelper", "Failed to save to downloads: ${e.message}", e)
            null
        }
    }

    /**
     * Сохраняет файл в выбранный пользователем Uri (через системный SAF Document Provider).
     */
    fun saveToUri(context: Context, sourceFile: File, destinationUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("AttachmentHelper", "Failed to write to Uri $destinationUri: ${e.message}", e)
            false
        }
    }

    /**
     * Открывает файл во внешнем приложении через FileProvider.
     */
    fun openFile(context: Context, file: File, mimeType: String) {
        if (!file.exists() || file.length() <= 0L) {
            android.widget.Toast.makeText(context, "Файл не найден или пуст", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val effectiveMime = getMimeType(file.name, mimeType)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, effectiveMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Открыть: ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Не удалось открыть файл: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
