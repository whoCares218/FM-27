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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.KnockoutRound
import com.footymanager.simulator.domain.model.Stadium
import com.footymanager.simulator.domain.model.UclStatus
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.EmptyState
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.InfoPill
import com.footymanager.simulator.ui.components.ScreenTitle
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.theme.StatColors

/**
 * Champions League hub: the 36-team league phase table with qualification bands,
 * the user's European fixtures, and the knockout bracket as it develops.
 */
@Composable
fun ChampionsLeagueScreen(
    career: Career,
    onOpenClub: (Long) -> Unit = {}
) {
    val state = career.championsLeague

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Champions League", "League phase · ${career.season}") }

        if (!state.active) {
            item {
                FmCard {
                    Text(
                        text = "Your club has not qualified for the Champions League this season.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Finish high enough in your league to qualify next season.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@LazyColumn
        }

        val userStatus = state.statusOf(career.userClubId)
        item {
            FmCard(accent = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClubCrest(career.userClub, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = career.userClub.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Position ${state.positionOf(career.userClubId).let { if (it == 0) "-" else "$it" }} of 36",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    InfoPill(userStatus.label, color = statusColor(userStatus))
                }
                if (state.winnerClubId != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Winners: ${career.club(state.winnerClubId)?.name ?: "-"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = StatColors.elite
                    )
                }
            }
        }

        item { SectionHeader("League phase table") }
        item {
            FmCard(padding = 10.dp) {
                Text(
                    text = "Positions 1–8 go straight to the Round of 16. 9–24 enter the knockout play-offs.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val sorted = state.sortedTable()
                sorted.forEachIndexed { index, row ->
                    val position = index + 1
                    val isUser = row.clubId == career.userClubId
                    UclTableRow(
                        position = position,
                        clubName = career.club(row.clubId)?.name ?: "Unknown",
                        played = row.played,
                        goalDifference = row.goalDifference,
                        points = row.points,
                        status = state.statusOf(row.clubId),
                        isUser = isUser,
                        onClick = { onOpenClub(row.clubId) }
                    )
                    if (position == 8 || position == 24) {
                        QualificationLine(
                            label = if (position == 8) "Round of 16" else "Play-off line",
                            color = if (position == 8) StatColors.elite else StatColors.average
                        )
                    }
                }
            }
        }

        if (state.ties.isNotEmpty()) {
            item { SectionHeader("Knockout bracket") }
            val rounds = KnockoutRound.entries.filter { round -> state.ties.any { it.round == round } }
            for (round in rounds) {
                val ties = state.ties.filter { it.round == round }
                item {
                    FmCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = round.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (round.legs == 2) "Two legs" else "Single match",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        ties.forEach { tie ->
                            BracketTieRow(tie = tie, career = career)
                        }
                    }
                }
            }
            if (state.winnerClubId != null) {
                item {
                    FmCard(accent = StatColors.elite) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.EmojiEvents,
                                contentDescription = null,
                                tint = StatColors.elite,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Champions League winners",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = career.club(state.winnerClubId)?.name ?: "-",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A horizontal qualification cut-off line inside the league-phase table. */
@Composable
private fun QualificationLine(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.5f))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.5f))
        )
    }
}

/** One knockout tie, showing both legs, aggregate and the winner. */
@Composable
private fun BracketTieRow(tie: com.footymanager.simulator.domain.model.KnockoutTie, career: Career) {
    val high = career.club(tie.highSeedClubId)
    val low = career.club(tie.lowSeedClubId)
    val userInvolved = tie.highSeedClubId == career.userClubId ||
        tie.lowSeedClubId == career.userClubId

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (userInvolved) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .padding(8.dp)
    ) {
        TieSide(
            club = high,
            aggregate = tie.highSeedAggregate,
            isWinner = tie.winnerClubId == tie.highSeedClubId,
            isDecided = tie.decided
        )
        Spacer(Modifier.height(4.dp))
        TieSide(
            club = low,
            aggregate = tie.lowSeedAggregate,
            isWinner = tie.winnerClubId == tie.lowSeedClubId,
            isDecided = tie.decided
        )
        if (tie.shootoutHigh != null && tie.shootoutLow != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Won ${tie.shootoutHigh}-${tie.shootoutLow} on penalties",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (userInvolved) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Your tie",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TieSide(
    club: com.footymanager.simulator.domain.model.Club?,
    aggregate: Int,
    isWinner: Boolean,
    isDecided: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (club != null) {
            ClubCrest(club = club, size = 22.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = club?.name ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !isDecided -> MaterialTheme.colorScheme.onSurface
                isWinner -> StatColors.elite
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$aggregate",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            color = if (isWinner) StatColors.elite else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UclTableRow(
    position: Int,
    clubName: String,
    played: Int,
    goalDifference: Int,
    points: Int,
    status: UclStatus,
    isUser: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(statusColor(status))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$position",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp)
        )
        Text(
            text = clubName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$played",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = if (goalDifference > 0) "+$goalDifference" else "$goalDifference",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = "$points",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun statusColor(status: UclStatus) = when (status) {
    UclStatus.DIRECT_R16 -> StatColors.elite
    UclStatus.PLAYOFF -> StatColors.average
    UclStatus.ELIMINATED -> StatColors.bad
    UclStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Stadium management: capacity, ticket pricing, attendance history and paid
 * expansion. Pricing is a real trade-off, and the screen says so.
 */
@Composable
fun StadiumScreen(
    career: Career,
    onSetTicketPrice: (Int) -> Unit,
    onExpand: () -> Unit
) {
    val stadium = career.stadium
    val band = stadium.priceBand(career.userClub.reputation)
    var price by remember(stadium.ticketPrice) { mutableFloatStateOf(stadium.ticketPrice.toFloat()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle(stadium.name, "Capacity ${"%,d".format(stadium.capacity)}") }

        item {
            FmCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell("Level", "${stadium.level}/${Stadium.MAX_CAPACITY.size}")
                    StatCell("Last crowd", "${"%,d".format(stadium.lastAttendance)}")
                    StatCell("Average", "${"%,d".format(stadium.averageAttendance)}")
                }
                Spacer(Modifier.height(10.dp))
                StatCell(
                    "Matchday income to date",
                    Fmt.money(stadium.totalMatchdayIncome),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Ticket pricing")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Recommended band: £${band.first} - £${band.last}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "£${price.toInt()}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = price,
                        onValueChange = { price = it },
                        valueRange = 5f..120f,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = priceHint(price.toInt(), band),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FmPrimaryButton(
                    text = "Set price to £${price.toInt()}",
                    onClick = { onSetTicketPrice(price.toInt()) }
                )
            }
        }

        item {
            FmCard {
                SectionHeader("Expansion")
                Spacer(Modifier.height(8.dp))
                if (stadium.expansionWeeksRemaining > 0) {
                    Text(
                        text = "Expansion in progress: ${stadium.expansionWeeksRemaining} week(s) remaining " +
                            "towards ${"%,d".format(stadium.expansionTargetCapacity)} seats.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (stadium.canExpand) {
                    StatCell(
                        "Next capacity",
                        "${"%,d".format(stadium.nextCapacity)} seats",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    StatCell(
                        "Cost",
                        Fmt.money(stadium.expansionCost),
                        valueColor = if (career.userClub.balance >= stadium.expansionCost)
                            StatColors.good else StatColors.bad,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    FmPrimaryButton(
                        text = "Start expansion",
                        onClick = onExpand
                    )
                } else {
                    EmptyState(
                        icon = Icons.Outlined.EmojiEvents,
                        title = "Maximum capacity reached",
                        body = "The stadium is as large as it can be."
                    )
                }
            }
        }
    }
}

private fun priceHint(price: Int, band: IntRange): String = when {
    price < band.first -> "Bargain pricing: big crowds, but you are leaving money on the table."
    price > band.last -> "Premium pricing: fewer fans and a dent in the atmosphere."
    else -> "Sensible pricing for this club's support."
}

/**
 * Sponsorship offers at the start of a season, or the signed deal once chosen.
 */
@Composable
fun SponsorsScreen(
    career: Career,
    onSign: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Sponsorship", "Season ${career.season}") }

        val signed = career.sponsorship
        if (signed != null) {
            item {
                FmCard(accent = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = signed.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    InfoPill(signed.tier)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatCell("Upfront", Fmt.money(signed.upfront))
                        StatCell("Weekly", Fmt.money(signed.weeklyInstalment))
                        StatCell("Season total", Fmt.money(signed.seasonal))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Bonus: ${signed.bonusCondition} (${Fmt.money(signed.bonusAmount)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        val offers = career.sponsorOffers
        if (offers.isNotEmpty()) {
            item { SectionHeader("Offers on the table") }
            items(offers, key = { it.id }) { offer ->
                FmCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = offer.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        InfoPill(offer.tier)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatCell("Upfront", Fmt.money(offer.upfront))
                        StatCell("Seasonal", Fmt.money(offer.seasonal))
                        StatCell("Bonus", Fmt.money(offer.bonusAmount))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${offer.bonusCondition} triggers the bonus.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = offer.risk,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    FmPrimaryButton(text = "Sign deal", onClick = { onSign(offer.id) })
                }
            }
        } else if (signed == null) {
            item {
                EmptyState(
                    icon = Icons.Outlined.EmojiEvents,
                    title = "No offers",
                    body = "Sponsorship offers arrive at the start of each season."
                )
            }
        }
    }
}
