package app.jackdaw.client.data.network.model

data class SyncResult(
    val isSuccess: Boolean,
    val newMessagesCount: Int = 0,
    val unmutedNewMessagesCount: Int = 0,
    val updatedMessagesCount: Int = 0,
    val sentMessagesCount: Int = 0,
    val errorMessage: String? = null,
    val syncedAtTimestamp: Long = System.currentTimeMillis()
)

data class SendResult(
    val isSuccess: Boolean,
    val serverMessageId: String? = null,
    val errorMessage: String? = null
)
