package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/**
 * One season of a player's career, as it stood when the campaign ended.
 *
 * Everything here is a copy taken at turnover, so it survives even if the player
 * later retires, transfers away or is released: the history is about what
 * happened, not about the current squad list.
 */
@Serializable
data class PlayerSeasonRecord(
    val season: String,
    val seasonNumber: Int,
    val playerId: Long,
    val playerName: String,
    val clubId: Long,
    val clubName: String,
    val appearances: Int = 0,
    val starts: Int = 0,
    val minutesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val cleanSheets: Int = 0,
    val goalsConceded: Int = 0,
    val ratingSum: Double = 0.0,
    val ratedMatches: Int = 0,
    val manOfTheMatch: Int = 0,
    val wagePaid: Long = 0L,
    val injuryDays: Int = 0,
    val injuries: Int = 0,
    val longestInjuryDays: Int = 0,
    val trophies: List<String> = emptyList()
) {
    val averageRating: Double get() = if (ratedMatches == 0) 0.0 else ratingSum / ratedMatches
}

/** A player's whole career at the user's club, aggregated from every season. */
@Serializable
data class PlayerCareerRecord(
    val playerId: Long,
    val playerName: String,
    val position: Position,
    /** The season the player first appeared for the club. */
    val firstSeason: String = "",
    val lastSeason: String = "",
    val seasons: Int = 0,
    val appearances: Int = 0,
    val starts: Int = 0,
    val substituteAppearances: Int = 0,
    val minutesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val cleanSheets: Int = 0,
    val manOfTheMatch: Int = 0,
    val ratingSum: Double = 0.0,
    val ratedMatches: Int = 0,
    val totalWagesPaid: Long = 0L,
    val injuryDays: Int = 0,
    val injuries: Int = 0,
    val longestInjuryDays: Int = 0,
    val trophies: List<String> = emptyList(),
    val retired: Boolean = false,
    /** True when the player is still at the club this season. */
    val active: Boolean = true
) {
    val averageRating: Double get() = if (ratedMatches == 0) 0.0 else ratingSum / ratedMatches

    val goalsPerMatch: Double get() = if (appearances == 0) 0.0 else goals.toDouble() / appearances

    val assistsPerMatch: Double get() = if (appearances == 0) 0.0 else assists.toDouble() / appearances
}

/** A single-season record, used for the "best ever" leaderboards. */
@Serializable
data class SeasonRecordHolder(
    val playerId: Long,
    val playerName: String,
    val season: String,
    val value: Double,
    /** The club the record was set at. */
    val clubName: String = ""
)

/** A transfer fee in or out, for the record-transfers list. */
@Serializable
data class TransferRecordEntry(
    val playerId: Long,
    val playerName: String,
    val clubName: String,
    val otherClubName: String,
    val fee: Long,
    val season: String,
    /** True when the user's club received the fee. */
    val received: Boolean
)

/**
 * A trophy the user's club has won, kept across seasons.
 */
@Serializable
data class TrophyRecord(
    val season: String,
    val competition: String,
    val label: String
)

/**
 * A single notable match in the club's history, e.g. the biggest win or the
 * highest-scoring game. The goals are stored from the club's own perspective, so
 * "biggest win" and "biggest defeat" share one shape.
 */
@Serializable
data class MatchRecordEntry(
    val label: String,
    val season: String,
    val opponentName: String,
    val home: Boolean,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val competition: String = "",
    val date: String = ""
) {
    /** Positive for a win, negative for a defeat, from the club's perspective. */
    val margin: Int get() = goalsFor - goalsAgainst
    val totalGoals: Int get() = goalsFor + goalsAgainst
}

/** One line of the club's season-by-season history. */
@Serializable
data class ClubSeasonRecord(
    val season: String,
    val seasonNumber: Int,
    val leagueName: String,
    val leagueId: String,
    val finalPosition: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int,
    val topScorerName: String = "",
    val topScorerGoals: Int = 0,
    val topAssisterName: String = "",
    val topAssisterAssists: Int = 0,
    val bestRatingName: String = "",
    val bestRating: Double = 0.0,
    val trophies: List<String> = emptyList(),
    val europeanSummary: String = "",
    val transferSpend: Long = 0L,
    val transferIncome: Long = 0L,
    val wageSpend: Long = 0L,
    val revenue: Long = 0L,
    val profit: Long = 0L,
    val averageAttendance: Int = 0,
    val championName: String = ""
)

/**
 * The user's club history: everything the club profile screen needs that is not
 * already derivable from the current career state. Written once per season at
 * turnover and loaded unchanged, so past seasons never drift.
 */
@Serializable
data class ClubHistory(
    val seasons: List<ClubSeasonRecord> = emptyList(),
    val players: Map<Long, PlayerCareerRecord> = emptyMap(),
    val playerSeasons: List<PlayerSeasonRecord> = emptyList(),
    val trophies: List<TrophyRecord> = emptyList(),
    val transfersPaid: List<TransferRecordEntry> = emptyList(),
    val transfersReceived: List<TransferRecordEntry> = emptyList(),
    /** Highest single-season goals tally, for the record board. */
    val recordGoalsInSeason: SeasonRecordHolder? = null,
    val recordAssistsInSeason: SeasonRecordHolder? = null,
    val recordRatingInSeason: SeasonRecordHolder? = null,
    val recordAppearancesInSeason: SeasonRecordHolder? = null,
    val recordCleanSheetsInSeason: SeasonRecordHolder? = null,
    val recordManOfTheMatchInSeason: SeasonRecordHolder? = null,
    val recordHighestPoints: SeasonRecordHolder? = null,
    val recordLowestPoints: SeasonRecordHolder? = null,
    val recordMostGoals: SeasonRecordHolder? = null,
    val recordFewestConceded: SeasonRecordHolder? = null,
    /** Biggest wins, heaviest defeats and the highest-scoring matches ever played. */
    val notableMatches: List<MatchRecordEntry> = emptyList()
) {
    /** Aggregated players ordered by total goals, for the all-time board. */
    fun topScorers(limit: Int = 10): List<PlayerCareerRecord> =
        players.values.filter { it.goals > 0 }.sortedWith(
            compareByDescending<PlayerCareerRecord> { it.goals }
                .thenByDescending { it.assists }
                .thenBy { it.playerName }
        ).take(limit)

    fun topAssists(limit: Int = 10): List<PlayerCareerRecord> =
        players.values.filter { it.assists > 0 }.sortedWith(
            compareByDescending<PlayerCareerRecord> { it.assists }
                .thenByDescending { it.goals }
                .thenBy { it.playerName }
        ).take(limit)

    fun topAppearances(limit: Int = 10): List<PlayerCareerRecord> =
        players.values.filter { it.appearances > 0 }.sortedWith(
            compareByDescending<PlayerCareerRecord> { it.appearances }
                .thenByDescending { it.minutesPlayed }
                .thenBy { it.playerName }
        ).take(limit)

    fun topRatings(limit: Int = 10): List<PlayerCareerRecord> =
        players.values.filter { it.ratedMatches >= 5 }.sortedWith(
            compareByDescending<PlayerCareerRecord> { it.averageRating }
                .thenByDescending { it.appearances }
                .thenBy { it.playerName }
        ).take(limit)

    fun topWages(limit: Int = 10): List<PlayerCareerRecord> =
        players.values.filter { it.totalWagesPaid > 0 }.sortedWith(
            compareByDescending<PlayerCareerRecord> { it.totalWagesPaid }
                .thenBy { it.playerName }
        ).take(limit)

    fun mostInjured(limit: Int = 10): List<PlayerCareerRecord> =
        players.values.filter { it.injuries > 0 }.sortedWith(
            compareByDescending<PlayerCareerRecord> { it.injuryDays }
                .thenByDescending { it.injuries }
                .thenBy { it.playerName }
        ).take(limit)

    fun mostManOfTheMatch(limit: Int = 10): List<PlayerCareerRecord> =
        players.values.filter { it.manOfTheMatch > 0 }.sortedWith(
            compareByDescending<PlayerCareerRecord> { it.manOfTheMatch }
                .thenByDescending { it.appearances }
                .thenBy { it.playerName }
        ).take(limit)

    val totalTrophies: Int get() = trophies.size

    /** The notable match carrying a given label, if it has been set. */
    fun notableMatch(label: String): MatchRecordEntry? =
        notableMatches.firstOrNull { it.label == label }

    fun trophyCount(competition: String): Int =
        trophies.count { it.competition == competition }
}
