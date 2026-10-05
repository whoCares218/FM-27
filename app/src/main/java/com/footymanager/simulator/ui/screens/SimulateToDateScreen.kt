package com.footymanager.simulator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.GameDate
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.ScreenTitle
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.theme.StatColors
import com.footymanager.simulator.viewmodel.SimulateDayResult
import com.footymanager.simulator.viewmodel.SimulateSummary
import com.footymanager.simulator.viewmodel.SimulateToDateState
import kotlinx.coroutines.delay

/** Calendar screen: pick a date between tomorrow and the end of the season. */
@Composable
fun SimulateToDateScreen(
    career: Career,
    onBack: () -> Unit,
    onConfirm: (GameDate) -> Unit
) {
    val today = career.date
    val horizon = career.simulateHorizon()
    val earliest = today.plusDays(1)

    var visibleMonth by remember { mutableStateOf(earliest.month) }
    var visibleYear by remember { mutableStateOf(earliest.year) }
    var selected by remember { mutableStateOf<GameDate?>(null) }
    var confirming by remember { mutableStateOf(false) }

    val monthFixtures = remember(career.fixtures, visibleMonth, visibleYear) {
        career.fixtures
            .filter { it.date?.year == visibleYear && it.date?.month == visibleMonth }
            .groupBy { it.date!! }
    }

    val monthLabel = "${GameDate.MONTH_NAMES[visibleMonth - 1]} $visibleYear"
    val firstWeekday = GameDate(visibleYear, visibleMonth, 1).dayOfWeekIndex
    val daysInMonth = GameDate.daysInMonth(visibleYear, visibleMonth)
    val cells: List<Int?> = List(firstWeekday) { null } + (1..daysInMonth).toList()

    fun stepMonth(delta: Int) {
        var m = visibleMonth + delta
        var y = visibleYear
        if (m < 1) { m = 12; y-- }
        if (m > 12) { m = 1; y++ }
        visibleMonth = m
        visibleYear = y
    }

    val canStepBack = visibleYear > earliest.year ||
        (visibleYear == earliest.year && visibleMonth > earliest.month)
    val canStepForward = visibleYear < horizon.year ||
        (visibleYear == horizon.year && visibleMonth < horizon.month)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle("Simulate to date", "Season ${career.season}")
        }

        item {
            FmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = today.numeric(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Season ends ${horizon.numeric()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            FmCard(padding = 12.dp) {
                // ---- Month navigation ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MonthArrow(
                        forward = false,
                        enabled = canStepBack,
                        onClick = { stepMonth(-1) }
                    )
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    MonthArrow(
                        forward = true,
                        enabled = canStepForward,
                        onClick = { stepMonth(1) }
                    )
                }
                Spacer(Modifier.height(10.dp))

                // ---- Weekday header ----
                Row(modifier = Modifier.fillMaxWidth()) {
                    GameDate.DAY_NAMES.forEach { day ->
                        Text(
                            text = day.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // ---- Day grid ----
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (day == null) {
                                    Spacer(Modifier.size(40.dp))
                                } else {
                                    val date = GameDate(visibleYear, visibleMonth, day)
                                    CalendarDay(
                                        date = date,
                                        today = today,
                                        horizon = horizon,
                                        earliest = earliest,
                                        selected = selected == date,
                                        matchCount = monthFixtures[date]?.size ?: 0,
                                        onClick = { selected = date }
                                    )
                                }
                            }
                        }
                        if (week.size < 7) {
                            repeat(7 - week.size) {
                                Box(modifier = Modifier.weight(1f)) { Spacer(Modifier.size(40.dp)) }
                            }
                        }
                    }
                }
            }
        }

        item {
            val chosen = selected
            if (chosen == null) {
                FmCard {
                    Text(
                        text = "Tap a date to see its fixtures. You can simulate to any day " +
                            "from tomorrow up to the end of the season.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val fixtures = career.fixturesOnDate(chosen)
                    .sortedWith(compareBy({ it.competition.ordinal }, { it.id }))
                FmCard(padding = 12.dp) {
                    SectionHeader(chosen.display()) {
                        Text(
                            text = "${fixtures.size} fixtures",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (fixtures.isEmpty()) {
                        Text(
                            text = "No matches scheduled on this date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        fixtures.forEach { match ->
                            FixtureLine(career = career, match = match)
                        }
                    }
                }
            }
        }

        item {
            val chosen = selected
            if (chosen != null && !confirming) {
                val count = remember(career, chosen) {
                    career.fixtures.count { !it.isPlayed && it.date != null && !it.date.isAfter(chosen) }
                }
                FmCard(accent = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = "SELECTED DATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = chosen.numeric(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$count matches will be simulated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FmPrimaryButton(
                        text = "SIMULATE TO THIS DATE",
                        onClick = { confirming = true },
                        icon = Icons.Outlined.FastForward
                    )
                }
            } else if (chosen != null && confirming) {
                SimulateConfirmCard(
                    career = career,
                    target = chosen,
                    onCancel = { confirming = false },
                    onConfirm = { onConfirm(chosen) }
                )
            }
        }

        item {
            FmSecondaryButton(text = "Back", onClick = onBack)
        }
    }
}

@Composable
private fun MonthArrow(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (forward) Icons.AutoMirrored.Filled.ArrowForward
            else Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = if (forward) "Next month" else "Previous month",
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun CalendarDay(
    date: GameDate,
    today: GameDate,
    horizon: GameDate,
    earliest: GameDate,
    selected: Boolean,
    matchCount: Int,
    onClick: () -> Unit
) {
    val isToday = date == today
    val selectable = !date.isBefore(earliest) && !date.isAfter(horizon)
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        !selectable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .padding(2.dp)
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(
                if (isToday && !selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                } else Modifier
            )
            .clickable(enabled = selectable, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${date.day}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        if (matchCount > 0 && selectable) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary
                    )
            )
        }
    }
}

@Composable
private fun FixtureLine(career: Career, match: Match) {
    val home = career.club(match.homeClubId)
    val away = career.club(match.awayClubId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(26.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(competitionColor(match.competition))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.competition.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${home?.name ?: "-"} vs ${away?.name ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun competitionColor(competition: CompetitionType): Color = when (competition) {
    CompetitionType.CHAMPIONS_LEAGUE -> StatColors.elite
    CompetitionType.EUROPA_LEAGUE -> StatColors.good
    CompetitionType.CONFERENCE_LEAGUE -> StatColors.average
    CompetitionType.DOMESTIC_CUP -> StatColors.poor
    CompetitionType.FRIENDLY -> StatColors.average
    CompetitionType.LEAGUE -> StatColors.good
}

/** Confirmation dialog content, shown before any simulation begins. */
@Composable
fun SimulateConfirmCard(
    career: Career,
    target: GameDate,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val from = career.date
    val count = remember(career, target) {
        career.fixtures.count { !it.isPlayed && it.date != null && !it.date.isAfter(target) }
    }
    FmCard(accent = MaterialTheme.colorScheme.primary) {
        SectionHeader("Simulate season")
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCell("From", from.numeric())
            StatCell("To", target.numeric())
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Matches to simulate: $count",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Are you sure? This advances the whole football world.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FmSecondaryButton(text = "Cancel", onClick = onCancel)
            }
            Box(modifier = Modifier.weight(1f)) {
                FmPrimaryButton(
                    text = "Confirm",
                    onClick = onConfirm,
                    icon = Icons.Filled.Check
                )
            }
        }
    }
}

/**
 * The animated progress screen. The whole run has already been computed; this
 * reveals the completed results one at a time at roughly a match per second.
 */
@Composable
fun SimulateProgressScreen(
    state: SimulateToDateState,
    revealDelayMillis: Long,
    onAdvance: () -> Unit,
    onContinue: () -> Unit
) {
    val revealCount = state.currentIndex.coerceAtMost(state.total)
    val visibleResults = state.results.take(revealCount)

    LaunchedEffect(state.currentIndex, state.finished) {
        if (!state.finished && state.currentIndex < state.total) {
            delay(revealDelayMillis)
            onAdvance()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ScreenTitle(
            if (state.finished) "Simulation complete" else "Simulating…",
            "${state.fromDate.numeric()} → ${state.toDate.numeric()}"
        )

        Spacer(Modifier.height(10.dp))

        val progress = if (state.total == 0) 1f else revealCount.toFloat() / state.total
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(400),
            label = "simProgress"
        )
        FmCard(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$revealCount / ${state.total}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (state.finished) "Done" else "Matches played",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleResults.asReversed(), key = { it.hashCode() + it.date.toEpochDay() }) { result ->
                ResultLine(result)
            }
        }

        if (state.finished) {
            Spacer(Modifier.height(10.dp))
            FmPrimaryButton(text = "CONTINUE", onClick = onContinue)
        }
    }
}

@Composable
private fun ResultLine(result: SimulateDayResult) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(120))
    ) {
        FmCard(
            padding = 10.dp,
            accent = if (result.isUserMatch) MaterialTheme.colorScheme.primary else null
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${result.date.numeric()} · ${result.competitionLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${result.homeName}  ${result.homeGoals}–${result.awayGoals}  ${result.awayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (result.isUserMatch) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The post-simulation summary report. */
@Composable
fun SimulateSummaryScreen(
    summary: SimulateSummary,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle("Simulation complete", "${summary.fromDate.numeric()} → ${summary.toDate.numeric()}")
        }

        item {
            FmCard(accent = MaterialTheme.colorScheme.primary) {
                Text(
                    text = "Matches simulated: ${summary.matchesSimulated}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Your record: ${summary.userMatches} played · " +
                        "${summary.userWins}W ${summary.userDraws}D ${summary.userLosses}L · " +
                        "${summary.goalsFor} scored, ${summary.goalsAgainst} conceded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard(padding = 12.dp) {
                SectionHeader("League position")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell(
                        "Before",
                        if (summary.positionBefore == 0) "—" else "${summary.positionBefore}"
                    )
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatCell(
                        "After",
                        if (summary.positionAfter == 0) "—" else "${summary.positionAfter}",
                        valueColor = if (summary.positionAfter in 1..4 && summary.positionAfter != 0)
                            StatColors.elite else null
                    )
                }
            }
        }

        if (summary.continentalNotes.isNotEmpty()) {
            item {
                FmCard {
                    SectionHeader("Europe")
                    Spacer(Modifier.height(8.dp))
                    summary.continentalNotes.forEach { note ->
                        Text(
                            text = "• $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            FmCard(padding = 12.dp) {
                SectionHeader("Finances")
                Spacer(Modifier.height(8.dp))
                val delta = summary.financeAfter - summary.financeBefore
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Balance", Fmt.money(summary.financeAfter))
                    StatCell(
                        "Change",
                        (if (delta >= 0) "+" else "-") + Fmt.money(kotlin.math.abs(delta)).removePrefix("£").let { "£$it" },
                        valueColor = if (delta >= 0) StatColors.good else StatColors.bad
                    )
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Squad")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Injuries", "${summary.injuries}")
                    StatCell("Suspensions", "${summary.suspensions}")
                    StatCell("Developed", "${summary.developed}", valueColor = StatColors.elite)
                }
            }
        }

        item {
            FmPrimaryButton(text = "CONTINUE", onClick = onContinue)
        }
    }
}
