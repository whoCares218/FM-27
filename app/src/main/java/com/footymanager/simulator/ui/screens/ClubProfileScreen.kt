package com.footymanager.simulator.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.TableRow
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.FormStrip
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.RatingBadge
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.theme.StatColors

/**
 * A profile page for any club in the game: identity, stadium, league standing,
 * form, leading players and recent fixtures. Reached from the league table.
 */
@Composable
fun ClubProfileScreen(
    career: Career,
    clubId: Long,
    onBack: () -> Unit,
    onOpenPlayer: (Long) -> Unit
) {
    val club = career.club(clubId)
    if (club == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "This club is not available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val league = League.byId(club.leagueId)
    val row = career.sortedTable(club.leagueId).firstOrNull { it.clubId == club.id }
    val position = career.positionOf(club.id, club.leagueId)
    val squad = remember(career.players, club.id) {
        career.squadOf(club.id).sortedByDescending { it.overall }
    }
    val form = remember(career.fixtures, club.id) { career.recentForm(club.id, 5) }
    val recent = remember(career.fixtures, club.id) {
        career.fixturesForClub(club.id).filter { it.isPlayed }.takeLast(4).reversed()
    }
    val upcoming = remember(career.fixtures, club.id) {
        career.fixturesForClub(club.id).filter { !it.isPlayed }.take(4)
    }
    val isUserClub = club.id == career.userClubId

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
                    text = "Club Profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- Identity header ----
        item {
            FmCard(accent = Color(club.primaryColor)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClubCrest(club = club, size = 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = club.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (club.nickname.isNotBlank()) {
                            Text(
                                text = "\"${club.nickname}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${club.city.ifBlank { club.country }} • ${league.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isUserClub) {
                        Text(
                            text = "YOUR CLUB",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ---- Key facts ----
        item {
            FmCard {
                SectionHeader("Club facts")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Reputation", "${club.reputation}", valueColor = StatColors.forRating(club.reputation))
                    StatCell("League pos.", if (position == 0) "—" else "$position", valueColor = positionColor(position))
                    StatCell("Squad", "${squad.size}")
                    StatCell("Avg rating", Fmt.rating(squad.map { it.overall }.average()))
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Stadium", club.stadiumName, modifier = Modifier.weight(1.6f))
                    StatCell("Capacity", "%,d".format(club.stadiumCapacity))
                    StatCell("Country", club.country)
                }
            }
        }

        // ---- Standing ----
        if (row != null) {
            item {
                FmCard {
                    SectionHeader("${league.name} standing")
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell("Played", "${row.played}")
                        StatCell("Won", "${row.won}", valueColor = StatColors.elite)
                        StatCell("Drawn", "${row.drawn}", valueColor = StatColors.average)
                        StatCell("Lost", "${row.lost}", valueColor = StatColors.bad)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell("Goals for", "${row.goalsFor}")
                        StatCell("Goals against", "${row.goalsAgainst}")
                        StatCell(
                            "Goal diff",
                            if (row.goalDifference > 0) "+${row.goalDifference}" else "${row.goalDifference}",
                            valueColor = when {
                                row.goalDifference > 0 -> StatColors.good
                                row.goalDifference < 0 -> StatColors.bad
                                else -> null
                            }
                        )
                        StatCell("Points", "${row.points}", valueColor = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // ---- Form ----
        item {
            FmCard {
                SectionHeader("Recent form")
                Spacer(Modifier.height(8.dp))
                if (form.isEmpty()) {
                    Text(
                        text = "No matches played yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FormStrip(results = form.map { formCharFor(it, club.id) })
                }
            }
        }

        // ---- Leading players ----
        item {
            SectionHeader("Key players")
        }
        items(squad.take(5), key = { "kp-${it.id}" }) { player ->
            FmCard(onClick = { onOpenPlayer(player.id) }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingBadge(player.overall, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = player.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PositionChip(player.position)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${player.age}y • POT ${player.potential}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = Fmt.money(player.value),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ---- Recent results ----
        if (recent.isNotEmpty()) {
            item { SectionHeader("Recent results") }
            items(recent, key = { "res-${it.id}" }) { match ->
                ClubFixtureRow(career = career, match = match, club = club)
            }
        }

        // ---- Upcoming ----
        if (upcoming.isNotEmpty()) {
            item { SectionHeader("Upcoming fixtures") }
            items(upcoming, key = { "up-${it.id}" }) { match ->
                ClubFixtureRow(career = career, match = match, club = club)
            }
        }
    }
}

@Composable
private fun ClubFixtureRow(career: Career, match: Match, club: Club) {
    val opponent = career.club(match.opponentOf(club.id)) ?: return
    val isHome = match.isHomeFor(club.id)
    val played = match.isPlayed
    val scored = match.goalsFor(club.id)
    val conceded = match.goalsAgainst(club.id)
    val outcomeColor = when {
        !played -> MaterialTheme.colorScheme.onSurfaceVariant
        scored > conceded -> StatColors.elite
        scored == conceded -> StatColors.average
        else -> StatColors.bad
    }

    FmCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isHome) "H" else "A",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isHome) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(16.dp)
            )
            ClubCrest(club = opponent, size = 26.dp)
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
                    text = match.competitionShortLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (played) "$scored - $conceded" else "vs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = outcomeColor
            )
        }
    }
}

/** Form character from a specific club's point of view. */
private fun formCharFor(match: Match, clubId: Long): Char {
    val scored = match.goalsFor(clubId)
    val conceded = match.goalsAgainst(clubId)
    return when {
        scored > conceded -> 'W'
        scored == conceded -> 'D'
        else -> 'L'
    }
}
