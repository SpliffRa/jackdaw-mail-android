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

    private var syncIteration = 0

    override suspend fun fetchNewEmails(
        account: MailAccount,
        folderId: String,
        sinceTimestamp: Long
    ): List<EmailMessage> {
        // Simulate network round-trip delay
        delay(750)

        if (folderId != "inbox") {
            return emptyList()
        }

        syncIteration++
        val now = System.currentTimeMillis()

        // Generate dynamic incoming emails on sync iterations to demonstrate live sync
        return when (syncIteration) {
            1 -> listOf(
                EmailMessage(
                    id = "msg_sync_${now}",
                    accountId = account.id,
                    folderId = "inbox",
                    senderName = "Иван Кузнецов (DevOps)",
                    senderEmail = "i.kuznetsov@corp-cloud.ru",
                    toRecipients = listOf(account.email),
                    subject = "Патч безопасности Kubernetes кластера применен",
                    snippet = "Все узлы production кластера обновлены до версии 1.31.2 без простоя сервисов. SLA доступности 99.98% соблюден...",
                    bodyText = "Коллеги, добрый день!\n\nПлановые регламентные работы по обновлению control-plane и worker-узлов успешно завершены.\n\nМетрики APM в норме, задержки API снизились на 12%.\nДежурные инженеры переведены в штатный режим.\n\nС уважением,\nОтдел инфраструктуры",
                    timestamp = now,
                    isRead = false,
                    isStarred = true,
                    hasAttachments = true,
                    attachments = listOf(
                        Attachment("att_k8s", "k8s_patch_audit_report.pdf", 312000, "application/pdf")
                    ),
                    slaInfo = SlaInfo(
                        severity = SlaSeverity.NORMAL,
                        deadlineTimestamp = now + 1000 * 60 * 30,
                        remainingLabel = "30 мин"
                    ),
                    threadId = "thread_k8s_upgrade",
                    relatedEmailsCount = 1,
                    deliveryStatus = DeliveryStatus.SENT
                )
            )
            2 -> listOf(
                EmailMessage(
                    id = "msg_sync_${now}",
                    accountId = account.id,
                    folderId = "inbox",
                    senderName = "Департамент комплаенс",
                    senderEmail = "compliance@corp-partner.com",
                    toRecipients = listOf(account.email),
                    subject = "RE: Срочно: Финальное согласование условий соглашения о конфиденциальности (NDA)",
                    snippet = "Спасибо за оперативный ответ! Правки по пункту 4.2 приняты с нашей стороны, направляем финальную версию с ЭЦП...",
                    bodyText = "Добрый день!\n\nСпасибо за оперативное согласование. Мы внесли согласованные правки в пункт 4.2.\n\nФинальная версия с ЭЦП прикреплена к письму.\n\nС уважением,\nЮридический департамент",
                    timestamp = now,
                    isRead = false,
                    isStarred = true,
                    hasAttachments = true,
                    attachments = listOf(
                        Attachment("att_nda_signed", "NDA_Agreement_Signed.pdf", 512000, "application/pdf")
                    ),
                    slaInfo = SlaInfo(
                        severity = SlaSeverity.NORMAL,
                        deadlineTimestamp = now + 1000 * 60 * 30,
                        remainingLabel = "30 мин"
                    ),
                    threadId = "thread_nda",
                    relatedEmailsCount = 3,
                    deliveryStatus = DeliveryStatus.SENT
                )
            )
            else -> emptyList()
        }
    }

    override suspend fun sendMessage(account: MailAccount, email: EmailMessage): SendResult {
        // Simulate SMTP / Exchange protocol transmission delay
        delay(600)
        return SendResult(
            isSuccess = true,
            serverMessageId = "srv_${UUID.randomUUID().toString().take(8)}"
        )
    }
}
