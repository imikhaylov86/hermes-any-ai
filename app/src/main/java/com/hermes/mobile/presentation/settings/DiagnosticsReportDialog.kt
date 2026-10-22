package com.hermes.mobile.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hermes.mobile.domain.model.DiagnosticsReport
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentAmber
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo

@Composable
fun DiagnosticsReportDialog(
    report: DiagnosticsReport?,
    isRunning: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
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
                .heightIn(min = 450.dp, max = 700.dp)
                .padding(horizontal = 14.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Заголовок окна
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(AppDimensions.ShapeChip)
                            .background(PrimaryIndigo.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = strings.diagTitle,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.diagTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.diagSubtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Содержимое
                if (isRunning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryIndigo,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = strings.diagPollingGateways,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = strings.diagSampleModelCost,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (report != null) {
                    // Сводная плашка
                    val bannerBg = if (report.isAllHealthy) AccentEmerald.copy(alpha = 0.12f) else AccentAmber.copy(alpha = 0.12f)
                    val bannerBorder = if (report.isAllHealthy) AccentEmerald.copy(alpha = 0.35f) else AccentAmber.copy(alpha = 0.35f)
                    val bannerIcon = if (report.isAllHealthy) Icons.Default.CheckCircle else Icons.Default.WarningAmber
                    val bannerTint = if (report.isAllHealthy) AccentEmerald else AccentAmber

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppDimensions.ShapeField)
                            .background(bannerBg)
                            .border(1.dp, bannerBorder, AppDimensions.ShapeField)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = bannerIcon,
                                contentDescription = null,
                                tint = bannerTint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (report.isAllHealthy) strings.diagSummaryAllOk else strings.diagSummaryIssues,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = bannerTint
                                )
                                Text(
                                    text = strings.diagSummaryFormat.format(
                                        report.totalGatewaysOnline,
                                        report.totalGatewaysChecked,
                                        report.totalModelsSuccess,
                                        report.totalModelsTested,
                                        report.totalTokensUsed
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Список шлюзов и моделей
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Локальный шлюз Hermes
                        item {
                            Surface(
                                shape = AppDimensions.ShapeCard,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = if (report.localGateway.isReachable) AccentEmerald else AccentRose,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = report.localGateway.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(AppDimensions.ShapeBadge)
                                                    .background(
                                                        if (report.localGateway.isReachable) AccentEmerald.copy(alpha = 0.15f)
                                                        else AccentRose.copy(alpha = 0.15f)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (report.localGateway.isReachable) strings.diagGatewayReachable.format(report.localGateway.latencyMs) else strings.diagGatewayUnreachable,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (report.localGateway.isReachable) AccentEmerald else AccentRose,
                                                    softWrap = false,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Text(
                                            text = report.localGateway.url,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (report.localGateway.errorMessage != null) {
                                            val err = report.localGateway.errorMessage
                                            val code = extractErrorCode(err)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "${strings.error}: $code",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = AccentRose
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        val diagData = buildString {
                                                            appendLine("=== ${appStrings.error}: ${report.localGateway.name} ===")
                                                            appendLine("URL: ${report.localGateway.url}")
                                                            appendLine("Time: ${java.util.Date(report.timestamp)}")
                                                            appendLine("Diagnostics:")
                                                            appendLine(err)
                                                        }
                                                        clipboard.setText(AnnotatedString(diagData))
                                                        Toast.makeText(context, appStrings.diagErrorCopied, Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = AppDimensions.ShapeButton,
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(26.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(11.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(strings.diagCopyErrorBtn, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Внешние провайдеры
                        items(report.providerResults) { prov ->
                            Surface(
                                shape = AppDimensions.ShapeCard,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Заголовок провайдера и статус шлюза
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudQueue,
                                                contentDescription = null,
                                                tint = PrimaryIndigo,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = prov.providerName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(AppDimensions.ShapeBadge)
                                                .background(
                                                    if (prov.gateway.isReachable) AccentEmerald.copy(alpha = 0.15f)
                                                    else AccentRose.copy(alpha = 0.15f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (prov.gateway.isReachable) strings.diagGatewayProvReachable.format(prov.gateway.latencyMs) else strings.diagGatewayProvUnreachable,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (prov.gateway.isReachable) AccentEmerald else AccentRose,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Text(
                                        text = prov.gateway.url,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!prov.gateway.isReachable && prov.gateway.errorMessage != null) {
                                        val err = prov.gateway.errorMessage
                                        val code = extractErrorCode(err)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = strings.diagGatewayErrorCode.format(code),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = AccentRose
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val diagData = buildString {
                                                        appendLine("=== ${appStrings.error}: ${prov.providerName} ===")
                                                        appendLine("Provider: ${prov.providerName} (${prov.providerId})")
                                                        appendLine("URL: ${prov.gateway.url}")
                                                        appendLine("Time: ${java.util.Date(report.timestamp)}")
                                                        appendLine("Diagnostics:")
                                                        appendLine(err)
                                                    }
                                                    clipboard.setText(AnnotatedString(diagData))
                                                    Toast.makeText(context, appStrings.diagErrorCopied, Toast.LENGTH_SHORT).show()
                                                },
                                                shape = AppDimensions.ShapeButton,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(11.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(strings.diagCopyErrorBtn, fontSize = 10.sp)
                                            }
                                        }
                                    }

                                    // Протестированная случайная модель
                                    if (prov.testedModel != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(AppDimensions.ShapeField)
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                                .padding(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Casino,
                                                    contentDescription = strings.diagRandomModel,
                                                    tint = PrimaryIndigo,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = strings.diagRandomModel,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = prov.testedModel.modelName,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    if (prov.testedModel.isSuccess) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = AccentEmerald,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = strings.diagModelAvailableStats.format(prov.testedModel.tokensUsed, prov.testedModel.latencyMs),
                                                                fontSize = 11.sp,
                                                                color = AccentEmerald,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    } else {
                                                        val err = prov.testedModel.errorMessage ?: strings.diagModelFailureDefault
                                                        val code = extractErrorCode(err)
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = Icons.Default.ErrorOutline,
                                                                    contentDescription = null,
                                                                    tint = AccentRose,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = strings.diagModelErrorCode.format(code),
                                                                    fontSize = 11.sp,
                                                                    color = AccentRose,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                            OutlinedButton(
                                                                onClick = {
                                                                    val diagData = buildString {
                                                                        appendLine("=== ${appStrings.error}: ${prov.testedModel.modelName} (${prov.providerName}) ===")
                                                                        appendLine("Provider: ${prov.providerName} (${prov.providerId})")
                                                                        appendLine("Gateway: ${prov.gateway.url}")
                                                                        appendLine("Model: ${prov.testedModel.modelName}")
                                                                        appendLine("Latency: ${prov.testedModel.latencyMs} ms")
                                                                        appendLine("Time: ${java.util.Date(report.timestamp)}")
                                                                        appendLine("Diagnostics:")
                                                                        appendLine(err)
                                                                    }
                                                                    clipboard.setText(AnnotatedString(diagData))
                                                                    Toast.makeText(context, appStrings.diagErrorCopied, Toast.LENGTH_SHORT).show()
                                                                },
                                                                shape = AppDimensions.ShapeButton,
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(26.dp)
                                                            ) {
                                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(11.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(strings.diagCopyErrorBtn, fontSize = 10.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = strings.diagNoModels,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Кнопки управления
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        enabled = !isRunning,
                        shape = AppDimensions.ShapeButton
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.retry,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.retry, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = AppDimensions.ShapeButton
                    ) {
                        Text(strings.close, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun extractErrorCode(errorMessage: String?): String {
    if (errorMessage.isNullOrBlank()) return "Error"
    val codeRegex = Regex("""\b([45]\d{2})\b""")
    val match = codeRegex.find(errorMessage)
    if (match != null) {
        return match.value
    }
    if (errorMessage.contains("timeout", ignoreCase = true)) return "Timeout"
    if (errorMessage.contains("connect", ignoreCase = true)) return "Offline"
    if (errorMessage.contains("refused", ignoreCase = true)) return "Refused"
    return "Error"
}
