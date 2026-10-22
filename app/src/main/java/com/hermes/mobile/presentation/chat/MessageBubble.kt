package com.hermes.mobile.presentation.chat

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hermes.mobile.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.domain.model.ChatMessage
import com.hermes.mobile.domain.model.MessageRole
import com.hermes.mobile.domain.model.MessageStatus
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.AssistantBubbleColor
import com.hermes.mobile.presentation.theme.CodeBlockBackground
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import com.hermes.mobile.presentation.theme.UserBubbleColor

@Composable
fun MessageBubble(
    message: ChatMessage,
    onRetry: ((String) -> Unit)? = null
) {
    val isUser = message.role == MessageRole.USER

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
    ) {
        val maxBubbleWidth = maxWidth * 0.85f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!isUser) {
                Image(
                    painter = painterResource(id = R.drawable.avatar_hermes),
                    contentDescription = "Hermes",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(if (isUser) UserBubbleColor else AssistantBubbleColor)
                    .padding(12.dp)
            ) {
                // Индикатор и отображение прикрепленных файлов
                if (message.attachments.isNotEmpty()) {
                    if (isUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (message.attachments.size == 1) strings.attachmentFile else "${strings.attachmentFiles} (${message.attachments.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.attachments.forEach { attach ->
                            if (attach.mimeType.startsWith("image/") && !attach.dataUrl.isNullOrBlank()) {
                                ChatImageCard(
                                    source = attach.dataUrl,
                                    label = attach.name
                                )
                            } else {
                                Surface(
                                    shape = AppDimensions.ShapeBadge,
                                    color = Color.Black.copy(alpha = 0.25f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = strings.fileDescription,
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = attach.name,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (attach.sizeBytes > 0) {
                                                Text(
                                                    text = formatFileSize(attach.sizeBytes),
                                                    color = Color.White.copy(alpha = 0.65f),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (isUser && (message.content.contains("📎 ") || message.content.contains("@file:"))) {
                    // Индикация для исторических сообщений с вложениями
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.attachmentSingle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                // Специальный аккордеон размышлений агента (Desktop standard)
                if (!isUser && !message.thinkingContent.isNullOrBlank()) {
                    ThinkingAccordion(
                        thinkingText = message.thinkingContent,
                        isStreaming = message.status == MessageStatus.STREAMING,
                        isAnswerPresent = message.content.isNotBlank(),
                        modifier = Modifier.padding(bottom = if (message.content.isNotBlank()) 8.dp else 0.dp)
                    )
                }

                // Рендеринг текста, изображений и блоков кода ответа
                if (message.content.isNotBlank()) {
                    MessageContentRenderer(content = message.content)
                }

                // Индикатор статуса генерации (показывается только при генерации ответа)
                if (message.status == MessageStatus.STREAMING && (message.content.isNotBlank() || message.thinkingContent.isNullOrBlank())) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 2.dp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.generating,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                // Индикатор ошибки и кнопка повтора запроса (Retry)
                if (message.status == MessageStatus.ERROR || !message.errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = AppDimensions.ShapeBadge,
                        color = AccentRose.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = strings.error,
                                    tint = AccentRose,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = message.errorMessage ?: strings.errorGeneratingResponse,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (onRetry != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { onRetry(message.id) },
                                        shape = AppDimensions.ShapeButton,
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.retry,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun MessageContentRenderer(content: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val appStrings = strings

    val blocks = androidx.compose.runtime.remember(content) { parseMessageContent(content) }

    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Text -> {
                        if (block.text.isNotBlank()) {
                            Text(
                                text = block.text,
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    is ContentBlock.Image -> {
                        ChatImageCard(
                            source = block.source,
                            label = block.label
                        )
                    }
                    is ContentBlock.Code -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(AppDimensions.ShapeBadge)
                                .background(CodeBlockBackground)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.language.ifBlank { "code" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray
                                )
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(block.code))
                                        Toast.makeText(context, appStrings.codeCopied, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = strings.copy,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = Color.Gray.copy(alpha = 0.3f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Text(
                                text = block.code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Специальный раскрываемый блок размышлений модели (по стандарту современных AI-клиентов)
 */
@Composable
fun ThinkingAccordion(
    thinkingText: String,
    isStreaming: Boolean,
    isAnswerPresent: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(!isAnswerPresent && isStreaming) }

    // Автоматически сворачиваем размышления, когда начинает генерироваться основной ответ
    LaunchedEffect(isAnswerPresent) {
        if (isAnswerPresent && isExpanded) {
            isExpanded = false
        }
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Surface(
        shape = AppDimensions.ShapeChip,
        color = Color(0xFF1B202D).copy(alpha = 0.75f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = strings.thinking,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (isStreaming && !isAnswerPresent) strings.thinkingProgress else strings.thinking,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    if (isStreaming && !isAnswerPresent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(9.dp),
                            strokeWidth = 1.5.dp,
                            color = PrimaryIndigo
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) strings.thinkingCollapse else strings.thinkingExpand,
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotationAngle)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = thinkingText,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}
