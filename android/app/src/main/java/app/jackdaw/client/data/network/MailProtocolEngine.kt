package app.jackdaw.client.data.network

import app.jackdaw.client.core.model.CalendarEvent
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.data.network.model.SendResult

import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.FolderType

import app.jackdaw.client.core.model.Attachment

data class DetailedEmailContent(
    val bodyText: String,
    val bodyHtml: String,
    val attachments: List<Attachment> = emptyList(),
    val isStarred: Boolean? = null,
    val hasAttachments: Boolean? = null
)

interface MailProtocolEngine {
    suspend fun fetchNewEmails(account: MailAccount, folderId: String, sinceTimestamp: Long): List<EmailMessage>
    suspend fun fetchCalendarEvents(account: MailAccount, startRange: Long, endRange: Long): List<CalendarEvent>
    suspend fun sendMessage(account: MailAccount, email: EmailMessage): SendResult
    suspend fun fetchFolders(account: MailAccount): List<Folder> = emptyList()
    suspend fun fetchEmailBodies(account: MailAccount, itemIds: List<String>): Map<String, Pair<String, String>> = emptyMap()
    suspend fun fetchEmailBody(account: MailAccount, itemId: String): Pair<String, String>? = null
    suspend fun fetchEmailFullDetails(account: MailAccount, itemId: String): DetailedEmailContent? = null
    suspend fun downloadAttachment(account: MailAccount, attachmentId: String): ByteArray? = null
    suspend fun updateEmailReadStatus(account: MailAccount, emailId: String, isRead: Boolean): Boolean = false
    suspend fun deleteEmail(account: MailAccount, emailId: String, hardDelete: Boolean = false): Boolean = false
    suspend fun moveEmail(account: MailAccount, emailId: String, targetFolderType: FolderType): Boolean = false
    suspend fun updateEmailStarStatus(account: MailAccount, emailId: String, isStarred: Boolean): Boolean = false
    suspend fun emptyTrash(account: MailAccount): Boolean = false
}

