package app.jackdaw.client.data.repository

import app.jackdaw.client.core.model.DeliveryStatus
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.FolderType
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.data.local.JackdawDatabase
import app.jackdaw.client.data.local.entity.AccountEntity
import app.jackdaw.client.data.local.entity.AttachmentEntity
import app.jackdaw.client.data.local.entity.CalendarEventEntity
import app.jackdaw.client.data.local.entity.EmailEntity
import app.jackdaw.client.data.local.entity.FolderEntity
import app.jackdaw.client.data.network.MailProtocolEngine
import app.jackdaw.client.data.network.MockNetworkSyncEngine
import app.jackdaw.client.data.network.model.SyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface MailRepository {
    fun getAccounts(): Flow<List<MailAccount>>
    fun getFolders(accountId: String): Flow<List<Folder>>
    fun getEmailsInFolder(accountId: String, folderId: String): Flow<List<EmailMessage>>
    fun getSlaEmails(accountId: String): Flow<List<EmailMessage>>
    fun getEmailById(id: String): Flow<EmailMessage?>
    fun getEmailsInThread(threadId: String): Flow<List<EmailMessage>>
    fun searchEmails(query: String): Flow<List<EmailMessage>>
    suspend fun markAsRead(emailId: String, isRead: Boolean)
    suspend fun toggleStar(emailId: String, isStarred: Boolean)
    suspend fun moveToArchive(emailId: String)
    suspend fun unarchiveEmail(emailId: String)
    suspend fun moveToTrash(emailId: String)
    suspend fun permanentlyDeleteEmail(emailId: String)
    suspend fun emptyTrash(accountId: String, folderId: String)
    suspend fun markSlaCompleted(emailId: String)
    suspend fun restoreEmail(emailId: String, originalFolderId: String)
    suspend fun queueEmailForSending(email: EmailMessage)
    suspend fun sendEmail(email: EmailMessage)
    suspend fun addAccount(account: MailAccount)
    suspend fun updateAccount(account: MailAccount)
    suspend fun deleteAccount(accountId: String)
    suspend fun syncAll(accountId: String): SyncResult
    suspend fun initializeSampleDataIfEmpty()
    suspend fun updateEmailBody(id: String, bodyText: String, bodyHtml: String?, snippet: String)
    suspend fun fetchEmailBodyDirect(account: MailAccount, itemId: String): Pair<String, String>?
    fun getTotalUnreadCount(): Flow<Int>
    suspend fun reorderFolders(orderedIds: List<String>)
    suspend fun toggleFolderMute(folderId: String)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OfflineFirstMailRepository(
    private val database: JackdawDatabase,
    private val mailProtocolEngine: MailProtocolEngine = app.jackdaw.client.data.network.OwaProtocolEngine()
) : MailRepository {

    private val accountDao = database.accountDao()
    private val folderDao = database.folderDao()
    private val emailDao = database.emailDao()
    private val attachmentDao = database.attachmentDao()
    private val calendarEventDao = database.calendarEventDao()

    init {
        (mailProtocolEngine as? app.jackdaw.client.data.network.OwaProtocolEngine)?.onSessionUpdated = { accId, cookies, canary ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                accountDao.updateSession(accId, cookies, canary)
            }
        }
    }


    override fun getAccounts(): Flow<List<MailAccount>> {
        return accountDao.getAllAccounts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTotalUnreadCount(): Flow<Int> {
        return emailDao.getTotalUnreadCountFlow()
    }

    override fun getFolders(accountId: String): Flow<List<Folder>> {
        return combine(
            folderDao.getFoldersByAccount(accountId),
            emailDao.getAllEmails(accountId)
        ) { folders, emails ->
            folders.map { folderEntity ->
                val unread = if (folderEntity.type == FolderType.SLA_ALERTS) {
                    emails.count { it.slaSeverity != SlaSeverity.NONE && it.slaSeverity != SlaSeverity.COMPLETED && !it.isRead }
                } else {
                    emails.count { it.folderId == folderEntity.id && !it.isRead }
                }
                val total = when (folderEntity.type) {
                    FolderType.SLA_ALERTS -> emails.count { it.slaSeverity != SlaSeverity.NONE && it.slaSeverity != SlaSeverity.COMPLETED }
                    FolderType.OUTBOX -> emails.count {
                        it.deliveryStatus != app.jackdaw.client.core.model.DeliveryStatus.SENT && (it.folderId == folderEntity.id || it.folderId.contains("outbox"))
                    }
                    else -> emails.count { it.folderId == folderEntity.id }
                }
                folderEntity.toDomain().copy(
                    unreadCount = unread,
                    totalCount = total
                )
            }
        }
    }

    override fun getEmailsInFolder(accountId: String, folderId: String): Flow<List<EmailMessage>> {
        val emailFlow = when {
            folderId == "sla_alerts" -> emailDao.getSlaEmails(accountId)
            folderId.contains("outbox") -> emailDao.getOutboxEmails(accountId, folderId)
            else -> emailDao.getEmailsInFolder(accountId, folderId)
        }

        return emailFlow.flatMapLatest { emailEntities ->
            if (emailEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(emailEntities.map { entity ->
                    attachmentDao.getAttachmentsForEmail(entity.id).map { attachments ->
                        val distinctAtts = attachments.map { it.toDomain() }.distinctBy { "${it.fileName}_${it.sizeBytes}" }
                        entity.toDomain(distinctAtts)
                    }
                }) { emailsArray -> emailsArray.toList() }
            }
        }
    }

    override fun getSlaEmails(accountId: String): Flow<List<EmailMessage>> {
        return emailDao.getSlaEmails(accountId).flatMapLatest { emailEntities ->
            if (emailEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(emailEntities.map { entity ->
                    attachmentDao.getAttachmentsForEmail(entity.id).map { attachments ->
                        val distinctAtts = attachments.map { it.toDomain() }.distinctBy { "${it.fileName}_${it.sizeBytes}" }
                        entity.toDomain(distinctAtts)
                    }
                }) { emailsArray -> emailsArray.toList() }
            }
        }
    }

    override fun getEmailById(id: String): Flow<EmailMessage?> {
        return emailDao.getEmailById(id).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                attachmentDao.getAttachmentsForEmail(entity.id).map { attachments ->
                    val distinctAtts = attachments.map { it.toDomain() }.distinctBy { "${it.fileName}_${it.sizeBytes}" }
                    entity.toDomain(distinctAtts)
                }
            }
        }
    }

    override fun getEmailsInThread(threadId: String): Flow<List<EmailMessage>> {
        return emailDao.getEmailsInThread(threadId).flatMapLatest { emailEntities ->
            if (emailEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(emailEntities.map { entity ->
                    attachmentDao.getAttachmentsForEmail(entity.id).map { attachments ->
                        val distinctAtts = attachments.map { it.toDomain() }.distinctBy { "${it.fileName}_${it.sizeBytes}" }
                        entity.toDomain(distinctAtts)
                    }
                }) { emailsArray -> emailsArray.toList() }
            }
        }
    }


    override fun searchEmails(query: String): Flow<List<EmailMessage>> {
        val sanitizedQuery = query.trim()
        if (sanitizedQuery.isBlank()) {
            return flowOf(emptyList())
        }
        val ftsQuery = "*$sanitizedQuery*"
        return emailDao.searchEmails(ftsQuery).flatMapLatest { emailEntities ->
            if (emailEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(emailEntities.map { entity ->
                    attachmentDao.getAttachmentsForEmail(entity.id).map { attachments ->
                        val distinctAtts = attachments.map { it.toDomain() }.distinctBy { "${it.fileName}_${it.sizeBytes}" }
                        entity.toDomain(distinctAtts)
                    }
                }) { emailsArray -> emailsArray.toList() }
            }
        }
    }

    override suspend fun markAsRead(emailId: String, isRead: Boolean) {
        emailDao.updateReadStatus(emailId, isRead)
    }

    override suspend fun toggleStar(emailId: String, isStarred: Boolean) {
        emailDao.updateStarredStatus(emailId, isStarred)
    }

    override suspend fun moveToArchive(emailId: String) {
        val email = emailDao.getEmailById(emailId).first()
        val targetFolder = when {
            email?.accountId == "acc_secondary" -> "sec_archive"
            email?.accountId != null && email.accountId != "acc_primary" -> "${email.accountId}_archive"
            else -> "archive"
        }
        emailDao.updateFolder(emailId, targetFolder)
    }

    override suspend fun unarchiveEmail(emailId: String) {
        val email = emailDao.getEmailById(emailId).first()
        val targetFolder = when {
            email?.accountId == "acc_secondary" -> "sec_inbox"
            email?.accountId != null && email.accountId != "acc_primary" -> "${email.accountId}_inbox"
            else -> "inbox"
        }
        emailDao.updateFolder(emailId, targetFolder)
    }

    override suspend fun moveToTrash(emailId: String) {
        val email = emailDao.getEmailById(emailId).first()
        val targetFolder = when {
            email?.accountId == "acc_secondary" -> "sec_trash"
            email?.accountId != null && email.accountId != "acc_primary" -> "${email.accountId}_trash"
            else -> "trash"
        }
        emailDao.updateFolder(emailId, targetFolder)
    }

    override suspend fun permanentlyDeleteEmail(emailId: String) {
        attachmentDao.deleteAttachmentsForEmail(emailId)
        emailDao.deleteEmail(emailId)
    }

    override suspend fun emptyTrash(accountId: String, folderId: String) {
        attachmentDao.deleteAttachmentsInFolder(accountId, folderId)
        emailDao.deleteEmailsInFolder(accountId, folderId)
    }

    override suspend fun markSlaCompleted(emailId: String) {
        val email = emailDao.getEmailById(emailId).first()
        val now = System.currentTimeMillis()
        val effectiveDeadline = if (email != null && email.slaDeadlineTimestamp > 0L) {
            email.slaDeadlineTimestamp
        } else if (email != null) {
            email.timestamp + 30 * 60 * 1000L
        } else {
            0L
        }
        val wasBreached = effectiveDeadline > 0L && now > effectiveDeadline
        val label = if (wasBreached) "Ответ дан с опозданием" else "Ответ дан вовремя"
        emailDao.updateSlaInfo(emailId, SlaSeverity.COMPLETED, effectiveDeadline, label)
    }

    override suspend fun restoreEmail(emailId: String, originalFolderId: String) {
        emailDao.updateFolder(emailId, originalFolderId)
    }

    override suspend fun sendEmail(email: EmailMessage) {
        val entity = EmailEntity.fromDomain(email)
        emailDao.insertEmail(entity)
        if (email.attachments.isNotEmpty()) {
            val attachmentEntities = email.attachments.map {
                AttachmentEntity.fromDomain(it, email.id)
            }
            attachmentDao.insertAttachments(attachmentEntities)
        }
    }

    override suspend fun queueEmailForSending(email: EmailMessage) {
        val outboxFolder = folderDao.getFolderByType(email.accountId, FolderType.OUTBOX)?.id ?: "outbox"
        val queuedEmail = email.copy(
            folderId = outboxFolder,
            deliveryStatus = DeliveryStatus.QUEUED
        )
        val entity = EmailEntity.fromDomain(queuedEmail)
        emailDao.insertEmail(entity)
        if (queuedEmail.attachments.isNotEmpty()) {
            val attachmentEntities = queuedEmail.attachments.map {
                AttachmentEntity.fromDomain(it, queuedEmail.id)
            }
            attachmentDao.insertAttachments(attachmentEntities)
        }
    }

    override suspend fun addAccount(account: MailAccount) {
        accountDao.insertAccount(AccountEntity.fromDomain(account))
        val standardFolders = listOf(
            Folder(id = "${account.id}_inbox", accountId = account.id, name = "Входящие", type = FolderType.INBOX, unreadCount = 0, totalCount = 0),
            Folder(id = "${account.id}_sent", accountId = account.id, name = "Отправленные", type = FolderType.SENT, unreadCount = 0, totalCount = 0),
            Folder(id = "${account.id}_outbox", accountId = account.id, name = "Исходящие", type = FolderType.OUTBOX, unreadCount = 0, totalCount = 0),
            Folder(id = "${account.id}_drafts", accountId = account.id, name = "Черновики", type = FolderType.DRAFTS, unreadCount = 0, totalCount = 0),
            Folder(id = "${account.id}_archive", accountId = account.id, name = "Архив", type = FolderType.ARCHIVE, unreadCount = 0, totalCount = 0),
            Folder(id = "${account.id}_trash", accountId = account.id, name = "Корзина", type = FolderType.TRASH, unreadCount = 0, totalCount = 0)
        )
        folderDao.insertFolders(standardFolders.map { FolderEntity.fromDomain(it) })
    }

    override suspend fun updateAccount(account: MailAccount) {
        accountDao.insertAccount(AccountEntity.fromDomain(account))
    }

    override suspend fun deleteAccount(accountId: String) {
        calendarEventDao.deleteEventsByAccount(accountId)
        emailDao.deleteEmailsByAccount(accountId)
        folderDao.deleteFoldersByAccount(accountId)
        accountDao.deleteAccount(accountId)
    }

    override suspend fun syncAll(accountId: String): SyncResult {
        return try {
            val accountEntity = accountDao.getAccountById(accountId)
                ?: accountDao.getDefaultAccount()
                ?: return SyncResult(isSuccess = false, errorMessage = "Account not found")

            val account = accountEntity.toDomain()
            android.util.Log.i("MailRepository", "syncAll started for account: ${account.email} on server ${account.serverHost}")

            if (account.serverHost.isBlank()) {
                val err = "Не указан адрес сервера почты в настройках аккаунта"
                app.jackdaw.client.data.network.OwaSyncDiagnostics.recordError("Validate", "", 0, err)
                return SyncResult(isSuccess = false, errorMessage = err)
            }

            if (account.protocol == app.jackdaw.client.core.model.AccountProtocol.IMAP) {
                val err = "Протокол IMAP в разработке. Для Exchange переключитесь на протокол OWA"
                app.jackdaw.client.data.network.OwaSyncDiagnostics.recordError("Validate", account.serverHost, 0, err)
                return SyncResult(isSuccess = false, errorMessage = err)
            }

            if (!account.isAuthorized && account.savedPassword.isBlank()) {
                val err = "Требуется авторизация: выполните вход через веб-интерфейс OWA в настройках"
                app.jackdaw.client.data.network.OwaSyncDiagnostics.recordError("Auth", account.serverHost, 401, err)
                return SyncResult(isSuccess = false, errorMessage = err)
            }
            val outboxFolder = folderDao.getFolderByType(account.id, FolderType.OUTBOX)?.id ?: "${account.id}_outbox"
            val sentFolder = folderDao.getFolderByType(account.id, FolderType.SENT)?.id ?: "${account.id}_sent"
            val inboxFolder = folderDao.getFolderByType(account.id, FolderType.INBOX)?.id ?: "${account.id}_inbox"

            // 1. Process pending outgoing emails (Outbox)
            val pendingEmails = emailDao.getPendingOutgoingEmails()
            var sentCount = 0
            for (pending in pendingEmails) {
                emailDao.updateDeliveryStatus(pending.id, DeliveryStatus.SENDING, outboxFolder)
                val sendResult = mailProtocolEngine.sendMessage(account, pending.toDomain())
                if (sendResult.isSuccess) {
                    emailDao.updateDeliveryStatus(pending.id, DeliveryStatus.SENT, sentFolder)
                    sentCount++
                } else {
                    emailDao.updateDeliveryStatus(pending.id, DeliveryStatus.FAILED, outboxFolder)
                }
            }

            // 2. Discover and synchronize server folders (Inbox, Sent, Trash, Drafts, Archive, and all custom folders)
            try {
                // First purge any cached Exchange system / search folders from Room
                folderDao.cleanupNonMailFolders(account.id)

                val remoteFolders = mailProtocolEngine.fetchFolders(account)
                android.util.Log.i("MailRepository", "syncAll: discovered ${remoteFolders.size} remote folders: ${remoteFolders.map { "${it.name}(${it.totalCount})" }}")
                if (remoteFolders.isNotEmpty()) {
                    val existingFolders = folderDao.getFoldersByAccount(account.id).first()
                    val existingMap = existingFolders.associateBy { it.id }
                    val entitiesToSave = remoteFolders.map { rf ->
                        val canonicalId = when (rf.type) {
                            FolderType.INBOX -> "${account.id}_inbox"
                            FolderType.SENT -> "${account.id}_sent"
                            FolderType.DRAFTS -> "${account.id}_drafts"
                            FolderType.ARCHIVE -> "${account.id}_archive"
                            FolderType.OUTBOX -> "${account.id}_outbox"
                            FolderType.TRASH -> "${account.id}_trash"
                            else -> rf.id
                        }
                        val existing = existingMap[canonicalId] ?: existingMap[rf.id]
                        val defaultOrder = when (rf.type) {
                            FolderType.INBOX -> 0
                            FolderType.SENT -> 1
                            FolderType.DRAFTS -> 2
                            FolderType.ARCHIVE -> 3
                            FolderType.OUTBOX -> 4
                            FolderType.TRASH -> 5
                            FolderType.SLA_ALERTS -> 6
                            FolderType.CUSTOM -> 100
                        }
                        FolderEntity(
                            id = canonicalId,
                            accountId = account.id,
                            name = if (rf.type == FolderType.INBOX) "Входящие" else rf.name,
                            type = rf.type,
                            unreadCount = if (rf.unreadCount > 0) rf.unreadCount else (existing?.unreadCount ?: 0),
                            totalCount = if (rf.totalCount > 0) rf.totalCount else (existing?.totalCount ?: 0),
                            displayOrder = existing?.displayOrder ?: defaultOrder,
                            isMuted = existing?.isMuted ?: false
                        )
                    }
                    folderDao.insertFolders(entitiesToSave)
                    folderDao.cleanupNonMailFolders(account.id)

                    // Reassign emails from duplicate raw folders and purge them
                    for (f in existingFolders) {
                        val canonicalTarget = when (f.type) {
                            FolderType.INBOX -> if (f.id != "${account.id}_inbox") "${account.id}_inbox" else null
                            FolderType.SENT -> if (f.id != "${account.id}_sent") "${account.id}_sent" else null
                            FolderType.DRAFTS -> if (f.id != "${account.id}_drafts") "${account.id}_drafts" else null
                            FolderType.TRASH -> if (f.id != "${account.id}_trash") "${account.id}_trash" else null
                            FolderType.ARCHIVE -> if (f.id != "${account.id}_archive") "${account.id}_archive" else null
                            else -> null
                        }
                        if (canonicalTarget != null) {
                            emailDao.reassignFolderEmails(account.id, f.id, canonicalTarget)
                            folderDao.deleteFolder(f.id)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MailRepository", "Failed to sync remote folders", e)
            }

            // Ensure standard Outbox folder exists
            val existingAccountFolders = folderDao.getFoldersByAccount(account.id).first()
            if (existingAccountFolders.none { it.type == FolderType.OUTBOX }) {
                folderDao.insertFolders(listOf(
                    FolderEntity(
                        id = outboxFolder,
                        accountId = account.id,
                        name = "Исходящие",
                        type = FolderType.OUTBOX,
                        displayOrder = 4
                    )
                ))
            }

            // 3. Fetch emails for all syncable folders
            val foldersToSync = folderDao.getFoldersByAccount(account.id).first()
                .filter { it.type != FolderType.OUTBOX && it.type != FolderType.SLA_ALERTS }

            var totalNewEmails = 0
            var unmutedNewEmails = 0
            if (foldersToSync.isNotEmpty()) {
                for (folder in foldersToSync) {
                    try {
                        val newEmails = mailProtocolEngine.fetchNewEmails(account, folder.id, 0L)
                        if (newEmails.isNotEmpty()) {
                            android.util.Log.i("MailRepository", "syncAll: folder ${folder.name} synced ${newEmails.size} emails")
                            val emailEntities = newEmails.map { EmailEntity.fromDomain(it.copy(folderId = folder.id)) }
                            emailDao.insertEmails(emailEntities)

                            for (email in newEmails) {
                                if (email.attachments.isNotEmpty()) {
                                    attachmentDao.deleteAttachmentsForEmail(email.id)
                                }
                            }
                            val attachments = newEmails.flatMap { email ->
                                email.attachments.map { AttachmentEntity.fromDomain(it, email.id) }
                            }
                            if (attachments.isNotEmpty()) {
                                attachmentDao.insertAttachments(attachments)
                            }
                            try {
                                attachmentDao.deduplicateAttachments()
                            } catch (_: Exception) {}
                            totalNewEmails += newEmails.size
                            if (!folder.isMuted) {
                                unmutedNewEmails += newEmails.size
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MailRepository", "Error syncing folder ${folder.name} (${folder.id})", e)
                    }
                }
            } else {
                // Fallback to inbox folder if folder list is not yet populated
                val newEmails = mailProtocolEngine.fetchNewEmails(account, inboxFolder, 0L)
                if (newEmails.isNotEmpty()) {
                    android.util.Log.i("MailRepository", "syncAll: inbox fallback synced ${newEmails.size} emails")
                    val emailEntities = newEmails.map { EmailEntity.fromDomain(it.copy(folderId = inboxFolder)) }
                    emailDao.insertEmails(emailEntities)
                    totalNewEmails += newEmails.size
                    unmutedNewEmails += newEmails.size
                }
            }

            // 4. Fetch calendar meetings & events from remote server
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - 30L * 86400000L
            val ninetyDaysAhead = now + 90L * 86400000L
            try {
                val calendarEvents = mailProtocolEngine.fetchCalendarEvents(account, thirtyDaysAgo, ninetyDaysAhead)
                android.util.Log.i("MailRepository", "syncAll: fetched ${calendarEvents.size} calendar events from Exchange")
                if (calendarEvents.isNotEmpty()) {
                    calendarEventDao.insertEvents(calendarEvents.map { CalendarEventEntity.fromDomain(it) })
                }
            } catch (e: Exception) {
                android.util.Log.e("MailRepository", "Error syncing calendar events", e)
            }

            // 5. Update folder unread and total counters
            val updatedFolders = folderDao.getFoldersByAccount(account.id).first()
            for (f in updatedFolders) {
                val unread = emailDao.getFolderUnreadCount(f.id)
                val total = emailDao.getFolderTotalCount(f.id)
                folderDao.updateCounts(
                    f.id,
                    if (unread > 0 || f.unreadCount == 0) unread else f.unreadCount,
                    if (total > 0 || f.totalCount == 0) total else f.totalCount
                )
            }

            // 6. Dynamic SLA recalculation (30 minutes response SLA from receipt timestamp)
            val allEmails = emailDao.getAllEmails(accountId).first()
            for (item in allEmails) {
                if (item.slaDeadlineTimestamp > 0L && item.slaSeverity != SlaSeverity.NONE && item.slaSeverity != SlaSeverity.COMPLETED) {
                    // Strictly enforce 30-minute SLA window from receipt timestamp
                    val effectiveDeadline = minOf(item.slaDeadlineTimestamp, item.timestamp + 30 * 60 * 1000L)
                    val remainingMs = effectiveDeadline - now
                    val (newSeverity, newLabel) = when {
                        remainingMs <= 0 -> SlaSeverity.BREACHED to "Просрочено"
                        remainingMs <= 10 * 60 * 1000L -> {
                            val mins = (remainingMs / (60 * 1000L)).coerceIn(1, 30)
                            SlaSeverity.URGENT to "$mins мин"
                        }
                        remainingMs <= 20 * 60 * 1000L -> {
                            val mins = (remainingMs / (60 * 1000L)).coerceIn(1, 30)
                            SlaSeverity.WARNING to "$mins мин"
                        }
                        else -> {
                            val mins = (remainingMs / (60 * 1000L)).coerceIn(1, 30)
                            SlaSeverity.NORMAL to "$mins мин"
                        }
                    }
                    if (newSeverity != item.slaSeverity || newLabel != item.slaRemainingLabel || effectiveDeadline != item.slaDeadlineTimestamp) {
                        emailDao.updateSlaInfo(item.id, newSeverity, effectiveDeadline, newLabel)
                    }
                }
            }

            val diagError = app.jackdaw.client.data.network.OwaSyncDiagnostics.lastError
            val lastHttpCode = app.jackdaw.client.data.network.OwaSyncDiagnostics.lastHttpCode
            val hasExplicitError = diagError != null && lastHttpCode != 200
            val reportSuccess = if (totalNewEmails == 0 && (hasExplicitError || (diagError != null && totalNewEmails == 0 && lastHttpCode != 0))) {
                false
            } else {
                true
            }

            SyncResult(
                isSuccess = reportSuccess,
                newMessagesCount = totalNewEmails,
                unmutedNewMessagesCount = unmutedNewEmails,
                sentMessagesCount = sentCount,
                syncedAtTimestamp = System.currentTimeMillis(),
                errorMessage = if (!reportSuccess) (diagError ?: "Ошибка подключения к серверу") else null
            )

        } catch (e: Exception) {
            SyncResult(
                isSuccess = false,
                errorMessage = e.message ?: "Unknown sync error"
            )
        }
    }

    override suspend fun initializeSampleDataIfEmpty() {
        // Clean up any legacy demo/mock data if existing on device
        accountDao.deleteAccount("acc_primary")
        accountDao.deleteAccount("acc_secondary")
        emailDao.deleteEmailsByAccount("acc_primary")
        emailDao.deleteEmailsByAccount("acc_secondary")
        folderDao.deleteFoldersByAccount("acc_primary")
        folderDao.deleteFoldersByAccount("acc_secondary")
        emailDao.deleteEmail("msg_sync_k8s_audit")
        emailDao.deleteEmail("msg_sync_nda_final")
        emailDao.deleteEmail("msg_nda_1")
        emailDao.deleteEmail("msg_nda_2")
        emailDao.deleteEmail("msg_k8s_patch")
        emailDao.deleteEmail("msg_spec")
    }

    override suspend fun updateEmailBody(id: String, bodyText: String, bodyHtml: String?, snippet: String) {
        emailDao.updateEmailBody(id, bodyText, bodyHtml, snippet)
    }

    override suspend fun fetchEmailBodyDirect(account: MailAccount, itemId: String): Pair<String, String>? {
        return mailProtocolEngine.fetchEmailBody(account, itemId)
    }

    override suspend fun reorderFolders(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, folderId ->
            folderDao.updateFolderOrder(folderId, index)
        }
    }

    override suspend fun toggleFolderMute(folderId: String) {
        val folder = folderDao.getFolderById(folderId) ?: return
        folderDao.updateFolderMute(folderId, !folder.isMuted)
    }
}

