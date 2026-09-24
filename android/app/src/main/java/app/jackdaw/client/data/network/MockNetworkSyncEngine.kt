package app.jackdaw.client.data.network

import app.jackdaw.client.core.model.Attachment
import app.jackdaw.client.core.model.DeliveryStatus
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.model.SlaInfo
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.data.network.model.SendResult
import kotlinx.coroutines.delay
import java.util.UUID

class MockNetworkSyncEngine : MailProtocolEngine {

    override suspend fun fetchNewEmails(
        account: MailAccount,
        folderId: String,
        sinceTimestamp: Long
    ): List<EmailMessage> {
        // Safe clean sync: do not inject synthetic third-party emails into user mailbox
        delay(300)
        return emptyList()
    }

    override suspend fun fetchCalendarEvents(
        account: MailAccount,
        startRange: Long,
        endRange: Long
    ): List<app.jackdaw.client.core.model.CalendarEvent> {
        delay(200)
        return emptyList()
    }


    override suspend fun sendMessage(account: MailAccount, email: EmailMessage): SendResult {
        // Simulate SMTP / Exchange protocol transmission delay
        delay(600)
        return SendResult(
            isSuccess = true,
            serverMessageId = "srv_${UUID.randomUUID().toString().take(8)}"
        )
    }

    override suspend fun updateEmailReadStatus(account: MailAccount, emailId: String, isRead: Boolean): Boolean {
        delay(100)
        return true
    }

    override suspend fun deleteEmail(account: MailAccount, emailId: String, hardDelete: Boolean): Boolean {
        delay(100)
        return true
    }

    override suspend fun moveEmail(account: MailAccount, emailId: String, targetFolderType: app.jackdaw.client.core.model.FolderType): Boolean {
        delay(100)
        return true
    }

    override suspend fun updateEmailStarStatus(account: MailAccount, emailId: String, isStarred: Boolean): Boolean {
        delay(100)
        return true
    }
}
