---
name: mvi-state-management
description: >-
  Use this skill when designing, refactoring, or reviewing ViewModels, state flow, side effects,
  and unidirectional data flow (MVI/UDF) in Jackdaw Mail Android client.
---

# Modern MVI / UDF Architecture Playbook

This skill outlines the standard patterns for state management, reactive streams, and lifecycle safety in Jackdaw Mail using Kotlin Coroutines and Flow.

---

## 1. Unidirectional Data Flow (UDF) Pattern

Each screen follows the strict loop:
```
[User Action / Intent] ──▶ [ViewModel Reducer] ──▶ [Repository / UseCase]
                                  │
                                  ▼
[Screen Recomposition] ◀── [StateFlow<UiState>]
```

---

## 2. Standard ViewModel Architecture

```kotlin
// 1. Immutable UI State
@Immutable
data class MailListUiState(
    val isLoading: Boolean = false,
    val emails: List<EmailItemUiModel> = emptyList(),
    val selectedFolder: String = "INBOX",
    val unreadCount: Int = 0,
    val errorMessage: String? = null
)

// 2. User Intents / Actions
sealed interface MailListIntent {
    data class SelectFolder(val folderId: String) : MailListIntent
    data class MarkAsRead(val emailId: String) : MailListIntent
    data class DeleteEmail(val emailId: String) : MailListIntent
    data object Refresh : MailListIntent
}

// 3. Single-Event Side Effects (Navigation, Toasts, Snackbars)
sealed interface MailListEffect {
    data class NavigateToDetail(val emailId: String) : MailListEffect
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : MailListEffect
}

// 4. ViewModel Implementation
class MailListViewModel(
    private val mailRepository: MailRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(MailListUiState())
    val uiState: StateFlow<MailListUiState> = _uiState.asStateFlow()

    private val _effect = Channel<MailListEffect>(Channel.BUFFERED)
    val effect: Flow<MailListEffect> = _effect.receiveAsFlow()

    fun processIntent(intent: MailListIntent) {
        when (intent) {
            is MailListIntent.SelectFolder -> selectFolder(intent.folderId)
            is MailListIntent.MarkAsRead -> markAsRead(intent.emailId)
            is MailListIntent.DeleteEmail -> deleteEmail(intent.emailId)
            is MailListIntent.Refresh -> refreshMailbox()
        }
    }

    private fun markAsRead(emailId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mailRepository.markAsRead(emailId)
        }
    }
}
```

---

## 3. Composable Consumption Pattern

Always collect state using `collectAsStateWithLifecycle()` to prevent background processing:

```kotlin
@Composable
fun MailListScreen(
    viewModel: MailListViewModel,
    onNavigateDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect single-event side effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MailListEffect.NavigateToDetail -> onNavigateDetail(effect.emailId)
                is MailListEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    MailListContent(
        state = state,
        onIntent = viewModel::processIntent
    )
}
```

---

## 4. Key Rules
- **No Direct State Mutations in UI:** Composable functions emit intents via callbacks, never mutating `StateFlow` directly.
- **Single Source of Truth:** Repository provides Room `Flow<T>`, ViewModel transforms it via `map` or `combine`, UI renders state.
- **Process Death Protection:** Preserve critical query filters or drafts in `SavedStateHandle`.
