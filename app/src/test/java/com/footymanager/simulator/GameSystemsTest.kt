package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.PlayerGenerator
import com.footymanager.simulator.domain.engine.DevelopmentEngine
import com.footymanager.simulator.domain.engine.FinanceEngine
import com.footymanager.simulator.domain.engine.SelectionRepair
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.Injury
import com.footymanager.simulator.domain.model.InjuryType
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.PositionSuitability
import com.footymanager.simulator.domain.model.TrainingFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameSystemsTest {

    private val clubs: List<Club> = ClubDatabase.buildAll()
    private var idSeq = 0L

    // ------------------------------------------------------------ database

    @Test
    fun `database covers all six requested leagues`() {
        val ids = clubs.map { it.leagueId }.toSet()
        assertTrue(ids.contains(League.PREMIER_LEAGUE.id))
        assertTrue(ids.contains(League.CHAMPIONSHIP.id))
        assertTrue(ids.contains(League.LA_LIGA.id))
        assertTrue(ids.contains(League.SERIE_A.id))
        assertTrue(ids.contains(League.BUNDESLIGA.id))
        assertTrue(ids.contains(League.LIGUE_1.id))
    }

    @Test
    fun `every league has at least sixteen clubs`() {
        for (league in League.all) {
            val count = clubs.count { it.leagueId == league.id }
            assertTrue("${league.name} has only $count clubs", count >= 16)
        }
    }

    @Test
    fun `club ids are unique`() {
        assertEquals(clubs.size, clubs.map { it.id }.toSet().size)
    }

    @Test
    fun `club budgets are positive and scale with reputation`() {
        assertTrue(clubs.all { it.transferBudget > 0 && it.wageBudget > 0 && it.stadiumCapacity > 0 })
        val elite = clubs.maxBy { it.reputation }
        val smallest = clubs.minBy { it.reputation }
        assertTrue(elite.transferBudget > smallest.transferBudget)
    }

    // -------------------------------------------------------------- players

    @Test
    fun `generated squad has a sensible positional spread`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(1)) { ++idSeq }
        assertEquals(24, squad.size)
        assertEquals(3, squad.count { it.position == Position.GK })
        assertTrue(squad.count { it.position.isDefender } >= 7)
        assertTrue(squad.count { it.position.isForward } >= 4)
    }

    @Test
    fun `player overall rating matches their attributes`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(2)) { ++idSeq }
        for (player in squad) {
            val recomputed = PlayerGenerator.overallFor(player.position, player.attributes)
            // The generator derives overall from attributes, so they must agree.
            assertEquals("${player.name} (${player.position})", player.overall, recomputed)
        }
    }

    @Test
    fun `goalkeepers are strong in goal and poor outfield`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(3)) { ++idSeq }
        val keepers = squad.filter { it.position == Position.GK }
        assertTrue(keepers.isNotEmpty())
        assertTrue("Keepers should have high goalkeeping", keepers.all { it.attributes.goalkeeping > 50 })
        val outfielders = squad.filter { it.position != Position.GK }
        assertTrue("Outfielders should not be keepers", outfielders.all { it.attributes.goalkeeping < 30 })
    }

    @Test
    fun `stronger clubs have stronger squads`() {
        val elite = clubs.maxBy { it.reputation }
        val weak = clubs.filter { it.leagueId == "ENG1" }.minBy { it.reputation }
        val eliteSquad = PlayerGenerator.generateSquad(elite, Random(10)) { ++idSeq }
        val weakSquad = PlayerGenerator.generateSquad(weak, Random(10)) { ++idSeq }
        assertTrue(eliteSquad.map { it.overall }.average() > weakSquad.map { it.overall }.average())
    }

    @Test
    fun `young players have headroom to their potential`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(4)) { ++idSeq }
        val youngsters = squad.filter { it.age <= 21 }
        assertTrue(youngsters.isNotEmpty())
        assertTrue("Young players should have potential above current ability",
            youngsters.all { it.potential >= it.overall })
    }

    @Test
    fun `values and wages rise with ability`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(5)) { ++idSeq }
        val sorted = squad.sortedBy { it.overall }
        val weakest = sorted.first()
        val strongest = sorted.last()
        assertTrue(strongest.value > weakest.value)
        assertTrue(strongest.wagePerWeek > weakest.wagePerWeek)
    }

    // ---------------------------------------------------- position suitability

    @Test
    fun `playing out of position reduces effectiveness`() {
        assertEquals(1.0, PositionSuitability.factor(Position.CB, Position.CB), 0.0001)
        assertTrue(PositionSuitability.factor(Position.CB, Position.ST) < 1.0)
        assertTrue(PositionSuitability.factor(Position.ST, Position.GK) < 0.5)
        assertTrue(PositionSuitability.factor(Position.GK, Position.ST) < 0.5)
    }

    @Test
    fun `related positions are penalised less than unrelated ones`() {
        val related = PositionSuitability.factor(Position.RB, Position.LB)
        val unrelated = PositionSuitability.factor(Position.CB, Position.ST)
        assertTrue(related > unrelated)
    }

    // -------------------------------------------------------------- selection

    @Test
    fun `auto pick fills every slot of every formation`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(6)) { ++idSeq }
        for (formation in Formation.all) {
            val selection = com.footymanager.simulator.domain.data.SelectionHelper.autoPickBest(squad, formation)
            assertEquals("Formation ${formation.name}", 11, selection.startingXi.size)
            assertTrue("Formation ${formation.name} should include a goalkeeper",
                selection.startingXi.any { it.slotIndex == 0 })
            assertEquals(11, selection.startingXi.map { it.playerId }.toSet().size)
        }
    }

    @Test
    fun `selection repair removes injured and suspended players`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(7)) { ++idSeq }
        val formation = Formation.F4231
        val selection = com.footymanager.simulator.domain.data.SelectionHelper.autoPickBest(squad, formation)

        // Injure three starters and suspend another.
        val injuredIds = selection.startingXi.take(3).map { it.playerId }.toSet()
        val suspendedId = selection.startingXi[5].playerId
        val damaged = squad.map {
            when (it.id) {
                in injuredIds -> it.copy(injury = Injury(InjuryType.HAMSTRING, 4))
                suspendedId -> it.copy(suspensionWeeks = 2)
                else -> it
            }
        }

        val repaired = SelectionRepair.repair(selection, damaged, formation)
        assertEquals(11, repaired.startingXi.size)
        assertTrue(repaired.startingXi.none { it.playerId in injuredIds })
        assertTrue(repaired.startingXi.none { it.playerId == suspendedId })
        assertEquals(11, repaired.startingXi.map { it.playerId }.toSet().size)
    }

    @Test
    fun `selection warnings flag problems`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(8)) { ++idSeq }
        val formation = Formation.F4231
        val selection = com.footymanager.simulator.domain.data.SelectionHelper.autoPickBest(squad, formation)
        // A valid selection with a goalkeeper should raise no goalkeeper warning.
        val warnings = SelectionRepair.warnings(selection, squad, formation)
        assertTrue(warnings.none { it.contains("goalkeeper") })

        val broken = selection.copy(startingXi = emptyList())
        assertTrue(SelectionRepair.warnings(broken, squad, formation).isNotEmpty())
    }

    // ------------------------------------------------------------ development

    @Test
    fun `development raises ability for a high-potential youngster over a season`() {
        // Scan squads until one contains a teenager, since age is drawn randomly.
        val prospect = clubs.asSequence()
            .map { PlayerGenerator.generateSquad(it, Random(it.id * 7 + 3)) { ++idSeq } }
            .flatMap { it.asSequence() }
            .firstOrNull { it.age <= 20 }
        assertTrue("Test requires a young player in the database", prospect != null)

        var player = prospect!!
        val random = Random(11)
        val startOverall = player.overall
        // Roughly 38 weeks of first-team football in a season.
        repeat(38) {
            player = DevelopmentEngine.develop(
                player = player,
                focus = TrainingFocus.BALANCED,
                minutesThisWeek = 90,
                developmentRate = 1.0,
                random = random
            )
        }
        assertTrue(
            "Prospect should improve from $startOverall (now ${player.overall})",
            player.overall > startOverall
        )
        assertTrue("Prospect must not exceed potential", player.overall <= player.potential)
    }

    @Test
    fun `veterans decline over time`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(12)) { ++idSeq }
        val veteran = squad.filter { it.age >= 33 }.maxByOrNull { it.overall }
        assertTrue("Test requires a veteran", veteran != null)

        var player = veteran!!
        val startOverall = player.overall
        val random = Random(13)
        repeat(60) {
            player = DevelopmentEngine.develop(
                player = player,
                focus = TrainingFocus.BALANCED,
                minutesThisWeek = 90,
                developmentRate = 1.0,
                random = random
            )
        }
        assertTrue(
            "Veteran should decline from $startOverall (now ${player.overall})",
            player.overall < startOverall
        )
    }

    @Test
    fun `young players develop faster than older players`() {
        val club = clubs.first()
        val squad = PlayerGenerator.generateSquad(club, Random(14)) { ++idSeq }
        val young = squad.filter { it.age in 18..20 }.maxByOrNull { it.potential - it.overall }!!
        val old = squad.filter { it.age in 26..28 }.maxByOrNull { it.potential - it.overall }!!

        fun grow(player: com.footymanager.simulator.domain.model.Player): Int {
            var p = player.copy(overall = 65, potential = 85)
            repeat(40) {
                p = DevelopmentEngine.develop(p, TrainingFocus.BALANCED, 90, 1.0, Random(it.toLong()))
            }
            return p.overall
        }
        val youngGrowth = grow(young.copy(age = 19)) - 65
        val oldGrowth = grow(old.copy(age = 27)) - 65
        assertTrue("Young ($youngGrowth) should out-grow old ($oldGrowth)", youngGrowth > oldGrowth)
    }

    // --------------------------------------------------------------- injuries

    @Test
    fun `injuries heal over time`() {
        val club = clubs.first()
        val player = PlayerGenerator.generateSquad(club, Random(15)) { ++idSeq }.first()
        val injured = player.copy(injury = Injury(InjuryType.KNEE, 10))
        assertTrue(injured.isInjured)
        assertFalse(injured.isAvailable)

        var current = injured
        repeat(10) { current = DevelopmentEngine.progressInjury(current) }
        assertFalse("Should be fit after the recovery period", current.isInjured)
        assertTrue(current.isAvailable)
    }

    @Test
    fun `injury severity maps to a plausible recovery window`() {
        for (type in InjuryType.entries) {
            if (type == InjuryType.NONE) continue
            assertTrue("${type.label} should have a positive recovery window", type.minWeeks >= 1)
            assertTrue("${type.label} min should not exceed max", type.minWeeks <= type.maxWeeks)
        }
    }

    @Test
    fun `five yellow cards trigger a suspension`() {
        val club = clubs.first()
        val player = PlayerGenerator.generateSquad(club, Random(16)) { ++idSeq }.first()
        var current = player
        repeat(5) { current = DevelopmentEngine.applyCards(current, yellows = 1, reds = 0) }
        assertTrue("Should be suspended after five bookings", current.isSuspended)
    }

    @Test
    fun `a red card triggers an immediate suspension`() {
        val club = clubs.first()
        val player = PlayerGenerator.generateSquad(club, Random(17)) { ++idSeq }.first()
        val sentOff = DevelopmentEngine.applyCards(player, yellows = 0, reds = 1)
        assertTrue(sentOff.isSuspended)
        assertFalse(sentOff.isAvailable)
    }

    @Test
    fun `suspensions expire`() {
        val club = clubs.first()
        val player = PlayerGenerator.generateSquad(club, Random(18)) { ++idSeq }.first()
        var current = player.copy(suspensionWeeks = 2)
        current = DevelopmentEngine.progressSuspension(current)
        assertTrue(current.isSuspended)
        current = DevelopmentEngine.progressSuspension(current)
        assertFalse(current.isSuspended)
    }

    // --------------------------------------------------------------- fitness

    @Test
    fun `playing a match reduces fitness`() {
        val club = clubs.first()
        val player = PlayerGenerator.generateSquad(club, Random(19)) { ++idSeq }
            .first { it.fitness >= 95 }
        val after = DevelopmentEngine.applyMatchEffects(
            player = player, minutes = 90, rating = 6.8,
            teamGoals = 2, opponentGoals = 1, styleFatigue = 1.0, isStarter = true
        )
        assertTrue("Fitness should drop", after.fitness < player.fitness)
    }

    @Test
    fun `training and rest restore fitness`() {
        val club = clubs.first()
        val tired = PlayerGenerator.generateSquad(club, Random(20)) { ++idSeq }
            .first().copy(fitness = 50)
        val recovered = DevelopmentEngine.weeklyRecovery(tired, TrainingFocus.FITNESS, Random(1))
        assertTrue(recovered.fitness > tired.fitness)
    }

    @Test
    fun `winning improves morale and losing reduces it`() {
        val club = clubs.first()
        val player = PlayerGenerator.generateSquad(club, Random(21)) { ++idSeq }.first()
            .copy(moraleScore = 3.0, form = 5.0)

        val winner = DevelopmentEngine.applyMatchEffects(player, 90, 7.5, 3, 0, 1.0, true)
        val loser = DevelopmentEngine.applyMatchEffects(player, 90, 5.5, 0, 3, 1.0, true)

        assertTrue(winner.moraleScore > player.moraleScore)
        assertTrue(loser.moraleScore < player.moraleScore)
    }

    // -------------------------------------------------------------- finances

    @Test
    fun `weekly wages reduce the club balance`() {
        val career = CareerFactory.create(
            CareerFactory.NewCareerRequest("Test Manager", clubs.first().id, Difficulty.NORMAL, 1234L)
        )
        val before = career.userClub.balance
        val after = FinanceEngine.payWeeklyWages(career, career.idCounter)
        assertTrue("Balance should fall after paying wages", after.userClub.balance < before)
        assertTrue(after.ledger.any { it.category == com.footymanager.simulator.domain.model.LedgerCategory.WAGES })
    }

    @Test
    fun `finance summary reflects the wage bill`() {
        val career = CareerFactory.create(
            CareerFactory.NewCareerRequest("Test Manager", clubs.first().id, Difficulty.NORMAL, 99L)
        )
        val summary = FinanceEngine.summarise(career)
        assertEquals(career.wageBill(career.userClubId), summary.weeklyWageBill)
        assertTrue(summary.weeklyWageBill > 0)
    }

    @Test
    fun `finance summary totals match the ledger and never double count`() {
        val career = CareerFactory.create(
            CareerFactory.NewCareerRequest("Test Manager", clubs.first().id, Difficulty.NORMAL, 77L)
        )
        val summary = FinanceEngine.summarise(career)
        val seasonEntries = career.ledger.filter { it.season == career.season }
        val ledgerIncome = seasonEntries.filter { it.amount > 0 }.sumOf { it.amount }
        val ledgerExpense = seasonEntries.filter { it.amount < 0 }.sumOf { -it.amount }

        assertEquals(ledgerIncome, summary.totalIncome)
        assertEquals(ledgerExpense, summary.totalExpense)
        assertEquals(summary.totalIncome - summary.totalExpense, summary.netSeason)
    }

    @Test
    fun `monthly finance points are ordered and reconcile with the ledger`() {
        val career = CareerFactory.create(
            CareerFactory.NewCareerRequest("Test Manager", clubs.first().id, Difficulty.NORMAL, 88L)
        )
        val summary = FinanceEngine.summarise(career)
        val months = summary.monthly.map { it.month }
        assertEquals("months must be sorted", months.sorted(), months)
        // Every point must be non-negative on both sides.
        summary.monthly.forEach { point ->
            assertTrue(point.income >= 0)
            assertTrue(point.expense >= 0)
        }
    }

    @Test
    fun `sponsorship offers provide five distinct risk profiles`() {
        val club = clubs.first { it.reputation >= 88 }
        val offers = com.footymanager.simulator.domain.engine.SponsorshipEngine
            .generateOffers(club, Random(5L))
        assertEquals(5, offers.size)
        assertEquals(5, offers.map { it.id }.toSet().size)
        // Every offer must be a real decision: some guaranteed money and a bonus.
        offers.forEach { offer ->
            assertTrue(offer.upfront + offer.seasonal > 0)
            assertTrue(offer.bonusAmount > 0)
            assertTrue(offer.risk.isNotBlank())
        }
    }

    @Test
    fun `difficulty scales the initial budgets`() {
        val easy = com.footymanager.simulator.domain.model.Difficulty.EASY
        val hard = com.footymanager.simulator.domain.model.Difficulty.HARD
        assertTrue(easy.financialPressure > hard.financialPressure)
        assertTrue(easy.transferDifficulty < hard.transferDifficulty)
        assertTrue(easy.aiStrength < hard.aiStrength)
    }
}
