package com.footymanager.simulator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.unit.sp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.GameDate
import com.footymanager.simulator.domain.model.League
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
import com.footymanager.simulator.viewmodel.SimulateStatus
import com.footymanager.simulator.viewmodel.SimulateSummary
import com.footymanager.simulator.viewmodel.SimulateToDateState

/** Subtle marker colour for a competition, used on the calendar and result cards. */
private fun competitionColor(competition: CompetitionType): Color = when (competition) {
    CompetitionType.CHAMPIONS_LEAGUE -> StatColors.elite
    CompetitionType.EUROPA_LEAGUE -> StatColors.good
    CompetitionType.CONFERENCE_LEAGUE -> StatColors.average
    CompetitionType.DOMESTIC_CUP -> StatColors.poor
    CompetitionType.FRIENDLY -> StatColors.average
    CompetitionType.LEAGUE -> StatColors.good
}

/**
 * A compact, non-truncated competition label for the narrow calendar cell. The
 * real competition is read from the fixture: a domestic fixture resolves to its
 * own league's short name (Premier, La Liga, Bundesliga …), a continental tie to
 * "UCL"/"UEL"/"UECL" and the cup to "Cup".
 */
private fun competitionShortLabel(match: Match): String = when (match.competition) {
    CompetitionType.LEAGUE -> League.byId(match.leagueId).shortName
    CompetitionType.DOMESTIC_CUP -> "Cup"
    CompetitionType.CHAMPIONS_LEAGUE -> "UCL"
    CompetitionType.EUROPA_LEAGUE -> "UEL"
    CompetitionType.CONFERENCE_LEAGUE -> "UECL"
    CompetitionType.FRIENDLY -> "Friendly"
}

/**
 * Calendar screen: pick a date between tomorrow and the end of the season.
 *
 * Each day cell shows the day number plus, on a match day, the user's opponent
 * and competition, so the whole run-in is legible without tapping anything. Below
 * the calendar the sections run SIMULATE TO THIS DATE, YOUR FIXTURES then ALL
 * FIXTURES. Every competition label is read from the fixture, never hard-coded.
 */
@Composable
fun SimulateToDateScreen(
    career: Career,
    onBack: () -> Unit,
    onConfirm: (GameDate) -> Unit
) {
    val today = career.date
    val horizon = career.simulateHorizon()
    val earliest = today.plusDays(1)
    val userClubId = career.userClubId

    var visibleMonth by remember { mutableStateOf(earliest.month) }
    var visibleYear by remember { mutableStateOf(earliest.year) }
    var selected by remember { mutableStateOf<GameDate?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var showAllFixtures by remember { mutableStateOf(false) }

    val monthFixtures = remember(career.fixtures, visibleMonth, visibleYear, userClubId) {
        career.fixtures
            .filter { it.date?.year == visibleYear && it.date?.month == visibleMonth }
            .groupBy { it.date!! }
    }
    val monthUserFixtures = remember(career.fixtures, visibleMonth, visibleYear, userClubId) {
        career.fixtures
            .filter {
                it.involves(userClubId) && it.date?.year == visibleYear && it.date?.month == visibleMonth
            }
            .sortedWith(compareBy({ it.date?.year ?: 9999 }, { it.date?.month ?: 12 }, { it.date?.day ?: 31 }))
    }
    val monthUserByDate = remember(monthUserFixtures) { monthUserFixtures.groupBy { it.date!! } }

    // The user's remaining fixtures for the whole season, in chronological order.
    val seasonUserFixtures = remember(career.fixtures, userClubId, today) {
        career.fixtures
            .filter { it.involves(userClubId) && it.date != null && !it.date!!.isBefore(today) }
            .sortedBy { it.date!!.toEpochDay() }
    }
    val seasonByDate = remember(career.fixtures, today) {
        career.fixtures
            .filter { it.date != null && !it.date!!.isBefore(today) }
            .groupBy { it.date!! }
            .toSortedMap(compareBy { it.toEpochDay() })
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MonthArrow(forward = false, enabled = canStepBack, onClick = { stepMonth(-1) })
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    MonthArrow(forward = true, enabled = canStepForward, onClick = { stepMonth(1) })
                }
                Spacer(Modifier.height(8.dp))
                CompetitionLegend()
                Spacer(Modifier.height(8.dp))

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

                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                if (day == null) {
                                    Spacer(Modifier.size(52.dp))
                                } else {
                                    val date = GameDate(visibleYear, visibleMonth, day)
                                    val dayUserMatch = (monthUserByDate[date] ?: emptyList())
                                        .minByOrNull { it.competition.ordinal }
                                    CalendarDay(
                                        career = career,
                                        date = date,
                                        today = today,
                                        horizon = horizon,
                                        earliest = earliest,
                                        selected = selected == date,
                                        matchCount = monthFixtures[date]?.size ?: 0,
                                        userMatch = dayUserMatch,
                                        onClick = { selected = date }
                                    )
                                }
                            }
                        }
                        if (week.size < 7) {
                            repeat(7 - week.size) {
                                Box(modifier = Modifier.weight(1f)) { Spacer(Modifier.size(52.dp)) }
                            }
                        }
                    }
                }

                val chosen = selected
                if (chosen != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                    )
                    Spacer(Modifier.height(10.dp))
                    val dayFixtures = remember(career.fixtures, chosen) {
                        career.fixturesOnDate(chosen)
                            .sortedWith(compareBy({ it.competition.ordinal }, { it.id }))
                    }
                    Text(
                        text = chosen.display(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    if (dayFixtures.isEmpty()) {
                        Text(
                            text = "No matches scheduled on this date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        dayFixtures.forEach { match -> FixtureLine(career = career, match = match) }
                    }
                }
            }
        }

        // ---- 1. SIMULATE TO THIS DATE ----
        item {
            val chosen = selected
            if (chosen != null && confirming) {
                SimulateConfirmCard(
                    career = career,
                    target = chosen,
                    onCancel = { confirming = false },
                    onConfirm = { onConfirm(chosen) }
                )
            } else {
                FmCard(accent = MaterialTheme.colorScheme.primary) {
                    SectionHeader("Simulate to this date")
                    Spacer(Modifier.height(8.dp))
                    if (chosen == null) {
                        Text(
                            text = "Tap a date on the calendar. You can simulate to any day from " +
                                "tomorrow up to the end of the season.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "SIMULATE TO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = chosen.display(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        val count = remember(career, chosen) {
                            career.fixtures.count { !it.isPlayed && it.date != null && !it.date.isAfter(chosen) }
                        }
                        Text(
                            text = "$count matches will be simulated across every competition.",
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
                }
            }
        }

        // ---- 2. YOUR FIXTURES (chronological, whole season) ----
        item {
            FmCard(padding = 12.dp, accent = MaterialTheme.colorScheme.primary) {
                SectionHeader("Your fixtures") {
                    Text(
                        text = "${seasonUserFixtures.size} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (seasonUserFixtures.isEmpty()) {
                    Text(
                        text = "No fixtures remaining this season.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    seasonUserFixtures.forEach { match ->
                        UserFixtureLine(career = career, match = match)
                    }
                }
            }
        }

        // ---- 3. ALL FIXTURES (collapsible, lazy by day) ----
        item {
            FmCard(padding = 12.dp) {
                SectionHeader("All fixtures") {
                    FmSecondaryButton(
                        text = if (showAllFixtures) "Hide" else "Show",
                        onClick = { showAllFixtures = !showAllFixtures }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (showAllFixtures) {
                        "Every scheduled match this season, by date."
                    } else {
                        "Every scheduled match this season, by date. Tap Show to expand."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showAllFixtures) {
            items(seasonByDate.keys.toList(), key = { it.toEpochDay() }) { date ->
                FmCard(padding = 12.dp) {
                    Text(
                        text = date.display(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    seasonByDate[date]!!.forEach { match -> FixtureLine(career = career, match = match) }
                }
            }
        }

        item {
            FmSecondaryButton(text = "Back", onClick = onBack)
        }
    }
}

/** Colour key for the calendar markers, so the icons are never ambiguous. */
@Composable
private fun CompetitionLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot("League", StatColors.good)
        LegendDot("Europe", StatColors.elite)
        LegendDot("Cup", StatColors.poor)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthArrow(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
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
    career: Career,
    date: GameDate,
    today: GameDate,
    horizon: GameDate,
    earliest: GameDate,
    selected: Boolean,
    matchCount: Int,
    userMatch: Match?,
    onClick: () -> Unit
) {
    val isToday = date == today
    val selectable = !date.isBefore(earliest) && !date.isAfter(horizon)
    val hasUserMatch = userMatch != null
    val markerColor = userMatch?.let { competitionColor(it.competition) } ?: MaterialTheme.colorScheme.primary
    val opponentName = userMatch?.let { m ->
        career.club(m.opponentOf(career.userClubId))?.shortName
    }
    val competitionText = userMatch?.let { competitionShortLabel(it) }
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        hasUserMatch -> markerColor.copy(alpha = 0.16f)
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
            .height(52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(
                when {
                    hasUserMatch && !selected ->
                        Modifier.border(1.dp, markerColor, MaterialTheme.shapes.small)
                    isToday && !selected ->
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    else -> Modifier
                }
            )
            .clickable(enabled = selectable, onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${date.day}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday || selected || hasUserMatch) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        if (hasUserMatch && opponentName != null) {
            Text(
                text = opponentName,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else markerColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (competitionText != null) {
                Text(
                    text = competitionText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        } else if (matchCount > 0 && selectable) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

/** A compact "date · vs Opponent · competition" line for the user's fixtures. */
@Composable
private fun UserFixtureLine(career: Career, match: Match) {
    val isHome = match.isHomeFor(career.userClubId)
    val opponent = career.club(match.opponentOf(career.userClubId))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(30.dp)
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
                text = "${match.date?.numeric() ?: "—"} · ${if (isHome) "vs" else "at"} ${opponent?.name ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (isHome) "H" else "A",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FixtureLine(career: Career, match: Match) {
    val home = career.club(match.homeClubId)
    val away = career.club(match.awayClubId)
    val userInvolved = match.involves(career.userClubId)
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
                fontWeight = if (userInvolved) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
                FmPrimaryButton(text = "Confirm", onClick = onConfirm, icon = Icons.Filled.Check)
            }
        }
    }
}

/**
 * The progress screen. The world is simulated one matchday at a time by the view
 * model; this reveals only the user's own results as they arrive and keeps a
 * compact live panel showing where the club stands domestically and in Europe.
 * The progress bar tracks matchdays actually played, not matches revealed.
 */
@Composable
fun SimulateProgressScreen(
    state: SimulateToDateState,
    onContinue: () -> Unit
) {
    val visibleResults = state.results.take(state.currentIndex.coerceAtMost(state.total))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ScreenTitle(
            if (state.finished) "Simulation complete" else "Simulating to ${state.toDate.display()}",
            "${state.fromDate.numeric()} → ${state.toDate.numeric()}"
        )

        Spacer(Modifier.height(10.dp))

        val animatedProgress by animateFloatAsState(
            targetValue = state.progressFraction,
            animationSpec = tween(300),
            label = "simProgress"
        )
        FmCard(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.matchdaysDone} / ${state.matchdaysTotal} matchdays",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${state.matchesSimulated} / ${state.totalMatches} fixtures",
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

        state.status?.let { status ->
            Spacer(Modifier.height(10.dp))
            StatusPanel(status)
        }

        Spacer(Modifier.height(10.dp))

        if (visibleResults.isEmpty() && !state.finished) {
            FmCard {
                Text(
                    text = "Simulating the season… your club's results will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleResults.asReversed(), key = { it.matchId }) { result ->
                ResultLine(result)
            }
        }

        if (state.finished) {
            Spacer(Modifier.height(10.dp))
            FmPrimaryButton(text = "CONTINUE", onClick = onContinue)
        } else {
            Spacer(Modifier.height(10.dp))
            FmSecondaryButton(text = "SKIP ANIMATION", onClick = onContinue)
        }
    }
}

/** Compact live standings while the season runs. */
@Composable
private fun StatusPanel(status: SimulateStatus) {
    FmCard(padding = 12.dp) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DOMESTIC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = status.domesticLeagueName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        status.domesticChampion != null -> "${status.domesticChampion} — Champions"
                        status.domesticComplete -> "Season complete"
                        status.domesticPosition == 0 -> "—"
                        else -> "Position: ${ordinal(status.domesticPosition)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.domesticPosition in 1..4)
                        StatColors.elite else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EUROPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = status.europeanCompetition ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (!status.europeanParticipating) "Not participating" else status.europeanDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ResultLine(result: SimulateDayResult) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 }
    ) {
        FmCard(padding = 10.dp, accent = MaterialTheme.colorScheme.primary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${result.date.numeric()} · ${result.competitionLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${if (result.userIsHome) "vs" else "at"} ${result.opponentName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${result.userGoals}–${result.opponentGoals}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (result.outcome) {
                        1 -> StatColors.good
                        0 -> StatColors.average
                        else -> StatColors.bad
                    }
                )
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
            ScreenTitle(
                "Simulation complete",
                "From ${summary.fromDate.numeric()} to ${summary.toDate.numeric()}"
            )
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
                    text = "Matches played by your club: ${summary.userMatches}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Record: ${summary.userWins}W ${summary.userDraws}D ${summary.userLosses}L · " +
                        "Goals: ${summary.goalsFor} for, ${summary.goalsAgainst} against",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard(padding = 12.dp) {
                SectionHeader(summary.domesticLeagueName)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Before", if (summary.positionBefore == 0) "—" else ordinal(summary.positionBefore))
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatCell(
                        "After",
                        if (summary.positionAfter == 0) "—" else ordinal(summary.positionAfter),
                        valueColor = if (summary.positionAfter in 1..4) StatColors.elite else null
                    )
                }
                if (summary.domesticChampion != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${summary.domesticLeagueName} Winner: ${summary.domesticChampion}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = StatColors.elite
                    )
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Europe")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary.europeanCompetition ?: "No European competition",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                if (summary.europeanBefore != summary.europeanAfter) {
                    Text(
                        text = "${summary.europeanBefore} → ${summary.europeanAfter}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = summary.europeanAfter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                summary.continentalNotes.forEach { note ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                        (if (delta >= 0) "+" else "-") + Fmt.money(kotlin.math.abs(delta)),
                        valueColor = if (delta >= 0) StatColors.good else StatColors.bad
                    )
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Squad & events")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Injuries", "${summary.injuries}")
                    StatCell("Suspensions", "${summary.suspensions}")
                    StatCell("Developed", "${summary.developed}", valueColor = StatColors.elite)
                }
                if (summary.majorEvents.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = summary.majorEvents.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            FmPrimaryButton(text = "CONTINUE", onClick = onContinue)
        }
    }
}

/** Ordinal helper shared by the progress and summary panels. */
private fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
