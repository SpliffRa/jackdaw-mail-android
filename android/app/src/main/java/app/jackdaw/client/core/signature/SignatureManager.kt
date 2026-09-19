package app.jackdaw.client.core.signature

import android.content.Context
import android.content.SharedPreferences
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.MailAccount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SignatureManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jackdaw_signature_prefs", Context.MODE_PRIVATE)

    var isSignatureEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIGNATURE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SIGNATURE_ENABLED, value).apply()

    fun getSignature(account: MailAccount): String {
        val key = KEY_SIGNATURE_TEXT_PREFIX + account.id
        val saved = prefs.getString(key, null)
        return if (!saved.isNullOrBlank()) {
            saved
        } else {
            getDefaultOutlookTemplate(account)
        }
    }

    fun setSignature(account: MailAccount, text: String) {
        val key = KEY_SIGNATURE_TEXT_PREFIX + account.id
        prefs.edit().putString(key, text).apply()
    }

    fun resetToOutlookTemplate(account: MailAccount): String {
        val template = getDefaultOutlookTemplate(account)
        setSignature(account, template)
        return template
    }

    fun getDefaultOutlookTemplate(account: MailAccount): String {
        return buildString {
            append("С уважением,\n")
            append("${account.displayName}\n")
            append("${account.email}\n")
            append("Jackdaw Mail для Android")
        }
    }

    /**
     * Constructs the standard Outlook reply body:
     * 1. Empty lines for new text
     * 2. Signature (if enabled)
     * 3. Outlook standard separator: -----Исходное сообщение-----
     * 4. Header block (От, Отправлено, Кому, Тема)
     * 5. Original email body
     */
    fun buildReplyBody(account: MailAccount, replyToEmail: EmailMessage): String {
        val signature = if (isSignatureEnabled) getSignature(account) else ""
        val dateFormat = SimpleDateFormat("d MMMM yyyy г. HH:mm", Locale("ru"))
        val sentDateStr = dateFormat.format(Date(replyToEmail.timestamp))

        return buildString {
            append("\n\n")
            if (signature.isNotBlank()) {
                append(signature)
                append("\n\n")
            }
            append("-----Исходное сообщение-----\n")
            append("От: ${replyToEmail.senderName.ifBlank { replyToEmail.senderEmail }} <${replyToEmail.senderEmail}>\n")
            append("Отправлено: $sentDateStr\n")
            if (replyToEmail.toRecipients.isNotEmpty()) {
                append("Кому: ${replyToEmail.toRecipients.joinToString(", ")}\n")
            }
            append("Тема: ${replyToEmail.subject}\n\n")
            append(replyToEmail.bodyText)
        }
    }

    fun buildNewEmailBody(account: MailAccount): String {
        if (!isSignatureEnabled) return ""
        val signature = getSignature(account)
        return if (signature.isNotBlank()) "\n\n$signature" else ""
    }

    companion object {
        private const val KEY_SIGNATURE_ENABLED = "signature_enabled"
        private const val KEY_SIGNATURE_TEXT_PREFIX = "signature_text_"

        @Volatile
        private var instance: SignatureManager? = null

        fun getInstance(context: Context): SignatureManager {
            return instance ?: synchronized(this) {
                instance ?: SignatureManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
