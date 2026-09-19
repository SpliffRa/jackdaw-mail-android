package app.jackdaw.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.jackdaw.client.core.model.AccountProtocol
import app.jackdaw.client.core.model.MailAccount

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val email: String,
    val displayName: String,
    val protocol: AccountProtocol = AccountProtocol.IMAP,
    val isDefault: Boolean = false,
    val avatarColorHex: Long = 0xFFF59E0BL
) {
    fun toDomain(): MailAccount = MailAccount(
        id = id,
        email = email,
        displayName = displayName,
        protocol = protocol,
        isDefault = isDefault,
        avatarColorHex = avatarColorHex
    )

    companion object {
        fun fromDomain(domain: MailAccount): AccountEntity = AccountEntity(
            id = domain.id,
            email = domain.email,
            displayName = domain.displayName,
            protocol = domain.protocol,
            isDefault = domain.isDefault,
            avatarColorHex = domain.avatarColorHex
        )
    }
}
