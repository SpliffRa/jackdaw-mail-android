package app.jackdaw.client.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.jackdaw.client.data.local.converter.Converters
import app.jackdaw.client.data.local.dao.AccountDao
import app.jackdaw.client.data.local.dao.AttachmentDao
import app.jackdaw.client.data.local.dao.EmailDao
import app.jackdaw.client.data.local.dao.FolderDao
import app.jackdaw.client.data.local.entity.AccountEntity
import app.jackdaw.client.data.local.entity.AttachmentEntity
import app.jackdaw.client.data.local.entity.EmailEntity
import app.jackdaw.client.data.local.entity.EmailFtsEntity
import app.jackdaw.client.data.local.entity.FolderEntity

@Database(
    entities = [
        AccountEntity::class,
        FolderEntity::class,
        EmailEntity::class,
        EmailFtsEntity::class,
        AttachmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JackdawDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun emailDao(): EmailDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        @Volatile
        private var INSTANCE: JackdawDatabase? = null

        fun getInstance(context: Context): JackdawDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JackdawDatabase::class.java,
                    "jackdaw_mail.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
