package app.jackdaw.client.core.util

/**
 * Utility for sanitizing email subjects, previews, and snippets.
 * Removes Unicode Object Replacement Characters (\uFFFC, \uFFFD) which render as [OBJ] boxes in Android,
 * strips zero-width/control characters, and normalizes whitespaces.
 */
object TextSanitizer {

    private val OBJ_CHARACTERS_REGEX = Regex("[\\uFFFC\\uFFFD\\uFEFF\\u200B-\\u200F\\u202A-\\u202E]")
    private val CONTROL_CHARACTERS_REGEX = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val MULTI_SPACE_REGEX = Regex("[ \\t\\xA0]+")
    private val MULTI_NEWLINE_REGEX = Regex("(\\r?\\n){3,}")

    /**
     * Cleans snippet/preview text to ensure high readability and no visual glitches like [OBJ].
     */
    fun cleanPreview(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(OBJ_CHARACTERS_REGEX, " ")
            .replace(CONTROL_CHARACTERS_REGEX, "")
            .replace("&nbsp;", " ")
            .replace("&#xfffc;", " ")
            .replace("&#65532;", " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .replace(MULTI_NEWLINE_REGEX, "\n\n")
            .trim()
    }

    /**
     * Cleans email subject.
     */
    fun cleanSubject(subject: String?): String {
        if (subject.isNullOrBlank()) return "(Без темы)"
        val cleaned = subject
            .replace(OBJ_CHARACTERS_REGEX, "")
            .replace(CONTROL_CHARACTERS_REGEX, "")
            .replace("&nbsp;", " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        return cleaned.ifBlank { "(Без темы)" }
    }

    /**
     * Checks if the given address string is a Microsoft Exchange X.500 / LegacyExchangeDN path
     * e.g., </O=SMARTDS/OU=EXCHANGE ADMINISTRATIVE GROUP (FYDIBOHF23SPDLT)/CN=RECIPIENTS/CN=USER831CE13E>.
     */
    fun isExchangeLegacyDn(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val trimmed = address.trim().trim('<', '>', '"', '\'')
        return trimmed.startsWith("/O=", ignoreCase = true) ||
               trimmed.startsWith("/o=", ignoreCase = true) ||
               (trimmed.contains("/OU=", ignoreCase = true) && trimmed.contains("/CN=", ignoreCase = true))
    }

    /**
     * Cleans an email address for display in the UI.
     * Replaces internal Exchange X.500 paths with clean account email in Sent items,
     * or suppresses them in received emails so raw LDAP paths are never shown.
     */
    fun cleanEmailAddress(
        address: String?,
        isSentFolder: Boolean = false,
        accountEmail: String? = null
    ): String {
        if (address.isNullOrBlank()) {
            return if (isSentFolder && !accountEmail.isNullOrBlank()) accountEmail.trim() else ""
        }
        val clean = address.trim().trim('<', '>', '"', '\'')
        if (isExchangeLegacyDn(clean)) {
            return if (isSentFolder && !accountEmail.isNullOrBlank()) {
                accountEmail.trim()
            } else {
                ""
            }
        }
        return clean
    }
}

/**
 * Extension property for quick cleaning of snippets and previews.
 */
fun String?.cleanEmailPreview(): String = TextSanitizer.cleanPreview(this)

/**
 * Extension property for quick cleaning of subjects.
 */
fun String?.cleanEmailSubject(): String = TextSanitizer.cleanSubject(this)

/**
 * Extension function for sanitizing email address display.
 */
fun String?.cleanDisplayEmail(isSentFolder: Boolean = false, accountEmail: String? = null): String =
    TextSanitizer.cleanEmailAddress(this, isSentFolder, accountEmail)
