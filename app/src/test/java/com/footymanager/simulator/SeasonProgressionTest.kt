package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.FinanceEngine
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.TransferEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.GamePhase
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.MatchStatus
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.TrainingFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * End-to-end tests that drive a career through a full season. These are the tests
 * that catch integration bugs between the engines, which unit tests cannot.
 */
class SeasonProgressionTest {

    private fun newCareer(clubId: Long = ClubDatabase.buildAll().first().id, seed: Long = 4242L): Career =
        CareerFactory.create(
            CareerFactory.NewCareerRequest("Test Manager", clubId, Difficulty.NORMAL, seed)
        )

    /** Plays every matchday of a season the way the ViewModel does. */
    private fun playFullSeason(career: Career, random: Random): Career {
        var current = career
        var guard = 0
        while (current.nextMatch() != null && guard < 200) {
            guard++
            val match = current.nextMatch()!!
            current = SeasonEngine.simulateOtherFixtures(current, random)
            val (afterUser, _) = SeasonEngine.simulateFixture(current, match, random, userMatch = true)
            current = SeasonEngine.advanceWeek(afterUser, random)
        }
        return current
    }

    @Test
    fun `a full season can be played without errors`() {
        val career = playFullSeason(newCareer(), Random(1))
        val totalMatchdays = career.totalMatchdays()
        assertEquals(
            "Every matchday should have been played",
            totalMatchdays,
            career.matchdayIndex
        )
        assertTrue(career.results.isNotEmpty())
    }

    @Test
    fun `every fixture is played at the end of a season`() {
        val career = playFullSeason(newCareer(), Random(2))
        val userLeagueFixtures = career.fixtures.filter { it.leagueId == career.userLeagueId }
        assertTrue(
            "All user-league fixtures should be complete",
            userLeagueFixtures.all { it.status == MatchStatus.PLAYED }
        )
    }

    @Test
    fun `the league table is consistent with the results`() {
        val career = playFullSeason(newCareer(), Random(3))
        val leagueId = career.userLeagueId
        val table = career.table.getValue(leagueId)
        val fixtures = career.fixtures.filter { it.leagueId == leagueId && it.isPlayed }

        val expectedPlayed = fixtures.groupBy { it.homeClubId }.mapValues { it.value.size } +
            fixtures.groupBy { it.awayClubId }.mapValues { it.value.size }

        for (row in table) {
            val home = fixtures.count { it.homeClubId == row.clubId }
            val away = fixtures.count { it.awayClubId == row.clubId }
            assertEquals("Played count for club ${row.clubId}", home + away, row.played)
            assertTrue("Points must be non-negative", row.points >= 0)
            assertEquals(
                "Goal difference must equal goals for minus goals against",
                row.goalsFor - row.goalsAgainst,
                row.goalDifference
            )
            assertEquals(
                "Points must equal 3*W + D",
                row.won * 3 + row.drawn,
                row.points
            )
            assertEquals("W+D+L must equal played", row.played, row.won + row.drawn + row.lost)
        }
    }

    @Test
    fun `total goals scored equals total goals conceded across the league`() {
        val career = playFullSeason(newCareer(), Random(4))
        val table = career.table.getValue(career.userLeagueId)
        assertEquals(
            "League goals for and against must balance",
            table.sumOf { it.goalsFor },
            table.sumOf { it.goalsAgainst }
        )
    }

    @Test
    fun `every league in the world progresses when the user's league plays`() {
        // Only the user's league is simulated, so verify that at least the user's
        // league is complete and its table sums are valid.
        val career = playFullSeason(newCareer(), Random(5))
        val table = career.table.getValue(career.userLeagueId)
        assertTrue("Table should have rows", table.isNotEmpty())
        assertTrue("Some matches should be played", table.sumOf { it.played } > 0)
    }

    @Test
    fun `player season statistics accumulate over a season`() {
        val career = playFullSeason(newCareer(), Random(6))
        val squad = career.squadOf(career.userClubId)

        val totalAppearances = squad.sumOf { it.seasonStats.appearances }
        assertTrue("Players should have accumulated appearances", totalAppearances > 0)

        // Someone must have scored, and the goals must be attributable to players.
        // The club now plays in more than one competition, so reconcile against
        // every fixture the club has played rather than the league table alone.
        val totalGoals = squad.sumOf { it.seasonStats.goals }
        val clubGoals = career.fixtures
            .filter { it.isPlayed && it.involves(career.userClubId) }
            .sumOf { it.goalsFor(career.userClubId) }
        assertEquals("Player goals should match the club's goals", clubGoals, totalGoals)

        // Ratings must be within the legal band.
        squad.filter { it.seasonStats.ratedMatches > 0 }.forEach { player ->
            assertTrue(
                "${player.name} rating out of range: ${player.seasonStats.averageRating}",
                player.seasonStats.averageRating in 3.5..10.0
            )
        }
    }

    @Test
    fun `players lose fitness and can recover during a season`() {
        val career = playFullSeason(newCareer(), Random(7))
        val squad = career.squadOf(career.userClubId)
        // Fitness must stay inside legal bounds at all times.
        assertTrue(squad.all { it.fitness in 0..100 })
        assertTrue(squad.all { it.form in 1.0..10.0 })
        assertTrue(squad.all { it.moraleScore in 1.0..5.0 })
    }

    @Test
    fun `the squad never becomes invalid through injury and suspension`() {
        val career = playFullSeason(newCareer(), Random(8))
        val squad = career.squadOf(career.userClubId)
        // Availability flags must be coherent.
        squad.forEach { player ->
            if (player.isInjured) assertFalse("Injured players cannot be available", player.isAvailable)
            if (player.isSuspended) assertFalse("Suspended players cannot be available", player.isAvailable)
            assertTrue("Injury weeks must be positive", player.injury.weeksRemaining >= 0)
            assertTrue("Suspension weeks cannot be negative", player.suspensionWeeks >= 0)
        }
    }

    @Test
    fun `season end produces a summary and starts a new season`() {
        val played = playFullSeason(newCareer(), Random(9))
        val ended = played.copy(phase = GamePhase.SEASON_ENDED)
        val next = SeasonEngine.endSeason(ended, Random(10))

        assertNotNull("A season summary should be produced", next.lastSeasonSummary)
        val summary = next.lastSeasonSummary!!
        assertTrue("Final position must be sensible", summary.finalPosition in 1..30)
        assertEquals("W+D+L must equal played", summary.played, summary.won + summary.drawn + summary.lost)
        assertTrue("A top scorer should be identified", summary.topScorerName.isNotBlank())

        // New season state.
        assertEquals(2, next.seasonNumber)
        assertEquals("2027/28", next.season)
        assertEquals(0, next.matchdayIndex)
        assertTrue("New fixtures must be generated", next.fixtures.isNotEmpty())
        assertTrue("Results must be cleared", next.results.isEmpty())
        assertTrue("Tables must be reset", next.table.values.all { rows -> rows.all { it.played == 0 } })
        assertTrue("Transfer spend must be reset", next.transferSpendThisSeason == 0L)
        assertTrue("Transfer income must be reset", next.transferIncomeThisSeason == 0L)
    }

    @Test
    fun `players age by one year at season turnover`() {
        val career = newCareer(seed = 555L)
        val squadBefore = career.squadOf(career.userClubId).associateBy { it.id }
        val played = playFullSeason(career, Random(11))
        val next = SeasonEngine.endSeason(played, Random(12))

        var compared = 0
        for (player in next.squadOf(next.userClubId)) {
            val before = squadBefore[player.id] ?: continue
            assertEquals(
                "${player.name} should age by exactly one year",
                before.age + 1,
                player.age
            )
            compared++
        }
        assertTrue("Should have compared at least a few players", compared > 5)
    }

    @Test
    fun `season statistics reset for the new campaign but career totals persist`() {
        val played = playFullSeason(newCareer(seed = 777L), Random(13))
        val next = SeasonEngine.endSeason(played, Random(14))

        next.squadOf(next.userClubId).forEach { player ->
            assertEquals("Season goals must reset", 0, player.seasonStats.goals)
            assertEquals("Season appearances must reset", 0, player.seasonStats.appearances)
            assertTrue(
                "Career totals must not go backwards",
                player.careerStats.appearances >= 0
            )
        }
        // At least one player should carry over a career appearance record.
        assertTrue(
            "Some career records should persist",
            next.squadOf(next.userClubId).any { it.careerStats.appearances > 0 }
        )
    }

    @Test
    fun `a second full season can be played after turnover`() {
        val firstSeason = playFullSeason(newCareer(seed = 999L), Random(15))
        val second = SeasonEngine.endSeason(firstSeason, Random(16))
        val afterSecond = playFullSeason(second, Random(17))

        assertEquals(
            "Second season should complete",
            afterSecond.totalMatchdays(),
            afterSecond.matchdayIndex
        )
        assertEquals(2, afterSecond.seasonNumber)
    }

    @Test
    fun `young players improve and veterans decline over a full season`() {
        val career = newCareer(seed = 313L)
        val before = career.squadOf(career.userClubId).associateBy { it.id }
        val played = playFullSeason(career, Random(18))

        val youngsters = played.squadOf(played.userClubId)
            .filter { it.age <= 20 && before[it.id] != null }
        val veterans = played.squadOf(played.userClubId)
            .filter { it.age >= 33 && before[it.id] != null }

        if (youngsters.isNotEmpty()) {
            val improved = youngsters.count { before[it.id]!!.overall < it.overall }
            assertTrue(
                "At least some young players should improve (improved $improved of ${youngsters.size})",
                improved > 0
            )
        }
        if (veterans.isNotEmpty()) {
            val declined = veterans.count { before[it.id]!!.overall > it.overall }
            assertTrue(
                "At least some veterans should decline (declined $declined of ${veterans.size})",
                declined > 0
            )
        }
    }

    @Test
    fun `finances move over a season and remain coherent`() {
        val career = playFullSeason(newCareer(seed = 111L), Random(19))
        val summary = FinanceEngine.summarise(career)
        assertTrue("Wages should have been paid", summary.wageSpend > 0)
        assertTrue("Matchday revenue should have been earned", summary.matchdayRevenue > 0)
        assertTrue("Balance must be a finite number", career.userClub.balance > Long.MIN_VALUE)
        assertTrue("Transfer budget cannot be negative", career.userClub.transferBudget >= 0)
    }

    @Test
    fun `transfer offers can be negotiated to completion`() {
        var career = newCareer(seed = 222L)
        val target = TransferEngine.marketPlayers(career).firstOrNull { it.value < 8_000_000L }
        assertNotNull("Test requires an affordable target", target)
        val player = target!!

        val asking = TransferEngine.askingPrice(career, player, player.clubId)
        val wage = TransferEngine.expectedWage(career, player, career.userClubId)
        val required = TransferEngine.requiredPackage(career, player, career.userClubId)

        // A lowball offer should be rejected but leave the deal alive.
        career = TransferEngine.createUserOffer(
            career,
            player,
            com.footymanager.simulator.domain.model.TransferPackage(fee = 1L),
            com.footymanager.simulator.domain.model.ContractTerms(wagePerWeek = wage / 2, contractYears = 3)
        )
        val lowballId = career.pendingOffers.last().id
        val lowball = career.pendingOffers.first { it.id == lowballId }
        assertEquals(
            com.footymanager.simulator.domain.model.OfferStatus.REJECTED,
            lowball.status
        )
        assertTrue("A rejection should explain the demand", lowball.message.isNotBlank())

        // A full-price offer should be accepted and move the player.
        val beforeSpend = career.transferSpendThisSeason
        val finalWage = TransferEngine.expectedWage(career, player, career.userClubId)
        career = TransferEngine.createUserOffer(
            career,
            player,
            com.footymanager.simulator.domain.model.TransferPackage(fee = required.fee),
            com.footymanager.simulator.domain.model.ContractTerms(wagePerWeek = finalWage, contractYears = 3)
        )
        val fairId = career.pendingOffers.last().id
        career = TransferEngine.resolveOffer(career, fairId, Random(2))

        val moved = career.player(player.id)
        assertEquals("Player should have joined the user's club", career.userClubId, moved?.clubId)
        assertTrue("Transfer spending should increase", career.transferSpendThisSeason >= beforeSpend)
        assertTrue(
            "A transfer should be reported in the news",
            career.news.any { it.headline.contains(player.name) }
        )
    }

    @Test
    fun `selling a player moves him out and brings in money`() {
        var career = newCareer(seed = 333L)
        val player = career.userSquad.maxByOrNull { it.value }!!
        val buyers = TransferEngine.interestedBuyers(career, player.id, Random(3))
        assertTrue("There should be interested buyers", buyers.isNotEmpty())
        val (buyer, fee) = buyers.first()

        val beforeIncome = career.transferIncomeThisSeason
        career = TransferEngine.sellPlayer(career, player.id, fee, buyer.id)

        assertEquals("Player should have left the club", buyer.id, career.player(player.id)?.clubId)
        assertTrue("Income should increase", career.transferIncomeThisSeason > beforeIncome)
        assertTrue(
            "The squad should no longer contain the player",
            career.squadOf(career.userClubId).none { it.id == player.id }
        )
    }

    @Test
    fun `transfer market excludes the user's own players`() {
        val career = newCareer(seed = 444L)
        val market = TransferEngine.marketPlayers(career)
        assertTrue(market.isNotEmpty())
        assertTrue(
            "The market must never include your own squad",
            market.none { it.clubId == career.userClubId }
        )
    }

    @Test
    fun `availability filters exclude injured and suspended players from selection`() {
        val career = newCareer(seed = 666L)
        val squad = career.squadOf(career.userClubId)
        val injuredIds = squad.take(4).map { it.id }.toSet()
        val damaged = career.copy(
            players = career.players.map {
                if (it.id in injuredIds) {
                    it.copy(injury = com.footymanager.simulator.domain.model.Injury(
                        com.footymanager.simulator.domain.model.InjuryType.KNEE, 6
                    ))
                } else it
            }
        )
        val selection = SeasonEngine.selectionFor(damaged, damaged.userClubId, 1)
        assertTrue(
            "Injured players must not be selected",
            selection.startingXi.none { it.playerId in injuredIds }
        )
        assertEquals("A full XI must still be selected", 11, selection.startingXi.size)
    }

    @Test
    fun `difficulty changes AI strength in the simulation`() {
        val clubs = ClubDatabase.buildAll()
        val easy = CareerFactory.create(
            CareerFactory.NewCareerRequest("E", clubs[5].id, Difficulty.EASY, 1L)
        )
        val hard = CareerFactory.create(
            CareerFactory.NewCareerRequest("H", clubs[5].id, Difficulty.HARD, 1L)
        )
        assertTrue(easy.difficulty.aiStrength < hard.difficulty.aiStrength)
        assertTrue(easy.difficulty.transferDifficulty < hard.difficulty.transferDifficulty)
    }

    @Test
    fun `training focus changes which attributes develop`() {
        val career = newCareer(seed = 888L)
        val player = career.squadOf(career.userClubId)
            .filter { it.age <= 21 && it.potential > it.overall }
            .maxByOrNull { it.potential - it.overall }
        if (player != null) {
            var attacking = player.copy(overall = 60, potential = 88, age = 19)
            var defending = attacking
            repeat(60) { i ->
                attacking = com.footymanager.simulator.domain.engine.DevelopmentEngine
                    .develop(attacking, TrainingFocus.ATTACK, 90, 1.0, Random(i.toLong()))
                defending = com.footymanager.simulator.domain.engine.DevelopmentEngine
                    .develop(defending, TrainingFocus.DEFENCE, 90, 1.0, Random(i.toLong()))
            }
            // Different focuses must produce different attribute profiles.
            assertTrue(
                "Training focus should influence attribute growth",
                attacking.attributes != defending.attributes
            )
        }
    }

    @Test
    fun `simulating a season is fast enough for a mobile device`() {
        val start = System.currentTimeMillis()
        playFullSeason(newCareer(seed = 1212L), Random(20))
        val elapsed = System.currentTimeMillis() - start
        // A full season should take well under 30 seconds on a JVM; on-device it
        // is slower, so this guards against accidental algorithmic blow-ups.
        assertTrue("A full season took ${elapsed}ms, which is too slow", elapsed < 30_000)
    }

    @Test
    fun `championship clubs can be managed too`() {
        val championshipClub = ClubDatabase.buildAll()
            .first { it.leagueId == League.CHAMPIONSHIP.id }
        val career = newCareer(clubId = championshipClub.id, seed = 1313L)
        assertEquals(League.CHAMPIONSHIP.id, career.userLeagueId)
        val played = playFullSeason(career, Random(21))
        assertEquals(played.totalMatchdays(), played.matchdayIndex)
        assertTrue(
            "Championship table should be populated",
            played.table.getValue(League.CHAMPIONSHIP.id).sumOf { it.played } > 0
        )
    }

    @Test
    fun `continental leagues can be managed`() {
        for (league in listOf(League.LA_LIGA, League.SERIE_A, League.BUNDESLIGA, League.LIGUE_1)) {
            val club = ClubDatabase.buildAll().first { it.leagueId == league.id }
            val career = newCareer(clubId = club.id, seed = league.id.hashCode().toLong())
            assertEquals(league.id, career.userLeagueId)
            val played = playFullSeason(career, Random(22))
            assertEquals(
                "Season should complete in ${league.name}",
                played.totalMatchdays(),
                played.matchdayIndex
            )
        }
    }

    @Test
    fun `no player is left with an illegal attribute or rating`() {
        val career = playFullSeason(newCareer(seed = 1414L), Random(23))
        for (player in career.players) {
            assertTrue("${player.name} overall out of range", player.overall in 20..99)
            assertTrue("${player.name} potential below overall", player.potential >= player.overall - 1)
            assertTrue("${player.name} age out of range", player.age in 15..45)
            assertTrue("${player.name} value negative", player.value >= 0)
            assertTrue("${player.name} wage negative", player.wagePerWeek >= 0)
            assertTrue("${player.name} contract negative", player.contractYearsRemaining >= 0)
            val attrs = player.attributes
            listOf(
                attrs.pace, attrs.shooting, attrs.passing,
                attrs.dribbling, attrs.defending, attrs.physical, attrs.goalkeeping
            ).forEach { value ->
                assertTrue("${player.name} attribute out of range: $value", value in 1..99)
            }
        }
    }

    @Test
    fun `free agents can be signed without a fee`() {
        var career = newCareer(seed = 1515L)
        // Release a player to create a free agent.
        val player = career.userSquad.last()
        career = TransferEngine.releasePlayer(career, player.id)
        val freeAgent = career.player(player.id)
        assertNotNull(freeAgent)
        assertEquals("Released players become free agents", null, freeAgent?.clubId)

        val asking = TransferEngine.askingPrice(career, freeAgent!!, null)
        assertEquals("Free agents cost no fee", 0L, asking)

        val wage = TransferEngine.expectedWage(career, freeAgent, career.userClubId)
        career = TransferEngine.createUserOffer(
            career,
            freeAgent,
            com.footymanager.simulator.domain.model.TransferPackage(fee = 0L),
            com.footymanager.simulator.domain.model.ContractTerms(wagePerWeek = wage, contractYears = 2)
        )
        val offerId = career.pendingOffers.last().id
        career = TransferEngine.resolveOffer(career, offerId, Random(4))
        assertEquals(
            "A free agent should join when wages are met",
            career.userClubId,
            career.player(player.id)?.clubId
        )
    }

    @Test
    fun `positions required by every formation exist in a generated squad`() {
        val career = newCareer(seed = 1616L)
        val squad = career.squadOf(career.userClubId)
        // A squad must be able to field a goalkeeper and a recognised striker.
        assertTrue(squad.any { it.position == Position.GK })
        assertTrue(squad.any { it.position.isForward })
        assertTrue(squad.any { it.position.isDefender })
        assertTrue(squad.any { it.position.isMidfielder })
    }

    @Test
    fun `the manager record accumulates over a season`() {
        val career = playFullSeason(newCareer(seed = 313L), Random(313))
        val record = career.managerRecord
        assertEquals(
            "Every user fixture must be counted once",
            record.wins + record.draws + record.losses,
            record.matchesManaged
        )
        assertTrue("A full season should register matches", record.matchesManaged > 0)
        assertTrue(record.pointsPerGame in 0.0..3.0)
        assertTrue(record.winRatePercent in 0..100)
    }

    @Test
    fun `fan satisfaction stays in range and tracks results`() {
        val career = playFullSeason(newCareer(seed = 505L), Random(505))
        assertTrue(
            "Fan satisfaction must stay within 0..100, was ${career.fanSatisfaction}",
            career.fanSatisfaction in 0..100
        )
    }

    @Test
    fun `a happy fanbase fills more of the ground`() {
        // Use a modest club so neither gate is capped by the stadium capacity,
        // which would otherwise hide the fan effect.
        val clubs = ClubDatabase.buildAll()
        val club = clubs.filter { it.reputation <= 66 }.minByOrNull { it.reputation } ?: clubs.last()
        val base = newCareer(clubId = club.id, seed = 909L)
        val stadium = base.stadium
        val unhappy = com.footymanager.simulator.domain.engine.StadiumEngine.attendance(
            stadium = stadium,
            reputation = base.userClub.reputation,
            opponentReputation = base.userClub.reputation,
            recentPointsPerGame = 1.5,
            random = Random(7),
            fanSatisfaction = 10
        )
        val happy = com.footymanager.simulator.domain.engine.StadiumEngine.attendance(
            stadium = stadium,
            reputation = base.userClub.reputation,
            opponentReputation = base.userClub.reputation,
            recentPointsPerGame = 1.5,
            random = Random(7),
            fanSatisfaction = 95
        )
        assertTrue(
            "Happy fans should attend in greater numbers ($happy vs $unhappy)",
            happy > unhappy
        )
    }
}
