package app.jackdaw.client.core.signature

import android.content.Context
import android.content.SharedPreferences
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.MailAccount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SignatureTemplate(
    val id: String,
    val name: String,
    val description: String,
    val pattern: String
)

class SignatureManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jackdaw_signature_prefs", Context.MODE_PRIVATE)

    var isSignatureEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIGNATURE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SIGNATURE_ENABLED, value).apply()

    fun getTemplates(): List<SignatureTemplate> = PRESET_TEMPLATES

    fun getTemplateById(id: String): SignatureTemplate? =
        PRESET_TEMPLATES.find { it.id == id }

    fun getSelectedTemplateId(account: MailAccount): String {
        val key = KEY_TEMPLATE_ID_PREFIX + account.id
        return prefs.getString(key, TEMPLATE_OUTLOOK_CORPORATE) ?: TEMPLATE_OUTLOOK_CORPORATE
    }

    fun setSelectedTemplateId(account: MailAccount, templateId: String) {
        val key = KEY_TEMPLATE_ID_PREFIX + account.id
        prefs.edit().putString(key, templateId).apply()
    }

    /**
     * Resolves variables ({name}, {email}, {app}) in a signature template pattern.
     */
    fun resolveTemplate(pattern: String, account: MailAccount): String {
        return pattern
            .replace("{name}", account.displayName.ifBlank { account.email.substringBefore("@") })
            .replace("{email}", account.email)
            .replace("{app}", "Jackdaw Mail для Android")
    }

    /**
     * Retrieves the active signature text for the given account.
     * If user explicitly edited the text, returns saved text (with resolved variables).
     * Otherwise returns resolved selected template.
     */
    fun getSignature(account: MailAccount): String {
        val customKey = KEY_SIGNATURE_TEXT_PREFIX + account.id
        val saved = prefs.getString(customKey, null)
        return if (!saved.isNullOrBlank()) {
            resolveTemplate(saved, account)
        } else {
            val templateId = getSelectedTemplateId(account)
            val template = getTemplateById(templateId) ?: PRESET_TEMPLATES[0]
            resolveTemplate(template.pattern, account)
        }
    }

    fun setSignature(account: MailAccount, text: String) {
        val key = KEY_SIGNATURE_TEXT_PREFIX + account.id
        prefs.edit().putString(key, text).apply()
    }

    /**
     * Loads a specific preset template for the account and persists it as active.
     */
    fun applyTemplate(account: MailAccount, templateId: String): String {
        val template = getTemplateById(templateId) ?: PRESET_TEMPLATES[0]
        setSelectedTemplateId(account, template.id)
        val rendered = resolveTemplate(template.pattern, account)
        setSignature(account, rendered)
        return rendered
    }

    fun resetToOutlookTemplate(account: MailAccount): String {
        return applyTemplate(account, TEMPLATE_OUTLOOK_CORPORATE)
    }

    fun getDefaultOutlookTemplate(account: MailAccount): String {
        return resolveTemplate(PRESET_TEMPLATES.first { it.id == TEMPLATE_OUTLOOK_CORPORATE }.pattern, account)
    }

    /**
     * Injects or replaces signature in an email body while respecting Outlook quote format.
     */
    fun injectOrReplaceSignature(currentBody: String, newSignature: String): String {
        if (!currentBody.contains(OUTLOOK_QUOTE_SEPARATOR)) {
            // New email or plain body
            val trimmed = currentBody.trimEnd()
            return if (trimmed.isBlank()) {
                newSignature
            } else {
                "$trimmed\n\n$newSignature"
            }
        }

        // Email with quoted reply
        val parts = currentBody.split(OUTLOOK_QUOTE_SEPARATOR, limit = 2)
        val userContent = parts[0].trimEnd()
        val quoteContent = parts.getOrNull(1) ?: ""

        val updatedUserPart = if (userContent.isBlank()) {
            newSignature
        } else {
            "$userContent\n\n$newSignature"
        }

        return buildString {
            append(updatedUserPart)
            append("\n\n")
            append(OUTLOOK_QUOTE_SEPARATOR)
            append(quoteContent)
        }
    }

    /**
     * Constructs the standard Outlook reply body:
     * 1. Signature (if enabled)
     * 2. Outlook standard separator: -----Исходное сообщение-----
     * 3. Header block (От, Отправлено, Кому, Тема)
     * 4. Original email body
     */
    fun buildReplyBody(account: MailAccount, replyToEmail: EmailMessage, templateText: String? = null): String {
        val signature = templateText ?: (if (isSignatureEnabled) getSignature(account) else "")
        val dateFormat = SimpleDateFormat("d MMMM yyyy г. HH:mm", Locale("ru"))
        val sentDateStr = dateFormat.format(Date(replyToEmail.timestamp))

        return buildString {
            if (signature.isNotBlank()) {
                append("\n\n")
                append(signature)
                append("\n\n")
            } else {
                append("\n\n")
            }
            append(OUTLOOK_QUOTE_SEPARATOR)
            append("\n")
            append("От: ${replyToEmail.senderName.ifBlank { replyToEmail.senderEmail }} <${replyToEmail.senderEmail}>\n")
            append("Отправлено: $sentDateStr\n")
            if (replyToEmail.toRecipients.isNotEmpty()) {
                append("Кому: ${replyToEmail.toRecipients.joinToString(", ")}\n")
            }
            append("Тема: ${replyToEmail.subject}\n\n")
            append(replyToEmail.bodyText)
        }
    }

    fun buildNewEmailBody(account: MailAccount, templateText: String? = null): String {
        if (!isSignatureEnabled && templateText == null) return ""
        val signature = templateText ?: getSignature(account)
        return if (signature.isNotBlank()) "\n\n$signature" else ""
    }

    companion object {
        const val OUTLOOK_QUOTE_SEPARATOR = "-----Исходное сообщение-----"

        const val TEMPLATE_OUTLOOK_MOBILE = "outlook_mobile"
        const val TEMPLATE_OUTLOOK_BUSINESS = "outlook_business"
        const val TEMPLATE_OUTLOOK_CORPORATE = "outlook_corporate"
        const val TEMPLATE_OUTLOOK_SHORT = "outlook_short"

        val PRESET_TEMPLATES = listOf(
            SignatureTemplate(
                id = TEMPLATE_OUTLOOK_MOBILE,
                name = "Outlook для Android",
                description = "Мобильный стандарт Microsoft",
                pattern = "Отправлено из Outlook для Android"
            ),
            SignatureTemplate(
                id = TEMPLATE_OUTLOOK_BUSINESS,
                name = "Деловой Outlook",
                description = "С уважением, Имя и адрес почты",
                pattern = "С уважением,\n{name}\n{email}"
            ),
            SignatureTemplate(
                id = TEMPLATE_OUTLOOK_CORPORATE,
                name = "Корпоративный Outlook",
                description = "Деловая подпись с указанием клиента",
                pattern = "С уважением,\n{name}\n{email}\nJackdaw Mail для Android"
            ),
            SignatureTemplate(
                id = TEMPLATE_OUTLOOK_SHORT,
                name = "Краткий шаблон",
                description = "Компактная строка в одну строку",
                pattern = "{name} • {email}"
            )
        )

        private const val KEY_SIGNATURE_ENABLED = "signature_enabled"
        private const val KEY_SIGNATURE_TEXT_PREFIX = "signature_text_"
        private const val KEY_TEMPLATE_ID_PREFIX = "signature_template_id_"

        @Volatile
        private var instance: SignatureManager? = null

        fun getInstance(context: Context): SignatureManager {
            return instance ?: synchronized(this) {
                instance ?: SignatureManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
