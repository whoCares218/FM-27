package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.ContractTerms
import com.footymanager.simulator.domain.model.FinanceLedgerEntry
import com.footymanager.simulator.domain.model.LedgerCategory
import com.footymanager.simulator.domain.model.NewsCategory
import com.footymanager.simulator.domain.model.NewsItem
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayerResponse
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.SellingClubResponse
import com.footymanager.simulator.domain.model.SquadRole
import com.footymanager.simulator.domain.model.TransferOffer
import com.footymanager.simulator.domain.model.TransferPackage
import kotlin.random.Random

/**
 * Transfer market: valuation, negotiation and (cheap) AI-to-AI trading.
 *
 * Negotiation is immediate. When the manager submits a bid the selling club
 * answers at once with ACCEPT, REJECT or a counter-offer, and the moment a fee is
 * agreed the player answers the personal terms at once too. Nothing waits for
 * another in-game day, so a deal can be done in a single sitting.
 *
 * A bid is a package: cash plus, optionally, one of the buyer's own players in
 * part exchange. The selling club reasons about the combined value together with
 * the player's rating, potential, age, contract length, importance to the squad,
 * the club's finances and the buyer's reputation, so a lower cash bid can still
 * succeed when the makeweight is good enough.
 */
object TransferEngine {

    /** Fee at which the selling club will accept immediately. */
    fun askingPrice(career: Career, player: Player, sellingClubId: Long?): Long {
        val sellingClub = sellingClubId?.let { career.club(it) }
        val difficulty = career.difficulty.transferDifficulty
        return if (sellingClub == null) {
            // Free agents cost nothing but still want a signing-on wage.
            0L
        } else {
            AiManager.askingPrice(player, sellingClub, difficulty)
        }
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
        // A player who is clearly the best in a thinly-stocked position is vital.
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
        val base = askingPrice(career, player, player.clubId)
        if (sellingClub == null) return TransferPackage(fee = 0L)

        val squad = career.squadOf(sellingClub.id)
        val importance = importance(player, squad)

        // Contract length: a player in his final year is cheaper to prise away.
        val contractFactor = when (player.contractYearsRemaining) {
            0 -> 0.70
            1 -> 0.84
            2 -> 0.96
            else -> 1.0
        }
        // Potential: a high-ceiling youngster commands a premium.
        val potentialFactor = 1.0 + (player.potential - player.overall).coerceAtLeast(0) * 0.012
        // Age: peak-age players are the most expensive to replace.
        val ageFactor = when {
            player.age <= 21 -> 1.10
            player.age <= 27 -> 1.06
            player.age <= 30 -> 1.0
            player.age <= 33 -> 0.92
            else -> 0.82
        }
        // A rich club can afford to say no; a cash-strapped one is keener to sell.
        val financesFactor = when {
            sellingClub.balance > sellingClub.wageBudget * 8 -> 1.10
            sellingClub.balance < 0 -> 0.90
            else -> 1.0
        }
        // Squad depth: plenty of cover makes the player expendable.
        val positionalDepth = squad.count { it.position == player.position }
        val depthFactor = if (positionalDepth >= 4) 0.92 else 1.0
        // Buyer reputation: a giant can be squeezed for a little more.
        val buyerFactor = 1.0 + (career.clubOrThrow(buyingClubId).reputation - 70) * 0.003
        // A player in red-hot form costs more.
        val formFactor = 1.0 + (player.form - 5.0) * 0.012
        // Importance is the strongest single signal.
        val importanceFactor = 0.86 + importance * 0.40

        val wanted = (base * contractFactor * potentialFactor * ageFactor * financesFactor *
            depthFactor * buyerFactor * formFactor * importanceFactor).toLong()
        return TransferPackage(fee = wanted.coerceAtLeast(player.value / 2))
    }

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
        return evaluateSellingClub(withOffer, offer.id, random = null)
    }

    /**
     * The user accepts the selling club's counter-offer. The agreed package is
     * then put to the player immediately.
     */
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

    /**
     * Re-evaluates an offer from the start (legacy/test entry point). Re-runs the
     * selling-club check on the current package and, if agreed, the player check.
     */
    fun resolveOffer(career: Career, offerId: Long, random: Random): Career {
        val offer = career.pendingOffers.firstOrNull { it.id == offerId } ?: return career
        return when {
            offer.status == OfferStatus.COMPLETED || offer.status == OfferStatus.WITHDRAWN -> career
            offer.sellingClubAgreed -> evaluatePlayer(career, offerId)
            else -> evaluateSellingClub(career, offerId, random)
        }
    }

    // ------------------------------------------------------------ evaluation

    /** The selling club's verdict on the current package, applied immediately. */
    private fun evaluateSellingClub(career: Career, offerId: Long, random: Random?): Career {
        val offer = career.pendingOffers.firstOrNull { it.id == offerId } ?: return career
        val player = career.player(offer.playerId) ?: return career
        val required = requiredPackage(career, player, offer.toClubId)
        val offered = offer.offerPackage
        val total = offered.totalValue
        val clubName = career.club(offer.fromClubId)?.name ?: "The club"

        return when {
            required.fee <= 0L || total >= (required.fee * 0.97).toLong() -> {
                // The selling club accepts: hand the deal straight to the player.
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
                // Close enough to talk: the club counters with what it wants.
                val topUp = (required.fee - (offered.playerOfferedValue)).coerceAtLeast(
                    (required.fee * 0.5).toLong()
                )
                val counter = if (offered.hasMakeweight) {
                    offered.copy(fee = topUp)
                } else {
                    TransferPackage(fee = required.fee)
                }
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

    /** The player's verdict on the personal terms, applied immediately. */
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
            effectiveRatio >= 0.98 -> {
                // The player accepts: the deal completes immediately.
                completeTransfer(career, offer, player)
            }
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
                // The makeweight moves the other way as part of the package.
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
        if (offer.fee > 0) {
            idCounter++
            ledger += FinanceLedgerEntry(
                id = idCounter,
                date = career.date,
                season = career.season,
                description = "Signed ${player.name} from ${sellingClubId?.let { career.club(it) }?.name ?: "free agency"}",
                amount = -offer.fee,
                category = LedgerCategory.TRANSFER_IN
            )
            if (sellingClubId != null) {
                idCounter++
                ledger += FinanceLedgerEntry(
                    id = idCounter,
                    date = career.date,
                    season = career.season,
                    description = "Sold ${player.name}",
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

        val isUserTransfer = buyingClub.id == career.userClubId
        val isUserSale = sellingClubId == career.userClubId

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
            ledger = (career.ledger + ledger).takeLast(400),
            news = news.takeLast(120),
            idCounter = idCounter,
            transferSpendThisSeason = if (isUserTransfer) career.transferSpendThisSeason + offer.fee
            else career.transferSpendThisSeason,
            transferIncomeThisSeason = if (isUserSale && !isUserTransfer) {
                career.transferIncomeThisSeason + offer.fee
            } else career.transferIncomeThisSeason
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
        val withOffer = career.copy(pendingOffers = career.pendingOffers + offer)
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
            ledger = ledger.takeLast(400),
            idCounter = idCounter
        )
    }

    /**
     * Cheap AI-to-AI trading. Runs once per matchday during open windows and
     * moves a small number of surplus players between AI clubs, which keeps the
     * world feeling alive without measurable performance cost.
     */
    fun runAiTransferActivity(career: Career, random: Random): Career {
        if (!career.transferWindow.isOpen(career.matchdayIndex)) return career
        // Only a couple of deals per week at most.
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

            // Find an affordable seller with a surplus player in that position.
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
                    p.copy(
                        clubId = buyer.id,
                        signedThisWindow = true,
                        seasonsAtClub = 0
                    )
                } else p
            }

            var idCounter = current.idCounter
            var news = current.news
            // Only surface AI deals involving the user's league so the feed stays relevant.
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

    /** Clubs interested in buying one of the user's players. */
    fun interestedBuyers(career: Career, playerId: Long, random: Random): List<Pair<com.footymanager.simulator.domain.model.Club, Long>> {
        val player = career.player(playerId) ?: return emptyList()
        return career.clubs
            .filter { it.id != career.userClubId }
            .filter { it.transferBudget >= player.value / 2 }
            .sortedByDescending { it.reputation }
            .take(4)
            .map { club ->
                val offer = (player.value * (0.85 + random.nextDouble() * 0.35)).toLong()
                club to offer
            }
            .sortedByDescending { it.second }
    }

    fun formatMoney(amount: Long): String = when {
        kotlin.math.abs(amount) >= 1_000_000_000 -> "%.2fB".format(amount / 1_000_000_000.0)
        kotlin.math.abs(amount) >= 1_000_000 -> "%.2fM".format(amount / 1_000_000.0)
        kotlin.math.abs(amount) >= 1_000 -> "%,dK".format(amount / 1_000)
        else -> amount.toString()
    }
}
