package com.hermes.mobile.presentation.settings

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hermes.mobile.domain.model.ConnectionState
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import com.hermes.mobile.transport.auth.RemotePairingHelper

@Composable
fun SettingsDialog(
    isDemoMode: Boolean,
    currentServerUrl: String,
    connectionState: ConnectionState = ConnectionState.READY,
    currentLanguage: String = "ru",
    onSelectLanguage: (String) -> Unit = {},
    onToggleDemoMode: (Boolean) -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSlashCommands: () -> Unit = {},
    onOpenTermuxStatus: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onRestoreBackup: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    isDebugKeysExportEnabled: Boolean = false,
    onToggleDebugKeysExport: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf(currentServerUrl) }
    var demoEnabled by remember { mutableStateOf(isDemoMode) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    val appStrings = strings

    // G1 (docs/07 §3): флаг экспорта ключей — только debug-сборка, по умолчанию off
    var debugKeysExportEnabled by remember { mutableStateOf(isDebugKeysExportEnabled) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = AppDimensions.ShapeDialog,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 450.dp, max = 700.dp)
                .padding(horizontal = 14.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Заголовок
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(AppDimensions.ShapeChip)
                                .background(PrimaryIndigo.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = strings.settingsTitle,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.settingsTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.settingsSubtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // 0. Выбор языка приложения (Русский / English)
                Surface(
                    shape = AppDimensions.ShapeCard,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.languageSection,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.languageSubtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = currentLanguage == "ru",
                                onClick = { onSelectLanguage("ru") },
                                label = {
                                    Text(
                                        "🇷🇺 RU",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentLanguage == "ru") FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = AppDimensions.ShapeChip
                            )
                            FilterChip(
                                selected = currentLanguage == "en",
                                onClick = { onSelectLanguage("en") },
                                label = {
                                    Text(
                                        "🇬🇧 EN",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentLanguage == "en") FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = AppDimensions.ShapeChip
                            )
                        }
                    }
                }

                // 1. Статус локального рантайма Hermes
                val isOnline = connectionState == ConnectionState.READY
                Surface(
                    shape = AppDimensions.ShapeCard,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTermuxStatus() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(AppDimensions.ShapeChip)
                                .background(if (isOnline) AccentEmerald.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (isOnline) AccentEmerald else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.localAgentCardTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isOnline) strings.localAgentCardSubtitleOnline else strings.localAgentCardSubtitleOffline,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(AppDimensions.ShapeBadge)
                                .background(if (isOnline) AccentEmerald.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isOnline) strings.localAgentCardStatusOnline else strings.localAgentCardStatusWaiting,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOnline) AccentEmerald else Color.Gray
                            )
                        }
                    }
                }

                // 2. Главная карточка проверки доступности шлюзов и моделей
                Surface(
                    shape = AppDimensions.ShapeCard,
                    color = PrimaryIndigo.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.checkGatewaysTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = strings.checkGatewaysDesc,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                        Button(
                            onClick = {
                                onOpenDiagnostics()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.checkGatewaysBtn,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 3. Управление провайдерами
                Surface(
                    shape = AppDimensions.ShapeCard,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.providersTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.providersSubtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenProviders,
                            shape = AppDimensions.ShapeButton,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(strings.configureBtn, fontSize = 12.sp)
                        }
                    }
                }

                // 4. Управление слэш-командами
                Surface(
                    shape = AppDimensions.ShapeCard,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.slashManagerTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.slashManagerSubtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenSlashCommands,
                            shape = AppDimensions.ShapeButton,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(strings.configureBtn, fontSize = 12.sp)
                        }
                    }
                }

                // 5. Резервное копирование и экспорт всех бесед
                val context = LocalContext.current
                val restoreFilePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            val jsonText = context.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.bufferedReader().readText()
                            }
                            if (!jsonText.isNullOrBlank()) {
                                onRestoreBackup(jsonText)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "${appStrings.error}: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                Surface(
                    shape = AppDimensions.ShapeCard,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.backupTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.backupSubtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onExportBackup,
                                shape = AppDimensions.ShapeButton,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.createBackupBtn, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { restoreFilePickerLauncher.launch("*/*") },
                                shape = AppDimensions.ShapeButton,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.restoreBackupBtn, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 6. Расширенные параметры подключения (Аккордеон)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppDimensions.ShapeField)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SettingsEthernet,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.connectionParamsAccordion,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            // Переключатель автономного демо-режима
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppDimensions.ShapeField)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.demoModeSwitch,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = strings.demoModeDesc,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = demoEnabled,
                                    onCheckedChange = {
                                        demoEnabled = it
                                        onToggleDemoMode(it)
                                    }
                                )
                            }

                            // G1 (docs/07 §3): экспорт ключей — только debug-сборка
                            if (com.hermes.mobile.BuildConfig.DEBUG) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(AppDimensions.ShapeField)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = strings.debugKeysExportSwitch,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = AccentRose
                                        )
                                        Text(
                                            text = strings.debugKeysExportDesc,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = debugKeysExportEnabled,
                                        onCheckedChange = {
                                            debugKeysExportEnabled = it
                                            onToggleDebugKeysExport(it)
                                        }
                                    )
                                }
                            }

                            if (!demoEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = strings.agentWsUrlLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    OutlinedTextField(
                                        value = urlText,
                                        onValueChange = { urlText = it },
                                        singleLine = true,
                                        shape = AppDimensions.ShapeField,
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("ws://127.0.0.1:8000/ws", fontSize = 12.sp) },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                val activity = context as? Activity
                                                if (activity != null) {
                                                    RemotePairingHelper.startQrScan(
                                                        activity = activity,
                                                        onSuccess = { cfg ->
                                                            urlText = cfg.serverUrl
                                                            onSaveServerUrl(cfg.serverUrl)
                                                            Toast.makeText(context, "${appStrings.online}: ${cfg.name}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onError = { err ->
                                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.QrCodeScanner,
                                                    contentDescription = strings.scanQrBtn,
                                                    tint = PrimaryIndigo
                                                )
                                            }
                                        }
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            val activity = context as? Activity
                                            if (activity != null) {
                                                RemotePairingHelper.startQrScan(
                                                    activity = activity,
                                                    onSuccess = { cfg ->
                                                        urlText = cfg.serverUrl
                                                        onSaveServerUrl(cfg.serverUrl)
                                                        Toast.makeText(context, "${appStrings.online}: ${cfg.name}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppDimensions.ShapeButton
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.scanQrBtn, fontSize = 12.sp)
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        SuggestionChip(
                                            onClick = { urlText = "ws://127.0.0.1:8000/ws" },
                                            label = { Text("127.0.0.1:8000", fontSize = 11.sp) },
                                            shape = AppDimensions.ShapeChip
                                        )
                                        SuggestionChip(
                                            onClick = { urlText = "ws://10.0.2.2:8000/ws" },
                                            label = { Text(strings.emulatorChip, fontSize = 11.sp) },
                                            shape = AppDimensions.ShapeChip
                                        )
                                    }

                                    Button(
                                        onClick = { onSaveServerUrl(urlText) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppDimensions.ShapeButton
                                    ) {
                                        Text(strings.saveUrlBtn, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Раздел: Обновление приложения
                Surface(
                    shape = AppDimensions.ShapeCard,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(AppDimensions.ShapeChip)
                                    .background(PrimaryIndigo.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appStrings.updateSectionTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "v${com.hermes.mobile.BuildConfig.VERSION_NAME} (GitHub Releases)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onCheckForUpdates,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = appStrings.checkUpdatesBtn,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Инфо футер
                Text(
                    text = "Hermes Any AI v${com.hermes.mobile.BuildConfig.VERSION_NAME} • Native Client",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )

                // Кнопка закрытия
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppDimensions.ShapeButton
                ) {
                    Text(strings.close, fontSize = 13.sp)
                }
            }
        }
    }
}
