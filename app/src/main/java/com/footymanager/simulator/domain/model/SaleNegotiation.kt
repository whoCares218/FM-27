package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/** How far a single interested club's bid has progressed. */
@Serializable
enum class BidStatus(val label: String) {
    INTERESTED("Interested"),
    IMPROVED("Improved offer"),
    WITHDRAWN("Withdrawn"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected")
}

/**
 * One club's pursuit of a listed player. The user can accept it, reject it, or
 * counter it, and the club answers immediately.
 */
@Serializable
data class ClubBid(
    val clubId: Long,
    val clubName: String,
    val clubReputation: Int,
    val amount: Long,
    val status: BidStatus = BidStatus.INTERESTED,
    /** The club's most recent reply, shown to the manager. */
    val message: String = "",
    /** True once the user has countered this club's offer. */
    val countered: Boolean = false
) {
    val isLive: Boolean get() = status == BidStatus.INTERESTED || status == BidStatus.IMPROVED
}

@Serializable
enum class SaleStatus(val label: String) {
    OPEN("Open"),
    AGREED("Agreed"),
    COMPLETED("Completed"),
    COLLAPSED("No buyers"),
    CANCELLED("Cancelled")
}

/**
 * A live sale negotiation for one of the user's players. Bids arrive at once and
 * the negotiation continues instantly — there is no artificial "wait a few days"
 * step. Clubs can improve, stand firm or walk away as the manager counters.
 */
@Serializable
data class SaleNegotiation(
    val id: Long,
    val playerId: Long,
    val playerName: String,
    val marketValue: Long,
    val askingPrice: Long,
    val bids: List<ClubBid> = emptyList(),
    val status: SaleStatus = SaleStatus.OPEN,
    val createdMatchday: Int = 0,
    val message: String = ""
) {
    val liveBids: List<ClubBid> get() = bids.filter { it.isLive }

    val bestBid: ClubBid? get() = liveBids.maxByOrNull { it.amount }

    val interestedCount: Int get() = liveBids.size

    /** The bid the user has accepted, if any. */
    val acceptedBid: ClubBid? get() = bids.firstOrNull { it.status == BidStatus.ACCEPTED }

    fun bidFor(clubId: Long): ClubBid? = bids.firstOrNull { it.clubId == clubId }
}

/**
 * A player the manager has listed for sale, together with the asking price. The
 * listing is what triggers interest; raising the price above market value thins
 * the field of buyers, exactly as it should.
 */
@Serializable
data class TransferListing(
    val playerId: Long,
    val askingPrice: Long,
    val createdMatchday: Int = 0
)
