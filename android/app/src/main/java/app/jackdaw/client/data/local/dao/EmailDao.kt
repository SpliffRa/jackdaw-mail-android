package app.jackdaw.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.data.local.entity.EmailEntity
import kotlinx.coroutines.flow.Flow

data class FolderStat(
    val folderId: String,
    val totalCount: Int,
    val unreadCount: Int
)

data class SlaRecalculationItem(
    val id: String,
    val timestamp: Long,
    val slaDeadlineTimestamp: Long,
    val slaSeverity: SlaSeverity,
    val slaRemainingLabel: String
)

@Dao
interface EmailDao {
    @Query("""
        SELECT folderId, 
               COUNT(*) AS totalCount, 
               COALESCE(SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END), 0) AS unreadCount 
        FROM emails 
        WHERE accountId = :accountId 
        GROUP BY folderId

        UNION ALL

        SELECT '__sla_alerts__' AS folderId, 
               COUNT(*) AS totalCount, 
               COALESCE(SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END), 0) AS unreadCount 
        FROM emails 
        WHERE accountId = :accountId 
          AND slaSeverity != 'NONE' 
          AND slaSeverity != 'COMPLETED'

        UNION ALL

        SELECT '__outbox__' AS folderId, 
               COUNT(*) AS totalCount, 
               COALESCE(SUM(CASE WHEN deliveryStatus IN ('QUEUED', 'SENDING', 'FAILED') THEN 1 ELSE 0 END), 0) AS unreadCount 
        FROM emails 
        WHERE accountId = :accountId 
          AND (folderId LIKE '%outbox%' OR deliveryStatus IN ('QUEUED', 'SENDING', 'FAILED'))
    """)
    fun getFolderStats(accountId: String): Flow<List<FolderStat>>

    @Query("""
        SELECT id, timestamp, slaDeadlineTimestamp, slaSeverity, slaRemainingLabel 
        FROM emails 
        WHERE accountId = :accountId 
          AND slaDeadlineTimestamp > 0 
          AND slaSeverity != 'NONE' 
          AND slaSeverity != 'COMPLETED'
    """)
    suspend fun getActiveSlaEmailsForRecalculation(accountId: String): List<SlaRecalculationItem>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId AND folderId = :folderId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun getPagedEmailsInFolder(accountId: String, folderId: String, limit: Int): Flow<List<EmailEntity>>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId 
          AND folderId = :folderId 
          AND (:filterUnread = 0 OR isRead = 0)
          AND (:filterStarred = 0 OR isStarred = 1)
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun getPagedEmailsInFolderFiltered(
        accountId: String,
        folderId: String,
        limit: Int,
        filterUnread: Boolean = false,
        filterStarred: Boolean = false
    ): Flow<List<EmailEntity>>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId AND folderId = :folderId 
        ORDER BY timestamp DESC
    """)
    fun getEmailsInFolder(accountId: String, folderId: String): Flow<List<EmailEntity>>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId AND (folderId = :folderId OR folderId LIKE '%outbox%' OR deliveryStatus IN ('QUEUED', 'SENDING', 'FAILED')) 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun getPagedOutboxEmails(accountId: String, folderId: String, limit: Int): Flow<List<EmailEntity>>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId AND (folderId = :folderId OR folderId LIKE '%outbox%' OR deliveryStatus IN ('QUEUED', 'SENDING', 'FAILED')) 
        ORDER BY timestamp DESC
    """)
    fun getOutboxEmails(accountId: String, folderId: String): Flow<List<EmailEntity>>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId 
        ORDER BY timestamp DESC
    """)
    fun getAllEmails(accountId: String): Flow<List<EmailEntity>>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId AND slaSeverity != 'NONE' AND slaSeverity != 'COMPLETED' 
        ORDER BY slaDeadlineTimestamp ASC, timestamp DESC 
        LIMIT :limit
    """)
    fun getPagedSlaEmails(accountId: String, limit: Int): Flow<List<EmailEntity>>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId AND slaSeverity != 'NONE' AND slaSeverity != 'COMPLETED' 
        ORDER BY slaDeadlineTimestamp ASC, timestamp DESC
    """)
    fun getSlaEmails(accountId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :id LIMIT 1")
    fun getEmailById(id: String): Flow<EmailEntity?>

    @Query("SELECT * FROM emails WHERE id = :id LIMIT 1")
    suspend fun getEmailEntityById(id: String): EmailEntity?

    @Query("""
        SELECT emails.id, emails.accountId, emails.folderId, emails.senderName, emails.senderEmail, 
               emails.toRecipients, emails.ccRecipients, emails.subject, emails.snippet, 
               '' AS bodyText, NULL AS bodyHtml, emails.timestamp, emails.isRead, emails.isStarred, 
               emails.hasAttachments, emails.slaSeverity, emails.slaDeadlineTimestamp, 
               emails.slaRemainingLabel, emails.threadId, emails.relatedEmailsCount, emails.deliveryStatus 
        FROM emails 
        JOIN emails_fts ON emails.rowid = emails_fts.rowid 
        WHERE emails_fts MATCH :query 
        ORDER BY emails.timestamp DESC 
        LIMIT :limit
    """)
    fun searchEmailsPaged(query: String, limit: Int): Flow<List<EmailEntity>>

    @Query("""
        SELECT emails.id, emails.accountId, emails.folderId, emails.senderName, emails.senderEmail, 
               emails.toRecipients, emails.ccRecipients, emails.subject, emails.snippet, 
               '' AS bodyText, NULL AS bodyHtml, emails.timestamp, emails.isRead, emails.isStarred, 
               emails.hasAttachments, emails.slaSeverity, emails.slaDeadlineTimestamp, 
               emails.slaRemainingLabel, emails.threadId, emails.relatedEmailsCount, emails.deliveryStatus 
        FROM emails 
        JOIN emails_fts ON emails.rowid = emails_fts.rowid 
        WHERE emails_fts MATCH :query 
        ORDER BY emails.timestamp DESC
    """)
    fun searchEmails(query: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE threadId = :threadId AND threadId != '' ORDER BY timestamp DESC, id DESC LIMIT 50")
    fun getEmailsInThread(threadId: String): Flow<List<EmailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailEntity)

    @Query("UPDATE emails SET isRead = :isRead WHERE id = :emailId")
    suspend fun updateReadStatus(emailId: String, isRead: Boolean)

    @Query("UPDATE emails SET isStarred = :isStarred WHERE id = :emailId")
    suspend fun updateStarredStatus(emailId: String, isStarred: Boolean)

    @Query("UPDATE emails SET folderId = :folderId WHERE id = :emailId")
    suspend fun updateFolder(emailId: String, folderId: String)

    @Query("UPDATE emails SET folderId = :newFolderId WHERE accountId = :accountId AND folderId = :oldFolderId")
    suspend fun reassignFolderEmails(accountId: String, oldFolderId: String, newFolderId: String)

    @Query("DELETE FROM emails WHERE id = :emailId")
    suspend fun deleteEmail(emailId: String)

    @Query("SELECT COUNT(*) FROM emails")
    suspend fun getEmailCount(): Int

    @Query("""
        SELECT COUNT(DISTINCT emails.id) 
        FROM emails 
        JOIN folders ON emails.folderId = folders.id 
        WHERE emails.isRead = 0 
          AND folders.isMuted = 0 
          AND emails.folderId NOT LIKE '%trash%' 
          AND emails.folderId NOT LIKE '%archive%'
    """)
    fun getTotalUnreadCountFlow(): Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT emails.id) 
        FROM emails 
        JOIN folders ON emails.folderId = folders.id 
        WHERE emails.isRead = 0 
          AND folders.isMuted = 0 
          AND emails.folderId NOT LIKE '%trash%' 
          AND emails.folderId NOT LIKE '%archive%'
    """)
    fun getUnmutedUnreadCountFlow(): Flow<Int>

    @Query("""
        SELECT emails.id, emails.accountId, emails.folderId, emails.senderName, emails.senderEmail, 
               emails.toRecipients, emails.ccRecipients, emails.subject, emails.snippet, 
               '' AS bodyText, NULL AS bodyHtml, emails.timestamp, emails.isRead, emails.isStarred, 
               emails.hasAttachments, emails.slaSeverity, emails.slaDeadlineTimestamp, 
               emails.slaRemainingLabel, emails.threadId, emails.relatedEmailsCount, emails.deliveryStatus 
        FROM emails 
        JOIN folders ON emails.folderId = folders.id 
        WHERE emails.isRead = 0 
          AND folders.isMuted = 0 
          AND emails.folderId NOT LIKE '%trash%' 
          AND emails.folderId NOT LIKE '%archive%' 
        GROUP BY emails.id 
        ORDER BY emails.timestamp DESC 
        LIMIT :limit
    """)
    fun getUnmutedUnreadEmailsFlow(limit: Int = 10): Flow<List<EmailEntity>>

    @Query("""
        SELECT emails.id, emails.accountId, emails.folderId, emails.senderName, emails.senderEmail, 
               emails.toRecipients, emails.ccRecipients, emails.subject, emails.snippet, 
               '' AS bodyText, NULL AS bodyHtml, emails.timestamp, emails.isRead, emails.isStarred, 
               emails.hasAttachments, emails.slaSeverity, emails.slaDeadlineTimestamp, 
               emails.slaRemainingLabel, emails.threadId, emails.relatedEmailsCount, emails.deliveryStatus 
        FROM emails 
        JOIN folders ON emails.folderId = folders.id 
        WHERE emails.isRead = 0 
          AND folders.isMuted = 0 
          AND emails.folderId NOT LIKE '%trash%' 
          AND emails.folderId NOT LIKE '%archive%' 
        GROUP BY emails.id 
        ORDER BY emails.timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getUnmutedUnreadEmails(limit: Int = 10): List<EmailEntity>

    @Query("SELECT * FROM emails WHERE deliveryStatus = 'QUEUED' ORDER BY timestamp ASC")
    suspend fun getPendingOutgoingEmails(): List<EmailEntity>

    @Query("UPDATE emails SET deliveryStatus = :status, folderId = :folderId WHERE id = :emailId")
    suspend fun updateDeliveryStatus(emailId: String, status: app.jackdaw.client.core.model.DeliveryStatus, folderId: String)

    @Query("UPDATE emails SET slaSeverity = :severity, slaDeadlineTimestamp = :deadlineTimestamp, slaRemainingLabel = :remainingLabel WHERE id = :emailId")
    suspend fun updateSlaInfo(emailId: String, severity: SlaSeverity, deadlineTimestamp: Long, remainingLabel: String)

    @Query("DELETE FROM emails WHERE accountId = :accountId")
    suspend fun deleteEmailsByAccount(accountId: String)

    @Query("DELETE FROM emails WHERE accountId = :accountId AND folderId = :folderId")
    suspend fun deleteEmailsInFolder(accountId: String, folderId: String)

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE slaSeverity = 'URGENT' OR slaSeverity = 'WARNING'
    """)
    suspend fun getUrgentSlaEmails(): List<EmailEntity>

    @Query("""
        SELECT id, accountId, folderId, senderName, senderEmail, toRecipients, ccRecipients, 
               subject, snippet, '' AS bodyText, NULL AS bodyHtml, timestamp, isRead, isStarred, 
               hasAttachments, slaSeverity, slaDeadlineTimestamp, slaRemainingLabel, threadId, 
               relatedEmailsCount, deliveryStatus 
        FROM emails 
        WHERE accountId = :accountId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getRecentEmails(accountId: String, limit: Int): List<EmailEntity>

    @Query("SELECT COUNT(*) FROM emails WHERE folderId = :folderId AND isRead = 0")
    suspend fun getFolderUnreadCount(folderId: String): Int

    @Query("SELECT COUNT(*) FROM emails WHERE folderId = :folderId")
    suspend fun getFolderTotalCount(folderId: String): Int

    @Query("UPDATE emails SET bodyText = :bodyText, bodyHtml = :bodyHtml, snippet = :snippet WHERE id = :id")
    suspend fun updateEmailBody(id: String, bodyText: String, bodyHtml: String?, snippet: String)

    @Query("UPDATE emails SET bodyText = :bodyText, bodyHtml = :bodyHtml, snippet = :snippet, hasAttachments = :hasAttachments WHERE id = :id")
    suspend fun updateEmailBodyWithAttachments(id: String, bodyText: String, bodyHtml: String?, snippet: String, hasAttachments: Boolean)

    @Query("SELECT id FROM emails WHERE id IN (:ids)")
    suspend fun getExistingEmailIds(ids: List<String>): List<String>

    @Query("SELECT id FROM emails WHERE accountId = :accountId AND folderId = :folderId")
    suspend fun getEmailIdsInFolder(accountId: String, folderId: String): List<String>

    @Query("SELECT id FROM emails WHERE accountId = :accountId AND folderId = :folderId AND timestamp >= :sinceTimestamp")
    suspend fun getEmailIdsInFolderSince(accountId: String, folderId: String, sinceTimestamp: Long): List<String>

    @Query("SELECT COUNT(*) FROM emails WHERE accountId = :accountId AND folderId = :folderId")
    suspend fun getEmailCountInFolder(accountId: String, folderId: String): Int

    @Query("DELETE FROM emails WHERE id IN (:emailIds)")
    suspend fun deleteEmailsByIds(emailIds: List<String>)

    @Query("DELETE FROM emails WHERE folderId NOT IN (SELECT id FROM folders)")
    suspend fun deleteOrphanedEmails()
}
