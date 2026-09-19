package app.jackdaw.client.data.network

import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.data.network.model.SendResult

interface MailProtocolEngine {
    suspend fun fetchNewEmails(account: MailAccount, folderId: String, sinceTimestamp: Long): List<EmailMessage>
    suspend fun sendMessage(account: MailAccount, email: EmailMessage): SendResult
}
