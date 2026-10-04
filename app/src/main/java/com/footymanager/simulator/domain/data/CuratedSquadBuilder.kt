package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.Attributes
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Injury
import com.footymanager.simulator.domain.model.InjuryType
import com.footymanager.simulator.domain.model.Player
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Turns the hand-authored [CuratedSquads] designs into real [Player] objects.
 *
 * Curated players go through the same attribute pipeline as generated ones: the
 * design supplies a target ability, position and archetype, and the attributes
 * are derived from the position profile with a small random spread. The stored
 * overall is recomputed from those attributes, so a curated player's displayed
 * rating is always consistent with the numbers on their profile page.
 *
 * Value and wage are not authored by hand — they fall out of the same
 * [Player.recomputeValue] / [Player.recomputeWage] formulas used everywhere else,
 * which keeps the transfer market coherent.
 */
object CuratedSquadBuilder {

    fun build(
        club: Club,
        designs: List<CuratedSquads.CuratedPlayer>,
        random: Random,
        idProvider: () -> Long,
        /**
         * Same AI-strength multiplier the generated squads use, so a curated AI
         * club is neither advantaged nor penalised by the chosen difficulty.
         * Authored ratings are the NORMAL-difficulty baseline (multiplier 1.0).
         */
        difficulty: Double = 1.0
    ): List<Player> = designs.map { design ->
        val profile = PlayerGenerator.profile(design.position)
        val target = (design.overall * difficulty).coerceIn(20.0, 96.0)
        val attributes = PlayerGenerator.buildAttributes(
            ability = target,
            profile = profile,
            position = design.position,
            random = random
        )
        val overall = PlayerGenerator.overallFor(design.position, attributes)
        // Potential must never sit below the recomputed current ability.
        val potential = (design.potential * difficulty)
            .coerceAtLeast(overall.toDouble())
            .roundToInt()
            .coerceAtMost(96)

        val base = Player(
            id = idProvider(),
            name = design.name,
            age = design.age,
            nationality = NameData.nationalityForLeague(club.country, random),
            position = design.position,
            attributes = attributes,
            potential = potential,
            overall = overall,
            preferredFoot = design.foot,
            fitness = 88 + random.nextInt(13),
            moraleScore = 3.2 + random.nextDouble() * 1.2 - 0.3,
            form = 4.2 + random.nextDouble() * 2.4,
            injury = Injury(InjuryType.NONE, 0),
            contractYearsRemaining = 1 + random.nextInt(4),
            wagePerWeek = 0,
            value = 0,
            clubId = club.id
        )
        base.copy(
            wagePerWeek = base.recomputeWage(),
            value = base.recomputeValue()
        )
    }

    /**
     * A curated squad may not cover every position a formation needs (for
     * example a side whose list has only two centre backs). This tops the squad
     * up to the standard 24 with generated players so the AI and the auto-picker
     * always have enough cover.
     */
    fun topUp(
        club: Club,
        curated: List<Player>,
        template: List<com.footymanager.simulator.domain.model.Position>,
        random: Random,
        idProvider: () -> Long
    ): List<Player> {
        if (curated.size >= template.size) return curated

        val baseAbility = 46.0 + club.reputation * 0.40
        val counts = curated.groupingBy { it.position }.eachCount().toMutableMap()
        val extra = mutableListOf<Player>()

        for (position in template) {
            val have = counts[position] ?: 0
            if (have > 0) {
                counts[position] = have - 1
                continue
            }
            extra += PlayerGenerator.generatePlayer(
                id = idProvider(),
                club = club,
                position = position,
                baseAbility = baseAbility - 6.0,
                random = random
            )
        }
        return curated + extra
    }
}
