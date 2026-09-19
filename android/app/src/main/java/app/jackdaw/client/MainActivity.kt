package app.jackdaw.client

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.jackdaw.client.core.designsystem.theme.JackdawTheme
import app.jackdaw.client.data.local.JackdawDatabase
import app.jackdaw.client.data.repository.OfflineFirstMailRepository
import app.jackdaw.client.ui.components.FolderDrawer
import app.jackdaw.client.ui.navigation.Screen
import app.jackdaw.client.ui.screens.compose.ComposeScreen
import app.jackdaw.client.ui.screens.maildetail.MailDetailScreen
import app.jackdaw.client.ui.screens.maillist.MailListScreen
import app.jackdaw.client.ui.screens.settings.SettingsScreen
import app.jackdaw.client.ui.viewmodel.MailViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import app.jackdaw.client.core.designsystem.theme.ThemeMode
import app.jackdaw.client.core.designsystem.theme.ThemePreferencesManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val database = JackdawDatabase.getInstance(applicationContext)
        val repository = OfflineFirstMailRepository(database)

        setContent {
            val themeManager = remember { ThemePreferencesManager.getInstance(applicationContext) }
            val currentThemeMode by themeManager.themeMode.collectAsState()
            val isDarkTheme = when (currentThemeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            JackdawTheme(darkTheme = isDarkTheme) {
                val mailViewModel: MailViewModel = viewModel(
                    factory = MailViewModel.Factory(repository)
                )

                JackdawMainApp(
                    viewModel = mailViewModel,
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = { mode -> themeManager.setThemeMode(mode) },
                    onToast = { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun JackdawMainApp(
    viewModel: MailViewModel,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToast: (String) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val accounts by viewModel.accounts.collectAsState()
    val currentAccount by viewModel.currentAccount.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val emails by viewModel.emails.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FolderDrawer(
                currentAccount = currentAccount,
                accounts = accounts,
                onSelectAccount = { account ->
                    viewModel.selectAccount(account)
                    scope.launch { drawerState.close() }
                },
                folders = folders,
                selectedFolderId = selectedFolder.id,
                onSelectFolder = { folder ->
                    viewModel.selectFolder(folder)
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.MailList.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.MailList.route) {
                MailListScreen(
                    currentAccount = currentAccount,
                    currentFolder = selectedFolder,
                    emails = emails,
                    searchQuery = searchQuery,
                    isSyncing = isSyncing,
                    snackbarHostState = snackbarHostState,
                    onSyncClick = {
                        viewModel.triggerSync { resultMsg ->
                            onToast(resultMsg)
                        }
                    },
                    onSwipeArchive = { emailToArchive ->
                        val originalFolder = emailToArchive.folderId
                        val isInArchive = originalFolder.contains("archive", ignoreCase = true)
                        if (isInArchive) {
                            viewModel.unarchiveEmail(emailToArchive.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Письмо возвращено во входящие",
                                    actionLabel = "Отменить",
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    viewModel.moveToArchive(emailToArchive.id)
                                }
                            }
                        } else {
                            viewModel.moveToArchive(emailToArchive.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Письмо архивировано",
                                    actionLabel = "Отменить",
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    viewModel.restoreEmail(emailToArchive.id, originalFolder)
                                }
                            }
                        }
                    },
                    onSwipeDelete = { emailToDelete ->
                        val originalFolder = emailToDelete.folderId
                        val isInTrash = originalFolder.contains("trash", ignoreCase = true)
                        if (isInTrash) {
                            viewModel.permanentlyDeleteEmail(emailToDelete.id)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Письмо удалено навсегда",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                            }
                        } else {
                            viewModel.moveToTrash(emailToDelete.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Письмо перемещено в корзину",
                                    actionLabel = "Отменить",
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    viewModel.restoreEmail(emailToDelete.id, originalFolder)
                                }
                            }
                        }
                    },
                    onEmptyTrash = {
                        viewModel.emptyTrash {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Корзина очищена",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onEmailClick = { clickedEmail ->
                        viewModel.markAsRead(clickedEmail.id, true)
                        navController.navigate(Screen.MailDetail.createRoute(clickedEmail.id))
                    },
                    onComposeClick = {
                        navController.navigate(Screen.Compose.createRoute())
                    },
                    onToggleStar = { emailId, isStarred ->
                        viewModel.toggleStar(emailId, isStarred)
                    }
                )
            }

            composable(
                route = Screen.MailDetail.route,
                arguments = listOf(navArgument("emailId") { type = NavType.StringType })
            ) { backStackEntry ->
                val emailId = backStackEntry.arguments?.getString("emailId") ?: ""
                val initialEmail = remember(emailId) { emails.find { it.id == emailId } }
                val detailEmail by viewModel.getEmailById(emailId).collectAsState(initial = initialEmail)
                val currentEmail = detailEmail ?: initialEmail

                val threadEmails by if (currentEmail?.threadId != null) {
                    viewModel.getEmailsInThread(currentEmail.threadId!!).collectAsState(initial = emptyList())
                } else {
                    androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList<app.jackdaw.client.core.model.EmailMessage>()) }
                }

                if (currentEmail != null) {
                    MailDetailScreen(
                        email = currentEmail,
                        threadEmails = threadEmails,
                        onBack = { navController.popBackStack() },
                        onReply = { replyToEmail ->
                            navController.navigate(Screen.Compose.createRoute(replyToEmail.id))
                        },
                        onDelete = { toDelete ->
                            val isInTrash = toDelete.folderId.contains("trash", ignoreCase = true)
                            if (isInTrash) {
                                viewModel.permanentlyDeleteEmail(toDelete.id)
                                onToast("Письмо удалено навсегда")
                            } else {
                                viewModel.moveToTrash(toDelete.id)
                                onToast("Письмо перемещено в корзину")
                            }
                            navController.popBackStack()
                        },
                        onArchive = { toArchive ->
                            viewModel.moveToArchive(toArchive.id)
                            onToast("Письмо перемещено в архив")
                            navController.popBackStack()
                        },
                        onToggleStar = { isStarred ->
                            viewModel.toggleStar(currentEmail.id, isStarred)
                        },
                        onToggleRead = { isRead ->
                            viewModel.markAsRead(currentEmail.id, isRead)
                        }
                    )
                } else {
                    navController.popBackStack()
                }
            }

            composable(
                route = Screen.Compose.route,
                arguments = listOf(navArgument("replyToId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val replyToId = backStackEntry.arguments?.getString("replyToId")
                val replyToEmail = emails.find { it.id == replyToId }

                ComposeScreen(
                    currentAccount = currentAccount,
                    initialTo = replyToEmail?.senderEmail ?: "",
                    initialSubject = replyToEmail?.let {
                        if (it.subject.startsWith("Re:", ignoreCase = true)) it.subject else "Re: ${it.subject}"
                    } ?: "",
                    onClose = { navController.popBackStack() },
                    onSend = { to, subject, body, attachments ->
                        viewModel.sendEmail(to, subject, body, attachments, replyToId)
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    accounts = accounts,
                    currentAccountId = currentAccount.id,
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    onBack = { navController.popBackStack() },
                    onSelectAccount = { account ->
                        viewModel.selectAccount(account)
                    },
                    onAddAccount = { newAccount ->
                        viewModel.addAccount(newAccount)
                        onToast("Аккаунт ${newAccount.displayName} добавлен")
                    },
                    onDeleteAccount = { accountId ->
                        viewModel.deleteAccount(accountId)
                        onToast("Аккаунт удален")
                    }
                )
            }
        }
    }
}
