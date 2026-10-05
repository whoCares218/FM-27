package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/** Which side of the table the manager is on. */
@Serializable
enum class NegotiationSide(val label: String) {
    BUY("Buying"),
    SELL("Selling")
}

/** The broad bucket a negotiation sits in on the Negotiations desk. */
@Serializable
enum class NegotiationOutcome(val label: String) {
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn")
}

/**
 * The precise state of a negotiation. [ONGOING] and [NEGOTIATING] are the two
 * live states (a counter-offer moves it to [NEGOTIATING]); the rest are terminal
 * and map onto one of the [NegotiationOutcome] buckets.
 */
@Serializable
enum class NegotiationStatus(val label: String) {
    ONGOING("Ongoing"),
    NEGOTIATING("Negotiating"),
    ACCEPTED("Accepted"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    val isLive: Boolean get() = this == ONGOING || this == NEGOTIATING || this == ACCEPTED

    val outcome: NegotiationOutcome
        get() = when (this) {
            ONGOING, NEGOTIATING, ACCEPTED -> NegotiationOutcome.ONGOING
            COMPLETED -> NegotiationOutcome.COMPLETED
            REJECTED -> NegotiationOutcome.REJECTED
            WITHDRAWN -> NegotiationOutcome.WITHDRAWN
        }
}

/** The kind of event recorded on the negotiation timeline. */
@Serializable
enum class NegotiationEventKind {
    OFFER,
    COUNTER_CLUB,
    COUNTER_MANAGER,
    TERMS,
    AGREEMENT,
    REJECTED,
    WITHDRAWN,
    INFO
}

/** One line on a negotiation's timeline. */
@Serializable
data class NegotiationEvent(
    val id: Long,
    val kind: NegotiationEventKind,
    val actor: String,
    /** Money attached to the event, or 0 when not a cash event. */
    val amount: Long = 0L,
    val text: String,
    val season: String,
    val date: GameDate,
    val matchday: Int,
    val status: NegotiationStatus
)

/**
 * A durable record of a single transfer discussion — the manager's side of it,
 * the other club, the money on the table and the full back-and-forth. One record
 * exists per (offer) on the buy side and per (listing + bidding club) on the sell
 * side, so the same discussion is never duplicated: subsequent counter-offers are
 * appended to the same record rather than starting a new one.
 *
 * It is stored inside [Career], so it is saved by the same atomic write as the
 * rest of the game and survives restarts, save/load and season turnover.
 */
@Serializable
data class NegotiationRecord(
    /** Stable identity for navigation; independent of the source offer/sale ids. */
    val id: Long,
    /** Dedupe key: "buy:<offerId>" or "sell:<saleId>:<clubId>". */
    val sourceKey: String,
    val playerId: Long,
    val playerName: String,
    val playerPosition: Position = Position.CM,
    val playerAge: Int = 0,
    val marketValue: Long,
    val askingPrice: Long,
    val side: NegotiationSide,
    /** The other club in the deal (seller on the buy side, bidder on the sell side). */
    val counterPartyClubId: Long,
    val counterPartyClubName: String,
    val userClubId: Long,
    val userClubName: String,
    val originalOffer: Long,
    val latestOffer: Long,
    /** The most recent counter from the other side, or 0 when there is none. */
    val latestCounter: Long = 0L,
    val wageProposal: Long = 0L,
    val contractYears: Int = 0,
    val status: NegotiationStatus,
    val events: List<NegotiationEvent> = emptyList(),
    val createdSeason: String,
    val createdDate: GameDate,
    val updatedSeason: String,
    val updatedDate: GameDate,
    val updatedMatchday: Int,
    /** True when something happened the manager has not looked at yet. */
    val unread: Boolean = true
) {
    val outcome: NegotiationOutcome get() = status.outcome

    val isLive: Boolean get() = status.isLive

    /** "Selling Club → Your Club" or "Your Club → Buying Club". */
    fun direction(): String = when (side) {
        NegotiationSide.BUY -> "$counterPartyClubName → $userClubName"
        NegotiationSide.SELL -> "$userClubName → $counterPartyClubName"
    }

    /** 0..1 share used for the progress indicator; terminal states are full. */
    val progress: Float
        get() = when (status) {
            NegotiationStatus.ONGOING -> 0.35f
            NegotiationStatus.NEGOTIATING -> 0.65f
            NegotiationStatus.ACCEPTED -> 0.85f
            else -> 1f
        }
}
