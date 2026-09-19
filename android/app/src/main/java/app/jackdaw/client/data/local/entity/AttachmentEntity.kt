package app.jackdaw.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.jackdaw.client.core.model.Attachment

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey
    val id: String,
    val emailId: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val localUri: String? = null
) {
    fun toDomain(): Attachment = Attachment(
        id = id,
        fileName = fileName,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        localUri = localUri
    )

    companion object {
        fun fromDomain(domain: Attachment, emailId: String): AttachmentEntity = AttachmentEntity(
            id = domain.id,
            emailId = emailId,
            fileName = domain.fileName,
            sizeBytes = domain.sizeBytes,
            mimeType = domain.mimeType,
            localUri = domain.localUri
        )
    }
}
