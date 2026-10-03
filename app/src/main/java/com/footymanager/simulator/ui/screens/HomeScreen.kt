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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.GamePhase
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.LedgerCategory
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.NewsCategory
import com.footymanager.simulator.domain.model.ObjectiveStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.FormStrip
import com.footymanager.simulator.ui.components.InfoPill
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell

/**
 * The dashboard. Shows the club's situation at a glance and the single most
 * important action: playing the next match.
 */
@Composable
fun HomeScreen(
    career: Career,
    onPlayMatch: () -> Unit,
    onQuickSim: () -> Unit,
    onOpenSquad: () -> Unit,
    onOpenLeague: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenBoard: () -> Unit,
    onOpenSponsors: () -> Unit,
    onStartNextSeason: () -> Unit
) {
    val club = career.userClub
    val nextMatch = career.nextFixtureAnyCompetition()
    val league = League.byId(career.userLeagueId)
    val position = career.userLeaguePosition
    val recentForm = career.recentForm(career.userClubId)
    val preSeason = career.phase == GamePhase.PRE_SEASON && career.sponsorship == null
    val seasonEnded = career.phase == GamePhase.SEASON_ENDED || nextMatch == null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Club header ----
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClubCrest(club = club, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = club.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${career.managerName} • ${league.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${career.date.display()} • ${career.season}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (position == 0) "-" else "$position",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = positionColor(position)
                    )
                    Text(
                        text = "LEAGUE POS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- Key numbers ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatCard(
                    label = "Balance",
                    value = Fmt.money(club.balance),
                    modifier = Modifier.weight(1f),
                    valueColor = if (club.balance >= 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                MiniStatCard(
                    label = "Transfer",
                    value = Fmt.money(club.transferBudget),
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    label = "Wage room",
                    value = Fmt.money((club.wageBudget - career.wageBill(career.userClubId)).coerceAtLeast(0)),
                    modifier = Modifier.weight(1f),
                    valueColor = if (career.wageBill(career.userClubId) <= club.wageBudget)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        // ---- Next match ----
        item {
            SectionHeader("Next match")
            Spacer(Modifier.height(8.dp))
            if (seasonEnded) {
                FmCard(accent = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = "Season complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "All ${career.totalMatchdays()} matchdays have been played. " +
                            "Review your season and start the next campaign.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                NextMatchCard(career = career, match = nextMatch!!)
            }
        }

        // ---- Primary actions ----
        item {
            if (preSeason) {
                FmCard(accent = MaterialTheme.colorScheme.primary) {
                    SectionHeader("Pre-season")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Choose a sponsorship deal before the ${career.season} season begins. " +
                            "Your decision sets the club's off-field income for the campaign.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FmPrimaryButton(
                        text = "Choose Sponsorship",
                        onClick = onOpenSponsors,
                        icon = Icons.Filled.PlayArrow
                    )
                }
            } else if (seasonEnded) {
                FmPrimaryButton(
                    text = "View Season Summary & Continue",
                    onClick = onStartNextSeason,
                    icon = Icons.Filled.PlayArrow
                )
            } else {
                FmPrimaryButton(
                    text = "Play Match",
                    onClick = onPlayMatch,
                    icon = Icons.Filled.PlayArrow
                )
                Spacer(Modifier.height(8.dp))
                FmSecondaryButton(
                    text = "Quick Sim",
                    onClick = onQuickSim,
                    icon = Icons.Outlined.FastForward
                )
            }
        }

        // ---- Form + squad snapshot ----
        item {
            FmCard {
                SectionHeader("Recent form")
                Spacer(Modifier.height(8.dp))
                FormStrip(results = recentForm.map { formChar(career, it) })
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell("Played", "${career.sortedTable(career.userLeagueId).firstOrNull { it.clubId == career.userClubId }?.played ?: 0}")
                    StatCell("Points", "${career.sortedTable(career.userLeagueId).firstOrNull { it.clubId == career.userClubId }?.points ?: 0}")
                    StatCell("Goals", "${career.totalGoalsFor(career.userClubId)}")
                    StatCell(
                        "Wage bill",
                        Fmt.money(career.wageBill(career.userClubId)),
                        valueColor = if (career.wageBill(career.userClubId) <= club.wageBudget)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ---- Squad alerts ----
        item {
            val injured = career.userSquad.count { it.isInjured }
            val suspended = career.userSquad.count { it.isSuspended }
            val lowFitness = career.userSquad.count { it.fitness < 60 }
            if (injured > 0 || suspended > 0 || lowFitness > 0) {
                FmCard(onClick = onOpenSquad) {
                    SectionHeader("Squad status")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (injured > 0) InfoPill("$injured injured", color = MaterialTheme.colorScheme.error)
                        if (suspended > 0) InfoPill("$suspended suspended", color = MaterialTheme.colorScheme.tertiary)
                        if (lowFitness > 0) InfoPill("$lowFitness tired", color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tap to manage the squad",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ---- Board ----
        item {
            FmCard(onClick = onOpenBoard) {
                SectionHeader("Board confidence")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${career.board.confidence}%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = confidenceColor(career.board.confidence),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = career.board.lastEvaluation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (career.board.objectives.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    career.board.objectives.take(2).forEach { objective ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(objectiveColor(objective.status))
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = objective.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = objective.status.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = objectiveColor(objective.status)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Upcoming fixtures ----
        val upcoming = career.upcomingFixtures(limit = 5)
            .filter { it.id != nextMatch?.id }
            .take(4)
        if (upcoming.isNotEmpty()) {
            item {
                FmCard(onClick = onOpenLeague) {
                    SectionHeader("Upcoming fixtures")
                    Spacer(Modifier.height(8.dp))
                    upcoming.forEach { fixture ->
                        val opponent = career.clubOrThrow(fixture.opponentOf(career.userClubId))
                        val isHome = fixture.isHomeFor(career.userClubId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isHome) "H" else "A",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isHome) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(16.dp)
                            )
                            ClubCrest(club = opponent, size = 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = opponent.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = fixture.competitionShortLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ---- Finance snapshot ----
        item {
            FmCard {
                SectionHeader("Finances")
                Spacer(Modifier.height(8.dp))
                val spent = career.ledger.filter { it.category == LedgerCategory.TRANSFER_IN }
                    .sumOf { -it.amount }
                val earned = career.ledger.filter { it.category == LedgerCategory.TRANSFER_OUT }
                    .sumOf { it.amount }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell("Transfer spend", Fmt.money(spent))
                    StatCell("Transfer income", Fmt.money(earned))
                    StatCell(
                        "Net",
                        Fmt.money(earned - spent),
                        valueColor = if (earned - spent >= 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ---- News ----
        item {
            FmCard(onClick = onOpenNews) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader("Latest news")
                    Icon(
                        Icons.Outlined.Newspaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                val latest = career.news.takeLast(3).reversed()
                if (latest.isEmpty()) {
                    Text(
                        text = "No news yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                latest.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Text(
                        text = categoryLabel(item.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor(item.category)
                    )
                    Text(
                        text = item.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NextMatchCard(career: Career, match: Match) {
    val opponent = career.clubOrThrow(match.opponentOf(career.userClubId))
    val isHome = match.isHomeFor(career.userClubId)
    val opponentPosition = career.positionOf(opponent.id, opponent.leagueId)
    val opponentForm = career.recentForm(opponent.id)
    val difficulty = matchDifficulty(career, opponent)

    FmCard(accent = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClubCrest(club = career.userClub, size = 34.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = career.userClub.shortName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isHome) "HOME" else "AWAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "vs",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = match.competitionShortLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClubCrest(club = opponent, size = 34.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = opponent.shortName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatCell("Opponent", opponent.name, modifier = Modifier.weight(1f))
            StatCell("Their pos.", if (opponentPosition == 0) "-" else "$opponentPosition", modifier = Modifier.weight(0.6f))
            StatCell(
                "Difficulty",
                difficulty,
                valueColor = difficultyColor(difficulty),
                modifier = Modifier.weight(0.8f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Their form",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            FormStrip(results = opponentForm.map { formChar(career, it) }, size = 16.dp)
        }
    }
}

fun formChar(career: Career, match: Match): Char {
    val isHome = match.homeClubId == career.userClubId
    val scored = if (isHome) match.homeGoals else match.awayGoals
    val conceded = if (isHome) match.awayGoals else match.homeGoals
    return when {
        scored > conceded -> 'W'
        scored == conceded -> 'D'
        else -> 'L'
    }
}

/** Short competition tag shown on fixture cards. */
fun Match.competitionShortLabel(): String = when (competition) {
    com.footymanager.simulator.domain.model.CompetitionType.CHAMPIONS_LEAGUE -> "Champions League"
    com.footymanager.simulator.domain.model.CompetitionType.DOMESTIC_CUP -> "Domestic Cup"
    com.footymanager.simulator.domain.model.CompetitionType.FRIENDLY -> "Friendly"
    com.footymanager.simulator.domain.model.CompetitionType.LEAGUE -> "Matchday $matchday"
}

/** Pre-match difficulty readout based on relative reputation and form. */
fun matchDifficulty(career: Career, opponent: com.footymanager.simulator.domain.model.Club): String {
    val gap = opponent.reputation - career.userClub.reputation
    return when {
        gap >= 12 -> "Very Hard"
        gap >= 5 -> "Hard"
        gap >= -5 -> "Even"
        gap >= -12 -> "Favourable"
        else -> "Easy"
    }
}

fun difficultyColor(label: String): androidx.compose.ui.graphics.Color = when (label) {
    "Very Hard" -> com.footymanager.simulator.ui.theme.StatColors.bad
    "Hard" -> com.footymanager.simulator.ui.theme.StatColors.poor
    "Even" -> com.footymanager.simulator.ui.theme.StatColors.average
    "Favourable" -> com.footymanager.simulator.ui.theme.StatColors.good
    else -> com.footymanager.simulator.ui.theme.StatColors.elite
}

fun confidenceColor(confidence: Int): androidx.compose.ui.graphics.Color = when {
    confidence >= 70 -> com.footymanager.simulator.ui.theme.StatColors.elite
    confidence >= 45 -> com.footymanager.simulator.ui.theme.StatColors.average
    confidence >= 25 -> com.footymanager.simulator.ui.theme.StatColors.poor
    else -> com.footymanager.simulator.ui.theme.StatColors.bad
}

/** League position colour: European places green, relegation zone red. */
fun positionColor(position: Int): androidx.compose.ui.graphics.Color = when {
    position == 0 -> com.footymanager.simulator.ui.theme.TextSecondaryDark
    position <= 4 -> com.footymanager.simulator.ui.theme.StatColors.elite
    position <= 10 -> com.footymanager.simulator.ui.theme.StatColors.good
    position <= 16 -> com.footymanager.simulator.ui.theme.StatColors.average
    else -> com.footymanager.simulator.ui.theme.StatColors.bad
}

fun objectiveColor(status: ObjectiveStatus): androidx.compose.ui.graphics.Color = when (status) {
    ObjectiveStatus.ACHIEVED -> com.footymanager.simulator.ui.theme.StatColors.elite
    ObjectiveStatus.ON_TRACK -> com.footymanager.simulator.ui.theme.StatColors.good
    ObjectiveStatus.AT_RISK -> com.footymanager.simulator.ui.theme.StatColors.poor
    ObjectiveStatus.FAILED -> com.footymanager.simulator.ui.theme.StatColors.bad
}

fun categoryLabel(category: NewsCategory): String = when (category) {
    NewsCategory.TRANSFER -> "TRANSFER NEWS"
    NewsCategory.INJURY -> "INJURY NEWS"
    NewsCategory.MATCH -> "MATCH NEWS"
    NewsCategory.BOARD -> "BOARD NEWS"
    NewsCategory.GENERAL -> "CLUB NEWS"
    NewsCategory.TRANSFER_WINDOW -> "TRANSFER WINDOW"
    NewsCategory.TRAINING -> "TRAINING"
    NewsCategory.AWARD -> "AWARDS"
}

fun categoryColor(category: NewsCategory): androidx.compose.ui.graphics.Color = when (category) {
    NewsCategory.TRANSFER -> com.footymanager.simulator.ui.theme.Amber
    NewsCategory.INJURY -> com.footymanager.simulator.ui.theme.StatColors.bad
    NewsCategory.MATCH -> com.footymanager.simulator.ui.theme.GrassGreen
    NewsCategory.BOARD -> com.footymanager.simulator.ui.theme.TurfTeal
    else -> com.footymanager.simulator.ui.theme.TextSecondaryDark
}

/** Shared helper: does the career still have fixtures to play? */
fun Career.hasMatchesRemaining(): Boolean = nextMatch() != null

/** Convenience for screens that need the user's key player. */
fun Career.bestPlayer(): Player? = userSquad.maxByOrNull { it.overall }
