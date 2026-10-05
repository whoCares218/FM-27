package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/**
 * Competitions a fixture can belong to. Domestic leagues use their own id
 * ("ENG1", ...) while cups and continental football use the constants here.
 */
@Serializable
enum class CompetitionType(val label: String, val shortLabel: String) {
    LEAGUE("League", "LG"),
    DOMESTIC_CUP("Domestic Cup", "CUP"),
    CHAMPIONS_LEAGUE("Champions League", "UCL"),
    EUROPA_LEAGUE("Europa League", "UEL"),
    CONFERENCE_LEAGUE("Conference League", "UECL"),
    FRIENDLY("Friendly", "FR")
}

/** True for the three continental competitions, which share the league-phase format. */
val CompetitionType.isEuropean: Boolean
    get() = this == CompetitionType.CHAMPIONS_LEAGUE ||
        this == CompetitionType.EUROPA_LEAGUE ||
        this == CompetitionType.CONFERENCE_LEAGUE

/** The three continental competitions, in prestige order. */
val europeanCompetitions: List<CompetitionType> = listOf(
    CompetitionType.CHAMPIONS_LEAGUE,
    CompetitionType.EUROPA_LEAGUE,
    CompetitionType.CONFERENCE_LEAGUE
)

/** A Champions League club's participation status in the league phase. */
@Serializable
enum class UclStatus(val label: String) {
    DIRECT_R16("Round of 16"),
    PLAYOFF("Knockout play-offs"),
    ELIMINATED("Eliminated"),
    PENDING("In progress")
}

/** Knockout stage a tie belongs to. */
@Serializable
enum class KnockoutRound(val label: String, val legs: Int, val order: Int) {
    PLAYOFF("Knockout play-off", 2, 0),
    R16("Round of 16", 2, 1),
    QUARTER_FINAL("Quarter-final", 2, 2),
    SEMI_FINAL("Semi-final", 2, 3),
    FINAL("Final", 1, 4)
}

/**
 * A two-legged (or single-legged, for the final) knockout tie. Legs are stored
 * as fixture ids so the full match simulation still runs through the normal
 * engine and player statistics are recorded exactly as they are for league games.
 */
@Serializable
data class KnockoutTie(
    val id: Long,
    val round: KnockoutRound,
    /** Higher-seeded side; hosts the second leg. */
    val highSeedClubId: Long,
    val lowSeedClubId: Long,
    val firstLegMatchId: Long? = null,
    val secondLegMatchId: Long? = null,
    val highSeedAggregate: Int = 0,
    val lowSeedAggregate: Int = 0,
    val winnerClubId: Long? = null,
    /** Set when the tie went to a shootout after extra time. */
    val shootoutHigh: Int? = null,
    val shootoutLow: Int? = null
) {
    val decided: Boolean get() = winnerClubId != null

    fun isComplete(firstLegPlayed: Boolean, secondLegPlayed: Boolean): Boolean =
        firstLegPlayed && (round.legs == 1 || secondLegPlayed)
}

/**
 * The state of one continental competition: 36 clubs in a single league phase,
 * the fixture list, the standings and the knockout bracket.
 *
 * The Champions League, Europa League and Conference League all share this shape
 * (they differ only in the number of league-phase matches), so one class models
 * all three and each competition keeps its own independent instance.
 */
@Serializable
data class ChampionsLeagueState(
    val season: String,
    /** Which continental competition this state belongs to. */
    val competition: CompetitionType = CompetitionType.CHAMPIONS_LEAGUE,
    /** The 36 participating clubs, ordered by seeding (pot 1 first). */
    val participantIds: List<Long> = emptyList(),
    /** Pot index (0..3) per club id, derived from seeding. */
    val pots: Map<Long, Int> = emptyMap(),
    val table: List<TableRow> = emptyList(),
    /** League-phase fixtures, one per matchday. */
    val fixtureIds: List<Long> = emptyList(),
    val currentMatchday: Int = 1,
    val ties: List<KnockoutTie> = emptyList(),
    val winnerClubId: Long? = null,
    val active: Boolean = false
) {
    val isLeaguePhaseComplete: Boolean get() = currentMatchday > leaguePhaseMatchdays

    /** League-phase matches per club for this competition's format. */
    val leaguePhaseMatchdays: Int
        get() = if (competition == CompetitionType.CONFERENCE_LEAGUE) 6 else 8

    fun sortedTable(): List<TableRow> = table.sortedWith(TableRow.comparator)

    fun positionOf(clubId: Long): Int {
        val idx = sortedTable().indexOfFirst { it.clubId == clubId }
        return if (idx < 0) 0 else idx + 1
    }

    fun statusOf(clubId: Long): UclStatus {
        if (!active) return UclStatus.PENDING
        val pos = positionOf(clubId)
        if (pos == 0) return UclStatus.ELIMINATED
        return when {
            pos <= DIRECT_QUALIFIERS -> UclStatus.DIRECT_R16
            pos <= DIRECT_QUALIFIERS + PLAYOFF_SPOTS -> UclStatus.PLAYOFF
            else -> UclStatus.ELIMINATED
        }
    }

    fun potOf(clubId: Long): Int = pots[clubId] ?: 0

    companion object {
        /** The current format: 36 clubs in every competition. */
        const val PARTICIPANTS = 36
        /** League-phase matchdays: eight for UCL/UEL, six for the UECL. */
        const val LEAGUE_PHASE_MATCHDAYS = 8
        const val MATCHES_PER_CLUB = 8
        const val DIRECT_QUALIFIERS = 8
        const val PLAYOFF_SPOTS = 16

        /** An inactive state for a competition the user's club is not in. */
        fun empty(competition: CompetitionType = CompetitionType.CHAMPIONS_LEAGUE) =
            ChampionsLeagueState(season = "", competition = competition)

        val EMPTY = ChampionsLeagueState(season = "")
    }
}

/** The Europa League and Conference League use the same state shape. */
typealias EuropeanCompetitionState = ChampionsLeagueState

/** A row of the Champions League league-phase table, with a qualification band. */
data class UclTableEntry(
    val position: Int,
    val row: TableRow,
    val status: UclStatus
)
