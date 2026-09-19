package app.jackdaw.client.data.local.converter

import androidx.room.TypeConverter
import app.jackdaw.client.core.model.AccountProtocol
import app.jackdaw.client.core.model.FolderType
import app.jackdaw.client.core.model.SlaSeverity

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString("||") ?: ""
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split("||")
    }

    @TypeConverter
    fun fromAccountProtocol(protocol: AccountProtocol): String = protocol.name

    @TypeConverter
    fun toAccountProtocol(value: String): AccountProtocol = runCatching {
        AccountProtocol.valueOf(value)
    }.getOrDefault(AccountProtocol.IMAP)

    @TypeConverter
    fun fromFolderType(type: FolderType): String = type.name

    @TypeConverter
    fun toFolderType(value: String): FolderType = runCatching {
        FolderType.valueOf(value)
    }.getOrDefault(FolderType.INBOX)

    @TypeConverter
    fun fromSlaSeverity(severity: SlaSeverity?): String = severity?.name ?: SlaSeverity.NONE.name

    @TypeConverter
    fun toSlaSeverity(value: String?): SlaSeverity = runCatching {
        value?.let { SlaSeverity.valueOf(it) } ?: SlaSeverity.NONE
    }.getOrDefault(SlaSeverity.NONE)

    @TypeConverter
    fun fromDeliveryStatus(status: app.jackdaw.client.core.model.DeliveryStatus): String = status.name

    @TypeConverter
    fun toDeliveryStatus(value: String): app.jackdaw.client.core.model.DeliveryStatus = runCatching {
        app.jackdaw.client.core.model.DeliveryStatus.valueOf(value)
    }.getOrDefault(app.jackdaw.client.core.model.DeliveryStatus.SENT)
}

