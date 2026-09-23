package app.jackdaw.client

import app.jackdaw.client.data.auth.OwaAuthManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwaProtocolEngineTest {

    @Test
    fun testNormalizeOwaUrl() {
        assertEquals("https://mail.corp.com/owa/", OwaAuthManager.normalizeOwaUrl("mail.corp.com"))
        assertEquals("https://mail.corp.com/owa/", OwaAuthManager.normalizeOwaUrl("https://mail.corp.com/owa"))
        assertEquals("https://mail.corp.com/owa/", OwaAuthManager.normalizeOwaUrl("https://mail.corp.com/owa/auth/logon.aspx"))
        assertEquals("https://mail.corp.com/owa/", OwaAuthManager.normalizeOwaUrl("https://mail.corp.com/owa/"))
    }

    @Test
    fun testParseCookiesAndExtractCanary() {
        val rawCookies = "cadata=abc123xyz; sessionid=sess999; X-OWA-CANARY=canary_token_value_456; domain=.corp.mail"
        val parsed = OwaAuthManager.parseCookies(rawCookies)

        assertEquals("abc123xyz", parsed["cadata"])
        assertEquals("sess999", parsed["sessionid"])
        assertEquals("canary_token_value_456", parsed["X-OWA-CANARY"])

        val canary = OwaAuthManager.extractCanary(rawCookies)
        assertEquals("canary_token_value_456", canary)
        assertTrue(OwaAuthManager.isSessionAuthenticated(rawCookies, "https://mail.corp.com/owa/#path=/mail"))
    }

    @Test
    fun testVersionComparison() {
        fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }

        assertTrue(compareVersions("1.4.8", "1.4.7") > 0)
        assertTrue(compareVersions("1.4.8", "1.4.6") > 0)
        assertEquals(0, compareVersions("1.4.8", "1.4.8"))
        assertTrue(compareVersions("1.4.6", "1.4.8") < 0)
    }
}
