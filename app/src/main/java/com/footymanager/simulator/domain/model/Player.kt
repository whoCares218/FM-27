package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/**
 * Outfield and goalkeeper attributes on a 1..99 scale, mirroring the six
 * attributes players are judged on plus a separate goalkeeping rating.
 */
@Serializable
data class Attributes(
    val pace: Int,
    val shooting: Int,
    val passing: Int,
    val dribbling: Int,
    val defending: Int,
    val physical: Int,
    val goalkeeping: Int = 1
) {
    fun get(key: AttributeKey): Int = when (key) {
        AttributeKey.PACE -> pace
        AttributeKey.SHOOTING -> shooting
        AttributeKey.PASSING -> passing
        AttributeKey.DRIBBLING -> dribbling
        AttributeKey.DEFENDING -> defending
        AttributeKey.PHYSICAL -> physical
        AttributeKey.GOALKEEPING -> goalkeeping
    }

    fun with(key: AttributeKey, value: Int): Attributes {
        val v = value.coerceIn(1, 99)
        return when (key) {
            AttributeKey.PACE -> copy(pace = v)
            AttributeKey.SHOOTING -> copy(shooting = v)
            AttributeKey.PASSING -> copy(passing = v)
            AttributeKey.DRIBBLING -> copy(dribbling = v)
            AttributeKey.DEFENDING -> copy(defending = v)
            AttributeKey.PHYSICAL -> copy(physical = v)
            AttributeKey.GOALKEEPING -> copy(goalkeeping = v)
        }
    }
}

@Serializable
enum class AttributeKey(val label: String) {
    PACE("Pace"),
    SHOOTING("Shooting"),
    PASSING("Passing"),
    DRIBBLING("Dribbling"),
    DEFENDING("Defending"),
    PHYSICAL("Physical"),
    GOALKEEPING("Goalkeeping")
}

@Serializable
enum class Morale(val label: String, val score: Int) {
    VERY_UNHAPPY("Very unhappy", 1),
    UNHAPPY("Unhappy", 2),
    NEUTRAL("Neutral", 3),
    HAPPY("Happy", 4),
    VERY_HAPPY("Very happy", 5);

    companion object {
        /** Maps a continuous 1..5 value onto the discrete morale band. */
        fun fromScore(score: Double): Morale = when {
            score < 1.8 -> VERY_UNHAPPY
            score < 2.6 -> UNHAPPY
            score < 3.4 -> NEUTRAL
            score < 4.2 -> HAPPY
            else -> VERY_HAPPY
        }
    }
}

/**
 * The role promised to a player in the squad hierarchy. It shapes both how happy
 * a player is with their minutes and how much a new signing expects to be paid.
 */
@Serializable
enum class SquadRole(val label: String, val description: String) {
    STAR("Star Player", "Undisputed first choice and the face of the team"),
    FIRST_TEAM("First Team", "A regular starter every week"),
    ROTATION("Rotation", "In and out of the side"),
    BACKUP("Backup", "Cover for injuries and rotation"),
    YOUTH("Youth Prospect", "Developing for the future");

    /** Wage expectation multiplier relative to the player's base demand. */
    val wageMultiplier: Double
        get() = when (this) {
            STAR -> 1.28
            FIRST_TEAM -> 1.10
            ROTATION -> 1.0
            BACKUP -> 0.90
            YOUTH -> 0.80
        }
}

@Serializable
enum class InjuryType(val label: String, val minWeeks: Int, val maxWeeks: Int) {
    NONE("Fit", 0, 0),
    MINOR_KNOCK("Minor knock", 1, 1),
    MUSCLE("Muscle injury", 2, 3),
    HAMSTRING("Hamstring", 3, 5),
    ANKLE("Ankle injury", 4, 7),
    KNEE("Knee injury", 8, 16);

    companion object {
        /** Injuries that can be picked up in a match, weighted by severity. */
        val matchInjuries: List<InjuryType> = listOf(
            MINOR_KNOCK, MINOR_KNOCK, MINOR_KNOCK,
            MUSCLE, MUSCLE,
            HAMSTRING,
            ANKLE,
            KNEE
        )
    }
}

@Serializable
data class Injury(
    val type: InjuryType,
    val weeksRemaining: Int
) {
    val isInjured: Boolean get() = weeksRemaining > 0 && type != InjuryType.NONE
}

/** A single line of a player's season record. */
@Serializable
data class PlayerSeasonStats(
    val appearances: Int = 0,
    val starts: Int = 0,
    val minutesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val cleanSheets: Int = 0,
    val goalsConceded: Int = 0,
    val ratingSum: Double = 0.0,
    val ratedMatches: Int = 0,
    val injuriesSuffered: Int = 0,
    val manOfTheMatch: Int = 0
) {
    val averageRating: Double get() = if (ratedMatches == 0) 0.0 else ratingSum / ratedMatches

    fun addAppearance(rating: Double, isStart: Boolean, minutes: Int): PlayerSeasonStats = copy(
        appearances = appearances + 1,
        starts = starts + if (isStart) 1 else 0,
        minutesPlayed = minutesPlayed + minutes,
        ratingSum = ratingSum + rating,
        ratedMatches = ratedMatches + 1
    )

    companion object {
        val EMPTY = PlayerSeasonStats()
    }
}

/**
 * A player in the game world. [id] is stable across saves so that statistics,
 * contracts and transfer history survive serialization.
 */
@Serializable
data class Player(
    val id: Long,
    val name: String,
    val age: Int,
    val nationality: String,
    val position: Position,
    val attributes: Attributes,
    val potential: Int,
    /** Underlying ability used by development; moves toward [potential]. */
    val overall: Int,
    val preferredFoot: PreferredFoot = PreferredFoot.RIGHT,
    /** 0..100 match sharpness; low fitness raises injury and error risk. */
    val fitness: Int = 100,
    /** Continuous 1..5 morale value, exposed as [morale]. */
    val moraleScore: Double = 3.0,
    /** 0..10 recent form; 5 is neutral. */
    val form: Double = 5.0,
    val injury: Injury = Injury(InjuryType.NONE, 0),
    val yellowCardAccumulation: Int = 0,
    val suspensionWeeks: Int = 0,
    val contractYearsRemaining: Int = 2,
    val wagePerWeek: Long = 10_000,
    val value: Long = 1_000_000,
    val clubId: Long? = null,
    /** Players signed this window cannot be sold again until the next window. */
    val signedThisWindow: Boolean = false,
    val seasonStats: PlayerSeasonStats = PlayerSeasonStats.EMPTY,
    val careerStats: PlayerSeasonStats = PlayerSeasonStats.EMPTY,
    /** Number of consecutive seasons at the club; drives loyalty morale. */
    val seasonsAtClub: Int = 0,
    /**
     * Fractional development progress toward the next attribute point. Kept in
     * the save so growth is gradual and cannot be reset by re-loading.
     */
    val developmentPool: Double = 0.0,
    /** The role the club has promised this player, set when they sign a contract. */
    val squadRole: SquadRole = SquadRole.ROTATION
) {
    val morale: Morale get() = Morale.fromScore(moraleScore)

    val isInjured: Boolean get() = injury.isInjured

    val isSuspended: Boolean get() = suspensionWeeks > 0

    /** A player who cannot be selected for the next match. */
    val isAvailable: Boolean get() = !isInjured && !isSuspended

    val fitnessBand: String
        get() = when {
            fitness >= 90 -> "Excellent"
            fitness >= 80 -> "Good"
            fitness >= 65 -> "Tired"
            fitness >= 45 -> "Fatigued"
            else -> "Exhausted"
        }

    /**
     * Market value, delegated to [PlayerValuation] so the whole game shares one
     * valuation model. Kept as a method on [Player] because development and
     * season turnover recompute it in place.
     */
    fun recomputeValue(): Long = PlayerValuer.marketValue(this)

    fun recomputeWage(): Long = PlayerValuer.weeklyWage(this)

    fun matches(search: String): Boolean {
        if (search.isBlank()) return true
        val q = search.trim().lowercase()
        return name.lowercase().contains(q) ||
            position.short.lowercase().contains(q) ||
            nationality.lowercase().contains(q)
    }
}
