package com.hermes.mobile.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.domain.model.ApprovalDecision
import com.hermes.mobile.domain.model.RiskLevel
import com.hermes.mobile.domain.model.ToolApprovalRequest
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentAmber
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.CodeBlockBackground
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolApprovalCard(
    request: ToolApprovalRequest,
    onDecision: (ApprovalDecision) -> Unit
) {
    val riskColor = when (request.riskLevel) {
        RiskLevel.CRITICAL -> AccentRose
        RiskLevel.MEDIUM -> AccentAmber
        RiskLevel.LOW -> AccentEmerald
    }

    var isParamsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(
                width = 1.dp,
                color = if (request.isResponded) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else riskColor.copy(alpha = 0.5f),
                shape = AppDimensions.ShapeCard
            ),
        shape = AppDimensions.ShapeCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (request.isResponded) Icons.Default.VerifiedUser else Icons.Default.Warning,
                        contentDescription = "Risk",
                        tint = if (request.isResponded) MaterialTheme.colorScheme.onSurfaceVariant else riskColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (request.isResponded) strings.approvalActionProcessed else strings.approvalActionPending,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    color = if (request.isResponded) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f) else riskColor.copy(alpha = 0.2f),
                    shape = AppDimensions.ShapeBadge
                ) {
                    Text(
                        text = request.riskLevel.name,
                        color = if (request.isResponded) MaterialTheme.colorScheme.onSurfaceVariant else riskColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.approvalToolLabel,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = request.toolName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = PrimaryIndigo
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            // Parameters block with expand/collapse
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppDimensions.ShapeBadge)
                    .background(CodeBlockBackground)
                    .clickable { isParamsExpanded = !isParamsExpanded }
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = request.parametersPreview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        maxLines = if (isParamsExpanded) Int.MAX_VALUE else 3
                    )
                    if (request.parametersPreview.lines().size > 3 || request.parametersPreview.length > 120) {
                        Text(
                            text = if (isParamsExpanded) strings.approvalCollapse else strings.approvalExpandFull,
                            fontSize = 10.sp,
                            color = PrimaryIndigo,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // State: Responded vs Pending Actions
            if (request.isResponded) {
                val (decisionColor, decisionText, decisionIcon) = when (request.userDecision) {
                    ApprovalDecision.APPROVE -> Triple(AccentEmerald, strings.approvalUserAllowed, Icons.Default.Check)
                    ApprovalDecision.ALWAYS_ALLOW -> Triple(AccentEmerald, strings.approvalAlwaysAllowed, Icons.Default.DoneAll)
                    ApprovalDecision.DENY, null -> Triple(AccentRose, strings.approvalUserDenied, Icons.Default.Close)
                }
                val timeStr = request.respondedAtEpochMs?.let {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                } ?: ""

                Surface(
                    shape = AppDimensions.ShapeField,
                    color = decisionColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, decisionColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = decisionIcon,
                                contentDescription = null,
                                tint = decisionColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = decisionText,
                                color = decisionColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        if (timeStr.isNotBlank()) {
                            Text(
                                text = timeStr,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                // Pending: Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onDecision(ApprovalDecision.DENY) },
                            modifier = Modifier.padding(end = 8.dp),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = AccentRose,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.approvalDeny, color = AccentRose, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onDecision(ApprovalDecision.APPROVE) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.approvalApprove, color = Color.White, fontSize = 12.sp)
                        }
                    }

                    if (request.canAlwaysAllow) {
                        TextButton(
                            onClick = { onDecision(ApprovalDecision.ALWAYS_ALLOW) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.approvalAlwaysAllowAction,
                                fontSize = 11.sp,
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
