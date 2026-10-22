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
import com.hermes.mobile.R
import com.hermes.mobile.domain.model.ConnectionState
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import com.hermes.mobile.transport.auth.RemotePairingHelper
import com.hermes.mobile.transport.termux.TermuxRunner

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
    val isTermuxInstalled = remember { TermuxRunner.isTermuxInstalled(context) }

    var isStartingDaemon by remember { mutableStateOf(false) }
    var showSetupScript by remember { mutableStateOf(!isTermuxInstalled) }
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

                // Карточка статуса окружения
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
                    }
                }

                // СЦЕНАРИЙ 1: Termux НЕ установлен
                if (!isTermuxInstalled) {
                    Button(
                        onClick = { TermuxRunner.openTermuxDownloadPage(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDimensions.ShapeButton
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.onboardingInstallTermuxBtn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // СЦЕНАРИЙ 2: Termux установлен — ОСНОВНОЕ ДЕЙСТВИЕ: скрытый запуск в фоне
                    Button(
                        onClick = {
                            isStartingDaemon = true
                            onStartDaemonInBackground()
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

                // Блок разовой настройки (если Hermes ещё не установлен в Termux)
                Surface(
                    shape = AppDimensions.ShapeField,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.onboardingFirstSetupTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = { showSetupScript = !showSetupScript },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showSetupScript) strings.thinkingCollapse else strings.thinkingExpand,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        AnimatedVisibility(visible = showSetupScript) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    text = "pkg update -y && pkg install -y python && pip install hermes-agent",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
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
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(TermuxRunner.SETUP_COMMAND))
                                            Toast.makeText(context, appStrings.onboardingCommandCopied, Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = AppDimensions.ShapeButton
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.copy, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (!TermuxRunner.openTermux(context)) {
                                                TermuxRunner.openTermuxDownloadPage(context)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = AppDimensions.ShapeButton
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.termuxOpenAppBtn, fontSize = 11.sp)
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
