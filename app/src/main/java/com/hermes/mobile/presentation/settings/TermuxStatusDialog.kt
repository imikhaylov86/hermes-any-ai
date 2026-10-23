package com.hermes.mobile.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.hermes.mobile.transport.termux.TermuxRunner

@Composable
fun TermuxStatusDialog(
    connectionState: ConnectionState,
    serverUrl: String,
    onStartDaemonInBackground: () -> Unit,
    onReconnect: () -> Unit,
    onOpenWizard: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isTermuxInstalled by remember { mutableStateOf(TermuxRunner.isTermuxInstalled(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isTermuxInstalled = TermuxRunner.isTermuxInstalled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            val updated = TermuxRunner.isTermuxInstalled(context)
            if (updated != isTermuxInstalled) {
                isTermuxInstalled = updated
            }
        }
    }

    var isStartingDaemon by remember { mutableStateOf(false) }
    LaunchedEffect(isStartingDaemon) {
        if (isStartingDaemon) {
            kotlinx.coroutines.delay(3500)
            isStartingDaemon = false
        }
    }
    val appStrings = strings

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
                .heightIn(min = 400.dp, max = 700.dp)
                .padding(horizontal = 14.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(AppDimensions.ShapeChip)
                                .background(PrimaryIndigo.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.termuxTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.termuxSubtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = strings.close, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Карточка статуса соединения
                Surface(
                    shape = AppDimensions.ShapeField,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (connectionState == ConnectionState.READY) AccentEmerald.copy(alpha = 0.35f) else AccentRose.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (connectionState == ConnectionState.READY) AccentEmerald else AccentRose)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (connectionState == ConnectionState.READY) strings.statusReadyTermux else strings.statusFailed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "URL: $serverUrl",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isTermuxInstalled) strings.onboardingTermuxDetected else strings.onboardingTermuxNotInstalled,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strings.termuxProtocolVersion,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Кнопка 1-клик фонового запуска
                if (isTermuxInstalled) {
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
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.termuxDaemonStarting, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.termuxStartDaemonBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Кнопка обновления Hermes в один клик
                    OutlinedButton(
                        onClick = {
                            val res = TermuxRunner.updateHermesDaemonInBackground(context)
                            if (res.isSuccess) {
                                Toast.makeText(context, appStrings.termuxUpdateSuccess, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "${appStrings.error}: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDimensions.ShapeButton
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryIndigo)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.termuxUpdateBtn, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = { TermuxRunner.openTermuxDownloadPage(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDimensions.ShapeButton
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.onboardingInstallTermuxBtn, fontSize = 13.sp)
                    }
                }

                // Мастер первоначальной настройки
                OutlinedButton(
                    onClick = onOpenWizard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppDimensions.ShapeButton
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryIndigo
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.termuxOpenWizardBtn,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Дополнительные кнопки управления
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!TermuxRunner.openTermux(context)) {
                                TermuxRunner.openTermuxDownloadPage(context)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = AppDimensions.ShapeButton
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.termuxOpenAppBtn, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(TermuxRunner.LAUNCH_COMMAND))
                            Toast.makeText(context, appStrings.onboardingCommandCopied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = AppDimensions.ShapeButton
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.copy, fontSize = 12.sp)
                    }
                }

                // Нижняя панель
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onReconnect) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.retry, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = AppDimensions.ShapeButton
                    ) {
                        Text(strings.close, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
