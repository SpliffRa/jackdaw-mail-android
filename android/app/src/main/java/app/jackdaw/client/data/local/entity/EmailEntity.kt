package app.jackdaw.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.jackdaw.client.core.model.Attachment
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.SlaInfo
import app.jackdaw.client.core.model.SlaSeverity

@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey
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
    val slaSeverity: SlaSeverity = SlaSeverity.NONE,
    val slaDeadlineTimestamp: Long = 0L,
    val slaRemainingLabel: String = "",
    val threadId: String? = null,
    val relatedEmailsCount: Int = 0,
    val deliveryStatus: app.jackdaw.client.core.model.DeliveryStatus = app.jackdaw.client.core.model.DeliveryStatus.SENT
) {
    fun toDomain(attachments: List<Attachment> = emptyList()): EmailMessage {
        val sla = if (slaSeverity != SlaSeverity.NONE) {
            SlaInfo(
                severity = slaSeverity,
                deadlineTimestamp = slaDeadlineTimestamp,
                remainingLabel = slaRemainingLabel
            )
        } else null

        return EmailMessage(
            id = id,
            accountId = accountId,
            folderId = folderId,
            senderName = senderName,
            senderEmail = senderEmail,
            toRecipients = toRecipients,
            ccRecipients = ccRecipients,
            subject = subject,
            snippet = snippet,
            bodyText = bodyText,
            bodyHtml = bodyHtml,
            timestamp = timestamp,
            isRead = isRead,
            isStarred = isStarred,
            hasAttachments = hasAttachments,
            attachments = attachments,
            slaInfo = sla,
            threadId = threadId,
            relatedEmailsCount = relatedEmailsCount,
            deliveryStatus = deliveryStatus
        )
    }

    companion object {
        fun fromDomain(domain: EmailMessage): EmailEntity = EmailEntity(
            id = domain.id,
            accountId = domain.accountId,
            folderId = domain.folderId,
            senderName = domain.senderName,
            senderEmail = domain.senderEmail,
            toRecipients = domain.toRecipients,
            ccRecipients = domain.ccRecipients,
            subject = domain.subject,
            snippet = domain.snippet,
            bodyText = domain.bodyText,
            bodyHtml = domain.bodyHtml,
            timestamp = domain.timestamp,
            isRead = domain.isRead,
            isStarred = domain.isStarred,
            hasAttachments = domain.hasAttachments,
            slaSeverity = domain.slaInfo?.severity ?: SlaSeverity.NONE,
            slaDeadlineTimestamp = domain.slaInfo?.deadlineTimestamp ?: 0L,
            slaRemainingLabel = domain.slaInfo?.remainingLabel ?: "",
            threadId = domain.threadId,
            relatedEmailsCount = domain.relatedEmailsCount,
            deliveryStatus = domain.deliveryStatus
        )
    }
}
