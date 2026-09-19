package app.jackdaw.client.core.model

enum class AccountProtocol {
    IMAP,
    EXCHANGE_EWS,
    ACTIVESTYNC,
    MICROSOFT_GRAPH
}

data class MailAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val protocol: AccountProtocol = AccountProtocol.IMAP,
    val isDefault: Boolean = false,
    val avatarColorHex: Long = 0xFFF59E0BL
)

enum class FolderType {
    INBOX,
    SLA_ALERTS,
    SENT,
    OUTBOX,
    DRAFTS,
    ARCHIVE,
    TRASH,
    CUSTOM
}

enum class DeliveryStatus {
    DRAFT,
    QUEUED,
    SENDING,
    SENT,
    FAILED
}

data class Folder(
    val id: String,
    val accountId: String,
    val name: String,
    val type: FolderType,
    val unreadCount: Int = 0,
    val totalCount: Int = 0
)

enum class SlaSeverity {
    NONE,
    NORMAL,
    WARNING,
    URGENT,
    BREACHED,
    COMPLETED
}

data class SlaInfo(
    val severity: SlaSeverity,
    val deadlineTimestamp: Long,
    val remainingLabel: String
)

data class Attachment(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val localUri: String? = null
)

data class EmailMessage(
    val id: String,
    val accountId: String,
    val folderId: String,
    val senderName: String,
    val senderEmail: String,
    val toRecipients: List<String>,
    val ccRecipients: List<String> = emptyList(),
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val hasAttachments: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val slaInfo: SlaInfo? = null,
    val threadId: String? = null,
    val relatedEmailsCount: Int = 0,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT
)

