package app.jackdaw.client.data.repository

import app.jackdaw.client.core.model.DeliveryStatus
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.FolderType
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.model.SampleData
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.data.local.JackdawDatabase
import app.jackdaw.client.data.local.entity.AccountEntity
import app.jackdaw.client.data.local.entity.AttachmentEntity
import app.jackdaw.client.data.local.entity.EmailEntity
import app.jackdaw.client.data.local.entity.FolderEntity
import app.jackdaw.client.data.network.MailProtocolEngine
import app.jackdaw.client.data.network.MockNetworkSyncEngine
import app.jackdaw.client.data.network.model.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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
    suspend fun deleteAccount(accountId: String)
    suspend fun syncAll(accountId: String): SyncResult
    suspend fun initializeSampleDataIfEmpty()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OfflineFirstMailRepository(
    private val database: JackdawDatabase,
    private val mailProtocolEngine: MailProtocolEngine = MockNetworkSyncEngine()
) : MailRepository {

    private val accountDao = database.accountDao()
    private val folderDao = database.folderDao()
    private val emailDao = database.emailDao()
    private val attachmentDao = database.attachmentDao()

    override fun getAccounts(): Flow<List<MailAccount>> {
        return accountDao.getAllAccounts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFolders(accountId: String): Flow<List<Folder>> {
        return combine(
            folderDao.getFoldersByAccount(accountId),
            emailDao.getAllEmails(accountId)
        ) { folders, emails ->
            folders.map { folderEntity ->
                val unread = if (folderEntity.type == FolderType.SLA_ALERTS) {
                    emails.count { it.slaSeverity != SlaSeverity.NONE && !it.isRead }
                } else {
                    emails.count { it.folderId == folderEntity.id && !it.isRead }
                }
                val total = when (folderEntity.type) {
                    FolderType.SLA_ALERTS -> emails.count { it.slaSeverity != SlaSeverity.NONE }
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
                        entity.toDomain(attachments.map { it.toDomain() })
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
                        entity.toDomain(attachments.map { it.toDomain() })
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
                    entity.toDomain(attachments.map { it.toDomain() })
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
                        entity.toDomain(attachments.map { it.toDomain() })
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
                        entity.toDomain(attachments.map { it.toDomain() })
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
        emailDao.updateSlaInfo(emailId, SlaSeverity.NONE, 0L, "Выполнен")
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

    override suspend fun deleteAccount(accountId: String) {
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
            val outboxFolder = folderDao.getFolderByType(account.id, FolderType.OUTBOX)?.id ?: "outbox"
            val sentFolder = folderDao.getFolderByType(account.id, FolderType.SENT)?.id ?: "sent"

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

            // 2. Fetch new emails from remote server
            val newEmails = mailProtocolEngine.fetchNewEmails(account, "inbox", System.currentTimeMillis() - 86400000)
            if (newEmails.isNotEmpty()) {
                val emailEntities = newEmails.map { EmailEntity.fromDomain(it) }
                emailDao.insertEmails(emailEntities)

                val attachments = newEmails.flatMap { email ->
                    email.attachments.map { AttachmentEntity.fromDomain(it, email.id) }
                }
                if (attachments.isNotEmpty()) {
                    attachmentDao.insertAttachments(attachments)
                }
            }

            // 3. Dynamic SLA recalculation (30 minutes response SLA from receipt timestamp)
            val now = System.currentTimeMillis()
            val allEmails = emailDao.getAllEmails(accountId).first()
            for (item in allEmails) {
                if (item.slaDeadlineTimestamp > 0L && item.slaSeverity != SlaSeverity.NONE) {
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

            SyncResult(
                isSuccess = true,
                newMessagesCount = newEmails.size,
                sentMessagesCount = sentCount,
                syncedAtTimestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            SyncResult(
                isSuccess = false,
                errorMessage = e.message ?: "Unknown sync error"
            )
        }
    }

    override suspend fun initializeSampleDataIfEmpty() {
        if (emailDao.getEmailCount() > 0) return
        accountDao.insertAccounts(SampleData.allAccounts.map { AccountEntity.fromDomain(it) })
        folderDao.insertFolders(SampleData.defaultFolders.map { FolderEntity.fromDomain(it) })
        emailDao.insertEmails(SampleData.sampleEmails.map { EmailEntity.fromDomain(it) })

        val attachments = SampleData.sampleEmails.flatMap { email ->
            email.attachments.map { AttachmentEntity.fromDomain(it, email.id) }
        }
        if (attachments.isNotEmpty()) {
            attachmentDao.insertAttachments(attachments)
        }
    }
}

