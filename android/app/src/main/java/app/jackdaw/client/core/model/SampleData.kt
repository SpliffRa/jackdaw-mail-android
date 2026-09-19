package app.jackdaw.client.core.model

object SampleData {
    val defaultAccount = MailAccount(
        id = "acc_primary",
        email = "president@jackdaw.app",
        displayName = "Mr. President",
        protocol = AccountProtocol.EXCHANGE_EWS,
        isDefault = true,
        avatarColorHex = 0xFFF59E0BL
    )

    val secondAccount = MailAccount(
        id = "acc_secondary",
        email = "tech.lead@fastmail.com",
        displayName = "Tech Lead (Личный)",
        protocol = AccountProtocol.IMAP,
        isDefault = false,
        avatarColorHex = 0xFF3B82F6L
    )

    val allAccounts = listOf(defaultAccount, secondAccount)

    val defaultFolders = listOf(
        // Primary account folders
        Folder(id = "inbox", accountId = "acc_primary", name = "Входящие", type = FolderType.INBOX, unreadCount = 4, totalCount = 28),
        Folder(id = "sla_alerts", accountId = "acc_primary", name = "SLA Контроль", type = FolderType.SLA_ALERTS, unreadCount = 2, totalCount = 5),
        Folder(id = "sent", accountId = "acc_primary", name = "Отправленные", type = FolderType.SENT, unreadCount = 0, totalCount = 14),
        Folder(id = "outbox", accountId = "acc_primary", name = "Исходящие", type = FolderType.OUTBOX, unreadCount = 0, totalCount = 0),
        Folder(id = "drafts", accountId = "acc_primary", name = "Черновики", type = FolderType.DRAFTS, unreadCount = 1, totalCount = 2),
        Folder(id = "archive", accountId = "acc_primary", name = "Архив", type = FolderType.ARCHIVE, unreadCount = 0, totalCount = 120),
        Folder(id = "trash", accountId = "acc_primary", name = "Корзина", type = FolderType.TRASH, unreadCount = 0, totalCount = 8),

        // Secondary account folders
        Folder(id = "sec_inbox", accountId = "acc_secondary", name = "Входящие", type = FolderType.INBOX, unreadCount = 2, totalCount = 12),
        Folder(id = "sec_sent", accountId = "acc_secondary", name = "Отправленные", type = FolderType.SENT, unreadCount = 0, totalCount = 6),
        Folder(id = "sec_outbox", accountId = "acc_secondary", name = "Исходящие", type = FolderType.OUTBOX, unreadCount = 0, totalCount = 0),
        Folder(id = "sec_archive", accountId = "acc_secondary", name = "Архив", type = FolderType.ARCHIVE, unreadCount = 0, totalCount = 45),
        Folder(id = "sec_trash", accountId = "acc_secondary", name = "Корзина", type = FolderType.TRASH, unreadCount = 0, totalCount = 3)
    )

    val sampleEmails = listOf(
        // Primary Account: Threaded NDA sequence
        EmailMessage(
            id = "msg_nda_1",
            accountId = "acc_primary",
            folderId = "inbox",
            senderName = "Департамент комплаенс",
            senderEmail = "compliance@corp-partner.com",
            toRecipients = listOf("president@jackdaw.app"),
            subject = "Соглашение о конфиденциальности (NDA) — первичный проект",
            snippet = "Направляем для ознакомления проект соглашения о неразглашении конфиденциальной информации...",
            bodyText = "Уважаемые партнеры!\n\nНаправляем согласованный проект соглашения о неразглашении конфиденциальной информации перед стартом совместной интеграции.\n\nПросим ознакомиться со статьями 3 и 4.",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 180,
            isRead = true,
            isStarred = false,
            hasAttachments = true,
            attachments = listOf(
                Attachment("att_nda_draft", "NDA_Agreement_Draft_v1.pdf", 420000, "application/pdf")
            ),
            slaInfo = null,
            threadId = "thread_nda",
            relatedEmailsCount = 2
        ),
        EmailMessage(
            id = "msg_nda_2",
            accountId = "acc_primary",
            folderId = "sent",
            senderName = "Mr. President",
            senderEmail = "president@jackdaw.app",
            toRecipients = listOf("compliance@corp-partner.com"),
            subject = "Re: Соглашение о конфиденциальности (NDA) — первичный проект",
            snippet = "Добрый день. Ознакомился с проектом. По пункту 4.2 предлагаем скорректировать срок действия обязательств...",
            bodyText = "Добрый день!\n\nОзнакомился с проектом. По пункту 4.2 предлагаем скорректировать срок действия обязательств с 5 до 3 лет с момента раскрытия информации, в соответствии со стандартом нашей компании.\n\nЖду обновленную редакцию.",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 60,
            isRead = true,
            isStarred = false,
            hasAttachments = false,
            threadId = "thread_nda",
            relatedEmailsCount = 2
        ),
        EmailMessage(
            id = "msg_1",
            accountId = "acc_primary",
            folderId = "inbox",
            senderName = "Департамент комплаенс",
            senderEmail = "compliance@corp-partner.com",
            toRecipients = listOf("president@jackdaw.app"),
            subject = "Срочно: Финальное согласование условий соглашения о конфиденциальности (NDA)",
            snippet = "Добрый день! Просим подтвердить пункт 4.2 в рамках регламентного окна ответа SLA (30 минут с момента получения)...",
            bodyText = "Добрый день!\n\nПросим подтвердить формулировку пункта 4.2 в приложенном проекте соглашения о конфиденциальности. Напоминаем, что согласно регламенту компании ответ должен быть предоставлен в течение 30 минут с момента получения обращения.\n\nВ случае необходимости внесения правок просим оперативно предоставить комментарии ответным письмом.\n\nС уважением,\nЮридический департамент",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 22,
            isRead = false,
            isStarred = true,
            hasAttachments = true,
            attachments = listOf(
                Attachment("att_1", "NDA_Agreement_Final.pdf", 458000, "application/pdf")
            ),
            slaInfo = SlaInfo(
                severity = SlaSeverity.URGENT,
                deadlineTimestamp = System.currentTimeMillis() + 1000 * 60 * 8,
                remainingLabel = "8 мин"
            ),
            threadId = "thread_nda",
            relatedEmailsCount = 2
        ),

        EmailMessage(
            id = "msg_2",
            accountId = "acc_primary",
            folderId = "inbox",
            senderName = "Сервисный деск Jackdaw",
            senderEmail = "support@jackdaw.app",
            toRecipients = listOf("president@jackdaw.app"),
            subject = "Тикет #4892: Синхронизация почтовых ящиков Exchange ActiveSync",
            snippet = "Ваш запрос принят на контроль. Согласно регламенту SLA ответ должен быть предоставлен в течение 30 минут с момента поступления...",
            bodyText = "Здравствуйте!\n\nУведомляем, что инцидент по оптимизации протокола ActiveSync успешно передан дежурному инженеру. Регламентное время предоставления ответа — 30 минут с момента регистрации тикета.\n\nТекущий статус: В обработке.\nПриоритет: Высокий.",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
            isRead = false,
            isStarred = false,
            hasAttachments = false,
            slaInfo = SlaInfo(
                severity = SlaSeverity.WARNING,
                deadlineTimestamp = System.currentTimeMillis() + 1000 * 60 * 15,
                remainingLabel = "15 мин"
            ),
            threadId = "thread_ticket",
            relatedEmailsCount = 1
        ),
        EmailMessage(
            id = "msg_3",
            accountId = "acc_primary",
            folderId = "inbox",
            senderName = "Алексей Смирнов",
            senderEmail = "a.smirnov@techteam.io",
            toRecipients = listOf("president@jackdaw.app"),
            subject = "Архитектура нативного Android клиента Jackdaw Mail (Kotlin + Compose)",
            snippet = "Привет! Подготовили спецификацию. Контрольный срок первого ответа по регламенту SLA — 30 минут...",
            bodyText = "Привет!\n\nСобрали спецификацию нативного клиента на Kotlin и Jetpack Compose. Ключевые преимущества:\n- Плавный интерфейс 120 FPS\n- Полнотекстовый поиск по письмам через Room SQLite FTS5 без ожидания сервера\n- Энергоэффективный WorkManager, не сажающий батарею в фоне\n- Поддержка темного оформления и фирменного стиля Amber/Charcoal.\n\nЖдем ответа в рамках SLA (30 минут с момента получения)!",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 5,
            isRead = false,
            isStarred = true,
            hasAttachments = true,
            attachments = listOf(
                Attachment("att_2", "Android_Architecture_Spec.pdf", 1240000, "application/pdf"),
                Attachment("att_3", "Benchmark_Metrics.xlsx", 85000, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            ),
            slaInfo = SlaInfo(
                severity = SlaSeverity.NORMAL,
                deadlineTimestamp = System.currentTimeMillis() + 1000 * 60 * 25,
                remainingLabel = "25 мин"
            ),
            threadId = "thread_android_arch",
            relatedEmailsCount = 4
        ),
        EmailMessage(
            id = "msg_4",
            accountId = "acc_primary",
            folderId = "inbox",
            senderName = "GitHub Notifications",
            senderEmail = "notifications@github.com",
            toRecipients = listOf("president@jackdaw.app"),
            subject = "[jackdaw-mail] CI / Automated release build passed successfully",
            snippet = "Workflow run 'Desktop & Android OTA' #142 finished with status: SUCCESS in 4m 12s...",
            bodyText = "Workflow Desktop & Android OTA completed successfully.\nAll test suites passed.\nArtifacts generated and published to release shell.",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 240,
            isRead = true,
            isStarred = false,
            hasAttachments = false,
            slaInfo = null,
            threadId = "thread_ci",
            relatedEmailsCount = 0
        ),

        // Secondary Account: Personal IMAP emails
        EmailMessage(
            id = "msg_sec_1",
            accountId = "acc_secondary",
            folderId = "sec_inbox",
            senderName = "Fastmail Team",
            senderEmail = "welcome@fastmail.com",
            toRecipients = listOf("tech.lead@fastmail.com"),
            subject = "Ваш защищенный почтовый ящик настроен и готов к работе",
            snippet = "Добро пожаловать в Fastmail! Протокол IMAP и SMTP настроены с TLS шифрованием...",
            bodyText = "Приветствуем!\n\nВаша учетная запись IMAP активирована.\nСервер входящей почты: imap.fastmail.com:993 (SSL/TLS)\nСервер исходящей почты: smtp.fastmail.com:465 (SSL/TLS)\n\nПриятного использования!",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 360,
            isRead = false,
            isStarred = true,
            hasAttachments = false
        ),
        EmailMessage(
            id = "msg_sec_2",
            accountId = "acc_secondary",
            folderId = "sec_inbox",
            senderName = "Kotlin Weekly",
            senderEmail = "newsletter@kotlinweekly.net",
            toRecipients = listOf("tech.lead@fastmail.com"),
            subject = "Kotlin Weekly #438: Jetpack Compose 1.7, Kotlin 2.1 & K2 Compiler",
            snippet = "Дайджест новостей из мира Kotlin: обновления компилятора K2, Compose Multiplatform и лучшие практики архитектуры...",
            bodyText = "В этом выпуске:\n1. Ключевые оптимизации компилятора K2 в больших проектах.\n2. Новые возможности анимации в Jetpack Compose.\n3. Room SQLite с поддержкой FTS5 для мобильных приложений.\n\nЧитайте онлайн на kotlinweekly.net!",
            timestamp = System.currentTimeMillis() - 1000 * 60 * 600,
            isRead = false,
            isStarred = false,
            hasAttachments = false
        )
    )
}
