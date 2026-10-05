package com.footymanager.simulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.AttributeKey
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.ui.components.AttributeBar
import com.footymanager.simulator.ui.components.FitnessBar
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.MoraleDots
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.RatingBadge
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.theme.StatColors

/** Full player profile: attributes, contract, form and season statistics. */
@Composable
fun PlayerDetailScreen(
    career: Career,
    playerId: Long,
    onBack: () -> Unit,
    onSetCaptain: (Long) -> Unit,
    onToggleSubstitute: (Long) -> Unit,
    onSellPlayer: (Long) -> Unit,
    onRelease: (Long) -> Unit
) {
    val player = career.player(playerId)
    if (player == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "This player is no longer at the club.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val club = player.clubId?.let { career.club(it) }
    val isUserPlayer = player.clubId == career.userClubId
    val isStarter = career.selection.startingXi.any { it.playerId == player.id }
    val isOnBench = player.id in career.selection.substitutes
    var tab by remember { mutableStateOf(ProfileTab.OVERVIEW) }
    var confirmRelease by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Header ----
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Player Profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingBadge(player.overall, size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = player.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (career.selection.captainId == player.id) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "Captain",
                                    tint = StatColors.elite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PositionChip(player.position)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${player.age} years • ${player.nationality} • ${player.preferredFoot.label} foot",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = club?.name ?: "Free agent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ---- Tab selector ----
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ProfileTab.entries.toList(), key = { it.name }) { option ->
                    SelectorChip(
                        label = option.label,
                        selected = option == tab,
                        onClick = { tab = option }
                    )
                }
            }
        }

        // ---- Availability alert ----
        if (!player.isAvailable) {
            item {
                FmCard(accent = StatColors.bad) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocalHospital,
                            contentDescription = null,
                            tint = StatColors.bad,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                player.isInjured ->
                                    "Out injured: ${player.injury.type.label} (${player.injury.weeksRemaining} week(s))"
                                else -> "Suspended for ${player.suspensionWeeks} match(es)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatColors.bad
                        )
                    }
                }
            }
        }

        when (tab) {
            ProfileTab.OVERVIEW -> {
                item {
                    FmCard {
                        SectionHeader("Condition")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Fitness",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp)
                            )
                            FitnessBar(player.fitness, width = 120.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${player.fitness}%",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Morale",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp)
                            )
                            MoraleDots(player.morale)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = player.morale.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Form",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp)
                            )
                            FitnessBar((player.form * 10).toInt(), width = 120.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = Fmt.rating(player.form),
                                style = MaterialTheme.typography.titleSmall,
                                color = StatColors.forRating((player.form * 10).toInt())
                            )
                        }
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Ability")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Overall", "${player.overall}", valueColor = StatColors.forRating(player.overall))
                            StatCell("Potential", "${player.potential}", valueColor = StatColors.forRating(player.potential))
                            StatCell("Headroom", "+${(player.potential - player.overall).coerceAtLeast(0)}")
                            StatCell("Value", Fmt.money(player.value), valueColor = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Season ${career.season} at a glance")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Apps", "${player.seasonStats.appearances}")
                            StatCell("Goals", "${player.seasonStats.goals}", valueColor = StatColors.elite)
                            StatCell("Assists", "${player.seasonStats.assists}", valueColor = StatColors.elite)
                            StatCell("Avg rating", Fmt.rating(player.seasonStats.averageRating))
                        }
                    }
                }
            }

            ProfileTab.PERFORMANCE -> {
                item {
                    FmCard {
                        SectionHeader("Season ${career.season} statistics")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Apps", "${player.seasonStats.appearances}")
                            StatCell("Starts", "${player.seasonStats.starts}")
                            StatCell("Goals", "${player.seasonStats.goals}", valueColor = StatColors.elite)
                            StatCell("Assists", "${player.seasonStats.assists}", valueColor = StatColors.elite)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Avg rating", Fmt.rating(player.seasonStats.averageRating))
                            StatCell("Yellows", "${player.seasonStats.yellowCards}", valueColor = StatColors.average)
                            StatCell("Reds", "${player.seasonStats.redCards}", valueColor = StatColors.bad)
                            StatCell("MOTM", "${player.seasonStats.manOfTheMatch}")
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Minutes", "${player.seasonStats.minutesPlayed}")
                            StatCell("Clean sheets", "${player.seasonStats.cleanSheets}")
                            StatCell("Conceded", "${player.seasonStats.goalsConceded}")
                            StatCell("Injuries", "${player.seasonStats.injuriesSuffered}")
                        }
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Career totals")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Apps", "${player.careerStats.appearances}")
                            StatCell("Goals", "${player.careerStats.goals}")
                            StatCell("Assists", "${player.careerStats.assists}")
                            StatCell("Avg rating", Fmt.rating(player.careerStats.averageRating))
                        }
                    }
                }
            }

            ProfileTab.ATTRIBUTES -> {
                item {
                    FmCard {
                        SectionHeader("Attributes")
                        Spacer(Modifier.height(10.dp))
                        val attributes = player.attributes
                        val keys = if (player.position == Position.GK) {
                            listOf(AttributeKey.GOALKEEPING, AttributeKey.PHYSICAL, AttributeKey.PASSING)
                        } else {
                            listOf(
                                AttributeKey.PACE, AttributeKey.SHOOTING, AttributeKey.PASSING,
                                AttributeKey.DRIBBLING, AttributeKey.DEFENDING, AttributeKey.PHYSICAL
                            )
                        }
                        keys.forEachIndexed { index, key ->
                            if (index > 0) Spacer(Modifier.height(8.dp))
                            AttributeBar(label = key.label, value = attributes.get(key))
                        }
                        if (player.position == Position.GK) {
                            Spacer(Modifier.height(8.dp))
                            AttributeBar(label = "Shot stopping", value = attributes.goalkeeping)
                        }
                    }
                }
            }

            ProfileTab.CONTRACT -> {
                item {
                    FmCard {
                        SectionHeader("Contract")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Wage", Fmt.wage(player.wagePerWeek))
                            StatCell(
                                "Expires in",
                                if (player.contractYearsRemaining == 0) "Expired"
                                else "${player.contractYearsRemaining} year(s)"
                            )
                            StatCell("At club", "${player.seasonsAtClub} season(s)")
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Value", Fmt.money(player.value), valueColor = MaterialTheme.colorScheme.primary)
                            StatCell("Age", "${player.age}")
                            StatCell("Nationality", player.nationality)
                            StatCell("Foot", player.preferredFoot.label)
                        }
                    }
                }
            }

            ProfileTab.HISTORY -> {
                item {
                    FmCard {
                        SectionHeader("Development")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Current", "${player.overall}", valueColor = StatColors.forRating(player.overall))
                            StatCell("Peak", "${player.potential}", valueColor = StatColors.forRating(player.potential))
                            StatCell("Room to grow", "+${(player.potential - player.overall).coerceAtLeast(0)}")
                            StatCell("Seasons here", "${player.seasonsAtClub}")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = developmentNote(player.age, player.potential - player.overall),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Career record")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Apps", "${player.careerStats.appearances}")
                            StatCell("Starts", "${player.careerStats.starts}")
                            StatCell("Goals", "${player.careerStats.goals}", valueColor = StatColors.elite)
                            StatCell("Assists", "${player.careerStats.assists}", valueColor = StatColors.elite)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Yellows", "${player.careerStats.yellowCards}", valueColor = StatColors.average)
                            StatCell("Reds", "${player.careerStats.redCards}", valueColor = StatColors.bad)
                            StatCell("MOTM", "${player.careerStats.manOfTheMatch}")
                            StatCell("Injuries", "${player.careerStats.injuriesSuffered}")
                        }
                    }
                }
            }
        }

        // ---- Actions (user squad only) ----
        if (isUserPlayer) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader("Actions")
                    Spacer(Modifier.height(8.dp))
                    FmSecondaryButton(
                        text = if (career.selection.captainId == player.id) "Remove as captain"
                        else "Make captain",
                        onClick = { onSetCaptain(player.id) },
                        enabled = isStarter || career.selection.captainId == player.id
                    )
                    Spacer(Modifier.height(8.dp))
                    FmSecondaryButton(
                        text = if (isOnBench) "Remove from substitutes" else "Add to substitutes",
                        onClick = { onToggleSubstitute(player.id) },
                        enabled = !isStarter
                    )
                    Spacer(Modifier.height(8.dp))
                    FmSecondaryButton(
                        text = "Sell player",
                        onClick = { onSellPlayer(player.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                    FmSecondaryButton(
                        text = "Release player",
                        onClick = { confirmRelease = true }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Selling opens the Transfers tab with this player ready to " +
                            "list. Releasing removes him with no fee.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("Release ${player.name}?") },
            text = {
                Text(
                    "He will leave the club immediately with no transfer fee and no " +
                        "money coming back. If you want to cash in, list him in the " +
                        "Transfers tab instead."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRelease = false
                        onRelease(player.id)
                    }
                ) { Text("Release", color = StatColors.bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRelease = false }) { Text("Cancel") }
            }
        )
    }
}

/** Sections of the player profile. */
enum class ProfileTab(val label: String) {
    OVERVIEW("Overview"),
    PERFORMANCE("Performance"),
    ATTRIBUTES("Attributes"),
    CONTRACT("Contract"),
    HISTORY("History")
}

/** One-line read on where a player is in his development curve. */
private fun developmentNote(age: Int, headroom: Int): String = when {
    age <= 21 && headroom >= 6 -> "A young player with plenty of room to improve. Give him minutes and training to speed his growth."
    age <= 21 -> "Young and close to his ceiling, but still developing physically."
    age <= 27 && headroom >= 4 -> "Entering his prime with some room to grow."
    age <= 27 -> "In his prime; expect a consistent level of performance."
    age <= 31 -> "Experienced. Development will slow and he may begin to decline."
    else -> "In the veteran stage of his career; physical attributes are likely to decline."
}
