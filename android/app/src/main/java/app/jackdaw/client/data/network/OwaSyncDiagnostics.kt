package app.jackdaw.client.data.network

import app.jackdaw.client.core.model.MailAccount
import java.time.Instant

/**
 * Diagnostics holder for Exchange/OWA connectivity, HTTP responses,
 * authentication cookies, and synchronization outcomes.
 */
object OwaSyncDiagnostics {
    @Volatile var lastSyncStatus: String = "Не выполнялась"
    @Volatile var lastSyncTimestamp: Long = 0L
    @Volatile var lastHttpCode: Int = 0
    @Volatile var lastAction: String = "Нет"
    @Volatile var lastUrl: String = "Нет"
    @Volatile var lastError: String? = null
    @Volatile var lastFoldersCount: Int = 0
    @Volatile var lastEmailsFetchedCount: Int = 0
    @Volatile var lastFolderNames: List<String> = emptyList()

    fun recordSuccess(action: String, url: String, code: Int, details: String) {
        lastSyncStatus = "Успешно: $details"
        lastSyncTimestamp = System.currentTimeMillis()
        lastHttpCode = code
        lastAction = action
        lastUrl = url
        lastError = null
    }

    fun recordError(action: String, url: String, code: Int, error: String) {
        lastSyncStatus = "Ошибка: $error"
        lastSyncTimestamp = System.currentTimeMillis()
        lastHttpCode = code
        lastAction = action
        lastUrl = url
        lastError = error
    }

    fun generateReport(account: MailAccount?): String {
        return buildString {
            appendLine("=== Jackdaw Mail Отчет о Диагностике ===")
            appendLine("Дата: ${Instant.now()}")
            if (account != null) {
                appendLine("Аккаунт: ${account.email}")
                appendLine("Сервер: ${account.serverHost}")
                appendLine("Протокол: ${account.protocol}")
                val hasCadata = account.authSessionCookies.contains("cadata", ignoreCase = true)
                val hasUserCtx = account.authSessionCookies.contains("UserContext", ignoreCase = true)
                val hasSessionId = account.authSessionCookies.contains("sessionid", ignoreCase = true)
                appendLine("Cookies длина: ${account.authSessionCookies.length} симв (cadata=$hasCadata, UserContext=$hasUserCtx, sessionid=$hasSessionId)")
                val cm = runCatching { android.webkit.CookieManager.getInstance() }.getOrNull()
                val cmCookies = runCatching { cm?.getCookie(account.serverHost) }.getOrNull()
                val cmHasUserCtx = cmCookies?.contains("UserContext", ignoreCase = true) == true
                if (!cmCookies.isNullOrBlank() && cmCookies != account.authSessionCookies) {
                    appendLine("WebView Cookies: ${cmCookies.length} симв (UserContext=$cmHasUserCtx)")
                }
                appendLine("Canary токен: ${if (account.authSessionToken.isNotBlank()) "Задан (${account.authSessionToken.take(8)}...)" else "Отсутствует"}")
                appendLine("Логин пользователя: ${account.loginUser.ifBlank { "(не указан)" }}")
                appendLine("Пароль для silent re-auth: ${if (account.savedPassword.isNotBlank()) "Задан" else "Не сохранен"}")
            } else {
                appendLine("Аккаунт: Не выбран")
            }
            appendLine("Последний статус: $lastSyncStatus")
            appendLine("Последнее действие: $lastAction")
            appendLine("Последний URL: $lastUrl")
            appendLine("HTTP Код: $lastHttpCode")
            if (!lastError.isNullOrBlank()) {
                appendLine("Детали ошибки: $lastError")
            }
            appendLine("Обнаружено папок на сервере: $lastFoldersCount")
            if (lastFolderNames.isNotEmpty()) {
                appendLine("Папки: ${lastFolderNames.joinToString(", ")}")
            }
            appendLine("Получено писем за последнюю синхронизацию: $lastEmailsFetchedCount")
            appendLine("=========================================")
        }
    }
}
