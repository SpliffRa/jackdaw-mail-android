package app.jackdaw.client.data.auth

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OwaAuthResult(
    val isSuccess: Boolean,
    val serverUrl: String,
    val email: String,
    val sessionCookies: String = "",
    val canaryToken: String = "",
    val errorMessage: String? = null
)

object OwaAuthManager {

    /**
     * Normalizes an input host or URL to a canonical OWA URL ending in /owa/.
     * E.g. "mail.corp.com" -> "https://mail.corp.com/owa/"
     */
    fun normalizeOwaUrl(input: String): String {
        var trimmed = input.trim()
        if (trimmed.isBlank()) {
            return "https://mail.corp.com/owa/"
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        val url = try {
            URL(trimmed)
        } catch (e: Exception) {
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/owa/"
        }

        val host = url.host
        val portPart = if (url.port != -1 && url.port != 443 && url.port != 80) ":${url.port}" else ""
        val path = url.path.trim('/')

        val pathLower = path.lowercase()
        return when {
            path.isBlank() || pathLower == "owa" || pathLower.startsWith("owa/") -> "${url.protocol}://$host$portPart/owa/"
            pathLower.endsWith(".aspx") -> "${url.protocol}://$host$portPart/owa/"
            else -> "${url.protocol}://$host$portPart/$path/"
        }
    }

    /**
     * Checks if cookies string or URL indicates an active authenticated OWA session.
     */
    fun isSessionAuthenticated(cookieHeader: String?, currentUrl: String?): Boolean {
        if (cookieHeader.isNullOrBlank() && currentUrl == null) return false

        // Check key Exchange / OWA authentication cookies
        val cookies = parseCookies(cookieHeader)
        val hasOwaAuthCookie = cookies.keys.any { key ->
            key.equals("cadata", ignoreCase = true) ||
            key.equals("sessionid", ignoreCase = true) ||
            key.equals("userContext", ignoreCase = true) ||
            key.contains("canary", ignoreCase = true) ||
            key.equals("X-OWA-CANARY", ignoreCase = true)
        }

        // Check if landed on OWA main view
        val urlMatches = currentUrl?.let {
            it.contains("/owa/#path=", ignoreCase = true) ||
            it.contains("/owa/?", ignoreCase = true) ||
            it.endsWith("/owa/") ||
            it.contains("/owa/default.aspx", ignoreCase = true) ||
            it.contains("mail.live.com", ignoreCase = true) ||
            it.contains("outlook.office.com/mail", ignoreCase = true)
        } ?: false

        return hasOwaAuthCookie || (urlMatches && (cookies.isNotEmpty()))
    }

    /**
     * Parses raw Cookie string into key-value map.
     */
    fun parseCookies(cookieHeader: String?): Map<String, String> {
        if (cookieHeader.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        cookieHeader.split(";").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                result[parts[0].trim()] = parts[1].trim()
            }
        }
        return result
    }

    /**
     * Extracts X-OWA-CANARY token from cookies if available.
     */
    fun extractCanary(cookieHeader: String?): String? {
        val cookies = parseCookies(cookieHeader)
        return cookies["X-OWA-CANARY"]
            ?: cookies.entries.firstOrNull { it.key.contains("canary", ignoreCase = true) }?.value
            ?: cookies["userContext"]
    }

    /**
     * Performs direct Exchange Form-Based Auth (FBA) at /owa/auth/owaauth.dll.
     */
    suspend fun authenticateDirectFba(
        serverInput: String,
        username: String,
        password: String
    ): OwaAuthResult = withContext(Dispatchers.IO) {
        val baseOwaUrl = normalizeOwaUrl(serverInput)
        val fbaEndpoint = if (baseOwaUrl.endsWith("/")) {
            "${baseOwaUrl}auth/owaauth.dll"
        } else {
            "$baseOwaUrl/auth/owaauth.dll"
        }

        try {
            val url = URL(fbaEndpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Jackdaw Mail)")
            }

            val postData = buildString {
                append("destination=").append(URLEncoder.encode(baseOwaUrl, "UTF-8"))
                append("&flags=4")
                append("&forcedownlevel=0")
                append("&trusted=4")
                append("&username=").append(URLEncoder.encode(username, "UTF-8"))
                append("&password=").append(URLEncoder.encode(password, "UTF-8"))
                append("&isUtf8=1")
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val setCookies = connection.headerFields["Set-Cookie"] ?: emptyList()
            val rawCookies = setCookies.joinToString("; ") { it.substringBefore(";") }

            val redirectLocation = connection.getHeaderField("Location") ?: ""
            val isSuccessRedirect = responseCode == 302 && (
                redirectLocation.contains("/owa", ignoreCase = true) ||
                !redirectLocation.contains("reason=", ignoreCase = true)
            )

            val cookiesMap = parseCookies(rawCookies)
            val hasValidCookie = cookiesMap.containsKey("cadata") ||
                cookiesMap.containsKey("sessionid") ||
                cookiesMap.containsKey("userContext")

            if (isSuccessRedirect || hasValidCookie || responseCode in 200..299) {
                val canary = extractCanary(rawCookies) ?: "canary_${System.currentTimeMillis()}"
                return@withContext OwaAuthResult(
                    isSuccess = true,
                    serverUrl = baseOwaUrl,
                    email = if (username.contains("@")) username else "$username@${URL(baseOwaUrl).host}",
                    sessionCookies = rawCookies,
                    canaryToken = canary
                )
            } else {
                return@withContext OwaAuthResult(
                    isSuccess = false,
                    serverUrl = baseOwaUrl,
                    email = username,
                    errorMessage = "Неверные учетные данные или сервер отклонил FBA вход (HTTP $responseCode)"
                )
            }
        } catch (e: Exception) {
            // If offline/mock server or direct connection refused, provide clean diagnostic
            return@withContext OwaAuthResult(
                isSuccess = false,
                serverUrl = baseOwaUrl,
                email = username,
                errorMessage = "Ошибка подключения к $fbaEndpoint: ${e.localizedMessage ?: "Сбой сети"}. Рекомендуется использовать вход через веб-интерфейс (SSO)."
            )
        }
    }

    /**
     * Reads all cookies from system CookieManager for given URL.
     */
    fun getCookiesFromManager(url: String): String {
        return try {
            CookieManager.getInstance().getCookie(url) ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
