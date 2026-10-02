package com.footymanager.simulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.engine.SelectionRepair
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.DefensiveLine
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.Tempo
import com.footymanager.simulator.domain.model.TrainingFocus
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.FitnessBar
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.OptionSelector
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.RatingBadge
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.theme.StatColors

/**
 * Tactics, formation and team selection on one screen.
 *
 * The pitch view is a real interactive control: tapping a position opens a picker
 * of eligible players, and the resulting XI is what the match engine uses.
 */
@Composable
fun TacticsScreen(
    career: Career,
    onSetFormation: (String) -> Unit,
    onSetMentality: (Mentality) -> Unit,
    onSetStyle: (PlayStyle) -> Unit,
    onSetDefensiveLine: (DefensiveLine) -> Unit,
    onSetTempo: (Tempo) -> Unit,
    onSetTrainingFocus: (TrainingFocus) -> Unit,
    onAutoPick: () -> Unit,
    onAssignSlot: (Int, Long) -> Unit,
    onRemoveFromSlot: (Int) -> Unit,
    onSetCaptain: (Long?) -> Unit,
    onToggleSubstitute: (Long) -> Unit
) {
    val tactics = career.tactics
    val formation = tactics.formation
    val squad = career.userSquad
    val byId = remember(squad) { squad.associateBy { it.id } }
    val selection = career.selection
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

    val warnings = remember(selection, squad, formation) {
        SelectionRepair.warnings(selection, squad, formation)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Formation ----
        item {
            FmCard {
                SectionHeader("Formation")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Formation.all, key = { it.id }) { option ->
                        val selected = option.id == formation.id
                        Column(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onSetFormation(option.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = formation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- Warnings ----
        if (warnings.isNotEmpty()) {
            item {
                FmCard(accent = StatColors.poor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = StatColors.poor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Selection issues",
                            style = MaterialTheme.typography.labelLarge,
                            color = StatColors.poor
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    warnings.forEach { warning ->
                        Text(
                            text = "• $warning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---- Pitch ----
        item {
            FmCard(padding = 10.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Starting XI")
                    Text(
                        text = "Tap a position to change it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                PitchView(
                    formation = formation,
                    selection = career.selection,
                    byId = byId,
                    captainId = career.selection.captainId,
                    onSlotClick = { pickerSlot = it }
                )
            }
        }

        // ---- Selection actions ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FmSecondaryButton(
                    text = "Auto-pick best XI",
                    onClick = onAutoPick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ---- Tactical settings ----
        item {
            FmCard {
                SectionHeader("Mentality")
                Spacer(Modifier.height(8.dp))
                OptionSelector(
                    label = "Approach",
                    options = Mentality.entries.toList(),
                    selected = tactics.mentality,
                    onSelect = onSetMentality,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tactics.mentality.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Playing style")
                Spacer(Modifier.height(8.dp))
                OptionSelector(
                    label = "Style",
                    options = PlayStyle.entries.toList(),
                    selected = tactics.style,
                    onSelect = onSetStyle,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tactics.style.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Shape")
                Spacer(Modifier.height(8.dp))
                OptionSelector(
                    label = "Defensive line",
                    options = DefensiveLine.entries.toList(),
                    selected = tactics.defensiveLine,
                    onSelect = onSetDefensiveLine,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Tempo",
                    options = Tempo.entries.toList(),
                    selected = tactics.tempo,
                    onSelect = onSetTempo,
                    optionLabel = { it.label }
                )
            }
        }

        // ---- Captain ----
        item {
            FmCard {
                SectionHeader("Captain")
                Spacer(Modifier.height(8.dp))
                val currentCaptain = selection.captainId?.let { byId[it] }
                Text(
                    text = currentCaptain?.name ?: "No captain selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        selection.startingXi.mapNotNull { byId[it.playerId] },
                        key = { it.id }
                    ) { player ->
                        SelectorChip(
                            label = player.name.substringAfterLast(' '),
                            selected = player.id == selection.captainId,
                            onClick = { onSetCaptain(player.id) }
                        )
                    }
                }
            }
        }

        // ---- Substitutes ----
        item {
            FmCard {
                SectionHeader("Substitutes (${selection.substitutes.size}/9)")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap a squad player below to add or remove them from the bench.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                val bench = selection.substitutes.mapNotNull { byId[it] }
                if (bench.isEmpty()) {
                    Text(
                        text = "No substitutes selected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                bench.forEach { player ->
                    BenchRow(player = player, onRemove = { onToggleSubstitute(player.id) })
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(6.dp))
                SectionHeader("Available squad")
                Spacer(Modifier.height(6.dp))
                val notSelected = squad.filter { player ->
                    selection.startingXi.none { it.playerId == player.id } &&
                        player.id !in selection.substitutes
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(notSelected, key = { it.id }) { player ->
                        SelectorChip(
                            label = "${player.name.substringAfterLast(' ')} ${player.position.short}",
                            selected = false,
                            onClick = { onToggleSubstitute(player.id) }
                        )
                    }
                }
            }
        }

        // ---- Training ----
        item {
            FmCard {
                SectionHeader("Weekly training focus")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TrainingFocus.entries.toList(), key = { it.name }) { focus ->
                        SelectorChip(
                            label = focus.label,
                            selected = focus == career.trainingFocus,
                            onClick = { onSetTrainingFocus(focus) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = career.trainingFocus.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ---- Player picker ----
    val slot = pickerSlot
    if (slot != null) {
        PlayerPickerDialog(
            slotIndex = slot,
            formation = formation,
            squad = squad,
            onDismiss = { pickerSlot = null },
            onPick = { playerId ->
                onAssignSlot(slot, playerId)
                pickerSlot = null
            },
            onClear = {
                onRemoveFromSlot(slot)
                pickerSlot = null
            }
        )
    }
}

/**
 * Vertical pitch with positioned slots. Uses weighted rows so it scales cleanly
 * from small phones to tablets without any hard-coded pixel positions.
 */
@Composable
private fun PitchView(
    formation: Formation,
    selection: com.footymanager.simulator.domain.model.TeamSelection,
    byId: Map<Long, Player>,
    captainId: Long?,
    onSlotClick: (Int) -> Unit
) {
    // Group slot indices into the pitch's horizontal lines, from defence upward.
    val lines = remember(formation) { formation.pitchLines() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(MaterialTheme.shapes.medium)
            .background(com.footymanager.simulator.ui.theme.PitchGreen)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        lines.reversed().forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                line.forEach { slotIndex ->
                    val slot = selection.startingXi.firstOrNull { it.slotIndex == slotIndex }
                    val player = slot?.let { byId[it.playerId] }
                    PitchSlot(
                        role = formation.roles[slotIndex],
                        player = player,
                        outOfPosition = slot?.outOfPosition == true,
                        isCaptain = player?.id == captainId,
                        onClick = { onSlotClick(slotIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PitchSlot(
    role: com.footymanager.simulator.domain.model.SlotRole,
    player: Player?,
    outOfPosition: Boolean,
    isCaptain: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(62.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (player != null) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                    )
                    .border(
                        width = if (outOfPosition) 2.dp else 1.dp,
                        color = when {
                            outOfPosition -> StatColors.poor
                            player != null -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player?.overall?.toString() ?: "+",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (player != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCaptain) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Captain",
                    tint = StatColors.elite,
                    modifier = Modifier
                        .size(13.dp)
                        .offsetAbove()
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = player?.name?.substringAfterLast(' ') ?: role.short,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (player != null) {
                "${player.fitness}%"
            } else role.longName,
            style = MaterialTheme.typography.labelSmall,
            color = if (player != null && player.fitness < 65) StatColors.poor
            else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Modifier.offsetAbove(): Modifier = this.then(
    androidx.compose.ui.Modifier.padding(bottom = 22.dp, start = 20.dp)
)

@Composable
private fun BenchRow(player: Player, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PositionChip(player.position)
        Spacer(Modifier.width(8.dp))
        Text(
            text = player.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${player.overall}",
            style = MaterialTheme.typography.titleSmall,
            color = StatColors.forRating(player.overall)
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = "Remove from bench",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onRemove)
        )
    }
}

/** Modal list of players eligible for a slot, best first. */
@Composable
private fun PlayerPickerDialog(
    slotIndex: Int,
    formation: Formation,
    squad: List<Player>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClear: () -> Unit
) {
    val role = formation.roles.getOrNull(slotIndex) ?: return
    val candidates = remember(squad, slotIndex) {
        squad.sortedWith(
            compareByDescending<Player> {
                com.footymanager.simulator.domain.model.PositionSuitability.factor(it.position, role.naturalPosition)
            }.thenByDescending { it.overall }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Select ${role.longName}")
                Text(
                    text = "Best players for this position are listed first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(candidates, key = { it.id }) { player ->
                    val suitability = com.footymanager.simulator.domain.model.PositionSuitability
                        .factor(player.position, role.naturalPosition)
                    val available = player.isAvailable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(enabled = available) { onPick(player.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RatingBadge(player.overall, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = player.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (available) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PositionChip(player.position)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (available) {
                                        if (suitability >= 1.0) "Natural fit"
                                        else "${(suitability * 100).toInt()}% suited"
                                    } else {
                                        if (player.isInjured) "Injured (${player.injury.weeksRemaining}w)"
                                        else "Suspended"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (available) {
                                        if (suitability >= 1.0) StatColors.elite else StatColors.poor
                                    } else StatColors.bad
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            FitnessBar(player.fitness, width = 60.dp)
                        }
                        Text(
                            text = Fmt.money(player.value),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClear) { Text("Clear slot") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
