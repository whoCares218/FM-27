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
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.PassingStyle
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.PossessionFocus
import com.footymanager.simulator.domain.model.Pressing
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.Tempo
import com.footymanager.simulator.domain.model.TrainingFocus
import com.footymanager.simulator.domain.model.Width
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
    onSetTactics: (Tactics) -> Unit,
    onSetTrainingFocus: (TrainingFocus) -> Unit,
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
                TacticalBoard(
                    formation = formation,
                    selection = career.selection,
                    byId = byId,
                    captainId = career.selection.captainId,
                    onSlotClick = { pickerSlot = it },
                    onSwapSlots = onSwapSlots
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

        // ---- Advanced instructions ----
        item {
            AdvancedInstructionsCard(tactics = tactics, onApply = onSetTactics)
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

/**
 * The interactive tactical board.
 *
 * Every token is draggable: dragging one token onto another swaps the two
 * players, and dragging a token into empty space nudges it to a nearby slot.
 * A single tap opens the picker for that position. The board shows each player's
 * rating, condition and role, and highlights anyone playing out of position.
 *
 * The gesture model is deliberately simple: on release we find the slot nearest
 * the token's centre and treat that as the drop target. This works reliably for
 * thumbs on a phone without needing precise hit-testing during the drag.
 */
@Composable
private fun TacticalBoard(
    formation: Formation,
    selection: com.footymanager.simulator.domain.model.TeamSelection,
    byId: Map<Long, Player>,
    captainId: Long?,
    onSlotClick: (Int) -> Unit,
    onSwapSlots: (Int, Int) -> Unit
) {
    val slots = remember(formation) {
        formation.coordinates.mapIndexed { index, point -> index to point }
    }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var hoverTarget by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(MaterialTheme.shapes.medium)
            .background(com.footymanager.simulator.ui.theme.PitchGreen)
    ) {
        val w = maxWidth.value
        val h = maxHeight.value

        // Pitch markings for a tactical-board look.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.90f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.22f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
        )
        // Penalty boxes at each end.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.46f)
                .height(h.dp * 0.14f)
                .border(1.dp, Color.White.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.46f)
                .height(h.dp * 0.14f)
                .border(1.dp, Color.White.copy(alpha = 0.18f))
        )

        slots.forEach { (index, point) ->
            val slot = selection.startingXi.firstOrNull { it.slotIndex == index }
            val player = slot?.let { byId[it.playerId] }
            val baseX = (point.x * w)
            // Flip so the attacking line sits at the top of the screen.
            val baseY = ((1f - point.y) * h)
            val isDragging = draggingIndex == index
            val isTarget = hoverTarget == index && draggingIndex != index

            TacticalToken(
                role = formation.roles[index],
                player = player,
                outOfPosition = slot?.outOfPosition == true,
                isCaptain = player?.id == captainId,
                isDragging = isDragging,
                isDropTarget = isTarget,
                modifier = Modifier
                    .offset(
                        x = (baseX - 30f).dp + (if (isDragging) dragOffset.x.dp else 0.dp),
                        y = (baseY - 34f).dp + (if (isDragging) dragOffset.y.dp else 0.dp)
                    )
                    .zIndex(if (isDragging) 2f else 1f)
                    .pointerInput(index) {
                        detectDragGestures(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                                hoverTarget = nearestSlot(
                                    baseX + dragOffset.x, baseY + dragOffset.y, slots, w, h
                                ).takeIf { it != index }
                            },
                            onDragEnd = {
                                val target = hoverTarget
                                if (target != null) onSwapSlots(index, target)
                                draggingIndex = null
                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                hoverTarget = null
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                hoverTarget = null
                            }
                        )
                    }
                    .clickable { onSlotClick(index) }
            )
        }
    }
}

/** Finds the slot whose pitch position is closest to a screen point. */
private fun nearestSlot(
    x: Float,
    y: Float,
    slots: List<Pair<Int, com.footymanager.simulator.domain.model.PitchPoint>>,
    width: Float,
    height: Float
): Int? {
    if (slots.isEmpty()) return null
    return slots.minByOrNull { (_, point) ->
        val sx = point.x * width
        val sy = (1f - point.y) * height
        val dx = sx - x
        val dy = sy - y
        dx * dx + dy * dy
    }?.first
}

/**
 * A single player token on the tactical board. The circular badge shows the
 * player's overall rating coloured by quality; the role tag, condition and
 * out-of-position flag sit beneath it.
 */
@Composable
private fun TacticalToken(
    role: com.footymanager.simulator.domain.model.SlotRole,
    player: Player?,
    outOfPosition: Boolean,
    isCaptain: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.12f else 1f,
        animationSpec = tween(120),
        label = "tokenScale"
    )
    val ringColor = when {
        isDropTarget -> StatColors.elite
        outOfPosition -> StatColors.poor
        player != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Column(
        modifier = modifier
            .width(60.dp)
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (player != null) StatColors.forRating(player.overall).copy(alpha = 0.92f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)
                    )
                    .border(
                        width = if (isDropTarget || outOfPosition) 2.dp else 1.5.dp,
                        color = ringColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player?.overall?.toString() ?: "+",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0B1220)
                )
            }
            if (isCaptain) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Captain",
                    tint = StatColors.elite,
                    modifier = Modifier
                        .size(14.dp)
                        .offset(x = 2.dp, y = (-2).dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = player?.name?.substringAfterLast(' ') ?: role.short,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (player != null) {
                "${role.short} · ${player.fitness}%"
            } else role.longName,
            style = MaterialTheme.typography.labelSmall,
            color = if (outOfPosition) StatColors.poor
            else Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
