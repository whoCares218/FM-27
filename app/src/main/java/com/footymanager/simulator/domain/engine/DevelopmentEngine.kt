package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.AttributeKey
import com.footymanager.simulator.domain.model.Attributes
import com.footymanager.simulator.domain.model.Injury
import com.footymanager.simulator.domain.model.InjuryType
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.TrainingFocus
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Everything that changes a player between matches: fitness, form, morale,
 * injuries, suspensions and long-term development.
 */
object DevelopmentEngine {

    // ------------------------------------------------------------ post-match

    /**
     * Applies the physical and psychological consequences of playing a match.
     *
     * @param minutes minutes actually played
     * @param rating match rating on the 4..10 scale
     * @param teamGoals goals scored by the player's team
     * @param opponentGoals goals conceded
     * @param tactics used, which scales fatigue
     * @param isStarter whether the player started
     */
    fun applyMatchEffects(
        player: Player,
        minutes: Int,
        rating: Double,
        teamGoals: Int,
        opponentGoals: Int,
        styleFatigue: Double,
        isStarter: Boolean
    ): Player {
        if (minutes <= 0) return player

        val load = (minutes / 90.0).coerceIn(0.0, 1.0)

        // ---- Fitness ----
        val baseDrain = if (player.age >= 32) 17.0 else if (player.age >= 29) 15.0 else 13.0
        val staminaResistance = player.attributes.physical / 100.0
        val drain = baseDrain * load * styleFatigue * (1.25 - staminaResistance * 0.5)
        val newFitness = (player.fitness - drain).coerceIn(20.0, 100.0)

        // ---- Form: results and personal performance both matter ----
        val resultSwing = when {
            teamGoals - opponentGoals >= 2 -> 0.9
            teamGoals > opponentGoals -> 0.5
            teamGoals == opponentGoals -> 0.0
            opponentGoals - teamGoals == 1 -> -0.45
            else -> -0.9
        }
        val performanceSwing = (rating - 6.6) * 0.55
        val newForm = (player.form * 0.72 + (player.form + resultSwing + performanceSwing) * 0.28)
            .coerceIn(1.0, 10.0)

        // ---- Morale: playing time, results and personal form ----
        var moraleDelta = resultSwing * 0.16 + (rating - 6.6) * 0.07
        if (isStarter) moraleDelta += 0.03 else moraleDelta -= 0.04
        if (minutes in 1..20) moraleDelta -= 0.05
        val newMorale = (player.moraleScore + moraleDelta).coerceIn(1.0, 5.0)

        return player.copy(
            fitness = newFitness.roundToInt(),
            form = round2(newForm),
            moraleScore = round2(newMorale)
        )
    }

    /** Fitness regained during a recovery/training week. */
    fun weeklyRecovery(player: Player, focus: TrainingFocus, random: Random): Player {
        if (player.fitness >= 100) return player
        val ageRecovery = when {
            player.age <= 23 -> 1.22
            player.age <= 27 -> 1.10
            player.age <= 30 -> 1.0
            player.age <= 33 -> 0.88
            else -> 0.74
        }
        val focusBonus = if (focus == TrainingFocus.FITNESS) 1.22 else 1.0
        val base = 16.0 * ageRecovery * focusBonus
        val recovered = (player.fitness + base + random.nextDouble(-2.0, 2.0)).coerceIn(0.0, 100.0)
        return player.copy(fitness = recovered.roundToInt())
    }

    /** Players not selected lose sharpness and grow frustrated. */
    fun applyBenchEffects(player: Player, random: Random): Player {
        val moraleDrop = if (player.overall >= 76) 0.09 else 0.03
        val formDrift = if (player.form > 5.0) -0.12 else 0.08
        return player.copy(
            fitness = (player.fitness + random.nextInt(1, 4)).coerceAtMost(100),
            moraleScore = round2((player.moraleScore - moraleDrop).coerceIn(1.0, 5.0)),
            form = round2((player.form + formDrift).coerceIn(1.0, 10.0))
        )
    }

    // --------------------------------------------------------- injuries

    /** Assigns a new injury and returns the updated player. */
    fun inflictInjury(player: Player, random: Random): Player {
        val type = InjuryType.matchInjuries[random.nextInt(InjuryType.matchInjuries.size)]
        val weeks = random.nextInt(type.minWeeks, type.maxWeeks + 1)
        val stats = player.seasonStats.copy(injuriesSuffered = player.seasonStats.injuriesSuffered + 1)
        return player.copy(
            injury = Injury(type, weeks),
            seasonStats = stats
        )
    }

    /** Advances injury recovery by one week. */
    fun progressInjury(player: Player): Player {
        if (!player.isInjured) return player
        val remaining = player.injury.weeksRemaining - 1
        return player.copy(
            injury = if (remaining <= 0) Injury(InjuryType.NONE, 0)
            else player.injury.copy(weeksRemaining = remaining)
        )
    }

    /** Advances a suspension by one week. */
    fun progressSuspension(player: Player): Player {
        if (player.suspensionWeeks <= 0) return player
        return player.copy(suspensionWeeks = player.suspensionWeeks - 1)
    }

    /** Applies the card consequences of a match to a player. */
    fun applyCards(player: Player, yellows: Int, reds: Int): Player {
        var updated = player
        if (yellows > 0) {
            var accumulation = player.yellowCardAccumulation + yellows
            // Five bookings in a season triggers a one-match ban.
            if (accumulation >= 5) {
                accumulation -= 5
                updated = updated.copy(suspensionWeeks = updated.suspensionWeeks + 1)
            }
            updated = updated.copy(yellowCardAccumulation = accumulation)
        }
        if (reds > 0) {
            updated = updated.copy(
                suspensionWeeks = updated.suspensionWeeks + 1 + reds,
                yellowCardAccumulation = 0
            )
        }
        return updated
    }

    // --------------------------------------------------------- development

    /**
     * Long-term progression. Growth depends on age, headroom to potential,
     * training focus and how much football the player actually played.
     *
     * Runs once per matchday-week so the effect is gradual and believable.
     */
    fun develop(
        player: Player,
        focus: TrainingFocus,
        minutesThisWeek: Int,
        developmentRate: Double,
        random: Random
    ): Player {
        if (player.isInjured && player.injury.type == InjuryType.KNEE) {
            // Serious injuries slow growth but do not stop it.
        }

        val ageFactor = when {
            player.age <= 17 -> 1.55
            player.age <= 19 -> 1.40
            player.age <= 21 -> 1.20
            player.age <= 23 -> 1.00
            player.age <= 25 -> 0.72
            player.age <= 27 -> 0.42
            player.age <= 29 -> 0.10
            player.age <= 31 -> -0.60
            player.age <= 33 -> -0.95
            else -> -1.25
        }

        val headroom = (player.potential - player.overall).coerceAtLeast(0)
        val playingTimeFactor = when {
            minutesThisWeek >= 70 -> 1.20
            minutesThisWeek >= 30 -> 0.95
            minutesThisWeek >= 1 -> 0.70
            else -> 0.55
        }

        // Progress accumulates in a fractional pool; a full point is banked when
        // it crosses 1.0 so ratings move in visible steps rather than constantly.
        // The coefficient is tuned so a 19-year-old playing every week gains
        // roughly three to four points across a season, while a squad player who
        // rarely features gains around one.
        var pool = player.developmentPool
        val baseGain = 0.075 * ageFactor * playingTimeFactor * developmentRate
        if (baseGain >= 0.0) {
            // Growth is limited by how much room is left before the potential ceiling.
            val headroomFactor = if (headroom > 0) (0.35 + headroom * 0.055).coerceAtMost(1.6) else 0.0
            pool += baseGain * headroomFactor
        } else {
            // Decline is not gated by headroom: a player at their ceiling still ages.
            pool += baseGain
        }
        pool += random.nextDouble(-0.012, 0.012)

        var updated = player
        if (pool >= 1.0 && headroom > 0) {
            pool -= 1.0
            updated = applyGrowth(updated, focus, random)
        } else if (pool <= -1.0 && ageFactor < 0) {
            pool += 1.0
            updated = applyDecline(updated, random)
        }
        pool = pool.coerceIn(-1.5, 1.5)

        return updated.copy(developmentPool = round2(pool))
    }

    /**
     * Improves one attribute according to the training focus and lifts the overall
     * rating by a point, never beyond the player's potential.
     */
    private fun applyGrowth(player: Player, focus: TrainingFocus, random: Random): Player {
        val keys = focusAttributesFor(player, focus)
        val chosen = keys[random.nextInt(keys.size)]
        val current = player.attributes.get(chosen)
        val newAttributes = if (current < 95) player.attributes.with(chosen, current + 1) else player.attributes

        val newOverall = (player.overall + 1).coerceAtMost(player.potential).coerceAtMost(96)
        val updated = player.copy(attributes = newAttributes, overall = newOverall)
        return updated.copy(value = updated.recomputeValue(), wagePerWeek = updated.recomputeWage())
    }

    /** Ageing erodes pace and physicality and costs a point of overall rating. */
    private fun applyDecline(player: Player, random: Random): Player {
        val keys = listOf(AttributeKey.PACE, AttributeKey.PACE, AttributeKey.PHYSICAL, AttributeKey.DRIBBLING)
        val chosen = keys[random.nextInt(keys.size)]
        val current = player.attributes.get(chosen)
        val newAttributes = if (current > 25) player.attributes.with(chosen, current - 1) else player.attributes

        val newOverall = (player.overall - 1).coerceAtLeast(35)
        val updated = player.copy(attributes = newAttributes, overall = newOverall)
        return updated.copy(value = updated.recomputeValue(), wagePerWeek = updated.recomputeWage())
    }

    private fun focusAttributes(focus: TrainingFocus): List<AttributeKey> = when (focus) {
        TrainingFocus.ATTACK -> listOf(AttributeKey.SHOOTING, AttributeKey.DRIBBLING, AttributeKey.PACE)
        TrainingFocus.DEFENCE -> listOf(AttributeKey.DEFENDING, AttributeKey.PHYSICAL)
        TrainingFocus.FITNESS -> listOf(AttributeKey.PHYSICAL, AttributeKey.PACE)
        TrainingFocus.POSSESSION -> listOf(AttributeKey.PASSING, AttributeKey.DRIBBLING)
        TrainingFocus.BALANCED -> AttributeKey.entries.toList()
    }

    /** Goalkeepers train goalkeeping regardless of the outfield focus. */
    fun focusAttributesFor(player: Player, focus: TrainingFocus): List<AttributeKey> =
        if (player.position == Position.GK) {
            listOf(AttributeKey.GOALKEEPING, AttributeKey.GOALKEEPING, AttributeKey.PHYSICAL)
        } else {
            focusAttributes(focus)
        }

    // ------------------------------------------------------------ contracts

    /** Players age by one year and lose value as their contract shortens. */
    fun applySeasonTurnover(player: Player): Player {
        val newAge = player.age + 1
        val newContract = (player.contractYearsRemaining - 1).coerceAtLeast(0)
        return player.copy(
            age = newAge,
            contractYearsRemaining = newContract,
            seasonsAtClub = player.seasonsAtClub + 1,
            seasonStats = com.footymanager.simulator.domain.model.PlayerSeasonStats.EMPTY,
            careerStats = mergeCareer(player.careerStats, player.seasonStats),
            yellowCardAccumulation = 0,
            suspensionWeeks = 0,
            developmentPool = 0.0,
            signedThisWindow = false,
            form = 5.0,
            fitness = 100
        ).let { it.copy(value = it.recomputeValue()) }
    }

    private fun mergeCareer(
        career: com.footymanager.simulator.domain.model.PlayerSeasonStats,
        season: com.footymanager.simulator.domain.model.PlayerSeasonStats
    ): com.footymanager.simulator.domain.model.PlayerSeasonStats = career.copy(
        appearances = career.appearances + season.appearances,
        starts = career.starts + season.starts,
        minutesPlayed = career.minutesPlayed + season.minutesPlayed,
        goals = career.goals + season.goals,
        assists = career.assists + season.assists,
        yellowCards = career.yellowCards + season.yellowCards,
        redCards = career.redCards + season.redCards,
        cleanSheets = career.cleanSheets + season.cleanSheets,
        goalsConceded = career.goalsConceded + season.goalsConceded,
        ratingSum = career.ratingSum + season.ratingSum,
        ratedMatches = career.ratedMatches + season.ratedMatches,
        injuriesSuffered = career.injuriesSuffered + season.injuriesSuffered,
        manOfTheMatch = career.manOfTheMatch + season.manOfTheMatch
    )

    private fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0
}
