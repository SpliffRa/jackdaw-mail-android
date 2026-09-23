package app.jackdaw.client.ui.screens.compose

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jackdaw.client.core.designsystem.theme.JackdawAmber
import app.jackdaw.client.core.model.Attachment
import app.jackdaw.client.core.model.MailAccount
import app.jackdaw.client.core.signature.SignatureManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    currentAccount: MailAccount,
    initialTo: String = "",
    initialSubject: String = "",
    initialBody: String = "",
    onClose: () -> Unit,
    onSend: (to: String, subject: String, body: String, attachments: List<Attachment>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val signatureManager = remember { SignatureManager.getInstance(context) }
    var toText by remember(initialTo) { mutableStateOf(initialTo) }
    var subjectText by remember(initialSubject) { mutableStateOf(initialSubject) }
    var bodyText by remember(initialBody) { mutableStateOf(initialBody) }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }

    LaunchedEffect(initialTo) {
        if (toText.isBlank() && initialTo.isNotBlank()) toText = initialTo
    }
    LaunchedEffect(initialSubject) {
        if (subjectText.isBlank() && initialSubject.isNotBlank()) subjectText = initialSubject
    }
    LaunchedEffect(initialBody) {
        val emptySig = signatureManager.buildNewEmailBody(currentAccount)
        if ((bodyText.isBlank() || bodyText == emptySig) && initialBody.isNotBlank()) {
            bodyText = initialBody
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val newAttachments = uris.map { uri ->
            var fileName = "attachment_${System.currentTimeMillis()}"
            var fileSize = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }
            } catch (e: Exception) {
                // Keep default names if failed to resolve
            }
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            var savedLocalUri = uri.toString()
            try {
                val attachmentsDir = java.io.File(context.filesDir, "attachments").apply { mkdirs() }
                val safeFileName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val destFile = java.io.File(attachmentsDir, "${System.currentTimeMillis()}_$safeFileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    savedLocalUri = destFile.absolutePath
                    if (fileSize == 0L) fileSize = destFile.length()
                }
            } catch (e: Exception) {
                // fallback to original uri
            }

            Attachment(
                id = "att_${System.currentTimeMillis()}_${(100..999).random()}",
                fileName = fileName,
                sizeBytes = fileSize,
                mimeType = mimeType,
                localUri = savedLocalUri
            )
        }
        attachments = attachments + newAttachments
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initialSubject.isNotBlank()) "Ответ на письмо" else "Новое письмо",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Отмена",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Attach File Button
                    IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "Прикрепить файл",
                            tint = if (attachments.isNotEmpty()) JackdawAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Send Button
                    Button(
                        onClick = { onSend(toText, subjectText, bodyText, attachments) },
                        enabled = toText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JackdawAmber,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Отправить", fontWeight = FontWeight.Bold)
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
                .padding(horizontal = 16.dp)
        ) {
            // Sender info line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "От:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(currentAccount.avatarColorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentAccount.displayName.firstOrNull()?.uppercase() ?: "J",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${currentAccount.displayName} <${currentAccount.email}>",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Recipient field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Кому:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp)
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    placeholder = { Text("email получателя...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Subject field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Тема:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp)
                )
                OutlinedTextField(
                    value = subjectText,
                    onValueChange = { subjectText = it },
                    placeholder = { Text("Тема сообщения...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))


            // Attachments list (if any selected)
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attachments.forEach { att ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = JackdawAmber
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = att.fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Удалить вложение",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { attachments = attachments.filter { it.id != att.id } }
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }

            // Body field
            OutlinedTextField(
                value = bodyText,
                onValueChange = { bodyText = it },
                placeholder = { Text("Текст сообщения...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            )
        }
    }
}
