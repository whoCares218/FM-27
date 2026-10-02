package com.footymanager.simulator.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.footymanager.simulator.domain.engine.MatchPhase
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.DefensiveLine
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.MatchEventType
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.Tempo
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.FormStrip
import com.footymanager.simulator.ui.components.InfoPill
import com.footymanager.simulator.ui.components.OptionSelector
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.components.StatComparisonRow
import com.footymanager.simulator.ui.components.fraction
import com.footymanager.simulator.ui.theme.StatColors
import com.footymanager.simulator.viewmodel.MatchDayState
import com.footymanager.simulator.viewmodel.MatchFeedItem
import com.footymanager.simulator.viewmodel.MatchMode

/**
 * The live match-day experience.
 *
 * Before kick-off this is a professional preview: teams, competition, venue,
 * form, lineups and bench. Nothing about the result exists yet. Pressing START
 * MATCH hands control to the engine, which is advanced a minute at a time, so the
 * score, clock, statistics and event feed all update as the game unfolds.
 *
 * At half time the engine stops and this screen becomes a management dashboard:
 * the manager may change tactics, formation and personnel with no time limit
 * before continuing the second half.
 */
@Composable
fun MatchDayScreen(
    career: Career,
    matchDay: MatchDayState,
    onBack: () -> Unit,
    onStart: (MatchMode) -> Unit,
    onContinueSecondHalf: () -> Unit,
    onContinueExtraTime: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onMakeLiveSub: (Long, Long) -> Unit,
    onPlanSub: (Long, Long) -> Unit,
    onCancelSub: (Long) -> Unit,
    onApplyLiveTactics: (Tactics) -> Unit,
    onContinueAfterMatch: () -> Unit
) {
    when {
        matchDay.isPlayed -> FinishedMatchView(matchDay, career, onContinueAfterMatch)
        !matchDay.started -> PreMatchView(career, matchDay, onBack, onStart)
        matchDay.awaitingHalfTime -> HalfTimeView(
            career, matchDay, onBack, onContinueSecondHalf, onPlanSub, onCancelSub,
            onMakeLiveSub, onApplyLiveTactics
        )
        else -> LiveMatchView(
            career, matchDay, onBack, onPause, onResume, onMakeLiveSub, onContinueExtraTime
        )
    }
}

// ------------------------------------------------------------------ pre-match

@Composable
private fun PreMatchView(
    career: Career,
    matchDay: MatchDayState,
    onBack: () -> Unit,
    onStart: (MatchMode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchHeader(career, matchDay, onBack) }

        item {
            FmCard(accent = MaterialTheme.colorScheme.primary, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamColumn(career.userClub, "You", true, Modifier.weight(1f))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(96.dp)
                    ) {
                        Text(
                            text = "vs",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (matchDay.isHome) "HOME" else "AWAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TeamColumn(matchDay.opponent, "Opponent", false, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                MatchInfoRow(career, matchDay)
            }
        }

        item {
            FmCard {
                SectionHeader("Match preview")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell(
                        "Difficulty",
                        matchDifficulty(career, matchDay.opponent),
                        valueColor = difficultyColor(matchDifficulty(career, matchDay.opponent))
                    )
                    StatCell(
                        "Their position",
                        career.positionOf(matchDay.opponent.id, matchDay.opponent.leagueId)
                            .let { if (it == 0) "-" else "$it" }
                    )
                    StatCell("Their strength", "${matchDay.opponent.reputation}")
                    StatCell("Your formation", matchDay.userFormationName)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Your form",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    FormStrip(
                        results = career.recentForm(career.userClubId)
                            .map { formCharFor(career, it, career.userClubId) },
                        size = 16.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Theirs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    FormStrip(
                        results = career.recentForm(matchDay.opponent.id)
                            .map { formCharFor(career, it, matchDay.opponent.id) },
                        size = 16.dp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoPill("Your XI: ${career.selection.startingXi.size}/11")
                    InfoPill("Opponent: ${matchDay.opponentFormationName}")
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Your starting XI")
                Spacer(Modifier.height(10.dp))
                LineupList(career, career.selection.startingXi.map { it.playerId })
            }
        }

        if (matchDay.pendingOtherFixtures.isNotEmpty()) {
            item {
                FmCard {
                    SectionHeader("Also this matchday")
                    Spacer(Modifier.height(8.dp))
                    matchDay.pendingOtherFixtures.forEach { fixture ->
                        val opponent = career.clubOrThrow(fixture.opponentOf(career.userClubId))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InfoPill(fixture.competition.shortLabel)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (fixture.isHomeFor(career.userClubId)) "vs ${opponent.name}"
                                else "at ${opponent.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            FmPrimaryButton(
                text = "Start Match",
                onClick = { onStart(MatchMode.PLAY) },
                icon = Icons.Filled.PlayArrow
            )
            Spacer(Modifier.height(8.dp))
            FmSecondaryButton(
                text = "Quick Sim",
                onClick = { onStart(MatchMode.QUICK) },
                icon = Icons.Outlined.FastForward
            )
            Spacer(Modifier.height(8.dp))
            FmSecondaryButton(text = "Back", onClick = onBack)
        }
    }
}

@Composable
private fun MatchInfoRow(career: Career, matchDay: MatchDayState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = matchDay.match.date?.display() ?: "Date TBC",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = career.userClub.stadiumName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- live match

@Composable
private fun LiveMatchView(
    career: Career,
    matchDay: MatchDayState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onMakeLiveSub: (Long, Long) -> Unit,
    onContinueExtraTime: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchHeader(career, matchDay, onBack) }
        item { LiveScoreboard(career, matchDay) }

        if (matchDay.awaitingExtraTime) {
            item {
                FmCard(accent = StatColors.average) {
                    SectionHeader("Extra time needed")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "The tie is level. Play extra time to find a winner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    FmPrimaryButton(
                        text = "Play Extra Time",
                        onClick = onContinueExtraTime,
                        icon = Icons.Filled.PlayArrow
                    )
                }
            }
        }

        item {
            FmCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader("Live feed")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (matchDay.simulating) {
                            Text(
                                text = "● LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatColors.bad,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val progress by animateFloatAsState(
                    targetValue = when (matchDay.phase) {
                        MatchPhase.FIRST_HALF -> matchDay.minute / 90f
                        MatchPhase.SECOND_HALF -> matchDay.minute / 90f
                        MatchPhase.FINISHED -> 1f
                        else -> 0.5f
                    },
                    label = "matchProgress"
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (matchDay.feed.isEmpty()) {
                    Text(
                        text = "Kick off imminent...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(matchDay.feed.size) {
                        if (matchDay.feed.isNotEmpty()) {
                            listState.animateScrollToItem(matchDay.feed.lastIndex)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(matchDay.feed) { item -> FeedRow(item) }
                    }
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Match statistics")
                Spacer(Modifier.height(12.dp))
                StatBlock(career, matchDay)
            }
        }

        item {
            FmCard {
                SectionHeader("Substitutions")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${matchDay.substitutionsMade} made · ${3 - matchDay.substitutionWindowsUsed} windows left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (matchDay.simulating) {
                    FmSecondaryButton(
                        text = "Pause to manage",
                        onClick = onPause,
                        icon = Icons.Filled.Pause
                    )
                } else {
                    LiveSubstitutionCard(career, matchDay, onMakeLiveSub)
                    Spacer(Modifier.height(8.dp))
                    FmPrimaryButton(
                        text = "Resume",
                        onClick = onResume,
                        icon = Icons.Filled.PlayArrow
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveScoreboard(career: Career, matchDay: MatchDayState) {
    val userLeading = matchDay.userGoals > matchDay.opponentGoals
    FmCard(accent = MaterialTheme.colorScheme.primary, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamColumn(career.userClub, "You", true, Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(104.dp)
            ) {
                Text(
                    text = "${matchDay.userGoals} - ${matchDay.opponentGoals}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = matchDay.clockLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (matchDay.simulating) StatColors.bad else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            TeamColumn(matchDay.opponent, "Opponent", false, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = matchDay.match.competitionLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (userLeading) "Leading" else if (matchDay.userGoals == matchDay.opponentGoals) "Level" else "Behind",
                style = MaterialTheme.typography.labelSmall,
                color = if (userLeading) StatColors.elite else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ------------------------------------------------------------------- half time

@Composable
private fun HalfTimeView(
    career: Career,
    matchDay: MatchDayState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onPlanSub: (Long, Long) -> Unit,
    onCancelSub: (Long) -> Unit,
    onMakeLiveSub: (Long, Long) -> Unit,
    onApplyLiveTactics: (Tactics) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchHeader(career, matchDay, onBack) }
        item {
            FmCard(accent = StatColors.average, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamColumn(career.userClub, "You", true, Modifier.weight(1f))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(104.dp)
                    ) {
                        Text(
                            text = "${matchDay.userGoals} - ${matchDay.opponentGoals}",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "HALF TIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatColors.average,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TeamColumn(matchDay.opponent, "Opponent", false, Modifier.weight(1f))
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Half-time dashboard")
                Spacer(Modifier.height(12.dp))
                StatBlock(career, matchDay)
            }
        }

        item {
            FmCard {
                SectionHeader("Your XI at the break")
                Spacer(Modifier.height(10.dp))
                LineupList(career, matchDay.homeOnPitch.ifEmpty { career.selection.startingXi.map { it.playerId } })
            }
        }

        item {
            HalfTimeTacticsCard(career, onApplyLiveTactics)
        }

        item {
            HalfTimeChangesCard(career, matchDay, onPlanSub, onCancelSub, onMakeLiveSub)
        }

        item {
            FmPrimaryButton(
                text = "Continue Second Half",
                onClick = onContinue,
                icon = Icons.Filled.PlayArrow
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Take as long as you need. The clock is stopped.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HalfTimeChangesCard(
    career: Career,
    matchDay: MatchDayState,
    onPlanSub: (Long, Long) -> Unit,
    onCancelSub: (Long) -> Unit,
    onMakeLiveSub: (Long, Long) -> Unit
) {
    val byId = career.userSquad.associateBy { it.id }
    val onPitchIds = matchDay.homeOnPitch.ifEmpty { career.selection.startingXi.map { it.playerId } }
    val benchIds = career.selection.substitutes
    val planned = matchDay.plannedSubstitutions

    FmCard {
        SectionHeader("Substitutions")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Up to 5 changes. Half-time changes are free of a window.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        planned.forEach { sub ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${byId[sub.playerOnId]?.name ?: "?"} for ${byId[sub.playerOffId]?.name ?: "?"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Remove",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCancelSub(sub.playerOnId) }
                        .padding(6.dp)
                )
            }
        }

        if (planned.size < 5) {
            SubstitutionPicker(
                onPitch = onPitchIds.mapNotNull { byId[it] },
                bench = benchIds.mapNotNull { byId[it] },
                onAdd = onPlanSub
            )
        }
    }
}

@Composable
private fun HalfTimeTacticsCard(career: Career, onApplyLiveTactics: (Tactics) -> Unit) {
    val tactics = career.tactics
    var expanded by remember { mutableStateOf(true) }
    FmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionHeader("Change tactics")
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "FORMATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Formation.all, key = { it.id }) { option ->
                    val selected = option.id == tactics.formationId
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                onApplyLiveTactics(tactics.copy(formationId = option.id))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OptionSelector(
                label = "Mentality",
                options = Mentality.entries.toList(),
                selected = tactics.mentality,
                onSelect = { onApplyLiveTactics(tactics.copy(mentality = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Style",
                options = PlayStyle.entries.toList(),
                selected = tactics.style,
                onSelect = { onApplyLiveTactics(tactics.copy(style = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Defensive line",
                options = DefensiveLine.entries.toList(),
                selected = tactics.defensiveLine,
                onSelect = { onApplyLiveTactics(tactics.copy(defensiveLine = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(12.dp))
            OptionSelector(
                label = "Tempo",
                options = Tempo.entries.toList(),
                selected = tactics.tempo,
                onSelect = { onApplyLiveTactics(tactics.copy(tempo = it)) },
                optionLabel = { it.label }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Changes take effect for the second half.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LiveSubstitutionCard(
    career: Career,
    matchDay: MatchDayState,
    onMakeLiveSub: (Long, Long) -> Unit
) {
    val byId = career.userSquad.associateBy { it.id }
    val onPitch = matchDay.homeOnPitch.mapNotNull { byId[it] }
    val bench = career.selection.substitutes.mapNotNull { byId[it] }
    FmCard {
        SectionHeader("Make a change")
        Spacer(Modifier.height(6.dp))
        SubstitutionPicker(onPitch = onPitch, bench = bench, onAdd = onMakeLiveSub)
    }
}

@Composable
private fun SubstitutionPicker(
    onPitch: List<Player>,
    bench: List<Player>,
    onAdd: (Long, Long) -> Unit
) {
    var offId by remember { mutableStateOf<Long?>(null) }
    var onId by remember { mutableStateOf<Long?>(null) }

    Text(
        text = "Bring off",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
    LazyRowOfPills(
        players = onPitch,
        selectedId = offId,
        onSelect = { offId = if (offId == it) null else it }
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Bring on",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
    LazyRowOfPills(
        players = bench,
        selectedId = onId,
        onSelect = { onId = if (onId == it) null else it }
    )
    Spacer(Modifier.height(10.dp))
    FmSecondaryButton(
        text = "Confirm change",
        onClick = {
            val off = offId
            val on = onId
            if (off != null && on != null) {
                onAdd(off, on)
                offId = null
                onId = null
            }
        }
    )
}

@Composable
private fun LazyRowOfPills(
    players: List<Player>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(players, key = { it.id }) { player ->
            val selected = selectedId == player.id
            val color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = if (selected) 0.22f else 0.10f))
                    .clickable { onSelect(player.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = player.surname(),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    maxLines = 1
                )
                Text(
                    text = "${player.position.short} · ${player.overall}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------------- finished

@Composable
private fun FinishedMatchView(
    matchDay: MatchDayState,
    career: Career,
    onContinue: () -> Unit
) {
    val result = matchDay.result
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchHeader(career, matchDay, null) }

        item {
            FmCard(accent = MaterialTheme.colorScheme.primary, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamColumn(career.userClub, "You", true, Modifier.weight(1f))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(104.dp)
                    ) {
                        Text(
                            text = "${matchDay.userGoals} - ${matchDay.opponentGoals}",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "FULL TIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TeamColumn(matchDay.opponent, "Opponent", false, Modifier.weight(1f))
                }
                if (result?.penaltyShootoutHome != null && result.penaltyShootoutAway != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "After penalties: ${result.penaltyShootoutHome} - ${result.penaltyShootoutAway}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (result != null && result.momentum.isNotEmpty()) {
            item {
                FmCard {
                    SectionHeader("Momentum")
                    Spacer(Modifier.height(10.dp))
                    MomentumBar(result.momentum, result.homeClubId == career.userClubId)
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Match statistics")
                Spacer(Modifier.height(12.dp))
                StatBlock(career, matchDay)
            }
        }

        item {
            FmCard {
                SectionHeader("Player ratings")
                Spacer(Modifier.height(10.dp))
                val sorted = result?.playerRatings.orEmpty().sortedByDescending { it.rating }
                sorted.forEach { rating ->
                    val isUser = career.userSquad.any { it.id == rating.playerId }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = rating.playerName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (rating.goals > 0) {
                            Icon(
                                Icons.Outlined.SportsSoccer,
                                contentDescription = "Scored",
                                tint = StatColors.elite,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "x${rating.goals} ",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatColors.elite
                            )
                        }
                        if (rating.isManOfTheMatch) {
                            Text(
                                text = "MOTM  ",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatColors.average,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = com.footymanager.simulator.ui.components.Fmt.rating(rating.rating),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatColors.forRating((rating.rating * 10).toInt())
                        )
                    }
                }
            }
        }

        item {
            FmPrimaryButton(text = "Continue", onClick = onContinue)
        }
    }
}

// ------------------------------------------------------------------- shared UI

@Composable
private fun MatchHeader(career: Career, matchDay: MatchDayState, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            Spacer(Modifier.width(36.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = matchDay.match.competitionLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Matchday ${matchDay.match.matchday}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (matchDay.mode == MatchMode.QUICK) InfoPill("Quick Sim")
    }
}

@Composable
private fun FeedRow(item: MatchFeedItem) {
    val accent = eventAccent(item.type)
    val isMilestone = item.type == MatchEventType.HALF_TIME ||
        item.type == MatchEventType.FULL_TIME ||
        item.type == MatchEventType.KICK_OFF
    val highlight = item.type == MatchEventType.GOAL
    val bg by animateColorAsState(
        targetValue = when {
            isMilestone -> MaterialTheme.colorScheme.surfaceVariant
            highlight -> accent.copy(alpha = 0.16f)
            item.isUserClub -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        label = "feedBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.minute,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.width(42.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBlock(career: Career, matchDay: MatchDayState) {
    val user = matchDay.userStats
    val opp = matchDay.opponentStats
    StatComparisonRow(
        label = "Possession",
        homeValue = "${user.possession}%",
        awayValue = "${opp.possession}%",
        homeFraction = user.possession / 100f
    )
    Spacer(Modifier.height(12.dp))
    StatComparisonRow(
        label = "Shots",
        homeValue = "${user.shots}",
        awayValue = "${opp.shots}",
        homeFraction = fraction(user.shots, opp.shots)
    )
    Spacer(Modifier.height(12.dp))
    StatComparisonRow(
        label = "On target",
        homeValue = "${user.shotsOnTarget}",
        awayValue = "${opp.shotsOnTarget}",
        homeFraction = fraction(user.shotsOnTarget, opp.shotsOnTarget)
    )
    Spacer(Modifier.height(12.dp))
    StatComparisonRow(
        label = "Expected goals",
        homeValue = "%.2f".format(user.expectedGoals),
        awayValue = "%.2f".format(opp.expectedGoals),
        homeFraction = fraction(user.expectedGoals.toInt(), opp.expectedGoals.toInt())
    )
    Spacer(Modifier.height(12.dp))
    StatComparisonRow(
        label = "Corners",
        homeValue = "${user.corners}",
        awayValue = "${opp.corners}",
        homeFraction = fraction(user.corners, opp.corners)
    )
    Spacer(Modifier.height(12.dp))
    StatComparisonRow(
        label = "Fouls",
        homeValue = "${user.fouls}",
        awayValue = "${opp.fouls}",
        homeFraction = fraction(user.fouls, opp.fouls)
    )
    Spacer(Modifier.height(12.dp))
    StatComparisonRow(
        label = "Cards (Y/R)",
        homeValue = "${user.yellowCards}/${user.redCards}",
        awayValue = "${opp.yellowCards}/${opp.redCards}",
        homeFraction = fraction(
            user.yellowCards * 2 + user.redCards * 5,
            opp.yellowCards * 2 + opp.redCards * 5
        )
    )
}

@Composable
private fun TeamColumn(
    club: com.footymanager.simulator.domain.model.Club,
    label: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ClubCrest(club = club, size = 44.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = club.shortName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LineupList(career: Career, playerIds: List<Long>) {
    val byId = career.userSquad.associateBy { it.id }
    playerIds.forEach { id ->
        val player = byId[id] ?: return@forEach
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = player.position.short,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(38.dp)
            )
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
        }
    }
}

@Composable
private fun eventAccent(type: MatchEventType) = when (type) {
    MatchEventType.GOAL, MatchEventType.PENALTY_GOAL -> StatColors.elite
    MatchEventType.YELLOW_CARD -> StatColors.average
    MatchEventType.RED_CARD, MatchEventType.SECOND_YELLOW -> StatColors.bad
    MatchEventType.INJURY -> StatColors.poor
    MatchEventType.SUBSTITUTION -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun com.footymanager.simulator.domain.model.Match.competitionLabel(): String = when (competition) {
    com.footymanager.simulator.domain.model.CompetitionType.CHAMPIONS_LEAGUE -> "Champions League"
    com.footymanager.simulator.domain.model.CompetitionType.DOMESTIC_CUP -> "Domestic Cup"
    com.footymanager.simulator.domain.model.CompetitionType.FRIENDLY -> "Friendly"
    com.footymanager.simulator.domain.model.CompetitionType.LEAGUE -> "League"
}

@Composable
private fun MomentumBar(momentum: List<Int>, userIsHome: Boolean) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Opponent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "You",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            momentum.forEach { raw ->
                val signed = if (userIsHome) raw else -raw
                val height = (6 + kotlin.math.abs(signed) * 0.30f).dp
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(height)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (signed >= 0) MaterialTheme.colorScheme.primary else StatColors.bad
                            )
                    )
                }
            }
        }
    }
}

private fun com.footymanager.simulator.domain.model.Player.surname(): String =
    name.substringAfterLast(' ').ifBlank { name }

private fun formCharFor(career: Career, match: com.footymanager.simulator.domain.model.Match, clubId: Long): Char {
    val isHome = match.homeClubId == clubId
    val scored = if (isHome) match.homeGoals else match.awayGoals
    val conceded = if (isHome) match.awayGoals else match.homeGoals
    return when {
        scored > conceded -> 'W'
        scored == conceded -> 'D'
        else -> 'L'
    }
}
