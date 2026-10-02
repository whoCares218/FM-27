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
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.TableRow
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FormStrip
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.theme.StatColors

/** League table, results and upcoming fixtures for the selected competition. */
@Composable
fun LeagueScreen(
    career: Career,
    onOpenFixtures: () -> Unit
) {
    var selectedLeagueId by remember { mutableStateOf(career.userLeagueId) }
    val league = League.byId(selectedLeagueId)
    val table = remember(career.table, selectedLeagueId) { career.sortedTable(selectedLeagueId) }
    val leagues = League.all.filter { career.clubs.any { c -> c.leagueId == it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(leagues, key = { it.id }) { option ->
                    SelectorChip(
                        label = option.shortName,
                        selected = option.id == selectedLeagueId,
                        onClick = { selectedLeagueId = option.id }
                    )
                }
            }
        }

        item {
            FmCard(padding = 10.dp) {
                SectionHeader(league.name) {
                    Text(
                        text = "Matchday ${career.matchdayIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(10.dp))
                TableHeaderRow()
                Spacer(Modifier.height(4.dp))
                table.forEachIndexed { index, row ->
                    TableRowItem(
                        position = index + 1,
                        row = row,
                        career = career,
                        league = league,
                        isUserClub = row.clubId == career.userClubId,
                        onClick = null
                    )
                }
            }
        }

        item {
            Text(
                text = "Green = promotion or title. Red = relegation.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            FmCard(onClick = onOpenFixtures) {
                SectionHeader("Fixtures and results")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "See your full schedule, recent results and upcoming matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap to open",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp)
        )
        Spacer(Modifier.width(30.dp))
        Text(
            text = "CLUB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        listOf("P", "W", "D", "L", "GD", "PTS").forEach { header ->
            Text(
                text = header,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(if (header == "PTS") 30.dp else 22.dp)
            )
        }
    }
}

@Composable
private fun TableRowItem(
    position: Int,
    row: TableRow,
    career: Career,
    league: League,
    isUserClub: Boolean,
    onClick: (() -> Unit)?
) {
    val club = career.club(row.clubId) ?: return
    val zoneColor = when {
        position <= 1 -> StatColors.elite
        position <= league.championsLeaguePlaces -> StatColors.good
        position <= 6 -> StatColors.average
        position > (career.clubs.count { it.leagueId == league.id } - league.relegationPlaces) -> StatColors.bad
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                if (isUserClub) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(zoneColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$position",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp)
        )
        ClubCrest(club = club, size = 24.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = club.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isUserClub) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TableCell("${row.played}")
        TableCell("${row.won}")
        TableCell("${row.drawn}")
        TableCell("${row.lost}")
        TableCell(
            text = if (row.goalDifference > 0) "+${row.goalDifference}" else "${row.goalDifference}",
            color = when {
                row.goalDifference > 0 -> StatColors.good
                row.goalDifference < 0 -> StatColors.bad
                else -> null
            }
        )
        TableCell("${row.points}", bold = true, width = 30.dp)
    }
}

@Composable
private fun TableCell(
    text: String,
    color: Color? = null,
    bold: Boolean = false,
    width: androidx.compose.ui.unit.Dp = 22.dp
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(width)
    )
}

/** The user's own fixture list: results behind, matches ahead. */
@Composable
fun FixturesScreen(career: Career) {
    val fixtures = remember(career.fixtures, career.userClubId) {
        career.fixtures
            .filter { it.involves(career.userClubId) }
            .sortedWith(
                compareBy(
                    { it.date?.let { d -> d.year * 10_000 + d.month * 100 + d.day } ?: Int.MAX_VALUE },
                    { it.matchday },
                    { it.id }
                )
            )
    }
    val results = remember(career.results) { career.results.associateBy { it.matchId } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Season ${career.season} fixtures")
        }

        val nextMatchday = career.matchdayIndex + 1
        items(fixtures, key = { it.id }) { match ->
            val isHome = match.homeClubId == career.userClubId
            val opponent = career.clubOrThrow(match.opponentOf(career.userClubId))
            val result = results[match.id]
            val isNext = match.matchday == nextMatchday && !match.isPlayed

            FmCard(
                accent = if (isNext) MaterialTheme.colorScheme.primary else null,
                padding = 12.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.width(56.dp)) {
                        Text(
                            text = match.competition.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = match.date?.let { "${it.day}/${it.month}" } ?: "MD${match.matchday}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ClubCrest(club = opponent, size = 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = opponent.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isNext) "Next match"
                            else if (match.isPlayed) "Played"
                            else "${if (isHome) "Home" else "Away"} · Matchday ${match.matchday}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isNext) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (result != null) {
                        val userGoals = if (isHome) result.homeGoals else result.awayGoals
                        val oppGoals = if (isHome) result.awayGoals else result.homeGoals
                        val color = when {
                            userGoals > oppGoals -> StatColors.elite
                            userGoals == oppGoals -> StatColors.average
                            else -> StatColors.bad
                        }
                        Text(
                            text = "$userGoals - $oppGoals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    } else {
                        Text(
                            text = "vs",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
