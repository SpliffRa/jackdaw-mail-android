package app.jackdaw.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.jackdaw.client.core.model.SlaSeverity
import app.jackdaw.client.data.local.entity.EmailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails WHERE accountId = :accountId AND folderId = :folderId ORDER BY timestamp DESC")
    fun getEmailsInFolder(accountId: String, folderId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND (folderId = :folderId OR folderId LIKE '%outbox%' OR deliveryStatus IN ('QUEUED', 'SENDING', 'FAILED')) ORDER BY timestamp DESC")
    fun getOutboxEmails(accountId: String, folderId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getAllEmails(accountId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND slaSeverity != 'NONE' AND slaSeverity != 'COMPLETED' ORDER BY slaDeadlineTimestamp ASC, timestamp DESC")
    fun getSlaEmails(accountId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :id LIMIT 1")
    fun getEmailById(id: String): Flow<EmailEntity?>

    @Query("SELECT emails.* FROM emails JOIN emails_fts ON emails.rowid = emails_fts.rowid WHERE emails_fts MATCH :query ORDER BY emails.timestamp DESC")
    fun searchEmails(query: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE threadId = :threadId ORDER BY timestamp ASC")
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

    @Query("DELETE FROM emails WHERE id = :emailId")
    suspend fun deleteEmail(emailId: String)

    @Query("SELECT COUNT(*) FROM emails")
    suspend fun getEmailCount(): Int

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

    @Query("SELECT * FROM emails WHERE slaSeverity = 'URGENT' OR slaSeverity = 'WARNING'")
    suspend fun getUrgentSlaEmails(): List<EmailEntity>

    @Query("SELECT * FROM emails WHERE accountId = :accountId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEmails(accountId: String, limit: Int): List<EmailEntity>
}
