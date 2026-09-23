package app.jackdaw.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.FolderType

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val name: String,
    val type: FolderType,
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
    val displayOrder: Int = 0,
    val isMuted: Boolean = false
) {
    fun toDomain(): Folder = Folder(
        id = id,
        accountId = accountId,
        name = name,
        type = type,
        unreadCount = unreadCount,
        totalCount = totalCount,
        displayOrder = displayOrder,
        isMuted = isMuted
    )

    companion object {
        fun fromDomain(domain: Folder): FolderEntity = FolderEntity(
            id = domain.id,
            accountId = domain.accountId,
            name = domain.name,
            type = domain.type,
            unreadCount = domain.unreadCount,
            totalCount = domain.totalCount,
            displayOrder = domain.displayOrder,
            isMuted = domain.isMuted
        )
    }
}
