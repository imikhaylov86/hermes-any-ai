package com.hermes.mobile.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hermes.mobile.domain.model.SlashCommandItem
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlashCommandsManagerDialog(
    commands: List<SlashCommandItem>,
    onAddCommand: (SlashCommandItem) -> Unit,
    onEditCommand: (oldCommandStr: String, updated: SlashCommandItem) -> Unit,
    onMoveCommand: (fromIndex: Int, toIndex: Int) -> Unit,
    onDeleteCommand: (commandStr: String) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingCommand by remember { mutableStateOf<SlashCommandItem?>(null) }
    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var commandToDelete by remember { mutableStateOf<SlashCommandItem?>(null) }

    val filteredWithIndex = remember(commands, searchQuery) {
        val q = searchQuery.trim().lowercase()
        commands.mapIndexed { idx, cmd -> cmd to idx }.filter { (cmd, _) ->
            if (q.isEmpty()) true
            else cmd.command.lowercase().contains(q) ||
                 cmd.title.lowercase().contains(q) ||
                 cmd.description.lowercase().contains(q)
        }
    }
    val filtered = filteredWithIndex.map { it.first }

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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Шапка диалога
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
                                    .background(PrimaryIndigo.copy(alpha = 0.15f)),
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
                                    text = strings.slashManagerTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.slashManagerSubtitle,
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

                    // Кнопка добавления новой команды
                    Button(
                        onClick = {
                            editingCommand = null
                            showAddOrEditDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDimensions.ShapeButton
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.slashManagerAddBtn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Поле быстрого поиска по списку
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(strings.slashManagerSearchHint, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        singleLine = true,
                        shape = AppDimensions.ShapeField,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Список зарегистрированных команд
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (filtered.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = strings.slashManagerNotFound,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(filteredWithIndex, key = { it.first.command }) { (cmd, origIdx) ->
                                Surface(
                                    shape = AppDimensions.ShapeField,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. Кнопки перемещения вверх/вниз
                                        if (searchQuery.isBlank()) {
                                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                                Column(
                                                    modifier = Modifier.padding(end = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    IconButton(
                                                        onClick = { onMoveCommand(origIdx, origIdx - 1) },
                                                        enabled = origIdx > 0,
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowUp,
                                                            contentDescription = strings.slashManagerMoveUp,
                                                            tint = if (origIdx > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { onMoveCommand(origIdx, origIdx + 1) },
                                                        enabled = origIdx < commands.size - 1,
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowDown,
                                                            contentDescription = strings.slashManagerMoveDown,
                                                            tint = if (origIdx < commands.size - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 2. Иконка типа команды
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(AppDimensions.ShapeChip)
                                                .background(
                                                    if (cmd.isCustom) AccentEmerald.copy(alpha = 0.15f)
                                                    else PrimaryIndigo.copy(alpha = 0.12f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (cmd.isCustom) Icons.Default.Bolt else Icons.Default.Terminal,
                                                contentDescription = null,
                                                tint = if (cmd.isCustom) AccentEmerald else PrimaryIndigo,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // 3. Текстовая информация (нажатие открывает редактирование)
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    editingCommand = cmd
                                                    showAddOrEditDialog = true
                                                }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = cmd.command,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (cmd.isCustom) AccentEmerald else PrimaryIndigo
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = cmd.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                            }
                                            Text(
                                                text = cmd.description,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // 4. Кнопки редактирования и удаления (справа)
                                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingCommand = cmd
                                                        showAddOrEditDialog = true
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = strings.slashManagerEdit,
                                                        tint = PrimaryIndigo,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { commandToDelete = cmd },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = strings.slashManagerDelete,
                                                        tint = AccentRose.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Нижняя панель действий
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onResetDefaults) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.slashManagerResetDefaults, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Button(
                            onClick = onDismiss,
                            shape = AppDimensions.ShapeButton
                        ) {
                            Text(strings.slashManagerDone, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Диалог создания или редактирования команды
    if (showAddOrEditDialog) {
        SlashCommandFormDialog(
            initialCommand = editingCommand,
            onSave = { oldCmd, updated ->
                if (oldCmd != null) {
                    onEditCommand(oldCmd, updated)
                } else {
                    onAddCommand(updated)
                }
                showAddOrEditDialog = false
                editingCommand = null
            },
            onDismiss = {
                showAddOrEditDialog = false
                editingCommand = null
            }
        )
    }

    // Подтверждение удаления команды
    if (commandToDelete != null) {
        val target = commandToDelete!!
        AlertDialog(
            onDismissRequest = { commandToDelete = null },
            title = { Text(strings.slashDeleteTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(strings.slashDeleteConfirm.format(target.command)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCommand(target.command)
                        commandToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text(strings.delete, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { commandToDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
fun SlashCommandFormDialog(
    initialCommand: SlashCommandItem?,
    onSave: (oldCommandStr: String?, updated: SlashCommandItem) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = initialCommand != null
    var commandName by remember { mutableStateOf(initialCommand?.command ?: "") }
    var title by remember { mutableStateOf(initialCommand?.title ?: "") }
    var description by remember { mutableStateOf(initialCommand?.description ?: "") }
    var example by remember { mutableStateOf(initialCommand?.example ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val appStrings = strings

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = AppDimensions.ShapeDialog,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(AppDimensions.ShapeChip)
                            .background(
                                if (isEditing) AccentEmerald.copy(alpha = 0.15f) else PrimaryIndigo.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Edit else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (isEditing) AccentEmerald else PrimaryIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEditing) strings.slashEditTitle else strings.slashNewTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                OutlinedTextField(
                    value = commandName,
                    onValueChange = {
                        commandName = it
                        errorMessage = null
                    },
                    label = { Text(strings.slashCmdNameLabel, fontSize = 12.sp) },
                    placeholder = { Text("/quota", fontSize = 12.sp) },
                    singleLine = true,
                    shape = AppDimensions.ShapeField,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text(strings.slashCmdTitleLabel, fontSize = 12.sp) },
                    placeholder = { Text(strings.slashCmdTitlePlaceholder, fontSize = 12.sp) },
                    singleLine = true,
                    shape = AppDimensions.ShapeField,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(strings.slashCmdDescLabel, fontSize = 12.sp) },
                    placeholder = { Text(strings.slashCmdDescPlaceholder, fontSize = 12.sp) },
                    singleLine = true,
                    shape = AppDimensions.ShapeField,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text(strings.slashCmdTemplateLabel, fontSize = 12.sp) },
                    placeholder = { Text("/quota", fontSize = 12.sp) },
                    singleLine = true,
                    shape = AppDimensions.ShapeField,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = AccentRose,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text(strings.cancel, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val cleanCmd = commandName.trim()
                            val cleanTitle = title.trim()
                            if (cleanCmd.isBlank()) {
                                errorMessage = appStrings.slashErrorEmptyName
                                return@Button
                            }
                            if (cleanTitle.isBlank()) {
                                errorMessage = appStrings.slashErrorEmptyTitle
                                return@Button
                            }
                            val formattedCmd = if (cleanCmd.startsWith("/")) cleanCmd else "/$cleanCmd"
                            val formattedExample = if (example.isNotBlank()) example.trim() else formattedCmd
                            val updatedItem = SlashCommandItem(
                                command = formattedCmd,
                                title = cleanTitle,
                                description = description.trim().ifBlank { cleanTitle },
                                example = formattedExample,
                                actionId = initialCommand?.actionId ?: "custom",
                                isCustom = initialCommand?.isCustom ?: true
                            )
                            onSave(initialCommand?.command, updatedItem)
                        },
                        shape = AppDimensions.ShapeButton
                    ) {
                        Text(strings.save, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
