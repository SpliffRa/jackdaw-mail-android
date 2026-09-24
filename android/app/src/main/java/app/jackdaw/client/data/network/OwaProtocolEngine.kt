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
import android.util.Base64
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.FolderType
import app.jackdaw.client.core.util.cleanEmailPreview
import app.jackdaw.client.core.util.cleanEmailSubject
import android.webkit.CookieManager
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.w3c.dom.Element

class OwaProtocolEngine : MailProtocolEngine {

    companion object {
        private const val TAG = "OwaProtocolEngine"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        @Volatile
        var mailboxSessionCooldownUntil: Long = 0L
    }


    var onSessionUpdated: ((accountId: String, cookies: String, canary: String) -> Unit)? = null

    private data class CachedSession(
        val cookies: String,
        val canary: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val sessionCache = java.util.concurrent.ConcurrentHashMap<String, CachedSession>()

    fun updateSession(accountId: String, serverHost: String, cookies: String, canary: String) {
        sessionCache[accountId] = CachedSession(cookies, canary)
        runCatching {
            val host = URL(OwaAuthManager.normalizeOwaUrl(serverHost)).host
            val cm = android.webkit.CookieManager.getInstance()
            for (part in cookies.split(";")) {
                val kv = part.trim()
                if (kv.isNotBlank()) {
                    cm.setCookie("https://$host/owa/", "$kv; path=/; secure; HttpOnly")
                    cm.setCookie("https://$host/", "$kv; path=/; secure; HttpOnly")
                }
            }
            cm.flush()
        }
        onSessionUpdated?.invoke(accountId, cookies, canary)
    }

    suspend fun ensureSession(account: MailAccount): Pair<String, String> {
        val now = System.currentTimeMillis()
        val cached = sessionCache[account.id]
        if (cached != null && cached.cookies.contains("cadata") && (now - cached.timestamp < 30 * 60 * 1000L)) {
            return Pair(cached.cookies, cached.canary)
        }

        val saved = account.authSessionCookies.trim()
        if (saved.contains("cadata")) {
            val canary = resolveCanary(account, saved).ifBlank { "canary_$now" }
            sessionCache[account.id] = CachedSession(saved, canary)
            return Pair(saved, canary)
        }

        if (account.loginUser.isNotBlank() && account.savedPassword.isNotBlank()) {
            Log.i(TAG, "No active cadata session in cache/account. Performing direct FBA auth for ${account.loginUser}...")
            val authResult = OwaAuthManager.authenticateDirectFba(account.serverHost, account.loginUser, account.savedPassword, account.email)
            if (authResult.isSuccess) {
                Log.i(TAG, "Direct FBA auth succeeded! Session cached.")
                updateSession(account.id, account.serverHost, authResult.sessionCookies, authResult.canaryToken)
                return Pair(authResult.sessionCookies, authResult.canaryToken)
            } else {
                Log.w(TAG, "Direct FBA auth failed: ${authResult.errorMessage}")
            }
        }

        val cookies = resolveCookies(account)
        val canary = resolveCanary(account, cookies).ifBlank { "canary_$now" }
        return Pair(cookies, canary)
    }

    override suspend fun fetchFolders(account: MailAccount): List<Folder> = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        if (serverUrl.isBlank()) {
            Log.w(TAG, "Cannot sync folders: serverHost is blank for account ${account.email}")
            return@withContext emptyList()
        }

        if (System.currentTimeMillis() < mailboxSessionCooldownUntil) {
            val remainingSec = (mailboxSessionCooldownUntil - System.currentTimeMillis()) / 1000L
            Log.w(TAG, "fetchFolders skipped: session limit cooldown active ($remainingSec s remaining)")
            return@withContext emptyList()
        }

        val (cookies, canary) = ensureSession(account)
        val discoveredFolders = mutableMapOf<String, Folder>()

        // 1. Try FindFolder (Shallow then Deep) to discover all custom folders
        val findFolderUrl = buildOwaServiceUrl(serverUrl, "FindFolder")
        for (traversal in listOf("Shallow", "Deep")) {
            try {
                val reqBody = buildFindFolderPayload(traversal)
                val responseJson = executeOwaJsonPost(findFolderUrl, reqBody, cookies, canary, account)
                if (responseJson != null) {
                    val parsed = parseFoldersFromFindFolderResponse(account, responseJson)
                    for (f in parsed) {
                        discoveredFolders[f.id] = f
                    }
                    if (discoveredFolders.isNotEmpty()) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "FindFolder $traversal failed: ${e.message}")
            }
        }

        // 2. Query well-known distinguished folders via GetFolder (inbox, sentitems, deleteditems, drafts, archive)
        // This guarantees standard folders like "Удаленные" with 300+ emails are always populated with accurate server counts!
        try {
            val getFolderUrl = buildOwaServiceUrl(serverUrl, "GetFolder")
            val getFolderPayload = buildGetFolderDistinguishedPayload()
            val responseJson = executeOwaJsonPost(getFolderUrl, getFolderPayload, cookies, canary, account)
            if (responseJson != null) {
                val parsed = parseFoldersFromFindFolderResponse(account, responseJson)
                for (f in parsed) {
                    discoveredFolders[f.id] = f
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GetFolder distinguished folders query failed: ${e.message}")
        }

        // Silent EWS SOAP fallback if OWA discovered no folders
        if (discoveredFolders.isEmpty()) {
            Log.i(TAG, "OWA FindFolder returned 0 folders, silently trying EWS FindFolder...")
            val ewsFolders = fetchFoldersViaEws(account)
            for (f in ewsFolders) {
                discoveredFolders[f.id] = f
            }
        }

        val list = discoveredFolders.values.toList()
        OwaSyncDiagnostics.lastFoldersCount = list.size
        OwaSyncDiagnostics.lastFolderNames = list.map { "${it.name} (${it.unreadCount}/${it.totalCount})" }
        list
    }

    data class EmailFullDetails(
        val id: String,
        val subject: String?,
        val senderName: String?,
        val senderEmail: String?,
        val toRecipients: List<String>?,
        val isRead: Boolean?,
        val isStarred: Boolean?,
        val receivedAt: Long?,
        val hasAttachments: Boolean?,
        val attachments: List<Attachment>,
        val importance: String?,
        val bodyText: String,
        val bodyHtml: String,
        val snippet: String
    )

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

        if (System.currentTimeMillis() < mailboxSessionCooldownUntil) {
            val remainingSec = (mailboxSessionCooldownUntil - System.currentTimeMillis()) / 1000L
            Log.w(TAG, "fetchNewEmails skipped: session limit cooldown active ($remainingSec s remaining)")
            return@withContext emptyList()
        }

        val (cookies, canary) = ensureSession(account)

        val owaServiceUrl = buildOwaServiceUrl(serverUrl, "FindItem")
        val allRawEmails = mutableListOf<EmailMessage>()
        var offset = 0
        val pageSize = 200
        val maxTotalToFetch = 1000

        try {
            var firstResponseJson: JSONObject? = null
            while (offset < maxTotalToFetch) {
                val requestBody = buildFindItemEmailPayload(folderId, offset, pageSize)
                val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account)
                if (responseJson == null) {
                    break
                }
                if (firstResponseJson == null) {
                    firstResponseJson = responseJson
                }

                val pageEmails = parseEmailsFromFindItemResponse(account, folderId, responseJson)
                if (pageEmails.isEmpty()) {
                    break
                }
                allRawEmails.addAll(pageEmails)

                val rootFolder = extractRootFolder(responseJson)
                val includesLast = rootFolder?.optBoolean("IncludesLastItemInRange", true) ?: true
                if (includesLast || pageEmails.size < pageSize) {
                    break
                }
                offset += pageEmails.size
            }

            val finalEmails = if (allRawEmails.isNotEmpty()) {
                allRawEmails.map { email ->
                    val timestamp = email.timestamp
                    val isRead = email.isRead
                    val slaSeverity = if (!isRead) {
                        val ageMinutes = (System.currentTimeMillis() - timestamp) / 60000L
                        when {
                            ageMinutes > 30 -> SlaSeverity.BREACHED
                            ageMinutes > 20 -> SlaSeverity.URGENT
                            ageMinutes > 10 -> SlaSeverity.WARNING
                            else -> SlaSeverity.NORMAL
                        }
                    } else {
                        SlaSeverity.COMPLETED
                    }

                    val bodyText = email.bodyText.ifBlank { email.snippet }
                    val bodyHtml = email.bodyHtml
                    val newSla = SlaInfo(
                        severity = slaSeverity,
                        deadlineTimestamp = timestamp + (30 * 60 * 1000L),
                        remainingLabel = if (slaSeverity == SlaSeverity.COMPLETED) "Ответ дан вовремя" else "${((timestamp + (30 * 60 * 1000L) - System.currentTimeMillis()) / 60000L).coerceAtLeast(0)} мин"
                    )

                    email.copy(
                        bodyText = bodyText,
                        bodyHtml = bodyHtml,
                        slaInfo = newSla
                    )
                }
            } else if (firstResponseJson != null) {
                // OWA FindItem succeeded, but folder is genuinely empty on server
                emptyList()
            } else {
                // OWA FindItem failed/null, silently try EWS SOAP if explicitly configured
                val isEwsCandidate = account.serverHost.contains("/ews", ignoreCase = true)
                if (isEwsCandidate) {
                    val ewsEmails = fetchEmailsViaEws(account, folderId)
                    if (ewsEmails.isNotEmpty()) {
                        OwaSyncDiagnostics.recordSuccess("FindItem(EWS)", buildEwsUrl(serverUrl), 200, "EWS synced ${ewsEmails.size} emails")
                    }
                    ewsEmails
                } else {
                    emptyList()
                }
            }
            OwaSyncDiagnostics.lastEmailsFetchedCount = finalEmails.size
            finalEmails
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch emails from OWA endpoint $owaServiceUrl", e)
            val isEwsCandidate = account.serverHost.contains("/ews", ignoreCase = true)
            val ewsEmails = if (isEwsCandidate) fetchEmailsViaEws(account, folderId) else emptyList()
            if (ewsEmails.isEmpty()) {
                OwaSyncDiagnostics.recordError("FindItem", owaServiceUrl, 0, e.message ?: "Exception")
            }
            OwaSyncDiagnostics.lastEmailsFetchedCount = ewsEmails.size
            ewsEmails
        }
    }

    suspend fun fetchEmailDetails(
        account: MailAccount,
        itemIds: List<String>
    ): Map<String, EmailFullDetails> = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext emptyMap()
        val serverUrl = account.serverHost.trim()
        if (serverUrl.isBlank()) return@withContext emptyMap()

        val (cookies, canary) = ensureSession(account)
        val owaServiceUrl = buildOwaServiceUrl(serverUrl, "GetItem")

        val resultMap = mutableMapOf<String, EmailFullDetails>()

        // Chunk in batches of 20 to conform with Exchange JSON-RPC limits
        for (chunk in itemIds.chunked(20)) {
            try {
                val requestBody = buildGetItemPayload(chunk)
                val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account) ?: continue
                val items = extractGetItemsArray(responseJson) ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val id = item.optJSONObject("ItemId")?.optString("Id") ?: continue
                    val subject = item.optString("Subject", "")

                    val fromObj = item.optJSONObject("From")?.optJSONObject("Mailbox")
                    val senderName = fromObj?.optString("Name", "").orEmpty()
                    val senderEmail = fromObj?.optString("EmailAddress", "").orEmpty()

                    val toRecipientsList = mutableListOf<String>()
                    val toRecipientsArray = item.optJSONArray("ToRecipients")
                    if (toRecipientsArray != null) {
                        for (k in 0 until toRecipientsArray.length()) {
                            val addr = toRecipientsArray.optJSONObject(k)?.optJSONObject("Mailbox")?.optString("EmailAddress")
                            if (!addr.isNullOrBlank()) toRecipientsList.add(addr)
                        }
                    }

                    val isRead = if (item.has("IsRead")) item.optBoolean("IsRead") else null
                    val dateStr = item.optString("DateTimeReceived", "")
                    val receivedAt = if (dateStr.isNotBlank()) parseIsoTimestamp(dateStr) else null
                    val hasAttachments = if (item.has("HasAttachments")) item.optBoolean("HasAttachments") else null
                    val importance = item.optString("Importance", "")

                    val bodyObj = item.optJSONObject("Body")
                    val bodyHtml = bodyObj?.optString("Value", "").orEmpty()
                    val textBody = item.optJSONObject("TextBody")?.optString("Value", "").orEmpty()
                    val normBody = item.optJSONObject("NormalizedBody")?.optString("Value", "").orEmpty()
                    val preview = item.optString("Preview", "").trim()

                    // Parse real attachments and resolve inline CID images into base64 data URIs
                    val attachmentsList = mutableListOf<Attachment>()
                    val attsArray = item.optJSONArray("Attachments")
                    var resolvedHtml = bodyHtml

                    if (attsArray != null) {
                        for (aIdx in 0 until attsArray.length()) {
                            val attObj = attsArray.optJSONObject(aIdx) ?: continue
                            val attId = attObj.optJSONObject("AttachmentId")?.optString("Id")
                                ?: attObj.optString("Id", "")
                            if (attId.isBlank()) continue

                            val name = attObj.optString("Name", "Вложение").ifBlank { "Вложение" }
                            val size = attObj.optLong("Size", 0L)
                            val mime = attObj.optString("ContentType", "application/octet-stream")
                            val isInline = attObj.optBoolean("IsInline", false)
                            val contentId = attObj.optString("ContentId", "")

                            val cleanCid = contentId.removePrefix("<").removeSuffix(">").trim()
                            val isReferencedInBody = cleanCid.isNotBlank() && (
                                resolvedHtml.contains("cid:$cleanCid", ignoreCase = true) ||
                                resolvedHtml.contains("cid:<$cleanCid>", ignoreCase = true)
                            )
                            val isImage = mime.startsWith("image/", ignoreCase = true) ||
                                name.endsWith(".png", true) || name.endsWith(".jpg", true) ||
                                name.endsWith(".jpeg", true) || name.endsWith(".gif", true) || name.endsWith(".webp", true)

                            // If inline image (e.g. signature logo like SMART DS), resolve CID into inline base64 data URI
                            if ((isInline || isReferencedInBody) && isImage) {
                                try {
                                    val imgBytes = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                                        downloadAttachment(account, attId)
                                    }
                                    if (imgBytes != null && imgBytes.isNotEmpty()) {
                                        val b64 = android.util.Base64.encodeToString(imgBytes, android.util.Base64.NO_WRAP)
                                        val effectiveMime = if (mime.startsWith("image/", ignoreCase = true)) mime else when {
                                            name.endsWith(".png", true) -> "image/png"
                                            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
                                            name.endsWith(".gif", true) -> "image/gif"
                                            else -> "image/png"
                                        }
                                        val dataUri = "data:$effectiveMime;base64,$b64"
                                        if (cleanCid.isNotBlank()) {
                                            resolvedHtml = resolvedHtml.replace(Regex("cid:<?" + Regex.escape(cleanCid) + ">?", RegexOption.IGNORE_CASE), dataUri)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to resolve inline CID image $contentId", e)
                                }
                            }

                            // Only filter out if it's purely an inline decorative/signature image referenced in HTML body
                            val isPureInlineSignature = isReferencedInBody && (isInline || isImage)
                            if (!isPureInlineSignature) {
                                attachmentsList.add(
                                    Attachment(
                                        id = attId,
                                        fileName = name,
                                        sizeBytes = size,
                                        mimeType = mime
                                    )
                                )
                            }
                        }
                    }

                    val flagObj = item.optJSONObject("Flag")
                    val flagStatus = flagObj?.optString("FlagStatus", "")
                    val isStarred = flagStatus.equals("Flagged", ignoreCase = true) || importance.equals("High", ignoreCase = true)

                    val cleanText = when {
                        textBody.isNotBlank() -> textBody
                        normBody.isNotBlank() -> normBody
                        resolvedHtml.isNotBlank() -> stripHtml(resolvedHtml)
                        preview.isNotBlank() -> preview
                        else -> ""
                    }
                    val snippet = (preview.ifBlank { cleanText.take(150) }).cleanEmailPreview()

                    resultMap[id] = EmailFullDetails(
                        id = id,
                        subject = subject.ifBlank { null }?.cleanEmailSubject(),
                        senderName = senderName.ifBlank { null },
                        senderEmail = senderEmail.ifBlank { null },
                        toRecipients = if (toRecipientsList.isNotEmpty()) toRecipientsList else null,
                        isRead = isRead,
                        isStarred = isStarred,
                        receivedAt = receivedAt,
                        hasAttachments = (hasAttachments == true) || attachmentsList.isNotEmpty(),
                        attachments = attachmentsList,
                        importance = importance.ifBlank { null },
                        bodyText = cleanText,
                        bodyHtml = resolvedHtml,
                        snippet = snippet
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch email details chunk: ${e.message}")
            }
        }

        resultMap
    }

    override suspend fun fetchEmailBodies(
        account: MailAccount,
        itemIds: List<String>
    ): Map<String, Pair<String, String>> = withContext(Dispatchers.IO) {
        val details = fetchEmailDetails(account, itemIds)
        details.mapValues { Pair(it.value.bodyText, it.value.bodyHtml) }
    }

    override suspend fun fetchEmailBody(account: MailAccount, itemId: String): Pair<String, String>? {
        val bodies = fetchEmailBodies(account, listOf(itemId))
        val body = bodies[itemId]
        if (body != null && (body.first.isNotBlank() || body.second.isNotBlank())) {
            return body
        }
        return null
    }

    override suspend fun fetchEmailFullDetails(account: MailAccount, itemId: String): DetailedEmailContent? {
        val details = fetchEmailDetails(account, listOf(itemId))
        val email = details[itemId] ?: return null
        return DetailedEmailContent(
            bodyText = email.bodyText,
            bodyHtml = email.bodyHtml,
            attachments = email.attachments,
            isStarred = email.isStarred,
            hasAttachments = email.hasAttachments
        )
    }

    override suspend fun downloadAttachment(account: MailAccount, attachmentId: String): ByteArray? = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        val (cookies, canary) = ensureSession(account)
        if (serverUrl.isBlank() || cookies.isBlank()) return@withContext null

        val encodedAttId = java.net.URLEncoder.encode(attachmentId, "UTF-8")
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val owaRoot = if (baseUrl.contains("/owa", ignoreCase = true)) {
            baseUrl.substringBefore("/owa", "") + "/owa/"
        } else {
            "${baseUrl}owa/"
        }

        // 1. Try REST GetFileAttachment endpoint
        try {
            val downloadUrl = "${owaRoot}service.svc/s/GetFileAttachment?id=$encodedAttId"
            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                if (this is javax.net.ssl.HttpsURLConnection) {
                    sslSocketFactory = trustAllSslSocketFactory
                    hostnameVerifier = trustAllHostnameVerifier
                }
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Cookie", cookies)
                if (canary.isNotBlank()) setRequestProperty("X-OWA-CANARY", canary)
                setRequestProperty("User-Agent", USER_AGENT)
                if (account.email.isNotBlank()) setRequestProperty("x-anchormailbox", account.email.trim())
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isNotEmpty()) return@withContext bytes
            } else {
                Log.w(TAG, "GetFileAttachment returned HTTP $code, trying JSON-RPC...")
            }
        } catch (e: Exception) {
            Log.w(TAG, "GetFileAttachment failed for $attachmentId: ${e.message}")
        }

        // 2. Fallback to Exchange JSON-RPC GetAttachment
        try {
            val owaServiceUrl = buildOwaServiceUrl(serverUrl, "GetAttachment")
            val getAttPayload = JSONObject().apply {
                put("__type", "GetAttachmentJsonRequest:#Exchange")
                put("Header", JSONObject().apply {
                    put("__type", "JsonRequestHeaders:#Exchange")
                    put("RequestServerVersion", "Exchange2013")
                })
                put("Body", JSONObject().apply {
                    put("__type", "GetAttachmentRequest:#Exchange")
                    put("AttachmentShape", JSONObject().apply {
                        put("__type", "AttachmentResponseShape:#Exchange")
                    })
                    put("AttachmentIds", JSONArray().apply {
                        put(JSONObject().apply {
                            put("__type", "RequestAttachmentId:#Exchange")
                            put("Id", attachmentId)
                        })
                    })
                })
            }
            val respJson = executeOwaJsonPost(owaServiceUrl, getAttPayload, cookies, canary, account)
            if (respJson != null) {
                val respBody = respJson.optJSONObject("Body") ?: respJson
                val items = respBody.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                if (items != null && items.length() > 0) {
                    val atts = items.optJSONObject(0)?.optJSONArray("Attachments")
                    if (atts != null && atts.length() > 0) {
                        val base64Content = atts.optJSONObject(0)?.optString("Content", "")
                        if (!base64Content.isNullOrBlank()) {
                            val decoded = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
                            if (decoded != null && decoded.isNotEmpty()) {
                                return@withContext decoded
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GetAttachment JSON-RPC failed for $attachmentId: ${e.message}")
        }

        // 3. Fallback to attachment.ashx
        try {
            val ashxUrl = "${owaRoot}attachment.ashx?attachId=$encodedAttId"
            val url = URL(ashxUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                if (this is javax.net.ssl.HttpsURLConnection) {
                    sslSocketFactory = trustAllSslSocketFactory
                    hostnameVerifier = trustAllHostnameVerifier
                }
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Cookie", cookies)
                if (canary.isNotBlank()) setRequestProperty("X-OWA-CANARY", canary)
                setRequestProperty("User-Agent", USER_AGENT)
                if (account.email.isNotBlank()) setRequestProperty("x-anchormailbox", account.email.trim())
            }

            if (conn.responseCode in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isNotEmpty()) return@withContext bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "attachment.ashx failed for $attachmentId: ${e.message}")
        }

        null
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
        val requestBody = buildFindItemCalendarPayload(startRange, endRange)

        try {
            val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account)
                ?: return@withContext emptyList()

            parseCalendarEventsFromFindItemResponse(account, responseJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch calendar events from OWA endpoint $owaServiceUrl", e)
            emptyList()
        }
    }

    override suspend fun sendMessage(account: MailAccount, email: EmailMessage): SendResult = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        val (cookies, canary) = ensureSession(account)

        if (serverUrl.isNotBlank() && cookies.isNotBlank()) {
            try {
                val owaServiceUrl = buildOwaServiceUrl(serverUrl, "CreateItem")
                val requestBody = buildCreateItemEmailPayload(email)
                val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account)
                if (responseJson != null) {
                    val respMessages = responseJson.optJSONObject("Body")?.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                    val firstMsg = respMessages?.optJSONObject(0)
                    if (firstMsg?.optString("ResponseClass") == "Error") {
                        val errMsg = firstMsg.optString("MessageText", "Exchange send error")
                        val errCode = firstMsg.optString("ResponseCode", "")
                        Log.e(TAG, "CreateItem failed with Exchange error: $errCode - $errMsg")
                        return@withContext SendResult(isSuccess = false, errorMessage = "$errCode: $errMsg")
                    }
                    val serverId = extractCreatedItemId(responseJson) ?: "owa_${UUID.randomUUID().toString().take(8)}"
                    Log.i(TAG, "Email successfully sent via Exchange CreateItem (id=$serverId)")
                    return@withContext SendResult(isSuccess = true, serverMessageId = serverId)
                } else {
                    return@withContext SendResult(isSuccess = false, errorMessage = "Сервер Exchange не ответил на отправку письма")
                }
            } catch (e: Exception) {
                Log.e(TAG, "OWA direct send failed", e)
                return@withContext SendResult(isSuccess = false, errorMessage = e.message ?: "Ошибка отправки письма")
            }
        }

        if (serverUrl.isBlank()) {
            // Fallback simulated delivery for local mock accounts
            SendResult(
                isSuccess = true,
                serverMessageId = "srv_${UUID.randomUUID().toString().take(8)}"
            )
        } else {
            SendResult(
                isSuccess = false,
                errorMessage = "Нет активной сессии OWA для отправки"
            )
        }
    }

    override suspend fun updateEmailReadStatus(account: MailAccount, emailId: String, isRead: Boolean): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        val (cookies, canary) = ensureSession(account)

        if (serverUrl.isBlank() || cookies.isBlank()) {
            Log.w(TAG, "Cannot update read status: missing serverUrl or cookies")
            return@withContext false
        }

        try {
            val owaServiceUrl = buildOwaServiceUrl(serverUrl, "UpdateItem")
            val requestBody = buildUpdateItemReadStatusPayload(emailId, isRead)
            val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account)
            if (responseJson != null) {
                val respMessages = responseJson.optJSONObject("Body")?.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                val firstMsg = respMessages?.optJSONObject(0)
                val responseClass = firstMsg?.optString("ResponseClass")
                if (responseClass == "Success") {
                    Log.i(TAG, "Exchange UpdateItem successful: marked $emailId as isRead=$isRead")
                    return@withContext true
                } else {
                    val errMsg = firstMsg?.optString("MessageText", "Unknown error")
                    val errCode = firstMsg?.optString("ResponseCode", "")
                    Log.w(TAG, "Exchange UpdateItem error for $emailId: $errCode - $errMsg")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating read status on Exchange for $emailId", e)
        }
        return@withContext false
    }

    override suspend fun deleteEmail(account: MailAccount, emailId: String, hardDelete: Boolean): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        val (cookies, canary) = ensureSession(account)
        if (serverUrl.isBlank() || cookies.isBlank()) return@withContext false

        try {
            val owaServiceUrl = buildOwaServiceUrl(serverUrl, "DeleteItem")
            val requestBody = buildDeleteItemPayload(emailId, hardDelete)
            val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account)
            if (responseJson != null) {
                val respMessages = responseJson.optJSONObject("Body")?.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                val firstMsg = respMessages?.optJSONObject(0)
                val responseClass = firstMsg?.optString("ResponseClass")
                if (responseClass == "Success") {
                    Log.i(TAG, "Exchange DeleteItem successful for $emailId (hardDelete=$hardDelete)")
                    return@withContext true
                }
            }
            if (hardDelete) {
                // Fallback to SoftDelete
                val fallbackBody = buildDeleteItemPayload(emailId, hardDelete = false, deleteTypeOverride = "SoftDelete")
                val fallbackJson = executeOwaJsonPost(owaServiceUrl, fallbackBody, cookies, canary, account)
                if (fallbackJson != null) {
                    val respMessages = fallbackJson.optJSONObject("Body")?.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                    val firstMsg = respMessages?.optJSONObject(0)
                    val responseClass = firstMsg?.optString("ResponseClass")
                    if (responseClass == "Success") {
                        Log.i(TAG, "Exchange DeleteItem SoftDelete successful for $emailId")
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteEmail for $emailId", e)
        }
        return@withContext false
    }

    override suspend fun emptyTrash(account: MailAccount): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        val (cookies, canary) = ensureSession(account)
        if (serverUrl.isBlank() || cookies.isBlank()) return@withContext false

        try {
            val owaServiceUrl = buildOwaServiceUrl(serverUrl, "EmptyFolder")
            for (delType in listOf("HardDelete", "SoftDelete")) {
                val requestBody = buildEmptyFolderPayload("deleteditems", delType)
                val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account)
                if (responseJson != null) {
                    val respMessages = responseJson.optJSONObject("Body")?.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                    val firstMsg = respMessages?.optJSONObject(0)
                    val responseClass = firstMsg?.optString("ResponseClass")
                    if (responseClass == "Success") {
                        Log.i(TAG, "Exchange EmptyFolder successful with $delType")
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in emptyTrash", e)
        }
        return@withContext false
    }

    override suspend fun moveEmail(account: MailAccount, emailId: String, targetFolderType: FolderType): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        val (cookies, canary) = ensureSession(account)
        if (serverUrl.isBlank() || cookies.isBlank()) return@withContext false

        val targetFolderId = when (targetFolderType) {
            FolderType.INBOX -> "inbox"
            FolderType.ARCHIVE -> "archive"
            FolderType.TRASH -> "deleteditems"
            FolderType.SENT -> "sentitems"
            FolderType.DRAFTS -> "drafts"
            else -> "inbox"
        }

        try {
            val owaServiceUrl = buildOwaServiceUrl(serverUrl, "MoveItem")
            val requestBody = buildMoveItemPayload(emailId, targetFolderId)
            val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account)
            if (responseJson != null) {
                val respMessages = responseJson.optJSONObject("Body")?.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                val firstMsg = respMessages?.optJSONObject(0)
                val responseClass = firstMsg?.optString("ResponseClass")
                if (responseClass == "Success") {
                    Log.i(TAG, "Exchange MoveItem successful: moved $emailId to $targetFolderId")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in moveEmail for $emailId", e)
        }
        return@withContext false
    }

    override suspend fun updateEmailStarStatus(account: MailAccount, emailId: String, isStarred: Boolean): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        val (cookies, canary) = ensureSession(account)
        if (serverUrl.isBlank() || cookies.isBlank()) return@withContext false

        try {
            val owaServiceUrl = buildOwaServiceUrl(serverUrl, "UpdateItem")
            val requestBody = buildUpdateItemFlagPayload(emailId, isStarred)
            val responseJson = executeOwaJsonPost(owaServiceUrl, requestBody, cookies, canary, account)
            if (responseJson != null) {
                val respMessages = responseJson.optJSONObject("Body")?.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                val firstMsg = respMessages?.optJSONObject(0)
                val responseClass = firstMsg?.optString("ResponseClass")
                if (responseClass == "Success") {
                    Log.i(TAG, "Exchange UpdateItem successful: updated flag for $emailId to $isStarred")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateEmailStarStatus for $emailId", e)
        }
        return@withContext false
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
        val map = LinkedHashMap<String, String>()
        
        // 1. Base cookies saved in account
        val saved = account.authSessionCookies.trim()
        if (saved.isNotBlank()) {
            for (part in saved.split(";")) {
                val kv = part.split("=", limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].trim()
                    val v = kv[1].trim()
                    if (k.isNotBlank()) map[k] = v
                }
            }
        }

        // 2. Fresh cookies from system CookieManager (take precedence over stale saved cookies)
        val host = runCatching { URL(account.serverHost).host }.getOrNull()
        if (!host.isNullOrBlank()) {
            val cm = android.webkit.CookieManager.getInstance()
            cm.flush()
            val candidateCookieUrls = listOf(
                "https://$host/owa/",
                "https://$host/",
                "https://$host/owa/service.svc",
                "https://$host/EWS/Exchange.asmx",
                account.serverHost
            )
            for (curl in candidateCookieUrls) {
                val header = cm.getCookie(curl) ?: continue
                for (part in header.split(";")) {
                    val kv = part.split("=", limit = 2)
                    if (kv.size == 2) {
                        val k = kv[0].trim()
                        val v = kv[1].trim()
                        if (k.isNotBlank()) map[k] = v
                    }
                }
            }
        }

        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun resolveCanary(account: MailAccount, cookies: String): String {
        val extracted = OwaAuthManager.extractCanary(cookies)
        if (!extracted.isNullOrBlank()) {
            return extracted
        }
        if (account.authSessionToken.isNotBlank() && !account.authSessionToken.startsWith("canary_web_")) {
            return account.authSessionToken
        }
        return ""
    }

    private fun buildOwaServiceUrl(serverHost: String, action: String): String {
        var base = OwaAuthManager.normalizeOwaUrl(serverHost)
        if (base.contains("/auth", ignoreCase = true)) {
            val before = base.substringBefore("/auth")
            base = if (before.endsWith("/owa") || before.endsWith("/owa/")) {
                if (before.endsWith("/")) before else "$before/"
            } else {
                "${before.trimEnd('/')}/owa/"
            }
        }
        if (!base.endsWith("/")) base += "/"
        return "${base}service.svc?action=$action&EP=1"
    }

    private fun executeOwaJsonPost(
        urlString: String,
        jsonBody: JSONObject,
        cookies: String,
        canary: String,
        account: MailAccount? = null,
        isRetryAfterAuth: Boolean = false
    ): JSONObject? {
        if (System.currentTimeMillis() < mailboxSessionCooldownUntil) {
            val remainingSec = (mailboxSessionCooldownUntil - System.currentTimeMillis()) / 1000L
            Log.w(TAG, "Request blocked by session limit cooldown ($remainingSec s remaining)")
            return null
        }

        val cleanUrl = urlString
            .replace(Regex("/owa/auth/.*service\\.svc", RegexOption.IGNORE_CASE), "/owa/service.svc")
            .replace(Regex("/auth/.*service\\.svc", RegexOption.IGNORE_CASE), "/owa/service.svc")

        val candidateUrls = listOf(
            cleanUrl,
            cleanUrl.substringBefore("&EP=1")
        )

        val effectiveCookies = cookies
        val effectiveCanary = canary.ifBlank {
            OwaAuthManager.extractCanary(effectiveCookies).orEmpty()
        }

        for (candidateUrl in candidateUrls.distinct()) {
            try {
                val url = URL(candidateUrl)
                val actionName = url.query?.substringAfter("action=")?.substringBefore("&") ?: "FindItem"
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    if (this is javax.net.ssl.HttpsURLConnection) {
                        sslSocketFactory = trustAllSslSocketFactory
                        hostnameVerifier = trustAllHostnameVerifier
                    }
                    instanceFollowRedirects = false
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 20000
                    val origin = "https://${url.host}"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    setRequestProperty("Origin", origin)
                    setRequestProperty("Referer", "$origin/owa/")
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Action", actionName)
                    setRequestProperty("X-OWA-ActionName", "${actionName}Action")
                    setRequestProperty("X-OWA-Attempt", "1")
                    if (effectiveCanary.isNotBlank()) {
                        setRequestProperty("X-OWA-CANARY", effectiveCanary)
                    }
                    if (effectiveCookies.isNotBlank()) {
                        setRequestProperty("Cookie", effectiveCookies)
                    }
                    if (account != null && account.email.isNotBlank()) {
                        setRequestProperty("x-anchormailbox", account.email.trim())
                    }
                    // ONLY set Basic Auth if there are NO session cookies:
                    if (effectiveCookies.isBlank() && account != null && account.loginUser.isNotBlank() && account.savedPassword.isNotBlank()) {
                        val authStr = "${account.loginUser.trim()}:${account.savedPassword}"
                        val authBase64 = Base64.encodeToString(authStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic $authBase64")
                    }
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    // Sliding session maintenance: capture Set-Cookie (e.g. X-BackEndCookie or ClientId updates)
                    val setCookies = conn.headerFields.entries
                        .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                        .flatMap { it.value }
                    if (setCookies.isNotEmpty() && account != null) {
                        val mergedMap = OwaAuthManager.parseCookies(effectiveCookies).toMutableMap()
                        for (sc in setCookies) {
                            val kv = sc.substringBefore(";").split("=", limit = 2)
                            if (kv.size == 2 && kv[0].isNotBlank()) {
                                mergedMap[kv[0].trim()] = kv[1].trim()
                            }
                        }
                        val mergedCookies = mergedMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                        val freshCanary = OwaAuthManager.extractCanary(mergedCookies) ?: effectiveCanary
                        updateSession(account.id, account.serverHost, mergedCookies, freshCanary)
                    }

                    val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    if (responseText.isNotBlank()) {
                        if (responseText.trimStart().startsWith("<")) {
                            Log.w(TAG, "OWA endpoint returned HTML page instead of JSON (session expired, HTTP $code)")
                            OwaSyncDiagnostics.recordError(actionName, candidateUrl, code, "Сервер вернул страницу входа (сессия истекла)")
                            if (!isRetryAfterAuth && account != null) {
                                // 1. Try initializing session via GET /owa/
                                val init = kotlinx.coroutines.runBlocking {
                                    OwaAuthManager.initializeOwaSession(account.serverHost, effectiveCookies)
                                }
                                if (init != null) {
                                    onSessionUpdated?.invoke(account.id, init.first, init.second)
                                    return executeOwaJsonPost(urlString, jsonBody, init.first, init.second, account, isRetryAfterAuth = true)
                                }
                                // 2. Try CookieManager
                                val cm = runCatching { CookieManager.getInstance() }.getOrNull()
                                val freshCookies = cm?.getCookie(candidateUrl) ?: cm?.getCookie(account.serverHost)
                                if (!freshCookies.isNullOrBlank() && freshCookies != effectiveCookies) {
                                    val freshCanary = OwaAuthManager.extractCanary(freshCookies).orEmpty()
                                    onSessionUpdated?.invoke(account.id, freshCookies, freshCanary)
                                    return executeOwaJsonPost(urlString, jsonBody, freshCookies, freshCanary, account, isRetryAfterAuth = true)
                                }
                                // 3. Try silent FBA login
                                if (account.loginUser.isNotBlank() && account.savedPassword.isNotBlank()) {
                                    val authResult = kotlinx.coroutines.runBlocking {
                                        OwaAuthManager.authenticateDirectFba(account.serverHost, account.loginUser, account.savedPassword, account.email)
                                    }
                                    if (authResult.isSuccess) {
                                        updateSession(account.id, account.serverHost, authResult.sessionCookies, authResult.canaryToken)
                                        return executeOwaJsonPost(urlString, jsonBody, authResult.sessionCookies, authResult.canaryToken, account, isRetryAfterAuth = true)
                                    }
                                }
                            }
                            return null
                        }
                        val json = JSONObject(responseText)
                        val respMessages = json.optJSONObject("Body")?.optJSONObject("ResponseMessages")?.optJSONArray("Items")
                        val firstMsg = respMessages?.optJSONObject(0)
                        if (firstMsg?.optString("ResponseClass") == "Error") {
                            val errCode = firstMsg.optString("ResponseCode")
                            val errMsg = firstMsg.optString("MessageText")
                            Log.w(TAG, "OWA Exchange error for $actionName ($candidateUrl): $errCode - $errMsg")
                            OwaSyncDiagnostics.recordError(actionName, candidateUrl, code, "Exchange: $errCode - $errMsg")
                        } else {
                            OwaSyncDiagnostics.recordSuccess(actionName, candidateUrl, code, "OK ($actionName)")
                        }
                        return json
                    }
                } else if (code == 449 && !isRetryAfterAuth) {
                    // Exchange "Retry With": server sends ClientId, UC, X-OWA-CANARY, X-BackEndCookie
                    val setCookies = conn.headerFields.entries
                        .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                        .flatMap { it.value }
                    val mergedMap = OwaAuthManager.parseCookies(effectiveCookies).toMutableMap()
                    for (sc in setCookies) {
                        val kv = sc.substringBefore(";").split("=", limit = 2)
                        if (kv.size == 2 && kv[0].isNotBlank()) {
                            mergedMap[kv[0].trim()] = kv[1].trim()
                        }
                    }
                    val mergedCookies = mergedMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    val freshCanary = OwaAuthManager.extractCanary(mergedCookies) ?: effectiveCanary
                    Log.i(TAG, "OWA returned HTTP 449 Retry With. Captured fresh session cookies and canary ($freshCanary). Retrying...")
                    if (account != null) {
                        updateSession(account.id, account.serverHost, mergedCookies, freshCanary)
                    }
                    return executeOwaJsonPost(urlString, jsonBody, mergedCookies, freshCanary, account, isRetryAfterAuth = true)
                } else if ((code == 401 || code == 440 || code == 302 || code == 301) && !isRetryAfterAuth && account != null) {
                    sessionCache.remove(account.id)
                    val redirectLoc = conn.getHeaderField("Location").orEmpty()
                    Log.w(TAG, "OWA session expired or redirected (HTTP $code to $redirectLoc). Attempting silent session recovery...")
                    OwaSyncDiagnostics.recordError(actionName, candidateUrl, code, "Сессия истекла (HTTP $code). Редирект: $redirectLoc")

                    // 1. Silent FBA direct auth if credentials saved (essential for HTTP 440 Login Timeout!)
                    if (account.loginUser.isNotBlank() && account.savedPassword.isNotBlank()) {
                        Log.i(TAG, "Attempting silent FBA direct auth for ${account.loginUser}...")
                        val authResult = kotlinx.coroutines.runBlocking {
                            OwaAuthManager.authenticateDirectFba(account.serverHost, account.loginUser, account.savedPassword, account.email)
                        }
                        if (authResult.isSuccess) {
                            Log.i(TAG, "Silent FBA re-auth succeeded! Retrying request...")
                            updateSession(account.id, account.serverHost, authResult.sessionCookies, authResult.canaryToken)
                            return executeOwaJsonPost(urlString, jsonBody, authResult.sessionCookies, authResult.canaryToken, account, isRetryAfterAuth = true)
                        } else {
                            Log.w(TAG, "Silent FBA re-auth failed: ${authResult.errorMessage}")
                        }
                    }

                    // 2. Silent session init/refresh via GET /owa/
                    val init = kotlinx.coroutines.runBlocking {
                        OwaAuthManager.initializeOwaSession(account.serverHost, effectiveCookies)
                    }
                    if (init != null) {
                        Log.i(TAG, "Silent session init via GET /owa/ succeeded! Retrying request...")
                        onSessionUpdated?.invoke(account.id, init.first, init.second)
                        return executeOwaJsonPost(urlString, jsonBody, init.first, init.second, account, isRetryAfterAuth = true)
                    }

                    // 3. Silent recovery via system CookieManager
                    val cm = runCatching { CookieManager.getInstance() }.getOrNull()
                    val freshCookies = cm?.getCookie(candidateUrl) ?: cm?.getCookie(account.serverHost)
                    if (!freshCookies.isNullOrBlank() && freshCookies != effectiveCookies) {
                        Log.i(TAG, "Silent cookie recovery from CookieManager succeeded! Retrying request...")
                        val freshCanary = OwaAuthManager.extractCanary(freshCookies).orEmpty()
                        onSessionUpdated?.invoke(account.id, freshCookies, freshCanary)
                        return executeOwaJsonPost(urlString, jsonBody, freshCookies, freshCanary, account, isRetryAfterAuth = true)
                    }
                } else {
                    val err = runCatching {
                        BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    }.getOrNull()
                    val isSessionLimit = code == 500 && err != null && (
                        err.contains("too many active sessions", ignoreCase = true) ||
                        err.contains("MapiExceptionSessionLimit", ignoreCase = true)
                    )
                    if (isSessionLimit) {
                        mailboxSessionCooldownUntil = System.currentTimeMillis() + 4 * 60 * 1000L
                        Log.w(TAG, "Exchange session limit reached (Too many active sessions). Setting 4-minute cooldown.")
                        OwaSyncDiagnostics.recordError(actionName, candidateUrl, code, "Exchange: лимит сессий почтового ящика (пауза 4 мин для сброса)")
                        return null
                    } else {
                        OwaSyncDiagnostics.recordError(actionName, candidateUrl, code, "HTTP $code: ${err?.take(120)}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request to $candidateUrl failed: ${e.message}")
            }
        }
        return null
    }


    private fun buildFindFolderPayload(traversal: String = "Deep"): JSONObject {
        val folderShape = JSONObject().apply {
            put("__type", "FolderResponseShape:#Exchange")
            put("BaseShape", "Default")
        }

        val parentFolderIds = JSONArray().apply {
            put(JSONObject().apply {
                put("__type", "DistinguishedFolderId:#Exchange")
                put("Id", "msgfolderroot")
            })
        }

        val paging = JSONObject().apply {
            put("__type", "IndexedPageView:#Exchange")
            put("BasePoint", "Beginning")
            put("Offset", 0)
            put("MaxEntriesReturned", 100)
        }

        val body = JSONObject().apply {
            put("__type", "FindFolderRequest:#Exchange")
            put("FolderShape", folderShape)
            put("ParentFolderIds", parentFolderIds)
            put("Traversal", traversal)
            put("Paging", paging)
        }

        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }

        return JSONObject().apply {
            put("__type", "FindFolderJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildGetFolderDistinguishedPayload(): JSONObject {
        val folderShape = JSONObject().apply {
            put("__type", "FolderResponseShape:#Exchange")
            put("BaseShape", "Default")
        }

        val folderIds = JSONArray().apply {
            for (distId in listOf("inbox", "sentitems", "deleteditems", "drafts", "archive", "junkemail")) {
                put(JSONObject().apply {
                    put("__type", "DistinguishedFolderId:#Exchange")
                    put("Id", distId)
                })
            }
        }

        val body = JSONObject().apply {
            put("__type", "GetFolderRequest:#Exchange")
            put("FolderShape", folderShape)
            put("FolderIds", folderIds)
        }

        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }

        return JSONObject().apply {
            put("__type", "GetFolderJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildGetItemPayload(itemIds: List<String>): JSONObject {
        val itemIdsArray = JSONArray()
        for (id in itemIds) {
            itemIdsArray.put(JSONObject().apply {
                put("__type", "ItemId:#Exchange")
                put("Id", id)
            })
        }

        val additionalProperties = JSONArray().apply {
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Body") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:NormalizedBody") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:TextBody") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Attachments") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Subject") })
        }

        val itemShape = JSONObject().apply {
            put("__type", "ItemResponseShape:#Exchange")
            put("BaseShape", "AllProperties")
            put("BodyType", "Best")
            put("AdditionalProperties", additionalProperties)
        }

        val body = JSONObject().apply {
            put("__type", "GetItemRequest:#Exchange")
            put("ItemShape", itemShape)
            put("ItemIds", itemIdsArray)
        }

        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }

        return JSONObject().apply {
            put("__type", "GetItemJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    internal fun buildFindItemEmailPayload(folderId: String, offset: Int = 0, maxEntries: Int = 200): JSONObject {
        val targetFolder = when {
            folderId.endsWith("inbox", ignoreCase = true) || folderId.equals("inbox", ignoreCase = true) -> "inbox"
            folderId.endsWith("sent", ignoreCase = true) || folderId.equals("sentitems", ignoreCase = true) -> "sentitems"
            folderId.endsWith("drafts", ignoreCase = true) || folderId.equals("drafts", ignoreCase = true) -> "drafts"
            folderId.endsWith("trash", ignoreCase = true) || folderId.endsWith("deleted", ignoreCase = true) || folderId.equals("deleteditems", ignoreCase = true) -> "deleteditems"
            folderId.endsWith("archive", ignoreCase = true) || folderId.equals("archive", ignoreCase = true) -> "archive"
            folderId.endsWith("junk", ignoreCase = true) || folderId.equals("junkemail", ignoreCase = true) -> "junkemail"
            else -> folderId
        }

        val additionalProperties = JSONArray().apply {
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Subject") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "message:IsRead") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:DateTimeReceived") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:HasAttachments") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Importance") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "message:From") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "message:ToRecipients") })
            put(JSONObject().apply { put("__type", "PropertyUri:#Exchange"); put("FieldURI", "item:Preview") })
        }

        val itemShape = JSONObject().apply {
            put("__type", "ItemResponseShape:#Exchange")
            put("BaseShape", "IdOnly")
            put("AdditionalProperties", additionalProperties)
        }

        val isDistinguished = targetFolder in listOf("inbox", "sentitems", "drafts", "deleteditems", "archive", "junkemail")
        val parentFolderIds = JSONArray().apply {
            put(JSONObject().apply {
                if (isDistinguished) {
                    put("__type", "DistinguishedFolderId:#Exchange")
                    put("Id", targetFolder)
                } else {
                    put("__type", "FolderId:#Exchange")
                    put("Id", targetFolder)
                }
            })
        }

        val paging = JSONObject().apply {
            put("__type", "IndexedPageView:#Exchange")
            put("BasePoint", "Beginning")
            put("Offset", offset)
            put("MaxEntriesReturned", maxEntries)
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

    private fun buildFindItemCalendarPayload(startRange: Long, endRange: Long): JSONObject {
        val startIso = Instant.ofEpochMilli(startRange).toString()
        val endIso = Instant.ofEpochMilli(endRange).toString()

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

        val calendarView = JSONObject().apply {
            put("__type", "CalendarView:#Exchange")
            put("StartDate", startIso)
            put("EndDate", endIso)
            put("MaxEntriesReturned", 100)
        }

        val body = JSONObject().apply {
            put("__type", "FindItemRequest:#Exchange")
            put("ItemShape", itemShape)
            put("ParentFolderIds", parentFolderIds)
            put("Traversal", "Shallow")
            put("CalendarView", calendarView)
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
        val rawBody = when {
            !email.bodyHtml.isNullOrBlank() -> email.bodyHtml
            email.bodyText.isNotBlank() -> email.bodyText
            else -> email.snippet
        }
        val bodyContent = if (rawBody.contains("<html", ignoreCase = true) || rawBody.contains("<p>", ignoreCase = true) || rawBody.contains("<div", ignoreCase = true)) {
            rawBody
        } else {
            rawBody.replace("\n", "<br/>")
        }

        val message = JSONObject().apply {
            put("__type", "Message:#Exchange")
            put("Subject", email.subject)
            put("Body", JSONObject().apply {
                put("BodyType", "HTML")
                put("Value", bodyContent)
            })
            val toRecipients = JSONArray()
            for (rec in email.toRecipients) {
                if (rec.isNotBlank()) {
                    toRecipients.put(JSONObject().apply {
                        put("EmailAddress", rec.trim())
                        put("RoutingType", "SMTP")
                    })
                }
            }
            put("ToRecipients", toRecipients)

            if (email.ccRecipients.isNotEmpty()) {
                val ccRecipients = JSONArray()
                for (rec in email.ccRecipients) {
                    if (rec.isNotBlank()) {
                        ccRecipients.put(JSONObject().apply {
                            put("EmailAddress", rec.trim())
                            put("RoutingType", "SMTP")
                        })
                    }
                }
                put("CcRecipients", ccRecipients)
            }
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

    private fun buildUpdateItemReadStatusPayload(emailId: String, isRead: Boolean): JSONObject {
        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }

        val path = JSONObject().apply {
            put("__type", "PropertyUri:#Exchange")
            put("FieldURI", "message:IsRead")
        }

        val item = JSONObject().apply {
            put("__type", "Message:#Exchange")
            put("IsRead", isRead)
        }

        val setItemField = JSONObject().apply {
            put("__type", "SetItemField:#Exchange")
            put("Path", path)
            put("Item", item)
        }

        val updates = JSONArray().apply {
            put(setItemField)
        }

        val itemChange = JSONObject().apply {
            put("__type", "ItemChange:#Exchange")
            put("ItemId", JSONObject().apply {
                put("__type", "ItemId:#Exchange")
                put("Id", emailId)
            })
            put("Updates", updates)
        }

        val body = JSONObject().apply {
            put("__type", "UpdateItemRequest:#Exchange")
            put("MessageDisposition", "SaveOnly")
            put("ConflictResolution", "AlwaysOverwrite")
            put("ItemChanges", JSONArray().apply { put(itemChange) })
        }

        return JSONObject().apply {
            put("__type", "UpdateItemJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildDeleteItemPayload(emailId: String, hardDelete: Boolean, deleteTypeOverride: String? = null): JSONObject {
        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }
        val itemIds = JSONArray().apply {
            put(JSONObject().apply {
                put("__type", "ItemId:#Exchange")
                put("Id", emailId)
            })
        }
        val deleteType = deleteTypeOverride ?: if (hardDelete) "HardDelete" else "MoveToDeletedItems"
        val body = JSONObject().apply {
            put("__type", "DeleteItemRequest:#Exchange")
            put("DeleteType", deleteType)
            put("ItemIds", itemIds)
        }
        return JSONObject().apply {
            put("__type", "DeleteItemJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildEmptyFolderPayload(folderDistinguishedId: String = "deleteditems", deleteType: String = "HardDelete"): JSONObject {
        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }
        val folderIds = JSONArray().apply {
            put(JSONObject().apply {
                put("__type", "DistinguishedFolderId:#Exchange")
                put("Id", folderDistinguishedId)
            })
        }
        val body = JSONObject().apply {
            put("__type", "EmptyFolderRequest:#Exchange")
            put("FolderIds", folderIds)
            put("DeleteType", deleteType)
            put("DeleteSubFolders", false)
        }
        return JSONObject().apply {
            put("__type", "EmptyFolderJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildMoveItemPayload(emailId: String, targetDistinguishedFolder: String): JSONObject {
        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }
        val toFolderId = JSONObject().apply {
            put("__type", "TargetFolderId:#Exchange")
            put("BaseFolderId", JSONObject().apply {
                put("__type", "DistinguishedFolderId:#Exchange")
                put("Id", targetDistinguishedFolder)
            })
        }
        val itemIds = JSONArray().apply {
            put(JSONObject().apply {
                put("__type", "ItemId:#Exchange")
                put("Id", emailId)
            })
        }
        val body = JSONObject().apply {
            put("__type", "MoveItemRequest:#Exchange")
            put("ToFolderId", toFolderId)
            put("ItemIds", itemIds)
        }
        return JSONObject().apply {
            put("__type", "MoveItemJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildUpdateItemFlagPayload(emailId: String, isStarred: Boolean): JSONObject {
        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }
        val path = JSONObject().apply {
            put("__type", "PropertyUri:#Exchange")
            put("FieldURI", "item:Flag")
        }
        val item = JSONObject().apply {
            put("__type", "Message:#Exchange")
            put("Flag", JSONObject().apply {
                put("__type", "FlagType:#Exchange")
                put("CompleteDate", JSONObject.NULL)
                put("DueDate", JSONObject.NULL)
                put("StartDate", JSONObject.NULL)
                put("FlagStatus", if (isStarred) "Flagged" else "NotFlagged")
            })
        }
        val setItemField = JSONObject().apply {
            put("__type", "SetItemField:#Exchange")
            put("Path", path)
            put("Item", item)
        }
        val itemChange = JSONObject().apply {
            put("__type", "ItemChange:#Exchange")
            put("ItemId", JSONObject().apply {
                put("__type", "ItemId:#Exchange")
                put("Id", emailId)
            })
            put("Updates", JSONArray().apply { put(setItemField) })
        }
        val body = JSONObject().apply {
            put("__type", "UpdateItemRequest:#Exchange")
            put("MessageDisposition", "SaveOnly")
            put("ConflictResolution", "AlwaysOverwrite")
            put("SendCalendarInvitationsOrCancellations", "SendToNone")
            put("SuppressReadReceipts", true)
            put("ItemChanges", JSONArray().apply { put(itemChange) })
        }
        return JSONObject().apply {
            put("__type", "UpdateItemJsonRequest:#Exchange")
            put("Header", header)
            put("Body", body)
        }
    }

    private fun buildUpdateItemImportancePayload(emailId: String, importance: String): JSONObject {
        val header = JSONObject().apply {
            put("__type", "JsonRequestHeaders:#Exchange")
            put("RequestServerVersion", "Exchange2013")
        }
        val path = JSONObject().apply {
            put("__type", "PropertyUri:#Exchange")
            put("FieldURI", "item:Importance")
        }
        val item = JSONObject().apply {
            put("__type", "Item:#Exchange")
            put("Importance", importance)
        }
        val setItemField = JSONObject().apply {
            put("__type", "SetItemField:#Exchange")
            put("Path", path)
            put("Item", item)
        }
        val itemChange = JSONObject().apply {
            put("__type", "ItemChange:#Exchange")
            put("ItemId", JSONObject().apply {
                put("__type", "ItemId:#Exchange")
                put("Id", emailId)
            })
            put("Updates", JSONArray().apply { put(setItemField) })
        }
        val body = JSONObject().apply {
            put("__type", "UpdateItemRequest:#Exchange")
            put("MessageDisposition", "SaveOnly")
            put("ConflictResolution", "AlwaysOverwrite")
            put("ItemChanges", JSONArray().apply { put(itemChange) })
        }
        return JSONObject().apply {
            put("__type", "UpdateItemJsonRequest:#Exchange")
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

            val attachmentsList = mutableListOf<Attachment>()
            val attachmentsArray = itemObj.optJSONArray("Attachments")
            if (attachmentsArray != null && attachmentsArray.length() > 0) {
                for (a in 0 until attachmentsArray.length()) {
                    val attObj = attachmentsArray.optJSONObject(a) ?: continue
                    val attId = attObj.optJSONObject("AttachmentId")?.optString("Id")
                        ?: attObj.optString("Id", "")
                    if (attId.isBlank()) continue
                    val name = attObj.optString("Name", "Вложение").ifBlank { "Вложение" }
                    val size = attObj.optLong("Size", 0L)
                    val mime = attObj.optString("ContentType", "application/octet-stream")
                    attachmentsList.add(
                        Attachment(
                            id = attId,
                            fileName = name,
                            sizeBytes = size,
                            mimeType = mime
                        )
                    )
                }
            }
            val attachments = attachmentsList.distinctBy { "${it.fileName}_${it.sizeBytes}" }

            val flagObj = itemObj.optJSONObject("Flag")
            val flagStatus = flagObj?.optString("FlagStatus", "")
            val isStarred = flagStatus.equals("Flagged", ignoreCase = true) || importanceStr.equals("High", ignoreCase = true)

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
                subject = subject.cleanEmailSubject(),
                snippet = (preview.ifBlank { bodyValue.take(160) }).cleanEmailPreview(),
                bodyText = bodyValue,
                bodyHtml = null,
                timestamp = receivedAt,
                isRead = isRead,
                isStarred = isStarred,
                hasAttachments = hasAttachments || attachments.isNotEmpty(),
                attachments = attachments,
                slaInfo = SlaInfo(
                    severity = slaSeverity,
                    deadlineTimestamp = receivedAt + (30 * 60 * 1000L),
                    remainingLabel = if (slaSeverity == SlaSeverity.COMPLETED) "Ответ дан вовремя" else "${((receivedAt + (30 * 60 * 1000L) - System.currentTimeMillis()) / 60000L).coerceAtLeast(0)} мин"
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

    private fun extractRootFolder(response: JSONObject): JSONObject? {
        val body = response.optJSONObject("Body")
        val root = body ?: response

        val responseMessages = root.optJSONObject("ResponseMessages")
        if (responseMessages != null) {
            val items = responseMessages.optJSONArray("Items")
            if (items != null && items.length() > 0) {
                val first = items.optJSONObject(0)
                val rootFolder = first?.optJSONObject("RootFolder")
                if (rootFolder != null) return rootFolder
            }
        }
        return root.optJSONObject("RootFolder")
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

    private fun parseFoldersFromFindFolderResponse(
        account: MailAccount,
        response: JSONObject
    ): List<Folder> {
        val foldersArray = extractFoldersArray(response) ?: return emptyList()
        val result = mutableListOf<Folder>()

        for (i in 0 until foldersArray.length()) {
            val folderObj = foldersArray.optJSONObject(i) ?: continue
            val folderIdObj = folderObj.optJSONObject("FolderId")
            val serverFolderId = folderIdObj?.optString("Id") ?: continue
            val displayName = folderObj.optString("DisplayName", "").trim()
            if (displayName.isBlank()) continue

            val folderTypeStr = folderObj.optString("__type", "")
            val folderClass = folderObj.optString("FolderClass", "")
            val lowerName = displayName.lowercase()

            // Exclude virtual search folders, non-mail classes, and Exchange internal plumbing
            if (folderTypeStr.contains("SearchFolder", ignoreCase = true) ||
                folderClass.startsWith("IPF.SearchFolder", ignoreCase = true) ||
                folderClass.startsWith("IPF.Appointment", ignoreCase = true) ||
                folderClass.startsWith("IPF.Contact", ignoreCase = true) ||
                folderClass.startsWith("IPF.Task", ignoreCase = true) ||
                folderClass.startsWith("IPF.Configuration", ignoreCase = true) ||
                folderClass.startsWith("IPF.StickyNote", ignoreCase = true) ||
                folderClass.startsWith("IPF.Journal", ignoreCase = true) ||
                folderClass.startsWith("IPF.Shortcut", ignoreCase = true) ||
                (folderClass.isNotEmpty() && !folderClass.startsWith("IPF.Note", ignoreCase = true)) ||
                lowerName == "календарь" || lowerName == "calendar" ||
                lowerName == "контакты" || lowerName == "contacts" ||
                lowerName == "задачи" || lowerName == "tasks" ||
                lowerName == "дни рождения" || lowerName == "журнал" ||
                lowerName == "заметки" || lowerName == "notes" ||
                lowerName == "организации" ||
                lowerName.contains("recipient cache") ||
                lowerName.contains("gal contacts") ||
                lowerName.contains("organizational contacts") ||
                lowerName.contains("peoplecentric") ||
                lowerName.contains("external contacts") ||
                lowerName.contains("externalcontacts") ||
                lowerName.contains("conversation action") ||
                lowerName.contains("quick step") ||
                lowerName.contains("настройка быстрых") ||
                lowerName.contains("yammer") ||
                lowerName.contains("ошибки синхронизации") ||
                lowerName.contains("sync issues") ||
                lowerName.contains("конфликты") ||
                lowerName.contains("conflicts") ||
                lowerName.contains("локальные ошибки") ||
                lowerName.contains("ошибки сервера") ||
                lowerName.contains("failures") ||
                lowerName.contains("файлы") ||
                lowerName.contains("files") ||
                lowerName.contains("rss") ||
                lowerName.contains("feeds") ||
                lowerName.contains("search folders") ||
                lowerName.contains("папки поиска") ||
                lowerName.contains("social activity") ||
                lowerName.contains("common views") ||
                lowerName.contains("sharing") ||
                lowerName.contains("shortcuts") ||
                lowerName.contains("spooler") ||
                lowerName.contains("voice mail") ||
                lowerName.contains("голосовая почта") ||
                lowerName.contains("news feed") ||
                lowerName.contains("новости") ||
                lowerName.contains("conversation history") ||
                lowerName.contains("журнал бесед") ||
                lowerName.contains("suggested contacts") ||
                lowerName.contains("companies") ||
                lowerName.contains("junk") ||
                lowerName.contains("нежелательн") ||
                lowerName == "buddies" ||
                lowerName.contains("inbound") ||
                lowerName.contains("outbound") ||
                lowerName.contains("clutter") ||
                lowerName.contains("scheduled") ||
                lowerName.contains("spamsubscriptions") ||
                lowerName.contains("personalmetadata") ||
                lowerName.contains("recoverable items") ||
                lowerName.contains("deletions") ||
                lowerName.contains("purges") ||
                lowerName.contains("versions") ||
                lowerName.contains("discoveryholds") ||
                lowerName.startsWith("{") ||
                lowerName.startsWith("@") ||
                lowerName.startsWith(".") ||
                lowerName.matches(Regex(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*", RegexOption.IGNORE_CASE))
            ) {
                continue
            }

            val unreadCount = folderObj.optInt("UnreadCount", 0)
            val totalCount = folderObj.optInt("TotalCount", 0)
            val distinguishedId = folderObj.optString("DistinguishedFolderId", "").lowercase()

            val folderType = when {
                distinguishedId == "inbox" || displayName.equals("Входящие", ignoreCase = true) || displayName.equals("Inbox", ignoreCase = true) -> FolderType.INBOX
                distinguishedId == "sentitems" || displayName.equals("Отправленные", ignoreCase = true) || displayName.equals("Sent Items", ignoreCase = true) || displayName.equals("Sent", ignoreCase = true) -> FolderType.SENT
                distinguishedId == "drafts" || displayName.equals("Черновики", ignoreCase = true) || displayName.equals("Drafts", ignoreCase = true) -> FolderType.DRAFTS
                distinguishedId == "deleteditems" || displayName.equals("Удаленные", ignoreCase = true) || displayName.equals("Deleted Items", ignoreCase = true) || displayName.equals("Корзина", ignoreCase = true) || displayName.equals("Trash", ignoreCase = true) -> FolderType.TRASH
                distinguishedId == "archive" || displayName.equals("Архив", ignoreCase = true) || displayName.equals("Archive", ignoreCase = true) -> FolderType.ARCHIVE
                distinguishedId == "outbox" || displayName.equals("Исходящие", ignoreCase = true) || displayName.equals("Outbox", ignoreCase = true) -> FolderType.OUTBOX
                else -> FolderType.CUSTOM
            }

            val folderLocalId = when (folderType) {
                FolderType.INBOX -> "${account.id}_inbox"
                FolderType.SENT -> "${account.id}_sent"
                FolderType.DRAFTS -> "${account.id}_drafts"
                FolderType.TRASH -> "${account.id}_trash"
                FolderType.ARCHIVE -> "${account.id}_archive"
                FolderType.OUTBOX -> "${account.id}_outbox"
                FolderType.SLA_ALERTS -> "${account.id}_sla_alerts"
                FolderType.CUSTOM -> serverFolderId
            }

            result.add(
                Folder(
                    id = folderLocalId,
                    accountId = account.id,
                    name = displayName,
                    type = folderType,
                    unreadCount = unreadCount,
                    totalCount = totalCount
                )
            )
        }

        return result
    }

    private fun extractFoldersArray(response: JSONObject): JSONArray? {
        val body = response.optJSONObject("Body")
        val root = body ?: response
        val combined = JSONArray()

        val responseMessages = root.optJSONObject("ResponseMessages")
        if (responseMessages != null) {
            val items = responseMessages.optJSONArray("Items")
            if (items != null && items.length() > 0) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val rootFolder = item.optJSONObject("RootFolder")
                    val list = rootFolder?.optJSONArray("Folders") ?: item.optJSONArray("Folders")
                    if (list != null) {
                        for (j in 0 until list.length()) {
                            list.optJSONObject(j)?.let { combined.put(it) }
                        }
                    } else if (item.has("FolderId")) {
                        combined.put(item)
                    }
                }
                if (combined.length() > 0) return combined
            }
        }

        val rootFolder = root.optJSONObject("RootFolder")
        if (rootFolder != null) {
            val list = rootFolder.optJSONArray("Folders")
            if (list != null) return list
        }

        return root.optJSONArray("Folders")
    }

    private fun extractGetItemsArray(response: JSONObject): JSONArray? {
        val body = response.optJSONObject("Body")
        val root = body ?: response
        val combined = JSONArray()

        val responseMessages = root.optJSONObject("ResponseMessages")
        if (responseMessages != null) {
            val items = responseMessages.optJSONArray("Items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val respMsg = items.optJSONObject(i) ?: continue
                    val subItems = respMsg.optJSONArray("Items")
                    if (subItems != null) {
                        for (j in 0 until subItems.length()) {
                            subItems.optJSONObject(j)?.let { combined.put(it) }
                        }
                    } else if (respMsg.has("ItemId")) {
                        combined.put(respMsg)
                    }
                }
                if (combined.length() > 0) return combined
            }
        }

        return root.optJSONArray("Items")
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return runCatching {
            // 1. Remove <head>...</head> and <style>...</style> blocks (including inline Outlook CSS)
            var cleaned = html
                .replace(Regex("<head[^>]*>.*?</head>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
                .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
                .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            // 2. Remove HTML comments (<!-- ... -->) which often contain Outlook CSS rules
            cleaned = cleaned.replace(Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL)), "")
            // 3. Remove cid: image src attributes to avoid "cid:image001.png@..." leaking into text
            cleaned = cleaned.replace(Regex("src=[\"']cid:[^\"']*[\"']", RegexOption.IGNORE_CASE), "src=\"\"")
            // 4. Parse remaining HTML to plain text
            val plain = android.text.Html.fromHtml(cleaned, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            // 5. Collapse whitespace and trim
            plain.replace(Regex("\\s+"), " ").trim()
        }.getOrElse {
            html.replace(Regex("<[^>]*>"), " ").replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("\\s+"), " ").trim()
        }
    }

    // ==========================================
    // Silent EWS (Exchange Web Services) Engine
    // ==========================================

    fun buildEwsUrl(serverHost: String): String {
        val base = serverHost.trim().trimEnd('/')
        val lower = base.lowercase()
        val owaIdx = lower.indexOf("/owa")
        val authIdx = lower.indexOf("/auth")
        val root = when {
            owaIdx != -1 -> base.substring(0, owaIdx)
            authIdx != -1 -> base.substring(0, authIdx)
            else -> base
        }
        return "$root/EWS/Exchange.asmx"
    }

    private fun executeEwsSoapPost(
        serverUrl: String,
        soapAction: String,
        soapBody: String,
        account: MailAccount
    ): String? {
        val ewsUrl = buildEwsUrl(serverUrl)
        val candidateUrls = listOf(
            ewsUrl,
            ewsUrl.replace("/EWS/", "/ews/"),
            ewsUrl.replace("/owa/", "/").replace("/OWA/", "/")
        ).distinct()

        val cookies = resolveCookies(account)
        val domainFromHost = runCatching {
            URL(serverUrl).host.split(".").let { parts ->
                if (parts.size >= 2) parts[parts.size - 2] else ""
            }
        }.getOrDefault("")

        val userCandidates = mutableListOf<String>()
        if (account.loginUser.isNotBlank()) userCandidates.add(account.loginUser.trim())
        if (account.email.isNotBlank() && account.email != account.loginUser) userCandidates.add(account.email.trim())
        if (account.loginUser.isNotBlank() && !account.loginUser.contains("@") && !account.loginUser.contains("\\") && domainFromHost.isNotBlank()) {
            userCandidates.add("$domainFromHost\\${account.loginUser.trim()}")
        }
        if (userCandidates.isEmpty()) userCandidates.add("")

        for (candidate in candidateUrls) {
            for (authUser in userCandidates) {
                try {
                    val url = URL(candidate)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        if (this is javax.net.ssl.HttpsURLConnection) {
                            sslSocketFactory = trustAllSslSocketFactory
                            hostnameVerifier = trustAllHostnameVerifier
                        }
                        instanceFollowRedirects = true
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 15000
                        readTimeout = 20000
                        setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                        setRequestProperty("SOAPAction", "\"http://schemas.microsoft.com/exchange/services/2006/messages/$soapAction\"")
                        setRequestProperty("User-Agent", USER_AGENT)
                        if (account.email.isNotBlank()) {
                            setRequestProperty("x-anchormailbox", account.email.trim())
                        }
                        if (authUser.isNotBlank() && account.savedPassword.isNotBlank()) {
                            val authStr = "$authUser:${account.savedPassword}"
                            val authBase64 = Base64.encodeToString(authStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                            setRequestProperty("Authorization", "Basic $authBase64")
                        }
                        if (cookies.isNotBlank()) {
                            setRequestProperty("Cookie", cookies)
                        }
                    }

                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(soapBody) }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                        if (resp.contains("ResponseClass=\"Success\"") || resp.contains("<m:Items>") || resp.contains("<t:Message>") || resp.contains("<m:Folders>")) {
                            Log.i(TAG, "EWS SOAP $soapAction success on $candidate with user '$authUser'")
                            return resp
                        }
                    } else {
                        Log.w(TAG, "EWS SOAP $candidate returned HTTP $code for user '$authUser'")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "EWS SOAP $candidate failed for user '$authUser': ${e.message}")
                }
            }
        }
        return null
    }

    suspend fun fetchEmailsViaEws(account: MailAccount, folderId: String): List<EmailMessage> = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        if (serverUrl.isBlank()) return@withContext emptyList()

        val target = when {
            folderId.endsWith("inbox", ignoreCase = true) || folderId.equals("inbox", ignoreCase = true) -> "inbox"
            folderId.endsWith("sent", ignoreCase = true) || folderId.equals("sentitems", ignoreCase = true) -> "sentitems"
            folderId.endsWith("drafts", ignoreCase = true) || folderId.equals("drafts", ignoreCase = true) -> "drafts"
            folderId.endsWith("trash", ignoreCase = true) || folderId.endsWith("deleted", ignoreCase = true) || folderId.equals("deleteditems", ignoreCase = true) -> "deleteditems"
            folderId.endsWith("archive", ignoreCase = true) || folderId.equals("archive", ignoreCase = true) -> "archive"
            folderId.endsWith("junk", ignoreCase = true) || folderId.equals("junkemail", ignoreCase = true) -> "junkemail"
            else -> folderId
        }

        val isDist = target in listOf("inbox", "sentitems", "drafts", "deleteditems", "archive", "junkemail")
        val folderXml = if (isDist) """<t:DistinguishedFolderId Id="$target" />""" else """<t:FolderId Id="$target" />"""

        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages"
                           xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types"
                           xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Header>
                <t:RequestServerVersion Version="Exchange2013" />
              </soap:Header>
              <soap:Body>
                <m:FindItem Traversal="Shallow">
                  <m:ItemShape>
                    <t:BaseShape>AllProperties</t:BaseShape>
                  </m:ItemShape>
                  <m:IndexedPageItemView MaxEntriesReturned="50" Offset="0" BasePoint="Beginning" />
                  <m:SortOrder>
                    <t:FieldOrder Order="Descending">
                      <t:FieldURI FieldURI="item:DateTimeReceived" />
                    </t:FieldOrder>
                  </m:SortOrder>
                  <m:ParentFolderIds>
                    $folderXml
                  </m:ParentFolderIds>
                </m:FindItem>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()

        val xmlResp = executeEwsSoapPost(serverUrl, "FindItem", soapBody, account) ?: return@withContext emptyList()
        parseEwsMessagesXml(account, folderId, xmlResp)
    }

    fun parseEwsMessagesXml(account: MailAccount, folderId: String, xmlText: String): List<EmailMessage> {
        val result = mutableListOf<EmailMessage>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xmlText)))

            val messageNodes = doc.getElementsByTagNameNS("*", "Message")
            val count = messageNodes.length
            for (i in 0 until count) {
                val elem = messageNodes.item(i) as? Element ?: continue
                val itemId = (elem.getElementsByTagNameNS("*", "ItemId").item(0) as? Element)?.getAttribute("Id").orEmpty()
                if (itemId.isBlank()) continue

                fun getChildText(tag: String): String {
                    val list = elem.getElementsByTagNameNS("*", tag)
                    return if (list.length > 0) list.item(0).textContent?.trim().orEmpty() else ""
                }

                val subject = getChildText("Subject")
                val dateTimeReceived = getChildText("DateTimeReceived")
                val isRead = getChildText("IsRead").equals("true", ignoreCase = true)
                val hasAttachments = getChildText("HasAttachments").equals("true", ignoreCase = true)
                val importance = getChildText("Importance")
                val bodyText = getChildText("Body")

                var senderName = ""
                var senderEmail = ""
                val fromNodes = elem.getElementsByTagNameNS("*", "From")
                if (fromNodes.length > 0) {
                    val fromElem = fromNodes.item(0) as? Element
                    if (fromElem != null) {
                        val nameList = fromElem.getElementsByTagNameNS("*", "Name")
                        if (nameList.length > 0) senderName = nameList.item(0).textContent?.trim().orEmpty()
                        val emailList = fromElem.getElementsByTagNameNS("*", "EmailAddress")
                        if (emailList.length > 0) senderEmail = emailList.item(0).textContent?.trim().orEmpty()
                    }
                }

                val timestamp = parseIsoTimestamp(dateTimeReceived)
                val cleanSub = subject.cleanEmailSubject()
                val cleanSnip = (if (bodyText.isNotBlank()) stripHtml(bodyText).take(150) else cleanSub).cleanEmailPreview()
                val slaSeverity = if (!isRead) {
                    val ageMinutes = (System.currentTimeMillis() - timestamp) / 60000L
                    when {
                        ageMinutes > 30 -> SlaSeverity.BREACHED
                        ageMinutes > 20 -> SlaSeverity.URGENT
                        ageMinutes > 10 -> SlaSeverity.WARNING
                        else -> SlaSeverity.NORMAL
                    }
                } else SlaSeverity.COMPLETED

                val targetFolderId = if (folderId.isNotBlank()) folderId else "${account.id}_inbox"
                result.add(
                    EmailMessage(
                        id = itemId,
                        accountId = account.id,
                        folderId = targetFolderId,
                        threadId = "th_${itemId.takeLast(12)}",
                        senderName = senderName.ifBlank { senderEmail.substringBefore("@", "Коллега") },
                        senderEmail = senderEmail.ifBlank { "unknown@corp.mail" },
                        toRecipients = listOf(account.email),
                        subject = cleanSub,
                        snippet = cleanSnip,
                        bodyText = bodyText,
                        bodyHtml = if (bodyText.contains("<")) bodyText else "<p>${bodyText.replace("\n", "<br/>")}</p>",
                        timestamp = timestamp,
                        isRead = isRead,
                        isStarred = importance.equals("High", ignoreCase = true),
                        hasAttachments = hasAttachments,
                        attachments = emptyList(),
                        slaInfo = SlaInfo(
                            severity = slaSeverity,
                            deadlineTimestamp = timestamp + (30 * 60 * 1000L),
                            remainingLabel = if (slaSeverity == SlaSeverity.COMPLETED) "Ответ дан вовремя" else "${((timestamp + (30 * 60 * 1000L) - System.currentTimeMillis()) / 60000L).coerceAtLeast(0)} мин"
                        ),
                        deliveryStatus = DeliveryStatus.SENT
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "EWS XML parse error: ${e.message}")
        }
        return result
    }

    suspend fun fetchFoldersViaEws(account: MailAccount): List<Folder> = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        if (serverUrl.isBlank()) return@withContext emptyList()
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages"
                           xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types"
                           xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Header>
                <t:RequestServerVersion Version="Exchange2013" />
              </soap:Header>
              <soap:Body>
                <m:FindFolder Traversal="Deep">
                  <m:FolderShape>
                    <t:BaseShape>Default</t:BaseShape>
                  </m:FolderShape>
                  <m:ParentFolderIds>
                    <t:DistinguishedFolderId Id="msgfolderroot" />
                  </m:ParentFolderIds>
                </m:FindFolder>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()

        val respXml = executeEwsSoapPost(serverUrl, "FindFolder", soapBody, account) ?: return@withContext emptyList()
        parseEwsFoldersXml(account, respXml)
    }

    fun parseEwsFoldersXml(account: MailAccount, xmlText: String): List<Folder> {
        val result = mutableListOf<Folder>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xmlText)))

            val folderNodes = doc.getElementsByTagNameNS("*", "Folder")
            val count = folderNodes.length
            for (i in 0 until count) {
                val elem = folderNodes.item(i) as? Element ?: continue
                val folderId = (elem.getElementsByTagNameNS("*", "FolderId").item(0) as? Element)?.getAttribute("Id").orEmpty()
                val displayName = elem.getElementsByTagNameNS("*", "DisplayName").item(0)?.textContent?.trim().orEmpty()
                val unreadCount = elem.getElementsByTagNameNS("*", "UnreadCount").item(0)?.textContent?.trim()?.toIntOrNull() ?: 0
                val totalCount = elem.getElementsByTagNameNS("*", "TotalCount").item(0)?.textContent?.trim()?.toIntOrNull() ?: 0

                if (folderId.isNotBlank() && displayName.isNotBlank()) {
                    val lower = displayName.lowercase()
                    val folderType = when {
                        lower.contains("входящ") || lower == "inbox" -> FolderType.INBOX
                        lower.contains("отправлен") || lower == "sent items" || lower == "sent" -> FolderType.SENT
                        lower.contains("черновик") || lower == "drafts" -> FolderType.DRAFTS
                        lower.contains("удал") || lower.contains("корзин") || lower == "deleted items" || lower == "trash" -> FolderType.TRASH
                        lower.contains("архив") || lower == "archive" -> FolderType.ARCHIVE
                        else -> FolderType.CUSTOM
                    }
                    val canonicalId = when (folderType) {
                        FolderType.INBOX -> "${account.id}_inbox"
                        FolderType.SENT -> "${account.id}_sent"
                        FolderType.DRAFTS -> "${account.id}_drafts"
                        FolderType.TRASH -> "${account.id}_trash"
                        FolderType.ARCHIVE -> "${account.id}_archive"
                        else -> folderId
                    }
                    result.add(
                        Folder(
                            id = canonicalId,
                            accountId = account.id,
                            name = displayName,
                            type = folderType,
                            unreadCount = unreadCount,
                            totalCount = totalCount
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "EWS Folders XML parse error: ${e.message}")
        }
        return result
    }

    suspend fun fetchEmailBodyViaEws(account: MailAccount, itemId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val serverUrl = account.serverHost.trim()
        if (serverUrl.isBlank()) return@withContext null
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages"
                           xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types"
                           xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Header>
                <t:RequestServerVersion Version="Exchange2013" />
              </soap:Header>
              <soap:Body>
                <m:GetItem>
                  <m:ItemShape>
                    <t:BaseShape>Default</t:BaseShape>
                    <t:IncludeMimeContent>false</t:IncludeMimeContent>
                    <t:BodyType>HTML</t:BodyType>
                  </m:ItemShape>
                  <m:ItemIds>
                    <t:ItemId Id="$itemId" />
                  </m:ItemIds>
                </m:GetItem>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()

        val respXml = executeEwsSoapPost(serverUrl, "GetItem", soapBody, account) ?: return@withContext null
        parseEwsBodyXml(respXml)
    }

    private fun parseEwsBodyXml(xmlText: String): Pair<String, String>? {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xmlText)))
            val bodyNodes = doc.getElementsByTagNameNS("*", "Body")
            if (bodyNodes.length > 0) {
                val bodyHtml = bodyNodes.item(0)?.textContent.orEmpty()
                if (bodyHtml.isNotBlank()) {
                    return Pair(stripHtml(bodyHtml), bodyHtml)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "EWS Body XML parse error: ${e.message}")
        }
        return null
    }
}
