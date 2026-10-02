package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Mentality(val label: String, val description: String) {
    VERY_DEFENSIVE("Very Defensive", "Sit deep and protect the lead"),
    DEFENSIVE("Defensive", "Cautious, prioritise the clean sheet"),
    BALANCED("Balanced", "Even approach in and out of possession"),
    ATTACKING("Attacking", "Push men forward and take risks"),
    VERY_ATTACKING("Very Attacking", "All-out attack, defence exposed");

    /** Scales the team's attacking intent; also raises the opponent's chance quality. */
    val attackModifier: Double
        get() = when (this) {
            VERY_DEFENSIVE -> 0.72
            DEFENSIVE -> 0.86
            BALANCED -> 1.0
            ATTACKING -> 1.14
            VERY_ATTACKING -> 1.28
        }

    val defenceModifier: Double
        get() = when (this) {
            VERY_DEFENSIVE -> 1.22
            DEFENSIVE -> 1.10
            BALANCED -> 1.0
            ATTACKING -> 0.92
            VERY_ATTACKING -> 0.82
        }
}

@Serializable
enum class PlayStyle(val label: String, val description: String) {
    POSSESSION("Possession", "Control the ball, patient build-up"),
    BALANCED("Balanced", "Adapt to the flow of the game"),
    COUNTER_ATTACK("Counter Attack", "Absorb pressure and break quickly"),
    HIGH_PRESS("High Press", "Win the ball high up the pitch"),
    DIRECT("Direct", "Get the ball forward fast");

    val possessionBias: Double
        get() = when (this) {
            POSSESSION -> 1.12
            BALANCED -> 1.0
            COUNTER_ATTACK -> 0.88
            HIGH_PRESS -> 1.05
            DIRECT -> 0.92
        }

    val chanceQualityBias: Double
        get() = when (this) {
            POSSESSION -> 0.98
            BALANCED -> 1.0
            COUNTER_ATTACK -> 1.08
            HIGH_PRESS -> 1.03
            DIRECT -> 0.96
        }

    /** Fatigue cost per match, so pressing football wears the squad down. */
    val fatigueBias: Double
        get() = when (this) {
            POSSESSION -> 1.0
            BALANCED -> 0.96
            COUNTER_ATTACK -> 0.92
            HIGH_PRESS -> 1.18
            DIRECT -> 1.02
        }
}

@Serializable
enum class DefensiveLine(val label: String) {
    DEEP("Deep"),
    NORMAL("Normal"),
    HIGH("High");

    val vulnerabilityToPace: Double
        get() = when (this) {
            DEEP -> 0.88
            NORMAL -> 1.0
            HIGH -> 1.14
        }

    val pressingHeight: Double
        get() = when (this) {
            DEEP -> 0.82
            NORMAL -> 1.0
            HIGH -> 1.16
        }
}

@Serializable
enum class Tempo(val label: String) {
    SLOW("Slow"),
    NORMAL("Normal"),
    FAST("Fast");

    val chanceVolume: Double
        get() = when (this) {
            SLOW -> 0.86
            NORMAL -> 1.0
            FAST -> 1.14
        }

    val errorRate: Double
        get() = when (this) {
            SLOW -> 0.9
            NORMAL -> 1.0
            FAST -> 1.12
        }
}

/** How wide the team plays; stretches the pitch or congests the middle. */
@Serializable
enum class Width(val label: String) {
    NARROW("Narrow"),
    NORMAL("Normal"),
    WIDE("Wide");

    val attackBias: Double
        get() = when (this) {
            NARROW -> 0.96
            NORMAL -> 1.0
            WIDE -> 1.06
        }

    val defensiveBias: Double
        get() = when (this) {
            NARROW -> 1.04
            NORMAL -> 1.0
            WIDE -> 0.96
        }
}

/** How aggressively the team presses to win the ball back. */
@Serializable
enum class Pressing(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    VERY_HIGH("Very High");

    val turnoverBias: Double
        get() = when (this) {
            LOW -> 0.90
            MEDIUM -> 1.0
            HIGH -> 1.10
            VERY_HIGH -> 1.18
        }

    /** Fatigue cost; heavy pressing wears players down faster. */
    val fatigueBias: Double
        get() = when (this) {
            LOW -> 0.90
            MEDIUM -> 1.0
            HIGH -> 1.14
            VERY_HIGH -> 1.26
        }

    /** Aggressive pressing concedes more fouls. */
    val foulBias: Double
        get() = when (this) {
            LOW -> 0.88
            MEDIUM -> 1.0
            HIGH -> 1.12
            VERY_HIGH -> 1.22
        }
}

/** Passing length and risk profile. */
@Serializable
enum class PassingStyle(val label: String) {
    SHORT("Short"),
    MIXED("Mixed"),
    DIRECT("Direct"),
    LONG_BALL("Long Ball");

    val possessionBias: Double
        get() = when (this) {
            SHORT -> 1.08
            MIXED -> 1.0
            DIRECT -> 0.95
            LONG_BALL -> 0.90
        }

    val chanceQualityBias: Double
        get() = when (this) {
            SHORT -> 1.0
            MIXED -> 1.0
            DIRECT -> 1.04
            LONG_BALL -> 0.97
        }
}

/** How the team builds attacks from the back. */
@Serializable
enum class BuildUp(val label: String) {
    PATIENT("Patient"),
    BALANCED("Balanced"),
    QUICK("Quick");

    val possessionBias: Double
        get() = when (this) {
            PATIENT -> 1.06
            BALANCED -> 1.0
            QUICK -> 0.96
        }

    val chanceVolumeBias: Double
        get() = when (this) {
            PATIENT -> 0.95
            BALANCED -> 1.0
            QUICK -> 1.07
        }
}

/** Emphasis on counter-attacking when possession is won. */
@Serializable
enum class CounterAttack(val label: String) {
    OFF("Off"),
    BALANCED("Balanced"),
    FREQUENT("Frequent");

    val chanceQualityBias: Double
        get() = when (this) {
            OFF -> 0.98
            BALANCED -> 1.0
            FREQUENT -> 1.07
        }

    val possessionBias: Double
        get() = when (this) {
            OFF -> 1.02
            BALANCED -> 1.0
            FREQUENT -> 0.94
        }
}

/** Possession focus: how much the team values keeping the ball. */
@Serializable
enum class PossessionFocus(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    val possessionBias: Double
        get() = when (this) {
            LOW -> 0.93
            MEDIUM -> 1.0
            HIGH -> 1.09
        }
}

/** Frequency of crosses into the box. */
@Serializable
enum class Crossing(val label: String) {
    RARE("Rare"),
    MIXED("Mixed"),
    FREQUENT("Frequent");

    val chanceVolumeBias: Double
        get() = when (this) {
            RARE -> 0.97
            MIXED -> 1.0
            FREQUENT -> 1.05
        }
}

/** How physically aggressive the team is in the tackle. */
@Serializable
enum class Aggression(val label: String) {
    LOW("Low"),
    NORMAL("Normal"),
    HIGH("High");

    val foulBias: Double
        get() = when (this) {
            LOW -> 0.85
            NORMAL -> 1.0
            HIGH -> 1.22
        }

    val defensiveBias: Double
        get() = when (this) {
            LOW -> 0.97
            NORMAL -> 1.0
            HIGH -> 1.05
        }
}

@Serializable
data class Tactics(
    val formationId: String = Formation.F4231.id,
    val mentality: Mentality = Mentality.BALANCED,
    val style: PlayStyle = PlayStyle.BALANCED,
    val defensiveLine: DefensiveLine = DefensiveLine.NORMAL,
    val tempo: Tempo = Tempo.NORMAL,
    val width: Width = Width.NORMAL,
    val pressing: Pressing = Pressing.MEDIUM,
    val passingStyle: PassingStyle = PassingStyle.MIXED,
    val buildUp: BuildUp = BuildUp.BALANCED,
    val counterAttack: CounterAttack = CounterAttack.BALANCED,
    val possessionFocus: PossessionFocus = PossessionFocus.MEDIUM,
    val crossing: Crossing = Crossing.MIXED,
    val aggression: Aggression = Aggression.NORMAL
) {
    val formation: Formation get() = Formation.byId(formationId)

    /** Combined possession multiplier from every instruction that affects it. */
    val possessionMultiplier: Double
        get() = style.possessionBias *
            passingStyle.possessionBias *
            buildUp.possessionBias *
            counterAttack.possessionBias *
            possessionFocus.possessionBias

    /** Combined attacking-chance quality multiplier. */
    val chanceQualityMultiplier: Double
        get() = style.chanceQualityBias *
            passingStyle.chanceQualityBias *
            counterAttack.chanceQualityBias *
            width.attackBias

    /** Combined chance-volume multiplier (how many chances are created). */
    val chanceVolumeMultiplier: Double
        get() = tempo.chanceVolume *
            buildUp.chanceVolumeBias *
            crossing.chanceVolumeBias *
            pressing.turnoverBias

    /** Combined defensive solidity multiplier. */
    val defensiveMultiplier: Double
        get() = width.defensiveBias * aggression.defensiveBias

    /** Combined fatigue cost per match. */
    val fatigueMultiplier: Double
        get() = style.fatigueBias * pressing.fatigueBias

    /** Combined foul propensity. */
    val foulMultiplier: Double
        get() = tempo.errorRate * pressing.foulBias * aggression.foulBias

    companion object {
        val DEFAULT = Tactics()
    }
}

/** Where a selected player sits in the XI. */
@Serializable
data class LineupSlot(
    val slotIndex: Int,
    val playerId: Long,
    /** True when the player is being fielded away from their natural position. */
    val outOfPosition: Boolean = false
)

@Serializable
data class TeamSelection(
    val startingXi: List<LineupSlot> = emptyList(),
    val substitutes: List<Long> = emptyList(),
    val captainId: Long? = null,
    val penaltyTakerId: Long? = null,
    val freeKickTakerId: Long? = null
) {
    val isEmpty: Boolean get() = startingXi.isEmpty()

    fun playerAt(slot: Int): Long? = startingXi.firstOrNull { it.slotIndex == slot }?.playerId

    val allSelectedIds: Set<Long> get() = (startingXi.map { it.playerId } + substitutes).toSet()

    fun isValid(): Boolean = startingXi.size == 11 &&
        startingXi.map { it.slotIndex }.toSet().size == 11 &&
        startingXi.any { it.slotIndex == 0 }
}

@Serializable
enum class MatchEventType {
    KICK_OFF,
    GOAL,
    OWN_GOAL,
    PENALTY_GOAL,
    PENALTY_MISSED,
    ASSIST,
    YELLOW_CARD,
    SECOND_YELLOW,
    RED_CARD,
    SUBSTITUTION,
    INJURY,
    SAVE,
    CHANCE_MISSED,
    HALF_TIME,
    FULL_TIME,
    EXTRA_TIME_START,
    EXTRA_TIME_END,
    PENALTY_SHOOTOUT_GOAL,
    PENALTY_SHOOTOUT_MISS,
    VAR,
    OFFSIDE,
    TACTICAL_CHANGE,
    INFO
}

@Serializable
data class MatchEvent(
    val minute: Int,
    val type: MatchEventType,
    val clubId: Long,
    /** Primary player involved (scorer, booked player, injured player, ...). */
    val playerId: Long? = null,
    val playerName: String = "",
    /** Secondary player (assister, player coming off, ...). */
    val secondaryPlayerId: Long? = null,
    val secondaryPlayerName: String = "",
    val detail: String = ""
) {
    val displayMinute: String get() = if (minute > 90) "90+${minute - 90}'" else "$minute'"
}

@Serializable
data class TeamMatchStats(
    val clubId: Long,
    val goals: Int = 0,
    val shots: Int = 0,
    val shotsOnTarget: Int = 0,
    val possession: Int = 50,
    val fouls: Int = 0,
    val corners: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val passAccuracy: Int = 80,
    val expectedGoals: Double = 0.0
)

@Serializable
data class PlayerMatchRating(
    val playerId: Long,
    val playerName: String,
    val rating: Double,
    val goals: Int = 0,
    val assists: Int = 0,
    val minutesPlayed: Int = 90,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val isManOfTheMatch: Boolean = false
)

@Serializable
data class MatchResult(
    val matchId: Long,
    val leagueId: String,
    val matchday: Int,
    val homeClubId: Long,
    val awayClubId: Long,
    val homeGoals: Int,
    val awayGoals: Int,
    val homeStats: TeamMatchStats,
    val awayStats: TeamMatchStats,
    val events: List<MatchEvent>,
    val playerRatings: List<PlayerMatchRating>,
    val playerOfTheMatchId: Long? = null,
    /** Set for knockout ties decided on penalties; null for league games. */
    val penaltyShootoutHome: Int? = null,
    val penaltyShootoutAway: Int? = null,
    /** Per-15-minute momentum swing for the home side, -100..100. */
    val momentum: List<Int> = emptyList(),
    /** True when the match needed extra time. */
    val extraTime: Boolean = false
) {
    val isHomeWin: Boolean get() = homeGoals > awayGoals
    val isAwayWin: Boolean get() = awayGoals > homeGoals
    val isDraw: Boolean get() = homeGoals == awayGoals

    fun scoreLine(): String = "$homeGoals - $awayGoals"
}
