package app.jackdaw.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.jackdaw.client.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE accountId = :accountId")
    fun getFoldersByAccount(accountId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Query("UPDATE folders SET unreadCount = :unreadCount, totalCount = :totalCount WHERE id = :id")
    suspend fun updateCounts(id: String, unreadCount: Int, totalCount: Int)

    @Query("DELETE FROM folders WHERE accountId = :accountId")
    suspend fun deleteFoldersByAccount(accountId: String)

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND type = :type LIMIT 1")
    suspend fun getFolderByType(accountId: String, type: app.jackdaw.client.core.model.FolderType): FolderEntity?

    @Query("""
        DELETE FROM folders 
        WHERE accountId = :accountId AND (
            name LIKE '%Календарь%' OR name LIKE '%Calendar%' OR
            name LIKE '%Контакты%' OR name LIKE '%Contacts%' OR
            name LIKE '%Recipient Cache%' OR
            name LIKE '%Задачи%' OR name LIKE '%Tasks%' OR
            name LIKE '%Журнал%' OR name LIKE '%Journal%' OR
            name LIKE '%Дни рождения%' OR name LIKE '%Birthdays%' OR
            name LIKE '%Conversation Action%' OR
            name LIKE '%External Contacts%' OR name LIKE '%ExternalContacts%' OR
            name LIKE '%Quick Step%' OR name LIKE '%Настройка быстрых%' OR
            name LIKE '%Yammer%' OR
            name LIKE '%Файлы%' OR name LIKE '%Files%' OR
            name LIKE '%Sync Issues%' OR name LIKE '%Ошибки синхронизации%' OR
            name LIKE '%Конфликты%' OR name LIKE '%Conflicts%' OR
            name LIKE '%Локальные ошибки%' OR name LIKE '%Ошибки сервера%' OR
            name LIKE '%Failures%' OR
            name LIKE '%Заметки%' OR name LIKE '%Notes%' OR
            name LIKE '%Организации%' OR
            name LIKE '%RSS%' OR name LIKE '%Feeds%' OR
            name LIKE '{%'
        )
    """)
    suspend fun cleanupNonMailFolders(accountId: String)
}
