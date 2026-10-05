package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.data.SeasonCalendar
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.CupState
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.MatchStatus
import kotlin.random.Random

/**
 * The domestic knockout cup.
 *
 * A 32-club, single-elimination bracket seeded from every domestic division so
 * lower-league clubs get a shot at a giant-killing. Each round is a one-off tie
 * played midweek on its own designated matchday, decided by extra time and
 * penalties if the scores are level. Results are produced by the normal
 * [MatchEngine], so cup ties carry the same cards, injuries and player
 * statistics as any other fixture.
 *
 * The bracket is rebuilt round-by-round: once every tie in a round is played the
 * winners are written back into the slot list and the next round's fixtures are
 * generated, exactly the way the continental knockouts work.
 */
object CupEngine {

    const val CUP_CLUBS = 32

    /** The domestic round on which a cup round is played. */
    private fun roundMatchday(round: Int): Int = SeasonCalendar.cupRoundMatchday(round)

    /**
     * Creates the season's cup: seeds 32 clubs across all divisions and builds
     * the opening round. Returns the state and the first round's fixtures.
     */
    fun createSeason(
        career: Career,
        season: String,
        random: Random,
        idProvider: () -> Long
    ): Pair<CupState, List<Match>> {
        val entrants = selectEntrants(career.clubs, random, career.userClubId)
        if (entrants.size < CUP_CLUBS) {
            return CupState(season = season, active = false) to emptyList()
        }
        val state = CupState(season = season, round = 0, entrants = entrants, active = true)
        val fixtures = buildRound(state, idProvider)
        return state.copy(
            round = 1,
            fixtureIds = fixtures.map { it.id }
        ) to fixtures
    }

    /**
     * Picks the 32 entrants. Every top-flight club is invited; the remaining
     * places are filled from the lower divisions in reputation order. The field
     * is then seeded so the strongest clubs are spread across the bracket, and
     * the user's club is guaranteed a place so the manager always has a cup run.
     */
    fun selectEntrants(clubs: List<Club>, random: Random, userClubId: Long? = null): List<Long> {
        val topFlight = clubs.filter { it.leagueId == League.PREMIER_LEAGUE.id }
        val lower = clubs
            .filter { it.leagueId != League.PREMIER_LEAGUE.id }
            .sortedByDescending { it.reputation }
            .take(CUP_CLUBS - topFlight.size)
        val field = (topFlight + lower).toMutableList()
        val userClub = userClubId?.let { id -> clubs.firstOrNull { it.id == id } }
        if (userClub != null && field.none { it.id == userClub.id }) {
            // Make room for the user's club by dropping the lowest-ranked extra.
            val drop = field.filter { it.id != userClub.id }.minByOrNull { it.reputation }
            if (drop != null) field.remove(drop)
            field.add(userClub)
        }
        if (field.size < CUP_CLUBS) return field.map { it.id }
        return seedBracket(field, random)
    }

    /**
     * Orders the field into the bracket: clubs are ranked by reputation and the
     * top seeds are paired against lower seeds, so the strongest sides cannot all
     * meet in the first round. A light shuffle keeps the draw varied.
     */
    private fun seedBracket(field: List<Club>, random: Random): List<Long> {
        val ranked = field.sortedByDescending { it.reputation }
        val half = ranked.size / 2
        val top = ranked.take(half).toMutableList()
        val bottom = ranked.drop(half).toMutableList()
        top.shuffle(random)
        bottom.shuffle(random)
        val slots = mutableListOf<Long>()
        for (i in 0 until half) {
            // Alternate which side is at home so the draw is not predictable.
            if (random.nextBoolean()) {
                slots += top[i].id; slots += bottom[i].id
            } else {
                slots += bottom[i].id; slots += top[i].id
            }
        }
        return slots
    }

    /**
     * Builds the fixtures for the state's current round from its slot list.
     * Slots are paired adjacent: 0v1, 2v3, ... The first-listed side is at home.
     */
    fun buildRound(state: CupState, idProvider: () -> Long): List<Match> {
        val round = state.round.coerceAtLeast(1)
        val matchday = roundMatchday(round)
        val date = SeasonCalendar.cupDate(round)
        val fixtures = mutableListOf<Match>()
        var i = 0
        while (i + 1 < state.entrants.size) {
            fixtures += Match(
                id = idProvider(),
                leagueId = League.CHAMPIONS_LEAGUE.id,
                matchday = matchday,
                homeClubId = state.entrants[i],
                awayClubId = state.entrants[i + 1],
                status = MatchStatus.SCHEDULED,
                competition = CompetitionType.DOMESTIC_CUP,
                date = date,
                competitionRound = round
            )
            i += 2
        }
        return fixtures
    }

    /**
     * Advances the cup after every matchday. When a round is fully played it
     * writes the winners into the slot list, builds the next round and, once the
     * final is decided, crowns the winner.
     */
    fun progress(career: Career, idProvider: () -> Long): Career {
        var state = career.cup
        if (!state.active || state.round == 0 || state.round > CupState.ROUNDS) return career

        val fixtures = career.fixtures
        val roundFixtures = fixtures.filter {
            it.competition == CompetitionType.DOMESTIC_CUP && it.competitionRound == state.round
        }
        if (roundFixtures.isEmpty() || roundFixtures.any { !it.isPlayed }) return career

        val winners = roundFixtures
            .sortedBy { it.id }
            .map { match -> winnerOf(match) }
        if (winners.any { it == null }) return career

        val decided = winners.filterNotNull()
        var updated = state.copy(entrants = decided)

        return if (state.round >= CupState.ROUNDS || decided.size == 1) {
            updated = updated.copy(winnerClubId = decided.firstOrNull(), active = true)
            career.copy(cup = updated)
        } else {
            val nextState = updated.copy(round = state.round + 1)
            val newFixtures = buildRound(nextState, idProvider)
            updated = nextState.copy(fixtureIds = updated.fixtureIds + newFixtures.map { it.id })
            career.copy(cup = updated, fixtures = career.fixtures + newFixtures)
        }
    }

    /** The winner of a played cup tie, following extra time then the shootout. */
    fun winnerOf(match: Match): Long? {
        if (!match.isPlayed) return null
        val home = match.homeGoals + match.homeGoalsExtraTime
        val away = match.awayGoals + match.awayGoalsExtraTime
        return when {
            home > away -> match.homeClubId
            away > home -> match.awayClubId
            else -> {
                val sh = match.shootoutHome ?: return null
                val sa = match.shootoutAway ?: return null
                when {
                    sh > sa -> match.homeClubId
                    sa > sh -> match.awayClubId
                    else -> null
                }
            }
        }
    }
}
