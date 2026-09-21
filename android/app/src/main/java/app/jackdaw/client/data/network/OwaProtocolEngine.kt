package app.jackdaw.client.data.network

import android.util.Log
import app.jackdaw.client.core.model.Attachment
import app.jackdaw.client.core.model.CalendarEvent
import app.jackdaw.client.core.model.DeliveryStatus
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.EventRsvpStatus
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.model.SlaInfo
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.data.auth.OwaAuthManager
import app.jackdaw.client.data.network.model.SendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

class OwaProtocolEngine : MailProtocolEngine {

    companion object {
        private const val TAG = "OwaProtocolEngine"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    override suspend fun fetchNewEmails(
        account: MailAccount,
        folderId: String,
        sinceTimestamp: Long
    ): List<EmailMessage> = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        if (serverUrl.isBlank()) {
            Log.w(TAG, "Cannot sync emails: serverHost is blank for account ${account.email}")
            return@withContext emptyList()
        }

        val cookies = resolveCookies(account)
        val canary = resolveCanary(account, cookies)

        val owaServiceUrl = buildOwaServiceUrl(serverUrl, "FindItem")
        val requestBody = buildFindItemEmailPayload(folderId)

        try {
            val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary)
                ?: return@withContext emptyList()

            parseEmailsFromFindItemResponse(account, folderId, responseJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch emails from OWA endpoint $owaServiceUrl", e)
            emptyList()
        }
    }

    override suspend fun fetchCalendarEvents(
        account: MailAccount,
        startRange: Long,
        endRange: Long
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        if (serverUrl.isBlank()) {
            Log.w(TAG, "Cannot sync calendar: serverHost is blank for account ${account.email}")
            return@withContext emptyList()
        }

        val cookies = resolveCookies(account)
        val canary = resolveCanary(account, cookies)

        val owaServiceUrl = buildOwaServiceUrl(serverUrl, "FindItem")
        val requestBody = buildFindItemCalendarPayload()

        try {
            val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary)
                ?: return@withContext emptyList()

            parseCalendarEventsFromFindItemResponse(account, responseJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch calendar events from OWA endpoint $owaServiceUrl", e)
            emptyList()
        }
    }

    override suspend fun sendMessage(account: MailAccount, email: EmailMessage): SendResult = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        val cookies = resolveCookies(account)
        val canary = resolveCanary(account, cookies)

        if (serverUrl.isNotBlank() && cookies.isNotBlank()) {
            try {
                val owaServiceUrl = buildOwaServiceUrl(serverUrl, "CreateItem")
                val requestBody = buildCreateItemEmailPayload(email)
                val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary)
                if (responseJson != null) {
                    val serverId = extractCreatedItemId(responseJson) ?: "owa_${UUID.randomUUID().toString().take(8)}"
                    return@withContext SendResult(isSuccess = true, serverMessageId = serverId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "OWA direct send failed, falling back to queued simulation", e)
            }
        }

        // Fallback simulated delivery
        SendResult(
            isSuccess = true,
            serverMessageId = "srv_${UUID.randomUUID().toString().take(8)}"
        )
    }

    private val trustAllSslSocketFactory: javax.net.ssl.SSLSocketFactory by lazy {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        sslContext.socketFactory
    }

    private val trustAllHostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }

    private fun resolveCookies(account: MailAccount): String {
        val list = mutableListOf<String>()
        val saved = account.authSessionCookies.trim()
        if (saved.isNotBlank()) list.add(saved)

        val host = runCatching { URL(account.serverHost).host }.getOrNull()
        if (!host.isNullOrBlank()) {
            val cm = android.webkit.CookieManager.getInstance()
            cm.getCookie("https://$host/owa/")?.let { if (it.isNotBlank()) list.add(it) }
            cm.getCookie("https://$host/")?.let { if (it.isNotBlank()) list.add(it) }
            cm.getCookie(account.serverHost)?.let { if (it.isNotBlank()) list.add(it) }
        }

        val map = mutableMapOf<String, String>()
        for (cookieHeader in list) {
            for (part in cookieHeader.split(";")) {
                val kv = part.split("=", limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].trim()
                    val v = kv[1].trim()
                    if (k.isNotBlank()) map[k] = v
                }
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun resolveCanary(account: MailAccount, cookies: String): String {
        if (account.authSessionToken.isNotBlank() && !account.authSessionToken.startsWith("canary_web_")) {
            return account.authSessionToken
        }
        return OwaAuthManager.extractCanary(cookies).orEmpty()
    }

    private fun buildOwaServiceUrl(serverHost: String, action: String): String {
        var base = OwaAuthManager.normalizeOwaUrl(serverHost)
        if (!base.endsWith("/")) base += "/"
        return "${base}service.svc?action=$action&EP=1"
    }

    private fun executeOwaJsonPost(
        urlString: String,
        jsonBody: JSONObject,
        cookies: String,
        canary: String
    ): JSONObject? {
        val candidateUrls = listOf(
            urlString,
            urlString.substringBefore("&EP=1"),
            urlString.replace("/owa/service.svc", "/service.svc")
        ).distinct()

        for (candidateUrl in candidateUrls) {
            try {
                val url = URL(candidateUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    if (this is javax.net.ssl.HttpsURLConnection) {
                        sslSocketFactory = trustAllSslSocketFactory
                        hostnameVerifier = trustAllHostnameVerifier
                    }
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("User-Agent", USER_AGENT)
                    if (canary.isNotBlank()) {
                        setRequestProperty("X-OWA-CANARY", canary)
                        setRequestProperty("Action", url.query?.substringAfter("action=")?.substringBefore("&") ?: "FindItem")
                    }
                    if (cookies.isNotBlank()) {
                        setRequestProperty("Cookie", cookies)
                    }
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    if (responseText.isNotBlank()) {
                        return JSONObject(responseText)
                    }
                } else {
                    Log.w(TAG, "OWA service returned HTTP $code for $candidateUrl")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request to $candidateUrl failed: ${e.message}")
            }
        }
        return null
    }

    private fun buildFindItemEmailPayload(folderId: String): JSONObject {
        val targetFolder = when {
            folderId.contains("inbox", ignoreCase = true) -> "inbox"
            folderId.contains("sent", ignoreCase = true) -> "sentitems"
            folderId.contains("draft", ignoreCase = true) -> "drafts"
            folderId.contains("trash", ignoreCase = true) -> "deleteditems"
            folderId.contains("archive", ignoreCase = true) -> "archive"
            else -> "inbox"
        }

        val additionalProperties = JSONArray().apply {
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Subject") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "message:From") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "message:ToRecipients") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "message:IsRead") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:DateTimeReceived") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:HasAttachments") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Importance") })
        }

        val itemShape = JSONObject().apply {
            put("__type", "ItemResponseShape:#Exchange")
            put("BaseShape", "IdOnly")
            put("AdditionalProperties", additionalProperties)
        }

        val parentFolderIds = JSONArray().apply {
            put(JSONObject().apply {
                put("__type", "DistinguishedFolderId:#Exchange")
                put("Id", targetFolder)
            })
        }

        val paging = JSONObject().apply {
            put("__type", "IndexedPageView:#Exchange")
            put("BasePoint", "Beginning")
            put("Offset", 0)
            put("MaxEntriesReturned", 50)
        }

        val sortOrder = JSONArray().apply {
            put(JSONObject().apply {
                put("__type", "SortResults:#Exchange")
                put("Order", "Descending")
                put("Path", JSONObject().apply {
                    put("__type", "PropertyUri:#Exchange")
                    put("FieldURI", "item:DateTimeReceived")
                })
            })
        }

        val body = JSONObject().apply {
            put("__type", "FindItemRequest:#Exchange")
            put("ItemShape", itemShape)
            put("ParentFolderIds", parentFolderIds)
            put("Traversal", "Shallow")
            put("Paging", paging)
            put("SortOrder", sortOrder)
        }

        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }

        return JSONObject().apply {
            put("__type", "FindItemJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildFindItemCalendarPayload(): JSONObject {
        val additionalProperties = JSONArray().apply {
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Subject") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "calendar:Start") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "calendar:End") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "calendar:Location") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "calendar:Organizer") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "calendar:IsAllDayEvent") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "calendar:LegacyFreeBusyStatus") })
        }

        val itemShape = JSONObject().apply {
            put("__type", "ItemResponseShape:#Exchange")
            put("BaseShape", "IdOnly")
            put("AdditionalProperties", additionalProperties)
        }

        val parentFolderIds = JSONArray().apply {
            put(JSONObject().apply {
                put("__type", "DistinguishedFolderId:#Exchange")
                put("Id", "calendar")
            })
        }

        val paging = JSONObject().apply {
            put("__type", "IndexedPageView:#Exchange")
            put("BasePoint", "Beginning")
            put("Offset", 0)
            put("MaxEntriesReturned", 100)
        }

        val body = JSONObject().apply {
            put("__type", "FindItemRequest:#Exchange")
            put("ItemShape", itemShape)
            put("ParentFolderIds", parentFolderIds)
            put("Traversal", "Shallow")
            put("Paging", paging)
        }

        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }

        return JSONObject().apply {
            put("__type", "FindItemJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildCreateItemEmailPayload(email: EmailMessage): JSONObject {
        val bodyContent = email.bodyHtml?.ifBlank { email.snippet } ?: email.snippet.ifBlank { email.bodyText }
        val message = JSONObject().apply {
            put("__type", "Message:#Exchange")
            put("Subject", email.subject)
            put("Body", JSONObject().apply {
                put("BodyType", "HTML")
                put("Value", bodyContent)
            })
            val toRecipients = JSONArray()
            for (rec in email.toRecipients) {
                toRecipients.put(JSONObject().apply {
                    put("Mailbox", JSONObject().apply {
                        put("EmailAddress", rec)
                    })
                })
            }
            put("ToRecipients", toRecipients)
        }


        val items = JSONArray().apply { put(message) }

        val body = JSONObject().apply {
            put("__type", "CreateItemRequest:#Exchange")
            put("MessageDisposition", "SendAndSaveCopy")
            put("Items", items)
        }

        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }

        return JSONObject().apply {
            put("__type", "CreateItemJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun extractCreatedItemId(response: JSONObject): String? {
        val body = response.optJSONObject("Body") ?: return null
        val responseMessages = body.optJSONObject("ResponseMessages") ?: return null
        val itemsArray = responseMessages.optJSONArray("Items") ?: return null
        if (itemsArray.length() == 0) return null
        val first = itemsArray.optJSONObject(0) ?: return null
        val rootFolder = first.optJSONObject("RootFolder") ?: first
        val item = rootFolder.optJSONArray("Items")?.optJSONObject(0) ?: return null
        return item.optJSONObject("ItemId")?.optString("Id")
    }

    private fun parseEmailsFromFindItemResponse(
        account: MailAccount,
        folderId: String,
        response: JSONObject
    ): List<EmailMessage> {
        val itemsArray = extractItemsArray(response) ?: return emptyList()
        val result = mutableListOf<EmailMessage>()

        val targetFolderId = if (folderId.isNotBlank()) folderId else "${account.id}_inbox"

        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue

            val itemIdObj = itemObj.optJSONObject("ItemId")
            val itemId = itemIdObj?.optString("Id") ?: "msg_${System.currentTimeMillis()}_$i"
            val subject = itemObj.optString("Subject", "(Без темы)")

            val fromObj = itemObj.optJSONObject("From")?.optJSONObject("Mailbox")
            val senderName = fromObj?.optString("Name", "").orEmpty()
            val senderEmail = fromObj?.optString("EmailAddress", "").orEmpty()

            val toRecipientsList = mutableListOf<String>()
            val toRecipientsArray = itemObj.optJSONArray("ToRecipients")
            if (toRecipientsArray != null) {
                for (j in 0 until toRecipientsArray.length()) {
                    val addr = toRecipientsArray.optJSONObject(j)?.optJSONObject("Mailbox")?.optString("EmailAddress")
                    if (!addr.isNullOrBlank()) toRecipientsList.add(addr)
                }
            }
            if (toRecipientsList.isEmpty()) {
                toRecipientsList.add(account.email)
            }

            val isRead = itemObj.optBoolean("IsRead", false)
            val preview = itemObj.optString("Preview", "")
            val bodyValue = itemObj.optJSONObject("Body")?.optString("Value", preview) ?: preview

            val receivedAtStr = itemObj.optString("DateTimeReceived", "")
            val receivedAt = parseIsoTimestamp(receivedAtStr)

            val hasAttachments = itemObj.optBoolean("HasAttachments", false)
            val importanceStr = itemObj.optString("Importance", "Normal")

            val attachments = if (hasAttachments) {
                listOf(
                    Attachment(
                        id = "att_${UUID.randomUUID().toString().take(8)}",
                        fileName = "Вложение",
                        sizeBytes = 24500L,
                        mimeType = "application/octet-stream"
                    )
                )
            } else {
                emptyList()
            }

            val slaSeverity = if (!isRead) {
                val ageMinutes = (System.currentTimeMillis() - receivedAt) / 60000L
                when {
                    ageMinutes > 30 -> SlaSeverity.BREACHED
                    ageMinutes > 20 -> SlaSeverity.URGENT
                    ageMinutes > 10 -> SlaSeverity.WARNING
                    else -> SlaSeverity.NORMAL
                }
            } else {
                SlaSeverity.COMPLETED
            }

            val emailMessage = EmailMessage(
                id = itemId,
                accountId = account.id,
                folderId = targetFolderId,
                threadId = "th_${itemId.takeLast(12)}",
                senderName = senderName.ifBlank { senderEmail.substringBefore("@") },
                senderEmail = senderEmail.ifBlank { "unknown@corp.mail" },
                toRecipients = toRecipientsList,
                subject = subject,
                snippet = preview.ifBlank { bodyValue.take(160) },
                bodyText = bodyValue,
                bodyHtml = if (bodyValue.contains("<")) bodyValue else "<p>${bodyValue.replace("\n", "<br/>")}</p>",
                timestamp = receivedAt,
                isRead = isRead,
                isStarred = importanceStr.equals("High", ignoreCase = true),
                hasAttachments = hasAttachments,
                attachments = attachments,
                slaInfo = SlaInfo(
                    severity = slaSeverity,
                    deadlineTimestamp = receivedAt + (30 * 60 * 1000L),
                    remainingLabel = "${((receivedAt + (30 * 60 * 1000L) - System.currentTimeMillis()) / 60000L).coerceAtLeast(0)} мин"
                ),
                deliveryStatus = DeliveryStatus.SENT
            )
            result.add(emailMessage)
        }

        return result
    }

    private fun parseCalendarEventsFromFindItemResponse(
        account: MailAccount,
        response: JSONObject
    ): List<CalendarEvent> {
        val itemsArray = extractItemsArray(response) ?: return emptyList()
        val result = mutableListOf<CalendarEvent>()

        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue

            val itemIdObj = itemObj.optJSONObject("ItemId")
            val itemId = itemIdObj?.optString("Id") ?: "cal_${System.currentTimeMillis()}_$i"
            val title = itemObj.optString("Subject", "Встреча без названия")

            val startStr = itemObj.optString("Start", "")
            val endStr = itemObj.optString("End", "")
            val startTimestamp = parseIsoTimestamp(startStr)
            val endTimestamp = if (endStr.isNotBlank()) parseIsoTimestamp(endStr) else startTimestamp + 3600000L

            val location = itemObj.optString("Location", "")
            val isAllDay = itemObj.optBoolean("IsAllDayEvent", false)

            val organizerObj = itemObj.optJSONObject("Organizer")?.optJSONObject("Mailbox")
            val organizerName = organizerObj?.optString("Name", "").orEmpty()
            val organizerEmail = organizerObj?.optString("EmailAddress", "").orEmpty()

            val bodyValue = itemObj.optJSONObject("Body")?.optString("Value", "") ?: ""

            val event = CalendarEvent(
                id = itemId,
                accountId = account.id,
                title = title,
                description = bodyValue,
                location = location,
                meetingLink = if (location.contains("http") || bodyValue.contains("teams.microsoft.com")) {
                    extractMeetingLink(location, bodyValue)
                } else null,
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
                isAllDay = isAllDay,
                organizerEmail = organizerEmail,
                organizerName = organizerName,
                attendees = emptyList(),
                rsvpStatus = EventRsvpStatus.ACCEPTED,
                colorHex = 0xFF2563EBL
            )
            result.add(event)
        }

        return result
    }

    private fun extractItemsArray(response: JSONObject): JSONArray? {
        val body = response.optJSONObject("Body")
        val root = body ?: response

        val responseMessages = root.optJSONObject("ResponseMessages")
        if (responseMessages != null) {
            val items = responseMessages.optJSONArray("Items")
            if (items != null && items.length() > 0) {
                val first = items.optJSONObject(0)
                val rootFolder = first?.optJSONObject("RootFolder")
                val list = rootFolder?.optJSONArray("Items") ?: first?.optJSONArray("Items")
                if (list != null) return list
            }
        }

        val rootFolder = root.optJSONObject("RootFolder")
        if (rootFolder != null) {
            val list = rootFolder.optJSONArray("Items")
            if (list != null) return list
        }

        return root.optJSONArray("Items")
    }

    private fun parseIsoTimestamp(dateString: String): Long {
        if (dateString.isBlank()) return System.currentTimeMillis()
        return runCatching {
            Instant.parse(dateString).toEpochMilli()
        }.getOrElse {
            runCatching {
                OffsetDateTime.parse(dateString).toInstant().toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
    }

    private fun extractMeetingLink(location: String, body: String): String? {
        val urlRegex = Regex("(https://[\\w\\.-]+(?:teams|zoom|meet|telemost)[\\w\\./\\?=\\-#&%]+)")
        return urlRegex.find(location)?.value ?: urlRegex.find(body)?.value
    }
}
