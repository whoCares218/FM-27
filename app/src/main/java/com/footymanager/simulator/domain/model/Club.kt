package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Club(
    val id: Long,
    val name: String,
    val shortName: String,
    val country: String,
    val leagueId: String,
    /** 1..100 global standing; drives board expectations and AI behaviour. */
    val reputation: Int,
    val stadiumName: String,
    val stadiumCapacity: Int,
    val balance: Long,
    val transferBudget: Long,
    val wageBudget: Long,
    /** Board objective description shown on the board screen. */
    val boardExpectation: String,
    /** Minimum acceptable league position for the board. */
    val targetLeaguePosition: Int,
    /** Kit colours used for the crest placeholder, as 0xRRGGBB values. */
    val primaryColor: Int,
    val secondaryColor: Int,
    val formationId: String = Formation.F4231.id
) {
    val crestInitials: String
        get() = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { shortName.take(2).uppercase() }

    companion object {
        const val USER_CLUB_ID = -1L
    }
}

@Serializable
data class League(
    val id: String,
    val name: String,
    val country: String,
    /** 1..100; used to scale generated squad strength and prize money. */
    val reputation: Int,
    val promotionPlaces: Int = 0,
    val relegationPlaces: Int = 3,
    val championsLeaguePlaces: Int = 4,
    val prizeMoneyPerPlace: Long = 2_000_000L,
    /** Divisions below this one, used for promotion/relegation chains. */
    val tier: Int = 1
) {
    /** Compact label for tab strips, e.g. "PL", "Champ", "La Liga". */
    val shortName: String
        get() = when (id) {
            "ENG1" -> "Premier"
            "ENG2" -> "Championship"
            "ESP1" -> "La Liga"
            "ITA1" -> "Serie A"
            "GER1" -> "Bundesliga"
            "FRA1" -> "Ligue 1"
            else -> name
        }

    companion object {
        val PREMIER_LEAGUE = League(
            id = "ENG1", name = "Premier League", country = "England", reputation = 95,
            relegationPlaces = 3, championsLeaguePlaces = 4,
            prizeMoneyPerPlace = 3_200_000L, tier = 1
        )
        val CHAMPIONSHIP = League(
            id = "ENG2", name = "Championship", country = "England", reputation = 72,
            promotionPlaces = 3, relegationPlaces = 3, championsLeaguePlaces = 0,
            prizeMoneyPerPlace = 900_000L, tier = 2
        )
        val LA_LIGA = League(
            id = "ESP1", name = "La Liga", country = "Spain", reputation = 92,
            relegationPlaces = 3, championsLeaguePlaces = 4,
            prizeMoneyPerPlace = 2_800_000L, tier = 1
        )
        val SERIE_A = League(
            id = "ITA1", name = "Serie A", country = "Italy", reputation = 89,
            relegationPlaces = 3, championsLeaguePlaces = 4,
            prizeMoneyPerPlace = 2_600_000L, tier = 1
        )
        val BUNDESLIGA = League(
            id = "GER1", name = "Bundesliga", country = "Germany", reputation = 88,
            relegationPlaces = 2, championsLeaguePlaces = 4,
            prizeMoneyPerPlace = 2_500_000L, tier = 1
        )
        val LIGUE_1 = League(
            id = "FRA1", name = "Ligue 1", country = "France", reputation = 84,
            relegationPlaces = 3, championsLeaguePlaces = 3,
            prizeMoneyPerPlace = 2_100_000L, tier = 1
        )

        /**
         * The Champions League is a continental competition, not a domestic
         * division, so it is excluded from round-robin fixture generation and
         * driven by [com.footymanager.simulator.domain.engine.ChampionsLeagueEngine].
         */
        val CHAMPIONS_LEAGUE = League(
            id = "UCL", name = "Champions League", country = "Europe", reputation = 97,
            relegationPlaces = 0, championsLeaguePlaces = 0,
            prizeMoneyPerPlace = 0L, tier = 1
        )

        val all: List<League> = listOf(
            PREMIER_LEAGUE, CHAMPIONSHIP, LA_LIGA, SERIE_A, BUNDESLIGA, LIGUE_1
        )

        /** Leagues that play a domestic round-robin schedule. */
        val domestic: List<League> = all

        fun byId(id: String): League = all.firstOrNull { it.id == id }
            ?: if (id == CHAMPIONS_LEAGUE.id) CHAMPIONS_LEAGUE else PREMIER_LEAGUE
    }
}

/** One row of a league table. */
@Serializable
data class TableRow(
    val clubId: Long,
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val points: Int = 0
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst

    fun applyResult(scored: Int, conceded: Int): TableRow {
        val w = if (scored > conceded) 1 else 0
        val d = if (scored == conceded) 1 else 0
        val l = if (scored < conceded) 1 else 0
        return copy(
            played = played + 1,
            won = won + w,
            drawn = drawn + d,
            lost = lost + l,
            goalsFor = goalsFor + scored,
            goalsAgainst = goalsAgainst + conceded,
            points = points + w * 3 + d
        )
    }

    companion object {
        /** Standard football ordering: points, goal difference, goals scored, then id. */
        val comparator: Comparator<TableRow> = compareByDescending<TableRow> { it.points }
            .thenByDescending { it.goalDifference }
            .thenByDescending { it.goalsFor }
            .thenBy { it.clubId }
    }
}

@Serializable
enum class MatchStatus { SCHEDULED, PLAYED }

@Serializable
data class Match(
    val id: Long,
    val leagueId: String,
    val matchday: Int,
    val homeClubId: Long,
    val awayClubId: Long,
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    /** Set once the detailed simulation for this match has been generated. */
    val resultId: Long? = null,
    /** Which competition this fixture belongs to. */
    val competition: CompetitionType = CompetitionType.LEAGUE,
    /** Real calendar date of the fixture, so the schedule is never random. */
    val date: GameDate? = null,
    /** Knockout tie this fixture belongs to, when applicable. */
    val tieId: Long? = null,
    /** Leg number within a two-legged tie (1 or 2). */
    val leg: Int = 1,
    /**
     * Competition-specific round number: the UCL league-phase matchday (1..8)
     * or the knockout round order. Zero for domestic league fixtures, whose
     * round is already [matchday].
     */
    val competitionRound: Int = 0,
    /** Extra time and shootout scores for knockout matches. */
    val homeGoalsExtraTime: Int = 0,
    val awayGoalsExtraTime: Int = 0,
    val shootoutHome: Int? = null,
    val shootoutAway: Int? = null
) {
    val isPlayed: Boolean get() = status == MatchStatus.PLAYED

    val isKnockout: Boolean get() = competition == CompetitionType.CHAMPIONS_LEAGUE &&
        tieId != null

    val wentToExtraTime: Boolean
        get() = homeGoalsExtraTime != 0 || awayGoalsExtraTime != 0

    val wentToShootout: Boolean get() = shootoutHome != null && shootoutAway != null

    fun involves(clubId: Long): Boolean = homeClubId == clubId || awayClubId == clubId

    fun opponentOf(clubId: Long): Long = if (homeClubId == clubId) awayClubId else homeClubId

    fun isHomeFor(clubId: Long): Boolean = homeClubId == clubId

    /** Goals scored by a club including extra time, but not the shootout. */
    fun goalsFor(clubId: Long): Int {
        val base = if (homeClubId == clubId) homeGoals else awayGoals
        val et = if (homeClubId == clubId) homeGoalsExtraTime else awayGoalsExtraTime
        return base + et
    }

    fun goalsAgainst(clubId: Long): Int {
        val base = if (homeClubId == clubId) awayGoals else homeGoals
        val et = if (homeClubId == clubId) awayGoalsExtraTime else homeGoalsExtraTime
        return base + et
    }

    /** Human-readable competition label for the UI. */
    val competitionLabel: String
        get() = when (competition) {
            CompetitionType.LEAGUE -> League.byId(leagueId).shortName
            else -> competition.label
        }
}
