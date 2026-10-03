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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.TransferOffer
import com.footymanager.simulator.ui.components.ClubCrest
import com.footymanager.simulator.ui.components.EmptyState
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.InfoPill
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.RatingBadge
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.theme.StatColors

/**
 * Transfer market with search, filters, bidding and outgoing sales.
 *
 * Negotiation is deliberately transparent: the asking price and wage demand are
 * shown, so the player understands why a bid succeeded or failed.
 */
@Composable
fun TransfersScreen(
    career: Career,
    onSearch: (String, Position?) -> List<Player>,
    onAskingPrice: (Player) -> Long,
    onExpectedWage: (Player) -> Long,
    onMakeOffer: (Long, Long, Long, Int) -> Unit,
    onResolveOffer: (Long) -> Unit,
    onWithdrawOffer: (Long) -> Unit,
    onInterestedBuyers: (Long) -> List<Pair<com.footymanager.simulator.domain.model.Club, Long>>,
    onSell: (Long, Long, Long) -> Unit,
    onRelease: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var positionFilter by remember { mutableStateOf<Position?>(null) }
    var sort by remember { mutableStateOf(MarketSort.RATING) }
    var affordableOnly by remember { mutableStateOf(false) }
    var shortlist by remember { mutableStateOf(emptySet<Long>()) }
    var negotiatingPlayerId by remember { mutableStateOf<Long?>(null) }
    var sellingPlayerId by remember { mutableStateOf<Long?>(null) }

    val results = remember(query, positionFilter, career.players, career.clubs) {
        onSearch(query, positionFilter)
    }
    val visible = remember(results, sort, affordableOnly, career.userClub.transferBudget) {
        val filtered = if (affordableOnly) {
            results.filter { career.userClub.transferBudget >= onAskingPrice(it) }
        } else results
        when (sort) {
            MarketSort.RATING -> filtered.sortedByDescending { it.overall }
            MarketSort.VALUE -> filtered.sortedByDescending { it.value }
            MarketSort.AGE -> filtered.sortedBy { it.age }
            MarketSort.POTENTIAL -> filtered.sortedByDescending { it.potential }
        }
    }
    val shortlisted = remember(shortlist, results) {
        results.filter { it.id in shortlist }.sortedByDescending { it.overall }
    }
    val pendingOffers = remember(career.pendingOffers) {
        career.pendingOffers.filter {
            it.status == OfferStatus.PENDING || it.status == OfferStatus.REJECTED
        }.sortedByDescending { it.id }
    }
    val windowOpen = career.transferWindow.isOpen(career.matchdayIndex)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- Budget summary ----
        item {
            FmCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Transfer budget", Fmt.money(career.userClub.transferBudget), valueColor = MaterialTheme.colorScheme.primary)
                    StatCell("Spent", Fmt.money(career.transferSpendThisSeason), valueColor = StatColors.poor)
                    StatCell("Received", Fmt.money(career.transferIncomeThisSeason), valueColor = StatColors.elite)
                }
                Spacer(Modifier.height(8.dp))
                if (!windowOpen) {
                    InfoPill(
                        text = "Transfer window closed — opens again after matchday 3",
                        color = StatColors.poor
                    )
                } else {
                    InfoPill(text = "Window open", color = StatColors.elite)
                }
            }
        }

        // ---- Pending offers ----
        if (pendingOffers.isNotEmpty()) {
            item { SectionHeader("Your offers") }
            items(pendingOffers, key = { "offer-${it.id}" }) { offer ->
                OfferCard(
                    offer = offer,
                    career = career,
                    onResolve = { onResolveOffer(offer.id) },
                    onWithdraw = { onWithdrawOffer(offer.id) }
                )
            }
        }

        // ---- Search ----
        item {
            FmCard {
                SectionHeader("Search players")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Player name or club") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        SelectorChip(
                            label = "All",
                            selected = positionFilter == null,
                            onClick = { positionFilter = null }
                        )
                    }
                    items(Position.entries.toList(), key = { it.name }) { position ->
                        SelectorChip(
                            label = position.short,
                            selected = positionFilter == position,
                            onClick = {
                                positionFilter = if (positionFilter == position) null else position
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MarketSort.entries.toList(), key = { it.name }) { option ->
                        SelectorChip(
                            label = option.label,
                            selected = sort == option,
                            onClick = { sort = option }
                        )
                    }
                    item {
                        SelectorChip(
                            label = "Affordable only",
                            selected = affordableOnly,
                            onClick = { affordableOnly = !affordableOnly }
                        )
                    }
                }
            }
        }

        // ---- Shortlist ----
        if (shortlisted.isNotEmpty()) {
            item { SectionHeader("Shortlist (${shortlisted.size})") }
            items(shortlisted, key = { "short-${it.id}" }) { player ->
                MarketPlayerRow(
                    player = player,
                    career = career,
                    askingPrice = onAskingPrice(player),
                    expectedWage = onExpectedWage(player),
                    affordable = career.userClub.transferBudget >= onAskingPrice(player),
                    shortlisted = true,
                    onToggleShortlist = {
                        shortlist = if (player.id in shortlist) shortlist - player.id
                        else shortlist + player.id
                    },
                    onClick = { negotiatingPlayerId = player.id }
                )
            }
        }

        // ---- Results ----
        item {
            SectionHeader("${visible.size} players available")
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No players found",
                    body = "Try a different name or clear the position filter."
                )
            }
        }

        items(visible.take(60), key = { it.id }) { player ->
            MarketPlayerRow(
                player = player,
                career = career,
                askingPrice = onAskingPrice(player),
                expectedWage = onExpectedWage(player),
                affordable = career.userClub.transferBudget >= onAskingPrice(player),
                shortlisted = player.id in shortlist,
                onToggleShortlist = {
                    shortlist = if (player.id in shortlist) shortlist - player.id
                    else shortlist + player.id
                },
                onClick = { negotiatingPlayerId = player.id }
            )
        }

        // ---- Sell your own players ----
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Sell from your squad")
        }
        items(career.userSquad.sortedByDescending { it.value }, key = { "sell-${it.id}" }) { player ->
            OwnPlayerRow(
                player = player,
                onClick = { sellingPlayerId = player.id }
            )
        }
    }

    // ---- Negotiation dialog ----
    negotiatingPlayerId?.let { playerId ->
        val player = career.player(playerId)
        if (player != null) {
            NegotiationDialog(
                player = player,
                career = career,
                askingPrice = onAskingPrice(player),
                expectedWage = onExpectedWage(player),
                onDismiss = { negotiatingPlayerId = null },
                onSubmit = { fee, wage, years ->
                    onMakeOffer(playerId, fee, wage, years)
                    negotiatingPlayerId = null
                }
            )
        } else {
            negotiatingPlayerId = null
        }
    }

    // ---- Sell dialog ----
    sellingPlayerId?.let { playerId ->
        val player = career.player(playerId)
        if (player != null) {
            SellPlayerDialog(
                player = player,
                buyers = onInterestedBuyers(playerId),
                onDismiss = { sellingPlayerId = null },
                onSell = { fee, buyerId ->
                    onSell(playerId, fee, buyerId)
                    sellingPlayerId = null
                },
                onRelease = {
                    onRelease(playerId)
                    sellingPlayerId = null
                }
            )
        } else {
            sellingPlayerId = null
        }
    }
}

@Composable
private fun MarketPlayerRow(
    player: Player,
    career: Career,
    askingPrice: Long,
    expectedWage: Long,
    affordable: Boolean,
    shortlisted: Boolean,
    onToggleShortlist: () -> Unit,
    onClick: () -> Unit
) {
    val club = player.clubId?.let { career.club(it) }
    FmCard(onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingBadge(player.overall, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleMedium,
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
                if (club != null) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClubCrest(club = club, size = 14.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = club.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Fmt.money(askingPrice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (affordable) MaterialTheme.colorScheme.primary else StatColors.bad
                )
                Text(
                    text = Fmt.wage(expectedWage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (affordable) "Bid" else "Over budget",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (affordable) MaterialTheme.colorScheme.primary else StatColors.bad
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (shortlisted) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (shortlisted) "Remove from shortlist" else "Add to shortlist",
                tint = if (shortlisted) StatColors.elite else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onToggleShortlist)
            )
        }
    }
}

/** How the transfer market results are ordered. */
enum class MarketSort(val label: String) {
    RATING("Rating"),
    POTENTIAL("Potential"),
    VALUE("Value"),
    AGE("Age")
}

@Composable
private fun OwnPlayerRow(player: Player, onClick: () -> Unit) {
    FmCard(onClick = onClick, padding = 12.dp) {
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
                        text = "${player.age}y • ${Fmt.wage(player.wagePerWeek)}",
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

@Composable
private fun OfferCard(
    offer: TransferOffer,
    career: Career,
    onResolve: () -> Unit,
    onWithdraw: () -> Unit
) {
    val accepted = offer.status == OfferStatus.COMPLETED
    FmCard(accent = if (accepted) StatColors.elite else StatColors.average) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = offer.playerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Your bid: ${Fmt.money(offer.fee)} • Wage offered: ${Fmt.wage(offer.wagePerWeek)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = offer.status.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (accepted) StatColors.elite else StatColors.average,
                fontWeight = FontWeight.Bold
            )
        }
        if (offer.message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = offer.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FmSecondaryButton(
                text = "Retry offer",
                onClick = onResolve,
                modifier = Modifier.weight(1f)
            )
            FmSecondaryButton(
                text = "Withdraw",
                onClick = onWithdraw,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Fee and wage negotiation. The player can see exactly what is being asked. */
@Composable
private fun NegotiationDialog(
    player: Player,
    career: Career,
    askingPrice: Long,
    expectedWage: Long,
    onDismiss: () -> Unit,
    onSubmit: (fee: Long, wage: Long, years: Int) -> Unit
) {
    val maxFee = (askingPrice * 1.5f).coerceAtLeast(player.value.toFloat())
    val minFee = (askingPrice * 0.5f).coerceAtLeast(0f)

    var feeRatio by remember(player.id) {
        mutableFloatStateOf(
            if (maxFee > minFee) ((askingPrice - minFee) / (maxFee - minFee)).coerceIn(0f, 1f) else 1f
        )
    }
    var wageRatio by remember(player.id) { mutableFloatStateOf(1f) }
    var years by remember(player.id) { mutableFloatStateOf(3f) }

    val offeredFee = (minFee + (maxFee - minFee) * feeRatio).toLong()
    val offeredWage = (expectedWage * (0.6f + wageRatio * 0.7f)).toLong().coerceAtLeast(1_000L)
    val affordableFee = career.userClub.transferBudget >= offeredFee
    val canSubmit = affordableFee && offeredFee > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Negotiate: ${player.name}")
                Text(
                    text = "${player.position.longName} • ${player.age} • Overall ${player.overall}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    FmCard {
                        SectionHeader("Their demands")
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Asking price", Fmt.money(askingPrice), valueColor = StatColors.average)
                            StatCell("Wage demand", Fmt.wage(expectedWage), valueColor = StatColors.average)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "A bid within 6% of the asking price is normally accepted.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Your offer")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Transfer fee: ${Fmt.money(offeredFee)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (affordableFee) MaterialTheme.colorScheme.primary else StatColors.bad
                        )
                        Slider(
                            value = feeRatio,
                            onValueChange = { feeRatio = it },
                            valueRange = 0f..1f
                        )
                        Text(
                            text = "Wage: ${Fmt.wage(offeredWage)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = wageRatio,
                            onValueChange = { wageRatio = it },
                            valueRange = 0f..1f
                        )
                        Text(
                            text = "Contract length: ${years.toInt()} years",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = years,
                            onValueChange = { years = it },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Budget remaining after deal: ${Fmt.money(career.userClub.transferBudget - offeredFee)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(offeredFee, offeredWage, years.toInt()) },
                enabled = canSubmit
            ) {
                Text(if (canSubmit) "Submit offer" else "Over budget")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Selling a player: pick from clubs that have registered interest. */
@Composable
private fun SellPlayerDialog(
    player: Player,
    buyers: List<Pair<com.footymanager.simulator.domain.model.Club, Long>>,
    onDismiss: () -> Unit,
    onSell: (fee: Long, buyerClubId: Long) -> Unit,
    onRelease: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Sell ${player.name}")
                Text(
                    text = "Value ${Fmt.money(player.value)} • ${Fmt.wage(player.wagePerWeek)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (buyers.isEmpty()) {
                    item {
                        Text(
                            text = "No clubs have registered an interest in this player right now. " +
                                "Try again later in the window, or release him.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(buyers, key = { it.first.id }) { (club, fee) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onSell(fee, club.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClubCrest(club = club, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = club.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Reputation ${club.reputation}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = Fmt.money(fee),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRelease) { Text("Release player") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
