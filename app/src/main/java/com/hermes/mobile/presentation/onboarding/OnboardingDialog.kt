package com.hermes.mobile.presentation.onboarding

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hermes.mobile.R
import com.hermes.mobile.domain.model.ConnectionState
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import com.hermes.mobile.transport.auth.RemotePairingHelper
import com.hermes.mobile.transport.termux.TermuxRunner
import kotlinx.coroutines.delay

@Composable
fun OnboardingDialog(
    connectionState: ConnectionState,
    onStartDaemonInBackground: () -> Unit,
    onEnableDemoMode: () -> Unit,
    onCustomServer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val appStrings = strings

    var isTermuxInstalled by remember { mutableStateOf(TermuxRunner.isTermuxInstalled(context)) }

    // Автоматическая проверка при возврате в приложение из Termux / браузера
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isTermuxInstalled = TermuxRunner.isTermuxInstalled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Фоновый опрос статуса установки Termux каждые 2 секунды
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val updated = TermuxRunner.isTermuxInstalled(context)
            if (updated != isTermuxInstalled) {
                isTermuxInstalled = updated
            }
        }
    }

    var isStartingDaemon by remember { mutableStateOf(false) }
    LaunchedEffect(isStartingDaemon) {
        if (isStartingDaemon) {
            delay(3500)
            isStartingDaemon = false
        }
    }

    var showSetupScript by remember { mutableStateOf(true) }
    var showCustomServerInput by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf("ws://192.168.1.100:8000/ws") }

    // Автоматическое закрытие онбординга при успешном подключении
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.READY) {
            onDismiss()
        }
    }

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
                .heightIn(min = 450.dp, max = 720.dp)
                .padding(horizontal = 14.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Иконка и заголовок
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(AppDimensions.ShapeCard)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "Hermes",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(AppDimensions.ShapeCard)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.onboardingWelcome,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.onboardingTagline,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Карточка статуса окружения с кнопкой ручной проверки
                Surface(
                    shape = AppDimensions.ShapeField,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isTermuxInstalled) AccentEmerald.copy(alpha = 0.3f) else AccentRose.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isTermuxInstalled) AccentEmerald else AccentRose)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTermuxInstalled) strings.onboardingTermuxDetected else strings.onboardingTermuxNotInstalled,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isTermuxInstalled)
                                    strings.onboardingTermuxDescDetected
                                else
                                    strings.onboardingTermuxDescNotInstalled,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                isTermuxInstalled = TermuxRunner.isTermuxInstalled(context)
                                Toast.makeText(context, if (isTermuxInstalled) appStrings.onboardingTermuxDetected else appStrings.onboardingTermuxNotInstalled, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = strings.onboardingCheckInstalledBtn,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // СЦЕНАРИЙ 1: Termux НЕ установлен
                if (!isTermuxInstalled) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { TermuxRunner.openTermuxDownloadPage(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.onboardingInstallTermuxBtn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                isTermuxInstalled = TermuxRunner.isTermuxInstalled(context)
                                Toast.makeText(context, if (isTermuxInstalled) appStrings.onboardingTermuxDetected else appStrings.onboardingTermuxNotInstalled, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.onboardingCheckInstalledBtn, fontSize = 12.sp)
                        }
                    }
                } else {
                    // СЦЕНАРИЙ 2: Termux установлен — ПОШАГОВАЯ НАСТРОЙКА И ЗАПУСК
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ШАГ 1: Разовая установка
                        Surface(
                            shape = AppDimensions.ShapeField,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(PrimaryIndigo),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("1", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = strings.onboardingStep1Title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    TextButton(
                                        onClick = { showSetupScript = !showSetupScript },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = if (showSetupScript) strings.thinkingCollapse else strings.thinkingExpand,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Text(
                                    text = strings.onboardingStep1Desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(TermuxRunner.SETUP_COMMAND))
                                        Toast.makeText(context, appStrings.onboardingCommandCopied, Toast.LENGTH_SHORT).show()
                                        if (!TermuxRunner.openTermux(context)) {
                                            TermuxRunner.openTermuxDownloadPage(context)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppDimensions.ShapeButton
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(strings.onboardingCopyAndOpenTermuxBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                AnimatedVisibility(visible = showSetupScript) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            shape = AppDimensions.ShapeBadge,
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = TermuxRunner.SETUP_COMMAND,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = PrimaryIndigo,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ШАГ 2: Запуск агента в фоне
                        Surface(
                            shape = AppDimensions.ShapeField,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryIndigo),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("2", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.onboardingStep2Title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = strings.onboardingStep2Desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        isStartingDaemon = true
                                        onStartDaemonInBackground()
                                        Toast.makeText(context, appStrings.onboardingDaemonSentToast, Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = !isStartingDaemon,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                    shape = AppDimensions.ShapeButton
                                ) {
                                    if (isStartingDaemon) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.termuxDaemonStarting, fontSize = 13.sp)
                                    } else {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.onboardingStartDaemonBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Альтернативный сценарий: Удалённый сервер (ПК / VPS)
                if (showCustomServerInput) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            label = { Text(strings.agentWsUrlLabel, fontSize = 11.sp) },
                            singleLine = true,
                            shape = AppDimensions.ShapeField,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        RemotePairingHelper.startQrScan(
                                            activity = activity,
                                            onSuccess = { cfg ->
                                                customUrlInput = cfg.serverUrl
                                                Toast.makeText(context, "${appStrings.onboardingQrRecognized}${cfg.name}", Toast.LENGTH_SHORT).show()
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
                                            customUrlInput = cfg.serverUrl
                                            Toast.makeText(context, "${appStrings.onboardingQrRecognized}${cfg.name}", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            shape = AppDimensions.ShapeButton,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.onboardingScanQrPrompt, fontSize = 12.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showCustomServerInput = false }) {
                                Text(strings.cancel, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    if (customUrlInput.isNotBlank()) {
                                        onCustomServer(customUrlInput.trim())
                                        onDismiss()
                                    }
                                },
                                shape = AppDimensions.ShapeButton
                            ) {
                                Text(strings.onboardingConnectServerBtn, fontSize = 12.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Нижние действия: Удалённый сервер, Демо-режим, Пропустить
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showCustomServerInput = !showCustomServerInput }) {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.onboardingRemoteServerBtn, fontSize = 11.sp)
                    }

                    TextButton(
                        onClick = {
                            onEnableDemoMode()
                            onDismiss()
                        }
                    ) {
                        Text(strings.onboardingDemoModeBtn, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    TextButton(onClick = onDismiss) {
                        Text(strings.onboardingSkipBtn, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
