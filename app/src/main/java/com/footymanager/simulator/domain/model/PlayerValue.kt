package com.footymanager.simulator.domain.model

import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * A market valuation broken into the factors that produced it, so the UI can
 * explain *why* a player is worth what they are worth rather than showing an
 * unexplained number.
 */
data class PlayerValuation(
    val marketValue: Long,
    val weeklyWage: Long,
    val ageFactor: Double,
    val potentialFactor: Double,
    val contractFactor: Double,
    val formFactor: Double,
    val performanceFactor: Double,
    val positionFactor: Double,
    val injuryFactor: Double,
    /** Short human-readable reasons, strongest first. */
    val notes: List<String>
)

/**
 * The original player market-value model.
 *
 * It is deliberately *not* a copy of any proprietary database. It reproduces the
 * public, well-documented shape of the football market (see Transfermarkt's
 * published factor list and the academic hedonic-regression literature): value
 * rises exponentially with current ability, is heavily age-dependent with a peak
 * in the early-to-mid twenties, pays a premium for unfulfilled potential, falls
 * sharply as a contract runs down, and is nudged by form, output, position,
 * injury and morale.
 *
 * The exponential ability term is the single most important driver: it keeps
 * elite players far more valuable than good ones, so a squad never collapses to
 * "everyone in the same rating band is worth the same".
 *
 * Values are calibrated so that (in the game's economy) a peak-age 90-overall
 * player sits around the hundred-million mark and a 65-overall journeyman around
 * a couple of million, matching the real shape of the market without copying it.
 */
object PlayerValuer {

    /** Hard ceiling so an all-time great cannot produce an absurd number. */
    const val MAX_VALUE = 260_000_000L
    const val MIN_VALUE = 20_000L

    /** Peak-age curve: premium for youth, steep decline after 27. */
    fun ageFactor(age: Int): Double = when {
        age <= 17 -> 1.28
        age == 18 -> 1.33
        age == 19 -> 1.38
        age == 20 -> 1.41
        age == 21 -> 1.42
        age == 22 -> 1.40
        age == 23 -> 1.36
        age == 24 -> 1.29
        age == 25 -> 1.21
        age == 26 -> 1.11
        age == 27 -> 1.00
        age == 28 -> 0.86
        age == 29 -> 0.72
        age == 30 -> 0.58
        age == 31 -> 0.45
        age == 32 -> 0.33
        age == 33 -> 0.23
        age == 34 -> 0.16
        age == 35 -> 0.11
        else -> 0.07
    }

    /** Unfulfilled potential is a genuine premium, capped so it cannot explode. */
    fun potentialFactor(potential: Int, overall: Int): Double {
        val gap = (potential - overall).coerceAtLeast(0)
        return (1.0 + gap * 0.06).coerceAtMost(2.0)
    }

    /** A player in the final year of a deal is worth far less than a free risk. */
    fun contractFactor(yearsRemaining: Int): Double = when {
        yearsRemaining <= 0 -> 0.30
        yearsRemaining == 1 -> 0.65
        yearsRemaining == 2 -> 0.85
        yearsRemaining == 3 -> 0.95
        else -> 1.0
    }

    fun formFactor(form: Double): Double = (0.90 + (form - 5.0) * 0.030).coerceIn(0.84, 1.16)

    /** Attacking and creative players carry a premium; keepers a discount. */
    fun positionFactor(position: Position): Double = when (position) {
        Position.ST -> 1.16
        Position.RW, Position.LW -> 1.12
        Position.CAM -> 1.10
        Position.CM -> 1.05
        Position.CDM -> 1.00
        Position.RB, Position.LB -> 0.98
        Position.CB -> 0.97
        Position.GK -> 0.92
    }

    /** Goals, assists and match ratings move value once there is a sample size. */
    fun performanceFactor(stats: PlayerSeasonStats): Double {
        if (stats.appearances < 3) return 1.0
        val perGame = (stats.goals + stats.assists).toDouble() / stats.appearances
        val ratingBoost = if (stats.ratedMatches > 0) (stats.averageRating - 6.8) * 0.03 else 0.0
        return (1.0 + (perGame - 0.25) * 0.12 + ratingBoost).coerceIn(0.90, 1.22)
    }

    fun injuryFactor(player: Player): Double = when {
        !player.isInjured -> 1.0
        player.injury.type == InjuryType.KNEE -> 0.68
        player.injury.weeksRemaining >= 4 -> 0.82
        else -> 0.92
    }

    fun moraleFactor(moraleScore: Double): Double =
        (0.96 + (moraleScore - 3.0) * 0.02).coerceIn(0.92, 1.04)

    /** The headline market value. */
    fun marketValue(player: Player): Long {
        val base = 190_000.0 * exp(0.157 * (player.overall - 50))
        val raw = base *
            ageFactor(player.age) *
            potentialFactor(player.potential, player.overall) *
            contractFactor(player.contractYearsRemaining) *
            formFactor(player.form) *
            positionFactor(player.position) *
            performanceFactor(player.seasonStats) *
            injuryFactor(player) *
            moraleFactor(player.moraleScore)
        return raw.roundToLong().coerceIn(MIN_VALUE, MAX_VALUE)
    }

    private fun wageAgeFactor(age: Int): Double = when {
        age <= 18 -> 0.55
        age <= 20 -> 0.70
        age <= 22 -> 0.85
        age <= 30 -> 1.0
        else -> 0.94
    }

    /**
     * The wage a player of this quality would command on the open market. Wages
     * scale steeply with ability (a world-class player earns many times a squad
     * player) and are damped for young players and veterans.
     */
    fun weeklyWage(player: Player): Long {
        val base = 1_700.0 * exp(0.15 * (player.overall - 50))
        val potentialUplift = 1.0 + (player.potential - player.overall).coerceAtLeast(0) * 0.012
        val raw = base * wageAgeFactor(player.age) * potentialUplift
        return raw.roundToLong().coerceIn(1_000L, 900_000L)
    }

    /** A full breakdown for the player profile / negotiation screens. */
    fun breakdown(player: Player): PlayerValuation {
        val age = ageFactor(player.age)
        val potential = potentialFactor(player.potential, player.overall)
        val contract = contractFactor(player.contractYearsRemaining)
        val form = formFactor(player.form)
        val performance = performanceFactor(player.seasonStats)
        val position = positionFactor(player.position)
        val injury = injuryFactor(player)

        val notes = buildList {
            when {
                player.age <= 21 -> add("Young enough to keep improving")
                player.age in 22..27 -> add("In his peak years")
                player.age >= 31 -> add("Limited years at the top remain")
            }
            if (player.potential - player.overall >= 6) add("High ceiling (potential ${player.potential})")
            when (player.contractYearsRemaining) {
                0 -> add("Out of contract")
                1 -> add("Final year of his contract")
            }
            if (player.form >= 6.8) add("In excellent form")
            if (player.form <= 3.5) add("Struggling for form")
            if (player.seasonStats.appearances >= 5 && player.seasonStats.averageRating >= 7.2) {
                add("Outstanding performances this season")
            }
            if (player.isInjured) add("Currently injured")
            if (player.moraleScore <= 2.4) add("Unhappy and pushing for a move")
        }

        return PlayerValuation(
            marketValue = marketValue(player),
            weeklyWage = weeklyWage(player),
            ageFactor = age,
            potentialFactor = potential,
            contractFactor = contract,
            formFactor = form,
            performanceFactor = performance,
            positionFactor = position,
            injuryFactor = injury,
            notes = notes
        )
    }
}
