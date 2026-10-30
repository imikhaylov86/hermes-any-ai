package com.hermes.mobile.presentation.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.hermes.mobile.R
import com.hermes.mobile.domain.model.Attachment
import com.hermes.mobile.domain.model.ConnectionState
import com.hermes.mobile.domain.model.MessageStatus
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.graphics.SolidColor
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.provider.ProviderBottomSheet
import com.hermes.mobile.presentation.session.RenameSessionDialog
import com.hermes.mobile.presentation.theme.*
import com.hermes.mobile.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val activeProviderName by viewModel.activeProviderName.collectAsState()
    val fallbackModel by viewModel.fallbackModel.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    val pendingApprovals by viewModel.pendingApprovals.collectAsState()
    val pendingClarifies by viewModel.pendingClarifies.collectAsState()

    val inputText by viewModel.inputText.collectAsState()
    val attachedFiles by viewModel.attachedFiles.collectAsState()

    val providers by viewModel.providers.collectAsState()
    val isProvidersLoading by viewModel.isProvidersLoading.collectAsState()
    val providersError by viewModel.providersError.collectAsState()

    val slashCommands by viewModel.slashCommands.collectAsState()
    var showSlashManagerDialog by remember { mutableStateOf(false) }

    val activeSession by viewModel.activeSession.collectAsState()
    var showRenameSessionDialog by remember { mutableStateOf(false) }
    val updateState by viewModel.updateState.collectAsState()

    val listState = rememberLazyListState()

    var showProviderSheet by remember { mutableStateOf(false) }
    val externalShowProviderSheet by viewModel.showProviderSheet.collectAsState()
    LaunchedEffect(externalShowProviderSheet) {
        if (externalShowProviderSheet) {
            showProviderSheet = true
            viewModel.showProviderSheet.value = false
        }
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Автопрокрутка вниз при новых сообщениях
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    // Лаунчер для выбора фото/файлов из Галереи или файлового менеджера
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    var fileName = "attachment_${System.currentTimeMillis()}"
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            val dn = cursor.getString(nameIndex)
                            if (!dn.isNullOrBlank()) fileName = dn
                        }
                    }
                    if (!fileName.contains(".") && mimeType.contains("/")) {
                        val ext = mimeType.substringAfter("/")
                        fileName = "$fileName.$ext"
                    }

                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val dataUrl = "data:$mimeType;base64,$base64"
                        val attachment = Attachment(
                            name = fileName,
                            mimeType = mimeType,
                            uriOrPath = uri.toString(),
                            sizeBytes = bytes.size.toLong(),
                            dataUrl = dataUrl
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            viewModel.addAttachment(attachment)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "Error reading attachment URI: $uri", e)
                }
            }
        }
    }

    val isStreaming = messages.any { it.status == MessageStatus.STREAMING }

    // Кнопка вложений: сперва runtime-разрешение на медиа (Android 13+), затем пикер.
    // Без permission DocumentsUI не показывает локальные коллекции («Изображения» пусты).
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.all { it }
        filePickerLauncher.launch("*/*")
    }

    fun openAttachmentPicker() {
        val sdk = android.os.Build.VERSION.SDK_INT
        val needed = if (sdk >= 33) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val toRequest = needed.filter {
            context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isEmpty()) {
            filePickerLauncher.launch("*/*")
        } else {
            mediaPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val sessionTitle = activeSession?.title?.ifBlank { strings.appName } ?: strings.appName
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppDimensions.ShapeField)
                            .clickable { showRenameSessionDialog = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = sessionTitle,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = strings.renameChat,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val (statusColor, statusText) = if (isDemoMode) {
                                AccentEmerald to strings.statusDemo
                            } else {
                                when (connectionState) {
                                    ConnectionState.READY -> AccentEmerald to strings.statusReadyTermux
                                    ConnectionState.CONNECTING -> AccentAmber to strings.statusConnecting
                                    ConnectionState.AUTHENTICATING -> AccentAmber to strings.statusAuthenticating
                                    ConnectionState.RECONNECTING -> AccentAmber to strings.statusReconnecting
                                    ConnectionState.FAILED -> AccentRose to strings.statusFailed
                                    ConnectionState.DISCONNECTED -> Color.Gray to strings.statusDisconnected
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = strings.sessionsTitle
                        )
                    }
                },
                actions = {
                    // Кнопка экспорта/поделиться текущей беседой в Markdown
                    IconButton(
                        onClick = { viewModel.exportCurrentSessionMarkdown(context) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = strings.shareChatMarkdown,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // Кнопка выбора модели и провайдера (компактный бейдж)
                    Surface(
                        shape = AppDimensions.ShapeChip,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(AppDimensions.ShapeChip)
                            .clickable { showProviderSheet = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.widthIn(max = 145.dp)
                            ) {
                                Text(
                                    text = activeProviderName.ifBlank { "Hermes" }.uppercase(),
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryIndigo,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activeModel.ifBlank { strings.configureBtn },
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = strings.providersTitle,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Всплывающая палитра слэш-команд при наборе /
                AnimatedVisibility(
                    visible = inputText.startsWith("/"),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SlashCommandPalette(
                        filterText = inputText,
                        commands = slashCommands,
                        onCommandSelected = { cmd ->
                            viewModel.executeSlashCommand(cmd)
                        },
                        onOpenManager = {
                            showSlashManagerDialog = true
                        }
                    )
                }

                // Полоса превью вложений перед отправкой
                if (attachedFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachedFiles.forEach { file ->
                            Surface(
                                shape = AppDimensions.ShapeBadge,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "📎 ${file.name.take(15)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.removeAttachment(file) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = strings.delete,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Строка ввода сообщения: сбалансированная, с симметричными отступами и единой стартовой высотой 44.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Кнопка вложений
                    IconButton(
                        onClick = { openAttachmentPicker() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(AppDimensions.ShapeButton)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = strings.attachmentFile,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Кнопка вызова меню слэш-команд ("/")
                    IconButton(
                        onClick = {
                            if (inputText.startsWith("/")) {
                                viewModel.inputText.value = ""
                            } else {
                                viewModel.inputText.value = "/"
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(AppDimensions.ShapeButton)
                            .background(
                                if (inputText.startsWith("/")) PrimaryIndigo.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Text(
                            text = "/",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = if (inputText.startsWith("/")) PrimaryIndigo else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Поле ввода текста с выровненной начальной высотой (44.dp)
                    BasicTextField(
                        value = inputText,
                        onValueChange = { viewModel.inputText.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        maxLines = 4,
                        cursorBrush = SolidColor(PrimaryIndigo),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clip(AppDimensions.ShapeField)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = strings.inputPlaceholder,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (isStreaming) {
                        IconButton(
                            onClick = { viewModel.stopGeneration() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(AppDimensions.ShapeButton)
                                .background(AccentRose)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = strings.stopGenerating,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        val hasContent = inputText.isNotBlank() || attachedFiles.isNotEmpty()
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = hasContent,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(AppDimensions.ShapeButton)
                                .background(
                                    if (hasContent) PrimaryIndigo else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = strings.send,
                                tint = if (hasContent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Информационный баннер о наличии новой версии приложения
            AnimatedVisibility(
                visible = updateState is com.hermes.mobile.domain.update.UpdateState.UpdateAvailable
            ) {
                if (updateState is com.hermes.mobile.domain.update.UpdateState.UpdateAvailable) {
                    val info = (updateState as com.hermes.mobile.domain.update.UpdateState.UpdateAvailable).info
                    Surface(
                        color = PrimaryIndigo.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.checkForUpdates(isManual = true) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Доступно обновление ${info.tagName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryIndigo
                                )
                            }
                            Text(
                                text = strings.downloadAndUpdateBtn,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo
                            )
                        }
                    }
                }
            }

            if (messages.isEmpty() && pendingApprovals.isEmpty() && pendingClarifies.isEmpty()) {
                EmptyChatWelcome(
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
                ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onRetry = { msgId -> viewModel.retryMessage(msgId) }
                    )
                }

                items(pendingApprovals, key = { it.requestId }) { request ->
                    ToolApprovalCard(
                        request = request,
                        onDecision = { decision ->
                            viewModel.respondToApproval(request.requestId, decision)
                        }
                    )
                }

                items(pendingClarifies, key = { it.promptId }) { prompt ->
                    ClarifyCard(
                        prompt = prompt,
                        onAnswerSelected = { answer ->
                            viewModel.respondToClarify(prompt.promptId, answer)
                        }
                    )
                }
            }
        }
    }
}

    if (showProviderSheet) {
        ProviderBottomSheet(
            providers = providers,
            isLoading = isProvidersLoading,
            errorMessage = providersError,
            currentModel = activeModel,
            currentProviderId = activeProvider,
            fallbackModel = fallbackModel,
            onSetFallbackModel = { providerId, model ->
                viewModel.setFallbackModel(providerId, model)
            },
            onClearFallbackModel = {
                viewModel.clearFallbackModel()
            },
            onSelectModel = { providerId, model ->
                showProviderSheet = false
                viewModel.setActiveModel(model, providerId) { success, errorMsg ->
                    if (!success && errorMsg != null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(errorMsg)
                        }
                    }
                }
            },
            onSaveApiKey = { providerId, key ->
                viewModel.saveApiKey(providerId, key) { success, errorMsg ->
                    if (!success && errorMsg != null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(errorMsg)
                        }
                    }
                }
            },
            onGetApiKey = { providerId ->
                viewModel.getApiKey(providerId)
            },
            onRefresh = {
                viewModel.refreshProviders()
            },
            onTestConnection = { providerId, callback ->
                viewModel.testProviderConnection(providerId, callback)
            },
            onAddCustomProvider = { name, apiMode, baseUrl, apiKey, models, callback ->
                viewModel.addCustomProvider(name, apiMode, baseUrl, apiKey, models, callback)
            },
            onDeleteProvider = { providerId, callback ->
                viewModel.deleteProvider(providerId, callback)
            },
            onTestCustomEndpoint = { baseUrl, apiMode, apiKey, name, callback ->
                viewModel.testCustomEndpoint(baseUrl, apiMode, apiKey, name, callback)
            },
            onDismiss = { showProviderSheet = false }
        )
    }

    if (showSlashManagerDialog) {
        SlashCommandsManagerDialog(
            commands = slashCommands,
            onAddCommand = { viewModel.addSlashCommand(it) },
            onEditCommand = { oldCmd, updated -> viewModel.updateSlashCommand(oldCmd, updated) },
            onMoveCommand = { fromIdx, toIdx -> viewModel.moveSlashCommand(fromIdx, toIdx) },
            onDeleteCommand = { viewModel.deleteSlashCommand(it) },
            onResetDefaults = { viewModel.resetSlashCommandsToDefault() },
            onDismiss = { showSlashManagerDialog = false }
        )
    }

    if (showRenameSessionDialog && activeSession != null) {
        RenameSessionDialog(
            currentTitle = activeSession!!.title,
            onRename = { newTitle ->
                viewModel.renameActiveSession(newTitle)
                showRenameSessionDialog = false
            },
            onDismiss = { showRenameSessionDialog = false }
        )
    }
}

@Composable
fun EmptyChatWelcome(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(AppDimensions.ShapeCard)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Hermes",
                modifier = Modifier
                    .size(68.dp)
                    .clip(AppDimensions.ShapeCard)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = strings.emptyWelcomeTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.emptyWelcomeSubtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = strings.emptyWelcomeHint,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
