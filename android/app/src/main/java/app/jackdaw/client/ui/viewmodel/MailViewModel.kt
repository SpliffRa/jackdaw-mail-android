package app.jackdaw.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.jackdaw.client.core.model.Attachment
import app.jackdaw.client.core.model.EmailMessage
import app.jackdaw.client.core.model.Folder
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.data.repository.MailRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MailUiState(
    val currentAccount: MailAccount? = null,
    val accounts: List<MailAccount> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolder: Folder? = null,
    val emails: List<EmailMessage> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MailViewModel(
    private val repository: MailRepository
) : ViewModel() {

    private val _manualSelectedAccountId = MutableStateFlow<String?>(null)
    private val _selectedFolderId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isSyncing = MutableStateFlow(false)
    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    private val _syncMessage = MutableStateFlow<String?>(null)

    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val accounts: StateFlow<List<MailAccount>> = repository.getAccounts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val currentAccount: StateFlow<MailAccount?> = combine(accounts, _manualSelectedAccountId) { accList, manualId ->
        if (accList.isEmpty()) null
        else accList.find { it.id == manualId } ?: accList.find { it.isDefault } ?: accList.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val folders: StateFlow<List<Folder>> = currentAccount.flatMapLatest { account ->
        if (account != null) {
            repository.getFolders(account.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val selectedFolder: StateFlow<Folder?> = combine(_selectedFolderId, folders) { folderId, folderList ->
        if (folderId != null) {
            folderList.find { it.id == folderId } ?: folderList.firstOrNull()
        } else {
            folderList.firstOrNull()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val emails: StateFlow<List<EmailMessage>> = combine(
        currentAccount,
        selectedFolder,
        _searchQuery
    ) { account, folder, query ->
        Triple(account, folder, query)
    }.flatMapLatest { (account, folder, query) ->
        if (account == null || folder == null) {
            flowOf(emptyList())
        } else if (query.isNotBlank()) {
            repository.searchEmails(query)
        } else {
            repository.getEmailsInFolder(account.id, folder.id)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val totalUnreadCount: StateFlow<Int> = repository.getTotalUnreadCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    init {
        viewModelScope.launch {
            repository.initializeSampleDataIfEmpty()
        }
        viewModelScope.launch {
            totalUnreadCount.collect { count ->
                app.jackdaw.client.core.notification.LauncherBadgeManager.setBadge(
                    app.jackdaw.client.JackdawApp.instance,
                    count
                )
            }
        }
        // Real-time foreground sync polling ticker (25s intervals)
        viewModelScope.launch {
            delay(2000)
            currentAccount.value?.let { acc ->
                if (!_isSyncing.value) {
                    repository.syncAll(acc.id)
                }
            }
            while (isActive) {
                delay(25000)
                currentAccount.value?.let { acc ->
                    if (!_isSyncing.value) {
                        val result = repository.syncAll(acc.id)
                        _lastSyncTimestamp.value = System.currentTimeMillis()
                        if (result.isSuccess && result.newMessagesCount > 0) {
                            try {
                                app.jackdaw.client.core.notification.SoundNotificationManager.getInstance(
                                    app.jackdaw.client.JackdawApp.instance
                                ).playIncomingMailSound()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun selectFolder(folder: Folder) {
        _selectedFolderId.value = folder.id
        _searchQuery.value = ""
    }

    fun selectAccount(account: MailAccount) {
        viewModelScope.launch {
            _manualSelectedAccountId.value = account.id
            val accountFolders = repository.getFolders(account.id).first()
            if (accountFolders.isNotEmpty()) {
                _selectedFolderId.value = accountFolders.first().id
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun triggerSync(onCompleted: ((String) -> Unit)? = null) {
        val account = currentAccount.value ?: return
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
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
                        "Синхронизировано: +${app.jackdaw.client.core.util.PluralRules.formatEmailCount(result.newMessagesCount)}, ${result.sentMessagesCount} отправлено"
                    result.newMessagesCount > 0 ->
                        "Получено: ${app.jackdaw.client.core.util.PluralRules.formatEmailCount(result.newMessagesCount)}"
                    result.sentMessagesCount > 0 ->
                        "Отправлено: ${app.jackdaw.client.core.util.PluralRules.formatEmailCount(result.sentMessagesCount)}"
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

    fun unarchiveEmail(emailId: String) {
        viewModelScope.launch {
            repository.unarchiveEmail(emailId)
        }
    }

    fun moveToTrash(emailId: String) {
        viewModelScope.launch {
            repository.moveToTrash(emailId)
        }
    }

    fun permanentlyDeleteEmail(emailId: String) {
        viewModelScope.launch {
            repository.permanentlyDeleteEmail(emailId)
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

    fun loadEmailBodyIfNeeded(email: EmailMessage) {
        if (email.bodyText.isBlank() || email.bodyText == email.subject) {
            viewModelScope.launch {
                val account = currentAccount.value ?: return@launch
                val pair = repository.fetchEmailBodyDirect(account, email.id)
                if (pair != null) {
                    val bodyText = pair.first.ifBlank { email.bodyText }
                    val bodyHtml = pair.second.ifBlank { email.bodyHtml }
                    val snippet = pair.first.take(150).ifBlank { email.snippet }
                    repository.updateEmailBody(email.id, bodyText, bodyHtml, snippet)
                }
            }
        }
    }

    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        attachments: List<Attachment> = emptyList(),
        replyToEmailId: String? = null
    ) {
        val account = currentAccount.value ?: return
        viewModelScope.launch {
            var threadId = "thread_${System.currentTimeMillis()}"
            var parentTimestamp = 0L
            if (replyToEmailId != null) {
                // Immediately fulfill SLA on the original incoming email
                repository.markSlaCompleted(replyToEmailId)
                repository.markAsRead(replyToEmailId, true)
                try {
                    val repliedEmail = repository.getEmailById(replyToEmailId).firstOrNull()
                    if (repliedEmail != null) {
                        if (!repliedEmail.threadId.isNullOrBlank()) {
                            threadId = repliedEmail.threadId!!
                        }
                        parentTimestamp = repliedEmail.timestamp
                    }
                } catch (_: Exception) {}
            }

            val currentNow = System.currentTimeMillis()
            val replyTimestamp = if (currentNow <= parentTimestamp) parentTimestamp + 2000L else currentNow

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
                timestamp = replyTimestamp,
                isRead = true,
                hasAttachments = attachments.isNotEmpty(),
                attachments = attachments,
                threadId = threadId,
                deliveryStatus = app.jackdaw.client.core.model.DeliveryStatus.QUEUED
            )
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
        val account = currentAccount.value ?: return
        val folder = selectedFolder.value ?: return
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
            triggerSync()
        }
    }

    fun updateAccount(account: MailAccount) {
        viewModelScope.launch {
            repository.updateAccount(account)
            if (currentAccount.value?.id == account.id) {
                selectAccount(account)
            }
            triggerSync()
        }
    }


    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            val allAccounts = accounts.value
            val remaining = allAccounts.filter { it.id != accountId }
            if (currentAccount.value?.id == accountId) {
                if (remaining.isNotEmpty()) {
                    selectAccount(remaining.first())
                } else {
                    _manualSelectedAccountId.value = null
                }
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
