package app.jackdaw.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.jackdaw.client.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE emailId = :emailId")
    fun getAttachmentsForEmail(emailId: String): Flow<List<AttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE emailId = :emailId")
    suspend fun deleteAttachmentsForEmail(emailId: String)

    @Query("DELETE FROM attachments WHERE emailId IN (SELECT id FROM emails WHERE accountId = :accountId AND folderId = :folderId)")
    suspend fun deleteAttachmentsInFolder(accountId: String, folderId: String)

    @Query("DELETE FROM attachments WHERE rowid NOT IN (SELECT MIN(rowid) FROM attachments GROUP BY emailId, fileName, sizeBytes)")
    suspend fun deduplicateAttachments()
}
