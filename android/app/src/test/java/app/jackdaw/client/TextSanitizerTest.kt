package app.jackdaw.client

import app.jackdaw.client.core.util.TextSanitizer
import app.jackdaw.client.core.util.cleanDisplayEmail
import app.jackdaw.client.core.util.cleanEmailPreview
import app.jackdaw.client.core.util.cleanEmailSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun testCleanPreviewRemovesGlitchCharacters() {
        val raw = "Hello\uFFFCWorld \uFEFFwith   spaces &nbsp; and \u0000control"
        val cleaned = raw.cleanEmailPreview()
        assertEquals("Hello World with spaces and control", cleaned)
    }

    @Test
    fun testCleanPreviewHandlesNullOrBlank() {
        assertEquals("", TextSanitizer.cleanPreview(null))
        assertEquals("", TextSanitizer.cleanPreview("   "))
    }

    @Test
    fun testCleanSubjectFallback() {
        assertEquals("(Без темы)", TextSanitizer.cleanSubject(null))
        assertEquals("(Без темы)", TextSanitizer.cleanSubject("   "))
        assertEquals("Важное сообщение", "  Важное \uFFFC  сообщение  ".cleanEmailSubject())
    }

    @Test
    fun testIsExchangeLegacyDnDetection() {
        val dn1 = "/O=SMARTDS/OU=EXCHANGE ADMINISTRATIVE GROUP (FYDIBOHF23SPDLT)/CN=RECIPIENTS/CN=USER831CE13E"
        val dn2 = "</O=SMARTDS/OU=FIRST ADMINISTRATIVE GROUP/CN=RECIPIENTS/CN=ADMIN>"
        val dn3 = "/o=company/ou=exchange/cn=recipients/cn=test"

        assertTrue(TextSanitizer.isExchangeLegacyDn(dn1))
        assertTrue(TextSanitizer.isExchangeLegacyDn(dn2))
        assertTrue(TextSanitizer.isExchangeLegacyDn(dn3))

        // Normal emails should return false
        assertFalse(TextSanitizer.isExchangeLegacyDn("john.doe@company.com"))
        assertFalse(TextSanitizer.isExchangeLegacyDn("<john.doe@company.com>"))
        assertFalse(TextSanitizer.isExchangeLegacyDn("user+tag@domain.co.uk"))
        assertFalse(TextSanitizer.isExchangeLegacyDn(null))
        assertFalse(TextSanitizer.isExchangeLegacyDn(""))
    }

    @Test
    fun testCleanEmailAddressForSentMail() {
        val dn = "/O=SMARTDS/OU=EXCHANGE ADMINISTRATIVE GROUP/CN=RECIPIENTS/CN=USER"
        val userEmail = "president@jackdaw.app"

        // In Sent folder, Exchange DN is replaced by user's account email
        val result = dn.cleanDisplayEmail(isSentFolder = true, accountEmail = userEmail)
        assertEquals("president@jackdaw.app", result)

        // Null or blank address in Sent folder also resolves to account email
        assertEquals("president@jackdaw.app", (null as String?).cleanDisplayEmail(isSentFolder = true, accountEmail = userEmail))

        // Normal address in Sent folder remains cleaned
        assertEquals("recipient@other.com", "<recipient@other.com>".cleanDisplayEmail(isSentFolder = true, accountEmail = userEmail))
    }

    @Test
    fun testCleanEmailAddressForReceivedMail() {
        val dn = "/O=SMARTDS/OU=EXCHANGE ADMINISTRATIVE GROUP/CN=RECIPIENTS/CN=USER"
        val userEmail = "president@jackdaw.app"

        // In received mail, raw internal Exchange DN is suppressed so raw LDAP is not displayed
        val result = dn.cleanDisplayEmail(isSentFolder = false, accountEmail = userEmail)
        assertEquals("", result)

        // Normal email address is preserved and stripped of brackets
        val normal = "<colleague@company.com>"
        assertEquals("colleague@company.com", normal.cleanDisplayEmail(isSentFolder = false, accountEmail = userEmail))
    }
}
