package com.footymanager.simulator.ui.screens

import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
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
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.CupState
import com.footymanager.simulator.domain.model.cupRoundLabel
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.TableRow
import com.footymanager.simulator.domain.model.europeanCompetitions
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.EmptyState
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmDropdown
import com.footymanager.simulator.ui.components.FormStrip
import com.footymanager.simulator.ui.components.ScreenTitle
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.theme.StatColors

/** League table, results and upcoming fixtures for the selected competition. */
@Composable
fun CompetitionHubScreen(
    career: Career,
    onOpenLeague: () -> Unit,
    onOpenLeagueById: (String) -> Unit = { onOpenLeague() },
    onOpenChampionsLeague: () -> Unit,
    onOpenEurope: () -> Unit = onOpenChampionsLeague,
    onOpenCompetition: (CompetitionType) -> Unit = { onOpenEurope() },
    onOpenFixtures: () -> Unit
) {
    var tab by remember { mutableStateOf(HubTab.DOMESTIC) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Competitions", "Season ${career.season}") }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(HubTab.entries.toList(), key = { it.name }) { option ->
                    SelectorChip(
                        label = option.label,
                        selected = tab == option,
                        onClick = { tab = option }
                    )
                }
            }
        }

        when (tab) {
            HubTab.DOMESTIC -> {
                item { DomesticTab(career = career, onOpenLeague = onOpenLeague, onOpenFixtures = onOpenFixtures) }
            }
            HubTab.EUROPE -> {
                item { EuropeTab(career = career, onOpenCompetition = onOpenCompetition) }
            }
            HubTab.CUPS -> {
                item { CupsTab(career = career, onOpenFixtures = onOpenFixtures) }
            }
            HubTab.OTHERS -> {
                item {
                    OthersTab(
                        career = career,
                        onOpenLeague = { id -> onOpenLeagueById(id) }
                    )
                }
            }
        }
    }
}

/** The tabs of the competition hub. */
enum class HubTab(val label: String) {
    DOMESTIC("Domestic"),
    EUROPE("Europe"),
    CUPS("Cups"),
    OTHERS("Other leagues")
}

@Composable
private fun DomesticTab(career: Career, onOpenLeague: () -> Unit, onOpenFixtures: () -> Unit) {
    val league = League.byId(career.userLeagueId)
    val position = career.userLeaguePosition
    val table = remember(career.table, career.userLeagueId) { career.sortedTable(career.userLeagueId) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FmCard(accent = MaterialTheme.colorScheme.primary, onClick = onOpenLeague) {
            SectionHeader(league.name) {
                Text(
                    text = if (position == 0) "—" else "$position",
                    style = MaterialTheme.typography.titleMedium,
                    color = positionColor(position)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Domestic league table, results and upcoming fixtures.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap to open the country and league selectors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // The whole division, not just the top of it: the manager should be able
        // to read every club's record from the hub without opening anything.
        if (table.isNotEmpty()) {
            FmCard(padding = 10.dp) {
                SectionHeader(league.name) {
                    Text(
                        text = "${table.size} clubs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                table.forEachIndexed { index, row ->
                    HubTableLine(
                        position = index + 1,
                        career = career,
                        row = row,
                        isUser = row.clubId == career.userClubId
                    )
                }
            }
        }

        FmCard(onClick = onOpenFixtures) {
            SectionHeader("Fixtures and results")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Your full calendar across every competition, with dates and results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap to open",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EuropeTab(career: Career, onOpenCompetition: (CompetitionType) -> Unit) {
    val userIn = europeanCompetitions.firstOrNull {
        career.europeanState(it).participantIds.contains(career.userClubId)
    }
    var selected by remember(userIn) {
        mutableStateOf(userIn ?: CompetitionType.CHAMPIONS_LEAGUE)
    }
    val state = career.europeanState(selected)
    val table = state.sortedTable()
    val userPosition = if (state.participantIds.contains(career.userClubId)) {
        state.positionOf(career.userClubId)
    } else 0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FmCard(padding = 12.dp) {
            FmDropdown(
                label = "Competition",
                options = europeanCompetitions,
                selected = selected,
                onSelect = { selected = it },
                optionLabel = { it.label }
            )
        }

        FmCard(padding = 10.dp, onClick = { onOpenCompetition(selected) }) {
            SectionHeader(selected.label) {
                Text(
                    text = when {
                        !state.active -> "Pre-season"
                        state.winnerClubId != null -> "Complete"
                        else -> "MD ${state.currentMatchday.coerceAtMost(state.leaguePhaseMatchdays)}/${state.leaguePhaseMatchdays}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (userPosition > 0) {
                    "Your club: position $userPosition of ${state.participantIds.size}"
                } else {
                    "${state.participantIds.size}-team league phase with knockouts. Tap to open the bracket."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (table.isNotEmpty()) {
            FmCard(padding = 10.dp) {
                SectionHeader("${selected.label} table") {
                    Text(
                        text = "${table.size} clubs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        league = League.forCompetition(selected),
                        clubCount = table.size,
                        isUserClub = row.clubId == career.userClubId,
                        onClick = null
                    )
                }
            }
        } else {
            EmptyState(
                icon = Icons.Outlined.EmojiEvents,
                title = "No table yet",
                body = "The league phase table appears once the competition begins."
            )
        }
    }
}

@Composable
private fun CupsTab(career: Career, onOpenFixtures: () -> Unit) {
    val cup = career.cup
    val cupFixtures = remember(career.fixtures) {
        career.fixtures.filter { it.competition == CompetitionType.DOMESTIC_CUP && it.involves(career.userClubId) }
            .sortedBy { it.competitionRound }
    }
    val played = cupFixtures.filter { it.isPlayed }
    val next = cupFixtures.firstOrNull { !it.isPlayed }
    val eliminated = played.any {
        it.goalsFor(career.userClubId) < it.goalsAgainst(career.userClubId)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FmCard {
            SectionHeader("Domestic Cup") {
                Text(
                    text = when {
                        cup.winnerClubId == career.userClubId -> "Winners"
                        eliminated -> "Eliminated"
                        next != null -> cupRoundLabel(next.competitionRound)
                        else -> "Not entered"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        cup.winnerClubId == career.userClubId -> StatColors.elite
                        eliminated -> StatColors.bad
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A 32-club knockout with sides from every division. One-off ties, " +
                    "extra time and penalties if needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("Rounds played", "${played.size}")
                StatCell(
                    "Won",
                    "${played.count { it.goalsFor(career.userClubId) > it.goalsAgainst(career.userClubId) }}",
                    valueColor = StatColors.elite
                )
                StatCell(
                    "Round",
                    when {
                        cup.winnerClubId != null -> "Final"
                        cup.active -> cupRoundLabel(cup.round.coerceIn(1, CupState.ROUNDS))
                        else -> "—"
                    }
                )
            }
        }

        if (cupFixtures.isNotEmpty()) {
            FmCard(padding = 10.dp) {
                SectionHeader("Your cup run")
                Spacer(Modifier.height(8.dp))
                cupFixtures.forEach { match ->
                    val opponent = career.club(match.opponentOf(career.userClubId))
                    val isHome = match.isHomeFor(career.userClubId)
                    val won = match.isPlayed &&
                        match.goalsFor(career.userClubId) > match.goalsAgainst(career.userClubId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cupRoundLabel(match.competitionRound),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(84.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ClubCrest(club = opponent ?: career.userClub, size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = opponent?.name ?: "-",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (match.isPlayed) "${match.goalsFor(career.userClubId)}-${match.goalsAgainst(career.userClubId)}"
                            else "vs",
                            style = MaterialTheme.typography.titleSmall,
                            color = when {
                                !match.isPlayed -> MaterialTheme.colorScheme.onSurfaceVariant
                                won -> StatColors.elite
                                match.goalsFor(career.userClubId) == match.goalsAgainst(career.userClubId) ->
                                    StatColors.average
                                else -> StatColors.bad
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isHome) "H" else "A",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            EmptyState(
                icon = Icons.Outlined.EmojiEvents,
                title = "No cup fixtures yet",
                body = "Cup rounds appear on your calendar as the season progresses."
            )
        }

        if (cup.winnerClubId != null) {
            val winner = career.club(cup.winnerClubId)
            FmCard(accent = StatColors.elite) {
                SectionHeader("Cup winners")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClubCrest(club = winner ?: career.userClub, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${winner?.name ?: "—"} lifted the cup",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        FmCard(onClick = onOpenFixtures) {
            Text(
                text = "Open the full fixture list",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun OthersTab(career: Career, onOpenLeague: (String) -> Unit) {
    val leagues = remember(career.clubs) {
        League.all.filter { league -> career.clubs.any { it.leagueId == league.id } }
            .filter { it.id != career.userLeagueId }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FmCard {
            SectionHeader("Other leagues")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Compact tables for competitions you are not in. Tap view full table " +
                    "to open the complete standings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        leagues.forEach { league ->
            val table = career.sortedTable(league.id)
            if (table.isNotEmpty()) {
                MiniTableCard(
                    career = career,
                    title = league.name,
                    rows = table,
                    onViewFull = { onOpenLeague(league.id) }
                )
            }
        }
        // Continental competitions the user is not in are worth a look too.
        europeanCompetitions.forEach { competition ->
            val state = career.europeanState(competition)
            val userIn = state.participantIds.contains(career.userClubId)
            val rows = state.sortedTable()
            if (state.active && !userIn && rows.isNotEmpty()) {
                MiniTableCard(
                    career = career,
                    title = competition.label,
                    rows = rows,
                    onViewFull = { onOpenLeague(League.forCompetition(competition).id) }
                )
            }
        }
    }
}

/** A compact top-5 table with a shortcut to the full standings. */
@Composable
private fun MiniTableCard(
    career: Career,
    title: String,
    rows: List<TableRow>,
    onViewFull: () -> Unit
) {
    FmCard(padding = 10.dp) {
        SectionHeader(title) {
            Text(
                text = "Top 5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        rows.take(5).forEachIndexed { index, row ->
            HubTableLine(
                position = index + 1,
                career = career,
                row = row,
                isUser = row.clubId == career.userClubId
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "VIEW FULL TABLE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onViewFull)
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HubTableLine(position: Int, career: Career, row: TableRow, isUser: Boolean) {
    val club = career.club(row.clubId) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp)
        )
        ClubCrest(club = club, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = club.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${row.played}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center
        )
        Text(
            text = if (row.goalDifference > 0) "+${row.goalDifference}" else "${row.goalDifference}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )
        Text(
            text = "${row.points}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.Center
        )
    }
}

/** League table, results and upcoming fixtures for the selected competition. */
@Composable
fun LeagueScreen(
    career: Career,
    onOpenFixtures: () -> Unit,
    onOpenClub: (Long) -> Unit = {},
    initialLeagueId: String? = null
) {
    val availableLeagues = remember(career.clubs) {
        (League.all + League.european.values)
            .filter { league ->
                League.european.values.contains(league) ||
                    career.clubs.any { it.leagueId == league.id }
            }
    }
    val countries = remember(availableLeagues) {
        availableLeagues.map { it.country }.distinct().sorted()
    }
    val initialLeague = remember(initialLeagueId, career.userLeagueId) {
        initialLeagueId?.let { League.byId(it) } ?: League.byId(career.userLeagueId)
    }
    var selectedCountry by remember(initialLeague) {
        mutableStateOf(initialLeague.country)
    }
    var selectedLeagueId by remember(initialLeague) {
        mutableStateOf(initialLeague.id)
    }

    // The league list follows the chosen country; picking a country resets the
    // league to that country's first competition.
    val countryLeagues = availableLeagues.filter { it.country == selectedCountry }
    val league = countryLeagues.firstOrNull { it.id == selectedLeagueId }
        ?: League.byId(selectedLeagueId)
    val europeanCompetition = europeanCompetitions.firstOrNull { League.forCompetition(it).id == league.id }
    val table = remember(career.table, career.championsLeague, career.europaLeague, career.conferenceLeague, league.id) {
        europeanCompetition?.let { career.europeanState(it).sortedTable() } ?: career.sortedTable(league.id)
    }
    val clubCount = remember(career.clubs, career.championsLeague, league.id) {
        europeanCompetition?.let { career.europeanState(it).participantIds.size }
            ?: career.clubs.count { it.leagueId == league.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FmCard(padding = 12.dp) {
                FmDropdown(
                    label = "Country",
                    options = countries,
                    selected = selectedCountry,
                    onSelect = { country ->
                        selectedCountry = country
                        availableLeagues.firstOrNull { it.country == country }
                            ?.let { selectedLeagueId = it.id }
                    },
                    optionLabel = { it }
                )
                Spacer(Modifier.height(10.dp))
                FmDropdown(
                    label = "League",
                    options = countryLeagues,
                    selected = league,
                    onSelect = { selectedLeagueId = it.id },
                    optionLabel = { it.name },
                    optionSupporting = { "Tier ${it.tier}" }
                )
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
                        clubCount = clubCount,
                        isUserClub = row.clubId == career.userClubId,
                        onClick = { onOpenClub(row.clubId) }
                    )
                }
            }
        }

        item {
            Text(
                text = "Green = title or European places. Red = relegation.",
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
            modifier = Modifier.width(20.dp)
        )
        Spacer(Modifier.width(26.dp))
        Text(
            text = "CLUB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        listOf("P", "W", "D", "L", "GF", "GA", "GD", "PTS").forEach { header ->
            Text(
                text = header,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(if (header == "PTS") 30.dp else 20.dp)
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
    clubCount: Int,
    isUserClub: Boolean,
    onClick: (() -> Unit)?
) {
    val club = career.club(row.clubId) ?: return
    val zoneColor = when {
        position <= 1 -> StatColors.elite
        position <= league.championsLeaguePlaces -> StatColors.good
        position <= 6 -> StatColors.average
        position > (clubCount - league.relegationPlaces) -> StatColors.bad
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
        Spacer(Modifier.width(5.dp))
        // The user's position slides into place when it changes, so a good run
        // is visible without reading the number.
        val animatedPosition by animateIntAsState(
            targetValue = position,
            animationSpec = tween(500),
            label = "leaguePosition"
        )
        Text(
            text = if (isUserClub) "$animatedPosition" else "$position",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isUserClub) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp)
        )
        ClubCrest(club = club, size = 22.dp)
        Spacer(Modifier.width(5.dp))
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
        TableCell("${row.goalsFor}")
        TableCell("${row.goalsAgainst}")
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
    width: androidx.compose.ui.unit.Dp = 20.dp
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
