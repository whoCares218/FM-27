package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.AmortisationCharge
import com.footymanager.simulator.domain.model.BidStatus
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.ClubBid
import com.footymanager.simulator.domain.model.ContractTerms
import com.footymanager.simulator.domain.model.FinanceLedgerEntry
import com.footymanager.simulator.domain.model.LedgerCategory
import com.footymanager.simulator.domain.model.NewsCategory
import com.footymanager.simulator.domain.model.NewsItem
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayerResponse
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.SaleNegotiation
import com.footymanager.simulator.domain.model.SaleStatus
import com.footymanager.simulator.domain.model.SellingClubResponse
import com.footymanager.simulator.domain.model.SquadRole
import com.footymanager.simulator.domain.model.TransferListing
import com.footymanager.simulator.domain.model.TransferDirection
import com.footymanager.simulator.domain.model.TransferHistoryEntry
import com.footymanager.simulator.domain.model.TransferOffer
import com.footymanager.simulator.domain.model.TransferPackage
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Transfer market: valuation, buying negotiation, selling negotiation and
 * (cheap) AI-to-AI trading.
 *
 * Buying is immediate: when the manager submits a bid the selling club answers
 * at once with ACCEPT, REJECT or a counter-offer, and the moment a fee is agreed
 * the player answers the personal terms too. Nothing waits for another in-game
 * day.
 *
 * Selling is a live, multi-club auction. Listing a player produces a handful of
 * interested clubs whose bids cluster around market value; the manager can
 * accept, reject or counter any of them, and the other clubs respond instantly —
 * improving, standing firm or walking away. There is no artificial "wait a few
 * days" step.
 *
 * Market value is not the selling price. The selling club's asking price adds a
 * premium for importance, contract length, finances, depth and the buyer's
 * reputation, and the manager's own asking price shapes how much interest a
 * listed player attracts.
 */
object TransferEngine {

    // ------------------------------------------------------------- buy pricing

    /** Fee at which the selling club will accept immediately. */
    fun askingPrice(career: Career, player: Player, sellingClubId: Long?): Long {
        val sellingClub = sellingClubId?.let { career.club(it) } ?: return 0L
        return AiManager.askingPrice(player, sellingClub, career.difficulty.transferDifficulty)
    }

    /** Wage the player will sign for, adjusted for the role they are promised. */
    fun expectedWage(
        career: Career,
        player: Player,
        buyingClubId: Long,
        role: SquadRole = SquadRole.ROTATION
    ): Long {
        val buyingClub = career.clubOrThrow(buyingClubId)
        val base = AiManager.expectedWage(player, buyingClub.reputation, career.difficulty.transferDifficulty)
        return (base * role.wageMultiplier).toLong().coerceAtLeast(player.wagePerWeek)
    }

    /** Every player the user's club could realistically bid for. */
    fun marketPlayers(career: Career): List<Player> {
        val userLeagueIds = com.footymanager.simulator.domain.model.League.all.map { it.id }.toSet()
        return career.players.filter { player ->
            player.clubId != career.userClubId &&
                player.clubId != null &&
                career.club(player.clubId ?: -1L)?.leagueId in userLeagueIds &&
                // Every club keeps its genuine stars unless they are unhappy.
                (player.overall < 88 || player.moraleScore < 2.6)
        }.sortedByDescending { it.overall }
    }

    /** How highly the selling club values a player, 0..1 (higher = more important). */
    private fun importance(player: Player, squad: List<Player>): Double {
        if (squad.isEmpty()) return 0.5
        val ranked = squad.sortedByDescending { it.overall }
        val rank = ranked.indexOfFirst { it.id == player.id }
        if (rank < 0) return 0.4
        val depth = squad.count { it.position == player.position }
        val positionalRank = squad.filter { it.position == player.position }
            .sortedByDescending { it.overall }
            .indexOfFirst { it.id == player.id }
        val depthFactor = (depth - positionalRank).coerceAtLeast(0) * 0.08
        val qualityFactor = 1.0 - (rank.toDouble() / ranked.size) * 0.5
        return (qualityFactor + depthFactor).coerceIn(0.2, 1.0)
    }

    /**
     * The package the selling club wants before it will part with the player.
     * Factors: rating, potential, age, value, contract length, importance, the
     * selling club's finances, squad depth, buyer reputation and current form.
     */
    fun requiredPackage(career: Career, player: Player, buyingClubId: Long): TransferPackage {
        val sellingClub = player.clubId?.let { career.club(it) }
        val base = player.value
        if (sellingClub == null) return TransferPackage(fee = 0L)

        val squad = career.squadOf(sellingClub.id)
        val importance = importance(player, squad)

        val contractFactor = when (player.contractYearsRemaining) {
            0 -> 0.70
            1 -> 0.84
            2 -> 0.96
            else -> 1.0
        }
        val potentialFactor = 1.0 + (player.potential - player.overall).coerceAtLeast(0) * 0.012
        val ageFactor = when {
            player.age <= 21 -> 1.10
            player.age <= 27 -> 1.06
            player.age <= 30 -> 1.0
            player.age <= 33 -> 0.92
            else -> 0.82
        }
        val financesFactor = when {
            sellingClub.balance > sellingClub.wageBudget * 8 -> 1.10
            sellingClub.balance < 0 -> 0.90
            else -> 1.0
        }
        val positionalDepth = squad.count { it.position == player.position }
        val depthFactor = if (positionalDepth >= 4) 0.92 else 1.0
        val buyerFactor = 1.0 + (career.clubOrThrow(buyingClubId).reputation - 70) * 0.003
        val formFactor = 1.0 + (player.form - 5.0) * 0.012
        val importanceFactor = 0.86 + importance * 0.40

        val wanted = (base * contractFactor * potentialFactor * ageFactor * financesFactor *
            depthFactor * buyerFactor * formFactor * importanceFactor).toLong()
        return TransferPackage(fee = wanted.coerceAtLeast(player.value / 2))
    }

    // ------------------------------------------------------------- buy offers

    /**
     * Creates a bid and resolves it immediately: the selling club answers, and if
     * it accepts the player answers the personal terms in the same instant.
     */
    fun createUserOffer(
        career: Career,
        player: Player,
        offerPackage: TransferPackage,
        terms: ContractTerms,
        isLoan: Boolean = false
    ): Career {
        val asking = requiredPackage(career, player, career.userClubId)
        val expected = expectedWage(career, player, career.userClubId, terms.squadRole)
        var idCounter = career.idCounter
        idCounter++
        val offer = TransferOffer(
            id = idCounter,
            playerId = player.id,
            playerName = player.name,
            fromClubId = player.clubId ?: 0L,
            toClubId = career.userClubId,
            fee = offerPackage.fee,
            wagePerWeek = terms.wagePerWeek,
            contractYears = terms.contractYears,
            status = OfferStatus.PENDING,
            askingPrice = asking.fee,
            expectedWage = expected,
            isUserInitiated = true,
            isLoan = isLoan,
            createdMatchday = career.matchdayIndex,
            message = "Bid submitted",
            offerPackage = offerPackage,
            squadRole = terms.squadRole,
            signingBonus = terms.signingBonus,
            releaseClause = terms.releaseClause
        )
        val withOffer = career.copy(pendingOffers = career.pendingOffers + offer, idCounter = idCounter)
        return evaluateSellingClub(withOffer, offer.id)
    }

    /** The user accepts the selling club's counter-offer; the player is tried at once. */
    fun acceptCounter(career: Career, offerId: Long): Career {
        val offer = career.pendingOffers.firstOrNull { it.id == offerId } ?: return career
        val counter = offer.counterPackage ?: return career
        val agreed = offer.copy(
            offerPackage = counter,
            fee = counter.fee,
            askingPrice = counter.fee,
            status = OfferStatus.ACCEPTED,
            sellingClubResponse = SellingClubResponse.ACCEPT,
            counterPackage = null,
            message = "Fee agreed: ${counter.label()}"
        )
        val updated = career.copy(
            pendingOffers = career.pendingOffers.map { if (it.id == offerId) agreed else it }
        )
        return evaluatePlayer(updated, offerId)
    }

    /** The user submits improved personal terms after the player asked for more. */
    fun submitPlayerTerms(career: Career, offerId: Long, terms: ContractTerms): Career {
        val offer = career.pendingOffers.firstOrNull { it.id == offerId } ?: return career
        val expected = expectedWage(career, career.player(offer.playerId) ?: return career,
            career.userClubId, terms.squadRole)
        val updated = career.copy(
            pendingOffers = career.pendingOffers.map {
                if (it.id == offerId) it.copy(
                    wagePerWeek = terms.wagePerWeek,
                    contractYears = terms.contractYears,
                    squadRole = terms.squadRole,
                    signingBonus = terms.signingBonus,
                    releaseClause = terms.releaseClause,
                    expectedWage = expected,
                    status = OfferStatus.ACCEPTED,
                    playerResponse = null
                ) else it
            }
        )
        return evaluatePlayer(updated, offerId)
    }

    /** Withdraws a bid, however far it has progressed. */
    fun cancelOffer(career: Career, offerId: Long): Career = career.copy(
        pendingOffers = career.pendingOffers.map {
            if (it.id == offerId) it.copy(status = OfferStatus.WITHDRAWN, message = "Offer withdrawn")
            else it
        }
    )

    /** Re-evaluates an offer from the start (legacy/test entry point). */
    fun resolveOffer(career: Career, offerId: Long, random: Random): Career {
        val offer = career.pendingOffers.firstOrNull { it.id == offerId } ?: return career
        return when {
            offer.status == OfferStatus.COMPLETED || offer.status == OfferStatus.WITHDRAWN -> career
            offer.sellingClubAgreed -> evaluatePlayer(career, offerId)
            else -> evaluateSellingClub(career, offerId)
        }
    }

    private fun evaluateSellingClub(career: Career, offerId: Long): Career {
        val offer = career.pendingOffers.firstOrNull { it.id == offerId } ?: return career
        val player = career.player(offer.playerId) ?: return career
        val required = requiredPackage(career, player, offer.toClubId)
        val offered = offer.offerPackage
        val total = offered.totalValue
        val clubName = career.club(offer.fromClubId)?.name ?: "The club"

        return when {
            required.fee <= 0L || total >= (required.fee * 0.97).toLong() -> {
                val agreed = offer.copy(
                    status = OfferStatus.ACCEPTED,
                    sellingClubResponse = SellingClubResponse.ACCEPT,
                    askingPrice = required.fee,
                    counterPackage = null,
                    message = "Fee agreed with $clubName"
                )
                evaluatePlayer(
                    career.copy(
                        pendingOffers = career.pendingOffers.map { if (it.id == offerId) agreed else it }
                    ),
                    offerId
                )
            }
            total >= (required.fee * 0.72).toLong() -> {
                val topUp = (required.fee - offered.playerOfferedValue).coerceAtLeast(
                    (required.fee * 0.5).toLong()
                )
                val counter = if (offered.hasMakeweight) offered.copy(fee = topUp)
                else TransferPackage(fee = required.fee)
                val countered = offer.copy(
                    status = OfferStatus.COUNTERED,
                    sellingClubResponse = SellingClubResponse.NEGOTIATE,
                    counterPackage = counter,
                    message = "$clubName counter: ${counter.label()}"
                )
                career.copy(
                    pendingOffers = career.pendingOffers.map { if (it.id == offerId) countered else it }
                )
            }
            else -> {
                val rejected = offer.copy(
                    status = OfferStatus.REJECTED,
                    sellingClubResponse = SellingClubResponse.REJECT,
                    askingPrice = required.fee,
                    message = "$clubName rejected the bid. They value ${player.name} at ${formatMoney(required.fee)}."
                )
                career.copy(
                    pendingOffers = career.pendingOffers.map { if (it.id == offerId) rejected else it }
                )
            }
        }
    }

    private fun evaluatePlayer(career: Career, offerId: Long): Career {
        val offer = career.pendingOffers.firstOrNull { it.id == offerId } ?: return career
        if (offer.status == OfferStatus.COMPLETED || offer.status == OfferStatus.WITHDRAWN) return career
        val player = career.player(offer.playerId) ?: return career
        val expected = expectedWage(career, player, offer.toClubId, offer.squadRole)

        val wageRatio = if (expected <= 0L) 1.0 else offer.wagePerWeek.toDouble() / expected
        val bonusHelp = if (offer.signingBonus > 0L) 0.03 else 0.0
        val clauseHelp = if (offer.releaseClause > 0L) 0.02 else 0.0
        val effectiveRatio = wageRatio + bonusHelp + clauseHelp

        return when {
            effectiveRatio >= 0.98 -> completeTransfer(career, offer, player)
            effectiveRatio >= 0.85 -> {
                val countered = offer.copy(
                    status = OfferStatus.COUNTERED,
                    playerResponse = PlayerResponse.NEGOTIATE,
                    expectedWage = expected,
                    message = "${player.name} wants ${formatMoney(expected)}/wk to sign."
                )
                career.copy(
                    pendingOffers = career.pendingOffers.map { if (it.id == offerId) countered else it }
                )
            }
            else -> {
                val rejected = offer.copy(
                    status = OfferStatus.REJECTED,
                    playerResponse = PlayerResponse.REJECT,
                    expectedWage = expected,
                    message = "${player.name} rejected the personal terms. He is asking for ${formatMoney(expected)}/wk."
                )
                career.copy(
                    pendingOffers = career.pendingOffers.map { if (it.id == offerId) rejected else it }
                )
            }
        }
    }

    // ------------------------------------------------------------- completion

    /** Moves the player, moves the money and writes the news. */
    private fun completeTransfer(career: Career, offer: TransferOffer, player: Player): Career {
        var idCounter = career.idCounter
        val buyingClub = career.clubOrThrow(offer.toClubId)
        val sellingClubId = player.clubId
        val makeweight = offer.offerPackage.playerOfferedId?.let { career.player(it) }

        val updatedClubs = career.clubs.map { club ->
            when (club.id) {
                buyingClub.id -> club.copy(
                    balance = club.balance - offer.fee,
                    transferBudget = (club.transferBudget - offer.fee).coerceAtLeast(0)
                )
                sellingClubId -> club.copy(balance = club.balance + offer.fee)
                else -> club
            }
        }

        val updatedPlayers = career.players.map { p ->
            when {
                p.id == player.id -> p.copy(
                    clubId = offer.toClubId,
                    wagePerWeek = offer.wagePerWeek,
                    contractYearsRemaining = offer.contractYears,
                    signedThisWindow = true,
                    seasonsAtClub = 0,
                    squadRole = offer.squadRole,
                    moraleScore = (p.moraleScore + 0.4).coerceAtMost(5.0)
                )
                makeweight != null && p.id == makeweight.id && sellingClubId != null -> p.copy(
                    clubId = sellingClubId,
                    signedThisWindow = true,
                    seasonsAtClub = 0,
                    moraleScore = (p.moraleScore + 0.2).coerceAtMost(5.0)
                )
                p.clubId == sellingClubId -> p.copy(moraleScore = (p.moraleScore - 0.05).coerceAtLeast(1.0))
                else -> p
            }
        }

        val ledger = mutableListOf<FinanceLedgerEntry>()
        val isUserBuy = buyingClub.id == career.userClubId
        val isUserSell = sellingClubId == career.userClubId
        // Only the user's own perspective is written to the ledger, so a single
        // purchase never shows up as both spending and income on their books.
        if (offer.fee > 0) {
            if (isUserBuy) {
                idCounter++
                ledger += FinanceLedgerEntry(
                    id = idCounter,
                    date = career.date,
                    season = career.season,
                    description = "Signed ${player.name} from ${sellingClubId?.let { career.club(it) }?.name ?: "free agency"}",
                    amount = -offer.fee,
                    category = LedgerCategory.TRANSFER_IN
                )
            }
            if (isUserSell) {
                idCounter++
                ledger += FinanceLedgerEntry(
                    id = idCounter,
                    date = career.date,
                    season = career.season,
                    description = "Sold ${player.name} to ${buyingClub.name}",
                    amount = offer.fee,
                    category = LedgerCategory.TRANSFER_OUT
                )
            }
        }

        idCounter++
        val news = career.news + NewsItem(
            id = idCounter,
            category = NewsCategory.TRANSFER,
            headline = "TRANSFER NEWS: ${buyingClub.name} sign ${player.name}",
            body = buildString {
                append("${buyingClub.name} have completed the signing of ${player.name} ")
                append("(${player.position.short}, ${player.age}) ")
                if (offer.offerPackage.hasMakeweight) {
                    append("in a package worth ${offer.offerPackage.label()} ")
                } else if (offer.fee > 0) {
                    append("for £${formatMoney(offer.fee)} ")
                } else {
                    append("on a free transfer ")
                }
                append("from ${sellingClubId?.let { career.club(it) }?.name ?: "free agency"}. ")
                append("He has signed a ${offer.contractYears}-year deal worth ")
                append("£${formatMoney(offer.wagePerWeek)} per week as a ${offer.squadRole.label.lowercase()}.")
            },
            date = career.date,
            season = career.season,
            clubId = buyingClub.id
        )

        val isUserTransfer = isUserBuy
        val isUserSale = isUserSell

        // Amortisation: a user signing adds a weekly charge; a user sale removes it.
        var book = career.amortisationBook
        if (isUserTransfer && offer.fee > 0) {
            book = book.filterNot { it.playerId == player.id } +
                AmortisationCharge.forSigning(player.id, player.name, offer.fee, offer.contractYears)
        }
        if (isUserSale) {
            book = book.filterNot { it.playerId == player.id }
        }

        // A permanent transfer-history row whenever the manager's club is involved.
        var history = career.transferHistory
        if (isUserTransfer || isUserSale) {
            idCounter++
            val direction = if (isUserTransfer) TransferDirection.IN else TransferDirection.OUT
            history = (history + TransferHistoryEntry(
                id = idCounter,
                playerId = player.id,
                playerName = player.name,
                position = player.position,
                age = player.age,
                fromClubId = sellingClubId ?: 0L,
                fromClubName = sellingClubId?.let { career.club(it)?.name } ?: "Free agent",
                toClubId = buyingClub.id,
                toClubName = buyingClub.name,
                fee = offer.fee,
                wagePerWeek = offer.wagePerWeek,
                contractYears = offer.contractYears,
                exchangedPlayerName = makeweight?.name ?: "",
                exchangedPlayerValue = if (makeweight != null) offer.offerPackage.playerOfferedValue else 0L,
                direction = direction,
                season = career.season,
                date = career.date,
                matchday = career.matchdayIndex
            )).takeLast(200)
        }

        return career.copy(
            clubs = updatedClubs,
            players = updatedPlayers,
            pendingOffers = career.pendingOffers.map {
                if (it.id == offer.id) it.copy(
                    status = OfferStatus.COMPLETED,
                    sellingClubResponse = SellingClubResponse.ACCEPT,
                    playerResponse = PlayerResponse.ACCEPT,
                    message = "Transfer completed"
                ) else it
            },
            ledger = (career.ledger + ledger).takeLast(600),
            news = news.takeLast(120),
            idCounter = idCounter,
            transferSpendThisSeason = if (isUserTransfer) career.transferSpendThisSeason + offer.fee
            else career.transferSpendThisSeason,
            transferIncomeThisSeason = if (isUserSale && !isUserTransfer) {
                career.transferIncomeThisSeason + offer.fee
            } else career.transferIncomeThisSeason,
            amortisationBook = book,
            transferHistory = history,
            transferListings = if (isUserSale) {
                career.transferListings.filterNot { it.playerId == player.id }
            } else career.transferListings
        )
    }

    /** Direct sale of a user player to an interested club. */
    fun sellPlayer(career: Career, playerId: Long, fee: Long, buyerClubId: Long): Career {
        val player = career.player(playerId) ?: return career
        if (player.clubId != career.userClubId) return career

        val offer = TransferOffer(
            id = career.idCounter + 1,
            playerId = playerId,
            playerName = player.name,
            fromClubId = career.userClubId,
            toClubId = buyerClubId,
            fee = fee,
            wagePerWeek = player.wagePerWeek,
            status = OfferStatus.ACCEPTED,
            askingPrice = fee,
            expectedWage = player.wagePerWeek,
            isUserInitiated = true,
            createdMatchday = career.matchdayIndex,
            offerPackage = TransferPackage(fee = fee)
        )
        val withOffer = career.copy(
            pendingOffers = career.pendingOffers + offer,
            pendingSale = null
        )
        return completeTransfer(withOffer, offer, player)
    }

    /** Releases a player, paying off the remainder of the contract. */
    fun releasePlayer(career: Career, playerId: Long): Career {
        val player = career.player(playerId) ?: return career
        if (player.clubId != career.userClubId) return career

        val payoff = player.wagePerWeek * 26
        var idCounter = career.idCounter
        val updatedClubs = career.clubs.map {
            if (it.id == career.userClubId) it.copy(balance = it.balance - payoff) else it
        }
        val updatedPlayers = career.players.map {
            if (it.id == playerId) it.copy(clubId = null, contractYearsRemaining = 0) else it
        }
        idCounter++
        val news = career.news + NewsItem(
            id = idCounter,
            category = NewsCategory.TRANSFER,
            headline = "${player.name} released",
            body = "${career.userClub.name} have released ${player.name}. " +
                "The club paid £${formatMoney(payoff)} to settle the remaining contract.",
            date = career.date,
            season = career.season,
            clubId = career.userClubId
        )
        idCounter++
        val ledger = career.ledger + FinanceLedgerEntry(
            id = idCounter,
            date = career.date,
            season = career.season,
            description = "Contract settlement for ${player.name}",
            amount = -payoff,
            category = LedgerCategory.OTHER
        )

        return career.copy(
            clubs = updatedClubs,
            players = updatedPlayers,
            news = news.takeLast(120),
            ledger = ledger.takeLast(600),
            idCounter = idCounter,
            amortisationBook = career.amortisationBook.filterNot { it.playerId == playerId },
            transferListings = career.transferListings.filterNot { it.playerId == playerId }
        )
    }

    // --------------------------------------------------------- sell negotiation

    /**
     * Lists one of the user's players for sale and immediately generates the
     * interested clubs. Interest scales with the player's quality, youth,
     * potential, form and position, and falls away as the asking price rises
     * above market value.
     */
    fun listPlayer(career: Career, playerId: Long, askingPrice: Long, random: Random): Career {
        val player = career.player(playerId) ?: return career
        if (player.clubId != career.userClubId) return career

        val value = player.value
        val asking = askingPrice.coerceAtLeast(value / 4)
        val listing = TransferListing(
            playerId = playerId,
            askingPrice = asking,
            createdMatchday = career.matchdayIndex
        )
        val bids = generateBids(career, player, asking, random)
        val negotiation = SaleNegotiation(
            id = career.idCounter + 1,
            playerId = playerId,
            playerName = player.name,
            marketValue = value,
            askingPrice = asking,
            bids = bids,
            status = if (bids.isEmpty()) SaleStatus.COLLAPSED else SaleStatus.OPEN,
            createdMatchday = career.matchdayIndex,
            message = if (bids.isEmpty()) {
                "No club is willing to meet that price right now."
            } else {
                "${bids.size} clubs have registered an interest in ${player.name}."
            }
        )
        return career.copy(
            transferListings = career.transferListings.filterNot { it.playerId == playerId } + listing,
            pendingSale = negotiation,
            idCounter = career.idCounter + 1
        )
    }

    /** Updates the asking price for a listed player and refreshes the interest. */
    fun updateAskingPrice(career: Career, playerId: Long, askingPrice: Long, random: Random): Career {
        val player = career.player(playerId) ?: return career
        if (player.clubId != career.userClubId) return career
        val asking = askingPrice.coerceAtLeast(player.value / 4)
        val negotiation = career.pendingSale?.takeIf { it.playerId == playerId }
        val bids = if (negotiation == null) {
            generateBids(career, player, asking, random)
        } else {
            // Re-evaluate existing bidders against the new price.
            regenerateBids(career, player, asking, negotiation, random)
        }
        val updated = SaleNegotiation(
            id = negotiation?.id ?: (career.idCounter + 1),
            playerId = playerId,
            playerName = player.name,
            marketValue = player.value,
            askingPrice = asking,
            bids = bids,
            status = if (bids.isEmpty()) SaleStatus.COLLAPSED else SaleStatus.OPEN,
            createdMatchday = negotiation?.createdMatchday ?: career.matchdayIndex,
            message = "Asking price set to ${formatMoney(asking)}. ${bids.size} clubs interested."
        )
        return career.copy(
            transferListings = career.transferListings.filterNot { it.playerId == playerId } +
                TransferListing(playerId, asking, career.matchdayIndex),
            pendingSale = updated,
            idCounter = if (negotiation == null) career.idCounter + 1 else career.idCounter
        )
    }

    /** How many clubs are interested in a listed player, 0..5. */
    fun interestedClubCount(career: Career, player: Player, askingPrice: Long): Int {
        val value = player.value.toDouble().coerceAtLeast(1.0)
        val ratio = askingPrice / value
        // Quality and youth drive interest.
        var score = (player.overall - 66) * 0.14 +
            (player.potential - player.overall) * 0.05 +
            (player.form - 5.0) * 0.10 +
            (28 - player.age).coerceAtLeast(-6) * 0.04
        score += when (player.position) {
            Position.ST, Position.RW, Position.LW, Position.CAM -> 0.5
            else -> 0.0
        }
        if (player.isInjured) score -= 1.5
        if (player.moraleScore <= 2.2) score += 0.2

        // Asking price: a bargain attracts more, a premium repels.
        score -= (ratio - 1.0) * 6.0

        return score.roundToInt().coerceIn(0, 5)
    }

    /** Builds the interested clubs and their opening bids for a listing. */
    private fun generateBids(
        career: Career,
        player: Player,
        askingPrice: Long,
        random: Random
    ): List<ClubBid> {
        val count = interestedClubCount(career, player, askingPrice)
        if (count <= 0) return emptyList()

        val value = player.value
        // Clubs with enough budget to realistically buy, best first.
        val candidates = career.clubs
            .filter { it.id != career.userClubId }
            .filter { it.transferBudget >= value / 3 }
            .sortedByDescending { it.reputation }
            .take(14)
        if (candidates.isEmpty()) return emptyList()

        // Richer, bigger clubs bid higher; use a shuffled weighted pick.
        val pool = candidates.shuffled(random)
        val chosen = pool.take(count.coerceAtMost(pool.size))

        return chosen.map { club ->
            // Bids cluster around market value, skewed by the club's means.
            val meansFactor = (club.transferBudget.toDouble() / value).coerceIn(0.5, 2.4)
            val base = value * (0.90 + random.nextDouble() * 0.10)
            val ambition = 0.96 + (meansFactor - 1.0).coerceIn(-0.2, 0.4) * 0.15
            val raw = (base * ambition).toLong()
            val amount = raw.coerceAtMost(askingPrice).coerceAtLeast(value / 3)
            ClubBid(
                clubId = club.id,
                clubName = club.name,
                clubReputation = club.reputation,
                amount = amount,
                status = BidStatus.INTERESTED,
                message = "${club.name} offer ${formatMoney(amount)}."
            )
        }.sortedByDescending { it.amount }
    }

    /** Re-evaluates existing bidders when the asking price changes. */
    private fun regenerateBids(
        career: Career,
        player: Player,
        askingPrice: Long,
        previous: SaleNegotiation,
        random: Random
    ): List<ClubBid> {
        val value = player.value
        val ratio = askingPrice.toDouble() / value.coerceAtLeast(1L)
        val kept = previous.bids.mapNotNull { bid ->
            val club = career.club(bid.clubId) ?: return@mapNotNull null
            if (!bid.isLive) return@mapNotNull null
            when {
                // A price far above value drives most clubs away.
                ratio >= 1.45 && random.nextDouble() < 0.75 -> null
                ratio >= 1.25 && random.nextDouble() < 0.45 -> null
                ratio >= 1.10 && random.nextDouble() < 0.20 -> null
                else -> {
                    val canStretch = club.transferBudget >= (value * ratio).toLong()
                    val maxAmount = if (canStretch) askingPrice else (value * 1.02).toLong()
                    bid.copy(
                        amount = bid.amount.coerceAtMost(maxAmount).coerceAtLeast(value / 3),
                        message = "${club.name} remain interested at ${formatMoney(bid.amount)}."
                    )
                }
            }
        }
        // Top up with fresh interest if the price came down and there is room.
        val extras = if (kept.size < interestedClubCount(career, player, askingPrice)) {
            generateBids(career, player, askingPrice, random)
                .filter { fresh -> kept.none { it.clubId == fresh.clubId } }
                .take(interestedClubCount(career, player, askingPrice) - kept.size)
        } else emptyList()
        return (kept + extras).sortedByDescending { it.amount }
    }

    /**
     * The manager counters one club's bid. The club answers at once — accept,
     * improve a little, stand firm or withdraw — and the other interested clubs
     * react in the same instant.
     */
    fun counterSaleBid(career: Career, clubId: Long, counterAmount: Long, random: Random): Career {
        val sale = career.pendingSale ?: return career
        val player = career.player(sale.playerId) ?: return career
        val bid = sale.bidFor(clubId) ?: return career
        if (!bid.isLive) return career

        val value = player.value.toDouble().coerceAtLeast(1.0)
        val counterRatio = counterAmount / value

        // The club's verdict on the counter.
        val responded = when {
            // A modest ask above their bid is usually accepted.
            counterAmount <= bid.amount -> bid.copy(
                amount = counterAmount,
                status = BidStatus.ACCEPTED,
                countered = true,
                message = "${bid.clubName} accept ${formatMoney(counterAmount)}."
            )
            // Asking far beyond their valuation makes them walk away.
            counterRatio >= 1.5 -> bid.copy(
                status = BidStatus.WITHDRAWN,
                countered = true,
                message = "${bid.clubName} have withdrawn from the negotiation."
            )
            // Reasonable stretch: they improve toward the counter.
            counterAmount <= (bid.amount * 1.18) -> {
                val improved = (bid.amount + (counterAmount - bid.amount) * 0.65).toLong()
                bid.copy(
                    amount = improved.coerceAtMost(counterAmount),
                    status = BidStatus.IMPROVED,
                    countered = true,
                    message = "${bid.clubName} improve to ${formatMoney(improved)}."
                )
            }
            else -> bid.copy(
                status = BidStatus.REJECTED,
                countered = true,
                message = "${bid.clubName} reject the counter and hold at ${formatMoney(bid.amount)}."
            )
        }

        // The other clubs react to the manager's stance.
        val others = sale.bids.map { other ->
            if (other.clubId == clubId || !other.isLive) return@map other
            val roll = random.nextDouble()
            when {
                roll < 0.22 -> other.copy(
                    status = BidStatus.WITHDRAWN,
                    message = "${other.clubName} leave the negotiation."
                )
                roll < 0.50 -> {
                    val improved = (other.amount * (1.01 + random.nextDouble() * 0.03)).toLong()
                        .coerceAtMost((value * 1.05).toLong())
                    other.copy(
                        amount = improved.coerceAtLeast(other.amount),
                        status = BidStatus.IMPROVED,
                        message = "${other.clubName} improve to ${formatMoney(improved)}."
                    )
                }
                else -> other
            }
        }

        val updatedBids = (others.map { if (it.clubId == clubId) responded else it })
        val anyLive = updatedBids.any { it.isLive }
        val accepted = responded.status == BidStatus.ACCEPTED
        val updatedSale = sale.copy(
            bids = updatedBids,
            status = when {
                accepted -> SaleStatus.AGREED
                anyLive -> SaleStatus.OPEN
                else -> SaleStatus.COLLAPSED
            },
            message = responded.message
        )
        return career.copy(pendingSale = updatedSale)
    }

    /** Accepts a club's bid and completes the sale immediately. */
    fun acceptSaleBid(career: Career, clubId: Long): Career {
        val sale = career.pendingSale ?: return career
        val player = career.player(sale.playerId) ?: return career
        val bid = sale.bidFor(clubId) ?: return career
        if (!bid.isLive) return career

        val marked = sale.copy(
            bids = sale.bids.map {
                if (it.clubId == clubId) it.copy(status = BidStatus.ACCEPTED)
                else if (it.isLive) it.copy(status = BidStatus.REJECTED)
                else it
            },
            status = SaleStatus.COMPLETED,
            message = "${player.name} sold to ${bid.clubName} for ${formatMoney(bid.amount)}."
        )
        val withSale = career.copy(pendingSale = marked)
        return sellPlayer(withSale, player.id, bid.amount, clubId)
    }

    /** Rejects one club's bid; it leaves the negotiation. */
    fun rejectSaleBid(career: Career, clubId: Long): Career {
        val sale = career.pendingSale ?: return career
        val bid = sale.bidFor(clubId) ?: return career
        val updatedBids = sale.bids.map {
            if (it.clubId == clubId) it.copy(status = BidStatus.REJECTED, message = "Bid rejected.") else it
        }
        val anyLive = updatedBids.any { it.isLive }
        return career.copy(
            pendingSale = sale.copy(
                bids = updatedBids,
                status = if (anyLive) SaleStatus.OPEN else SaleStatus.COLLAPSED,
                message = "${bid.clubName}'s bid rejected."
            )
        )
    }

    /** Cancels the sale entirely and clears the listing. */
    fun cancelSale(career: Career): Career {
        val sale = career.pendingSale ?: return career
        return career.copy(
            pendingSale = null,
            transferListings = career.transferListings.filterNot { it.playerId == sale.playerId }
        )
    }

    /** Clubs interested in buying one of the user's players (legacy view). */
    fun interestedBuyers(career: Career, playerId: Long, random: Random): List<Pair<Club, Long>> {
        val player = career.player(playerId) ?: return emptyList()
        val value = player.value
        return generateBids(career, player, (value * 1.05).toLong(), random)
            .mapNotNull { bid -> career.club(bid.clubId)?.let { it to bid.amount } }
    }

    // -------------------------------------------------------------- AI trading

    /**
     * Cheap AI-to-AI trading. Runs once per matchday during open windows and
     * moves a small number of surplus players between AI clubs, which keeps the
     * world feeling alive without measurable performance cost.
     */
    fun runAiTransferActivity(career: Career, random: Random): Career {
        if (!career.transferWindow.isOpen(career.matchdayIndex)) return career
        val deals = if (random.nextDouble() < 0.55) 1 else 0
        if (deals == 0) return career

        var current = career
        repeat(deals) {
            val aiClubs = current.clubs.filter { it.id != current.userClubId && it.leagueId.isNotBlank() }
            if (aiClubs.isEmpty()) return@repeat

            val buyer = aiClubs[random.nextInt(aiClubs.size)]
            val buyerSquad = current.players.filter { it.clubId == buyer.id }
            if (buyerSquad.isEmpty()) return@repeat

            val need = AiManager.weakestPosition(buyerSquad)

            val candidate = current.players
                .filter { it.clubId != null && it.clubId != buyer.id && it.clubId != current.userClubId }
                .filter { it.position == need }
                .filter { it.value <= buyer.transferBudget / 2 }
                .filter { it.overall < buyerSquad.maxOfOrNull { p -> p.overall } ?: 60 || it.age <= 23 }
                .minByOrNull { random.nextInt(1000) }
                ?: return@repeat

            val sellerId = candidate.clubId ?: return@repeat
            val seller = current.clubOrThrow(sellerId)
            val fee = AiManager.askingPrice(candidate, seller, current.difficulty.transferDifficulty)

            if (fee > buyer.transferBudget || fee > buyer.balance) return@repeat
            if (candidate.overall > (buyerSquad.maxOfOrNull { it.overall } ?: 0) + 6) return@repeat

            val updatedClubs = current.clubs.map { club ->
                when (club.id) {
                    buyer.id -> club.copy(
                        balance = club.balance - fee,
                        transferBudget = (club.transferBudget - fee).coerceAtLeast(0)
                    )
                    sellerId -> club.copy(balance = club.balance + fee)
                    else -> club
                }
            }
            val updatedPlayers = current.players.map { p ->
                if (p.id == candidate.id) {
                    p.copy(clubId = buyer.id, signedThisWindow = true, seasonsAtClub = 0)
                } else p
            }

            var idCounter = current.idCounter
            var news = current.news
            if (buyer.leagueId == current.userLeagueId || seller.leagueId == current.userLeagueId) {
                idCounter++
                news = news + NewsItem(
                    id = idCounter,
                    category = NewsCategory.TRANSFER,
                    headline = "${buyer.name} sign ${candidate.name}",
                    body = "${buyer.name} have signed ${candidate.name} from ${seller.name} " +
                        "for £${formatMoney(fee)}.",
                    date = current.date,
                    season = current.season,
                    clubId = buyer.id
                )
            }

            current = current.copy(
                clubs = updatedClubs,
                players = updatedPlayers,
                news = news.takeLast(120),
                idCounter = idCounter
            )
        }
        return current
    }

    // ---------------------------------------------------------------- helpers

    fun formatMoney(amount: Long): String = when {
        kotlin.math.abs(amount) >= 1_000_000_000 -> "%.2fB".format(amount / 1_000_000_000.0)
        kotlin.math.abs(amount) >= 1_000_000 -> "%.2fM".format(amount / 1_000_000.0)
        kotlin.math.abs(amount) >= 1_000 -> "%,dK".format(amount / 1_000)
        else -> amount.toString()
    }
}
