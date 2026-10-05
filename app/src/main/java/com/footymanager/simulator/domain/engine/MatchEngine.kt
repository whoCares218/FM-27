package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.MatchEvent
import com.footymanager.simulator.domain.model.MatchEventType
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayerMatchRating
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.TeamMatchStats
import com.footymanager.simulator.domain.model.TeamSelection
import com.footymanager.simulator.domain.model.Formation
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** A substitution the manager has scripted for this match. */
data class PlannedSubstitution(
    val playerOffId: Long,
    val playerOnId: Long,
    /** Minute the change is made; 45 is treated as half time and is free of a window. */
    val minute: Int = 45
)

/** Inputs for one side of a match. */
data class MatchTeamInput(
    val clubId: Long,
    val clubName: String,
    val reputation: Int,
    val tactics: Tactics,
    val selection: TeamSelection,
    /** All players belonging to this club, keyed by id. */
    val squadById: Map<Long, Player>,
    /** Effective strength including the difficulty-scaled AI adjustment. */
    val strengthMultiplier: Double = 1.0,
    /** Manager-scripted substitutions, applied instead of the default pattern. */
    val plannedSubstitutions: List<PlannedSubstitution> = emptyList()
)

/** Captures the on-pitch selection and tactics of one side for persistence. */
fun MatchTeamInput.toSnapshot(): com.footymanager.simulator.domain.model.TeamInputSnapshot =
    com.footymanager.simulator.domain.model.TeamInputSnapshot(
        clubId = clubId,
        clubName = clubName,
        reputation = reputation,
        tactics = tactics,
        startingXi = selection.startingXi.map { it.playerId },
        substitutes = selection.substitutes,
        captainId = selection.captainId,
        penaltyTakerId = selection.penaltyTakerId,
        freeKickTakerId = selection.freeKickTakerId,
        strengthMultiplier = strengthMultiplier
    )

/**
 * Rebuilds a [MatchTeamInput] from a persisted snapshot using the career's
 * current squad, so player lookups (and their live fitness) stay authoritative.
 */
fun com.footymanager.simulator.domain.model.TeamInputSnapshot.toInput(
    squadById: Map<Long, Player>,
    fallbackMultiplier: Double = 1.0
): MatchTeamInput = MatchTeamInput(
    clubId = clubId,
    clubName = clubName,
    reputation = reputation,
    tactics = tactics,
    selection = toSelection(),
    squadById = squadById,
    strengthMultiplier = if (strengthMultiplier != 0.0) strengthMultiplier else fallbackMultiplier
)

data class SimulatedMatch(
    val homeGoals: Int,
    val awayGoals: Int,
    /** Goals scored in normal time only, excluding extra time. */
    val homeGoalsRegular: Int = homeGoals,
    val awayGoalsRegular: Int = awayGoals,
    val homeStats: TeamMatchStats,
    val awayStats: TeamMatchStats,
    val events: List<MatchEvent>,
    val ratings: List<PlayerMatchRating>,
    val playerOfTheMatchId: Long?,
    /** Per-15-minute momentum for the home side, -100..100. */
    val momentum: List<Int> = emptyList(),
    /** Set when a knockout tie needed a shootout. */
    val shootoutHome: Int? = null,
    val shootoutAway: Int? = null
)

/** The competition rules that shape a match. */
data class MatchRules(
    /** 90 for a league match, 120 when extra time can be played. */
    val allowExtraTime: Boolean = false,
    val allowShootout: Boolean = false,
    val maxSubstitutions: Int = 5,
    /** Separate in-match substitution opportunities; half time is free. */
    val maxSubstitutionWindows: Int = 3
) {
    companion object {
        val LEAGUE = MatchRules()
        val KNOCKOUT = MatchRules(
            allowExtraTime = true,
            allowShootout = true,
            maxSubstitutions = 5,
            maxSubstitutionWindows = 3
        )
    }
}

/**
 * The match simulation engine.
 *
 * The design goal is *structured randomness*: a stronger, better-organised team
 * creates more and better chances, but goals remain a stochastic process, so
 * upsets happen at a believable rate.
 *
 * Steps:
 *  1. Score both sides' attack/midfield/defence from their selected XI, tactics,
 *     fitness, form, morale and home advantage.
 *  2. Derive expected goals (xG) for each side from a Poisson-style model whose
 *     rate depends on the ratio of one side's attack to the other's defence.
 *  3. Draw the actual goal count from that rate.
 *  4. Generate shots, possession, cards, corners and injuries with rates that
 *     scale consistently with the goals and tactics.
 *  5. Attribute goals and assists to individual players by weighting on their
 *     attacking/creative contribution.
 */
object MatchEngine {

    private const val MINUTES = 90
    private const val HOME_ADVANTAGE = 0.34

    /** Average goals per team per match in the model, before any modifiers. */
    private const val BASE_XG = 1.16

    fun simulate(
        home: MatchTeamInput,
        away: MatchTeamInput,
        random: Random,
        homeAdvantage: Boolean = true,
        /** Reduces randomness so the player's decisions weigh more heavily. */
        determinism: Double = 1.0,
        rules: MatchRules = MatchRules.LEAGUE
    ): SimulatedMatch {
        val homePlayers = TeamStrengthCalculator.toMatchPlayers(home.squadById, home.selection, home.tactics.formation, home.tactics.playerInstructions)
        val awayPlayers = TeamStrengthCalculator.toMatchPlayers(away.squadById, away.selection, away.tactics.formation, away.tactics.playerInstructions)

        val homeStrength = applyMultiplier(
            TeamStrengthCalculator.build(home.clubId, homePlayers, home.reputation),
            home.strengthMultiplier
        )
        val awayStrength = applyMultiplier(
            TeamStrengthCalculator.build(away.clubId, awayPlayers, away.reputation),
            away.strengthMultiplier
        )

        val events = mutableListOf<MatchEvent>()

        // ---- Possession ----
        val possessionHome = computePossession(home, away, homeStrength, awayStrength, homeAdvantage)

        // ---- Expected goals ----
        val homeXg = expectedGoals(
            attack = homeStrength.attack,
            opponentDefence = awayStrength.defence,
            opponentKeeper = awayStrength.goalkeeping,
            tactics = home.tactics,
            possessionShare = possessionHome / 100.0,
            homeAdvantage = homeAdvantage
        )
        val awayXg = expectedGoals(
            attack = awayStrength.attack,
            opponentDefence = homeStrength.defence,
            opponentKeeper = homeStrength.goalkeeping,
            tactics = away.tactics,
            possessionShare = (100 - possessionHome) / 100.0,
            homeAdvantage = false
        )

        // Goals are drawn per half rather than as one flat Poisson draw. The break
        // is a natural point to let the scoreline shape the rest of the game: a
        // side that is behind commits more players forward and creates more (while
        // exposing itself), a comfortable leader manages the match and creates
        // less. That feedback, plus a small quality edge for the side in front, is
        // what produces realistic, autocorrelated football instead of independent
        // 90-minute goal counts.
        val homeFirstHalf = drawGoals(homeXg / 2.0, random, determinism)
        val awayFirstHalf = drawGoals(awayXg / 2.0, random, determinism)

        val homeSecondRate = secondHalfRate(homeXg, homeFirstHalf, awayFirstHalf)
        val awaySecondRate = secondHalfRate(awayXg, awayFirstHalf, homeFirstHalf)
        val homeGoals = homeFirstHalf + drawGoals(homeSecondRate, random, determinism)
        val awayGoals = awayFirstHalf + drawGoals(awaySecondRate, random, determinism)

        val ratingsTracker = RatingTracker()
        ratingsTracker.register(homePlayers, isHome = true)
        ratingsTracker.register(awayPlayers, isHome = false)

        val homeEvents = mutableListOf<MatchEvent>()
        val awayEvents = mutableListOf<MatchEvent>()

        events += MatchEvent(0, MatchEventType.KICK_OFF, home.clubId, detail = "Kick off at ${home.clubName}")

        // ---- Goals ----
        val homeGoalMinutes = drawMinutes(homeGoals, random)
        val awayGoalMinutes = drawMinutes(awayGoals, random)

        for (minute in homeGoalMinutes) {
            val scorer = pickAttacker(homePlayers, random)
            val assister = pickAssister(homePlayers, scorer, random)
            homeEvents += MatchEvent(
                minute = minute,
                type = MatchEventType.GOAL,
                clubId = home.clubId,
                playerId = scorer?.id,
                playerName = scorer?.name ?: "",
                secondaryPlayerId = assister?.id,
                secondaryPlayerName = assister?.name ?: "",
                detail = if (assister != null) "Assisted by ${assister.name}" else "Unassisted"
            )
            if (scorer != null) ratingsTracker.addGoal(scorer.id)
            if (assister != null) ratingsTracker.addAssist(assister.id)
        }

        for (minute in awayGoalMinutes) {
            val scorer = pickAttacker(awayPlayers, random)
            val assister = pickAssister(awayPlayers, scorer, random)
            awayEvents += MatchEvent(
                minute = minute,
                type = MatchEventType.GOAL,
                clubId = away.clubId,
                playerId = scorer?.id,
                playerName = scorer?.name ?: "",
                secondaryPlayerId = assister?.id,
                secondaryPlayerName = assister?.name ?: "",
                detail = if (assister != null) "Assisted by ${assister.name}" else "Unassisted"
            )
            if (scorer != null) ratingsTracker.addGoal(scorer.id)
            if (assister != null) ratingsTracker.addAssist(assister.id)
        }

        // ---- Cards ----
        val homeFouls = drawFouls(home.tactics, homeStrength, random)
        val awayFouls = drawFouls(away.tactics, awayStrength, random)

        val homeYellows = drawCards(homeFouls, home.tactics, random, isHome = true)
        val awayYellows = drawCards(awayFouls, away.tactics, random, isHome = false)

        repeat(homeYellows) {
            val booked = pickDefensivePlayer(homePlayers, random)
            homeEvents += MatchEvent(
                minute = random.nextInt(6, MINUTES + 1),
                type = MatchEventType.YELLOW_CARD,
                clubId = home.clubId,
                playerId = booked?.id,
                playerName = booked?.name ?: "",
                detail = "Booked for a foul"
            )
            if (booked != null) ratingsTracker.addYellow(booked.id)
        }
        repeat(awayYellows) {
            val booked = pickDefensivePlayer(awayPlayers, random)
            awayEvents += MatchEvent(
                minute = random.nextInt(6, MINUTES + 1),
                type = MatchEventType.YELLOW_CARD,
                clubId = away.clubId,
                playerId = booked?.id,
                playerName = booked?.name ?: "",
                detail = "Booked for a foul"
            )
            if (booked != null) ratingsTracker.addYellow(booked.id)
        }

        // Red cards are rare and slightly more likely for aggressive setups.
        val homeReds = drawReds(home.tactics, random)
        val awayReds = drawReds(away.tactics, random)
        repeat(homeReds) {
            val sentOff = pickDefensivePlayer(homePlayers, random)
            homeEvents += MatchEvent(
                minute = random.nextInt(25, MINUTES + 1),
                type = MatchEventType.RED_CARD,
                clubId = home.clubId,
                playerId = sentOff?.id,
                playerName = sentOff?.name ?: "",
                detail = "Sent off"
            )
            if (sentOff != null) ratingsTracker.addRed(sentOff.id)
        }
        repeat(awayReds) {
            val sentOff = pickDefensivePlayer(awayPlayers, random)
            awayEvents += MatchEvent(
                minute = random.nextInt(25, MINUTES + 1),
                type = MatchEventType.RED_CARD,
                clubId = away.clubId,
                playerId = sentOff?.id,
                playerName = sentOff?.name ?: "",
                detail = "Sent off"
            )
            if (sentOff != null) ratingsTracker.addRed(sentOff.id)
        }

        // ---- Injuries ----
        val homeInjuries = drawInjuries(home.tactics, homeFouls, random)
        val awayInjuries = drawInjuries(away.tactics, awayFouls, random)
        repeat(homeInjuries) {
            val victim = pickOutfielder(homePlayers, random)
            homeEvents += MatchEvent(
                minute = random.nextInt(10, MINUTES + 1),
                type = MatchEventType.INJURY,
                clubId = home.clubId,
                playerId = victim?.id,
                playerName = victim?.name ?: "",
                detail = "Forced off with an injury"
            )
        }
        repeat(awayInjuries) {
            val victim = pickOutfielder(awayPlayers, random)
            awayEvents += MatchEvent(
                minute = random.nextInt(10, MINUTES + 1),
                type = MatchEventType.INJURY,
                clubId = away.clubId,
                playerId = victim?.id,
                playerName = victim?.name ?: "",
                detail = "Forced off with an injury"
            )
        }

        // ---- Substitutions ----
        // Scripted changes take priority; otherwise a sensible default pattern is
        // used. Either way the modern rules are enforced: five players across
        // three in-match windows, with half-time changes not using a window.
        val homeSubs = substitutionsFor(home, homePlayers, random, rules)
        val awaySubs = substitutionsFor(away, awayPlayers, random, rules)
        homeEvents += homeSubs
        awayEvents += awaySubs

        // Players who came off or on only played part of the match, which must be
        // reflected in their match rating's minutes.
        val totalMinutes = if (rules.allowExtraTime) 120 else 90
        applySubMinutes(ratingsTracker, homeSubs, totalMinutes)
        applySubMinutes(ratingsTracker, awaySubs, totalMinutes)

        // ---- Stoppage time ----
        val firstHalfStoppage = 1 + random.nextInt(0, 4)
        val secondHalfStoppage = 2 + random.nextInt(0, 6)

        // ---- Shots, corners and pass accuracy derived from the same model ----
        val homeShots = shotsFor(homeGoals, homeXg, home.tactics, random)
        val awayShots = shotsFor(awayGoals, awayXg, away.tactics, random)
        val homeOnTarget = onTargetFor(homeGoals, homeShots, random)
        val awayOnTarget = onTargetFor(awayGoals, awayShots, random)

        val homeCorners = cornersFor(homeXg, possessionHome, random)
        val awayCorners = cornersFor(awayXg, 100 - possessionHome, random)

        val homePassAccuracy = passAccuracy(home.tactics, possessionHome, homeStrength, awayStrength, random)
        val awayPassAccuracy = passAccuracy(away.tactics, 100 - possessionHome, awayStrength, homeStrength, random)

        // ---- Assemble the timeline ----
        val halfTime = MatchEvent(
            minute = 45 + firstHalfStoppage,
            type = MatchEventType.HALF_TIME,
            clubId = home.clubId,
            detail = "Half time: ${home.clubName} ${homeGoalMinutes.count { it <= 45 }} - " +
                "${awayGoalMinutes.count { it <= 45 }} ${away.clubName} (+$firstHalfStoppage)"
        )
        val fullTimeMinute = 90 + secondHalfStoppage

        val allTimeline = (homeEvents + awayEvents)
            .sortedBy { it.minute }
            .toMutableList()
        allTimeline.add(halfTime)

        // ---- Extra time for knockout ties level after 90 minutes ----
        var homeEtGoals = 0
        var awayEtGoals = 0
        var shootoutHome: Int? = null
        var shootoutAway: Int? = null
        var extraTimePlayed = false

        if (rules.allowExtraTime && homeGoals == awayGoals) {
            extraTimePlayed = true
            allTimeline.add(
                MatchEvent(
                    minute = fullTimeMinute,
                    type = MatchEventType.EXTRA_TIME_START,
                    clubId = home.clubId,
                    detail = "Level after 90 minutes - extra time to be played"
                )
            )
            // Extra time produces roughly a third of a normal half's chances.
            val etHomeXg = homeXg * 0.30
            val etAwayXg = awayXg * 0.30
            homeEtGoals = drawGoals(etHomeXg, random, determinism).coerceAtMost(3)
            awayEtGoals = drawGoals(etAwayXg, random, determinism).coerceAtMost(3)

            repeat(homeEtGoals) {
                val scorer = pickAttacker(homePlayers, random)
                val assister = pickAssister(homePlayers, scorer, random)
                allTimeline.add(
                    MatchEvent(
                        minute = random.nextInt(91, 121),
                        type = MatchEventType.GOAL,
                        clubId = home.clubId,
                        playerId = scorer?.id,
                        playerName = scorer?.name ?: "",
                        secondaryPlayerId = assister?.id,
                        secondaryPlayerName = assister?.name ?: "",
                        detail = "Extra time goal"
                    )
                )
                if (scorer != null) ratingsTracker.addGoal(scorer.id)
                if (assister != null) ratingsTracker.addAssist(assister.id)
            }
            repeat(awayEtGoals) {
                val scorer = pickAttacker(awayPlayers, random)
                val assister = pickAssister(awayPlayers, scorer, random)
                allTimeline.add(
                    MatchEvent(
                        minute = random.nextInt(91, 121),
                        type = MatchEventType.GOAL,
                        clubId = away.clubId,
                        playerId = scorer?.id,
                        playerName = scorer?.name ?: "",
                        secondaryPlayerId = assister?.id,
                        secondaryPlayerName = assister?.name ?: "",
                        detail = "Extra time goal"
                    )
                )
                if (scorer != null) ratingsTracker.addGoal(scorer.id)
                if (assister != null) ratingsTracker.addAssist(assister.id)
            }
            allTimeline.add(
                MatchEvent(
                    minute = 120,
                    type = MatchEventType.EXTRA_TIME_END,
                    clubId = home.clubId,
                    detail = "End of extra time: ${home.clubName} ${homeGoals + homeEtGoals} - " +
                        "${awayGoals + awayEtGoals} ${away.clubName}"
                )
            )

            // ---- Penalty shootout if still level ----
            if (rules.allowShootout && homeGoals + homeEtGoals == awayGoals + awayEtGoals) {
                val (sh, sa) = ChampionsLeagueEngine.simulateShootout(random)
                shootoutHome = sh
                shootoutAway = sa
                var minute = 121
                repeat(maxOf(sh, sa)) {
                    minute++
                    allTimeline.add(
                        MatchEvent(
                            minute = minute,
                            type = MatchEventType.PENALTY_SHOOTOUT_GOAL,
                            clubId = home.clubId,
                            detail = "Shootout: ${home.clubName} ${minOf(it + 1, sh)} - " +
                                "${minOf(it + 1, sa)} ${away.clubName}"
                        )
                    )
                }
            }
        }

        val fullTime = MatchEvent(
            minute = fullTimeMinute,
            type = MatchEventType.FULL_TIME,
            clubId = home.clubId,
            detail = "Full time: ${home.clubName} ${homeGoals + homeEtGoals} - " +
                "${awayGoals + awayEtGoals} ${away.clubName}"
        )
        allTimeline.add(fullTime)
        val timeline = allTimeline.sortedBy { it.minute }
        events += timeline

        val homeStats = TeamMatchStats(
            clubId = home.clubId,
            goals = homeGoals + homeEtGoals,
            shots = homeShots,
            shotsOnTarget = homeOnTarget,
            possession = possessionHome,
            fouls = homeFouls,
            corners = homeCorners,
            yellowCards = homeYellows,
            redCards = homeReds,
            passAccuracy = homePassAccuracy,
            expectedGoals = round1(homeXg)
        )
        val awayStats = TeamMatchStats(
            clubId = away.clubId,
            goals = awayGoals + awayEtGoals,
            shots = awayShots,
            shotsOnTarget = awayOnTarget,
            possession = 100 - possessionHome,
            fouls = awayFouls,
            corners = awayCorners,
            yellowCards = awayYellows,
            redCards = awayReds,
            passAccuracy = awayPassAccuracy,
            expectedGoals = round1(awayXg)
        )

        // ---- Player ratings ----
        val ratings = ratingsTracker.finish(
            homeStats = homeStats,
            awayStats = awayStats,
            homeStrength = homeStrength,
            awayStrength = awayStrength,
            homeSelection = home.selection,
            awaySelection = away.selection
        )

        val motm = ratings.maxByOrNull { it.rating }?.playerId

        // ---- Momentum: a per-15-minute read of who is on top ----
        val momentum = buildMomentum(
            homeXg = homeXg,
            awayXg = awayXg,
            homeGoals = homeGoals + homeEtGoals,
            awayGoals = awayGoals + awayEtGoals,
            homeStrength = homeStrength,
            awayStrength = awayStrength,
            random = random
        )

        return SimulatedMatch(
            homeGoals = homeGoals + homeEtGoals,
            awayGoals = awayGoals + awayEtGoals,
            homeGoalsRegular = homeGoals,
            awayGoalsRegular = awayGoals,
            homeStats = homeStats,
            awayStats = awayStats,
            events = events,
            ratings = ratings,
            playerOfTheMatchId = motm,
            momentum = momentum,
            shootoutHome = shootoutHome,
            shootoutAway = shootoutAway
        )
    }

    /**
     * Momentum is a coarse, presentation-friendly signal of which side is on top
     * across the match, on a -100..100 scale where positive favours the home team.
     */
    internal fun buildMomentum(
        homeXg: Double,
        awayXg: Double,
        homeGoals: Int,
        awayGoals: Int,
        homeStrength: TeamStrength,
        awayStrength: TeamStrength,
        random: Random
    ): List<Int> {
        val base = (homeStrength.overall - awayStrength.overall) * 1.2
        val goalSwing = (homeGoals - awayGoals) * 8.0
        return (0 until 6).map { i ->
            // Momentum swings through the match rather than staying flat.
            val wave = kotlin.math.sin(i * 1.1) * 14.0
            val value = base + goalSwing + wave + random.nextDouble(-10.0, 10.0)
            value.coerceIn(-100.0, 100.0).roundToInt()
        }
    }

    // ---------------------------------------------------------------- helpers

    internal fun applyMultiplier(strength: TeamStrength, multiplier: Double): TeamStrength {
        if (multiplier == 1.0) return strength
        return strength.copy(
            overall = strength.overall * multiplier,
            attack = strength.attack * multiplier,
            midfield = strength.midfield * multiplier,
            defence = strength.defence * multiplier,
            goalkeeping = strength.goalkeeping * multiplier
        )
    }

    internal fun computePossession(
        home: MatchTeamInput,
        away: MatchTeamInput,
        homeStrength: TeamStrength,
        awayStrength: TeamStrength,
        homeAdvantage: Boolean
    ): Int {
        val homeMid = homeStrength.midfield * home.tactics.possessionMultiplier
        val awayMid = awayStrength.midfield * away.tactics.possessionMultiplier
        val adv = if (homeAdvantage) 1.05 else 1.0
        val total = (homeMid * adv) + awayMid
        if (total <= 0.0) return 50
        val share = (homeMid * adv) / total
        // Compress toward 50% so possession never looks absurd.
        val compressed = 0.5 + (share - 0.5) * 0.72
        return (compressed * 100).roundToInt().coerceIn(28, 72)
    }

    /**
     * Poisson-style expected goals. The core is the ratio of a side's attack to
     * the opponent's defence+keeper, which makes squad quality the dominant
     * factor while still allowing tactical swings.
     */
    internal fun expectedGoals(
        attack: Double,
        opponentDefence: Double,
        opponentKeeper: Double,
        tactics: Tactics,
        possessionShare: Double,
        homeAdvantage: Boolean
    ): Double {
        val defensiveResistance = opponentDefence * 0.72 + opponentKeeper * 0.28
        val ratio = (attack.coerceAtLeast(20.0)) / defensiveResistance.coerceAtLeast(20.0)
        // Elasticity below 1 compresses the gap between a strong attack and a weak
        // defence, so a mismatch inflates the expected goals without letting a
        // routine league game turn into a cricket score.
        val qualityFactor = ratio.pow(1.32)

        var xg = BASE_XG * qualityFactor
        xg *= tactics.mentality.attackModifier
        xg *= tactics.chanceQualityMultiplier
        xg *= tactics.chanceVolumeMultiplier
        // Dominating the ball helps, but with diminishing returns.
        xg *= 0.86 + possessionShare * 0.28
        // A high line against a strong attack invites chances; a deep line denies them.
        xg *= 0.92 + (tactics.defensiveLine.pressingHeight - 1.0) * 0.25

        if (homeAdvantage) xg *= (1.0 + HOME_ADVANTAGE * 0.35)

        // Cap the per-team expectation so no single fixture can run away with the
        // score even when a top side meets a struggling one.
        return xg.coerceIn(0.18, 3.0)
    }

    /**
     * The expected-goals rate for one side's second half, adjusted for the
     * half-time scoreline.
     *
     * A chasing side raises its rate (chasing) while the leader eases off
     * (managing the game); the leading side still keeps a small edge because the
     * better team is usually the one in front. The effect is capped so it shifts
     * the shape of the distribution without dominating the underlying quality
     * model.
     */
    internal fun secondHalfRate(
        baseXg: Double,
        ownFirstHalfGoals: Int,
        opponentFirstHalfGoals: Int
    ): Double {
        val margin = ownFirstHalfGoals - opponentFirstHalfGoals
        // Chasing raises the rate but tapers: a side three down does not keep
        // doubling its threat, and a rout is damped rather than compounded.
        val chasing = (-margin).coerceIn(0, 3) * 0.15
        val leading = margin.coerceIn(0, 3) * 0.13
        val factor = (1.0 + chasing - leading).coerceIn(0.72, 1.45)
        return (baseXg / 2.0) * factor
    }

    /**
     * Draws a goal count from the expected-goals rate. [determinism] above 1.0
     * flattens the distribution toward the expectation (used for Easy difficulty
     * so player decisions matter more), below 1.0 adds variance.
     */
    internal fun drawGoals(xg: Double, random: Random, determinism: Double): Int {
        val d = determinism.coerceIn(0.6, 1.8)
        // Knuth's Poisson sampler, with the rate scaled by determinism.
        val lambda = xg.pow(1.0 / d) * if (d > 1.0) xg.pow(1.0 - 1.0 / d) else 1.0
        val effectiveLambda = lambda.coerceIn(0.15, 5.0)
        val limit = exp(-effectiveLambda)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= random.nextDouble()
        } while (p > limit && k < 12)
        return (k - 1).coerceAtLeast(0)
    }

    /** Goal minutes, spread realistically across the match. */
    internal fun drawMinutes(count: Int, random: Random): List<Int> =
        (0 until count).map {
            // Slight bias toward the later stages of each half.
            val half = if (random.nextDouble() < 0.54) 1 else 2
            val minute = if (half == 1) {
                (random.nextDouble().pow(0.85) * 45).toInt() + 1
            } else {
                46 + (random.nextDouble().pow(0.85) * 49).toInt()
            }
            minute.coerceIn(1, MINUTES + 6)
        }.sorted()

    internal fun pickAttacker(players: List<MatchPlayer>, random: Random): MatchPlayer? {
        val candidates = players.filter { !it.isGoalkeeper }
        if (candidates.isEmpty()) return null
        val weights = candidates.map { max(0.05, it.attackWeight * it.confidenceFactor) }
        return weightedPick(candidates, weights, random)
    }

    internal fun pickAssister(
        players: List<MatchPlayer>,
        scorer: MatchPlayer?,
        random: Random
    ): MatchPlayer? {
        val candidates = players.filter { !it.isGoalkeeper && it.id != scorer?.id }
        if (candidates.isEmpty()) return null
        // Roughly two thirds of goals are assisted.
        if (random.nextDouble() > 0.68) return null
        val weights = candidates.map { max(0.05, it.creativityWeight) }
        return weightedPick(candidates, weights, random)
    }

    internal fun pickDefensivePlayer(players: List<MatchPlayer>, random: Random): MatchPlayer? {
        val candidates = players.filter { !it.isGoalkeeper }
        if (candidates.isEmpty()) return null
        // Fouls cluster among defenders and midfielders.
        val weights = candidates.map { max(0.1, it.defensiveWeight) }
        return weightedPick(candidates, weights, random)
    }

    internal fun pickOutfielder(players: List<MatchPlayer>, random: Random): MatchPlayer? {
        val candidates = players.filter { !it.isGoalkeeper }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    internal fun <T> weightedPick(items: List<T>, weights: List<Double>, random: Random): T? {
        if (items.isEmpty()) return null
        val total = weights.sum()
        if (total <= 0.0) return items[random.nextInt(items.size)]
        var roll = random.nextDouble() * total
        for (i in items.indices) {
            roll -= weights[i]
            if (roll <= 0.0) return items[i]
        }
        return items.last()
    }

    internal fun drawFouls(tactics: Tactics, strength: TeamStrength, random: Random): Int {
        val base = 11.0
        val lineFactor = 0.9 + tactics.defensiveLine.pressingHeight * 0.12
        val mean = base * tactics.foulMultiplier * lineFactor
        return poisson(mean, random).coerceIn(3, 26)
    }

    internal fun drawCards(fouls: Int, tactics: Tactics, random: Random, isHome: Boolean): Int {
        // Roughly one booking per six fouls, nudged by mentality and home bias.
        val base = fouls / 6.0
        val aggression = if (tactics.mentality == com.footymanager.simulator.domain.model.Mentality.VERY_ATTACKING) 1.15 else 1.0
        val homeBias = if (isHome) 0.92 else 1.08
        return poisson(base * aggression * homeBias, random).coerceIn(0, 6)
    }

    internal fun drawReds(tactics: Tactics, random: Random): Int {
        val p = 0.035 * tactics.tempo.errorRate
        return if (random.nextDouble() < p) 1 else 0
    }

    internal fun drawInjuries(tactics: Tactics, fouls: Int, random: Random): Int {
        // High pressing and fast tempo raise injury risk; fouls suffered raise it further.
        var p = 0.09 * tactics.style.fatigueBias * tactics.tempo.errorRate
        p += fouls * 0.0016
        return if (random.nextDouble() < p) 1 else 0
    }

    internal fun shotsFor(goals: Int, xg: Double, tactics: Tactics, random: Random): Int {
        val base = goals * 2.2 + xg * 7.4
        val volume = tactics.tempo.chanceVolume
        val shots = base * volume + random.nextDouble(-2.0, 2.5)
        return shots.roundToInt().coerceAtLeast(goals).coerceIn(2, 34)
    }

    internal fun onTargetFor(goals: Int, shots: Int, random: Random): Int {
        val ratio = 0.34 + random.nextDouble(-0.05, 0.08)
        return (shots * ratio).roundToInt().coerceIn(goals, shots)
    }

    internal fun cornersFor(xg: Double, possession: Int, random: Random): Int {
        val base = 3.2 + xg * 2.1 + (possession - 50) * 0.035
        return poisson(base.coerceAtLeast(0.8), random).coerceIn(0, 16)
    }

    internal fun passAccuracy(
        tactics: Tactics,
        possession: Int,
        strength: TeamStrength,
        opponent: TeamStrength,
        random: Random
    ): Int {
        var acc = 74.0
        acc += tactics.style.possessionBias * 3.4 - 3.4
        acc += (strength.midfield - opponent.midfield) * 0.18
        acc += (possession - 50) * 0.08
        acc += random.nextDouble(-2.2, 2.2)
        return acc.roundToInt().coerceIn(58, 94)
    }

    internal fun poisson(mean: Double, random: Random): Int {
        if (mean <= 0.0) return 0
        val limit = exp(-mean)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= random.nextDouble()
        } while (p > limit && k < 30)
        return k - 1
    }

    internal fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0

    /** Converts substitution events into minutes played for each involved player. */
    private fun applySubMinutes(
        tracker: RatingTracker,
        subs: List<MatchEvent>,
        totalMinutes: Int
    ) {
        for (event in subs) {
            val onId = event.playerId ?: continue
            val offId = event.secondaryPlayerId ?: continue
            tracker.setMinutes(offId, event.minute)
            tracker.setMinutes(onId, (totalMinutes - event.minute).coerceAtLeast(1))
        }
    }

    /**
     * Builds the substitution events for one side.
     *
     * Modern rules are enforced: at most five players are introduced, and only
     * three in-match opportunities may be used. Half-time changes (minute 45) are
     * free and do not consume one of those three windows.
     *
     * When the manager has scripted changes they are used; otherwise a sensible
     * default pattern is generated so AI teams still rotate their squads.
     */
    private fun substitutionsFor(
        input: MatchTeamInput,
        onPitch: List<MatchPlayer>,
        random: Random,
        rules: MatchRules
    ): List<MatchEvent> {
        val bench = input.selection.substitutes.mapNotNull { input.squadById[it] }
        if (bench.isEmpty()) return emptyList()

        val events = mutableListOf<MatchEvent>()
        val used = mutableSetOf<Long>()
        val onPitchIds = onPitch.map { it.id }.toMutableSet()

        val scripted = input.plannedSubstitutions
            .filter { it.playerOffId in onPitchIds && it.playerOnId !in onPitchIds }
            .sortedBy { it.minute }

        val changes: List<Pair<Long, Long>> = if (scripted.isNotEmpty()) {
            scripted.map { it.playerOffId to it.playerOnId }
        } else {
            // Default AI pattern: rotate two or three players, mostly late on.
            val count = 1 + random.nextInt(3)
            (0 until count).mapNotNull {
                val off = onPitch.filter { !it.isGoalkeeper && it.id in onPitchIds && it.id !in used }
                    .randomOrNull(random) ?: return@mapNotNull null
                val on = bench.filter { it.id !in used && it.id !in onPitchIds }
                    .randomOrNull(random) ?: return@mapNotNull null
                off.id to on.id
            }
        }

        var playersUsed = 0
        var windowsUsed = 0
        // Track the minute of each window so several changes in the same window
        // only consume one opportunity.
        var currentWindowMinute = -1

        for ((offId, onId) in changes) {
            if (playersUsed >= rules.maxSubstitutions) break
            if (offId !in onPitchIds || onId in onPitchIds) continue

            val minute = if (scripted.isNotEmpty()) {
                scripted.firstOrNull { it.playerOffId == offId }?.minute ?: 60
            } else {
                random.nextInt(55, 88)
            }

            val isHalfTime = minute == 45
            if (!isHalfTime) {
                // A change at a new minute needs a fresh window; several changes
                // at the same minute share one.
                if (currentWindowMinute != minute) {
                    if (windowsUsed >= rules.maxSubstitutionWindows) continue
                    windowsUsed++
                    currentWindowMinute = minute
                }
            }

            val on = input.squadById[onId] ?: continue
            val off = input.squadById[offId] ?: continue
            used += offId
            used += onId
            onPitchIds.remove(offId)
            onPitchIds.add(onId)
            playersUsed++

            events += MatchEvent(
                minute = minute,
                type = MatchEventType.SUBSTITUTION,
                clubId = input.clubId,
                playerId = onId,
                playerName = on.name,
                secondaryPlayerId = offId,
                secondaryPlayerName = off.name,
                detail = if (isHalfTime) "${on.name} replaces ${off.name} (half time)"
                else "${on.name} replaces ${off.name}"
            )
        }
        return events
    }
}

private fun <T> List<T>.randomOrNull(random: Random): T? =
    if (isEmpty()) null else this[random.nextInt(size)]
