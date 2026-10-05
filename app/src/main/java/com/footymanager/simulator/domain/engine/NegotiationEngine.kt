package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.BidStatus
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.NegotiationEvent
import com.footymanager.simulator.domain.model.NegotiationEventKind
import com.footymanager.simulator.domain.model.NegotiationOutcome
import com.footymanager.simulator.domain.model.NegotiationRecord
import com.footymanager.simulator.domain.model.NegotiationSide
import com.footymanager.simulator.domain.model.NegotiationStatus
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.PlayerResponse
import com.footymanager.simulator.domain.model.SaleNegotiation
import com.footymanager.simulator.domain.model.SaleStatus
import com.footymanager.simulator.domain.model.SellingClubResponse
import com.footymanager.simulator.domain.model.TransferOffer
import com.footymanager.simulator.domain.model.ClubBid
import com.footymanager.simulator.domain.model.Position

/**
 * Turns the transient buy/sell negotiation objects into durable
 * [NegotiationRecord]s on the career's transfer desk.
 *
 * The records are derived from [Career.pendingOffers] and [Career.pendingSale]
 * plus a handful of explicit calls at the moments where a source object is about
 * to disappear (a completed sale, a cancelled listing). Every update for the same
 * discussion appends to the same record, so the timeline grows in place and the
 * same transfer is never listed twice.
 */
object NegotiationEngine {

    /** Keep the desk bounded so a long career cannot grow the save without limit. */
    private const val MAX_RECORDS = 160

    /**
     * Dedupe key for a buy discussion. Keyed on the player and the selling club
     * (not the offer id) so that re-bidding on the same player appends to the
     * existing record instead of spawning a duplicate negotiation.
     */
    private fun buyKey(offer: TransferOffer): String = "buy:${offer.playerId}:${offer.fromClubId}"

    private fun sellKey(sale: SaleNegotiation, clubId: Long): String = "sell:${sale.id}:$clubId"

    // -------------------------------------------------------------- buy side

    /** Creates or updates a record for every bid the manager has made. */
    fun syncBuy(career: Career): Career {
        var records = career.negotiations
        career.pendingOffers.forEach { offer ->
            records = upsert(records, career) { existing ->
                buildBuyRecord(career, offer, existing)
            }
        }
        return career.copy(negotiations = trim(records))
    }

    private fun buildBuyRecord(
        career: Career,
        offer: TransferOffer,
        existing: NegotiationRecord?
    ): NegotiationRecord {
        val player = career.player(offer.playerId)
        val counterClub = career.club(offer.fromClubId)
        val status = buyStatus(offer)
        val offerValue = offer.offerPackage.totalValue
        val counterValue = offer.counterPackage?.totalValue ?: 0L

        val event = buyEvent(career, offer, status)
        val events = appendEvent(existing?.events.orEmpty(), event, career)

        return NegotiationRecord(
            id = existing?.id ?: nextId(career),
            sourceKey = buyKey(offer),
            playerId = offer.playerId,
            playerName = offer.playerName,
            playerPosition = player?.position ?: Position.CM,
            playerAge = player?.age ?: 0,
            marketValue = player?.value ?: offerValue,
            askingPrice = offer.askingPrice,
            side = NegotiationSide.BUY,
            counterPartyClubId = offer.fromClubId,
            counterPartyClubName = counterClub?.name ?: "Unknown club",
            userClubId = career.userClubId,
            userClubName = career.userClub.name,
            originalOffer = existing?.originalOffer ?: offerValue,
            latestOffer = offerValue,
            latestCounter = counterValue,
            wageProposal = offer.wagePerWeek,
            contractYears = offer.contractYears,
            status = status,
            events = events,
            createdSeason = existing?.createdSeason ?: career.season,
            createdDate = existing?.createdDate ?: career.date,
            updatedSeason = career.season,
            updatedDate = career.date,
            updatedMatchday = career.matchdayIndex,
            unread = if (existing == null) true else events.size > existing.events.size
        )
    }

    private fun buyStatus(offer: TransferOffer): NegotiationStatus = when (offer.status) {
        OfferStatus.PENDING -> NegotiationStatus.ONGOING
        OfferStatus.COUNTERED -> NegotiationStatus.NEGOTIATING
        OfferStatus.ACCEPTED -> NegotiationStatus.ACCEPTED
        OfferStatus.COMPLETED -> NegotiationStatus.COMPLETED
        OfferStatus.REJECTED, OfferStatus.COLLAPSED -> NegotiationStatus.REJECTED
        OfferStatus.WITHDRAWN -> NegotiationStatus.WITHDRAWN
    }

    private fun buyEvent(
        career: Career,
        offer: TransferOffer,
        status: NegotiationStatus
    ): NegotiationEvent {
        val club = career.club(offer.fromClubId)?.name ?: "The club"
        val amount = offer.offerPackage.totalValue
        val kind: NegotiationEventKind
        val actor: String
        val text: String
        when {
            offer.status == OfferStatus.COUNTERED && offer.sellingClubResponse == SellingClubResponse.NEGOTIATE -> {
                kind = NegotiationEventKind.COUNTER_CLUB
                actor = club
                text = offer.message.ifBlank { "$club countered" }
            }
            offer.status == OfferStatus.COUNTERED && offer.playerResponse == PlayerResponse.NEGOTIATE -> {
                kind = NegotiationEventKind.TERMS
                actor = offer.playerName
                text = offer.message.ifBlank { "${offer.playerName} wants more" }
            }
            offer.status == OfferStatus.COMPLETED -> {
                kind = NegotiationEventKind.AGREEMENT
                actor = career.userClub.name
                text = "${offer.playerName} signed for ${TransferEngine.formatMoney(offer.fee)}"
            }
            offer.status == OfferStatus.WITHDRAWN -> {
                kind = NegotiationEventKind.WITHDRAWN
                actor = career.userClub.name
                text = offer.message.ifBlank { "Offer withdrawn" }
            }
            offer.status == OfferStatus.REJECTED && offer.playerResponse == PlayerResponse.REJECT -> {
                kind = NegotiationEventKind.REJECTED
                actor = offer.playerName
                text = offer.message.ifBlank { "${offer.playerName} rejected the terms" }
            }
            offer.status == OfferStatus.REJECTED -> {
                kind = NegotiationEventKind.REJECTED
                actor = club
                text = offer.message.ifBlank { "$club rejected the bid" }
            }
            offer.status == OfferStatus.ACCEPTED -> {
                kind = NegotiationEventKind.INFO
                actor = club
                text = offer.message.ifBlank { "Fee agreed with $club" }
            }
            else -> {
                kind = NegotiationEventKind.OFFER
                actor = career.userClub.name
                text = "Bid submitted: ${TransferEngine.formatMoney(amount)}"
            }
        }
        return event(career, kind, actor, amount, text, status)
    }

    // ------------------------------------------------------------- sell side

    /**
     * Creates or updates a record for every bid on the current listing. If the
     * sale has just been completed or cancelled, the caller passes it before the
     * source is cleared so the terminal state is captured.
     */
    fun syncSell(
        career: Career,
        sale: SaleNegotiation? = null,
        managerCounters: Map<Long, Long> = emptyMap()
    ): Career {
        val current = sale ?: career.pendingSale ?: return career
        var records = career.negotiations
        current.bids.forEach { bid ->
            records = upsert(records, career) { existing ->
                buildSellRecord(career, current, bid, existing, managerCounters[bid.clubId])
            }
        }
        return career.copy(negotiations = trim(records))
    }

    /**
     * Marks every still-live record for [sale] as withdrawn. Called when the
     * manager abandons a listing, before the live sale object is discarded.
     */
    fun withdrawSaleRecords(career: Career, sale: SaleNegotiation): Career {
        val prefix = "sell:${sale.id}:"
        val updated = career.negotiations.map { record ->
            if (record.sourceKey.startsWith(prefix) && record.status.isLive) {
                val event = event(
                    career,
                    NegotiationEventKind.WITHDRAWN,
                    career.userClub.name,
                    0L,
                    "Negotiation abandoned",
                    NegotiationStatus.WITHDRAWN
                )
                record.copy(
                    status = NegotiationStatus.WITHDRAWN,
                    events = appendEvent(record.events, event, career),
                    updatedSeason = career.season,
                    updatedDate = career.date,
                    updatedMatchday = career.matchdayIndex,
                    unread = true
                )
            } else record
        }
        return career.copy(negotiations = updated)
    }

    private fun buildSellRecord(
        career: Career,
        sale: SaleNegotiation,
        bid: ClubBid,
        existing: NegotiationRecord?,
        managerCounter: Long?
    ): NegotiationRecord {
        val player = career.player(sale.playerId)
        val status = sellStatus(sale, bid)
        val club = career.club(bid.clubId)?.name ?: bid.clubName

        var events = existing?.events.orEmpty()
        // A manager counter sits on the timeline just before the club's reply.
        if (managerCounter != null && managerCounter != existing?.latestOffer) {
            events = appendEvent(
                events,
                event(
                    career,
                    NegotiationEventKind.COUNTER_MANAGER,
                    career.userClub.name,
                    managerCounter,
                    "Manager counter: ${TransferEngine.formatMoney(managerCounter)}",
                    NegotiationStatus.NEGOTIATING
                ),
                career
            )
        }
        events = appendEvent(events, sellEvent(career, sale, bid, status, club), career)

        return NegotiationRecord(
            id = existing?.id ?: nextId(career),
            sourceKey = sellKey(sale, bid.clubId),
            playerId = sale.playerId,
            playerName = sale.playerName,
            playerPosition = player?.position ?: Position.CM,
            playerAge = player?.age ?: 0,
            marketValue = sale.marketValue,
            askingPrice = sale.askingPrice,
            side = NegotiationSide.SELL,
            counterPartyClubId = bid.clubId,
            counterPartyClubName = club,
            userClubId = career.userClubId,
            userClubName = career.userClub.name,
            originalOffer = existing?.originalOffer ?: bid.amount,
            latestOffer = bid.amount,
            latestCounter = if (bid.status == BidStatus.IMPROVED) bid.amount else existing?.latestCounter ?: 0L,
            wageProposal = 0L,
            contractYears = 0,
            status = status,
            events = events,
            createdSeason = existing?.createdSeason ?: career.season,
            createdDate = existing?.createdDate ?: career.date,
            updatedSeason = career.season,
            updatedDate = career.date,
            updatedMatchday = career.matchdayIndex,
            unread = if (existing == null) true else events.size > existing.events.size
        )
    }

    private fun sellStatus(sale: SaleNegotiation, bid: ClubBid): NegotiationStatus = when (bid.status) {
        BidStatus.INTERESTED -> NegotiationStatus.ONGOING
        BidStatus.IMPROVED -> NegotiationStatus.NEGOTIATING
        BidStatus.ACCEPTED -> if (sale.status == SaleStatus.COMPLETED) {
            NegotiationStatus.COMPLETED
        } else {
            NegotiationStatus.ACCEPTED
        }
        BidStatus.REJECTED -> NegotiationStatus.REJECTED
        BidStatus.WITHDRAWN -> NegotiationStatus.WITHDRAWN
    }

    private fun sellEvent(
        career: Career,
        sale: SaleNegotiation,
        bid: ClubBid,
        status: NegotiationStatus,
        club: String
    ): NegotiationEvent {
        val kind: NegotiationEventKind
        val actor: String
        val text: String
        when (bid.status) {
            BidStatus.INTERESTED -> {
                kind = NegotiationEventKind.OFFER
                actor = club
                text = "$club offered ${TransferEngine.formatMoney(bid.amount)}"
            }
            BidStatus.IMPROVED -> {
                kind = NegotiationEventKind.COUNTER_CLUB
                actor = club
                text = "$club improved to ${TransferEngine.formatMoney(bid.amount)}"
            }
            BidStatus.ACCEPTED -> {
                kind = NegotiationEventKind.AGREEMENT
                actor = club
                text = if (sale.status == SaleStatus.COMPLETED) {
                    "${sale.playerName} sold to $club for ${TransferEngine.formatMoney(bid.amount)}"
                } else {
                    "$club accepted ${TransferEngine.formatMoney(bid.amount)}"
                }
            }
            BidStatus.REJECTED -> {
                kind = NegotiationEventKind.REJECTED
                actor = club
                text = bid.message.ifBlank { "$club's bid was rejected" }
            }
            BidStatus.WITHDRAWN -> {
                kind = NegotiationEventKind.WITHDRAWN
                actor = club
                text = bid.message.ifBlank { "$club withdrew" }
            }
        }
        return event(career, kind, actor, bid.amount, text, status)
    }

    // ----------------------------------------------------------- read state

    /** Marks one negotiation as read (opening its detail page clears the dot). */
    fun markRead(career: Career, recordId: Long): Career = career.copy(
        negotiations = career.negotiations.map {
            if (it.id == recordId) it.copy(unread = false) else it
        }
    )

    fun markAllRead(career: Career): Career = career.copy(
        negotiations = career.negotiations.map { if (it.unread) it.copy(unread = false) else it }
    )

    // -------------------------------------------------------------- helpers

    val activeRecords: (Career) -> List<NegotiationRecord> = { career ->
        career.negotiations.filter { it.status.isLive }.sortedByDescending { it.id }
    }

    fun countOutcome(career: Career, outcome: NegotiationOutcome): Int =
        career.negotiations.count { it.outcome == outcome }

    private fun nextId(career: Career): Long {
        val base = career.idCounter
        val used = career.negotiations.map { it.id }.toSet()
        var candidate = base + 1
        while (candidate in used) candidate++
        return candidate
    }

    private fun event(
        career: Career,
        kind: NegotiationEventKind,
        actor: String,
        amount: Long,
        text: String,
        status: NegotiationStatus
    ) = NegotiationEvent(
        id = 0L,
        kind = kind,
        actor = actor,
        amount = amount,
        text = text,
        season = career.season,
        date = career.date,
        matchday = career.matchdayIndex,
        status = status
    )

    /** Appends [new] unless it duplicates the last event on the timeline. */
    private fun appendEvent(
        existing: List<NegotiationEvent>,
        new: NegotiationEvent,
        career: Career
    ): List<NegotiationEvent> {
        val last = existing.lastOrNull()
        if (last != null && last.kind == new.kind && last.text == new.text && last.status == new.status) {
            return existing
        }
        return existing + new.copy(id = existing.size + 1L)
    }

    private fun upsert(
        records: List<NegotiationRecord>,
        career: Career,
        build: (NegotiationRecord?) -> NegotiationRecord
    ): List<NegotiationRecord> {
        // Freshly built record carries its own sourceKey; find the match by key.
        val probe = build(null)
        val index = records.indexOfFirst { it.sourceKey == probe.sourceKey }
        return if (index >= 0) {
            records.toMutableList().also { it[index] = build(records[index]) }
        } else {
            // Allocate an id that is unique across the running list, not just the
            // career, so several records created in one sync cannot collide.
            val used = records.mapTo(HashSet()) { it.id }
            var id = career.idCounter + 1
            while (id in used) id++
            records + probe.copy(id = id)
        }
    }

    private fun trim(records: List<NegotiationRecord>): List<NegotiationRecord> {
        if (records.size <= MAX_RECORDS) return records
        // Never drop a live negotiation; evict the oldest settled ones.
        val live = records.filter { it.status.isLive }
        val settled = records.filterNot { it.status.isLive }.sortedByDescending { it.id }
        return (live + settled.take((MAX_RECORDS - live.size).coerceAtLeast(0)))
            .sortedByDescending { it.id }
    }
}
