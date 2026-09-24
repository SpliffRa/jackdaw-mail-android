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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
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

    private data class EmailQueryParams(
        val account: MailAccount?,
        val folder: Folder?,
        val query: String,
        val limit: Int
    )

    private val _currentLimit = MutableStateFlow(50)

    fun loadMoreEmails() {
        _currentLimit.value += 50
    }

    val emails: StateFlow<List<EmailMessage>> = combine(
        currentAccount,
        selectedFolder,
        _searchQuery,
        _currentLimit
    ) { account, folder, query, limit ->
        EmailQueryParams(account, folder, query, limit)
    }.flatMapLatest { (account, folder, query, limit) ->
        if (account == null || folder == null) {
            flowOf(emptyList())
        } else if (query.isNotBlank()) {
            repository.searchEmailsPaged(query, limit)
        } else {
            repository.getPagedEmailsInFolder(account.id, folder.id, limit)
        }
    }.flowOn(Dispatchers.IO)
    .stateIn(
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
            var firstSyncDone = false
            currentAccount.collect { acc ->
                if (acc != null && !firstSyncDone && !_isSyncing.value) {
                    firstSyncDone = true
                    _isSyncing.value = true
                    try {
                        repository.syncAll(acc.id)
                    } finally {
                        _isSyncing.value = false
                        _lastSyncTimestamp.value = System.currentTimeMillis()
                    }
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(180_000L) // 3 minutes periodic refresh
                currentAccount.value?.let { acc ->
                    if (!_isSyncing.value) {
                        _isSyncing.value = true
                        try {
                            repository.syncAll(acc.id)
                        } finally {
                            _isSyncing.value = false
                            _lastSyncTimestamp.value = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun selectFolder(folder: Folder) {
        _currentLimit.value = 50
        _selectedFolderId.value = folder.id
        _searchQuery.value = ""
    }

    fun selectAccount(account: MailAccount) {
        _currentLimit.value = 50
        viewModelScope.launch {
            _manualSelectedAccountId.value = account.id
            val accountFolders = repository.getFolders(account.id).first()
            if (accountFolders.isNotEmpty()) {
                _selectedFolderId.value = accountFolders.first().id
            }
        }
    }

    fun setSearchQuery(query: String) {
        _currentLimit.value = 50
        _searchQuery.value = query
    }

    fun triggerSync(onCompleted: ((String) -> Unit)? = null) {
        val account = currentAccount.value ?: return
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.syncAll(account.id)
                val msg = if (result.isSuccess) {
                    when {
                        result.sentMessagesCount > 0 ->
                            "Отправлено: ${app.jackdaw.client.core.util.PluralRules.formatEmailCount(result.sentMessagesCount)}"
                        else ->
                            null
                    }
                } else {
                    result.errorMessage ?: "Ошибка синхронизации"
                }
                _syncMessage.value = msg
                onCompleted?.invoke(msg ?: "")
            } finally {
                _isSyncing.value = false
                _lastSyncTimestamp.value = System.currentTimeMillis()
            }
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

    private val loadingEmailBodyIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun loadEmailBodyIfNeeded(email: EmailMessage, force: Boolean = false) {
        val hasPlaceholderAttachment = email.attachments.any { 
            it.fileName == "Вложение" || it.sizeBytes == 24500L || it.id.contains("_att_") 
        }
        val hasCidInHtml = email.bodyHtml?.contains("cid:", ignoreCase = true) == true
        val needsAttachmentDetails = email.hasAttachments && (email.attachments.isEmpty() || hasPlaceholderAttachment)

        val isFullBodyLoaded = if (force) {
            false
        } else if (!email.bodyHtml.isNullOrBlank()) {
            email.bodyHtml!!.length > 300 && !hasPlaceholderAttachment && !hasCidInHtml && !needsAttachmentDetails
        } else {
            email.bodyText.length > 500 && email.bodyText != email.snippet && !email.bodyText.endsWith("...") && !needsAttachmentDetails
        }

        if (!isFullBodyLoaded && !email.id.startsWith("mock_")) {
            if (force) {
                loadingEmailBodyIds.remove(email.id)
            }
            if (!loadingEmailBodyIds.add(email.id)) return
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val accList = repository.getAccounts().firstOrNull().orEmpty()
                    val account = currentAccount.value 
                        ?: accList.find { it.id == email.accountId }
                        ?: accList.find { it.isDefault }
                        ?: accList.firstOrNull() 
                        ?: return@launch
                    val success = repository.fetchEmailFullDetails(account, email.id)
                    if (!success) {
                        val pair = repository.fetchEmailBodyDirect(account, email.id)
                        if (pair != null && (pair.first.isNotBlank() || pair.second.isNotBlank())) {
                            val bodyText = pair.first.ifBlank { email.bodyText }
                            val bodyHtml = pair.second.ifBlank { email.bodyHtml }
                            val snippet = pair.first.take(150).ifBlank { email.snippet }
                            repository.updateEmailBody(email.id, bodyText, bodyHtml, snippet)
                        }
                    }
                } finally {
                    loadingEmailBodyIds.remove(email.id)
                }
            }
        }
    }

    fun downloadAttachment(attachment: Attachment, onReady: (java.io.File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = currentAccount.value ?: run {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onReady(null) }
                return@launch
            }
            val file = repository.downloadAttachment(account, attachment)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onReady(file)
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

    fun reorderFolders(orderedIds: List<String>) {
        viewModelScope.launch {
            repository.reorderFolders(orderedIds)
        }
    }

    fun toggleFolderMute(folderId: String) {
        viewModelScope.launch {
            repository.toggleFolderMute(folderId)
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
