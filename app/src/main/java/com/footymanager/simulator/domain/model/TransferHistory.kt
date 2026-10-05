package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/** Which way a completed deal moved a player relative to the manager's club. */
@Serializable
enum class TransferDirection { IN, OUT }

/**
 * A permanent record of one completed transfer involving the manager's club.
 * Kept separately from the finance ledger because it carries football detail the
 * ledger has no room for (wage, contract length, any part-exchange player).
 */
@Serializable
data class TransferHistoryEntry(
    val id: Long,
    val playerId: Long,
    val playerName: String,
    val position: Position,
    val age: Int,
    val fromClubId: Long,
    val fromClubName: String,
    val toClubId: Long,
    val toClubName: String,
    /** Cash fee, excluding any makeweight valuation. */
    val fee: Long,
    val wagePerWeek: Long,
    val contractYears: Int,
    /** Name of a player included in the deal, or blank. */
    val exchangedPlayerName: String = "",
    val exchangedPlayerValue: Long = 0L,
    val direction: TransferDirection,
    val season: String,
    val date: GameDate,
    val matchday: Int
) {
    /** Headline package value: cash plus any part-exchange player. */
    val totalValue: Long get() = fee + exchangedPlayerValue
}
