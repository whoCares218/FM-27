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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.ClubSeasonRecord
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.PlayerCareerRecord
import com.footymanager.simulator.domain.model.SeasonRecordHolder
import com.footymanager.simulator.domain.model.TrophyRecord
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.EmptyState
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.ScreenTitle
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.theme.StatColors

/** The sections of the club history screen. */
private enum class HistoryTab(val label: String) {
    SEASONS("Seasons"),
    PLAYERS("Players"),
    RECORDS("Records"),
    MONEY("Money")
}

/**
 * The club's museum: every season of the current career, the all-time player
 * leaderboards, the single-season record board, the trophy cabinet, the record
 * transfers and the all-time finance. Everything is read from the persisted
 * [com.footymanager.simulator.domain.model.ClubHistory], so past seasons never
 * change once they are archived.
 */
@Composable
fun ClubHistoryScreen(
    career: Career,
    onBack: () -> Unit,
    onOpenPlayer: (Long) -> Unit
) {
    var tab by remember { mutableStateOf(HistoryTab.SEASONS) }
    val history = career.clubHistory
    val club = career.userClub

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    text = "Club History",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard(accent = Color(club.primaryColor)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClubCrest(club = club, size = 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = club.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${League.byId(club.leagueId).name} • ${career.managerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Season ${career.season}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Seasons", "${history.seasons.size}")
                    StatCell("Trophies", "${history.totalTrophies}", valueColor = StatColors.elite)
                    StatCell("Manager games", "${career.managerRecord.matchesManaged}")
                    StatCell(
                        "Win rate",
                        "${career.managerRecord.winRatePercent}%",
                        valueColor = StatColors.forRating(career.managerRecord.winRatePercent)
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(HistoryTab.entries.toList(), key = { it.name }) { option ->
                    SelectorChip(
                        label = option.label,
                        selected = tab == option,
                        onClick = { tab = option }
                    )
                }
            }
        }

        when (tab) {
            HistoryTab.SEASONS -> {
                item { SeasonHistoryCard(career = career) }
                item { TrophyCabinetCard(career = career) }
            }
            HistoryTab.PLAYERS -> {
                item { LeaderboardCard("All-time top goalscorers", history.topScorers(), onOpenPlayer, kind = LeaderKind.GOALS) }
                item { LeaderboardCard("All-time top assists", history.topAssists(), onOpenPlayer, kind = LeaderKind.ASSISTS) }
                item { LeaderboardCard("Best all-time average rating", history.topRatings(), onOpenPlayer, kind = LeaderKind.RATING) }
                item { LeaderboardCard("Most appearances", history.topAppearances(), onOpenPlayer, kind = LeaderKind.APPEARANCES) }
                item { LeaderboardCard("Most player-of-the-match awards", history.mostManOfTheMatch(), onOpenPlayer, kind = LeaderKind.MOTM) }
            }
            HistoryTab.RECORDS -> {
                item { SingleSeasonRecordsCard(career = career) }
                item { LeaderboardCard("Top 10 most expensive players by total wages paid", history.topWages(), onOpenPlayer, kind = LeaderKind.WAGES) }
                item { LeaderboardCard("Most injured players", history.mostInjured(), onOpenPlayer, kind = LeaderKind.INJURIES) }
            }
            HistoryTab.MONEY -> {
                item { AllTimeFinanceCard(career = career) }
                item { RecordTransfersCard(career = career, received = false, onOpenPlayer = onOpenPlayer) }
                item { RecordTransfersCard(career = career, received = true, onOpenPlayer = onOpenPlayer) }
            }
        }
    }
}

@Composable
private fun SeasonHistoryCard(career: Career) {
    val archived = career.clubHistory.seasons.sortedByDescending { it.seasonNumber }
    val current = currentSeasonRecord(career)
    val rows = (listOfNotNull(current) + archived).distinctBy { it.seasonNumber }

    FmCard {
        SectionHeader("Season history")
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text(
                text = "Your first season is still in progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FmCard
        }
        rows.forEachIndexed { index, season ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            SeasonRow(season = season, live = season.seasonNumber == career.seasonNumber)
        }
    }
}

@Composable
private fun SeasonRow(season: ClubSeasonRecord, live: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (live) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = season.season,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            if (live) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "IN PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (season.finalPosition > 0) "${ordinalSuffix(season.finalPosition)} in ${season.leagueName}"
                else season.leagueName,
                style = MaterialTheme.typography.bodySmall,
                color = positionColor(season.finalPosition)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${season.played} played • ${season.won}W ${season.drawn}D ${season.lost}L • " +
                "${season.goalsFor} GF ${season.goalsAgainst} GA • ${season.points} pts",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (season.topScorerGoals > 0) {
            Text(
                text = "Top scorer: ${season.topScorerName} (${season.topScorerGoals})" +
                    if (season.topAssisterAssists > 0) " • Assists: ${season.topAssisterName} (${season.topAssisterAssists})" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (season.europeanSummary.isNotBlank()) {
            Text(
                text = season.europeanSummary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (season.trophies.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "🏆 " + season.trophies.joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = StatColors.elite,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TrophyCabinetCard(career: Career) {
    val trophies = career.clubHistory.trophies.sortedWith(
        compareByDescending<TrophyRecord> { it.season }.thenBy { it.competition }
    )
    FmCard {
        SectionHeader("Trophy cabinet") {
            Text(
                text = "${trophies.size}",
                style = MaterialTheme.typography.titleSmall,
                color = StatColors.elite,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        if (trophies.isEmpty()) {
            Text(
                text = "No silverware yet. Win a competition to fill the cabinet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FmCard
        }
        val grouped = trophies.groupBy { it.competition }
        grouped.forEach { (competition, won) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = StatColors.elite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = competition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = won.sortedByDescending { it.season }.joinToString(", ") { it.season },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "×${won.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = StatColors.elite
                )
            }
        }
    }
}

private enum class LeaderKind { GOALS, ASSISTS, RATING, APPEARANCES, MOTM, WAGES, INJURIES }

@Composable
private fun LeaderboardCard(
    title: String,
    entries: List<PlayerCareerRecord>,
    onOpenPlayer: (Long) -> Unit,
    kind: LeaderKind
) {
    FmCard(padding = 12.dp) {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                text = "Not enough data yet — play a full season to build these records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FmCard
        }
        entries.forEachIndexed { index, player ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { onOpenPlayer(player.playerId) }
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp)
                )
                PositionChip(position = player.position)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = player.playerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleFor(player, kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = valueFor(player, kind),
                    style = MaterialTheme.typography.titleSmall,
                    color = when (index) {
                        0 -> StatColors.elite
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private fun valueFor(player: PlayerCareerRecord, kind: LeaderKind): String = when (kind) {
    LeaderKind.GOALS -> "${player.goals}"
    LeaderKind.ASSISTS -> "${player.assists}"
    LeaderKind.RATING -> Fmt.rating(player.averageRating)
    LeaderKind.APPEARANCES -> "${player.appearances}"
    LeaderKind.MOTM -> "${player.manOfTheMatch}"
    LeaderKind.WAGES -> Fmt.money(player.totalWagesPaid)
    LeaderKind.INJURIES -> "${player.injuryDays}d"
}

private fun subtitleFor(player: PlayerCareerRecord, kind: LeaderKind): String = when (kind) {
    LeaderKind.GOALS ->
        "${player.appearances} apps • ${"%.2f".format(player.goalsPerMatch)} goals/game • ${player.seasons} season(s)"
    LeaderKind.ASSISTS ->
        "${player.appearances} apps • ${"%.2f".format(player.assistsPerMatch)} assists/game"
    LeaderKind.RATING ->
        "${player.seasons} season(s) • ${player.appearances} apps"
    LeaderKind.APPEARANCES ->
        "${player.starts} starts • ${player.substituteAppearances} sub • ${player.minutesPlayed} mins"
    LeaderKind.MOTM ->
        "${player.appearances} apps • ${player.seasons} season(s)"
    LeaderKind.WAGES ->
        "${player.seasons} season(s) • ${player.injuries} injuries"
    LeaderKind.INJURIES ->
        "${player.injuries} injuries • longest ${player.longestInjuryDays} days"
}

@Composable
private fun SingleSeasonRecordsCard(career: Career) {
    val history = career.clubHistory
    val current = currentSeasonRecord(career)
    val records = listOf(
        "Most goals in a season" to history.recordGoalsInSeason,
        "Most assists in a season" to history.recordAssistsInSeason,
        "Best average rating in a season" to history.recordRatingInSeason,
        "Most appearances in a season" to history.recordAppearancesInSeason,
        "Most clean sheets in a season" to history.recordCleanSheetsInSeason,
        "Most player-of-the-match awards in a season" to history.recordManOfTheMatchInSeason,
        "Highest points total" to history.recordHighestPoints,
        "Lowest points total" to history.recordLowestPoints,
        "Most goals scored in a season" to history.recordMostGoals,
        "Fewest goals conceded in a season" to history.recordFewestConceded
    )

    FmCard {
        SectionHeader("Single-season records")
        Spacer(Modifier.height(8.dp))
        if (records.all { it.second == null }) {
            Text(
                text = if (current != null) {
                    "Records are archived at the end of each season. Finish this campaign to set the first marks."
                } else {
                    "No records yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FmCard
        }
        records.forEach { (label, holder) ->
            if (holder != null) {
                RecordLine(label = label, holder = holder)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun RecordLine(label: String, holder: SeasonRecordHolder) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = holder.playerName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatRecordValue(holder.value),
                style = MaterialTheme.typography.titleSmall,
                color = StatColors.elite
            )
            Text(
                text = holder.season,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRecordValue(value: Double): String =
    if (value == value.toInt().toDouble()) "${value.toInt()}" else Fmt.rating(value)

@Composable
private fun AllTimeFinanceCard(career: Career) {
    val history = career.clubHistory
    val finance = career.financialHistory
    val seasons = history.seasons

    val totalRevenue = finance.sumOf { it.totalIncome }
    val totalExpenses = finance.sumOf { it.totalExpense }
    val totalWages = finance.sumOf { it.wageSpend }
    val transferSpend = seasons.sumOf { it.transferSpend }
    val transferIncome = seasons.sumOf { it.transferIncome }
    val netTransfer = transferSpend - transferIncome
    val bestRevenue = finance.maxByOrNull { it.totalIncome }
    val bestProfit = finance.maxByOrNull { it.netProfit }
    val highestFeePaid = history.transfersPaid.maxByOrNull { it.fee }
    val highestFeeReceived = history.transfersReceived.maxByOrNull { it.fee }

    FmCard {
        SectionHeader("All-time finance")
        Spacer(Modifier.height(8.dp))
        FinanceLine("Total revenue", totalRevenue, StatColors.elite)
        FinanceLine("Total expenses", totalExpenses, StatColors.bad)
        FinanceLine("Total wages paid", totalWages, StatColors.bad)
        FinanceLine("Total transfer spending", transferSpend, StatColors.bad)
        FinanceLine("Total transfer income", transferIncome, StatColors.elite)
        FinanceLine(
            "Net transfer spending",
            netTransfer,
            if (netTransfer >= 0) StatColors.average else StatColors.elite
        )
        Spacer(Modifier.height(6.dp))
        bestRevenue?.let {
            Text(
                text = "Highest season revenue: ${Fmt.money(it.totalIncome)} (${it.season})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        bestProfit?.let {
            Text(
                text = "Highest season profit: ${Fmt.money(it.netProfit)} (${it.season})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        highestFeePaid?.let {
            Text(
                text = "Highest fee paid: ${Fmt.money(it.fee)} for ${it.playerName} (${it.season})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        highestFeeReceived?.let {
            Text(
                text = "Highest fee received: ${Fmt.money(it.fee)} for ${it.playerName} (${it.season})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinanceLine(label: String, amount: Long, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = Fmt.money(amount),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecordTransfersCard(career: Career, received: Boolean, onOpenPlayer: (Long) -> Unit) {
    val history = career.clubHistory
    val entries = if (received) history.transfersReceived else history.transfersPaid
    val title = if (received) "Highest transfer fees received" else "Highest transfer fees paid"
    val emptyBody = if (received) {
        "No sales on record yet."
    } else {
        "No signings on record yet."
    }

    FmCard {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                text = emptyBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FmCard
        }
        entries.take(10).forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { onOpenPlayer(entry.playerId) }
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.playerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (received) "to ${entry.otherClubName} • ${entry.season}"
                        else "from ${entry.otherClubName} • ${entry.season}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = Fmt.money(entry.fee),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (received) StatColors.elite else StatColors.bad
                )
            }
        }
        if (received && entries.size > 1) {
            val biggestProfit = entries.maxByOrNull { it.fee }
            biggestProfit?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Most profitable sale: ${it.playerName} — ${Fmt.money(it.fee)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The current, still-unfinished season, shown live in the season history. */
private fun currentSeasonRecord(career: Career): ClubSeasonRecord? {
    val league = League.byId(career.userLeagueId)
    val sorted = career.sortedTable(career.userLeagueId)
    if (sorted.isEmpty()) return null
    val position = sorted.indexOfFirst { it.clubId == career.userClubId } + 1
    val row = sorted.firstOrNull { it.clubId == career.userClubId }
    val squad = career.squadOf(career.userClubId)
    val topScorer = squad.maxByOrNull { it.seasonStats.goals }
    val topAssister = squad.maxByOrNull { it.seasonStats.assists }
    val bestRating = squad.filter { it.seasonStats.ratedMatches >= 5 }
        .maxByOrNull { it.seasonStats.averageRating }
    return ClubSeasonRecord(
        season = career.season,
        seasonNumber = career.seasonNumber,
        leagueName = league.name,
        leagueId = league.id,
        finalPosition = position,
        played = row?.played ?: 0,
        won = row?.won ?: 0,
        drawn = row?.drawn ?: 0,
        lost = row?.lost ?: 0,
        goalsFor = row?.goalsFor ?: 0,
        goalsAgainst = row?.goalsAgainst ?: 0,
        points = row?.points ?: 0,
        topScorerName = topScorer?.name ?: "-",
        topScorerGoals = topScorer?.seasonStats?.goals ?: 0,
        topAssisterName = topAssister?.name ?: "-",
        topAssisterAssists = topAssister?.seasonStats?.assists ?: 0,
        bestRatingName = bestRating?.name ?: "-",
        bestRating = bestRating?.seasonStats?.averageRating ?: 0.0,
        championName = sorted.firstOrNull()?.let { career.club(it.clubId)?.name } ?: ""
    )
}

/** "1st", "2nd", "3rd", "4th"… */
private fun ordinalSuffix(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}
