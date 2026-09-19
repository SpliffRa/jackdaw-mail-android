package app.jackdaw.client.core.signature

import android.content.Context
import android.content.SharedPreferences
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.MailAccount

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
        return prefs.getString(key, TEMPLATE_REPLY_PROVIDED) ?: TEMPLATE_REPLY_PROVIDED
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
        return applyTemplate(account, TEMPLATE_REPLY_PROVIDED)
    }

    fun getDefaultOutlookTemplate(account: MailAccount): String {
        return resolveTemplate(PRESET_TEMPLATES.first { it.id == TEMPLATE_REPLY_PROVIDED }.pattern, account)
    }

    /**
     * Injects or cleanly replaces the signature at the bottom of the email body.
     * No quoting headers or separator clutters.
     */
    fun injectOrReplaceSignature(
        currentBody: String,
        newSignature: String,
        currentAccount: MailAccount? = null
    ): String {
        var text = currentBody.trimEnd()

        // Strip previous signature at the bottom if present
        val existingSignatures = mutableListOf<String>()
        if (currentAccount != null) {
            existingSignatures.add(getSignature(currentAccount).trim())
            for (tmpl in PRESET_TEMPLATES) {
                existingSignatures.add(resolveTemplate(tmpl.pattern, currentAccount).trim())
            }
        }
        for (tmpl in PRESET_TEMPLATES) {
            existingSignatures.add(tmpl.pattern.trim())
        }

        for (existing in existingSignatures) {
            if (existing.isNotBlank() && text.endsWith(existing)) {
                text = text.substring(0, text.length - existing.length).trimEnd()
                break
            }
        }

        return if (text.isBlank()) {
            if (newSignature.isNotBlank()) "\n\n\n$newSignature" else ""
        } else {
            if (newSignature.isNotBlank()) "$text\n\n$newSignature" else text
        }
    }

    fun removeSignature(currentBody: String, currentAccount: MailAccount): String {
        return injectOrReplaceSignature(currentBody, "", currentAccount)
    }

    /**
     * Constructs reply body: clean space for user's reply, with the signature placed at the bottom.
     * No '-----Исходное сообщение-----' quote block.
     */
    fun buildReplyBody(account: MailAccount, replyToEmail: EmailMessage, templateText: String? = null): String {
        val signature = templateText ?: (if (isSignatureEnabled) getSignature(account) else "")
        return if (signature.isNotBlank()) {
            "\n\n\n$signature"
        } else {
            ""
        }
    }

    fun buildNewEmailBody(account: MailAccount, templateText: String? = null): String {
        if (!isSignatureEnabled && templateText == null) return ""
        val signature = templateText ?: getSignature(account)
        return if (signature.isNotBlank()) "\n\n\n$signature" else ""
    }

    companion object {
        const val TEMPLATE_REPLY_PROVIDED = "template_reply_provided"
        const val TEMPLATE_OUTLOOK_BUSINESS = "outlook_business"
        const val TEMPLATE_OUTLOOK_MOBILE = "outlook_mobile"
        const val TEMPLATE_OUTLOOK_CORPORATE = "outlook_corporate"
        const val TEMPLATE_OUTLOOK_SHORT = "outlook_short"

        val PRESET_TEMPLATES = listOf(
            SignatureTemplate(
                id = TEMPLATE_REPLY_PROVIDED,
                name = "Ответ предоставил",
                description = "Ответ предоставил(а): {name} <{email}>",
                pattern = "Ответ предоставил(а): {name} <{email}>"
            ),
            SignatureTemplate(
                id = TEMPLATE_OUTLOOK_BUSINESS,
                name = "Деловой ответ",
                description = "С уважением, {name}",
                pattern = "С уважением,\n{name}\n{email}"
            ),
            SignatureTemplate(
                id = TEMPLATE_OUTLOOK_MOBILE,
                name = "Outlook для Android",
                description = "Классическая мобильная строка",
                pattern = "Отправлено из Outlook для Android"
            ),
            SignatureTemplate(
                id = TEMPLATE_OUTLOOK_CORPORATE,
                name = "Корпоративная",
                description = "С уважением, {name} • Jackdaw Mail",
                pattern = "С уважением,\n{name}\n{email}\nJackdaw Mail для Android"
            ),
            SignatureTemplate(
                id = TEMPLATE_OUTLOOK_SHORT,
                name = "Краткая",
                description = "Имя и email в одну строку",
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
