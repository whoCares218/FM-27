package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.Attributes
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Injury
import com.footymanager.simulator.domain.model.InjuryType
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.PreferredFoot
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Generates a complete, internally consistent player database.
 *
 * Every player is produced by:
 *  1. choosing a target ability from the club's reputation and a squad "tier",
 *  2. deriving each attribute from that ability using a position-specific profile,
 *  3. recomputing the overall rating from the attributes with position weights,
 *     so the displayed rating always matches the underlying attributes.
 */
object PlayerGenerator {

    /** The 24-player squad template used for every club. */
    private val squadTemplate: List<Position> = listOf(
        Position.GK, Position.GK, Position.GK,
        Position.RB, Position.RB,
        Position.LB, Position.LB,
        Position.CB, Position.CB, Position.CB, Position.CB,
        Position.CDM, Position.CDM,
        Position.CM, Position.CM, Position.CM,
        Position.CAM, Position.CAM,
        Position.RW, Position.RW,
        Position.LW, Position.LW,
        Position.ST, Position.ST
    )

    /**
     * Attribute offsets relative to a player's base ability, per position.
     * These create recognisable archetypes: fast wingers, physical centre backs,
     * creative tens and pure finishers.
     */
    private fun profile(position: Position): IntArray = when (position) {
        //            pace, shoot, pass, dribble, defend, physical, gk
        Position.GK -> intArrayOf(-20, -46, -10, -26, -30, -6, 4)
        Position.RB -> intArrayOf(5, -10, 0, -2, 3, 0, -60)
        Position.LB -> intArrayOf(5, -10, 0, -2, 3, 0, -60)
        Position.CB -> intArrayOf(-5, -18, -6, -13, 7, 6, -60)
        Position.CDM -> intArrayOf(-4, -11, 3, -3, 6, 5, -60)
        Position.CM -> intArrayOf(-2, -4, 6, 2, -2, 1, -60)
        Position.CAM -> intArrayOf(1, 2, 7, 7, -15, -7, -60)
        Position.RW -> intArrayOf(8, 1, 1, 8, -19, -8, -60)
        Position.LW -> intArrayOf(8, 1, 1, 8, -19, -8, -60)
        Position.ST -> intArrayOf(4, 9, -5, 2, -23, 3, -60)
    }

    /**
     * Weights used to derive the overall rating from attributes. Must sum to 1.0
     * so the resulting rating stays on the same 1..99 scale as the attributes.
     */
    private fun weights(position: Position): DoubleArray = when (position) {
        //                pace, shoot, pass, dribble, defend, physical, gk
        Position.GK -> doubleArrayOf(0.04, 0.01, 0.04, 0.01, 0.05, 0.10, 0.75)
        Position.RB -> doubleArrayOf(0.25, 0.05, 0.15, 0.15, 0.25, 0.15, 0.0)
        Position.LB -> doubleArrayOf(0.25, 0.05, 0.15, 0.15, 0.25, 0.15, 0.0)
        Position.CB -> doubleArrayOf(0.15, 0.05, 0.10, 0.05, 0.40, 0.25, 0.0)
        Position.CDM -> doubleArrayOf(0.10, 0.08, 0.22, 0.12, 0.28, 0.20, 0.0)
        Position.CM -> doubleArrayOf(0.10, 0.10, 0.30, 0.20, 0.15, 0.15, 0.0)
        Position.CAM -> doubleArrayOf(0.14, 0.20, 0.28, 0.26, 0.04, 0.08, 0.0)
        Position.RW -> doubleArrayOf(0.28, 0.18, 0.16, 0.28, 0.03, 0.07, 0.0)
        Position.LW -> doubleArrayOf(0.28, 0.18, 0.16, 0.28, 0.03, 0.07, 0.0)
        Position.ST -> doubleArrayOf(0.20, 0.34, 0.10, 0.18, 0.04, 0.14, 0.0)
    }

    /** Computes the overall rating implied by a set of attributes for a position. */
    fun overallFor(position: Position, attributes: Attributes): Int {
        val w = weights(position)
        val values = doubleArrayOf(
            attributes.pace.toDouble(),
            attributes.shooting.toDouble(),
            attributes.passing.toDouble(),
            attributes.dribbling.toDouble(),
            attributes.defending.toDouble(),
            attributes.physical.toDouble(),
            attributes.goalkeeping.toDouble()
        )
        var sum = 0.0
        for (i in w.indices) sum += w[i] * values[i]
        return sum.roundToInt().coerceIn(1, 99)
    }

    /** Base squad ability implied by a club's reputation. */
    private fun squadBaseAbility(reputation: Int): Double = 46.0 + reputation * 0.40

    /**
     * Quality tiers applied across a squad so clubs have genuine stars, reliable
     * starters, rotation options and prospects rather than 24 identical players.
     */
    private val tierOffsets = doubleArrayOf(6.0, 6.0, 3.0, 3.0, 3.0, 1.0, 1.0, 1.0, 0.0, 0.0, -1.0, -1.0, -2.0, -3.0, -4.0, -5.0, -6.0, -6.0, -7.0, -8.0, -9.0, -10.0, -3.0, -6.0)

    fun generateSquad(
        club: Club,
        random: Random,
        difficulty: Double = 1.0,
        idProvider: () -> Long
    ): List<Player> {
        val base = squadBaseAbility(club.reputation) * difficulty
        val offsets = tierOffsets.copyOf()
        // Shuffle the tiers so the stars are not always the first-listed players,
        // but keep the goalkeeper slots away from the very weakest tiers.
        val shuffled = offsets.toMutableList()
        shuffled.shuffle(random)
        val gkTiers = shuffled.filter { it >= -4.0 }.take(3).toMutableList()
        while (gkTiers.size < 3) gkTiers.add(-5.0)

        var gkIndex = 0
        return squadTemplate.map { position ->
            val offset = if (position == Position.GK) {
                gkTiers[gkIndex++]
            } else {
                shuffled.removeAt(0)
            }
            generatePlayer(
                id = idProvider(),
                club = club,
                position = position,
                baseAbility = base + offset,
                random = random
            )
        }
    }

    fun generatePlayer(
        id: Long,
        club: Club,
        position: Position,
        baseAbility: Double,
        random: Random
    ): Player {
        val age = drawAge(random)
        val profile = profile(position)

        // Younger players are further from their ceiling; veterans are past it.
        val ageAbilityDelta = when {
            age <= 18 -> -7.0
            age <= 20 -> -4.5
            age <= 22 -> -2.5
            age <= 24 -> -0.8
            age <= 27 -> 0.6
            age <= 29 -> 0.0
            age <= 31 -> -1.8
            age <= 33 -> -3.6
            else -> -5.5
        }
        val ability = (baseAbility + ageAbilityDelta).coerceIn(30.0, 94.0)

        val attrs = buildAttributes(ability, profile, position, random)
        val overall = overallFor(position, attrs)

        val potential = computePotential(overall, age, random)

        val foot = when {
            random.nextDouble() < 0.74 -> PreferredFoot.RIGHT
            random.nextDouble() < 0.86 -> PreferredFoot.LEFT
            else -> PreferredFoot.BOTH
        }

        val contractYears = 1 + random.nextInt(4)

        val base = Player(
            id = id,
            name = generateName(club.country, random),
            age = age,
            nationality = NameData.nationalityForLeague(club.country, random),
            position = position,
            attributes = attrs,
            potential = potential,
            overall = overall,
            preferredFoot = foot,
            fitness = 88 + random.nextInt(13),
            moraleScore = 3.0 + random.nextDouble() * 1.4 - 0.4,
            form = 4.0 + random.nextDouble() * 2.6,
            injury = Injury(InjuryType.NONE, 0),
            contractYearsRemaining = contractYears,
            wagePerWeek = 0,
            value = 0,
            clubId = club.id
        )
        return base.copy(
            wagePerWeek = base.recomputeWage(),
            value = base.recomputeValue()
        )
    }

    private fun buildAttributes(
        ability: Double,
        profile: IntArray,
        position: Position,
        random: Random
    ): Attributes {
        fun value(index: Int): Int {
            val variance = random.nextDouble(-5.5, 5.5)
            val raw = ability + profile[index] + variance
            // Goalkeeping is only meaningful for keepers.
            if (index == 6 && position != Position.GK) {
                return (raw + random.nextInt(1, 10)).roundToInt().coerceIn(1, 25)
            }
            return raw.roundToInt().coerceIn(1, 99)
        }
        return Attributes(
            pace = value(0),
            shooting = value(1),
            passing = value(2),
            dribbling = value(3),
            defending = value(4),
            physical = value(5),
            goalkeeping = value(6)
        )
    }

    private fun computePotential(overall: Int, age: Int, random: Random): Int {
        val headroom = when {
            age <= 17 -> random.nextInt(12, 22)
            age <= 19 -> random.nextInt(9, 18)
            age <= 21 -> random.nextInt(6, 13)
            age <= 23 -> random.nextInt(3, 9)
            age <= 25 -> random.nextInt(1, 5)
            age <= 28 -> random.nextInt(0, 2)
            else -> 0
        }
        return (overall + headroom).coerceAtMost(96)
    }

    private fun drawAge(random: Random): Int {
        // Weighted toward the 21-29 peak, with a tail of teenagers and veterans.
        val roll = random.nextDouble()
        return when {
            roll < 0.10 -> random.nextInt(17, 21)
            roll < 0.40 -> random.nextInt(21, 25)
            roll < 0.72 -> random.nextInt(25, 29)
            roll < 0.90 -> random.nextInt(29, 33)
            else -> random.nextInt(33, 37)
        }
    }

    private fun generateName(country: String, random: Random): String =
        if (random.nextDouble() < 0.24) {
            NameData.internationalName(random)
        } else {
            "${NameData.firstName(country, random)} ${NameData.lastName(country, random)}"
        }

    /**
     * A pool of unattached players so the transfer market always has options
     * even when every club refuses to sell.
     */
    fun generateFreeAgents(
        count: Int,
        random: Random,
        idProvider: () -> Long
    ): List<Player> {
        val positions = Position.entries
        return (0 until count).map {
            val position = positions[random.nextInt(positions.size)]
            val ability = random.nextDouble(52.0, 76.0)
            val age = random.nextInt(19, 35)
            val profile = profile(position)
            val attrs = buildAttributes(ability, profile, position, random)
            val overall = overallFor(position, attrs)
            val stub = Club(
                id = -999L, name = "Free Agent", shortName = "FA", country = "England",
                leagueId = "", reputation = 50, stadiumName = "", stadiumCapacity = 0,
                balance = 0, transferBudget = 0, wageBudget = 0, boardExpectation = "",
                targetLeaguePosition = 20, primaryColor = 0, secondaryColor = 0
            )
            val player = generatePlayer(
                id = idProvider(),
                club = stub,
                position = position,
                baseAbility = ability,
                random = random
            )
            player.copy(
                age = age,
                attributes = attrs,
                overall = overall,
                clubId = null,
                contractYearsRemaining = 0,
                value = 0,
                wagePerWeek = 0
            ).let { it.copy(value = it.recomputeValue(), wagePerWeek = it.recomputeWage()) }
        }
    }
}
