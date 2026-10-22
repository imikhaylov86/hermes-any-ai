package com.hermes.mobile.presentation.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.R
import com.hermes.mobile.domain.model.ConnectionState
import com.hermes.mobile.domain.model.SessionInfo
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import com.hermes.mobile.viewmodel.SessionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SessionTimeGroup {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    EARLIER
}

fun getSessionTimeGroup(timestampEpochMs: Long): SessionTimeGroup {
    if (timestampEpochMs <= 0) return SessionTimeGroup.EARLIER
    val now = Calendar.getInstance()
    val sessTime = Calendar.getInstance().apply { timeInMillis = timestampEpochMs }

    val isSameDay = now.get(Calendar.YEAR) == sessTime.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == sessTime.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) return SessionTimeGroup.TODAY

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == sessTime.get(Calendar.YEAR) &&
                      yesterday.get(Calendar.DAY_OF_YEAR) == sessTime.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return SessionTimeGroup.YESTERDAY

    val daysDiff = (now.timeInMillis - timestampEpochMs) / (1000L * 60 * 60 * 24)
    if (daysDiff < 7) return SessionTimeGroup.THIS_WEEK

    return SessionTimeGroup.EARLIER
}

fun formatSessionTime(timestampEpochMs: Long, yesterdayLabel: String = "Вчера"): String {
    if (timestampEpochMs <= 0) return ""
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestampEpochMs }

    val isSameDay = now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)

    if (isSameDay) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampEpochMs))
    }

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
                      yesterday.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) {
        return yesterdayLabel
    }

    val daysDiff = (now.timeInMillis - timestampEpochMs) / (1000L * 60 * 60 * 24)
    if (daysDiff < 7) {
        val sdf = SimpleDateFormat("EEE", Locale.getDefault())
        return sdf.format(Date(timestampEpochMs)).replaceFirstChar { it.uppercase() }
    }

    val sdf = SimpleDateFormat("d MMM", Locale.getDefault())
    return sdf.format(Date(timestampEpochMs))
}

@Composable
fun SessionGroupHeader(
    title: String,
    icon: ImageVector? = null,
    count: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            letterSpacing = 0.6.sp
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($count)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SessionDrawerContent(
    viewModel: SessionViewModel,
    activeSessionId: String,
    connectionState: ConnectionState,
    onSelectSession: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenTermuxStatus: () -> Unit = {},
    onActiveSessionDeleted: () -> Unit = {}
) {
    val sessions by viewModel.sessions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var sessionToRename by remember { mutableStateOf<SessionInfo?>(null) }
    var sessionToDelete by remember { mutableStateOf<SessionInfo?>(null) }
    var isArchiveExpanded by remember { mutableStateOf(false) }

    val (archivedSessions, nonArchivedSessions) = remember(sessions) {
        sessions.partition { it.isArchived }
    }
    val (pinnedSessions, unpinnedSessions) = remember(nonArchivedSessions) {
        nonArchivedSessions.partition { it.isPinned }
    }
    val groupedRecent = remember(unpinnedSessions) {
        unpinnedSessions.groupBy { getSessionTimeGroup(it.updatedAtEpochMs) }
    }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.84f),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppDimensions.ShapeCard)
                    .clickable {
                        onCloseDrawer()
                        onOpenTermuxStatus()
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppDimensions.ShapeChip)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "Hermes",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(AppDimensions.ShapeChip)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Hermes Any AI",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (connectionState == ConnectionState.READY) AccentEmerald else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (connectionState == ConnectionState.READY) strings.statusReadyTermux else strings.localAgentCardStatusWaiting,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (connectionState == ConnectionState.READY) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Кнопка создания нового чата
            Button(
                onClick = {
                    viewModel.createSession { newId ->
                        onSelectSession(newId)
                        onCloseDrawer()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = AppDimensions.ShapeButton
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.newChat, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.newChat, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Поле поиска
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text(strings.searchSessionsHint, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = strings.searchSessionsHint, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = strings.cancel, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AppDimensions.ShapeField
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            val isRu = strings.close == "Закрыть"
            val timeGroupTitles = listOf(
                SessionTimeGroup.TODAY to if (isRu) "Сегодня" else "Today",
                SessionTimeGroup.YESTERDAY to if (isRu) "Вчера" else "Yesterday",
                SessionTimeGroup.THIS_WEEK to if (isRu) "На этой неделе" else "This Week",
                SessionTimeGroup.EARLIER to if (isRu) "Ранее" else "Earlier"
            )

            // Список бесед
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    // Режим поиска
                    if (sessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.noSessionsFound,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(sessions, key = { it.id }) { session ->
                            SessionRowItem(
                                session = session,
                                isActive = session.id == activeSessionId,
                                onSelect = {
                                    onSelectSession(session.id)
                                    onCloseDrawer()
                                },
                                onTogglePin = { viewModel.togglePin(session.id) },
                                onToggleArchive = { viewModel.toggleArchive(session.id) },
                                onRename = { sessionToRename = session },
                                onExport = { viewModel.exportSessionMarkdown(context, session.id) },
                                onDelete = { sessionToDelete = session }
                            )
                        }
                    }
                } else {
                    // Группированный режим по датам

                    // 1. Закреплённые беседы (Pinned)
                    if (pinnedSessions.isNotEmpty()) {
                        item(key = "hdr_pinned") {
                            SessionGroupHeader(
                                title = strings.sectionPinned,
                                icon = Icons.Default.PushPin,
                                count = pinnedSessions.size
                            )
                        }
                        items(pinnedSessions, key = { it.id }) { session ->
                            SessionRowItem(
                                session = session,
                                isActive = session.id == activeSessionId,
                                onSelect = {
                                    onSelectSession(session.id)
                                    onCloseDrawer()
                                },
                                onTogglePin = { viewModel.togglePin(session.id) },
                                onToggleArchive = { viewModel.toggleArchive(session.id) },
                                onRename = { sessionToRename = session },
                                onExport = { viewModel.exportSessionMarkdown(context, session.id) },
                                onDelete = { sessionToDelete = session }
                            )
                        }
                    }

                    // 2. Недавние группы по датам
                    timeGroupTitles.forEach { (group, title) ->
                        val groupList = groupedRecent[group]
                        if (!groupList.isNullOrEmpty()) {
                            item(key = "hdr_${group.name}") {
                                SessionGroupHeader(title = title, count = groupList.size)
                            }
                            items(groupList, key = { it.id }) { session ->
                                SessionRowItem(
                                    session = session,
                                    isActive = session.id == activeSessionId,
                                    onSelect = {
                                        onSelectSession(session.id)
                                        onCloseDrawer()
                                    },
                                    onTogglePin = { viewModel.togglePin(session.id) },
                                    onToggleArchive = { viewModel.toggleArchive(session.id) },
                                    onRename = { sessionToRename = session },
                                    onExport = { viewModel.exportSessionMarkdown(context, session.id) },
                                    onDelete = { sessionToDelete = session }
                                )
                            }
                        }
                    }

                    // 3. Архив бесед (Collapsible)
                    if (archivedSessions.isNotEmpty()) {
                        item(key = "hdr_archive") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = AppDimensions.ShapeField,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isArchiveExpanded = !isArchiveExpanded }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Archive,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = strings.sectionArchived,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = AppDimensions.ShapeBadge,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "${archivedSessions.size}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isArchiveExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (isArchiveExpanded) {
                            items(archivedSessions, key = { it.id }) { session ->
                                SessionRowItem(
                                    session = session,
                                    isActive = session.id == activeSessionId,
                                    onSelect = {
                                        onSelectSession(session.id)
                                        onCloseDrawer()
                                    },
                                    onTogglePin = { viewModel.togglePin(session.id) },
                                    onToggleArchive = { viewModel.toggleArchive(session.id) },
                                    onRename = { sessionToRename = session },
                                    onExport = { viewModel.exportSessionMarkdown(context, session.id) },
                                    onDelete = { sessionToDelete = session }
                                )
                            }
                        }
                    }
                }
            }

            // Footer: Настройки и диагностика
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppDimensions.ShapeField)
                    .clickable {
                        onCloseDrawer()
                        onOpenSettings()
                    }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = strings.settingsTitle,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(strings.settingsTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(strings.settingsSubtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // Диалог переименования беседы
    if (sessionToRename != null) {
        val target = sessionToRename!!
        RenameSessionDialog(
            currentTitle = target.title,
            onRename = { newTitle ->
                viewModel.renameSession(target.id, newTitle)
                sessionToRename = null
            },
            onDismiss = { sessionToRename = null }
        )
    }

    // Диалог подтверждения удаления беседы
    if (sessionToDelete != null) {
        val target = sessionToDelete!!
        val isCurrent = target.id == activeSessionId
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(strings.deleteChatConfirm, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("«${target.title}»") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(target.id) {
                            if (isCurrent) {
                                onActiveSessionDeleted()
                            }
                        }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text(strings.delete, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { sessionToDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
fun SessionRowItem(
    session: SessionInfo,
    isActive: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isRu = strings.close == "Закрыть"
    val yesterdayLabel = if (isRu) "Вчера" else "Yesterday"
    val formattedTime = remember(session.updatedAtEpochMs, isRu) { formatSessionTime(session.updatedAtEpochMs, yesterdayLabel) }

    Surface(
        onClick = onSelect,
        shape = AppDimensions.ShapeField,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (session.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = session.title,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (formattedTime.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formattedTime,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (session.lastMessagePreview.isNotBlank()) {
                        Text(
                            text = session.lastMessagePreview,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (session.isPinned) strings.unpinChat else strings.pinChat, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.renameChat, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.shareChatMarkdown, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        onClick = {
                            showMenu = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (session.isArchived) strings.unarchiveChat else strings.archiveChat, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        onClick = {
                            showMenu = false
                            onToggleArchive()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    DropdownMenuItem(
                        text = { Text(strings.delete, fontSize = 13.sp, color = AccentRose) },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
