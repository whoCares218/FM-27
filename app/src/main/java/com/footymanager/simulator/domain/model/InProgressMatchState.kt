package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/**
 * A fully serializable snapshot of an in-progress or paused match.
 *
 * Everything the live engine needs to continue exactly where it left off lives
 * here, including the random generator's state, so a match interrupted at minute
 * 30 resumes at minute 30 with the same score, events, cards, substitutions and
 * fatigue — whether the interruption was navigation, the app going to the
 * background, or the process being killed.
 *
 * It is stored inside [Career] so it travels through the existing single atomic
 * save write rather than needing a parallel persistence path.
 */
@Serializable
data class InProgressMatchState(
    val matchId: Long,
    val leagueId: String,
    val matchday: Int,
    val homeClubId: Long,
    val awayClubId: Long,
    /** True when the user is the home side; drives which side they manage. */
    val userIsHome: Boolean,
    val mode: MatchModePersist = MatchModePersist.PLAY,

    /** Live clock and period. */
    val phase: MatchPhasePersist = MatchPhasePersist.FIRST_HALF,
    val minute: Int = 0,

    /** Score and period breakdown. */
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    val homeGoalsRegular: Int = 0,
    val awayGoalsRegular: Int = 0,
    val homeEtGoals: Int = 0,
    val awayEtGoals: Int = 0,
    val shootoutHome: Int? = null,
    val shootoutAway: Int? = null,

    /** Running statistics. */
    val homeShots: Int = 0,
    val awayShots: Int = 0,
    val homeOnTarget: Int = 0,
    val awayOnTarget: Int = 0,
    val homeFouls: Int = 0,
    val awayFouls: Int = 0,
    val homeCorners: Int = 0,
    val awayCorners: Int = 0,
    val homeYellows: Int = 0,
    val awayYellows: Int = 0,
    val homeReds: Int = 0,
    val awayReds: Int = 0,
    val homePossessionSum: Double = 0.0,
    val possessionSamples: Int = 0,
    val homeDangerous: Int = 0,
    val awayDangerous: Int = 0,
    val homeDomination: Double = 50.0,

    val firstHalfStoppage: Int = 0,
    val secondHalfStoppage: Int = 0,

    /** The complete event history, so the feed is identical on resume. */
    val events: List<MatchEvent> = emptyList(),

    /** Player-level bookkeeping. */
    val offMinutes: Map<Long, Int> = emptyMap(),
    val onAtMinute: Map<Long, Int> = emptyMap(),
    val sentOffClubs: List<Long> = emptyList(),
    val subsMade: Map<Long, Int> = emptyMap(),
    val windowsUsed: Map<Long, Int> = emptyMap(),
    val ratings: List<RatingSnapshot> = emptyList(),

    /** The inputs in force for each side (selection, tactics and bench). */
    val homeInput: TeamInputSnapshot,
    val awayInput: TeamInputSnapshot,

    /** The user's live XI and remaining bench, tracked across substitutions. */
    val liveXi: List<Long> = emptyList(),
    val liveBench: List<Long> = emptyList(),

    /** Competition rules in force. */
    val allowExtraTime: Boolean = false,
    val allowShootout: Boolean = false,
    val maxSubstitutions: Int = 5,
    val maxSubstitutionWindows: Int = 3,

    val determinism: Double = 1.0,
    val homeAdvantage: Boolean = true,

    /** SplitMix64 state of the engine's random generator, so draws resume exactly. */
    val rngState: Long = 0L,

    /** Saved when the user pauses or leaves; true if the clock should restart on resume. */
    val suspended: Boolean = false
) {
    val isFinished: Boolean get() = phase == MatchPhasePersist.FINISHED
}

/** Which presentation mode the interrupted match used. */
@Serializable
enum class MatchModePersist { PLAY, QUICK }

/** Serializable mirror of the engine's [com.footymanager.simulator.domain.engine.MatchPhase]. */
@Serializable
enum class MatchPhasePersist {
    FIRST_HALF,
    HALF_TIME,
    SECOND_HALF,
    EXTRA_TIME_BREAK,
    EXTRA_TIME_FIRST,
    EXTRA_TIME_SECOND,
    SHOOTOUT,
    FINISHED
}

/**
 * One side of a match as it stands right now: the XI on the pitch, the bench
 * still available and the tactics in force. The full squad is not stored here —
 * it is looked up from the career by club id on resume.
 */
@Serializable
data class TeamInputSnapshot(
    val clubId: Long,
    val clubName: String,
    val reputation: Int,
    val tactics: Tactics,
    val startingXi: List<Long>,
    val substitutes: List<Long>,
    val captainId: Long? = null,
    val penaltyTakerId: Long? = null,
    val freeKickTakerId: Long? = null,
    val strengthMultiplier: Double = 1.0
) {
    fun toSelection(): TeamSelection = TeamSelection(
        startingXi = startingXi.mapIndexed { index, id -> LineupSlot(index, id) },
        substitutes = substitutes,
        captainId = captainId,
        penaltyTakerId = penaltyTakerId,
        freeKickTakerId = freeKickTakerId
    )
}

/**
 * Per-player accumulators carried by the rating tracker. Ratings are computed on
 * a 4.0-10.0 scale at full time, so only the raw counters need saving.
 */
@Serializable
data class RatingSnapshot(
    val playerId: Long,
    val isHome: Boolean,
    val goals: Int = 0,
    val assists: Int = 0,
    val yellows: Int = 0,
    val reds: Int = 0,
    val minutes: Int = 90,
    /** Effective ability at the time, so a restored rating matches an unbroken one. */
    val ability: Double = 0.0,
    val isGoalkeeper: Boolean = false
)
