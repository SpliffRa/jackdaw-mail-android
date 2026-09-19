package app.jackdaw.client.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "emails_fts")
@Fts4(contentEntity = EmailEntity::class)
data class EmailFtsEntity(
    val subject: String,
    val senderName: String,
    val senderEmail: String,
    val snippet: String,
    val bodyText: String
)
