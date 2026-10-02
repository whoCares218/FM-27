package com.footymanager.simulator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.MatchEvent
import com.footymanager.simulator.domain.model.MatchEventType
import com.footymanager.simulator.domain.model.MatchResult
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.FormStrip
import com.footymanager.simulator.ui.components.InfoPill
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.components.StatComparisonRow
import com.footymanager.simulator.ui.components.fraction
import com.footymanager.simulator.ui.theme.StatColors
import com.footymanager.simulator.viewmodel.MatchDayState
import kotlinx.coroutines.delay

/**
 * The match-day experience.
 *
 * Before kick-off this is a preview with the two lineups and a difficulty
 * readout. Once played, the event timeline animates in minute order, followed by
 * the full statistics and player ratings.
 */
@Composable
fun MatchDayScreen(
    career: Career,
    matchDay: MatchDayState,
    animationMultiplier: Float,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onContinue: () -> Unit
) {
    val isPlayed = matchDay.isPlayed
    val result = matchDay.result

    // Reveal events progressively so the match feels like it unfolds.
    var revealedCount by remember(result) { mutableIntStateOf(if (result == null) 0 else result.events.size) }
    var animationFinished by remember(result) { mutableStateOf(result == null) }

    LaunchedEffect(result) {
        if (result == null) {
            animationFinished = true
            return@LaunchedEffect
        }
        if (animationMultiplier <= 0f) {
            revealedCount = result.events.size
            animationFinished = true
            return@LaunchedEffect
        }
        revealedCount = 0
        animationFinished = false
        val baseDelay = (620 * animationMultiplier).toLong().coerceAtLeast(60L)
        // Do not animate every single event of a 20-event match in real time:
        // compress so the whole timeline lands within a few seconds.
        val step = if (result.events.size > 12) result.events.size / 10 + 1 else 1
        var index = 0
        while (index < result.events.size) {
            index += step
            revealedCount = index.coerceAtMost(result.events.size)
            delay(baseDelay)
        }
        animationFinished = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Header ----
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Matchday ${matchDay.match.matchday}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- Scoreboard ----
        item {
            FmCard(accent = MaterialTheme.colorScheme.primary, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamColumn(
                        club = career.userClub,
                        label = "You",
                        isUser = true,
                        modifier = Modifier.weight(1f)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(104.dp)
                    ) {
                        if (isPlayed && result != null) {
                            Text(
                                text = "${result.homeGoals} - ${result.awayGoals}",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "FULL TIME",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
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
                    }
                    TeamColumn(
                        club = matchDay.opponent,
                        label = "Opponent",
                        isUser = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ---- Pre-match preview ----
        if (!isPlayed) {
            item {
                FmCard {
                    SectionHeader("Match preview")
                    Spacer(Modifier.height(10.dp))
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
                        StatCell("Your formation", career.tactics.formation.name)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Their form",
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
                    LineupList(career = career, selection = career.selection)
                }
            }

            item {
                FmPrimaryButton(
                    text = "Play Match",
                    onClick = onPlay,
                    icon = Icons.Filled.PlayArrow
                )
                Spacer(Modifier.height(8.dp))
                FmSecondaryButton(text = "Back", onClick = onBack)
            }
        }

        // ---- Live timeline ----
        if (isPlayed && result != null) {
            item {
                FmCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader("Match timeline")
                        if (!animationFinished) {
                            Text(
                                text = "Playing...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!animationFinished) {
                        val progress by animateFloatAsState(
                            targetValue = revealedCount.toFloat() / result.events.size.coerceAtLeast(1),
                            label = "matchProgress"
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    val visible = result.events.take(revealedCount)
                    val listState = rememberLazyListState()
                    LaunchedEffect(visible.size) {
                        if (visible.isNotEmpty()) {
                            listState.animateScrollToItem(visible.lastIndex)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(visible, key = { eventKey(it) }) { event ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { it / 3 }
                            ) {
                                TimelineRow(event = event, career = career, matchDay = matchDay)
                            }
                        }
                    }
                }
            }

            // ---- Full statistics ----
            item {
                FmCard {
                    SectionHeader("Match statistics")
                    Spacer(Modifier.height(12.dp))
                    StatComparisonRow(
                        label = "Possession",
                        homeValue = "${result.homeStats.possession}%",
                        awayValue = "${result.awayStats.possession}%",
                        homeFraction = result.homeStats.possession / 100f
                    )
                    Spacer(Modifier.height(12.dp))
                    StatComparisonRow(
                        label = "Shots",
                        homeValue = "${result.homeStats.shots}",
                        awayValue = "${result.awayStats.shots}",
                        homeFraction = fraction(result.homeStats.shots, result.awayStats.shots)
                    )
                    Spacer(Modifier.height(12.dp))
                    StatComparisonRow(
                        label = "On target",
                        homeValue = "${result.homeStats.shotsOnTarget}",
                        awayValue = "${result.awayStats.shotsOnTarget}",
                        homeFraction = fraction(result.homeStats.shotsOnTarget, result.awayStats.shotsOnTarget)
                    )
                    Spacer(Modifier.height(12.dp))
                    StatComparisonRow(
                        label = "Corners",
                        homeValue = "${result.homeStats.corners}",
                        awayValue = "${result.awayStats.corners}",
                        homeFraction = fraction(result.homeStats.corners, result.awayStats.corners)
                    )
                    Spacer(Modifier.height(12.dp))
                    StatComparisonRow(
                        label = "Fouls",
                        homeValue = "${result.homeStats.fouls}",
                        awayValue = "${result.awayStats.fouls}",
                        homeFraction = fraction(result.homeStats.fouls, result.awayStats.fouls)
                    )
                    Spacer(Modifier.height(12.dp))
                    StatComparisonRow(
                        label = "Pass accuracy",
                        homeValue = "${result.homeStats.passAccuracy}%",
                        awayValue = "${result.awayStats.passAccuracy}%",
                        homeFraction = fraction(result.homeStats.passAccuracy, result.awayStats.passAccuracy)
                    )
                    Spacer(Modifier.height(12.dp))
                    StatComparisonRow(
                        label = "Cards (Y/R)",
                        homeValue = "${result.homeStats.yellowCards}/${result.homeStats.redCards}",
                        awayValue = "${result.awayStats.yellowCards}/${result.awayStats.redCards}",
                        homeFraction = fraction(
                            result.homeStats.yellowCards * 2 + result.homeStats.redCards * 5,
                            result.awayStats.yellowCards * 2 + result.awayStats.redCards * 5
                        )
                    )
                }
            }

            // ---- Player ratings ----
            item {
                FmCard {
                    SectionHeader("Player ratings")
                    Spacer(Modifier.height(10.dp))
                    val sorted = result.playerRatings.sortedByDescending { it.rating }
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
                                    .background(if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                                text = Fmt.rating(rating.rating),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatColors.forRating((rating.rating * 10).toInt())
                            )
                        }
                    }
                }
            }

            item {
                FmPrimaryButton(
                    text = if (animationFinished) "Continue" else "Skip to result",
                    onClick = {
                        if (!animationFinished) {
                            revealedCount = result.events.size
                            animationFinished = true
                        } else {
                            onContinue()
                        }
                    }
                )
            }
        }
    }
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
private fun TimelineRow(event: MatchEvent, career: Career, matchDay: MatchDayState) {
    val isUserEvent = event.clubId == career.userClubId
    val accent = when (event.type) {
        MatchEventType.GOAL -> StatColors.elite
        MatchEventType.YELLOW_CARD -> StatColors.average
        MatchEventType.RED_CARD -> StatColors.bad
        MatchEventType.INJURY -> StatColors.poor
        MatchEventType.SUBSTITUTION -> MaterialTheme.colorScheme.primary
        MatchEventType.HALF_TIME, MatchEventType.FULL_TIME -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val isMilestone = event.type == MatchEventType.HALF_TIME || event.type == MatchEventType.FULL_TIME ||
        event.type == MatchEventType.KICK_OFF

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                if (isMilestone) MaterialTheme.colorScheme.surfaceVariant
                else if (isUserEvent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${event.minute}'",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.width(34.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eventTitle(event),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = eventDetail(event, matchDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isMilestone) {
            Text(
                text = if (isUserEvent) career.userClub.shortName else matchDay.opponent.shortName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }
}

private fun eventTitle(event: MatchEvent): String = when (event.type) {
    MatchEventType.GOAL -> "GOAL"
    MatchEventType.YELLOW_CARD -> "YELLOW CARD"
    MatchEventType.RED_CARD -> "RED CARD"
    MatchEventType.INJURY -> "INJURY"
    MatchEventType.SUBSTITUTION -> "SUBSTITUTION"
    MatchEventType.HALF_TIME -> "HALF TIME"
    MatchEventType.FULL_TIME -> "FULL TIME"
    MatchEventType.KICK_OFF -> "KICK OFF"
    else -> event.type.name.replace('_', ' ')
}

private fun eventDetail(event: MatchEvent, matchDay: MatchDayState): String = when (event.type) {
    MatchEventType.GOAL -> buildString {
        append(event.playerName.ifBlank { "Unknown" })
        if (event.secondaryPlayerName.isNotBlank()) append(" (assist: ${event.secondaryPlayerName})")
    }
    MatchEventType.SUBSTITUTION -> event.detail.ifBlank {
        "${event.playerName} replaces ${event.secondaryPlayerName}"
    }
    MatchEventType.YELLOW_CARD, MatchEventType.RED_CARD -> event.playerName
    MatchEventType.INJURY -> event.playerName
    else -> event.detail.ifBlank { matchDay.opponent.name }
}

@Composable
private fun LineupList(
    career: Career,
    selection: com.footymanager.simulator.domain.model.TeamSelection
) {
    val byId = career.userSquad.associateBy { it.id }
    val formation = career.tactics.formation
    selection.startingXi.forEach { slot ->
        val player = byId[slot.playerId] ?: return@forEach
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formation.roles.getOrNull(slot.slotIndex)?.short ?: "-",
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
            if (slot.outOfPosition) {
                Text(
                    text = "OOP",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatColors.poor
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "${player.overall}",
                style = MaterialTheme.typography.titleSmall,
                color = StatColors.forRating(player.overall)
            )
        }
    }
}

private fun eventKey(event: MatchEvent): String =
    "${event.minute}-${event.type}-${event.playerId}-${event.clubId}"

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
