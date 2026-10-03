package com.footymanager.simulator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.engine.MatchPhase
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.DefensiveLine
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.MatchEventType
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.Tempo
import com.footymanager.simulator.ui.components.ClubBadge
import com.footymanager.simulator.ui.components.DominationBar
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.FormStrip
import com.footymanager.simulator.ui.components.InfoPill
import com.footymanager.simulator.ui.components.OptionSelector
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.components.StatComparisonRow
import com.footymanager.simulator.ui.components.badgePalette
import com.footymanager.simulator.ui.components.fraction
import com.footymanager.simulator.ui.theme.StatColors
import com.footymanager.simulator.viewmodel.MatchDayState
import com.footymanager.simulator.viewmodel.MatchFeedItem
import com.footymanager.simulator.viewmodel.MatchMode

/**
 * The live match-day experience.
 *
 * Before kick-off this is a professional preview. Pressing START MATCH hands
 * control to the engine, which is advanced a minute at a time, so the score,
 * clock, domination bar and event feed all update as the game unfolds.
 *
 * At half time the engine stops and this screen becomes a management dashboard
 * with no time limit. The manager continues only when they press START SECOND HALF.
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
    val home = if (matchDay.isHome) career.userClub else matchDay.opponent
    val away = if (matchDay.isHome) matchDay.opponent else career.userClub

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchHeader(career, matchDay, onBack) }

        item {
            FmCard(accent = MaterialTheme.colorScheme.primary, padding = 16.dp) {
                ScoreHeader(home, away, matchDay, centerTop = "KICK OFF", centerBottom = "vs")
                Spacer(Modifier.height(12.dp))
                MatchInfoRow(career, matchDay, home)
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
                PitchLineup(career, career.selection.startingXi.map { it.playerId }, career.tactics)
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
private fun MatchInfoRow(career: Career, matchDay: MatchDayState, homeClub: Club) {
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
            text = homeClub.stadiumName.ifBlank { career.userClub.stadiumName },
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
    val home = if (matchDay.isHome) career.userClub else matchDay.opponent
    val away = if (matchDay.isHome) matchDay.opponent else career.userClub
    var showSubs by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchHeader(career, matchDay, onBack) }

        // ---- Scoreboard: badges, score, minute, competition, stadium ----
        item {
            FmCard(accent = MaterialTheme.colorScheme.primary, padding = 16.dp) {
                ScoreHeader(
                    home, away, matchDay,
                    centerTop = matchDay.clockLabel,
                    centerBottom = if (matchDay.simulating) "LIVE" else "PAUSED",
                    animateScore = true
                )
                Spacer(Modifier.height(10.dp))
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
                        text = home.stadiumName.ifBlank { "Stadium" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ---- LIVE DOMINATION BAR, directly under the score ----
        item {
            FmCard {
                DominationBar(
                    homeClub = home,
                    awayClub = away,
                    homeShare = matchDay.homeDomination
                )
            }
        }

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

        // ---- Live event feed ----
        item {
            FmCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader("Live feed")
                    if (matchDay.simulating) LiveIndicator()
                }
                Spacer(Modifier.height(8.dp))
                val progress by animateFloatAsState(
                    targetValue = when (matchDay.phase) {
                        MatchPhase.FIRST_HALF -> (matchDay.minute / 90f).coerceIn(0f, 0.5f)
                        MatchPhase.SECOND_HALF -> (matchDay.minute / 90f).coerceIn(0.5f, 1f)
                        MatchPhase.FINISHED -> 1f
                        else -> 0.5f
                    },
                    label = "matchProgress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
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
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(matchDay.feed) { item -> FeedRow(item) }
                    }
                }
            }
        }

        // ---- Match statistics ----
        item {
            FmCard {
                SectionHeader("Match statistics")
                Spacer(Modifier.height(12.dp))
                StatBlock(career, matchDay)
            }
        }

        // ---- Live management ----
        item {
            FmCard {
                SectionHeader("Match management")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${matchDay.substitutionsMade} of ${matchDay.maxSubstitutions} subs used · " +
                        "${(matchDay.maxSubstitutionWindows - matchDay.substitutionWindowsUsed).coerceAtLeast(0)} windows left",
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
                    FmSecondaryButton(
                        text = if (showSubs) "Hide substitutions" else "Make a substitution",
                        onClick = { showSubs = !showSubs }
                    )
                    if (showSubs) {
                        Spacer(Modifier.height(12.dp))
                        SubstitutionPanel(career, matchDay, onMakeLiveSub)
                    }
                    Spacer(Modifier.height(10.dp))
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
private fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "liveAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(StatColors.bad.copy(alpha = alpha))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = StatColors.bad,
            fontWeight = FontWeight.Bold
        )
    }
}

/** The shared scoreboard: two badges, the score, and a centre label block. */
@Composable
private fun ScoreHeader(
    home: Club,
    away: Club,
    matchDay: MatchDayState,
    centerTop: String,
    centerBottom: String,
    animateScore: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (animateScore) 1f else 1f,
        label = "scoreScale"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamColumn(home, if (home.id == matchDay.opponent.id) "Opponent" else "You",
            home.id != matchDay.opponent.id, Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(112.dp)
        ) {
            Text(
                text = "${matchDay.homeGoals} - ${matchDay.awayGoals}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                modifier = Modifier.scale(scale)
            )
            Text(
                text = centerTop,
                style = MaterialTheme.typography.labelMedium,
                color = if (centerBottom == "LIVE") StatColors.bad else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = centerBottom,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TeamColumn(away, if (away.id == matchDay.opponent.id) "Opponent" else "You",
            away.id != matchDay.opponent.id, Modifier.weight(1f))
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
    val home = if (matchDay.isHome) career.userClub else matchDay.opponent
    val away = if (matchDay.isHome) matchDay.opponent else career.userClub

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchHeader(career, matchDay, onBack) }

        item {
            FmCard(accent = StatColors.average, padding = 16.dp) {
                ScoreHeader(home, away, matchDay, centerTop = "HALF TIME", centerBottom = "Interval")
                Spacer(Modifier.height(10.dp))
                DominationBar(homeClub = home, awayClub = away, homeShare = matchDay.homeDomination)
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
                val ids = matchDay.homeOnPitch.ifEmpty { career.selection.startingXi.map { it.playerId } }
                PitchLineup(career, ids, career.tactics)
                Spacer(Modifier.height(12.dp))
                FatigueList(career, ids)
            }
        }

        item { HalfTimeTacticsCard(career, onApplyLiveTactics) }

        item { HalfTimeChangesCard(career, matchDay, onPlanSub, onCancelSub) }

        item {
            FmPrimaryButton(
                text = "START SECOND HALF",
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

/** Lists the on-pitch players with their fatigue and rating, for the break. */
@Composable
private fun FatigueList(career: Career, ids: List<Long>) {
    val byId = career.userSquad.associateBy { it.id }
    Text(
        text = "FATIGUE",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    ids.mapNotNull { byId[it] }.forEach { player ->
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
                modifier = Modifier.width(34.dp)
            )
            Text(
                text = player.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            com.footymanager.simulator.ui.components.FitnessBar(player.fitness, width = 54.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${player.fitness}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (player.fitness < 60) StatColors.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun HalfTimeChangesCard(
    career: Career,
    matchDay: MatchDayState,
    onPlanSub: (Long, Long) -> Unit,
    onCancelSub: (Long) -> Unit
) {
    val byId = career.userSquad.associateBy { it.id }
    val onPitchIds = matchDay.homeOnPitch.ifEmpty { career.selection.startingXi.map { it.playerId } }
    val benchIds = matchDay.benchIds
    val planned = matchDay.plannedSubstitutions

    FmCard {
        SectionHeader("Half-time substitutions")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Up to ${matchDay.maxSubstitutions} changes. Half-time changes are free of a window.",
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

        if (planned.size < matchDay.maxSubstitutions) {
            SubstitutionPanel(
                career = career,
                matchDay = matchDay,
                onConfirm = onPlanSub,
                onPitchIds = onPitchIds,
                benchIds = benchIds
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

// ----------------------------------------------------------- substitutions

/**
 * The substitution picker.
 *
 * The flow is deliberately explicit and cannot dead-end: the manager first picks
 * a player to take off from the current XI (the "SUBSTITUTE OUT" step), then a
 * replacement from the bench ("SUBSTITUTE IN"), and finally confirms. The chosen
 * outgoing player is highlighted, and the summary line spells out OUT and IN so
 * there is never any doubt about who is leaving and who is arriving.
 */
@Composable
private fun SubstitutionPanel(
    career: Career,
    matchDay: MatchDayState,
    onConfirm: (Long, Long) -> Unit,
    onPitchIds: List<Long> = matchDay.homeOnPitch,
    benchIds: List<Long> = matchDay.benchIds
) {
    val byId = career.userSquad.associateBy { it.id }
    var offId by remember { mutableStateOf<Long?>(null) }
    var onId by remember { mutableStateOf<Long?>(null) }

    val onPitch = onPitchIds.mapNotNull { byId[it] }
    val bench = benchIds.mapNotNull { byId[it] }
    val canConfirm = offId != null && onId != null

    Text(
        text = "STEP 1 · SUBSTITUTE OUT",
        style = MaterialTheme.typography.labelSmall,
        color = if (offId != null) StatColors.bad else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(onPitch, key = { it.id }) { player ->
            SubChip(
                player = player,
                selected = offId == player.id,
                accent = StatColors.bad,
                onClick = { offId = if (offId == player.id) null else player.id }
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        text = "STEP 2 · SUBSTITUTE IN",
        style = MaterialTheme.typography.labelSmall,
        color = if (onId != null) StatColors.elite else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(6.dp))
    if (bench.isEmpty()) {
        Text(
            text = "No substitutes left on the bench.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bench, key = { it.id }) { player ->
                SubChip(
                    player = player,
                    selected = onId == player.id,
                    accent = StatColors.elite,
                    onClick = { onId = if (onId == player.id) null else player.id }
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    // Clear OUT / IN summary so the direction of the change is unambiguous.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "OUT",
                style = MaterialTheme.typography.labelSmall,
                color = StatColors.bad,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = offId?.let { byId[it]?.name } ?: "Select a player",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "→",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "IN",
                style = MaterialTheme.typography.labelSmall,
                color = StatColors.elite,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = onId?.let { byId[it]?.name } ?: "Select a substitute",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    FmSecondaryButton(
        text = "Confirm substitution",
        enabled = canConfirm,
        onClick = {
            val off = offId
            val on = onId
            if (off != null && on != null) {
                onConfirm(off, on)
                offId = null
                onId = null
            }
        }
    )
}

/** A selectable player chip for the substitution picker. */
@Composable
private fun SubChip(
    player: Player,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val border = if (selected) accent else MaterialTheme.colorScheme.outline
    val bg = if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = player.surname(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        Text(
            text = "${player.position.short} · ${player.overall} · ${player.fitness}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    val home = if (matchDay.isHome) career.userClub else matchDay.opponent
    val away = if (matchDay.isHome) matchDay.opponent else career.userClub

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MatchHeader(career, matchDay, null) }

        item {
            FmCard(accent = MaterialTheme.colorScheme.primary, padding = 16.dp) {
                ScoreHeader(home, away, matchDay, centerTop = "FULL TIME", centerBottom = "Result")
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

        item {
            FmCard {
                SectionHeader("Final domination")
                Spacer(Modifier.height(10.dp))
                DominationBar(
                    homeClub = home,
                    awayClub = away,
                    homeShare = matchDay.homeDomination,
                    animated = false
                )
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

/**
 * A single feed line. Goal, card and substitution events animate in and carry a
 * stronger visual weight than routine entries, so important moments stand out.
 */
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

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 } +
            (if (highlight) scaleIn(tween(260)) else fadeIn(tween(220)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(bg)
                .then(
                    if (highlight) Modifier.border(1.dp, accent.copy(alpha = 0.6f),
                        MaterialTheme.shapes.extraSmall) else Modifier
                )
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
            if (highlight) {
                Icon(
                    Icons.Outlined.SportsSoccer,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
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
        homeFraction = fraction((user.expectedGoals * 100).toInt(), (opp.expectedGoals * 100).toInt())
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
    club: Club,
    label: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ClubBadge(club = club, size = 46.dp)
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

/**
 * A read-only pitch view of a lineup, laid out from the formation's coordinates.
 * Gives the match-day screens a proper tactical-board look.
 */
@Composable
private fun PitchLineup(career: Career, playerIds: List<Long>, tactics: Tactics) {
    val byId = career.userSquad.associateBy { it.id }
    val formation = tactics.formation
    val slots = career.selection.startingXi.sortedBy { it.slotIndex }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(com.footymanager.simulator.ui.theme.PitchGreen)
    ) {
        val w = maxWidth
        val h = maxHeight

        // Pitch markings.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(70.dp)
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
        )

        // Player tokens, positioned from the formation coordinates.
        formation.coordinates.forEachIndexed { index, point ->
            val slot = slots.firstOrNull { it.slotIndex == index }
            val player = slot?.let { byId[it.playerId] }
                ?: playerIds.getOrNull(index)?.let { byId[it] }
            val x = ((point.x - 0.5f) * w.value * 0.86f).dp
            // y = 0 is the team's own goal; flip so attackers appear at the top.
            val y = ((0.5f - point.y) * h.value * 0.86f).dp
            PitchToken(
                player = player,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = x, y = y)
            )
        }
    }
}

@Composable
private fun PitchToken(player: Player?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (player != null) StatColors.forRating(player.overall).copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.3f)
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = player?.position?.short ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF0B1220),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = player?.surname() ?: "Empty",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun eventAccent(type: MatchEventType): Color = when (type) {
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
