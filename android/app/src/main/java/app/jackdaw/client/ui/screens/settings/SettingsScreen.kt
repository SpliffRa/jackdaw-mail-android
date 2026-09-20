package app.jackdaw.client.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import app.jackdaw.client.core.designsystem.theme.SlaUrgentRed
import app.jackdaw.client.core.designsystem.theme.ThemeMode
import app.jackdaw.client.core.model.AccountProtocol
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.notification.SoundNotificationManager
import app.jackdaw.client.core.readstatus.MarkAsReadMode
import app.jackdaw.client.core.readstatus.ReadStatusManager
import app.jackdaw.client.core.signature.SignatureManager
import app.jackdaw.client.core.update.AppUpdateInfo
import app.jackdaw.client.core.update.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

enum class SettingsSubfolder(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    THEME("Тема оформления", "Выбор тёмной, светлой или системной темы", Icons.Rounded.SettingsBrightness),
    ACCOUNTS("Учетные записи", "Управление подключенными почтовыми ящиками", Icons.Rounded.AccountCircle),
    SYNC_SLA("Синхронизация и SLA", "Фоновая проверка, Wi-Fi и контроль 30 минут", Icons.Rounded.Sync),
    NOTIFICATIONS_SOUND("Уведомления и звуки", "Тихий чпок, громкость и сигналы", Icons.Rounded.Notifications),
    SIGNATURE("Шаблон подписи", "Точный текст подписи для писем и ответов", Icons.Rounded.EditNote),
    READ_STATUS("Пометка прочитанных (Outlook)", "Правила смены статуса прочтения писем", Icons.Rounded.MarkEmailRead),
    ABOUT("О приложении и обновления", "Версия клиента, проверка и установка APK", Icons.Rounded.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    accounts: List<MailAccount>,
    currentAccountId: String,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onSelectAccount: (MailAccount) -> Unit,
    onAddAccount: (MailAccount) -> Unit,
    onDeleteAccount: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val soundManager = remember { SoundNotificationManager.getInstance(context) }
    val readStatusManager = remember { ReadStatusManager.getInstance(context) }
    val signatureManager = remember { SignatureManager.getInstance(context) }

    var activeSubfolder by remember { mutableStateOf<SettingsSubfolder?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<MailAccount?>(null) }

    // Settings switches
    var backgroundSyncEnabled by remember { mutableStateOf(true) }
    var wifiOnlyEnabled by remember { mutableStateOf(false) }
    var slaAlertsEnabled by remember { mutableStateOf(true) }

    // Sound settings
    var incomingSound by remember { mutableStateOf(soundManager.isIncomingSoundEnabled) }
    var sentSound by remember { mutableStateOf(soundManager.isSentSoundEnabled) }
    var slaSound by remember { mutableStateOf(soundManager.isSlaSoundEnabled) }
    var notificationsEnabled by remember { mutableStateOf(soundManager.isNotificationsEnabled) }
    var soundVolume by remember { mutableFloatStateOf(soundManager.soundVolume) }

    // Read status settings
    var currentMarkAsReadMode by remember { mutableStateOf(readStatusManager.mode) }
    var markAsReadDelay by remember { mutableIntStateOf(readStatusManager.delaySeconds) }

    // Signature settings
    val currentAccountObj = remember(accounts, currentAccountId) {
        accounts.find { it.id == currentAccountId } ?: accounts.firstOrNull() ?: MailAccount("default", "user@jackdaw.email", "Пользователь")
    }
    var isSignatureEnabled by remember { mutableStateOf(signatureManager.isSignatureEnabled) }
    var signatureText by remember(currentAccountObj.id) {
        mutableStateOf(signatureManager.getSignature(currentAccountObj))
    }

    // Update state
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    // Hardware/System back button navigates back to subfolders root
    BackHandler(enabled = activeSubfolder != null) {
        activeSubfolder = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = activeSubfolder?.title ?: "Настройки",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeSubfolder != null) {
                            activeSubfolder = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            if (activeSubfolder == null) {
                // ROOT SETTINGS SUBFOLDERS LIST
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsSubfolder.entries.forEachIndexed { index, subfolder ->
                            val subtitleText = when (subfolder) {
                                SettingsSubfolder.THEME -> "Текущая: ${currentThemeMode.title}"
                                SettingsSubfolder.ACCOUNTS -> "${accounts.size} ящик(ов) • Активен: ${currentAccountObj.displayName}"
                                SettingsSubfolder.SYNC_SLA -> "Контроль SLA 30 мин: ${if (slaAlertsEnabled) "Вкл" else "Выкл"}"
                                SettingsSubfolder.NOTIFICATIONS_SOUND -> "Громкость ${(soundVolume * 100).toInt()}% • Чпок: ${if (incomingSound) "Вкл" else "Выкл"}"
                                SettingsSubfolder.SIGNATURE -> if (isSignatureEnabled) "Включена" else "Отключена"
                                SettingsSubfolder.READ_STATUS -> currentMarkAsReadMode.title
                                SettingsSubfolder.ABOUT -> "Версия v${BuildConfig.VERSION_NAME}"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeSubfolder = subfolder }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(JackdawAmber.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = subfolder.icon,
                                        contentDescription = null,
                                        tint = JackdawAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subfolder.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (index < SettingsSubfolder.entries.size - 1) {
                                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 14.dp))
                            }
                        }
                    }
                }
            } else {
                // SUBFOLDER CONTENT
                when (activeSubfolder!!) {
                    SettingsSubfolder.THEME -> {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    val isSelected = mode == currentThemeMode
                                    val icon = when (mode) {
                                        ThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) JackdawAmber.copy(alpha = 0.22f) else Color.Transparent)
                                            .clickable { onThemeModeChange(mode) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = mode.title,
                                                tint = if (isSelected) JackdawAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = mode.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsSubfolder.ACCOUNTS -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ПОДКЛЮЧЕННЫЕ АККАУНТЫ",
                                style = MaterialTheme.typography.labelSmall,
                                color = JackdawAmber,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = { showAddAccountDialog = true },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = JackdawAmber)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Добавить", style = MaterialTheme.typography.labelSmall, color = JackdawAmber, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                accounts.forEachIndexed { index, account ->
                                    val isCurrent = account.id == currentAccountId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isCurrent) { onSelectAccount(account) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(account.avatarColorHex)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = account.displayName.firstOrNull()?.uppercase() ?: "A",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = account.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isCurrent) {
                                                    Spacer(modifier = Modifier.width(6.dp))
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
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "${account.email} • ${account.protocol.displayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (accounts.size > 1) {
                                            IconButton(
                                                onClick = { accountToDelete = account },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Удалить",
                                                    tint = SlaUrgentRed.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (index < accounts.size - 1) {
                                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 14.dp))
                                    }
                                }
                            }
                        }
                    }

                    SettingsSubfolder.SYNC_SLA -> {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Фоновая синхронизация", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Каждые 15 минут через WorkManager", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = backgroundSyncEnabled,
                                        onCheckedChange = { backgroundSyncEnabled = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                                    )
                                }

                                HorizontalDivider(color = dividerColor)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Только по Wi-Fi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Экономия мобильного интернета", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = wifiOnlyEnabled,
                                        onCheckedChange = { wifiOnlyEnabled = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                                    )
                                }

                                HorizontalDivider(color = dividerColor)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Контроль SLA (30 минут)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Дедлайн ответа и цветовые маркеры", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = slaAlertsEnabled,
                                        onCheckedChange = { slaAlertsEnabled = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                                    )
                                }
                            }
                        }
                    }

                    SettingsSubfolder.NOTIFICATIONS_SOUND -> {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Системные уведомления", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Push-уведомления в шторке Android", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = notificationsEnabled,
                                        onCheckedChange = {
                                            notificationsEnabled = it
                                            soundManager.isNotificationsEnabled = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                                    )
                                }

                                HorizontalDivider(color = dividerColor)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Входящее письмо (тихий чпок)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Мягкий сигнал и виброотклик", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { soundManager.playIncomingMailSound() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Прослушать", tint = JackdawAmber, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = incomingSound,
                                            onCheckedChange = {
                                                incomingSound = it
                                                soundManager.isIncomingSoundEnabled = it
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                                        )
                                    }
                                }

                                // Регулятор громкости звука
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Громкость звука уведомлений", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("${(soundVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = JackdawAmber)
                                    }
                                    Slider(
                                        value = soundVolume,
                                        onValueChange = {
                                            soundVolume = it
                                            soundManager.soundVolume = it
                                        },
                                        onValueChangeFinished = {
                                            soundManager.playPreviewSound(soundVolume)
                                        },
                                        valueRange = 0.05f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = JackdawAmber,
                                            activeTrackColor = JackdawAmber,
                                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                HorizontalDivider(color = dividerColor)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Звук отправки письма", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Тактильный отклик об отправке", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { soundManager.playSentMailSound() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Прослушать", tint = JackdawAmber, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = sentSound,
                                            onCheckedChange = {
                                                sentSound = it
                                                soundManager.isSentSoundEnabled = it
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                                        )
                                    }
                                }

                                HorizontalDivider(color = dividerColor)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Звуковые алерты SLA", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Оповещение о дедлайне 30 мин", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { soundManager.playSlaAlertSound() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Прослушать", tint = SlaUrgentRed, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = slaSound,
                                            onCheckedChange = {
                                                slaSound = it
                                                soundManager.isSlaSoundEnabled = it
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsSubfolder.SIGNATURE -> {
                        // EXACT SIGNATURE TEMPLATE (Clean & Direct)
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Добавлять подпись в письма", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Автоматически внизу сообщений и ответов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = isSignatureEnabled,
                                        onCheckedChange = {
                                            isSignatureEnabled = it
                                            signatureManager.isSignatureEnabled = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = JackdawAmber, checkedTrackColor = JackdawAmber.copy(alpha = 0.4f))
                                    )
                                }

                                if (isSignatureEnabled) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = dividerColor)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Точный шаблон подписи:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        TextButton(
                                            onClick = {
                                                val reset = signatureManager.resetToOutlookTemplate(currentAccountObj)
                                                signatureText = reset
                                                Toast.makeText(context, "Установлен стандартный шаблон ответа", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("По умолчанию", style = MaterialTheme.typography.labelSmall, color = JackdawAmber)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = signatureText,
                                        onValueChange = {
                                            signatureText = it
                                            signatureManager.setSignature(currentAccountObj, it)
                                        },
                                        placeholder = { Text("Текст подписи...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = JackdawAmber,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Теги:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        listOf("{name}", "{email}", "{app}").forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable {
                                                        signatureText = if (signatureText.isBlank()) tag else "$signatureText $tag"
                                                        signatureManager.setSignature(currentAccountObj, signatureText)
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(text = tag, style = MaterialTheme.typography.labelSmall, color = JackdawAmber, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Заданный шаблон подписи будет автоматически подгружаться внизу ваших сообщений без лишних цитат и заголовков.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    SettingsSubfolder.READ_STATUS -> {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                MarkAsReadMode.entries.forEachIndexed { index, mode ->
                                    val isSelected = currentMarkAsReadMode == mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentMarkAsReadMode = mode
                                                readStatusManager.mode = mode
                                                Toast.makeText(context, "Режим: ${mode.title}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                currentMarkAsReadMode = mode
                                                readStatusManager.mode = mode
                                                Toast.makeText(context, "Режим: ${mode.title}", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = JackdawAmber,
                                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = mode.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (mode == MarkAsReadMode.AFTER_DELAY && isSelected) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 34.dp, end = 8.dp, bottom = 10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Задержка перед пометкой:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$markAsReadDelay сек", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = JackdawAmber)
                                            }
                                            Slider(
                                                value = markAsReadDelay.toFloat(),
                                                onValueChange = {
                                                    markAsReadDelay = it.toInt()
                                                    readStatusManager.delaySeconds = it.toInt()
                                                },
                                                valueRange = 1f..15f,
                                                steps = 13,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = JackdawAmber,
                                                    activeTrackColor = JackdawAmber,
                                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    if (index < MarkAsReadMode.entries.size - 1) {
                                        HorizontalDivider(color = dividerColor)
                                    }
                                }
                            }
                        }
                    }

                    SettingsSubfolder.ABOUT -> {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Jackdaw Mail v${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Сборка: ${BuildConfig.VERSION_CODE} • GitHub Release",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isCheckingUpdates = true
                                                updateStatusMessage = "Проверка обновлений..."
                                                val result = updateManager.checkForUpdates()
                                                isCheckingUpdates = false
                                                result.onSuccess { info ->
                                                    updateInfo = info
                                                    if (info.isUpdateAvailable) {
                                                        updateStatusMessage = "Доступна новая версия: ${info.versionName}"
                                                    } else {
                                                        updateStatusMessage = "Установлена актуальная версия"
                                                    }
                                                }.onFailure { err ->
                                                    updateStatusMessage = "Ошибка: ${err.message ?: "Сбой соединения"}"
                                                }
                                            }
                                        },
                                        enabled = !isCheckingUpdates && !isDownloadingUpdate,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = JackdawAmber
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        if (isCheckingUpdates) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = JackdawAmber, strokeWidth = 2.dp)
                                        } else {
                                            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Проверить", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                if (updateStatusMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = updateStatusMessage!!, style = MaterialTheme.typography.labelSmall, color = JackdawAmber)
                                }

                                if (isDownloadingUpdate) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Загрузка: ${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth(), color = JackdawAmber)
                                }

                                if (downloadedApkFile != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { updateManager.installApk(downloadedApkFile!!) },
                                        colors = ButtonDefaults.buttonColors(containerColor = JackdawAmber, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text("Установить обновление", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                } else if (updateInfo?.isUpdateAvailable == true) {
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text("Скачать v${updateInfo!!.versionName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Add Account Dialog
    var showOwaWebLoginDialog by remember { mutableStateOf(false) }
    var newDisplayName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newProtocol by remember { mutableStateOf(AccountProtocol.EXCHANGE_OWA) }
    var newServerHost by remember { mutableStateOf("mail.corp.com") }

    if (showAddAccountDialog) {
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
                        label = {
                            Text(
                                if (newProtocol == AccountProtocol.EXCHANGE_OWA) "OWA сервер или URL"
                                else "Сервер (Exchange / IMAP)"
                            )
                        },
                        placeholder = {
                            if (newProtocol == AccountProtocol.EXCHANGE_OWA) {
                                Text("https://mail.corp.com/owa", fontSize = 12.sp)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Протокол подключения:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { newProtocol = AccountProtocol.EXCHANGE_OWA },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (newProtocol == AccountProtocol.EXCHANGE_OWA) JackdawAmber else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (newProtocol == AccountProtocol.EXCHANGE_OWA) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("OWA", fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = { newProtocol = AccountProtocol.EXCHANGE_EWS },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (newProtocol == AccountProtocol.EXCHANGE_EWS) JackdawAmber else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (newProtocol == AccountProtocol.EXCHANGE_EWS) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exchange", fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = { newProtocol = AccountProtocol.IMAP },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (newProtocol == AccountProtocol.IMAP) JackdawAmber else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (newProtocol == AccountProtocol.IMAP) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("IMAP", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    if (newProtocol == AccountProtocol.EXCHANGE_OWA) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showOwaWebLoginDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JackdawAmber.copy(alpha = 0.18f),
                                contentColor = JackdawAmber
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = JackdawAmber
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Войти через веб-интерфейс OWA (SSO / MFA)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Поддерживает форму входа Exchange, ADFS, SAML и двухфакторную проверку (2FA).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
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
                                avatarColorHex = if (newProtocol == AccountProtocol.EXCHANGE_OWA) 0xFFF59E0BL else 0xFF10B981L,
                                serverHost = newServerHost.trim()
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
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showOwaWebLoginDialog) {
        OwaWebLoginDialog(
            initialOwaUrl = newServerHost.ifBlank { "mail.corp.com" },
            initialEmail = newEmail,
            onDismissRequest = { showOwaWebLoginDialog = false },
            onAccountAuthorized = { owaAccount ->
                onAddAccount(owaAccount)
                Toast.makeText(context, "OWA аккаунт ${owaAccount.email} подключен", Toast.LENGTH_SHORT).show()
                showOwaWebLoginDialog = false
                showAddAccountDialog = false
            }
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
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
