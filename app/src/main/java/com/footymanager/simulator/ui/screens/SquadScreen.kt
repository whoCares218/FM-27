package com.footymanager.simulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.PositionCategory
import com.footymanager.simulator.ui.components.EmptyState
import com.footymanager.simulator.ui.components.FitnessBar
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.FormStrip
import com.footymanager.simulator.ui.components.MoraleDots
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.RatingBadge
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.theme.StatColors

enum class SquadSort(val label: String) {
    POSITION("Position"),
    RATING("Rating"),
    AGE("Age"),
    VALUE("Value"),
    WAGE("Wage"),
    FORM("Form"),
    FITNESS("Fitness"),
    MORALE("Morale"),
    GOALS("Goals")
}

/**
 * Squad list with sorting, filtering and the full player detail set. Tapping a
 * player opens their profile; the "lineup" section handles the starting XI.
 */
@Composable
fun SquadScreen(
    career: Career,
    onOpenPlayer: (Long) -> Unit,
    onAutoPick: () -> Unit
) {
    var sort by remember { mutableStateOf(SquadSort.POSITION) }
    var filter by remember { mutableStateOf<PositionCategory?>(null) }
    var showOnlyAvailable by remember { mutableStateOf(false) }

    val squad = remember(career.players, career.userClubId) { career.userSquad }

    val filtered = remember(squad, filter, showOnlyAvailable, sort) {
        squad
            .filter { filter == null || it.position.category == filter }
            .filter { !showOnlyAvailable || it.isAvailable }
            .let { list ->
                when (sort) {
                    SquadSort.POSITION -> list.sortedWith(
                        compareBy({ Position.sortOrder(it.position) }, { -it.overall })
                    )
                    SquadSort.RATING -> list.sortedByDescending { it.overall }
                    SquadSort.AGE -> list.sortedBy { it.age }
                    SquadSort.VALUE -> list.sortedByDescending { it.value }
                    SquadSort.WAGE -> list.sortedByDescending { it.wagePerWeek }
                    SquadSort.FORM -> list.sortedByDescending { it.form }
                    SquadSort.FITNESS -> list.sortedByDescending { it.fitness }
                    SquadSort.MORALE -> list.sortedByDescending { it.moraleScore }
                    SquadSort.GOALS -> list.sortedByDescending { it.seasonStats.goals }
                }
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Squad (${squad.size} players)") {
                Text(
                    text = "Auto-pick XI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onAutoPick)
                )
            }
        }

        // ---- Sort selector ----
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SquadSort.entries.toList(), key = { it.name }) { option ->
                    SelectorChip(
                        label = option.label,
                        selected = option == sort,
                        onClick = { sort = option }
                    )
                }
            }
        }

        // ---- Position filter ----
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    SelectorChip(
                        label = "All",
                        selected = filter == null,
                        onClick = { filter = null }
                    )
                }
                items(PositionCategory.entries.toList(), key = { it.name }) { category ->
                    SelectorChip(
                        label = categoryLabel(category),
                        selected = filter == category,
                        onClick = { filter = if (filter == category) null else category }
                    )
                }
                item {
                    SelectorChip(
                        label = "Available",
                        selected = showOnlyAvailable,
                        onClick = { showOnlyAvailable = !showOnlyAvailable }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Groups,
                    title = "No players match",
                    body = "Adjust the filter or sorting options to see squad members."
                )
            }
        }

        items(filtered, key = { it.id }) { player ->
            PlayerRow(
                player = player,
                isCaptain = career.selection.captainId == player.id,
                isStarter = career.selection.startingXi.any { it.playerId == player.id },
                isOnBench = player.id in career.selection.substitutes,
                onClick = { onOpenPlayer(player.id) }
            )
        }
    }
}

@Composable
fun SelectorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun PlayerRow(
    player: Player,
    isCaptain: Boolean,
    isStarter: Boolean,
    isOnBench: Boolean,
    onClick: () -> Unit
) {
    FmCard(onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingBadge(player.overall, size = 40.dp)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCaptain) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Captain",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PositionChip(player.position)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${player.age}y • ${player.nationality}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isStarter) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "XI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isOnBench) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "SUB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoraleDots(player.morale)
                    Spacer(Modifier.width(8.dp))
                    FitnessBar(player.fitness)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${player.fitness}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Availability problems take priority over everything else.
                if (player.isInjured) {
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocalHospital,
                            contentDescription = null,
                            tint = StatColors.bad,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${player.injury.type.label} • ${player.injury.weeksRemaining}w",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatColors.bad
                        )
                    }
                } else if (player.isSuspended) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "Suspended • ${player.suspensionWeeks} match(es)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Fmt.money(player.value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = "${player.seasonStats.goals}G ${player.seasonStats.assists}A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "avg ${Fmt.rating(player.seasonStats.averageRating)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatColors.forRating((player.seasonStats.averageRating * 10).toInt())
                )
            }
        }
    }
}

private fun categoryLabel(category: PositionCategory): String = when (category) {
    PositionCategory.GOALKEEPER -> "GK"
    PositionCategory.DEFENDER -> "DEF"
    PositionCategory.MIDFIELDER -> "MID"
    PositionCategory.FORWARD -> "ATT"
}
