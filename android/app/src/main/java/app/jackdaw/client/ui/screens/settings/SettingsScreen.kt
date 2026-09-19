package app.jackdaw.client.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jackdaw.client.BuildConfig
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.designsystem.theme.JackdawBackgroundDark
import app.jackdaw.client.core.designsystem.theme.JackdawBorderDark
import app.jackdaw.client.core.designsystem.theme.JackdawSurfaceDark
import app.jackdaw.client.core.designsystem.theme.JackdawSurfaceElevatedDark
import app.jackdaw.client.core.designsystem.theme.SlaUrgentRed
import app.jackdaw.client.core.model.AccountProtocol
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.update.AppUpdateInfo
import app.jackdaw.client.core.update.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    accounts: List<MailAccount>,
    currentAccountId: String,
    onBack: () -> Unit,
    onSelectAccount: (MailAccount) -> Unit = {},
    onAddAccount: (MailAccount) -> Unit,
    onDeleteAccount: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<MailAccount?>(null) }

    // Settings switches
    var backgroundSyncEnabled by remember { mutableStateOf(true) }
    var wifiOnlyEnabled by remember { mutableStateOf(false) }
    var slaAlertsEnabled by remember { mutableStateOf(true) }

    // Update state
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = "Настройки и аккаунты",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JackdawBackgroundDark
                )
            )
        },
        containerColor = JackdawBackgroundDark,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // SECTION 1: ACCOUNTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "УЧЕТНЫЕ ЗАПИСИ",
                    style = MaterialTheme.typography.labelMedium,
                    color = JackdawAmber,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAddAccountDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JackdawSurfaceElevatedDark,
                        contentColor = JackdawAmber
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Account cards
            accounts.forEach { account ->
                val isCurrent = account.id == currentAccountId
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = JackdawSurfaceDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = !isCurrent) { onSelectAccount(account) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(account.avatarColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = account.displayName.firstOrNull()?.uppercase() ?: "A",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = account.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(JackdawAmber.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Активен",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JackdawAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = account.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(JackdawSurfaceElevatedDark)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = account.protocol.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (accounts.size > 1) {
                            IconButton(onClick = { accountToDelete = account }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Удалить аккаунт",
                                    tint = SlaUrgentRed.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 2: SYNCHRONIZATION & SLA
            Text(
                text = "СИНХРОНИЗАЦИЯ И SLA",
                style = MaterialTheme.typography.labelMedium,
                color = JackdawAmber,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = JackdawSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Фоновая синхронизация", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Периодическая проверка WorkManager (каждые 15 мин)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = backgroundSyncEnabled,
                            onCheckedChange = { backgroundSyncEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                        )
                    }

                    HorizontalDivider(color = JackdawBorderDark, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Только по Wi-Fi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Экономия мобильного интернет-трафика", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = wifiOnlyEnabled,
                            onCheckedChange = { wifiOnlyEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                        )
                    }

                    HorizontalDivider(color = JackdawBorderDark, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Контроль срока ответа (SLA)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Автоматический подсчет дедлайнов и цветовые метки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = slaAlertsEnabled,
                            onCheckedChange = { slaAlertsEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 3: OTA UPDATES
            Text(
                text = "ОБНОВЛЕНИЯ ПРИЛОЖЕНИЯ (OTA)",
                style = MaterialTheme.typography.labelMedium,
                color = JackdawAmber,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = JackdawSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Rounded.SystemUpdate, contentDescription = null, tint = JackdawAmber, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Jackdaw Mail v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Сборка: ${BuildConfig.VERSION_CODE} • SemVer Release", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Release highlights summary
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(JackdawSurfaceElevatedDark)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Что нового в v${BuildConfig.VERSION_NAME}:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = JackdawAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Мониторинг SLA: строгий 30-мин дедлайн ответа", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• В корзине: кнопка полной очистки с диалогом", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Исправлены открытие и передача вложений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (updateStatusMessage != null) {
                        Text(text = updateStatusMessage!!, style = MaterialTheme.typography.bodySmall, color = JackdawAmber)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (isDownloadingUpdate) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Загрузка обновления: ${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth(), color = JackdawAmber)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdates = true
                                    updateStatusMessage = "Проверка обновлений на GitHub..."
                                    val result = updateManager.checkForUpdates()
                                    isCheckingUpdates = false
                                    result.onSuccess { info ->
                                        updateInfo = info
                                        if (info.isUpdateAvailable) {
                                            updateStatusMessage = "Доступна новая версия: ${info.versionName}"
                                        } else {
                                            updateStatusMessage = info.releaseNotes.ifBlank { "Установлена актуальная версия (v${BuildConfig.VERSION_NAME})" }
                                        }
                                    }.onFailure { err ->
                                        updateStatusMessage = "Ошибка проверки: ${err.message ?: "Сбой соединения"}"
                                    }
                                }
                            },
                            enabled = !isCheckingUpdates && !isDownloadingUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = JackdawSurfaceElevatedDark, contentColor = JackdawAmber),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = JackdawAmber, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Проверка...")
                            } else {
                                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Проверить")
                            }
                        }

                        if (downloadedApkFile != null) {
                            Button(
                                onClick = {
                                    updateManager.installApk(downloadedApkFile!!)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JackdawAmber, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Установить", fontWeight = FontWeight.Bold)
                            }
                        } else if (updateInfo?.isUpdateAvailable == true) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isDownloadingUpdate = true
                                        val dlResult = updateManager.downloadUpdate(updateInfo!!.downloadUrl) { progress ->
                                            downloadProgress = progress
                                        }
                                        isDownloadingUpdate = false
                                        dlResult.onSuccess { file ->
                                            downloadedApkFile = file
                                            updateStatusMessage = "Пакет готов к установке"
                                            updateManager.installApk(file)
                                        }.onFailure {
                                            updateStatusMessage = "Ошибка загрузки файла"
                                        }
                                    }
                                },
                                enabled = !isDownloadingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = JackdawAmber, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Скачать v${updateInfo!!.versionName}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        var newDisplayName by remember { mutableStateOf("") }
        var newEmail by remember { mutableStateOf("") }
        var newProtocol by remember { mutableStateOf(AccountProtocol.EXCHANGE_EWS) }
        var newServerHost by remember { mutableStateOf("mail.corp.com") }

        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Добавить учетную запись", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { newDisplayName = it },
                        label = { Text("Имя пользователя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newServerHost,
                        onValueChange = { newServerHost = it },
                        label = { Text("Сервер (Exchange / IMAP)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Протокол подключения:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { newProtocol = AccountProtocol.EXCHANGE_EWS },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (newProtocol == AccountProtocol.EXCHANGE_EWS) JackdawAmber else JackdawSurfaceElevatedDark,
                                contentColor = if (newProtocol == AccountProtocol.EXCHANGE_EWS) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exchange", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { newProtocol = AccountProtocol.IMAP },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (newProtocol == AccountProtocol.IMAP) JackdawAmber else JackdawSurfaceElevatedDark,
                                contentColor = if (newProtocol == AccountProtocol.IMAP) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("IMAP", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newEmail.isNotBlank()) {
                            val createdAccount = MailAccount(
                                id = "acc_${System.currentTimeMillis()}",
                                email = newEmail.trim(),
                                displayName = newDisplayName.ifBlank { newEmail.substringBefore("@") },
                                protocol = newProtocol,
                                isDefault = false,
                                avatarColorHex = 0xFF10B981L
                            )
                            onAddAccount(createdAccount)
                            Toast.makeText(context, "Аккаунт ${createdAccount.email} добавлен", Toast.LENGTH_SHORT).show()
                            showAddAccountDialog = false
                        }
                    },
                    enabled = newEmail.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = JackdawAmber, contentColor = Color.Black)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = JackdawSurfaceDark
        )
    }

    // Delete confirmation dialog
    if (accountToDelete != null) {
        val target = accountToDelete!!
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Удалить аккаунт?", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    text = "Вы действительно хотите удалить учетную запись «${target.displayName}» (${target.email})? Все связанные локальные письма и папки будут удалены.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount(target.id)
                        Toast.makeText(context, "Аккаунт ${target.email} удален", Toast.LENGTH_SHORT).show()
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlaUrgentRed, contentColor = Color.White)
                ) {
                    Text("Удалить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = JackdawSurfaceDark
        )
    }
}
