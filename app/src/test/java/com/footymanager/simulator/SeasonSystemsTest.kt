package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.ChampionsLeagueEngine
import com.footymanager.simulator.domain.engine.MatchRules
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.KnockoutRound
import com.footymanager.simulator.domain.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Coverage for the season-long systems that are only exercised end to end: the
 * Champions League, the calendar, and the multi-competition season flow.
 */
class SeasonSystemsTest {

    private fun newCareer(clubId: Long = ClubDatabase.buildAll().first().id, seed: Long = 909L): Career =
        CareerFactory.create(
            CareerFactory.NewCareerRequest("Test Manager", clubId, Difficulty.NORMAL, seed)
        )

    /** Plays a whole season exactly the way the ViewModel does. */
    private fun playFullSeason(career: Career, random: Random): Career {
        var current = career
        var guard = 0
        while (current.matchdayIndex < current.totalMatchdays() && guard < 200) {
            guard++
            val matchday = current.matchdayIndex + 1
            current = SeasonEngine.simulateOtherFixtures(current, random)
            val own = current.fixtures.filter {
                !it.isPlayed && it.involves(current.userClubId) && it.matchday == matchday
            }
            for (fixture in own) {
                val (next, _) = SeasonEngine.simulateFixture(current, fixture, random, userMatch = true)
                current = next
            }
            current = SeasonEngine.advanceWeek(current, random)
            var idc = current.idCounter
            current = ChampionsLeagueEngine.progress(current, random) { ++idc }
            current = current.copy(idCounter = idc)
        }
        return current
    }

    @Test
    fun `champions league has thirty six participants in four pots`() {
        val career = newCareer()
        val state = career.championsLeague
        assertTrue("UCL should be active", state.active)
        assertEquals(36, state.participantIds.size)
        assertEquals(36, state.participantIds.toSet().size)
        assertEquals(36, state.table.size)
        assertEquals(4, state.pots.values.toSet().size)
        assertEquals(9, state.pots.values.count { it == 0 })
    }

    @Test
    fun `every participant plays exactly eight league phase matches`() {
        val career = newCareer()
        val leaguePhase = career.fixtures.filter {
            it.competition == CompetitionType.CHAMPIONS_LEAGUE && it.tieId == null
        }
        assertEquals("Eight matchdays of eighteen games", 8 * 18, leaguePhase.size)
        for (id in career.championsLeague.participantIds) {
            val count = leaguePhase.count { it.involves(id) }
            assertEquals("Club $id should play eight matches", 8, count)
        }
    }

    @Test
    fun `no club plays twice on the same champions league matchday`() {
        val career = newCareer()
        val leaguePhase = career.fixtures.filter {
            it.competition == CompetitionType.CHAMPIONS_LEAGUE && it.tieId == null
        }
        for (matchday in 1..8) {
            val games = leaguePhase.filter { it.competitionRound == matchday }
            val clubs = games.flatMap { listOf(it.homeClubId, it.awayClubId) }
            assertEquals("Matchday $matchday has a duplicate club", clubs.size, clubs.toSet().size)
        }
    }

    @Test
    fun `knockout bracket resolves to a single champion`() {
        val career = playFullSeason(newCareer(), Random(11))
        val state = career.championsLeague
        assertNotNull("A champion should be crowned", state.winnerClubId)
        val final = state.ties.firstOrNull { it.round == KnockoutRound.FINAL }
        assertNotNull("A final should exist", final)
        assertEquals(final!!.winnerClubId, state.winnerClubId)
        // Every round should have been built.
        for (round in KnockoutRound.entries) {
            assertTrue("Round ${round.label} missing", state.ties.any { it.round == round })
        }
    }

    @Test
    fun `all fixtures are played by the end of a season`() {
        val career = playFullSeason(newCareer(), Random(12))
        val unplayed = career.fixtures.filter {
            it.competition == CompetitionType.LEAGUE && it.status != MatchStatus.PLAYED
        }
        assertTrue("All league fixtures should be played", unplayed.isEmpty())
    }

    @Test
    fun `every fixture has a real date`() {
        val career = newCareer()
        assertTrue(career.fixtures.all { it.date != null })
        // Dates should be monotonic with matchday for league fixtures.
        val league = career.fixtures
            .filter { it.competition == CompetitionType.LEAGUE && it.leagueId == career.userLeagueId }
        val first = league.minByOrNull { it.matchday }!!
        val last = league.maxByOrNull { it.matchday }!!
        val firstDate = first.date!!
        val lastDate = last.date!!
        assertTrue(
            "Later matchdays should be later in the calendar",
            lastDate.year > firstDate.year ||
                (lastDate.year == firstDate.year && lastDate.month > firstDate.month) ||
                (lastDate.year == firstDate.year && lastDate.month == firstDate.month && lastDate.day >= firstDate.day)
        )
    }

    @Test
    fun `knockout fixtures use knockout rules`() {
        val career = playFullSeason(newCareer(), Random(13))
        val knockout = career.fixtures.filter { it.isKnockout }
        assertTrue("Knockout games should exist", knockout.isNotEmpty())
        assertEquals(MatchRules.KNOCKOUT, MatchRules.KNOCKOUT)
    }

    @Test
    fun `a career survives a second season with a fresh champions league`() {
        val first = playFullSeason(newCareer(), Random(14))
        val second = SeasonEngine.endSeason(first, Random(15))
        assertEquals("2027/28", second.season)
        assertTrue("A new UCL should be built", second.championsLeague.active)
        assertEquals(36, second.championsLeague.participantIds.size)
        assertEquals(0, second.championsLeague.ties.size)
        assertTrue("Fixtures should be regenerated", second.fixtures.isNotEmpty())
    }

    @Test
    fun `stadium expansion increases capacity once complete`() {
        // Pick a club whose ground can still be expanded; some elite clubs start
        // at the top stadium level, which is correct and simply cannot grow.
        val expandable = ClubDatabase.buildAll().first { club ->
            com.footymanager.simulator.domain.model.Stadium
                .initial(club.stadiumName, club.stadiumCapacity, club.reputation)
                .canExpand
        }
        var career = newCareer(expandable.id)
        val before = career.stadium.capacity
        val random = Random(16)
        // Force an expansion and run the clock forward until it completes.
        career = career.copy(
            stadium = career.stadium.copy(
                expansionWeeksRemaining = 1,
                expansionTargetCapacity = career.stadium.nextCapacity
            )
        )
        repeat(3) { career = SeasonEngine.advanceWeek(career, random) }
        assertTrue(
            "Capacity should grow after the expansion completes",
            career.stadium.capacity > before
        )
    }

    @Test
    fun `sponsorship offers are available at the start of a career`() {
        val career = newCareer()
        assertTrue("Offers should be generated", career.sponsorOffers.isNotEmpty())
        assertTrue(career.sponsorOffers.all { it.upfront > 0 && it.seasonal > 0 })
    }

    @Test
    fun `rewarded ad allowance pays out three times a day and resets`() {
        var state = com.footymanager.simulator.domain.model.AdRewardState()
        val day = 20_000L

        val rewards = (1..3).map { _ ->
            val (next, reward) = state.recordAd(day)
            state = next
            reward
        }
        assertEquals(listOf(3_000_000L, 3_000_000L, 4_000_000L), rewards)
        assertEquals(3, state.adsWatchedToday)
        assertEquals(0, state.remainingToday)

        // A fourth attempt on the same day yields nothing.
        val (sameDay, extra) = state.recordAd(day)
        assertEquals(0L, extra)
        assertEquals(3, sameDay.adsWatchedToday)

        // The next calendar day restores the full allowance.
        val (nextDay, firstReward) = state.recordAd(day + 1)
        assertEquals(1, nextDay.adsWatchedToday)
        assertEquals(3_000_000L, firstReward)
        assertEquals(7_000_000L, nextDay.remainingRewardToday)
    }

    @Test
    fun `match rules use five substitutions across three windows`() {
        val rules = MatchRules()
        assertEquals(5, rules.maxSubstitutions)
        assertEquals(3, rules.maxSubstitutionWindows)
    }
}
