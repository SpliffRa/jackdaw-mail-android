package app.jackdaw.client.data.auth

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
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

    val trustAllSslSocketFactory: javax.net.ssl.SSLSocketFactory by lazy {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        sslContext.socketFactory
    }

    val trustAllHostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }

    fun extractCanaryFromHtml(html: String): String? {
        if (html.isBlank()) return null
        val patterns = listOf(
            Regex("""["']([a-zA-Z0-9_\-\.]{16,})["']\s*,\s*["']canary["']""", RegexOption.IGNORE_CASE),
            Regex("""var\s+a_sCanary\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""window\.g_canary\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""["']?Canary["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""name=["']destination["'][^>]*value=["']([^"']+)["']""")
        )
        for (p in patterns) {
            val m = p.find(html)
            if (m != null && m.groupValues.size > 1 && m.groupValues[1].length >= 8) {
                return m.groupValues[1]
            }
        }
        return null
    }

    /**
     * Initializes or refreshes an active OWA session by sending a GET to /owa/.
     * Exchange uses this GET to allocate a UserContext cookie and output Canary tokens.
     */
    suspend fun initializeOwaSession(
        serverInput: String,
        cookies: String
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val baseOwaUrl = normalizeOwaUrl(serverInput)
        try {
            val url = URL(baseOwaUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                if (this is javax.net.ssl.HttpsURLConnection) {
                    sslSocketFactory = trustAllSslSocketFactory
                    hostnameVerifier = trustAllHostnameVerifier
                }
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                if (cookies.isNotBlank()) {
                    setRequestProperty("Cookie", cookies)
                }
            }

            val finalUrl = conn.url.toString()
            if (finalUrl.contains("logon.aspx", ignoreCase = true) || finalUrl.contains("reason=", ignoreCase = true)) {
                return@withContext null
            }

            val setCookies = conn.headerFields.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value }

            val mergedMap = parseCookies(cookies).toMutableMap()
            for (sc in setCookies) {
                val kv = sc.substringBefore(";").split("=", limit = 2)
                if (kv.size == 2 && kv[0].isNotBlank()) {
                    mergedMap[kv[0].trim()] = kv[1].trim()
                }
            }
            val mergedCookies = mergedMap.entries.joinToString("; ") { "${it.key}=${it.value}" }

            val htmlBody = runCatching {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            }.getOrNull().orEmpty()

            val canary = extractCanaryFromHtml(htmlBody)
                ?: extractCanary(mergedCookies)
                ?: "canary_${System.currentTimeMillis()}"

            Pair(mergedCookies, canary)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Performs direct Exchange Form-Based Auth (FBA) at /owa/auth/owaauth.dll.
     */
    suspend fun authenticateDirectFba(
        serverInput: String,
        username: String,
        password: String,
        emailHint: String = ""
    ): OwaAuthResult = withContext(Dispatchers.IO) {
        val baseOwaUrl = normalizeOwaUrl(serverInput)
        val fbaEndpoint = if (baseOwaUrl.endsWith("/")) {
            "${baseOwaUrl}auth/owaauth.dll"
        } else {
            "$baseOwaUrl/auth/owaauth.dll"
        }

        val domainFromHost = runCatching {
            URL(baseOwaUrl).host.split(".").let { parts ->
                if (parts.size >= 2) parts[parts.size - 2] else ""
            }
        }.getOrDefault("")

        val userCandidates = mutableListOf(username.trim())
        if (emailHint.isNotBlank() && emailHint != username) {
            userCandidates.add(emailHint.trim())
        }
        if (!username.contains("@") && !username.contains("\\") && domainFromHost.isNotBlank()) {
            userCandidates.add("$domainFromHost\\${username.trim()}")
        }

        var lastError: String? = null

        for (candidateUser in userCandidates.distinct()) {
            try {
                val url = URL(fbaEndpoint)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    if (this is javax.net.ssl.HttpsURLConnection) {
                        sslSocketFactory = trustAllSslSocketFactory
                        hostnameVerifier = trustAllHostnameVerifier
                    }
                    requestMethod = "POST"
                    doOutput = true
                    instanceFollowRedirects = false
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                }

                val postData = buildString {
                    append("destination=").append(URLEncoder.encode(baseOwaUrl, "UTF-8"))
                    append("&flags=4")
                    append("&forcedownlevel=0")
                    append("&trusted=4")
                    append("&username=").append(URLEncoder.encode(candidateUser, "UTF-8"))
                    append("&password=").append(URLEncoder.encode(password, "UTF-8"))
                    append("&isUtf8=1")
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val setCookies = connection.headerFields.entries
                    .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                    .flatMap { it.value }
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
                    // Critical: follow up with GET /owa/ to initialize UserContext and Canary
                    val initialized = initializeOwaSession(baseOwaUrl, rawCookies)
                    val effectiveCookies = initialized?.first ?: rawCookies
                    val canary = initialized?.second
                        ?: extractCanary(effectiveCookies)
                        ?: "canary_${System.currentTimeMillis()}"

                    return@withContext OwaAuthResult(
                        isSuccess = true,
                        serverUrl = baseOwaUrl,
                        email = if (candidateUser.contains("@")) candidateUser else if (emailHint.isNotBlank()) emailHint else "$candidateUser@${URL(baseOwaUrl).host}",
                        sessionCookies = effectiveCookies,
                        canaryToken = canary
                    )
                } else {
                    lastError = "Сервер отклонил вход для '$candidateUser' (HTTP $responseCode)"
                }
            } catch (e: Exception) {
                lastError = "Ошибка подключения к $fbaEndpoint: ${e.localizedMessage ?: "Сбой сети"}"
            }
        }

        return@withContext OwaAuthResult(
            isSuccess = false,
            serverUrl = baseOwaUrl,
            email = username,
            errorMessage = lastError ?: "Неверные учетные данные или сервер отклонил FBA вход"
        )
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
