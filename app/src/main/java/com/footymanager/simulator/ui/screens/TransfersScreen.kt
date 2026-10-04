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
    onRequiredPackage: (Player) -> TransferPackage,
    onMakeOfferPackage: (Long, TransferPackage, ContractTerms) -> Unit,
    onAcceptCounter: (Long) -> Unit,
    onSubmitPlayerTerms: (Long, ContractTerms) -> Unit,
    onCancelOffer: (Long) -> Unit,
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

        // ---- Active negotiations ----
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
