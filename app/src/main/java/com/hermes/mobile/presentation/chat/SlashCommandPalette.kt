package com.hermes.mobile.presentation.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.domain.model.SlashCommandItem
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo

fun getSlashCommandIcon(item: SlashCommandItem): ImageVector {
    if (item.isCustom) return Icons.Default.Bolt
    return when (item.actionId.lowercase()) {
        "reset" -> Icons.Default.RestartAlt
        "model" -> Icons.Default.SmartToy
        "clear" -> Icons.Default.DeleteSweep
        "status" -> Icons.Default.Speed
        "search" -> Icons.Default.Search
        "approval" -> Icons.Default.Security
        "clarify" -> Icons.Default.HelpOutline
        "help" -> Icons.Default.Info
        "quota" -> Icons.Default.PieChart
        else -> Icons.Default.Terminal
    }
}

@Composable
fun SlashCommandPalette(
    filterText: String,
    commands: List<SlashCommandItem>,
    onCommandSelected: (SlashCommandItem) -> Unit,
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query = filterText.removePrefix("/").trim().lowercase()
    val filteredCommands = if (query.isEmpty()) {
        commands
    } else {
        commands.filter {
            it.command.removePrefix("/").lowercase().contains(query) ||
            it.title.lowercase().contains(query) ||
            it.description.lowercase().contains(query)
        }
    }

    if (filteredCommands.isEmpty()) return

    Surface(
        shape = AppDimensions.ShapeCard,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = strings.slashPaletteTitle,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.slashPaletteTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${filteredCommands.size})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onOpenManager,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = strings.slashManagerTitle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp)
            ) {
                items(filteredCommands, key = { it.command }) { cmd ->
                    val icon = getSlashCommandIcon(cmd)
                    val tint = if (cmd.isCustom) AccentEmerald else PrimaryIndigo

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCommandSelected(cmd) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(AppDimensions.ShapeChip)
                                .background(tint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = cmd.title,
                                tint = tint,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = cmd.command,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = tint
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cmd.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
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

                        Icon(
                            imageVector = Icons.Default.NorthWest,
                            contentDescription = strings.slashPaletteSelect,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
