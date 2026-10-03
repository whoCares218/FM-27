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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.data.AnimationSpeed
import com.footymanager.simulator.domain.data.GameSettings
import com.footymanager.simulator.domain.engine.FinanceEngine
import com.footymanager.simulator.domain.engine.SeasonSummaryBuilder
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.ObjectiveStatus
import com.footymanager.simulator.domain.model.SeasonSummary
import com.footymanager.simulator.domain.model.TrainingFocus
import com.footymanager.simulator.ui.components.EmptyState
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.ChartBar
import com.footymanager.simulator.ui.components.CompositionBar
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.MonthlyBarChart
import com.footymanager.simulator.ui.components.InfoPill
import com.footymanager.simulator.ui.components.RatingBadge
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.navigation.MoreDestination
import com.footymanager.simulator.ui.navigation.moreDestinations
import com.footymanager.simulator.ui.theme.StatColors

/** The "More" tab: a hub for every secondary screen. */
@Composable
fun MoreScreen(
    career: Career,
    onNavigate: (String) -> Unit,
    onSave: () -> Unit,
    onMainMenu: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Club management")
        }
        items(moreDestinations, key = { it.route }) { destination ->
            MoreRow(destination = destination, onClick = { onNavigate(destination.route) })
        }

        item {
            Spacer(Modifier.height(6.dp))
            SectionHeader("Career")
        }
        item {
            FmPrimaryButton(text = "Save Career", onClick = onSave)
        }
        item {
            FmSecondaryButton(text = "Return to Main Menu", onClick = onMainMenu)
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Career: ${career.managerName} at ${career.userClub.name} • ${career.season}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MoreRow(destination: MoreDestination, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = destination.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = destination.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ------------------------------------------------------------------ finances

@Composable
fun FinancesScreen(career: Career) {
    val summary = remember(career) { FinanceEngine.summarise(career) }
    val recentLedger = remember(career.ledger) { career.ledger.takeLast(40).reversed() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FmCard(accent = MaterialTheme.colorScheme.primary) {
                SectionHeader("Club balance")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Fmt.money(summary.balance),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.balance >= 0) MaterialTheme.colorScheme.primary
                    else StatColors.bad
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Transfer budget", Fmt.money(summary.transferBudget))
                    StatCell("Wage budget", Fmt.money(summary.wageBudget))
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Season ${career.season} summary")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Income", Fmt.money(summary.totalIncome), valueColor = StatColors.elite)
                    StatCell("Expense", Fmt.money(summary.totalExpense), valueColor = StatColors.bad)
                    StatCell(
                        "Net",
                        Fmt.money(summary.netSeason),
                        valueColor = if (summary.netSeason >= 0) StatColors.elite else StatColors.bad
                    )
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Monthly cash flow")
                Spacer(Modifier.height(10.dp))
                MonthlyBarChart(
                    bars = summary.monthly.map { ChartBar(it.label, it.income, it.expense) }
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Wages")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell(
                        "Weekly bill",
                        Fmt.money(summary.weeklyWageBill),
                        valueColor = if (summary.isWageBillHealthy) MaterialTheme.colorScheme.primary
                        else StatColors.bad
                    )
                    StatCell(
                        "Headroom",
                        Fmt.money(summary.wageHeadroom),
                        valueColor = if (summary.wageHeadroom >= 0) StatColors.elite else StatColors.bad
                    )
                    StatCell("Paid this season", Fmt.money(summary.wageSpend))
                }
                Spacer(Modifier.height(8.dp))
                if (!summary.isWageBillHealthy) {
                    InfoPill(
                        text = "Wage bill exceeds the board's budget",
                        color = StatColors.bad
                    )
                } else {
                    InfoPill(text = "Wage bill within budget", color = StatColors.elite)
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Season ${career.season} transfers")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Spent", Fmt.money(summary.transferSpend), valueColor = StatColors.poor)
                    StatCell("Received", Fmt.money(summary.transferIncome), valueColor = StatColors.elite)
                    StatCell(
                        "Net",
                        Fmt.money(summary.netTransfer),
                        valueColor = if (summary.netTransfer >= 0) StatColors.elite else StatColors.poor
                    )
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Where the money comes from")
                Spacer(Modifier.height(10.dp))
                CompositionBar(
                    segments = listOf(
                        "Matchday" to summary.matchdayRevenue.coerceAtLeast(0),
                        "Prize money" to summary.prizeMoney.coerceAtLeast(0),
                        "Sponsorship" to summary.sponsorshipRevenue.coerceAtLeast(0),
                        "Player sales" to summary.transferIncome.coerceAtLeast(0),
                        "Other" to summary.otherIncome.coerceAtLeast(0)
                    ).filter { it.second > 0 }
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Where the money goes")
                Spacer(Modifier.height(10.dp))
                CompositionBar(
                    segments = listOf(
                        "Wages" to summary.wageSpend.coerceAtLeast(0),
                        "Transfers" to summary.transferSpend.coerceAtLeast(0),
                        "Stadium" to summary.stadiumSpend.coerceAtLeast(0)
                    ).filter { it.second > 0 }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Stadium capacity: ${"%,d".format(career.userClub.stadiumCapacity)} seats.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { SectionHeader("Recent transactions") }

        if (recentLedger.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Star,
                    title = "No transactions yet",
                    body = "Wages, transfers and matchday revenue will appear here."
                )
            }
        }

        items(recentLedger, key = { it.id }) { entry ->
            FmCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entry.date.display()} • ${entry.category.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = (if (entry.amount >= 0) "+" else "") + Fmt.money(entry.amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.amount >= 0) StatColors.elite else StatColors.bad
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------- board

@Composable
fun BoardScreen(career: Career) {
    val board = career.board
    val position = career.userLeaguePosition

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FmCard(accent = confidenceColor(board.confidence)) {
                SectionHeader("Board confidence")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${board.confidence}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = confidenceColor(board.confidence)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = board.lastEvaluation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                InfoPill(
                    text = when {
                        board.confidence >= 70 -> "Job secure"
                        board.confidence >= 45 -> "Under normal pressure"
                        board.confidence >= 25 -> "Under pressure"
                        else -> "Job at risk"
                    },
                    color = confidenceColor(board.confidence)
                )
            }
        }

        item {
            FmCard {
                SectionHeader("League performance")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Current position", if (position == 0) "-" else "$position")
                    StatCell("Target", "${career.userClub.targetLeaguePosition}th")
                    StatCell("Matchday", "${career.matchdayIndex + 1}/${career.totalMatchdays()}")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = career.userClub.boardExpectation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { SectionHeader("Season objectives") }

        items(board.objectives, key = { it.title }) { objective ->
            val color = when (objective.status) {
                ObjectiveStatus.ACHIEVED -> StatColors.elite
                ObjectiveStatus.ON_TRACK -> StatColors.good
                ObjectiveStatus.AT_RISK -> StatColors.average
                ObjectiveStatus.FAILED -> StatColors.bad
            }
            FmCard(accent = color, padding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = objective.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = objective.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = objective.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Squad development")
                Spacer(Modifier.height(8.dp))
                val youngsters = career.userSquad.filter { it.age <= 23 }
                val improving = youngsters.filter { it.potential > it.overall }
                Text(
                    text = "${youngsters.size} players aged 23 or under, " +
                        "${improving.size} with room to grow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Squad size", "${career.userSquad.size}")
                    StatCell("Avg age", Fmt.rating(career.userSquad.map { it.age }.average()))
                    StatCell("Avg rating", Fmt.rating(career.userSquad.map { it.overall }.average()))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- news

@Composable
fun NewsScreen(career: Career) {
    val news = remember(career.news) { career.news.reversed() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (news.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Star,
                    title = "No news yet",
                    body = "Match reports, transfers, injuries and board updates will appear here."
                )
            }
        }
        items(news, key = { it.id }) { item ->
            FmCard(padding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = categoryLabel(item.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor(item.category),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.date.display()} • ${item.season}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------- statistics

@Composable
fun StatisticsScreen(career: Career) {
    val squad = career.userSquad
    val league = career.sortedTable(career.userLeagueId)
    val row = league.firstOrNull { it.clubId == career.userClubId }

    val scorers = remember(squad) { squad.sortedByDescending { it.seasonStats.goals }.take(10) }
    val assisters = remember(squad) { squad.sortedByDescending { it.seasonStats.assists }.take(10) }
    val rated = remember(squad) {
        squad.filter { it.seasonStats.ratedMatches >= 3 }
            .sortedByDescending { it.seasonStats.averageRating }
            .take(10)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FmCard {
                SectionHeader("Club statistics")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Played", "${row?.played ?: 0}")
                    StatCell("Won", "${row?.won ?: 0}", valueColor = StatColors.elite)
                    StatCell("Drawn", "${row?.drawn ?: 0}", valueColor = StatColors.average)
                    StatCell("Lost", "${row?.lost ?: 0}", valueColor = StatColors.bad)
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Scored", "${row?.goalsFor ?: 0}")
                    StatCell("Conceded", "${row?.goalsAgainst ?: 0}")
                    StatCell("Points", "${row?.points ?: 0}", valueColor = MaterialTheme.colorScheme.primary)
                    StatCell("Win rate", winRate(row))
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Season averages")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Squad rating", Fmt.rating(squad.map { it.overall }.average()))
                    StatCell("Goals/game", Fmt.rating(goalsPerGame(career)))
                    StatCell("Clean sheets", "${squad.filter { it.position.isGoalkeeper }.sumOf { it.seasonStats.cleanSheets }}")
                }
            }
        }

        item { SectionHeader("Top scorers") }
        items(scorers, key = { "scorer-${it.id}" }) { player ->
            StatLeaderRow(
                name = player.name,
                detail = "${player.position.short} • ${player.seasonStats.appearances} apps",
                value = "${player.seasonStats.goals}",
                valueColor = StatColors.elite
            )
        }

        item { SectionHeader("Top assisters") }
        items(assisters, key = { "assister-${it.id}" }) { player ->
            StatLeaderRow(
                name = player.name,
                detail = "${player.position.short} • ${player.seasonStats.appearances} apps",
                value = "${player.seasonStats.assists}",
                valueColor = MaterialTheme.colorScheme.primary
            )
        }

        item { SectionHeader("Best average rating") }
        items(rated, key = { "rated-${it.id}" }) { player ->
            StatLeaderRow(
                name = player.name,
                detail = "${player.seasonStats.starts} starts • ${player.seasonStats.goals}G ${player.seasonStats.assists}A",
                value = Fmt.rating(player.seasonStats.averageRating),
                valueColor = StatColors.forRating((player.seasonStats.averageRating * 10).toInt())
            )
        }
    }
}

@Composable
private fun StatLeaderRow(
    name: String,
    detail: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    FmCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

private fun winRate(row: com.footymanager.simulator.domain.model.TableRow?): String {
    if (row == null || row.played == 0) return "-"
    return "${(row.won * 100 / row.played)}%"
}

private fun goalsPerGame(career: Career): Double {
    val row = career.sortedTable(career.userLeagueId).firstOrNull { it.clubId == career.userClubId }
    if (row == null || row.played == 0) return 0.0
    return row.goalsFor.toDouble() / row.played
}

// ------------------------------------------------------------------ training

@Composable
fun TrainingScreen(career: Career, onSetFocus: (TrainingFocus) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FmCard {
                SectionHeader("Weekly training focus")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Training runs between matches. The focus shapes which attributes " +
                        "your players improve and how quickly they recover.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(TrainingFocus.entries.toList(), key = { it.name }) { focus ->
            val selected = focus == career.trainingFocus
            FmCard(
                accent = if (selected) MaterialTheme.colorScheme.primary else null,
                onClick = { onSetFocus(focus) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = focus.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selected) {
                        InfoPill("Active", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = focus.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Squad condition")
                Spacer(Modifier.height(10.dp))
                val tired = career.userSquad.count { it.fitness < 70 }
                val injured = career.userSquad.count { it.isInjured }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell(
                        "Avg fitness",
                        "${career.userSquad.map { it.fitness }.average().toInt()}%",
                        valueColor = if (tired == 0) StatColors.elite else StatColors.average
                    )
                    StatCell("Below 70%", "$tired", valueColor = if (tired > 3) StatColors.poor else StatColors.good)
                    StatCell("Injured", "$injured", valueColor = if (injured > 0) StatColors.bad else StatColors.elite)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsScreen(
    settings: GameSettings,
    hasCareer: Boolean,
    onBack: () -> Unit,
    onSetSound: (Boolean) -> Unit,
    onSetVibration: (Boolean) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetDifficulty: (Difficulty) -> Unit,
    onSetAnimationSpeed: (AnimationSpeed) -> Unit,
    onResetCareer: () -> Unit,
    onAbout: () -> Unit
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Presentation")
                Spacer(Modifier.height(10.dp))
                SettingSwitch("Sound effects", settings.soundEnabled, onSetSound)
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Vibration", settings.vibrationEnabled, onSetVibration)
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Dark theme", settings.darkTheme, onSetDarkTheme)
            }
        }

        item {
            FmCard {
                SectionHeader("Animation speed")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimationSpeed.entries.forEach { speed ->
                        val selected = speed == settings.animationSpeed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onSetAnimationSpeed(speed) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = speed.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Controls how quickly the match timeline plays out.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Default difficulty")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Difficulty.entries.forEach { option ->
                        val selected = option == settings.difficulty
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onSetDifficulty(option) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = settings.difficulty.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Applies to new careers. The current career keeps the difficulty it started with.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Career data")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "All game data is stored locally on this device. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FmSecondaryButton(
                    text = "Reset career",
                    onClick = { showResetConfirm = true },
                    enabled = hasCareer
                )
            }
        }

        item {
            FmSecondaryButton(text = "About", onClick = onAbout)
        }
    }

    if (showResetConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset career?") },
            text = {
                Text("This permanently deletes your saved career and cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onResetCareer()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ----------------------------------------------------------- season summary

@Composable
fun SeasonSummaryScreen(
    career: Career,
    onStartNextSeason: () -> Unit
) {
    val summary: SeasonSummary = remember(career) {
        career.lastSeasonSummary ?: SeasonSummaryBuilder.build(career)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FmCard(accent = MaterialTheme.colorScheme.primary) {
                Text(
                    text = "SEASON SUMMARY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${summary.season} • ${summary.clubName}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary.leagueName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Final standings")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${summary.finalPosition}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = StatColors.forRating(
                            (100 - summary.finalPosition * 4).coerceIn(40, 95)
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = ordinal(summary.finalPosition),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${summary.points} points",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Played", "${summary.played}")
                    StatCell("Won", "${summary.won}", valueColor = StatColors.elite)
                    StatCell("Drawn", "${summary.drawn}", valueColor = StatColors.average)
                    StatCell("Lost", "${summary.lost}", valueColor = StatColors.bad)
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Scored", "${summary.goalsFor}")
                    StatCell("Conceded", "${summary.goalsAgainst}")
                    StatCell("GD", "${summary.goalsFor - summary.goalsAgainst}")
                    StatCell("Champions", summary.championName, valueColor = StatColors.elite)
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Player awards")
                Spacer(Modifier.height(10.dp))
                AwardRow("Top scorer", summary.topScorerName, "${summary.topScorerGoals} goals", StatColors.elite)
                Spacer(Modifier.height(10.dp))
                AwardRow("Top assister", summary.topAssisterName, "${summary.topAssisterAssists} assists", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                AwardRow(
                    "Player of the season",
                    summary.playerOfSeasonName,
                    Fmt.rating(summary.playerOfSeasonRating),
                    StatColors.average
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Transfer business")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Spent", Fmt.money(summary.transferSpend), valueColor = StatColors.poor)
                    StatCell("Received", Fmt.money(summary.transferIncome), valueColor = StatColors.elite)
                    StatCell(
                        "Net",
                        Fmt.money(summary.transferIncome - summary.transferSpend),
                        valueColor = if (summary.transferIncome >= summary.transferSpend) StatColors.elite else StatColors.poor
                    )
                }
            }
        }

        if (summary.trophies.isNotEmpty()) {
            item {
                FmCard(accent = StatColors.elite) {
                    SectionHeader("Achievements")
                    Spacer(Modifier.height(8.dp))
                    summary.trophies.forEach { trophy ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = StatColors.elite,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = trophy,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Board evaluation")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary.boardEvaluation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Board confidence: ${career.board.confidence}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = confidenceColor(career.board.confidence)
                )
            }
        }

        item {
            FmPrimaryButton(
                text = "Start ${nextSeasonLabel(career)} Season",
                onClick = onStartNextSeason
            )
        }
    }
}

@Composable
private fun AwardRow(
    label: String,
    name: String,
    detail: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.titleSmall,
            color = color
        )
    }
}

private fun nextSeasonLabel(career: Career): String {
    val start = 2025 + career.seasonNumber + 1
    return "$start/${(start + 1).toString().takeLast(2)}"
}

private fun ordinal(position: Int): String = when (position % 10) {
    1 -> if (position == 11) "${position}th" else "${position}st"
    2 -> if (position == 12) "${position}th" else "${position}nd"
    3 -> if (position == 13) "${position}th" else "${position}rd"
    else -> "${position}th"
}

// -------------------------------------------------------------- how to play

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    val sections = remember {
        listOf(
            "Choosing a club" to
                "Pick any club from six European leagues. Reputation, stadium size and " +
                "budgets all shape how hard the job is. The board sets a league objective " +
                "you must meet.",
            "Building a squad" to
                "The Squad tab lists every player with their rating, age, fitness, morale " +
                "and form. Sort by any column and filter by position to find weaknesses.",
            "Tactics and lineup" to
                "The Tactics tab is where matches are won. Choose a formation, then tap a " +
                "position on the pitch to pick a player. Players out of position perform " +
                "worse, so keep an eye on the warning panel.",
            "Playing matches" to
                "Tap Play Match on the Home screen for the full match-day experience, or " +
                "Quick Sim to jump straight to the result. Matches are simulated from " +
                "player ability, tactics, fitness, form, morale, home advantage and " +
                "randomness.",
            "Training" to
                "Set a weekly training focus. Attack, Defence, Fitness and Possession " +
                "direct which attributes improve. Young players grow fastest with regular " +
                "first-team football; veterans decline.",
            "Transfers" to
                "Search the market, then bid. The asking price and wage demand are shown " +
                "up front, and a bid within about 6% of the asking price is normally " +
                "accepted. You can also sell or release your own players.",
            "Finances" to
                "Wages are paid weekly and matchday revenue comes in from home games. " +
                "Keep the wage bill inside the board's budget or confidence will suffer.",
            "The board" to
                "The board judges league position, finances and squad development. " +
                "Confidence rises with good results and falls with bad ones. Reach zero " +
                "and your job is at risk.",
            "Saving" to
                "Your career saves automatically after every match and transfer, and you " +
                "can save manually from the More tab. Everything is stored locally on " +
                "your device."
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
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
                    text = "How to Play",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        items(sections, key = { it.first }) { (title, body) ->
            FmCard {
                SectionHeader(title)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            FmCard {
                SectionHeader("Good luck")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Win the league, balance the books, and build a squad that lasts. " +
                        "Every decision matters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** About dialog content, reused from the settings screen. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Football Manager 26/27 Simulator") },
        text = {
            Column {
                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "An offline football club management simulation. All clubs, players " +
                        "and competitions in this game are fictional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No account is required and no data leaves your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
