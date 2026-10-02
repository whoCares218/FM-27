package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.FinanceLedgerEntry
import com.footymanager.simulator.domain.model.LedgerCategory
import com.footymanager.simulator.domain.model.NewsCategory
import com.footymanager.simulator.domain.model.NewsItem
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.TransferOffer
import kotlin.random.Random

/**
 * Transfer market: valuation, negotiation, and (cheap) AI-to-AI trading.
 *
 * Negotiation is intentionally two-sided but simple: the selling club names an
 * asking price, the player names a wage, and the manager can keep improving the
 * offer. Each rejected bid slightly softens the selling club's stance so a
 * determined manager can eventually get a deal done at a fair price.
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

    /** Wage the player will sign for. */
    fun expectedWage(career: Career, player: Player, buyingClubId: Long): Long {
        val buyingClub = career.clubOrThrow(buyingClubId)
        return AiManager.expectedWage(player, buyingClub.reputation, career.difficulty.transferDifficulty)
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

    /** Creates a pending offer from the user's club. */
    fun createUserOffer(
        career: Career,
        player: Player,
        fee: Long,
        wage: Long,
        contractYears: Int,
        isLoan: Boolean = false
    ): Career {
        val asking = askingPrice(career, player, player.clubId)
        val expected = expectedWage(career, player, career.userClubId)
        var idCounter = career.idCounter
        idCounter++
        val offer = TransferOffer(
            id = idCounter,
            playerId = player.id,
            playerName = player.name,
            fromClubId = player.clubId ?: 0L,
            toClubId = career.userClubId,
            fee = fee,
            wagePerWeek = wage,
            contractYears = contractYears,
            status = OfferStatus.PENDING,
            askingPrice = asking,
            expectedWage = expected,
            isUserInitiated = true,
            isLoan = isLoan,
            createdMatchday = career.matchdayIndex,
            message = "Offer submitted"
        )
        return career.copy(
            pendingOffers = career.pendingOffers + offer,
            idCounter = idCounter
        )
    }

    /**
     * Evaluates a pending user offer and either completes the transfer or returns
     * a rejection with the selling club's revised demand.
     */
    fun resolveOffer(career: Career, offerId: Long, random: Random): Career {
        val offer = career.pendingOffers.firstOrNull { it.id == offerId } ?: return career
        if (offer.status != OfferStatus.PENDING) return career
        val player = career.player(offer.playerId) ?: return career

        val asking = askingPrice(career, player, player.clubId)
        val expected = expectedWage(career, player, career.userClubId)

        // ---- Fee check ----
        // A bid within 6% of the asking price is accepted; anything less is rejected
        // but softens the demand for next time.
        val feeAcceptable = asking == 0L || offer.fee >= (asking * 0.94).toLong()
        val wageAcceptable = offer.wagePerWeek >= (expected * 0.96).toLong()

        if (feeAcceptable && wageAcceptable) {
            return completeTransfer(career, offer, player)
        }

        var idCounter = career.idCounter
        idCounter++
        val revisedAsking = if (!feeAcceptable) {
            // The selling club comes down slightly on each failed attempt.
            maxOf(player.value, (asking * 0.94).toLong())
        } else asking

        val revisedWage = if (!wageAcceptable) {
            maxOf(player.wagePerWeek, (expected * 0.97).toLong())
        } else expected

        val message = when {
            !feeAcceptable && !wageAcceptable ->
                "Bid rejected. ${career.club(player.clubId ?: -1L)?.name ?: "The club"} want £${formatMoney(revisedAsking)} and the player wants £${formatMoney(revisedWage)}/wk."
            !feeAcceptable ->
                "Transfer fee rejected. ${career.club(player.clubId ?: -1L)?.name ?: "The club"} are holding out for £${formatMoney(revisedAsking)}."
            else ->
                "${player.name} rejected the personal terms. He is asking for £${formatMoney(revisedWage)}/wk."
        }

        val updatedOffer = offer.copy(
            status = OfferStatus.REJECTED,
            askingPrice = revisedAsking,
            expectedWage = revisedWage,
            message = message
        )

        val news = career.news + NewsItem(
            id = idCounter,
            category = NewsCategory.TRANSFER,
            headline = "Bid for ${player.name} rejected",
            body = message,
            date = career.date,
            season = career.season,
            clubId = career.userClubId
        )

        return career.copy(
            pendingOffers = career.pendingOffers.map { if (it.id == offerId) updatedOffer else it },
            news = news.takeLast(120),
            idCounter = idCounter
        )
    }

    /** Moves the player, moves the money and writes the news. */
    private fun completeTransfer(career: Career, offer: TransferOffer, player: Player): Career {
        var idCounter = career.idCounter
        val buyingClub = career.clubOrThrow(offer.toClubId)
        val sellingClubId = player.clubId

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
            if (p.id == player.id) {
                p.copy(
                    clubId = offer.toClubId,
                    wagePerWeek = offer.wagePerWeek,
                    contractYearsRemaining = offer.contractYears,
                    signedThisWindow = true,
                    seasonsAtClub = 0,
                    moraleScore = (p.moraleScore + 0.4).coerceAtMost(5.0)
                )
            } else if (p.clubId == sellingClubId) {
                // Selling a player unsettles the squad slightly.
                p.copy(moraleScore = (p.moraleScore - 0.05).coerceAtLeast(1.0))
            } else p
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
                if (offer.fee > 0) {
                    append("for £${formatMoney(offer.fee)} ")
                    append("from ${sellingClubId?.let { career.club(it) }?.name ?: "free agency"}. ")
                } else {
                    append("on a free transfer. ")
                }
                append("He has signed a ${offer.contractYears}-year deal worth ")
                append("£${formatMoney(offer.wagePerWeek)} per week.")
            },
            date = career.date,
            season = career.season,
            clubId = buyingClub.id
        )

        val isUserTransfer = buyingClub.id == career.userClubId

        return career.copy(
            clubs = updatedClubs,
            players = updatedPlayers,
            pendingOffers = career.pendingOffers.map {
                if (it.id == offer.id) it.copy(status = OfferStatus.COMPLETED, message = "Transfer completed") else it
            },
            ledger = (career.ledger + ledger).takeLast(400),
            news = news.takeLast(120),
            idCounter = idCounter,
            transferSpendThisSeason = if (isUserTransfer) career.transferSpendThisSeason + offer.fee
            else career.transferSpendThisSeason,
            transferIncomeThisSeason = if (sellingClubId == career.userClubId && !isUserTransfer) {
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
            createdMatchday = career.matchdayIndex
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
