package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.InjuryType
import com.footymanager.simulator.domain.model.MatchEvent
import com.footymanager.simulator.domain.model.MatchEventType
import com.footymanager.simulator.domain.model.MatchResult
import com.footymanager.simulator.domain.model.TeamMatchStats
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The live, step-by-step match engine.
 *
 * Both presentation modes use this single engine so results stay consistent:
 *  - **Play Match** advances the state minute by minute over roughly ten real
 *    minutes, so the manager watches the game unfold.
 *  - **Quick Sim** advances in large chunks, compressing the same ninety minutes
 *    into about ten seconds.
 *
 * Neither mode computes the whole match up front. Each [advance] consumes a slice
 * of the clock, draws the incidents that happen inside that slice and mutates the
 * score. Nothing about the second half exists until the manager has taken their
 * half-time break, which is what makes tactical changes and substitutions
 * genuinely affect the outcome rather than merely decorating a pre-decided result.
 *
 * The model is a minute-resolution Poisson race between the two sides. Rates are
 * built from the same quality model used elsewhere: squad strength, fitness,
 * morale and form, home advantage, tactical setup, red cards and fatigue. Each
 * tick is a handful of arithmetic operations, so it stays cheap on a mid-range
 * phone.
 */
class ProgressiveMatchEngine(
    val home: MatchTeamInput,
    val away: MatchTeamInput,
    private val random: Random,
    private val homeAdvantage: Boolean = true,
    private val determinism: Double = 1.0,
    private val rules: MatchRules = MatchRules.LEAGUE,
    /** Draws the penalty shootout when extra time ends level. */
    private val shootout: (Random) -> Pair<Int, Int> = { ChampionsLeagueEngine.simulateShootout(it) }
) {

    // ------------------------------------------------------------------ state

    private var phase = MatchPhase.FIRST_HALF
    private val totalMinutes = if (rules.allowExtraTime) 120 else 90

    private var minute = 0

    private var homeGoals = 0
    private var awayGoals = 0
    private var homeGoalsRegular = 0
    private var awayGoalsRegular = 0
    private var homeEtGoals = 0
    private var awayEtGoals = 0

    private var homeShots = 0
    private var awayShots = 0
    private var homeOnTarget = 0
    private var awayOnTarget = 0
    private var homeFouls = 0
    private var awayFouls = 0
    private var homeCorners = 0
    private var awayCorners = 0
    private var homeYellows = 0
    private var awayYellows = 0
    private var homeReds = 0
    private var awayReds = 0
    private var homePossessionSum = 0.0
    private var possessionSamples = 0

    /**
     * Live "domination" signal on a 0..100 scale where 50 is an even contest.
     * It blends possession, shot volume, dangerous attacks and the on-pitch
     * quality gap, then decays toward the middle so a single incident cannot
     * flip the bar. The UI interpolates toward this value, so it only needs to
     * be recomputed once per simulated minute.
     */
    private var homeDomination: Double = 50.0

    /** Dangerous attacks (entries into the final third) per side. */
    private var homeDangerous = 0
    private var awayDangerous = 0

    private var secondHalfStoppage = 0
    private var firstHalfStoppageValue = 0
    private var shootoutHome: Int? = null
    private var shootoutAway: Int? = null

    private val events = mutableListOf<MatchEvent>()
    private val tracker = RatingTracker()

    /** Minutes played by players who have left the pitch. */
    private val offMinutes = mutableMapOf<Long, Int>()

    /** Minute each substitute came on at. */
    private val onAtMinute = mutableMapOf<Long, Int>()

    /** Club ids currently reduced to ten men. */
    private val sentOffClubs = mutableSetOf<Long>()

    private val subsMade = mutableMapOf<Long, Int>()
    private val windowsUsed = mutableMapOf<Long, Int>()

    /** The inputs in force for each side, updated by half-time changes. */
    private var activeHome: MatchTeamInput = home
    private var activeAway: MatchTeamInput = away

    init {
        tracker.register(
            TeamStrengthCalculator.toMatchPlayers(home.squadById, home.selection, home.tactics.formation),
            isHome = true
        )
        tracker.register(
            TeamStrengthCalculator.toMatchPlayers(away.squadById, away.selection, away.tactics.formation),
            isHome = false
        )
        events += MatchEvent(0, MatchEventType.KICK_OFF, home.clubId, detail = "Kick off at ${home.clubName}")
    }

    // --------------------------------------------------------------- accessors

    val isHalfTime: Boolean get() = phase == MatchPhase.HALF_TIME
    val isFinished: Boolean get() = phase == MatchPhase.FINISHED
    val isExtraTime: Boolean
        get() = phase == MatchPhase.EXTRA_TIME_FIRST || phase == MatchPhase.EXTRA_TIME_SECOND

    val currentPhase: MatchPhase get() = phase

    /** The display clock: the live minute, or the period marker during pauses. */
    val clockMinute: Int
        get() = when (phase) {
            MatchPhase.HALF_TIME, MatchPhase.EXTRA_TIME_BREAK -> 45
            MatchPhase.FINISHED -> 90
            else -> minute
        }

    /** 0..1 progress through the match, for a progress indicator. */
    val progress: Float
        get() = when (phase) {
            MatchPhase.FIRST_HALF -> (minute / 90f).coerceIn(0f, 0.5f)
            MatchPhase.HALF_TIME -> 0.5f
            MatchPhase.SECOND_HALF -> (minute / 90f).coerceIn(0.5f, 1f)
            MatchPhase.FINISHED -> 1f
            else -> (minute / totalMinutes.toFloat()).coerceIn(0.5f, 1f)
        }

    fun score(): Pair<Int, Int> = homeGoals to awayGoals

    fun eventLog(): List<MatchEvent> = events.sortedBy { it.minute }

    fun substitutionsMade(clubId: Long): Int = subsMade[clubId] ?: 0

    fun windowsConsumed(clubId: Long): Int = windowsUsed[clubId] ?: 0

    /**
     * The live domination state as a pair of percentages that always sum to 100.
     * The home value is returned first. This is the raw engine signal; the UI
     * animates toward it rather than reading it every frame.
     */
    fun domination(): Pair<Int, Int> {
        val home = homeDomination.roundToInt().coerceIn(3, 97)
        return home to (100 - home)
    }

    // --------------------------------------------------------------- control

    /**
     * Advances the match by [minutes] of game time, generating every incident in
     * the window. Repeated calls drive Play Match (small slices) and Quick Sim
     * (large slices) through the same code path. The call stops early when a
     * period boundary (half time or full time) is reached, so the clock never
     * runs on while the manager is making decisions.
     */
    fun advance(minutes: Int = 1): List<MatchEvent> {
        if (phase == MatchPhase.HALF_TIME || phase == MatchPhase.FINISHED) return emptyList()
        val produced = mutableListOf<MatchEvent>()
        var remaining = minutes.coerceAtLeast(1)

        while (remaining > 0) {
            val endOfPeriod = endOfCurrentPeriod()
            if (minute >= endOfPeriod) {
                produced += transitionFromPeriod()
                break
            }
            // Always step a single game minute so every incident carries the correct
            // clock time, whether the caller asked for one minute or forty-five.
            produced += simulateMinute()
            minute += 1
            remaining -= 1
            if (minute >= endOfPeriod) {
                produced += transitionFromPeriod()
                break
            }
        }
        events += produced
        return produced
    }

    /** Leaves the half-time interval and starts the second half. */
    fun beginSecondHalf(): List<MatchEvent> {
        if (phase != MatchPhase.HALF_TIME) return emptyList()
        phase = MatchPhase.SECOND_HALF
        minute = 45
        secondHalfStoppage = 2 + random.nextInt(0, 6)
        val out = listOf(
            MatchEvent(45, MatchEventType.INFO, home.clubId, detail = "Second half under way")
        )
        events += out
        return out
    }

    /** Continues into extra time from a level knockout tie. */
    fun beginExtraTime(): List<MatchEvent> {
        if (phase != MatchPhase.EXTRA_TIME_BREAK) return emptyList()
        phase = MatchPhase.EXTRA_TIME_FIRST
        minute = 90
        val out = listOf(
            MatchEvent(
                90, MatchEventType.EXTRA_TIME_START, home.clubId,
                detail = "Level after ninety minutes - extra time"
            )
        )
        events += out
        return out
    }

    /** True when a level knockout tie still needs extra time played. */
    val needsExtraTime: Boolean get() = phase == MatchPhase.EXTRA_TIME_BREAK

    /**
     * Applies the manager's half-time changes. The updated inputs are used for
     * every subsequent minute, so tactical changes really do reshape the rest of
     * the game.
     */
    fun applyHalfTimeChanges(
        homeInput: MatchTeamInput?,
        awayInput: MatchTeamInput?
    ): List<MatchEvent> {
        val out = mutableListOf<MatchEvent>()
        homeInput?.let {
            activeHome = it
            out += applySubstitutions(it, atHalfTime = true)
        }
        awayInput?.let {
            activeAway = it
            out += applySubstitutions(it, atHalfTime = true)
        }
        return out
    }

    /** Records a mid-match substitution the manager has queued (not at the break). */
    fun applyLiveSubstitution(updated: MatchTeamInput, isHome: Boolean): List<MatchEvent> {
        if (isHome) activeHome = updated else activeAway = updated
        return applySubstitutions(updated, atHalfTime = false)
    }

    /**
     * Applies a single live substitution immediately.
     *
     * Unlike [applyLiveSubstitution], which replays queued changes, this records
     * exactly one change: it consumes a substitution and, outside half time, a
     * window, then books the minutes. It refuses changes that are illegal under
     * the competition rules and returns an empty list when refused.
     */
    fun applyDirectSubstitution(
        updated: MatchTeamInput,
        isHome: Boolean,
        playerOffId: Long,
        playerOnId: Long,
        atHalfTime: Boolean = false
    ): List<MatchEvent> {
        val clubId = updated.clubId
        // Validate against the players actually on the pitch right now, not the
        // proposed XI, otherwise the outgoing player would already look absent.
        val current = if (isHome) activeHome else activeAway
        val onPitch = playersOf(current).map { it.id }.toMutableSet()
        if (playerOffId !in onPitch || playerOnId in onPitch) return emptyList()
        if ((subsMade[clubId] ?: 0) >= rules.maxSubstitutions) return emptyList()
        if (!atHalfTime) {
            val used = windowsUsed[clubId] ?: 0
            if (used >= rules.maxSubstitutionWindows) return emptyList()
            windowsUsed[clubId] = used + 1
        }

        val on = updated.squadById[playerOnId] ?: return emptyList()
        val off = updated.squadById[playerOffId] ?: return emptyList()
        subsMade[clubId] = (subsMade[clubId] ?: 0) + 1
        val changeMinute = if (atHalfTime) 45 else minute.coerceAtLeast(1)
        offMinutes[playerOffId] = changeMinute
        onAtMinute[playerOnId] = changeMinute
        if (isHome) activeHome = updated else activeAway = updated

        val event = MatchEvent(
            minute = changeMinute,
            type = MatchEventType.SUBSTITUTION,
            clubId = clubId,
            playerId = playerOnId,
            playerName = on.name,
            secondaryPlayerId = playerOffId,
            secondaryPlayerName = off.name,
            detail = "${on.name} replaces ${off.name}"
        )
        events += event
        return listOf(event)
    }

    /** The input currently in force for a side. */
    fun activeInput(isHome: Boolean): MatchTeamInput = if (isHome) activeHome else activeAway

    /** Swaps in updated tactics mid-match without disturbing the current XI. */
    fun updateActiveInput(updated: MatchTeamInput, isHome: Boolean) {
        if (isHome) activeHome = updated else activeAway = updated
    }

    /**
     * Adopts a new shape mid-match. A formation change usually keeps the same
     * eleven (positions shift), but it can also swap personnel. Any player who
     * leaves the pitch is booked out and any replacement booked in, so ratings and
     * minutes stay correct. Returns the substitution events this produced.
     */
    fun applyFormationChange(updated: MatchTeamInput, isHome: Boolean): List<MatchEvent> {
        val current = if (isHome) activeHome else activeAway
        val clubId = updated.clubId
        val newIds = updated.selection.startingXi.map { it.playerId }
        if (newIds.isEmpty()) {
            updateActiveInput(updated, isHome)
            return emptyList()
        }
        val currentIds = playersOf(current).map { it.id }
        val off = currentIds.filter { it !in newIds }
        val on = newIds.filter { it !in currentIds }
        val out = mutableListOf<MatchEvent>()
        val changeMinute = minute.coerceAtLeast(1)

        off.zip(on).forEach { (offId, onId) ->
            offMinutes[offId] = changeMinute
            onAtMinute[onId] = changeMinute
            subsMade[clubId] = (subsMade[clubId] ?: 0) + 1
            val onPlayer = updated.squadById[onId]
            val offPlayer = updated.squadById[offId]
            if (onPlayer != null && offPlayer != null) {
                out += MatchEvent(
                    minute = changeMinute,
                    type = MatchEventType.SUBSTITUTION,
                    clubId = clubId,
                    playerId = onId,
                    playerName = onPlayer.name,
                    secondaryPlayerId = offId,
                    secondaryPlayerName = offPlayer.name,
                    detail = "${onPlayer.name} replaces ${offPlayer.name} (change of shape)"
                )
            }
        }
        updateActiveInput(updated, isHome)
        events += out
        return out
    }

    // ------------------------------------------------------------- simulation

    private fun endOfCurrentPeriod(): Int = when (phase) {
        MatchPhase.FIRST_HALF -> 45 + firstHalfStoppage()
        MatchPhase.SECOND_HALF -> 90 + secondHalfStoppage
        MatchPhase.EXTRA_TIME_FIRST -> 105
        MatchPhase.EXTRA_TIME_SECOND -> 120
        else -> minute
    }

    private fun firstHalfStoppage(): Int {
        if (firstHalfStoppageValue == 0) firstHalfStoppageValue = 1 + random.nextInt(0, 4)
        return firstHalfStoppageValue
    }

    private fun simulateMinute(): List<MatchEvent> {
        val out = mutableListOf<MatchEvent>()
        val hInput = activeHome
        val aInput = activeAway

        val hStrength = effectiveStrength(hInput)
        val aStrength = effectiveStrength(aInput)

        val hPossession = MatchEngine.computePossession(hInput, aInput, hStrength, aStrength, homeAdvantage)
        val aPossession = 100 - hPossession
        homePossessionSum += hPossession
        possessionSamples += 1

        // A sending-off swings the game toward the eleven-man side.
        val hHandicap = if (home.clubId in sentOffClubs) 0.76 else 1.0
        val aHandicap = if (away.clubId in sentOffClubs) 0.76 else 1.0

        val hXgPer90 = MatchEngine.expectedGoals(
            attack = hStrength.attack * hHandicap,
            opponentDefence = aStrength.defence * aHandicap,
            opponentKeeper = aStrength.goalkeeping,
            tactics = hInput.tactics,
            possessionShare = hPossession / 100.0,
            homeAdvantage = homeAdvantage
        )
        val aXgPer90 = MatchEngine.expectedGoals(
            attack = aStrength.attack * aHandicap,
            opponentDefence = hStrength.defence * hHandicap,
            opponentKeeper = hStrength.goalkeeping,
            tactics = aInput.tactics,
            possessionShare = aPossession / 100.0,
            homeAdvantage = false
        )

        val hFatigue = conditionFactorOf(hInput)
        val aFatigue = conditionFactorOf(aInput)

        // ---- Shots ----
        val hShotsNow = MatchEngine.poisson(
            (hXgPer90 / 90.0) * 7.4 * hInput.tactics.chanceVolumeMultiplier, random
        )
        val aShotsNow = MatchEngine.poisson(
            (aXgPer90 / 90.0) * 7.4 * aInput.tactics.chanceVolumeMultiplier, random
        )
        homeShots += hShotsNow
        awayShots += aShotsNow
        homeOnTarget += MatchEngine.poisson(hShotsNow * 0.36, random)
        awayOnTarget += MatchEngine.poisson(aShotsNow * 0.36, random)

        // ---- Goals ----
        val hGoalsNow = rollGoals((hXgPer90 / 90.0) * hFatigue)
        val aGoalsNow = rollGoals((aXgPer90 / 90.0) * aFatigue)
        repeat(hGoalsNow) {
            homeGoals++
            if (isExtraTime) homeEtGoals++ else homeGoalsRegular++
            out += goalEvent(home.clubId, activeHome, minute)
        }
        repeat(aGoalsNow) {
            awayGoals++
            if (isExtraTime) awayEtGoals++ else awayGoalsRegular++
            out += goalEvent(away.clubId, activeAway, minute)
        }

        // ---- Fouls and cards ----
        val hFoulsNow = MatchEngine.poisson(0.13 * hInput.tactics.foulMultiplier, random)
        val aFoulsNow = MatchEngine.poisson(0.13 * aInput.tactics.foulMultiplier, random)
        homeFouls += hFoulsNow
        awayFouls += aFoulsNow

        repeat(MatchEngine.poisson(hFoulsNow / 6.0, random)) {
            val booked = MatchEngine.pickDefensivePlayer(playersOf(hInput), random) ?: return@repeat
            homeYellows++
            tracker.addYellow(booked.id)
            out += MatchEvent(
                minute, MatchEventType.YELLOW_CARD, home.clubId,
                playerId = booked.id, playerName = booked.name, detail = "Booked for a foul"
            )
        }
        repeat(MatchEngine.poisson(aFoulsNow / 6.0, random)) {
            val booked = MatchEngine.pickDefensivePlayer(playersOf(aInput), random) ?: return@repeat
            awayYellows++
            tracker.addYellow(booked.id)
            out += MatchEvent(
                minute, MatchEventType.YELLOW_CARD, away.clubId,
                playerId = booked.id, playerName = booked.name, detail = "Booked for a foul"
            )
        }

        // ---- Red cards (rare) ----
        if (random.nextDouble() < 0.0016 * hInput.tactics.aggression.foulBias) {
            MatchEngine.pickDefensivePlayer(playersOf(hInput), random)?.let { sent ->
                out += sendOff(home.clubId, sent.id, sent.name, secondYellow = false)
            }
        }
        if (random.nextDouble() < 0.0016 * aInput.tactics.aggression.foulBias) {
            MatchEngine.pickDefensivePlayer(playersOf(aInput), random)?.let { sent ->
                out += sendOff(away.clubId, sent.id, sent.name, secondYellow = false)
            }
        }

        // ---- Injuries ----
        if (random.nextDouble() < 0.0035 * hInput.tactics.fatigueMultiplier) {
            MatchEngine.pickOutfielder(playersOf(hInput), random)?.let { victim ->
                out += injure(home.clubId, victim.id, victim.name)
            }
        }
        if (random.nextDouble() < 0.0035 * aInput.tactics.fatigueMultiplier) {
            MatchEngine.pickOutfielder(playersOf(aInput), random)?.let { victim ->
                out += injure(away.clubId, victim.id, victim.name)
            }
        }

        // ---- Corners ----
        homeCorners += MatchEngine.poisson(0.05 * (hPossession / 50.0), random)
        awayCorners += MatchEngine.poisson(0.05 * (aPossession / 50.0), random)

        // ---- Dangerous attacks ----
        // Entries into the final third scale with attacking intent, tempo, the
        // quality gap and the possession share, so they track what a viewer sees.
        val hThreat = hXgPer90 / 90.0 * hInput.tactics.chanceVolumeMultiplier
        val aThreat = aXgPer90 / 90.0 * aInput.tactics.chanceVolumeMultiplier
        homeDangerous += MatchEngine.poisson(0.45 * hThreat * (hPossession / 50.0) + 0.05, random)
        awayDangerous += MatchEngine.poisson(0.45 * aThreat * (aPossession / 50.0) + 0.05, random)

        updateDomination(
            hPossession.toDouble(), hShotsNow, aShotsNow, hHandicap, aHandicap, hStrength, aStrength
        )
        return out
    }

    /**
     * Blends the running match indicators into a single domination value.
     *
     * Possession is the anchor, shot and dangerous-attack differentials add
     * pressure, and the on-pitch quality gap (including red-card handicaps) adds
     * a small bias. The result is eased toward the previous value so the bar
     * drifts realistically instead of snapping.
     */
    private fun updateDomination(
        hPossession: Double,
        hShotsNow: Int,
        aShotsNow: Int,
        hHandicap: Double,
        aHandicap: Double,
        hStrength: TeamStrength,
        aStrength: TeamStrength
    ) {
        val possessionSignal = (hPossession - 50.0) * 0.60

        val totalShots = (homeShots + awayShots).coerceAtLeast(1)
        val shotSignal = ((homeShots - awayShots).toDouble() / totalShots) * 14.0

        val totalDangerous = (homeDangerous + awayDangerous).coerceAtLeast(1)
        val attackSignal = ((homeDangerous - awayDangerous).toDouble() / totalDangerous) * 12.0

        // Quality gap: a small nudge, so a much better side gradually takes over.
        val qualitySignal = (hStrength.overall - aStrength.overall) * 0.35

        // Red cards are decisive: a ten-man side cedes control.
        val redSignal = (aHandicap - hHandicap) * 40.0

        // Recent shot burst gives an immediate kick without flipping the bar.
        val burst = (hShotsNow - aShotsNow) * 2.2

        val target = (50.0 + possessionSignal + shotSignal + attackSignal + qualitySignal +
            redSignal + burst).coerceIn(6.0, 94.0)

        // Ease toward the target: responsive but never a jump.
        homeDomination += (target - homeDomination) * 0.18
        homeDomination = homeDomination.coerceIn(6.0, 94.0)
    }

    private fun playersOf(input: MatchTeamInput): List<MatchPlayer> =
        TeamStrengthCalculator.toMatchPlayers(input.squadById, input.selection, input.tactics.formation)

    private fun goalEvent(clubId: Long, active: MatchTeamInput, minute: Int): MatchEvent {
        val players = playersOf(active)
        val scorer = MatchEngine.pickAttacker(players, random)
        val assister = MatchEngine.pickAssister(players, scorer, random)
        if (scorer != null) tracker.addGoal(scorer.id)
        if (assister != null) tracker.addAssist(assister.id)
        return MatchEvent(
            minute = minute,
            type = MatchEventType.GOAL,
            clubId = clubId,
            playerId = scorer?.id,
            playerName = scorer?.name ?: "",
            secondaryPlayerId = assister?.id,
            secondaryPlayerName = assister?.name ?: "",
            detail = if (assister != null) "Assisted by ${assister.name}" else "Unassisted"
        )
    }

    private fun sendOff(clubId: Long, playerId: Long, playerName: String, secondYellow: Boolean): MatchEvent {
        sentOffClubs += clubId
        if (clubId == home.clubId) homeReds++ else awayReds++
        offMinutes[playerId] = minute.coerceAtLeast(1)
        tracker.addRed(playerId)
        return MatchEvent(
            minute = minute,
            type = if (secondYellow) MatchEventType.SECOND_YELLOW else MatchEventType.RED_CARD,
            clubId = clubId,
            playerId = playerId,
            playerName = playerName,
            detail = if (secondYellow) "Second booking - sent off" else "Sent off"
        )
    }

    private fun injure(clubId: Long, playerId: Long, playerName: String): MatchEvent {
        offMinutes[playerId] = minute.coerceAtLeast(1)
        val type = randomInjuryType()
        return MatchEvent(
            minute = minute,
            type = MatchEventType.INJURY,
            clubId = clubId,
            playerId = playerId,
            playerName = playerName,
            detail = "Forced off with a ${type.label.lowercase()}"
        )
    }

    private fun randomInjuryType(): InjuryType {
        val pool = InjuryType.matchInjuries
        return pool[random.nextInt(pool.size)]
    }

    private fun rollGoals(rate: Double): Int {
        if (rate <= 0.0) return 0
        val adjusted = rate.pow(1.0 / determinism.coerceIn(0.6, 1.8)).coerceIn(0.0, 4.0)
        val limit = exp(-adjusted)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= random.nextDouble()
        } while (p > limit && k < 6)
        return k - 1
    }

    /** Fitness at kick-off scales how well a side converts its chances. */
    private fun conditionFactorOf(input: MatchTeamInput): Double {
        val squad = input.selection.startingXi.mapNotNull { input.squadById[it.playerId] }
        if (squad.isEmpty()) return 1.0
        val avg = squad.sumOf { it.fitness } / squad.size.toDouble()
        return TeamStrengthCalculator.conditionFactor(avg.roundToInt())
    }

    private fun effectiveStrength(input: MatchTeamInput): TeamStrength =
        MatchEngine.applyMultiplier(
            TeamStrengthCalculator.build(input.clubId, playersOf(input), input.reputation),
            input.strengthMultiplier
        )

    // ------------------------------------------------------------- transitions

    private fun transitionFromPeriod(): List<MatchEvent> {
        val out = mutableListOf<MatchEvent>()
        when (phase) {
            MatchPhase.FIRST_HALF -> {
                phase = MatchPhase.HALF_TIME
                out += MatchEvent(
                    minute = minute,
                    type = MatchEventType.HALF_TIME,
                    clubId = home.clubId,
                    detail = "Half time: ${home.clubName} $homeGoals - $awayGoals ${away.clubName}"
                )
            }
            MatchPhase.SECOND_HALF -> {
                if (rules.allowExtraTime && homeGoals == awayGoals) {
                    phase = MatchPhase.EXTRA_TIME_BREAK
                    out += MatchEvent(
                        minute = 90,
                        type = MatchEventType.EXTRA_TIME_START,
                        clubId = home.clubId,
                        detail = "Level after ninety minutes - extra time to be played"
                    )
                } else {
                    out += conclude()
                }
            }
            MatchPhase.EXTRA_TIME_FIRST -> {
                phase = MatchPhase.EXTRA_TIME_SECOND
                minute = 105
                out += MatchEvent(105, MatchEventType.INFO, home.clubId, detail = "Second period of extra time")
            }
            MatchPhase.EXTRA_TIME_SECOND -> {
                if (homeGoals == awayGoals && rules.allowShootout) {
                    val (sh, sa) = shootout(random)
                    shootoutHome = sh
                    shootoutAway = sa
                    out += MatchEvent(
                        minute = 120, type = MatchEventType.PENALTY_SHOOTOUT_GOAL, clubId = home.clubId,
                        detail = "Penalty shootout: ${home.clubName} $sh - $sa ${away.clubName}"
                    )
                }
                out += conclude()
            }
            else -> Unit
        }
        return out
    }

    private fun conclude(): MatchEvent {
        phase = MatchPhase.FINISHED
        applyMinutesBookkeeping()
        val suffix = when {
            shootoutHome != null -> " (${shootoutHome}-${shootoutAway} on penalties)"
            homeEtGoals > 0 || awayEtGoals > 0 -> " (a.e.t.)"
            else -> ""
        }
        return MatchEvent(
            minute = minute,
            type = MatchEventType.FULL_TIME,
            clubId = home.clubId,
            detail = "Full time: ${home.clubName} $homeGoals - $awayGoals ${away.clubName}$suffix"
        )
    }

    private fun applyMinutesBookkeeping() {
        val finalMinute = minute.coerceAtLeast(1)
        for ((id, off) in offMinutes) tracker.setMinutes(id, off)
        for ((id, on) in onAtMinute) tracker.setMinutes(id, (finalMinute - on).coerceAtLeast(1))
    }

    // ------------------------------------------------------------ substitutions

    private fun applySubstitutions(updated: MatchTeamInput, atHalfTime: Boolean): List<MatchEvent> {
        val clubId = updated.clubId
        val out = mutableListOf<MatchEvent>()
        val onPitch = playersOf(updated).map { it.id }.toMutableSet()

        val scripted = updated.plannedSubstitutions
            .filter { it.playerOffId in onPitch && it.playerOnId !in onPitch }
            .sortedBy { it.minute }

        for (change in scripted) {
            if ((subsMade[clubId] ?: 0) >= rules.maxSubstitutions) break
            if (change.playerOffId !in onPitch || change.playerOnId in onPitch) continue

            val free = atHalfTime || change.minute == 45
            if (!free) {
                val used = windowsUsed[clubId] ?: 0
                if (used >= rules.maxSubstitutionWindows) continue
                windowsUsed[clubId] = used + 1
            }

            val on = updated.squadById[change.playerOnId] ?: continue
            val off = updated.squadById[change.playerOffId] ?: continue
            onPitch.remove(change.playerOffId)
            onPitch.add(change.playerOnId)
            subsMade[clubId] = (subsMade[clubId] ?: 0) + 1
            val changeMinute = if (atHalfTime) 45 else minute.coerceAtLeast(1)
            offMinutes[change.playerOffId] = changeMinute
            onAtMinute[change.playerOnId] = changeMinute

            out += MatchEvent(
                minute = changeMinute,
                type = MatchEventType.SUBSTITUTION,
                clubId = clubId,
                playerId = change.playerOnId,
                playerName = on.name,
                secondaryPlayerId = change.playerOffId,
                secondaryPlayerName = off.name,
                detail = if (atHalfTime) "${on.name} replaces ${off.name} (half time)"
                else "${on.name} replaces ${off.name}"
            )
        }
        events += out
        return out
    }

    /** AI sides rotate at the break without manager input. */
    fun applyDefaultHalfTimeSubs(input: MatchTeamInput, isHome: Boolean): List<MatchEvent> {
        if (input.selection.substitutes.isEmpty()) return emptyList()
        val onPitch = playersOf(input)
        val onIds = onPitch.map { it.id }.toSet()
        val bench = input.selection.substitutes.mapNotNull { input.squadById[it] }.filter { it.id !in onIds }
        if (bench.isEmpty()) return emptyList()
        val off = onPitch.filter { !it.isGoalkeeper }.minByOrNull { it.player.fitness } ?: return emptyList()
        val on = bench.maxByOrNull { it.overall } ?: return emptyList()
        return applyLiveSubstitution(
            input.copy(plannedSubstitutions = listOf(PlannedSubstitution(off.id, on.id, 45))),
            isHome = isHome
        )
    }

    /** AI reaction: replace a badly tiring outfielder. */
    fun reactToFatigue(input: MatchTeamInput, isHome: Boolean): List<MatchEvent> {
        if (input.selection.substitutes.isEmpty()) return emptyList()
        val onPitch = playersOf(input)
        val onIds = onPitch.map { it.id }.toSet()
        val bench = input.selection.substitutes.mapNotNull { input.squadById[it] }.filter { it.id !in onIds }
        val tired = onPitch.filter { !it.isGoalkeeper && it.player.fitness < 62 }.minByOrNull { it.player.fitness }
            ?: return emptyList()
        val replacement = bench.maxByOrNull { it.overall } ?: return emptyList()
        return applyLiveSubstitution(
            input.copy(plannedSubstitutions = listOf(PlannedSubstitution(tired.id, replacement.id, minute))),
            isHome = isHome
        )
    }

    // ------------------------------------------------------------------ output

    fun stats(): Pair<TeamMatchStats, TeamMatchStats> {
        val possession = if (possessionSamples == 0) 50
        else (homePossessionSum / possessionSamples).roundToInt().coerceIn(28, 72)

        val homeStats = TeamMatchStats(
            clubId = home.clubId,
            goals = homeGoals,
            shots = homeShots.coerceAtLeast(homeGoals),
            shotsOnTarget = homeOnTarget.coerceIn(homeGoals, homeShots.coerceAtLeast(homeGoals)),
            possession = possession,
            fouls = homeFouls,
            corners = homeCorners,
            yellowCards = homeYellows,
            redCards = homeReds,
            passAccuracy = passAccuracy(activeHome, possession),
            expectedGoals = round1(homeGoals * 0.62 + homeShots * 0.11)
        )
        val awayStats = TeamMatchStats(
            clubId = away.clubId,
            goals = awayGoals,
            shots = awayShots.coerceAtLeast(awayGoals),
            shotsOnTarget = awayOnTarget.coerceIn(awayGoals, awayShots.coerceAtLeast(awayGoals)),
            possession = 100 - possession,
            fouls = awayFouls,
            corners = awayCorners,
            yellowCards = awayYellows,
            redCards = awayReds,
            passAccuracy = passAccuracy(activeAway, 100 - possession),
            expectedGoals = round1(awayGoals * 0.62 + awayShots * 0.11)
        )
        return homeStats to awayStats
    }

    private fun passAccuracy(input: MatchTeamInput, possession: Int): Int {
        val base = 74.0 + input.tactics.style.possessionBias * 3.4 - 3.4 + (possession - 50) * 0.08
        return base.roundToInt().coerceIn(58, 94)
    }

    fun momentum(): List<Int> {
        val blocks = max(1, (minute + 14) / 15)
        val (hStats, aStats) = stats()
        val base = (hStats.expectedGoals - aStats.expectedGoals) * 40.0 +
            (hStats.possession - 50) * 0.6
        return (0 until blocks).map { i ->
            val wave = kotlin.math.sin(i * 1.1) * 12.0
            (base + wave + random.nextDouble(-8.0, 8.0)).coerceIn(-100.0, 100.0).roundToInt()
        }
    }

    /** Packages the finished match as a [MatchResult] for the rest of the game. */
    fun toResult(matchId: Long, leagueId: String, matchday: Int): MatchResult {
        val (hStats, aStats) = stats()
        val ratings = tracker.finish(
            homePlayers = playersOf(home),
            awayPlayers = playersOf(away),
            homeStats = hStats,
            awayStats = aStats,
            homeStrength = effectiveStrength(home),
            awayStrength = effectiveStrength(away),
            homeSelection = home.selection,
            awaySelection = away.selection
        )
        return MatchResult(
            matchId = matchId,
            leagueId = leagueId,
            matchday = matchday,
            homeClubId = home.clubId,
            awayClubId = away.clubId,
            homeGoals = homeGoals,
            awayGoals = awayGoals,
            homeStats = hStats,
            awayStats = aStats,
            events = eventLog(),
            playerRatings = ratings,
            playerOfTheMatchId = ratings.maxByOrNull { it.rating }?.playerId,
            penaltyShootoutHome = shootoutHome,
            penaltyShootoutAway = shootoutAway,
            momentum = momentum(),
            extraTime = homeEtGoals > 0 || awayEtGoals > 0 || shootoutHome != null
        )
    }

    /** The regular-time score, used to seed a knockout tie's aggregate. */
    fun regularTimeScore(): Pair<Int, Int> = homeGoalsRegular to awayGoalsRegular

    private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0
}

/** The period of play the engine is currently in. */
enum class MatchPhase {
    FIRST_HALF,
    HALF_TIME,
    SECOND_HALF,
    EXTRA_TIME_BREAK,
    EXTRA_TIME_FIRST,
    EXTRA_TIME_SECOND,
    SHOOTOUT,
    FINISHED
}
