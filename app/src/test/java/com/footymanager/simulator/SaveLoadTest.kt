package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.SaveCodec
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.TrainingFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Save/load round-trip tests. A management game is worthless if a career cannot
 * be restored exactly, so every meaningful piece of state is compared.
 */
class SaveLoadTest {

    private fun career() = CareerFactory.create(
        CareerFactory.NewCareerRequest(
            managerName = "Alex Reid",
            clubId = ClubDatabase.buildAll()[3].id,
            difficulty = Difficulty.NORMAL,
            seed = 987654L
        )
    )

    @Test
    fun `a brand new career survives a save and load round trip`() {
        val original = career()
        val restored = SaveCodec.decode(SaveCodec.encode(original))

        assertNotNull("Save should decode", restored)
        restored!!
        assertEquals(original.managerName, restored.managerName)
        assertEquals(original.userClubId, restored.userClubId)
        assertEquals(original.season, restored.season)
        assertEquals(original.seasonNumber, restored.seasonNumber)
        assertEquals(original.matchdayIndex, restored.matchdayIndex)
        assertEquals(original.difficulty, restored.difficulty)
        assertEquals(original.clubs.size, restored.clubs.size)
        assertEquals(original.players.size, restored.players.size)
        assertEquals(original.fixtures.size, restored.fixtures.size)
        assertEquals(original.table, restored.table)
    }

    @Test
    fun `every player survives the round trip exactly`() {
        val original = career()
        val restored = SaveCodec.decode(SaveCodec.encode(original))!!
        val byId = restored.players.associateBy { it.id }
        assertEquals(original.players.size, byId.size)
        for (player in original.players) {
            val other = byId[player.id]
            assertNotNull("Player ${player.id} should survive", other)
            assertEquals("Player ${player.name} differs", player, other)
        }
    }

    @Test
    fun `tactics and selection survive the round trip`() {
        var original = career()
        original = original.copy(
            tactics = original.tactics.copy(
                formationId = com.footymanager.simulator.domain.model.Formation.F352.id,
                mentality = Mentality.VERY_ATTACKING,
                style = PlayStyle.HIGH_PRESS,
                tempo = com.footymanager.simulator.domain.model.Tempo.FAST,
                defensiveLine = com.footymanager.simulator.domain.model.DefensiveLine.HIGH
            ),
            trainingFocus = TrainingFocus.POSSESSION
        )

        val restored = SaveCodec.decode(SaveCodec.encode(original))!!
        assertEquals(original.tactics, restored.tactics)
        assertEquals("3-5-2", restored.tactics.formation.id)
        assertEquals(TrainingFocus.POSSESSION, restored.trainingFocus)
        assertEquals(original.selection, restored.selection)
    }

    @Test
    fun `a career mid-season survives a round trip`() {
        var original = career()
        val random = Random(31)
        repeat(7) {
            val match = original.nextMatch() ?: return@repeat
            original = SeasonEngine.simulateOtherFixtures(original, random)
            val (after, _) = SeasonEngine.simulateFixture(original, match, random, userMatch = true)
            original = SeasonEngine.advanceWeek(after, random)
        }
        assertTrue("Test needs played matches", original.results.isNotEmpty())

        val restored = SaveCodec.decode(SaveCodec.encode(original))!!
        assertEquals(original.matchdayIndex, restored.matchdayIndex)
        assertEquals(original.results.size, restored.results.size)
        assertEquals(original.table, restored.table)
        assertEquals(original.news.size, restored.news.size)
        assertEquals(original.ledger.size, restored.ledger.size)
        assertEquals(original.userClub.balance, restored.userClub.balance)
        assertEquals(original.userClub.transferBudget, restored.userClub.transferBudget)
    }

    @Test
    fun `player development, injuries and suspensions survive a round trip`() {
        val original = career()
        // Inject a variety of states to make sure they serialize.
        val mutated = original.copy(
            players = original.players.mapIndexed { index, player ->
                when (index % 4) {
                    0 -> player.copy(
                        injury = com.footymanager.simulator.domain.model.Injury(
                            com.footymanager.simulator.domain.model.InjuryType.HAMSTRING, 4
                        )
                    )
                    1 -> player.copy(suspensionWeeks = 2, yellowCardAccumulation = 4)
                    2 -> player.copy(developmentPool = 0.77, form = 8.4, moraleScore = 4.6)
                    else -> player.copy(contractYearsRemaining = 0, signedThisWindow = true)
                }
            }
        )

        val restored = SaveCodec.decode(SaveCodec.encode(mutated))!!
        for (player in mutated.players) {
            assertEquals(player, restored.players.first { it.id == player.id })
        }
    }

    @Test
    fun `league standings and fixtures survive a round trip`() {
        var original = career()
        val random = Random(32)
        repeat(12) {
            val match = original.nextMatch() ?: return@repeat
            original = SeasonEngine.simulateOtherFixtures(original, random)
            val (after, _) = SeasonEngine.simulateFixture(original, match, random, userMatch = true)
            original = SeasonEngine.advanceWeek(after, random)
        }

        val restored = SaveCodec.decode(SaveCodec.encode(original))!!
        assertEquals(original.fixtures, restored.fixtures)
        assertEquals(original.results, restored.results)
        for (league in original.table.keys) {
            assertEquals(
                "Table for $league differs",
                original.sortedTable(league),
                restored.sortedTable(league)
            )
        }
    }

    @Test
    fun `a corrupted payload decodes to null instead of crashing`() {
        assertNull(SaveCodec.decode(""))
        assertNull(SaveCodec.decode("not json at all"))
        assertNull(SaveCodec.decode("""{"managerName": 123}"""))
        assertNull(SaveCodec.decode("{ broken"))
    }

    @Test
    fun `an unknown field added by a future version is ignored`() {
        val original = career()
        val payload = SaveCodec.encode(original)
        // Simulate a newer version writing an extra field at the top level.
        val withExtra = payload.dropLast(1) + ""","futureField":{"x":1}}"""
        val restored = SaveCodec.decode(withExtra)
        assertNotNull("Unknown fields must not break loading", restored)
        assertEquals(original.userClubId, restored!!.userClubId)
    }

    @Test
    fun `encoding is stable so the same career produces the same payload`() {
        val original = career()
        val first = SaveCodec.encode(original)
        val second = SaveCodec.encode(original)
        assertEquals("Encoding must be deterministic", first, second)

        // And decoding then re-encoding must be a fixed point.
        val restored = SaveCodec.decode(first)!!
        assertEquals("Re-encoding a decoded save must be identical", first, SaveCodec.encode(restored))
    }

    @Test
    fun `a season end state with a summary survives a round trip`() {
        var original = career()
        val random = Random(33)
        repeat(original.totalMatchdays()) {
            val match = original.nextMatch() ?: return@repeat
            original = SeasonEngine.simulateOtherFixtures(original, random)
            val (after, _) = SeasonEngine.simulateFixture(original, match, random, userMatch = true)
            original = SeasonEngine.advanceWeek(after, random)
        }
        val ended = SeasonEngine.endSeason(
            original.copy(phase = com.footymanager.simulator.domain.model.GamePhase.SEASON_ENDED),
            random
        )

        val restored = SaveCodec.decode(SaveCodec.encode(ended))!!
        assertEquals(ended.season, restored.season)
        assertEquals(ended.seasonNumber, restored.seasonNumber)
        assertEquals(ended.lastSeasonSummary, restored.lastSeasonSummary)
        assertEquals(ended.awards, restored.awards)
        assertEquals(ended.matchdayIndex, restored.matchdayIndex)
    }
}
