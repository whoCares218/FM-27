package com.footymanager.simulator.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.engine.SelectionRepair
import com.footymanager.simulator.domain.model.Aggression
import com.footymanager.simulator.domain.model.BuildUp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CounterAttack
import com.footymanager.simulator.domain.model.Crossing
import com.footymanager.simulator.domain.model.DefensiveLine
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.IndividualInstruction
import com.footymanager.simulator.domain.model.MarkingStyle
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.PassingStyle
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayerDuty
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.PossessionFocus
import com.footymanager.simulator.domain.model.Pressing
import com.footymanager.simulator.domain.model.SetPieceTakers
import com.footymanager.simulator.domain.model.TacticalPreset
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.TeamSelection
import com.footymanager.simulator.domain.model.Tempo
import com.footymanager.simulator.domain.model.TrainingFocus
import com.footymanager.simulator.domain.model.Width
import com.footymanager.simulator.ui.components.FormationPitch
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmDropdown
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
    onSetTactics: (Tactics) -> Unit,
    onSetTrainingFocus: (TrainingFocus) -> Unit,
    onApplyPreset: (TacticalPreset) -> Unit,
    onSetWidth: (Width) -> Unit,
    onSetPressing: (Pressing) -> Unit,
    onSetPassing: (PassingStyle) -> Unit,
    onSetBuildUp: (BuildUp) -> Unit,
    onSetCounterAttack: (CounterAttack) -> Unit,
    onSetPossessionFocus: (PossessionFocus) -> Unit,
    onSetCrossing: (Crossing) -> Unit,
    onSetAggression: (Aggression) -> Unit,
    onSetIndividualInstruction: (Long, IndividualInstruction) -> Unit,
    onSetSetPieces: (SetPieceTakers) -> Unit,
    onAutoPick: () -> Unit,
    onAssignSlot: (Int, Long) -> Unit,
    onRemoveFromSlot: (Int) -> Unit,
    onSwapSlots: (Int, Int) -> Unit,
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---- Formation and Starting XI, visually connected in one card ----
        item {
            FmCard(padding = 12.dp) {
                FmDropdown(
                    label = "Formation",
                    options = Formation.all,
                    selected = formation,
                    onSelect = { onSetFormation(it.id) },
                    optionLabel = { it.name },
                    optionSupporting = { it.description }
                )
                Spacer(Modifier.height(10.dp))
                SectionHeader(
                    title = "Starting XI",
                    trailing = {
                        Text(
                            text = "${formation.name} · tap a player",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                FormationPitch(
                    formation = formation,
                    playerIds = selection.startingXi.associate { it.slotIndex to it.playerId },
                    playerName = { byId[it] },
                    selectedSlot = pickerSlot,
                    onSlotClick = { pickerSlot = it }
                )
                Spacer(Modifier.height(8.dp))
                FmSecondaryButton(text = "Auto-pick best XI", onClick = onAutoPick)
            }
        }

        // ---- Warnings ----
        if (warnings.isNotEmpty()) {
            item {
                FmCard(accent = StatColors.poor, padding = 10.dp) {
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
                    Spacer(Modifier.height(4.dp))
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

        // ---- Bench ----
        item {
            FmCard {
                SectionHeader("Bench (${selection.substitutes.size}/9)")
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

        // ---- Team instructions: presets first, then every lever ----
        item {
            FmCard {
                SectionHeader("Team instructions")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Start with a preset, then fine-tune below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TacticalPreset.entries.toList(), key = { it.name }) { preset ->
                        SelectorChip(
                            label = preset.label,
                            selected = false,
                            onClick = { onApplyPreset(preset) }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OptionSelector(
                    label = "Mentality",
                    options = Mentality.entries.toList(),
                    selected = tactics.mentality,
                    onSelect = onSetMentality,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Playing style",
                    options = PlayStyle.entries.toList(),
                    selected = tactics.style,
                    onSelect = onSetStyle,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Width",
                    options = Width.entries.toList(),
                    selected = tactics.width,
                    onSelect = onSetWidth,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Pressing",
                    options = Pressing.entries.toList(),
                    selected = tactics.pressing,
                    onSelect = onSetPressing,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Passing",
                    options = PassingStyle.entries.toList(),
                    selected = tactics.passingStyle,
                    onSelect = onSetPassing,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Build-up",
                    options = BuildUp.entries.toList(),
                    selected = tactics.buildUp,
                    onSelect = onSetBuildUp,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Counter-attack",
                    options = CounterAttack.entries.toList(),
                    selected = tactics.counterAttack,
                    onSelect = onSetCounterAttack,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Possession focus",
                    options = PossessionFocus.entries.toList(),
                    selected = tactics.possessionFocus,
                    onSelect = onSetPossessionFocus,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Crossing",
                    options = Crossing.entries.toList(),
                    selected = tactics.crossing,
                    onSelect = onSetCrossing,
                    optionLabel = { it.label }
                )
                Spacer(Modifier.height(12.dp))
                OptionSelector(
                    label = "Aggression",
                    options = Aggression.entries.toList(),
                    selected = tactics.aggression,
                    onSelect = onSetAggression,
                    optionLabel = { it.label }
                )
            }
        }

        // ---- Individual instructions ----
        item {
            IndividualInstructionsCard(
                career = career,
                onSetInstruction = onSetIndividualInstruction
            )
        }

        // ---- Set pieces ----
        item {
            SetPiecesCard(career = career, onSetTakers = onSetSetPieces)
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
 * Advanced team instructions. Each selector maps to a real multiplier in the
 * match engine (possession, chance volume, fouls, counter threat), so these are
 * genuine tactical levers rather than cosmetic options.
 */
@Composable
private fun AdvancedInstructionsCard(tactics: Tactics, onApply: (Tactics) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    FmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionHeader("Advanced instructions")
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Width",
                options = Width.entries.toList(),
                selected = tactics.width,
                onSelect = { onApply(tactics.copy(width = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Pressing",
                options = Pressing.entries.toList(),
                selected = tactics.pressing,
                onSelect = { onApply(tactics.copy(pressing = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Passing",
                options = PassingStyle.entries.toList(),
                selected = tactics.passingStyle,
                onSelect = { onApply(tactics.copy(passingStyle = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Build-up",
                options = BuildUp.entries.toList(),
                selected = tactics.buildUp,
                onSelect = { onApply(tactics.copy(buildUp = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Counter-attack",
                options = CounterAttack.entries.toList(),
                selected = tactics.counterAttack,
                onSelect = { onApply(tactics.copy(counterAttack = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Possession focus",
                options = PossessionFocus.entries.toList(),
                selected = tactics.possessionFocus,
                onSelect = { onApply(tactics.copy(possessionFocus = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Crossing",
                options = Crossing.entries.toList(),
                selected = tactics.crossing,
                onSelect = { onApply(tactics.copy(crossing = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Aggression",
                options = Aggression.entries.toList(),
                selected = tactics.aggression,
                onSelect = { onApply(tactics.copy(aggression = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Higher pressing and aggression win the ball back sooner but " +
                    "cost more fouls and cards. Direct, quick build-up creates more " +
                    "chances but concedes possession.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


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

/**
 * Per-player instructions. The manager picks a starter, then adjusts their duty,
 * marking and movement. Every change is fed straight to the match engine through
 * the player's individual modifier, so a "stay back" full back really does defend
 * more and attack less.
 */
@Composable
private fun IndividualInstructionsCard(
    career: Career,
    onSetInstruction: (Long, IndividualInstruction) -> Unit
) {
    val starters = career.selection.startingXi
        .sortedBy { it.slotIndex }
        .mapNotNull { career.player(it.playerId) }
    var selectedId by remember { mutableStateOf(starters.firstOrNull()?.id) }
    val selected = selectedId?.let { id -> starters.firstOrNull { it.id == id } }

    FmCard {
        SectionHeader("Individual instructions")
        Spacer(Modifier.height(6.dp))
        if (starters.isEmpty()) {
            Text(
                text = "Pick a starting XI first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FmCard
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(starters, key = { it.id }) { player ->
                SelectorChip(
                    label = player.name.substringAfterLast(' '),
                    selected = player.id == selectedId,
                    onClick = { selectedId = player.id }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (selected != null) {
            val instruction = career.tactics.playerInstructions[selected.id] ?: IndividualInstruction.DEFAULT
            Text(
                text = selected.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "DUTY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(PlayerDuty.entries.toList(), key = { it.name }) { duty ->
                    SelectorChip(
                        label = duty.label,
                        selected = instruction.duty == duty,
                        onClick = { onSetInstruction(selected.id, instruction.copy(duty = duty)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "MARKING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MarkingStyle.entries.toList(), key = { it.name }) { marking ->
                    SelectorChip(
                        label = marking.label,
                        selected = instruction.marking == marking,
                        onClick = { onSetInstruction(selected.id, instruction.copy(marking = marking)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow("Get forward", instruction.getForward) {
                onSetInstruction(selected.id, instruction.copy(getForward = it, stayBack = false))
            }
            ToggleRow("Stay back", instruction.stayBack) {
                onSetInstruction(selected.id, instruction.copy(stayBack = it, getForward = false))
            }
            ToggleRow("Take more risks", instruction.takeMoreRisks) {
                onSetInstruction(selected.id, instruction.copy(takeMoreRisks = it))
            }
            if (instruction != IndividualInstruction.DEFAULT) {
                Spacer(Modifier.height(8.dp))
                FmSecondaryButton(
                    text = "Reset to default",
                    onClick = { onSetInstruction(selected.id, IndividualInstruction.DEFAULT) }
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (checked) "On" else "Off",
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Designated penalty, free-kick, corner and long-throw takers. */
@Composable
private fun SetPiecesCard(career: Career, onSetTakers: (SetPieceTakers) -> Unit) {
    val starters = career.selection.startingXi
        .sortedBy { it.slotIndex }
        .mapNotNull { career.player(it.playerId) }
    val takers = career.tactics.setPieces

    FmCard {
        SectionHeader("Set pieces")
        Spacer(Modifier.height(8.dp))
        if (starters.isEmpty()) {
            Text(
                text = "Pick a starting XI first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FmCard
        }
        SetPieceRow(
            label = "Penalties",
            starters = starters,
            selectedId = takers.penaltyTakerId,
            onSelect = { onSetTakers(takers.copy(penaltyTakerId = it)) }
        )
        Spacer(Modifier.height(10.dp))
        SetPieceRow(
            label = "Free kicks",
            starters = starters,
            selectedId = takers.freeKickTakerId,
            onSelect = { onSetTakers(takers.copy(freeKickTakerId = it)) }
        )
        Spacer(Modifier.height(10.dp))
        SetPieceRow(
            label = "Corners",
            starters = starters,
            selectedId = takers.cornerTakerId,
            onSelect = { onSetTakers(takers.copy(cornerTakerId = it)) }
        )
        Spacer(Modifier.height(10.dp))
        SetPieceRow(
            label = "Long throws",
            starters = starters,
            selectedId = takers.longThrowTakerId,
            onSelect = { onSetTakers(takers.copy(longThrowTakerId = it)) }
        )
    }
}

@Composable
private fun SetPieceRow(
    label: String,
    starters: List<Player>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                SelectorChip(
                    label = "None",
                    selected = selectedId == null,
                    onClick = { onSelect(null) }
                )
            }
            items(starters, key = { it.id }) { player ->
                SelectorChip(
                    label = player.name.substringAfterLast(' '),
                    selected = player.id == selectedId,
                    onClick = { onSelect(player.id) }
                )
            }
        }
    }
}

