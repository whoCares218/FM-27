package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.MatchStatus
import kotlin.random.Random

/**
 * Builds a full double round-robin league schedule using the circle method, then
 * mirrors it so every club plays each opponent home and away.
 *
 * The circle method guarantees each club plays exactly once per matchday, which
 * is what a league table requires.
 */
object FixtureGenerator {

    fun generateLeagueFixtures(
        league: League,
        clubs: List<Club>,
        random: Random,
        idProvider: () -> Long
    ): List<Match> {
        require(clubs.size >= 2) { "A league needs at least two clubs" }

        val teams = clubs.map { it.id }.toMutableList()
        // Odd numbers of clubs get a rotating bye; the database uses even counts.
        val hasBye = teams.size % 2 != 0
        if (hasBye) teams.add(BYE_ID)
        teams.shuffle(random)

        val n = teams.size
        val roundsPerHalf = n - 1
        val half = n / 2

        val firstHalf = mutableListOf<List<Pair<Long, Long>>>()

        // Standard circle method: fix the last team, rotate the rest each round.
        val rotation = teams.toMutableList()
        for (round in 0 until roundsPerHalf) {
            val roundPairs = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until half) {
                val home = rotation[i]
                val away = rotation[n - 1 - i]
                if (home == BYE_ID || away == BYE_ID) continue
                // Alternate home/away by round so no club is always at home.
                if (round % 2 == 0) roundPairs.add(home to away) else roundPairs.add(away to home)
            }
            firstHalf.add(roundPairs)

            // Rotate: keep index 0 fixed, move the rest one position clockwise.
            val fixed = rotation[0]
            val tail = rotation.subList(1, n).toMutableList()
            tail.add(0, tail.removeAt(tail.size - 1))
            rotation.clear()
            rotation.add(fixed)
            rotation.addAll(tail)
        }

        val matches = mutableListOf<Match>()
        var matchday = 1

        for (round in firstHalf) {
            for ((home, away) in round) {
                matches += Match(
                    id = idProvider(),
                    leagueId = league.id,
                    matchday = matchday,
                    homeClubId = home,
                    awayClubId = away,
                    status = MatchStatus.SCHEDULED
                )
            }
            matchday++
        }

        // Second half: reverse the fixtures so home advantage balances out.
        for (round in firstHalf) {
            for ((home, away) in round) {
                matches += Match(
                    id = idProvider(),
                    leagueId = league.id,
                    matchday = matchday,
                    homeClubId = away,
                    awayClubId = home,
                    status = MatchStatus.SCHEDULED
                )
            }
            matchday++
        }

        return matches
    }

    private const val BYE_ID = -999L
}
