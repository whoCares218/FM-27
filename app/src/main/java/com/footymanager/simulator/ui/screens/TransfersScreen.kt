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
import com.footymanager.simulator.domain.model.ContractTerms
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayerResponse
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.SellingClubResponse
import com.footymanager.simulator.domain.model.SquadRole
import com.footymanager.simulator.domain.model.TransferOffer
import com.footymanager.simulator.domain.model.TransferPackage
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
 * The transfer hub, split into two complete management systems.
 *
 * BUY is the market: search, filter, inspect, negotiate and sign players.
 * SELL is the manager's own auction: list a player, set an asking price, watch
 * clubs bid, and negotiate until a deal is agreed. Keeping the two apart stops
 * either from feeling like an afterthought.
 */
enum class TransferTab(val label: String) { BUY("Buy"), SELL("Sell") }

@Composable
fun TransfersScreen(
    career: Career,
    onSearch: (String, Position?) -> List<Player>,
    onAskingPrice: (Player) -> Long,
    onExpectedWage: (Player) -> Long,
    onRequiredPackage: (Player) -> TransferPackage,
    onMakeOfferPackage: (Long, TransferPackage, ContractTerms) -> Unit,
    onAcceptCounter: (Long) -> Unit,
    onSubmitPlayerTerms: (Long, ContractTerms) -> Unit,
    onCancelOffer: (Long) -> Unit,
    onListPlayer: (Long, Long) -> Unit,
    onSetAskingPrice: (Long, Long) -> Unit,
    onInterestedCount: (Player, Long) -> Int,
    onCounterSaleBid: (Long, Long) -> Unit,
    onAcceptSaleBid: (Long) -> Unit,
    onRejectSaleBid: (Long) -> Unit,
    onCancelSale: () -> Unit,
    onRelease: (Long) -> Unit
) {
    var tab by remember { mutableStateOf(TransferTab.BUY) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- BUY | SELL selector ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TransferTab.entries.forEach { option ->
                val selected = tab == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { tab = option }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when (tab) {
            TransferTab.BUY -> BuySection(
                career = career,
                onSearch = onSearch,
                onAskingPrice = onAskingPrice,
                onExpectedWage = onExpectedWage,
                onRequiredPackage = onRequiredPackage,
                onMakeOfferPackage = onMakeOfferPackage,
                onAcceptCounter = onAcceptCounter,
                onSubmitPlayerTerms = onSubmitPlayerTerms,
                onCancelOffer = onCancelOffer
            )
            TransferTab.SELL -> SellSection(
                career = career,
                onListPlayer = onListPlayer,
                onSetAskingPrice = onSetAskingPrice,
                onInterestedCount = onInterestedCount,
                onCounterSaleBid = onCounterSaleBid,
                onAcceptSaleBid = onAcceptSaleBid,
                onRejectSaleBid = onRejectSaleBid,
                onCancelSale = onCancelSale,
                onRelease = onRelease
            )
        }
    }
}

@Composable
private fun BuySection(
    career: Career,
    onSearch: (String, Position?) -> List<Player>,
    onAskingPrice: (Player) -> Long,
    onExpectedWage: (Player) -> Long,
    onRequiredPackage: (Player) -> TransferPackage,
    onMakeOfferPackage: (Long, TransferPackage, ContractTerms) -> Unit,
    onAcceptCounter: (Long) -> Unit,
    onSubmitPlayerTerms: (Long, ContractTerms) -> Unit,
    onCancelOffer: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var positionFilter by remember { mutableStateOf<Position?>(null) }
    var sort by remember { mutableStateOf(MarketSort.RATING) }
    var affordableOnly by remember { mutableStateOf(false) }
    var shortlist by remember { mutableStateOf(emptySet<Long>()) }
    var negotiatingPlayerId by remember { mutableStateOf<Long?>(null) }

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
    val activeOffers = remember(career.pendingOffers) {
        career.pendingOffers.filter {
            it.status == OfferStatus.COUNTERED ||
                it.status == OfferStatus.REJECTED ||
                it.status == OfferStatus.COMPLETED ||
                it.awaitingPlayer
        }.sortedByDescending { it.id }.take(6)
    }
    val windowOpen = career.transferWindow.isOpen(career.matchdayIndex)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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

        if (activeOffers.isNotEmpty()) {
            item { SectionHeader("Negotiations") }
            items(activeOffers, key = { "offer-${it.id}" }) { offer ->
                OfferCard(
                    offer = offer,
                    career = career,
                    onAcceptCounter = { onAcceptCounter(offer.id) },
                    onSubmitTerms = { terms -> onSubmitPlayerTerms(offer.id, terms) },
                    onCancel = { onCancelOffer(offer.id) }
                )
            }
        }

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

        item { SectionHeader("${visible.size} players available") }

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
    }

    negotiatingPlayerId?.let { playerId ->
        val player = career.player(playerId)
        if (player != null) {
            NegotiationDialog(
                player = player,
                career = career,
                requiredPackage = onRequiredPackage(player),
                expectedWage = onExpectedWage(player),
                onDismiss = { negotiatingPlayerId = null },
                onSubmit = { offerPackage, terms ->
                    onMakeOfferPackage(playerId, offerPackage, terms)
                    negotiatingPlayerId = null
                }
            )
        } else {
            negotiatingPlayerId = null
        }
    }
}

@Composable
private fun SellSection(
    career: Career,
    onListPlayer: (Long, Long) -> Unit,
    onSetAskingPrice: (Long, Long) -> Unit,
    onInterestedCount: (Player, Long) -> Int,
    onCounterSaleBid: (Long, Long) -> Unit,
    onAcceptSaleBid: (Long) -> Unit,
    onRejectSaleBid: (Long) -> Unit,
    onCancelSale: () -> Unit,
    onRelease: (Long) -> Unit
) {
    var sellingPlayerId by remember { mutableStateOf<Long?>(null) }
    val sale = career.pendingSale
    val windowOpen = career.transferWindow.isOpen(career.matchdayIndex)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FmCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Squad value", Fmt.money(career.userSquad.sumOf { it.value }))
                    StatCell("Sold this season", Fmt.money(career.transferIncomeThisSeason), valueColor = StatColors.elite)
                    StatCell("Listed", "${career.transferListings.size}")
                }
                Spacer(Modifier.height(8.dp))
                if (!windowOpen) {
                    InfoPill(text = "Transfer window closed — sales reopen after matchday 3", color = StatColors.poor)
                } else {
                    InfoPill(text = "Window open", color = StatColors.elite)
                }
            }
        }

        if (sale != null) {
            item { SectionHeader("Live negotiation: ${sale.playerName}") }
            item {
                SaleNegotiationCard(
                    career = career,
                    onCounter = { clubId, amount -> onCounterSaleBid(clubId, amount) },
                    onAccept = { clubId -> onAcceptSaleBid(clubId) },
                    onReject = { clubId -> onRejectSaleBid(clubId) },
                    onCancel = onCancelSale
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("Your squad")
        }
        items(career.userSquad.sortedByDescending { it.value }, key = { "sell-${it.id}" }) { player ->
            val listed = career.transferListings.any { it.playerId == player.id }
            SellSquadRow(
                player = player,
                listed = listed,
                onClick = { sellingPlayerId = player.id }
            )
        }
    }

    sellingPlayerId?.let { playerId ->
        val player = career.player(playerId)
        if (player != null) {
            SellPlayerDialog(
                player = player,
                askingPrice = career.transferListings.firstOrNull { it.playerId == playerId }?.askingPrice
                    ?: player.value,
                interestedCount = onInterestedCount(player, player.value),
                onDismiss = { sellingPlayerId = null },
                onList = { asking ->
                    onListPlayer(playerId, asking)
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

@Composable
private fun SellSquadRow(player: Player, listed: Boolean, onClick: () -> Unit) {
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Fmt.money(player.value),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (listed) {
                    Text(
                        text = "LISTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatColors.average,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "List for sale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * The live multi-club sale negotiation: every interested club's bid, with the
 * ability to counter, accept or reject each one. All clubs answer immediately.
 */
@Composable
private fun SaleNegotiationCard(
    career: Career,
    onCounter: (Long, Long) -> Unit,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val sale = career.pendingSale ?: return
    var counteringClubId by remember(sale.id) { mutableStateOf<Long?>(null) }

    FmCard(accent = MaterialTheme.colorScheme.primary) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCell("Market value", Fmt.money(sale.marketValue))
            StatCell("Asking price", Fmt.money(sale.askingPrice))
            StatCell("Clubs bidding", "${sale.interestedCount}")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = sale.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        if (sale.bids.isEmpty()) {
            Text(
                text = "No club is willing to meet your price. Lower the asking price to attract buyers.",
                style = MaterialTheme.typography.bodySmall,
                color = StatColors.bad
            )
        } else {
            sale.bids.forEach { bid ->
                val club = career.club(bid.clubId)
                val accent = when (bid.status) {
                    com.footymanager.simulator.domain.model.BidStatus.ACCEPTED -> StatColors.elite
                    com.footymanager.simulator.domain.model.BidStatus.WITHDRAWN,
                    com.footymanager.simulator.domain.model.BidStatus.REJECTED -> StatColors.bad
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (club != null) {
                            ClubCrest(club = club, size = 26.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bid.clubName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = bid.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = Fmt.money(bid.amount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    if (bid.isLive) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FmSecondaryButton(
                                text = "Accept",
                                onClick = { onAccept(bid.clubId) },
                                modifier = Modifier.weight(1f)
                            )
                            FmSecondaryButton(
                                text = "Counter",
                                onClick = {
                                    counteringClubId =
                                        if (counteringClubId == bid.clubId) null else bid.clubId
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FmSecondaryButton(
                                text = "Reject",
                                onClick = { onReject(bid.clubId) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (counteringClubId == bid.clubId) {
                        Spacer(Modifier.height(6.dp))
                        CounterBidEditor(
                            playerName = sale.playerName,
                            marketValue = sale.marketValue,
                            currentBid = bid.amount,
                            askingPrice = sale.askingPrice,
                            onConfirm = { amount ->
                                onCounter(bid.clubId, amount)
                                counteringClubId = null
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel sale") }
    }
}

@Composable
private fun CounterBidEditor(
    playerName: String,
    marketValue: Long,
    currentBid: Long,
    askingPrice: Long,
    onConfirm: (Long) -> Unit
) {
    val low = minOf(currentBid, marketValue)
    val high = maxOf(askingPrice, (marketValue * 1.5).toLong())
    var amount by remember(currentBid) {
        mutableFloatStateOf((currentBid * 1.05).toFloat().coerceIn(low.toFloat(), high.toFloat()))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(10.dp)
    ) {
        Text(
            text = "Counter ${currentBid.let { Fmt.money(it) }} with",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Fmt.money(amount.toLong()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Slider(
                value = amount,
                onValueChange = { amount = it },
                valueRange = low.toFloat()..high.toFloat(),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        FmPrimaryButton(
            text = "Send counter",
            onClick = { onConfirm(amount.toLong()) }
        )
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
private fun OfferCard(
    offer: TransferOffer,
    career: Career,
    onAcceptCounter: () -> Unit,
    onSubmitTerms: (ContractTerms) -> Unit,
    onCancel: () -> Unit
) {
    val completed = offer.status == OfferStatus.COMPLETED
    val rejected = offer.status == OfferStatus.REJECTED
    val accent = when {
        completed -> StatColors.elite
        rejected -> StatColors.bad
        offer.sellingClubResponse == SellingClubResponse.ACCEPT -> StatColors.good
        else -> StatColors.average
    }
    FmCard(accent = accent) {
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
                    text = "Your bid: ${offer.offerPackage.label()} • Wage: ${Fmt.wage(offer.wagePerWeek)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = when {
                    completed -> "COMPLETED"
                    offer.awaitingPlayer -> "WITH PLAYER"
                    offer.counterPackage != null -> "COUNTER"
                    rejected -> "REJECTED"
                    else -> offer.status.name
                },
                style = MaterialTheme.typography.labelSmall,
                color = accent,
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

        // ---- Selling club counter-offer ----
        val counter = offer.counterPackage
        if (counter != null && offer.sellingClubResponse == SellingClubResponse.NEGOTIATE) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "SELLING CLUB COUNTER",
                style = MaterialTheme.typography.labelSmall,
                color = StatColors.average,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = counter.label(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FmPrimaryButton(
                    text = "Accept counter",
                    onClick = onAcceptCounter,
                    modifier = Modifier.weight(1f)
                )
                FmSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            }
            return@FmCard
        }

        // ---- Player wants improved terms ----
        if (offer.awaitingPlayer && offer.playerResponse == PlayerResponse.NEGOTIATE) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "PLAYER WANTS MORE",
                style = MaterialTheme.typography.labelSmall,
                color = StatColors.average,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Asking ${Fmt.wage(offer.expectedWage)} per week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            PlayerTermsEditor(
                career = career,
                playerId = offer.playerId,
                expectedWage = offer.expectedWage,
                onOffer = onSubmitTerms,
                onCancel = onCancel
            )
            return@FmCard
        }

        if (!completed && !rejected) {
            Spacer(Modifier.height(8.dp))
            FmSecondaryButton(text = "Withdraw", onClick = onCancel)
        }
    }
}

/** Compact editor for the personal terms when a player asks for more. */
@Composable
private fun PlayerTermsEditor(
    career: Career,
    playerId: Long,
    expectedWage: Long,
    onOffer: (ContractTerms) -> Unit,
    onCancel: () -> Unit
) {
    var wageRatio by remember(playerId) { mutableFloatStateOf(1f) }
    var years by remember(playerId) { mutableFloatStateOf(3f) }
    var roleIndex by remember(playerId) { mutableStateOf(2) }
    var bonus by remember(playerId) { mutableFloatStateOf(0f) }

    val role = SquadRole.entries[roleIndex.coerceIn(0, SquadRole.entries.size - 1)]
    val offeredWage = (expectedWage * (0.9f + wageRatio * 0.5f)).toLong().coerceAtLeast(1_000L)
    val signingBonus = (offeredWage * 26f * bonus).toLong()

    Column {
        Text(
            text = "Wage: ${Fmt.wage(offeredWage)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(value = wageRatio, onValueChange = { wageRatio = it }, valueRange = 0f..1f)
        Text(
            text = "Contract length: ${years.toInt()} years",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(value = years, onValueChange = { years = it }, valueRange = 1f..5f, steps = 3)
        Text(
            text = "Squad role",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SquadRole.entries.toList(), key = { it.name }) { option ->
                val idx = SquadRole.entries.indexOf(option)
                SelectorChip(
                    label = option.label,
                    selected = idx == roleIndex,
                    onClick = { roleIndex = idx }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Signing bonus: ${Fmt.money(signingBonus)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(value = bonus, onValueChange = { bonus = it }, valueRange = 0f..1f)
        Spacer(Modifier.height(8.dp))
        val budgetOk = career.userClub.transferBudget >= signingBonus
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FmPrimaryButton(
                text = if (budgetOk) "Offer terms" else "Over budget",
                enabled = budgetOk,
                onClick = {
                    onOffer(
                        ContractTerms(
                            wagePerWeek = offeredWage,
                            contractYears = years.toInt(),
                            squadRole = role,
                            signingBonus = signingBonus
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
            FmSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
        }
    }
}

/** Fee and wage negotiation. The player can see exactly what is being asked. */
@Composable
private fun NegotiationDialog(
    player: Player,
    career: Career,
    requiredPackage: TransferPackage,
    expectedWage: Long,
    onDismiss: () -> Unit,
    onSubmit: (TransferPackage, ContractTerms) -> Unit
) {
    val requiredFee = requiredPackage.fee
    val maxFee = (requiredFee * 1.5f).coerceAtLeast(player.value.toFloat())
    val minFee = (requiredFee * 0.4f).coerceAtLeast(0f)

    var feeRatio by remember(player.id) {
        mutableFloatStateOf(
            if (maxFee > minFee) ((requiredFee - minFee) / (maxFee - minFee)).coerceIn(0f, 1f) else 1f
        )
    }
    var wageRatio by remember(player.id) { mutableFloatStateOf(1f) }
    var years by remember(player.id) { mutableFloatStateOf(3f) }
    var roleIndex by remember(player.id) { mutableStateOf(2) }
    var makeweightId by remember(player.id) { mutableStateOf<Long?>(null) }

    val offeredCash = (minFee + (maxFee - minFee) * feeRatio).toLong()
    val makeweight = makeweightId?.let { career.player(it) }
    val makeweightValue = makeweight?.value ?: 0L
    val totalValue = offeredCash + makeweightValue
    val offeredWage = (expectedWage * (0.75f + wageRatio * 0.55f)).toLong().coerceAtLeast(1_000L)
    val role = SquadRole.entries[roleIndex.coerceIn(0, SquadRole.entries.size - 1)]
    val affordable = career.userClub.transferBudget >= offeredCash
    val package_ = TransferPackage(
        fee = offeredCash,
        playerOfferedId = makeweight?.id,
        playerOfferedName = makeweight?.name ?: "",
        playerOfferedValue = makeweightValue
    )

    // Candidates the manager could include in a part-exchange deal.
    val makeweightCandidates = remember(career.userSquad, player.id) {
        career.userSquad.sortedByDescending { it.value }.take(14)
    }

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
                        SectionHeader("Their valuation")
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Wanted fee", Fmt.money(requiredFee), valueColor = StatColors.average)
                            StatCell("Wage demand", Fmt.wage(expectedWage), valueColor = StatColors.average)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "They value ${player.name} based on ability, potential, age, " +
                                "contract length, form and how important he is to their squad.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Bid type")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                makeweight == null -> "Cash only"
                                offeredCash > 0 -> "Player + cash"
                                else -> "Player swap"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Cash offer")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = Fmt.money(offeredCash),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (affordable) MaterialTheme.colorScheme.primary else StatColors.bad
                        )
                        Slider(value = feeRatio, onValueChange = { feeRatio = it }, valueRange = 0f..1f)
                        Text(
                            text = "Budget remaining after fee: ${Fmt.money(career.userClub.transferBudget - offeredCash)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Add a player (player + cash / swap)")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Including one of your players can lower the cash needed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                SelectorChip(
                                    label = "None",
                                    selected = makeweightId == null,
                                    onClick = { makeweightId = null }
                                )
                            }
                            items(makeweightCandidates, key = { it.id }) { candidate ->
                                SelectorChip(
                                    label = "${candidate.name.substringAfterLast(' ')} ${Fmt.money(candidate.value)}",
                                    selected = makeweightId == candidate.id,
                                    onClick = { makeweightId = candidate.id }
                                )
                            }
                        }
                        if (makeweight != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Package total: ${package_.label()}",
                                style = MaterialTheme.typography.titleSmall,
                                color = StatColors.good
                            )
                        }
                    }
                }
                item {
                    FmCard {
                        SectionHeader("Contract offer")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Wage: ${Fmt.wage(offeredWage)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(value = wageRatio, onValueChange = { wageRatio = it }, valueRange = 0f..1f)
                        Text(
                            text = "Contract length: ${years.toInt()} years",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(value = years, onValueChange = { years = it }, valueRange = 1f..5f, steps = 3)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Squad role",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(SquadRole.entries.toList(), key = { it.name }) { option ->
                                val idx = SquadRole.entries.indexOf(option)
                                SelectorChip(
                                    label = option.label,
                                    selected = idx == roleIndex,
                                    onClick = { roleIndex = idx }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        package_,
                        ContractTerms(
                            wagePerWeek = offeredWage,
                            contractYears = years.toInt(),
                            squadRole = role
                        )
                    )
                },
                enabled = affordable && totalValue > 0
            ) {
                Text(if (affordable) "Submit bid" else "Over budget")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Listing a player for sale: set an asking price and see the likely interest. */
@Composable
private fun SellPlayerDialog(
    player: Player,
    askingPrice: Long,
    interestedCount: Int,
    onDismiss: () -> Unit,
    onList: (asking: Long) -> Unit,
    onRelease: () -> Unit
) {
    val value = player.value
    var asking by remember(player.id) {
        mutableFloatStateOf(askingPrice.coerceAtLeast(value / 4).toFloat())
    }
    val ratio = if (value > 0) asking / value.toDouble() else 1.0
    val demandHint = when {
        ratio <= 0.95 -> "Bargain price — expect strong interest."
        ratio <= 1.10 -> "Around market value — normal interest."
        ratio <= 1.30 -> "Above market value — fewer clubs will bid."
        else -> "Well above market value — most clubs will walk away."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Sell ${player.name}")
                Text(
                    text = "Market value ${Fmt.money(value)} • ${Fmt.wage(player.wagePerWeek)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Set your asking price. Clubs bid immediately — you can then " +
                        "negotiate with each of them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Asking price ${Fmt.money(asking.toLong())}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = asking,
                    onValueChange = { asking = it },
                    valueRange = (value / 4).toFloat()..(value * 2).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = demandHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                InfoPill(
                    text = "Estimated interest: $interestedCount club(s) at market value",
                    color = if (interestedCount > 0) StatColors.elite else StatColors.bad
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onList(asking.toLong()) }) { Text("List for sale") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRelease) { Text("Release") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

