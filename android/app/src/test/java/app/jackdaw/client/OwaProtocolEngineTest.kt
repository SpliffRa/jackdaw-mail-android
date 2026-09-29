package app.jackdaw.client

import app.jackdaw.client.data.auth.OwaAuthManager
import app.jackdaw.client.data.network.OwaProtocolEngine
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

    @Test
    fun testBuildEwsUrl() {
        val engine = OwaProtocolEngine()
        assertEquals("https://mail.corp.com/EWS/Exchange.asmx", engine.buildEwsUrl("https://mail.corp.com/owa/"))
        assertEquals("https://mail.corp.com/EWS/Exchange.asmx", engine.buildEwsUrl("https://mail.corp.com/owa"))
        assertEquals("https://mail.corp.com/EWS/Exchange.asmx", engine.buildEwsUrl("https://mail.corp.com/owa/auth/logon.aspx"))
        assertEquals("https://mail.corp.com/EWS/Exchange.asmx", engine.buildEwsUrl("https://mail.corp.com"))
        assertEquals("mail.corp.com/EWS/Exchange.asmx", engine.buildEwsUrl("mail.corp.com"))
    }

    @Test
    fun testParseEwsMessagesXml() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <m:FindItemResponse xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages"
                                    xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types">
                  <m:ResponseMessages>
                    <m:FindItemResponseMessage ResponseClass="Success">
                      <m:ResponseCode>NoError</m:ResponseCode>
                      <m:RootFolder TotalItemsInView="1" IncludesLastItemInRange="true">
                        <t:Items>
                          <t:Message>
                            <t:ItemId Id="AQMkAD" ChangeKey="CQAAAB" />
                            <t:Subject>Тестовое письмо от руководства</t:Subject>
                            <t:DateTimeReceived>2026-09-23T18:30:00Z</t:DateTimeReceived>
                            <t:HasAttachments>false</t:HasAttachments>
                            <t:From>
                              <t:Mailbox>
                                <t:Name>Иван Иванов</t:Name>
                                <t:EmailAddress>i.ivanov@corp.mail</t:EmailAddress>
                              </t:Mailbox>
                            </t:From>
                            <t:IsRead>false</t:IsRead>
                            <t:Body BodyType="Text">Коллеги, добрый день. Напоминаю о встрече в 19:00.</t:Body>
                          </t:Message>
                        </t:Items>
                      </m:RootFolder>
                    </m:FindItemResponseMessage>
                  </m:ResponseMessages>
                </m:FindItemResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val account = app.jackdaw.client.core.model.MailAccount(
            id = "acc_1",
            email = "user@corp.mail",
            displayName = "User",
            serverHost = "https://mail.corp.mail/owa"
        )

        val engine = OwaProtocolEngine()
        val messages = engine.parseEwsMessagesXml(account, "acc_1_inbox", xml)
        assertEquals(1, messages.size)
        val msg = messages[0]
        assertEquals("AQMkAD", msg.id)
        assertEquals("Тестовое письмо от руководства", msg.subject)
        assertEquals("Иван Иванов", msg.senderName)
        assertEquals("i.ivanov@corp.mail", msg.senderEmail)
        assertFalse(msg.isRead)
        assertTrue(msg.bodyText.contains("Коллеги, добрый день"))
    }

    @Test
    fun testParseEwsFoldersXml() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <m:FindFolderResponse xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages"
                                      xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types">
                  <m:ResponseMessages>
                    <m:FindFolderResponseMessage ResponseClass="Success">
                      <m:ResponseCode>NoError</m:ResponseCode>
                      <m:RootFolder TotalItemsInView="2" IncludesLastItemInRange="true">
                        <t:Folders>
                          <t:Folder>
                            <t:FolderId Id="fld_inbox_123" ChangeKey="AQAA" />
                            <t:DisplayName>Входящие</t:DisplayName>
                            <t:TotalCount>42</t:TotalCount>
                            <t:UnreadCount>5</t:UnreadCount>
                          </t:Folder>
                          <t:Folder>
                            <t:FolderId Id="fld_sent_456" ChangeKey="AQAA" />
                            <t:DisplayName>Отправленные</t:DisplayName>
                            <t:TotalCount>15</t:TotalCount>
                            <t:UnreadCount>0</t:UnreadCount>
                          </t:Folder>
                        </t:Folders>
                      </m:RootFolder>
                    </m:FindFolderResponseMessage>
                  </m:ResponseMessages>
                </m:FindFolderResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val account = app.jackdaw.client.core.model.MailAccount(
            id = "acc_1",
            email = "user@corp.mail",
            displayName = "User",
            serverHost = "https://mail.corp.mail/owa"
        )

        val engine = OwaProtocolEngine()
        val folders = engine.parseEwsFoldersXml(account, xml)
        assertEquals(2, folders.size)
        assertEquals("acc_1_inbox", folders[0].id)
        assertEquals(app.jackdaw.client.core.model.FolderType.INBOX, folders[0].type)
        assertEquals(5, folders[0].unreadCount)
        assertEquals(42, folders[0].totalCount)

        assertEquals("acc_1_sent", folders[1].id)
        assertEquals(app.jackdaw.client.core.model.FolderType.SENT, folders[1].type)
        assertEquals(0, folders[1].unreadCount)
        assertEquals(15, folders[1].totalCount)
    }

    @Test
    fun testFindItemPayloadSortOrder() {
        val engine = OwaProtocolEngine()
        val payload = engine.buildFindItemEmailPayload("inbox")
        val body = payload.getJSONObject("Body")
        val sortOrder = body.getJSONArray("SortOrder")
        assertEquals(1, sortOrder.length())
        val firstSort = sortOrder.getJSONObject(0)
        assertEquals("Descending", firstSort.getString("Order"))
        val path = firstSort.getJSONObject("Path")
        assertEquals("item:DateTimeReceived", path.getString("FieldURI"))

        val parentFolderIds = body.getJSONArray("ParentFolderIds")
        assertEquals(1, parentFolderIds.length())
        val parent = parentFolderIds.getJSONObject(0)
        assertEquals("DistinguishedFolderId:#Exchange", parent.getString("__type"))
        assertEquals("inbox", parent.getString("Id"))
    }
}

