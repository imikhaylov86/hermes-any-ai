package com.hermes.mobile.presentation.provider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hermes.mobile.domain.model.ProviderApiMode
import com.hermes.mobile.domain.model.ProviderConfig
import com.hermes.mobile.domain.model.ProviderStatus
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentAmber
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderBottomSheet(
    providers: List<ProviderConfig>,
    isLoading: Boolean,
    errorMessage: String?,
    currentModel: String,
    currentProviderId: String = "",
    fallbackModel: String = "",
    onSelectModel: (providerId: String, model: String) -> Unit,
    onSetFallbackModel: (providerId: String, model: String) -> Unit = { _, _ -> },
    onClearFallbackModel: () -> Unit = {},
    onSaveApiKey: (String, String) -> Unit,
    onGetApiKey: (String) -> String?,
    onRefresh: () -> Unit,
    onTestConnection: (String, (Boolean, Long, String?) -> Unit) -> Unit,
    onAddCustomProvider: (name: String, apiMode: ProviderApiMode, baseUrl: String, apiKey: String, models: List<String>, (Boolean, String?) -> Unit) -> Unit,
    onDeleteProvider: (providerId: String, (Boolean, String?) -> Unit) -> Unit,
    onTestCustomEndpoint: (baseUrl: String, apiMode: ProviderApiMode, apiKey: String, name: String, (Boolean, Long, String?, List<String>) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedProviderId by remember {
        mutableStateOf(
            if (currentProviderId.isNotEmpty() && providers.any { it.id == currentProviderId }) {
                currentProviderId
            } else {
                providers.firstOrNull { it.availableModels.contains(currentModel) }?.id
                    ?: providers.firstOrNull()?.id ?: ""
            }
        )
    }

    LaunchedEffect(providers, currentProviderId) {
        if (selectedProviderId.isEmpty() || providers.none { it.id == selectedProviderId }) {
            selectedProviderId = if (currentProviderId.isNotEmpty() && providers.any { it.id == currentProviderId }) {
                currentProviderId
            } else {
                providers.firstOrNull { it.availableModels.contains(currentModel) }?.id
                    ?: providers.firstOrNull()?.id ?: ""
            }
        }
    }

    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }

    var apiKeyText by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var keySavedNotice by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf(false) }

    // Диалоги добавления и удаления
    var showAddDialog by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<ProviderConfig?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val appStrings = strings

    LaunchedEffect(selectedProviderId) {
        // Безопасность: не отображаем расшифрованный ключ в открытом поле
        apiKeyText = ""
        isApiKeyVisible = false
        keySavedNotice = false
        testResultText = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Заголовок и кнопки действий
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.providerSheetTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(36.dp),
                        shape = AppDimensions.ShapeChip
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = strings.addCustomProviderTitle,
                            modifier = Modifier.size(20.dp),
                            tint = PrimaryIndigo
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier.size(36.dp),
                        shape = AppDimensions.ShapeChip
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryIndigo
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = strings.retry,
                                modifier = Modifier.size(18.dp),
                                tint = PrimaryIndigo
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Сообщение об ошибке соединения с runtime
            if (errorMessage != null) {
                Surface(
                    shape = AppDimensions.ShapeCard,
                    color = AccentRose.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = errorMessage,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onRefresh) {
                            Text(strings.retry, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (isLoading && providers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = PrimaryIndigo)
                        Text(
                            text = strings.provPollingHermes,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (providers.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.provNoProvidersConfigured,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = strings.provNoProvidersHint,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showAddDialog = true },
                            shape = AppDimensions.ShapeButton
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.provAddProviderBtn)
                        }
                        OutlinedButton(
                            onClick = onRefresh,
                            shape = AppDimensions.ShapeButton
                        ) {
                            Text(strings.retry)
                        }
                    }
                }
            } else {
                // Горизонтальный список провайдеров (чипы)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    providers.forEach { prov ->
                        val isSelected = prov.id == selectedProviderId
                        val (dotColor, _) = when (prov.status) {
                            ProviderStatus.CONNECTED -> AccentEmerald to strings.provStatusConnected
                            ProviderStatus.AUTH_REQUIRED -> AccentAmber to strings.provStatusAuthRequired
                            ProviderStatus.ERROR -> AccentRose to strings.provStatusError
                            ProviderStatus.DISCONNECTED -> Color.Gray to strings.provStatusDisconnected
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedProviderId = prov.id },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = prov.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (prov.isCustom) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "⚙",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            shape = AppDimensions.ShapeChip
                        )
                    }

                    // Быстрая кнопка «+ Добавить» в стиле чипов
                    SuggestionChip(
                        onClick = { showAddDialog = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PrimaryIndigo
                            )
                        },
                        label = {
                            Text(
                                text = strings.provNewChip,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryIndigo
                            )
                        },
                        shape = AppDimensions.ShapeChip
                    )
                }

                selectedProvider?.let { prov ->
                    // Статус карточки провайдера
                    Surface(
                        shape = AppDimensions.ShapeCard,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = prov.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        if (prov.isCustom) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = AppDimensions.ShapeBadge,
                                                color = PrimaryIndigo.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = strings.provCustomBadge,
                                                    fontSize = 10.sp,
                                                    color = PrimaryIndigo,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when (prov.status) {
                                            ProviderStatus.CONNECTED -> "✓ ${strings.online}"
                                            ProviderStatus.AUTH_REQUIRED -> "⚠️ ${strings.apiKeyLabel}"
                                            ProviderStatus.ERROR -> "✗ ${strings.error}"
                                            ProviderStatus.DISCONNECTED -> strings.offline
                                        },
                                        fontSize = 12.sp,
                                        color = when (prov.status) {
                                            ProviderStatus.CONNECTED -> AccentEmerald
                                            ProviderStatus.AUTH_REQUIRED -> AccentAmber
                                            ProviderStatus.ERROR -> AccentRose
                                            ProviderStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Кнопка тестирования связи
                                    OutlinedButton(
                                        onClick = {
                                            isTestingConnection = true
                                            testResultText = null
                                            onTestConnection(prov.id) { success, latencyMs, err ->
                                                isTestingConnection = false
                                                testResultSuccess = success
                                                testResultText = if (success) {
                                                    "${appStrings.online}: ${latencyMs} ms"
                                                } else {
                                                    err ?: appStrings.statusFailed
                                                }
                                            }
                                        },
                                        enabled = !isTestingConnection,
                                        shape = AppDimensions.ShapeButton,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        if (isTestingConnection) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text(strings.testLatencyBtn, fontSize = 11.sp)
                                        }
                                    }

                                    // Кнопка удаления для пользовательских провайдеров
                                    if (prov.isCustom) {
                                        IconButton(
                                            onClick = { providerToDelete = prov },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = strings.delete,
                                                tint = AccentRose,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Дополнительная инфо для пользовательских провайдеров
                            if (prov.isCustom || !prov.baseUrl.isNullOrBlank()) {
                                Surface(
                                    shape = AppDimensions.ShapeBadge,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("API:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(prov.apiMode.displayName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        if (!prov.baseUrl.isNullOrBlank()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("URL:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(prov.baseUrl, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                                            }
                                        }
                                    }
                                }
                            }

                            if (testResultText != null) {
                                Text(
                                    text = (if (testResultSuccess) "✓ " else "✗ ") + testResultText,
                                    color = if (testResultSuccess) AccentEmerald else AccentRose,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Резервная модель баннер
                    if (fallbackModel.isNotBlank()) {
                        Surface(
                            shape = AppDimensions.ShapeCard,
                            color = AccentAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${strings.fallbackBadge}: $fallbackModel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = onClearFallbackModel, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Список доступных моделей
                    Text(
                        text = strings.provAvailableModelsCount.format(prov.availableModels.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (prov.availableModels.isEmpty()) {
                        Text(
                            text = strings.provNoModelsList.format(prov.currentModel ?: "Hermes"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            prov.availableModels.forEach { modelName ->
                                val isCurrent = modelName == currentModel || modelName == prov.currentModel
                                val isFallback = modelName == fallbackModel
                                Surface(
                                    onClick = {
                                        if (!prov.hasApiKey || prov.status == ProviderStatus.AUTH_REQUIRED) {
                                            testResultSuccess = false
                                            testResultText = appStrings.provNeedApiKeyNotice.format(prov.name)
                                        } else {
                                            onSelectModel(prov.id, modelName)
                                        }
                                    },
                                    shape = AppDimensions.ShapeField,
                                    color = if (isCurrent) PrimaryIndigo.copy(alpha = 0.15f)
                                    else if (isFallback) AccentAmber.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isCurrent) BorderStroke(1.5.dp, PrimaryIndigo)
                                    else if (isFallback) BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
                                    else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = modelName,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (isCurrent) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = AppDimensions.ShapeBadge,
                                                        color = PrimaryIndigo.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = strings.provPrimaryBadge,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = PrimaryIndigo,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                if (isFallback) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = AppDimensions.ShapeBadge,
                                                        color = AccentAmber.copy(alpha = 0.25f)
                                                    ) {
                                                        Text(
                                                            text = strings.fallbackBadge,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = AccentAmber,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Кнопка назначения/снятия резервной модели
                                            IconButton(
                                                onClick = {
                                                    if (isFallback) {
                                                        onClearFallbackModel()
                                                    } else {
                                                        onSetFallbackModel(prov.id, modelName)
                                                    }
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Security,
                                                    contentDescription = if (isFallback) strings.fallbackModelClearBtn else strings.setFallbackBtnDesc,
                                                    tint = if (isFallback) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }

                                            if (isCurrent) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = AccentEmerald,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Ввод/обновление API-ключа с защитой от утечек
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.provApiKeyLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (prov.hasApiKey) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = strings.provEncryptedKeystore,
                                        fontSize = 11.sp,
                                        color = AccentEmerald
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { apiKeyText = it },
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Кнопка вставки из буфера
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrBlank()) {
                                                apiKeyText = clip.trim()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = strings.provPasteKey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Кнопка скрытия/показа
                                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                        Icon(
                                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isApiKeyVisible) strings.provHideKey else strings.provShowKey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            placeholder = {
                                Text(
                                    if (prov.hasApiKey) strings.provKeySavedPlaceholder else strings.provEnterKeyPlaceholder,
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeField
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    if (apiKeyText.isNotBlank()) {
                                        onSaveApiKey(prov.id, apiKeyText)
                                        keySavedNotice = true
                                        apiKeyText = "" // Очищаем из поля ввода для безопасности
                                    }
                                },
                                enabled = apiKeyText.isNotBlank(),
                                shape = AppDimensions.ShapeButton
                            ) {
                                Text(strings.provSaveToHermes)
                            }
                        }

                        if (keySavedNotice) {
                            Text(
                                text = strings.provKeySavedNotice,
                                color = AccentEmerald,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    // Диалог создания пользовательского провайдера
    if (showAddDialog) {
        AddCustomProviderDialog(
            onDismiss = { showAddDialog = false },
            onTestConnection = onTestCustomEndpoint,
            onAddProvider = { name, apiMode, baseUrl, apiKey, models, callback ->
                onAddCustomProvider(name, apiMode, baseUrl, apiKey, models) { success, err ->
                    callback(success, err)
                    if (success) {
                        showAddDialog = false
                    }
                }
            }
        )
    }

    // Диалог подтверждения удаления
    providerToDelete?.let { prov ->
        AlertDialog(
            onDismissRequest = { if (!isDeleting) providerToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = AccentRose
                )
            },
            title = {
                Text(strings.provDeleteTitle)
            },
            text = {
                Text(
                    text = strings.provDeleteConfirm.format(prov.name),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        onDeleteProvider(prov.id) { _, _ ->
                            isDeleting = false
                            providerToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                    shape = AppDimensions.ShapeButton,
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(strings.delete)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { providerToDelete = null },
                    enabled = !isDeleting
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onTestConnection: (baseUrl: String, apiMode: ProviderApiMode, apiKey: String, name: String, (Boolean, Long, String?, List<String>) -> Unit) -> Unit,
    onAddProvider: (name: String, apiMode: ProviderApiMode, baseUrl: String, apiKey: String, models: List<String>, (Boolean, String?) -> Unit) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var selectedApiMode by remember { mutableStateOf(ProviderApiMode.OPENAI_COMPATIBLE) }
    var apiModeExpanded by remember { mutableStateOf(false) }
    var baseUrlText by remember { mutableStateOf("") }
    var apiKeyText by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf(false) }
    var discoveredModels by remember { mutableStateOf<List<String>>(emptyList()) }

    var isSaving by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val appStrings = strings

    val isNameValid = nameText.trim().isNotEmpty() && nameText.trim().length <= 16
    val isUrlValid = baseUrlText.trim().isNotEmpty() && (baseUrlText.startsWith("http://") || baseUrlText.startsWith("https://"))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = AppDimensions.ShapeCard,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Заголовок
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = strings.provAddTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.provAddSubtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = strings.close)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Поле 1: Название провайдера (максимум 16 символов)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { input ->
                                if (input.length <= 16) {
                                    nameText = input
                                }
                            },
                            singleLine = true,
                            label = { Text(strings.provNameLabel) },
                            placeholder = { Text(strings.provNameHint) },
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (nameText.length >= 16) strings.provNameLimitReached else strings.provNameLimit,
                                        fontSize = 11.sp,
                                        color = if (nameText.length >= 16) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${nameText.length}/16",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            isError = nameText.isNotBlank() && nameText.length > 16,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeField
                        )
                    }

                    // Поле 2: Пикер выбора типа взаимодействия
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.provProtocolLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ExposedDropdownMenuBox(
                            expanded = apiModeExpanded,
                            onExpandedChange = { apiModeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedApiMode.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = apiModeExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = AppDimensions.ShapeField
                            )

                            ExposedDropdownMenu(
                                expanded = apiModeExpanded,
                                onDismissRequest = { apiModeExpanded = false }
                            ) {
                                ProviderApiMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(mode.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(mode.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            selectedApiMode = mode
                                            apiModeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = selectedApiMode.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Поле 3: Base URL
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = baseUrlText,
                            onValueChange = { baseUrlText = it },
                            singleLine = true,
                            label = { Text(strings.provUrlLabel) },
                            placeholder = { Text(selectedApiMode.defaultUrl) },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            isError = baseUrlText.isNotBlank() && !isUrlValid,
                            supportingText = {
                                if (baseUrlText.isNotBlank() && !isUrlValid) {
                                    Text(strings.provUrlError, color = AccentRose, fontSize = 11.sp)
                                } else {
                                    Text(strings.provUrlDesc, fontSize = 11.sp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeField
                        )

                        // Быстрые подсказки URL
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "OpenAI" to "https://api.openai.com/v1",
                                "DeepSeek" to "https://api.deepseek.com/v1",
                                "OpenRouter" to "https://openrouter.ai/api/v1",
                                "Ollama" to "http://127.0.0.1:11434/v1"
                            ).forEach { (label, url) ->
                                SuggestionChip(
                                    onClick = { baseUrlText = url },
                                    label = { Text(label, fontSize = 11.sp) },
                                    shape = AppDimensions.ShapeChip
                                )
                            }
                        }
                    }

                    // Поле 4: API-ключ (обязательно маскируется)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.provKeyLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { apiKeyText = it },
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrBlank()) {
                                                apiKeyText = clip.trim()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = strings.provPasteKey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                        Icon(
                                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isApiKeyVisible) strings.provHideKey else strings.provShowKey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            placeholder = { Text("sk-...", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeField
                        )

                        Text(
                            text = strings.provKeyNotice,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Кнопка тестирования соединения перед сохранением
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResultText = null
                            onTestConnection(baseUrlText.trim(), selectedApiMode, apiKeyText.trim(), nameText.trim()) { success, latency, err, models ->
                                isTesting = false
                                testResultSuccess = success
                                if (success) {
                                    discoveredModels = models
                                    testResultText = if (models.isNotEmpty()) {
                                        appStrings.provTestSuccessModels.format(latency, models.size)
                                    } else {
                                        appStrings.provTestSuccessLatency.format(latency)
                                    }
                                } else {
                                    testResultText = err ?: appStrings.provTestFailed
                                }
                            }
                        },
                        enabled = !isTesting && isUrlValid,
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDimensions.ShapeButton
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.provTestingConnection, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.provTestConnectionBtn, fontSize = 13.sp)
                        }
                    }

                    if (testResultText != null) {
                        Text(
                            text = (if (testResultSuccess) "✓ " else "✗ ") + testResultText,
                            color = if (testResultSuccess) AccentEmerald else AccentRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (discoveredModels.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), AppDimensions.ShapeField)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strings.provModelsDiscovered.format(discoveredModels.size),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                discoveredModels.take(8).forEach { modelId ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(modelId, fontSize = 10.sp) },
                                        shape = AppDimensions.ShapeChip
                                    )
                                }
                                if (discoveredModels.size > 8) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(strings.provMoreModels.format(discoveredModels.size - 8), fontSize = 10.sp) },
                                        shape = AppDimensions.ShapeChip
                                    )
                                }
                            }
                        }
                    }

                    if (saveErrorMessage != null) {
                        Text(
                            text = "✗ $saveErrorMessage",
                            color = AccentRose,
                            fontSize = 12.sp
                        )
                    }

                    // Кнопки Сохранить / Отмена
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Text(strings.cancel)
                        }

                        Button(
                            onClick = {
                                isSaving = true
                                saveErrorMessage = null
                                onAddProvider(
                                    nameText.trim().take(16),
                                    selectedApiMode,
                                    baseUrlText.trim(),
                                    apiKeyText.trim(),
                                    discoveredModels
                                ) { success, err ->
                                    isSaving = false
                                    if (!success) {
                                        saveErrorMessage = err ?: appStrings.provSaveConfigError
                                    }
                                }
                            },
                            enabled = !isSaving && isNameValid && isUrlValid,
                            modifier = Modifier.weight(1f),
                            shape = AppDimensions.ShapeButton
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(strings.save)
                            }
                        }
                    }
                }
            }
        }
    }
}

