package app.jackdaw.client.ui.screens.setup

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.model.AccountProtocol
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.ui.screens.settings.OwaWebLoginDialog

@Composable
fun SetupAccountScreen(
    onAccountAdded: (MailAccount) -> Unit
) {
    val context = LocalContext.current
    var selectedProtocol by remember { mutableStateOf(AccountProtocol.EXCHANGE_OWA) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var serverHost by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showOwaWebLoginDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Logo Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(JackdawAmber.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Email,
                    contentDescription = null,
                    tint = JackdawAmber,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Jackdaw Mail",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Подключите ваш почтовый ящик для безопасной работы с почтой без промежуточных серверов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Protocol Selector Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ВЫБЕРИТЕ ПРОТОКОЛ",
                        style = MaterialTheme.typography.labelSmall,
                        color = JackdawAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedProtocol = AccountProtocol.EXCHANGE_OWA },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedProtocol == AccountProtocol.EXCHANGE_OWA) JackdawAmber else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedProtocol == AccountProtocol.EXCHANGE_OWA) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("OWA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { selectedProtocol = AccountProtocol.EXCHANGE_EWS },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedProtocol == AccountProtocol.EXCHANGE_EWS) JackdawAmber else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedProtocol == AccountProtocol.EXCHANGE_EWS) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exchange", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { selectedProtocol = AccountProtocol.IMAP },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedProtocol == AccountProtocol.IMAP) JackdawAmber else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedProtocol == AccountProtocol.IMAP) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("IMAP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Credentials Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "УЧЕТНЫЕ ДАННЫЕ",
                        style = MaterialTheme.typography.labelSmall,
                        color = JackdawAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Display Name
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Ваше имя") },
                        placeholder = { Text("например: Иван Иванов") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JackdawAmber,
                            focusedLabelColor = JackdawAmber
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Email Address
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email адрес") },
                        placeholder = { Text("user@company.ru") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JackdawAmber,
                            focusedLabelColor = JackdawAmber
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Server Host / OWA URL
                    OutlinedTextField(
                        value = serverHost,
                        onValueChange = { serverHost = it },
                        label = {
                            Text(
                                if (selectedProtocol == AccountProtocol.EXCHANGE_OWA) "Адрес OWA сервера или URL"
                                else "Сервер (Exchange / IMAP)"
                            )
                        },
                        placeholder = {
                            Text(
                                if (selectedProtocol == AccountProtocol.EXCHANGE_OWA) "https://mail.company.ru/owa"
                                else "mail.company.ru"
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JackdawAmber,
                            focusedLabelColor = JackdawAmber
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JackdawAmber,
                            focusedLabelColor = JackdawAmber
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            if (selectedProtocol == AccountProtocol.EXCHANGE_OWA) {
                Button(
                    onClick = {
                        if (serverHost.isBlank() && email.isNotBlank() && email.contains("@")) {
                            serverHost = "https://${email.substringAfter("@")}/owa"
                        }
                        showOwaWebLoginDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JackdawAmber.copy(alpha = 0.18f),
                        contentColor = JackdawAmber
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Войти через веб-интерфейс OWA (SSO / MFA)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    if (email.isBlank()) {
                        Toast.makeText(context, "Введите email адрес", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val createdAccount = MailAccount(
                        id = "acc_${System.currentTimeMillis()}",
                        email = email.trim(),
                        displayName = displayName.ifBlank { email.substringBefore("@") },
                        protocol = selectedProtocol,
                        isDefault = true,
                        avatarColorHex = if (selectedProtocol == AccountProtocol.EXCHANGE_OWA) 0xFFF59E0BL else 0xFF10B981L,
                        serverHost = serverHost.trim()
                    )
                    onAccountAdded(createdAccount)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JackdawAmber,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Подключить почту", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Security reassurance badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Прямое соединение без посредников. Данные не покидают ваше устройство.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showOwaWebLoginDialog) {
        val targetUrl = serverHost.ifBlank {
            if (email.contains("@")) "https://${email.substringAfter("@")}/owa" else "https://mail.company.ru/owa"
        }
        OwaWebLoginDialog(
            initialOwaUrl = targetUrl,
            initialEmail = email,
            onDismissRequest = { showOwaWebLoginDialog = false },
            onAccountAuthorized = { owaAccount ->
                onAccountAdded(owaAccount)
                showOwaWebLoginDialog = false
            }
        )
    }
}
