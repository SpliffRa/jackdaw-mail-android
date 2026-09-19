package app.jackdaw.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.jackdaw.client.core.model.Attachment
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.model.SampleData
import app.jackdaw.client.data.repository.MailRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MailUiState(
    val currentAccount: MailAccount = SampleData.defaultAccount,
    val accounts: List<MailAccount> = listOf(SampleData.defaultAccount),
    val folders: List<Folder> = SampleData.defaultFolders,
    val selectedFolder: Folder = SampleData.defaultFolders.first(),
    val emails: List<EmailMessage> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MailViewModel(
    private val repository: MailRepository
) : ViewModel() {

    private val _currentAccount = MutableStateFlow(SampleData.defaultAccount)
    private val _selectedFolderId = MutableStateFlow(SampleData.defaultFolders.first().id)
    private val _searchQuery = MutableStateFlow("")
    private val _isSyncing = MutableStateFlow(false)
    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    private val _syncMessage = MutableStateFlow<String?>(null)

    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val accounts = repository.getAccounts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf(SampleData.defaultAccount)
    )

    val folders = _currentAccount.flatMapLatest { account ->
        repository.getFolders(account.id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SampleData.defaultFolders
    )

    val selectedFolder: StateFlow<Folder> = combine(_selectedFolderId, folders) { folderId, folderList ->
        folderList.find { it.id == folderId } ?: folderList.firstOrNull() ?: SampleData.defaultFolders.first()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SampleData.defaultFolders.first()
    )

    val emails: StateFlow<List<EmailMessage>> = combine(
        _currentAccount,
        selectedFolder,
        _searchQuery
    ) { account, folder, query ->
        Triple(account, folder, query)
    }.flatMapLatest { (account, folder, query) ->
        if (query.isNotBlank()) {
            repository.searchEmails(query)
        } else {
            repository.getEmailsInFolder(account.id, folder.id)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SampleData.sampleEmails
    )

    init {
        viewModelScope.launch {
            repository.initializeSampleDataIfEmpty()
        }
    }

    val currentAccount: StateFlow<MailAccount> = _currentAccount.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun selectFolder(folder: Folder) {
        _selectedFolderId.value = folder.id
        _searchQuery.value = ""
    }

    fun selectAccount(account: MailAccount) {
        viewModelScope.launch {
            val accountFolders = repository.getFolders(account.id).first()
            if (accountFolders.isNotEmpty()) {
                _selectedFolderId.value = accountFolders.first().id
            }
            _currentAccount.value = account
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun triggerSync(onCompleted: ((String) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val account = _currentAccount.value
            val result = repository.syncAll(account.id)
            _isSyncing.value = false
            _lastSyncTimestamp.value = System.currentTimeMillis()

            val msg = if (result.isSuccess) {
                if (result.newMessagesCount > 0) {
                    try {
                        app.jackdaw.client.core.notification.SoundNotificationManager.getInstance(
                            app.jackdaw.client.JackdawApp.instance
                        ).playIncomingMailSound()
                    } catch (_: Exception) {}
                }
                when {
                    result.newMessagesCount > 0 && result.sentMessagesCount > 0 ->
                        "Синхронизировано: +${result.newMessagesCount} новых, ${result.sentMessagesCount} отправлено"
                    result.newMessagesCount > 0 ->
                        "Получено новых писем: ${result.newMessagesCount}"
                    result.sentMessagesCount > 0 ->
                        "Отправлено из очереди: ${result.sentMessagesCount}"
                    else ->
                        "Все папки актуальны"
                }
            } else {
                result.errorMessage ?: "Ошибка синхронизации"
            }
            _syncMessage.value = msg
            onCompleted?.invoke(msg)
        }
    }

    fun markAsRead(emailId: String, isRead: Boolean) {
        viewModelScope.launch {
            repository.markAsRead(emailId, isRead)
        }
    }

    fun toggleStar(emailId: String, isStarred: Boolean) {
        viewModelScope.launch {
            repository.toggleStar(emailId, isStarred)
        }
    }

    fun moveToArchive(emailId: String) {
        viewModelScope.launch {
            repository.moveToArchive(emailId)
        }
    }

    fun moveToTrash(emailId: String) {
        viewModelScope.launch {
            repository.moveToTrash(emailId)
        }
    }

    fun restoreEmail(emailId: String, originalFolderId: String) {
        viewModelScope.launch {
            repository.restoreEmail(emailId, originalFolderId)
        }
    }

    fun getEmailsInThread(threadId: String): kotlinx.coroutines.flow.Flow<List<EmailMessage>> {
        return repository.getEmailsInThread(threadId)
    }

    fun getEmailById(emailId: String): kotlinx.coroutines.flow.Flow<EmailMessage?> {
        return repository.getEmailById(emailId)
    }

    fun sendEmail(to: String, subject: String, body: String, attachments: List<Attachment> = emptyList()) {
        val account = _currentAccount.value
        val newEmail = EmailMessage(
            id = "msg_${System.currentTimeMillis()}",
            accountId = account.id,
            folderId = "outbox",
            senderName = account.displayName,
            senderEmail = account.email,
            toRecipients = listOf(to),
            subject = subject,
            snippet = body.take(120),
            bodyText = body,
            timestamp = System.currentTimeMillis(),
            isRead = true,
            hasAttachments = attachments.isNotEmpty(),
            attachments = attachments,
            deliveryStatus = app.jackdaw.client.core.model.DeliveryStatus.QUEUED
        )
        viewModelScope.launch {
            repository.queueEmailForSending(newEmail)
            try {
                app.jackdaw.client.core.notification.SoundNotificationManager.getInstance(
                    app.jackdaw.client.JackdawApp.instance
                ).playSentMailSound()
            } catch (_: Exception) {}
            // Trigger sync to attempt sending immediately
            triggerSync()
        }
    }

    fun emptyTrash(onDone: () -> Unit = {}) {
        val account = _currentAccount.value
        val folder = selectedFolder.value
        viewModelScope.launch {
            repository.emptyTrash(account.id, folder.id)
            onDone()
        }
    }

    fun markSlaCompleted(emailId: String) {
        viewModelScope.launch {
            repository.markSlaCompleted(emailId)
        }
    }

    fun addAccount(account: MailAccount) {
        viewModelScope.launch {
            repository.addAccount(account)
            selectAccount(account)
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            val allAccounts = accounts.value
            val remaining = allAccounts.filter { it.id != accountId }
            if (_currentAccount.value.id == accountId && remaining.isNotEmpty()) {
                selectAccount(remaining.first())
            }
            repository.deleteAccount(accountId)
        }
    }

    class Factory(private val repository: MailRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MailViewModel::class.java)) {
                return MailViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
