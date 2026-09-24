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
import kotlinx.coroutines.flow.flowOn
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
    suspend fun fetchEmailFullDetails(account: MailAccount, emailId: String): Boolean
    suspend fun downloadAttachment(account: MailAccount, attachment: app.jackdaw.client.core.model.Attachment): java.io.File?
    fun getTotalUnreadCount(): Flow<Int>
    fun getUnmutedUnreadCount(): Flow<Int>
    fun getUnmutedUnreadEmails(limit: Int = 10): Flow<List<EmailMessage>>
    fun getPagedEmailsInFolder(accountId: String, folderId: String, limit: Int = 50): Flow<List<EmailMessage>>
    fun searchEmailsPaged(query: String, limit: Int = 50): Flow<List<EmailMessage>>
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

    private val repositoryScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val pendingReadStatusUpdates = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

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
        return emailDao.getUnmutedUnreadCountFlow().flowOn(Dispatchers.IO)
    }

    override fun getUnmutedUnreadCount(): Flow<Int> {
        return emailDao.getUnmutedUnreadCountFlow().flowOn(Dispatchers.IO)
    }

    override fun getUnmutedUnreadEmails(limit: Int): Flow<List<EmailMessage>> {
        return emailDao.getUnmutedUnreadEmailsFlow(limit).map { entities ->
            entities.map { it.toDomain(emptyList()) }
        }.flowOn(Dispatchers.IO)
    }

    override fun getPagedEmailsInFolder(accountId: String, folderId: String, limit: Int): Flow<List<EmailMessage>> {
        val emailFlow = when {
            folderId == "sla_alerts" -> emailDao.getPagedSlaEmails(accountId, limit)
            folderId.contains("outbox") -> emailDao.getPagedOutboxEmails(accountId, folderId, limit)
            else -> emailDao.getPagedEmailsInFolder(accountId, folderId, limit)
        }
        return emailFlow.map { entities ->
            entities.map { it.toDomain(emptyList()) }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchEmailsPaged(query: String, limit: Int): Flow<List<EmailMessage>> {
        val sanitizedQuery = query.trim()
        if (sanitizedQuery.isBlank()) {
            return flowOf(emptyList())
        }
        val ftsQuery = "*$sanitizedQuery*"
        return emailDao.searchEmailsPaged(ftsQuery, limit).map { entities ->
            entities.map { it.toDomain(emptyList()) }
        }.flowOn(Dispatchers.IO)
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
        }.flowOn(Dispatchers.IO)
    }

    override fun getEmailsInFolder(accountId: String, folderId: String): Flow<List<EmailMessage>> {
        val emailFlow = when {
            folderId == "sla_alerts" -> emailDao.getSlaEmails(accountId)
            folderId.contains("outbox") -> emailDao.getOutboxEmails(accountId, folderId)
            else -> emailDao.getEmailsInFolder(accountId, folderId)
        }
        return emailFlow.map { emailEntities ->
            emailEntities.map { it.toDomain(emptyList()) }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSlaEmails(accountId: String): Flow<List<EmailMessage>> {
        return emailDao.getSlaEmails(accountId).map { emailEntities ->
            emailEntities.map { it.toDomain(emptyList()) }
        }.flowOn(Dispatchers.IO)
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
        }.flowOn(Dispatchers.IO)
    }

    override fun getEmailsInThread(threadId: String): Flow<List<EmailMessage>> {
        return emailDao.getEmailsInThread(threadId).map { emailEntities ->
            emailEntities.map { it.toDomain(emptyList()) }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchEmails(query: String): Flow<List<EmailMessage>> {
        val sanitizedQuery = query.trim()
        if (sanitizedQuery.isBlank()) {
            return flowOf(emptyList())
        }
        val ftsQuery = "*$sanitizedQuery*"
        return emailDao.searchEmails(ftsQuery).map { emailEntities ->
            emailEntities.map { it.toDomain(emptyList()) }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun markAsRead(emailId: String, isRead: Boolean) {
        emailDao.updateReadStatus(emailId, isRead)
        pendingReadStatusUpdates[emailId] = isRead

        repositoryScope.launch {
            try {
                val email = emailDao.getEmailById(emailId).first() ?: return@launch
                val account = accountDao.getAccountById(email.accountId)?.toDomain()
                    ?: accountDao.getDefaultAccount()?.toDomain()
                    ?: return@launch

                if (account.serverHost.isNotBlank() && !emailId.startsWith("mock_")) {
                    val success = mailProtocolEngine.updateEmailReadStatus(account, emailId, isRead)
                    if (success) {
                        pendingReadStatusUpdates.remove(emailId)
                        android.util.Log.i("MailRepository", "Successfully synced read status ($isRead) to Exchange for $emailId")
                    } else {
                        android.util.Log.w("MailRepository", "Exchange read status update failed for $emailId, retained in pending queue")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MailRepository", "Failed to sync read status for $emailId", e)
            }
        }
    }

    override suspend fun toggleStar(emailId: String, isStarred: Boolean) {
        emailDao.updateStarredStatus(emailId, isStarred)

        repositoryScope.launch {
            try {
                val email = emailDao.getEmailById(emailId).first() ?: return@launch
                val account = accountDao.getAccountById(email.accountId)?.toDomain()
                    ?: accountDao.getDefaultAccount()?.toDomain()
                    ?: return@launch

                if (account.serverHost.isNotBlank() && !emailId.startsWith("mock_")) {
                    mailProtocolEngine.updateEmailStarStatus(account, emailId, isStarred)
                }
            } catch (e: Exception) {
                android.util.Log.e("MailRepository", "Error syncing toggleStar for $emailId", e)
            }
        }
    }

    override suspend fun moveToArchive(emailId: String) {
        val email = emailDao.getEmailById(emailId).first()
        val targetFolder = when {
            email?.accountId == "acc_secondary" -> "sec_archive"
            email?.accountId != null && email.accountId != "acc_primary" -> "${email.accountId}_archive"
            else -> "archive"
        }
        emailDao.updateFolder(emailId, targetFolder)

        if (email != null) {
            repositoryScope.launch {
                try {
                    val account = accountDao.getAccountById(email.accountId)?.toDomain()
                        ?: accountDao.getDefaultAccount()?.toDomain()
                        ?: return@launch

                    if (account.serverHost.isNotBlank() && !emailId.startsWith("mock_")) {
                        mailProtocolEngine.moveEmail(account, emailId, FolderType.ARCHIVE)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MailRepository", "Error syncing moveToArchive for $emailId", e)
                }
            }
        }
    }

    override suspend fun unarchiveEmail(emailId: String) {
        val email = emailDao.getEmailById(emailId).first()
        val targetFolder = when {
            email?.accountId == "acc_secondary" -> "sec_inbox"
            email?.accountId != null && email.accountId != "acc_primary" -> "${email.accountId}_inbox"
            else -> "inbox"
        }
        emailDao.updateFolder(emailId, targetFolder)

        if (email != null) {
            repositoryScope.launch {
                try {
                    val account = accountDao.getAccountById(email.accountId)?.toDomain()
                        ?: accountDao.getDefaultAccount()?.toDomain()
                        ?: return@launch

                    if (account.serverHost.isNotBlank() && !emailId.startsWith("mock_")) {
                        mailProtocolEngine.moveEmail(account, emailId, FolderType.INBOX)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MailRepository", "Error syncing unarchiveEmail for $emailId", e)
                }
            }
        }
    }

    override suspend fun moveToTrash(emailId: String) {
        val email = emailDao.getEmailById(emailId).first()
        val targetFolder = when {
            email?.accountId == "acc_secondary" -> "sec_trash"
            email?.accountId != null && email.accountId != "acc_primary" -> "${email.accountId}_trash"
            else -> "trash"
        }
        emailDao.updateFolder(emailId, targetFolder)

        if (email != null) {
            repositoryScope.launch {
                try {
                    val account = accountDao.getAccountById(email.accountId)?.toDomain()
                        ?: accountDao.getDefaultAccount()?.toDomain()
                        ?: return@launch

                    if (account.serverHost.isNotBlank() && !emailId.startsWith("mock_")) {
                        mailProtocolEngine.deleteEmail(account, emailId, hardDelete = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MailRepository", "Error syncing moveToTrash for $emailId", e)
                }
            }
        }
    }

    override suspend fun permanentlyDeleteEmail(emailId: String) {
        val email = emailDao.getEmailById(emailId).first()
        attachmentDao.deleteAttachmentsForEmail(emailId)
        emailDao.deleteEmail(emailId)

        if (email != null) {
            repositoryScope.launch {
                try {
                    val account = accountDao.getAccountById(email.accountId)?.toDomain()
                        ?: accountDao.getDefaultAccount()?.toDomain()
                        ?: return@launch

                    if (account.serverHost.isNotBlank() && !emailId.startsWith("mock_")) {
                        mailProtocolEngine.deleteEmail(account, emailId, hardDelete = true)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MailRepository", "Error syncing permanentlyDeleteEmail for $emailId", e)
                }
            }
        }
    }

    override suspend fun emptyTrash(accountId: String, folderId: String) {
        attachmentDao.deleteAttachmentsInFolder(accountId, folderId)
        emailDao.deleteEmailsInFolder(accountId, folderId)
        folderDao.updateCounts(folderId, 0, 0)

        repositoryScope.launch {
            try {
                val account = accountDao.getAccountById(accountId)?.toDomain() ?: return@launch
                if (account.serverHost.isNotBlank()) {
                    val success = mailProtocolEngine.emptyTrash(account)
                    android.util.Log.i("MailRepository", "emptyTrash on server result: $success")
                }
            } catch (e: Exception) {
                android.util.Log.e("MailRepository", "Error syncing emptyTrash for $accountId", e)
            }
        }
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
        val email = emailDao.getEmailById(emailId).first()
        emailDao.updateFolder(emailId, originalFolderId)

        if (email != null) {
            repositoryScope.launch {
                try {
                    val account = accountDao.getAccountById(email.accountId)?.toDomain()
                        ?: accountDao.getDefaultAccount()?.toDomain()
                        ?: return@launch

                    if (account.serverHost.isNotBlank() && !emailId.startsWith("mock_")) {
                        mailProtocolEngine.moveEmail(account, emailId, FolderType.INBOX)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MailRepository", "Error syncing restoreEmail for $emailId", e)
                }
            }
        }
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

        // Immediately attempt transmission in background
        repositoryScope.launch {
            try {
                val account = accountDao.getAccountById(queuedEmail.accountId)?.toDomain()
                    ?: accountDao.getDefaultAccount()?.toDomain()
                    ?: return@launch

                if (account.serverHost.isNotBlank()) {
                    processPendingOutgoingEmails(account)
                }
            } catch (e: Exception) {
                android.util.Log.e("MailRepository", "Error during immediate outgoing email transmission", e)
            }
        }
    }

    private suspend fun processPendingOutgoingEmails(account: MailAccount): Int {
        val outboxFolder = folderDao.getFolderByType(account.id, FolderType.OUTBOX)?.id ?: "${account.id}_outbox"
        val sentFolder = folderDao.getFolderByType(account.id, FolderType.SENT)?.id ?: "${account.id}_sent"

        val pendingEmails = emailDao.getPendingOutgoingEmails()
        var sentCount = 0
        for (pending in pendingEmails) {
            emailDao.updateDeliveryStatus(pending.id, DeliveryStatus.SENDING, outboxFolder)
            val attachments = attachmentDao.getAttachmentsForEmail(pending.id).first().map { it.toDomain() }
            val sendResult = mailProtocolEngine.sendMessage(account, pending.toDomain(attachments))
            if (sendResult.isSuccess) {
                emailDao.updateDeliveryStatus(pending.id, DeliveryStatus.SENT, sentFolder)
                sentCount++
                android.util.Log.i("MailRepository", "Successfully sent outgoing email ${pending.id} (${pending.subject})")
            } else {
                emailDao.updateDeliveryStatus(pending.id, DeliveryStatus.FAILED, outboxFolder)
                android.util.Log.w("MailRepository", "Failed to send outgoing email ${pending.id}: ${sendResult.errorMessage}")
            }
        }
        return sentCount
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

            if (app.jackdaw.client.data.network.OwaProtocolEngine.mailboxSessionCooldownUntil > System.currentTimeMillis()) {
                val remainingSec = (app.jackdaw.client.data.network.OwaProtocolEngine.mailboxSessionCooldownUntil - System.currentTimeMillis()) / 1000L
                val msg = "Exchange: превышен лимит сессий почтового ящика. Ожидание сброса сервером ($remainingSec сек)"
                return SyncResult(isSuccess = false, errorMessage = msg)
            }

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

            // 0. Flush pending read status updates to Exchange
            if (pendingReadStatusUpdates.isNotEmpty()) {
                val pendingCopies = HashMap(pendingReadStatusUpdates)
                for ((pendingId, pendingIsRead) in pendingCopies) {
                    try {
                        val success = mailProtocolEngine.updateEmailReadStatus(account, pendingId, pendingIsRead)
                        if (success) {
                            pendingReadStatusUpdates.remove(pendingId)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MailRepository", "Error syncing pending read status for $pendingId", e)
                    }
                }
            }

            // 1. Process pending outgoing emails (Outbox)
            val sentCount = processPendingOutgoingEmails(account)

            // 2. Discover and synchronize server folders (Inbox, Sent, Trash, Drafts, Archive, and all custom folders)
            var remoteFolderCount = 0
            try {
                // First purge any cached Exchange system / search folders from Room
                folderDao.cleanupNonMailFolders(account.id)

                val remoteFolders = mailProtocolEngine.fetchFolders(account)
                remoteFolderCount = remoteFolders.size
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
                    emailDao.deleteOrphanedEmails()
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

            // 3. Fetch emails for all syncable folders (Inbox first!)
            val foldersToSync = folderDao.getFoldersByAccount(account.id).first()
                .filter { it.type != FolderType.OUTBOX && it.type != FolderType.SLA_ALERTS }
                .sortedBy { if (it.type == FolderType.INBOX) 0 else 1 }

            var totalNewEmails = 0
            var unmutedNewEmails = 0
            if (foldersToSync.isNotEmpty()) {
                for (folder in foldersToSync) {
                    if (app.jackdaw.client.data.network.OwaProtocolEngine.mailboxSessionCooldownUntil > System.currentTimeMillis()) {
                        android.util.Log.w("MailRepository", "syncAll: aborting folder sync loop due to active session limit cooldown")
                        break
                    }
                    try {
                        val newEmails = mailProtocolEngine.fetchNewEmails(account, folder.id, 0L)
                        val localEmailIds = emailDao.getEmailIdsInFolder(account.id, folder.id).toSet()

                        if (newEmails.isNotEmpty()) {
                            // Reconcile deletions: remove any local emails that no longer exist on the server
                            val serverEmailIds = newEmails.map { it.id }.toSet()
                            val toDeleteLocally = localEmailIds - serverEmailIds
                            if (toDeleteLocally.isNotEmpty()) {
                                val deleteList = toDeleteLocally.toList()
                                attachmentDao.deleteAttachmentsForEmails(deleteList)
                                emailDao.deleteEmailsByIds(deleteList)
                                android.util.Log.i("MailRepository", "syncAll: pruned ${toDeleteLocally.size} deleted emails from folder ${folder.name}")
                            }

                            val emailEntities = newEmails.map { email ->
                                val effectiveIsRead = pendingReadStatusUpdates[email.id] ?: email.isRead
                                val existing = emailDao.getEmailEntityById(email.id)
                                val hasFullBody = !existing?.bodyHtml.isNullOrBlank() && (existing?.bodyHtml?.length ?: 0) > 300
                                val effectiveHtml = if (hasFullBody) existing?.bodyHtml else email.bodyHtml
                                val effectiveText = if (hasFullBody) (existing?.bodyText ?: email.bodyText) else email.bodyText
                                EmailEntity.fromDomain(email.copy(
                                    folderId = folder.id,
                                    isRead = effectiveIsRead,
                                    bodyHtml = effectiveHtml,
                                    bodyText = effectiveText
                                ))
                            }
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
                        } else if (folder.totalCount == 0 && localEmailIds.isNotEmpty()) {
                            // Server explicitly reports 0 items in folder (e.g. emptied trash or cleared folder)
                            attachmentDao.deleteAttachmentsInFolder(account.id, folder.id)
                            emailDao.deleteEmailsInFolder(account.id, folder.id)
                            android.util.Log.i("MailRepository", "syncAll: cleared ${localEmailIds.size} local emails for empty remote folder ${folder.name}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MailRepository", "Error syncing folder ${folder.name} (${folder.id})", e)
                    }
                }
            } else {
                // Fallback to inbox folder if folder list is not yet populated
                val newEmails = mailProtocolEngine.fetchNewEmails(account, inboxFolder, 0L)
                val localEmailIds = emailDao.getEmailIdsInFolder(account.id, inboxFolder).toSet()
                if (newEmails.isNotEmpty()) {
                    val serverEmailIds = newEmails.map { it.id }.toSet()
                    val toDeleteLocally = localEmailIds - serverEmailIds
                    if (toDeleteLocally.isNotEmpty()) {
                        val deleteList = toDeleteLocally.toList()
                        attachmentDao.deleteAttachmentsForEmails(deleteList)
                        emailDao.deleteEmailsByIds(deleteList)
                    }
                    android.util.Log.i("MailRepository", "syncAll: inbox fallback synced ${newEmails.size} emails")
                    val emailEntities = newEmails.map { email ->
                        val existing = emailDao.getEmailEntityById(email.id)
                        val hasFullBody = !existing?.bodyHtml.isNullOrBlank() && (existing?.bodyHtml?.length ?: 0) > 300
                        val effectiveHtml = if (hasFullBody) existing?.bodyHtml else email.bodyHtml
                        val effectiveText = if (hasFullBody) (existing?.bodyText ?: email.bodyText) else email.bodyText
                        EmailEntity.fromDomain(email.copy(
                            folderId = inboxFolder,
                            bodyHtml = effectiveHtml,
                            bodyText = effectiveText
                        ))
                    }
                    emailDao.insertEmails(emailEntities)
                    totalNewEmails += newEmails.size
                    unmutedNewEmails += newEmails.size
                }
            }

            // 4. Fetch calendar meetings & events from remote server (if not in cooldown)
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - 30L * 86400000L
            val ninetyDaysAhead = now + 90L * 86400000L
            if (app.jackdaw.client.data.network.OwaProtocolEngine.mailboxSessionCooldownUntil <= System.currentTimeMillis()) {
                try {
                    val calendarEvents = mailProtocolEngine.fetchCalendarEvents(account, thirtyDaysAgo, ninetyDaysAhead)
                    android.util.Log.i("MailRepository", "syncAll: fetched ${calendarEvents.size} calendar events from Exchange")
                    if (calendarEvents.isNotEmpty()) {
                        calendarEventDao.insertEvents(calendarEvents.map { CalendarEventEntity.fromDomain(it) })
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MailRepository", "Error syncing calendar events", e)
                }
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
            val reportSuccess = if (remoteFolderCount == 0 && totalNewEmails == 0 && diagError != null && lastHttpCode != 200) {
                false
            } else {
                true
            }

            if (reportSuccess) {
                app.jackdaw.client.data.network.OwaSyncDiagnostics.recordSuccess(
                    "syncAll",
                    account.serverHost,
                    200,
                    "Папок: $remoteFolderCount, получено писем: $totalNewEmails"
                )
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

    override suspend fun fetchEmailFullDetails(account: MailAccount, emailId: String): Boolean {
        val details = mailProtocolEngine.fetchEmailFullDetails(account, emailId) ?: return false
        emailDao.updateEmailBody(
            emailId,
            details.bodyText,
            details.bodyHtml,
            details.bodyText.take(150)
        )
        if (details.isStarred != null) {
            emailDao.updateStarredStatus(emailId, details.isStarred)
        }
        if (details.attachments.isNotEmpty()) {
            attachmentDao.deleteAttachmentsForEmail(emailId)
            attachmentDao.insertAttachments(details.attachments.map { 
                app.jackdaw.client.data.local.entity.AttachmentEntity.fromDomain(it, emailId) 
            })
        }
        return true
    }

    override suspend fun downloadAttachment(
        account: MailAccount,
        attachment: app.jackdaw.client.core.model.Attachment
    ): java.io.File? {
        try {
            val context = app.jackdaw.client.JackdawApp.instance
            val attachmentsDir = java.io.File(context.cacheDir, "attachments").apply { mkdirs() }
            val safeFileName = attachment.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = java.io.File(attachmentsDir, "${attachment.id.take(8)}_$safeFileName")

            if (targetFile.exists()) {
                if (targetFile.length() > 0L) {
                    return targetFile
                } else {
                    targetFile.delete()
                }
            }

            val bytes = mailProtocolEngine.downloadAttachment(account, attachment.id)
            if (bytes != null && bytes.isNotEmpty()) {
                targetFile.writeBytes(bytes)
                if (targetFile.exists() && targetFile.length() > 0L) {
                    attachmentDao.updateAttachmentLocalUri(attachment.id, targetFile.absolutePath)
                    return targetFile
                }
            } else if (targetFile.exists()) {
                targetFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("MailRepository", "Error downloading attachment ${attachment.fileName}", e)
        }
        return null
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

