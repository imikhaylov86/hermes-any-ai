package com.hermes.mobile.presentation.settings

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hermes.mobile.BuildConfig
import com.hermes.mobile.domain.update.UpdateInfo
import com.hermes.mobile.domain.update.UpdateState
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    updateState: UpdateState,
    canInstallPackages: Boolean,
    onCheckAgain: () -> Unit,
    onStartDownload: (UpdateInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    onRequestInstallPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    if (updateState is UpdateState.Idle) return

    val appStrings = strings
    val context = LocalContext.current

    Dialog(
        onDismissRequest = {
            if (updateState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = updateState !is UpdateState.Downloading,
            dismissOnClickOutside = updateState !is UpdateState.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = AppDimensions.ShapeDialog,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (updateState) {
                    is UpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = PrimaryIndigo,
                                strokeWidth = 3.dp
                            )
                            Column {
                                Text(
                                    text = appStrings.checkingUpdates,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "GitHub Releases API • imikhaylov86/hermes-any-ai",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is UpdateState.NoUpdateAvailable -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(AppDimensions.ShapeChip)
                                    .background(AccentEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = appStrings.noUpdatesTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = appStrings.currentVersionLatest,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Text(appStrings.close, fontSize = 13.sp)
                        }
                    }

                    is UpdateState.UpdateAvailable -> {
                        val info = updateState.info
                        val sizeMb = if (info.asset.size > 0) {
                            String.format(Locale.US, "%.1f MB", info.asset.size / (1024f * 1024f))
                        } else null

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(AppDimensions.ShapeChip)
                                        .background(PrimaryIndigo.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = PrimaryIndigo,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = appStrings.updateAvailableTitle,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${info.tagName} • ${sizeMb ?: "APK"}",
                                        fontSize = 12.sp,
                                        color = PrimaryIndigo,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (info.changelog.isNotBlank()) {
                            Surface(
                                shape = AppDimensions.ShapeCard,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = appStrings.whatIsNew,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = info.changelog,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = AppDimensions.ShapeButton
                            ) {
                                Text(appStrings.later, fontSize = 13.sp)
                            }
                            Button(
                                onClick = { onStartDownload(info) },
                                modifier = Modifier.weight(1.5f),
                                shape = AppDimensions.ShapeButton,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(appStrings.downloadAndUpdateBtn, fontSize = 13.sp)
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        val info = updateState.info
                        val percent = updateState.progressPercent

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(AppDimensions.ShapeChip)
                                    .background(PrimaryIndigo.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.size(22.dp),
                                    color = PrimaryIndigo,
                                    strokeWidth = 2.5.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = String.format(Locale.getDefault(), appStrings.downloadingProgressFormat, percent),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = info.asset.name,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(AppDimensions.ShapeChip),
                            color = PrimaryIndigo,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = "Пожалуйста, подождите. Обновление скачивается и будет передано системному установщику без потери данных.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        val file = File(updateState.apkPath)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(AppDimensions.ShapeChip)
                                    .background(AccentEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Файл обновления готов",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${updateState.info.tagName} • ${file.name}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!canInstallPackages && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Surface(
                                shape = AppDimensions.ShapeCard,
                                color = AccentRose.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = appStrings.installPermissionRequired,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onRequestInstallPermission,
                                        shape = AppDimensions.ShapeButton,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(appStrings.grantPermissionBtn, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { onInstallApk(file) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeButton,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(appStrings.installUpdateBtn, fontSize = 13.sp)
                        }
                    }

                    is UpdateState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(AppDimensions.ShapeChip)
                                    .background(AccentRose.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AccentRose,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = appStrings.updateErrorTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = updateState.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 3
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = AppDimensions.ShapeButton
                            ) {
                                Text(appStrings.close, fontSize = 13.sp)
                            }
                            Button(
                                onClick = onCheckAgain,
                                modifier = Modifier.weight(1f),
                                shape = AppDimensions.ShapeButton
                            ) {
                                Text(appStrings.retry, fontSize = 13.sp)
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
