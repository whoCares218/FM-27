package com.footymanager.simulator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.footymanager.simulator.domain.data.AnimationSpeed
import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.GameSettings
import com.footymanager.simulator.domain.data.CareerStore
import com.footymanager.simulator.domain.data.SelectionHelper
import com.footymanager.simulator.domain.data.SaveRepository
import com.footymanager.simulator.domain.data.SettingsRepository
import com.footymanager.simulator.domain.data.SettingsStore
import com.footymanager.simulator.domain.engine.ChampionsLeagueEngine
import com.footymanager.simulator.domain.engine.AiManager
import com.footymanager.simulator.domain.engine.FinanceEngine
import com.footymanager.simulator.domain.engine.MatchPhase
import com.footymanager.simulator.domain.engine.MatchRules
import com.footymanager.simulator.domain.engine.MatchTeamInput
import com.footymanager.simulator.domain.engine.PlannedSubstitution
import com.footymanager.simulator.domain.engine.ProgressiveMatchEngine
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.SelectionRepair
import com.footymanager.simulator.domain.engine.SerializableRandom
import com.footymanager.simulator.domain.engine.SponsorshipEngine
import com.footymanager.simulator.domain.engine.StadiumEngine
import com.footymanager.simulator.domain.engine.TransferEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.AttendanceEstimate
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.GamePhase
import com.footymanager.simulator.domain.model.LineupSlot
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.MatchEvent
import com.footymanager.simulator.domain.model.MatchEventType
import com.footymanager.simulator.domain.model.MatchResult
import com.footymanager.simulator.domain.model.MatchModePersist
import com.footymanager.simulator.domain.model.MatchPhasePersist
import com.footymanager.simulator.domain.model.InProgressMatchState
import com.footymanager.simulator.domain.model.MatchdayFinance
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PositionChange
import com.footymanager.simulator.domain.model.SaleNegotiation
import com.footymanager.simulator.domain.model.SponsorOffer
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.TeamMatchStats
import com.footymanager.simulator.domain.model.TeamSelection
import com.footymanager.simulator.domain.model.TrainingFocus
import com.footymanager.simulator.domain.model.TransferListing
import com.footymanager.simulator.domain.model.TransferOffer
import com.footymanager.simulator.ui.sound.SoundCue
import com.footymanager.simulator.ui.sound.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** How the manager chose to play the current match. */
enum class MatchMode { PLAY, QUICK }

/** A single line of the live match feed shown while the game unfolds. */
data class MatchFeedItem(
    val minute: String,
    val type: MatchEventType,
    val text: String,
    val isUserClub: Boolean
)

/**
 * Transient state for the match-day experience.
 *
 * Before kick-off this is a preview. Once [started] it reflects the live match:
 * the clock, the running score, the event feed and the two sides' statistics all
 * come straight from the engine, which is advanced progressively so the result is
 * never known in advance.
 */
data class MatchDayState(
    val match: Match,
    val opponent: Club,
    val isHome: Boolean,
    val opponentFormationName: String,
    val userFormationName: String,
    val mode: MatchMode = MatchMode.PLAY,
    /** True once the manager has pressed START MATCH. */
    val started: Boolean = false,
    /** True while the engine is actively advancing. */
    val simulating: Boolean = false,
    val phase: MatchPhase = MatchPhase.FIRST_HALF,
    val minute: Int = 0,
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    val homeStats: TeamMatchStats = TeamMatchStats(clubId = 0),
    val awayStats: TeamMatchStats = TeamMatchStats(clubId = 0),
    val feed: List<MatchFeedItem> = emptyList(),
    val homeOnPitch: List<Long> = emptyList(),
    val awayOnPitch: List<Long> = emptyList(),
    /** Substitutions the manager has queued, keyed by the player coming off. */
    val plannedSubstitutions: List<PlannedSubstitution> = emptyList(),
    /** True when the first half has ended and the manager may take their time. */
    val awaitingHalfTime: Boolean = false,
    /** True when extra time is required before the tie can be settled. */
    val awaitingExtraTime: Boolean = false,
    val substitutionsMade: Int = 0,
    val substitutionWindowsUsed: Int = 0,
    val result: MatchResult? = null,
    /** Remaining fixtures on the same matchday week that are still to be played. */
    val pendingOtherFixtures: List<Match> = emptyList(),
    /**
     * Live domination, home share first, always summing to 100. The UI animates
     * toward this value every frame, so it only changes once per simulated minute.
     */
    val homeDomination: Int = 50,
    /** The minute of the most recent significant event, used to trigger flashes. */
    val lastEventMinute: Int = -1,
    /** Bench players still available to come on. */
    val benchIds: List<Long> = emptyList(),
    /** Competition limits, shown on the substitution screen. */
    val maxSubstitutions: Int = 5,
    val maxSubstitutionWindows: Int = 3,
    /** Forward gate estimate for a home fixture, shown before kick-off. */
    val attendanceEstimate: AttendanceEstimate? = null,
    /** The user's league position before this fixture, for the movement animation. */
    val positionBefore: Int = 0,
    /** The user's league position after this fixture, for the movement animation. */
    val positionAfter: Int = 0,
    /** The full financial report for this fixture once it has been settled. */
    val matchdayFinance: MatchdayFinance? = null
) {
    val isPlayed: Boolean get() = result != null
    val isFinished: Boolean get() = result != null

    val positionChange: PositionChange get() = PositionChange(positionBefore, positionAfter)

    /** Goals for the user's club regardless of venue. */
    val userGoals: Int get() = if (isHome) homeGoals else awayGoals
    val opponentGoals: Int get() = if (isHome) awayGoals else homeGoals

    val userStats: TeamMatchStats get() = if (isHome) homeStats else awayStats
    val opponentStats: TeamMatchStats get() = if (isHome) awayStats else homeStats

    val clockLabel: String
        get() = when {
            phase == MatchPhase.HALF_TIME -> "HT"
            phase == MatchPhase.FINISHED -> "FT"
            phase == MatchPhase.EXTRA_TIME_BREAK -> "ET"
            minute > 90 -> "90+${minute - 90}'"
            else -> "$minute'"
        }
}

/**
 * Owns the live career and mediates every interaction between the UI and the
 * simulation engines. All mutations are persisted immediately so the game can be
 * closed at any point without losing progress.
 *
 * The repositories are injectable so tests can run against an isolated store.
 */
class GameViewModel(
    application: Application,
    private val saveRepository: CareerStore,
    private val settingsRepository: SettingsStore
) : AndroidViewModel(application) {

    /**
     * Constructor used by the platform's default ViewModel factory, which
     * instantiates AndroidViewModel subclasses reflectively and only ever looks
     * for a single-argument `(Application)` constructor.
     *
     * The primary constructor's default arguments compile down to a synthetic
     * constructor with a bitmask parameter, not a real `(Application)` overload,
     * so without this the reflective lookup fails and the app crashes on launch.
     * Tests inject their own stores through the primary constructor.
     */
    constructor(application: Application) : this(
        application,
        SaveRepository(application),
        SettingsRepository(application)
    )

    private val _career = MutableStateFlow<Career?>(null)
    val career: StateFlow<Career?> = _career.asStateFlow()

    private val _settings = MutableStateFlow(GameSettings())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    private val _hasSave = MutableStateFlow(false)
    val hasSave: StateFlow<Boolean> = _hasSave.asStateFlow()

    private val _matchDay = MutableStateFlow<MatchDayState?>(null)
    val matchDay: StateFlow<MatchDayState?> = _matchDay.asStateFlow()

    /** The live engine for the current match; null when no match is in progress. */
    private var _engine: ProgressiveMatchEngine? = null

    /** The career snapshot the live engine was built from. */
    private var _engineCareer: Career? = null

    /**
     * The user's XI currently on the pitch, tracked across live substitutions so
     * that a second change cannot resurrect a player who has already gone off.
     * Kept in sync whenever the engine adopts a new selection.
     */
    private var _liveXi: List<Long> = emptyList()

    /** Bench still available for the live match. */
    private var _liveBench: List<Long> = emptyList()

    /** The coroutine driving the live match clock. */
    private var engineJob: Job? = null

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Lightweight synthesised sound effects, gated by the player's setting. */
    val sound = SoundManager()

    /** Reward state of the current career, for the rewarded-ad card. */
    val adRewards: StateFlow<com.footymanager.simulator.domain.model.AdRewardState?>
        get() = _adRewards.asStateFlow()
    private val _adRewards =
        MutableStateFlow<com.footymanager.simulator.domain.model.AdRewardState?>(null)

    /**
     * Serializes persistence. Rapid edits each queue a save, and without this
     * the writes race and an older snapshot can land last, losing the newest
     * tactics or selection. Deletes travel the same queue so a pending write can
     * never resurrect a career that was just reset.
     */
    private val persistRequests = Channel<PersistRequest>(Channel.CONFLATED)

    /** Dedicated scope so persistence never depends on the main-thread job. */
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private sealed interface PersistRequest {
        data class Write(
            val career: Career,
            val onResult: ((Boolean) -> Unit)? = null
        ) : PersistRequest

        data object Delete : PersistRequest
    }

    init {
        // Persistence runs off the main thread; the queue is drained in order on
        // a single coroutine so an older snapshot can never land after a newer one.
        persistenceScope.launch {
            for (request in persistRequests) {
                when (request) {
                    is PersistRequest.Write -> {
                        val saved = saveRepository.save(request.career)
                        request.onResult?.invoke(saved)
                    }
                    PersistRequest.Delete -> saveRepository.deleteSave()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect {
                _settings.value = it
                sound.enabled = it.soundEnabled
            }
        }
        viewModelScope.launch {
            _hasSave.value = saveRepository.hasSave()
        }
        viewModelScope.launch {
            _career.collect { _adRewards.value = it?.adRewards }
        }
    }

    // ------------------------------------------------------------ lifecycle

    fun startNewCareer(managerName: String, clubId: Long, difficulty: Difficulty) {
        viewModelScope.launch {
            _isBusy.value = true
            val created = withContext(Dispatchers.Default) {
                CareerFactory.create(
                    CareerFactory.NewCareerRequest(
                        managerName = managerName.ifBlank { "The Manager" },
                        clubId = clubId,
                        difficulty = difficulty,
                        seed = System.currentTimeMillis()
                    )
                )
            }
            _career.value = created
            _matchDay.value = null
            persist(created)
            _hasSave.value = true
            _isBusy.value = false
        }
    }

    fun continueCareer() {
        viewModelScope.launch {
            _isBusy.value = true
            val loaded = saveRepository.load()
            _career.value = loaded
            _matchDay.value = null
            _isBusy.value = false
            if (loaded == null) _message.value = "No saved career was found."
        }
    }

    fun loadCareer() = continueCareer()

    fun saveCareer() {
        val current = _career.value ?: return
        persistRequests.trySend(
            PersistRequest.Write(current) { ok ->
                _message.value = if (ok) "Career saved" else "Could not save the career"
            }
        )
    }

    fun returnToMainMenu() {
        // If a match is live, capture it first so the save reflects the current
        // minute rather than the last settled matchday.
        if (_engine != null) persistSnapshotIfAny()
        _career.value?.let { persist(it) }
        engineJob?.cancel()
        _engine = null
        _engineCareer = null
        _career.value = null
        _matchDay.value = null
    }

    fun resetCareer() {
        persistDelete()
        _career.value = null
        _matchDay.value = null
        _hasSave.value = false
        _message.value = "Career reset"
    }

    fun clearMessage() {
        _message.value = null
    }

    // ------------------------------------------------------------- settings

    fun setSound(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSound(enabled) }
    fun setVibration(enabled: Boolean) = viewModelScope.launch { settingsRepository.setVibration(enabled) }
    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDarkTheme(enabled) }
    fun setDifficulty(difficulty: Difficulty) = viewModelScope.launch { settingsRepository.setDifficulty(difficulty) }
    fun setAnimationSpeed(speed: AnimationSpeed) = viewModelScope.launch { settingsRepository.setAnimationSpeed(speed) }

    /** Plays a UI cue through the shared sound manager. */
    fun playSound(cue: SoundCue) {
        when (cue) {
            SoundCue.CLICK -> sound.click()
            SoundCue.SUCCESS -> sound.success()
            SoundCue.FAILURE -> sound.failure()
            SoundCue.GOAL -> sound.goal()
            SoundCue.WHISTLE -> sound.whistle()
        }
    }

    // --------------------------------------------------------------- tactics

    fun setFormation(formationId: String) {
        updateCareer { career ->
            val tactics = career.tactics.copy(formationId = formationId)
            val repaired = SelectionRepair.repair(
                career.selection, career.squadOf(career.userClubId), Formation.byId(formationId)
            )
            career.copy(tactics = tactics, selection = repaired)
        }
    }

    fun setMentality(mentality: com.footymanager.simulator.domain.model.Mentality) =
        updateCareer { it.copy(tactics = it.tactics.copy(mentality = mentality)) }

    fun setStyle(style: com.footymanager.simulator.domain.model.PlayStyle) =
        updateCareer { it.copy(tactics = it.tactics.copy(style = style)) }

    fun setDefensiveLine(line: com.footymanager.simulator.domain.model.DefensiveLine) =
        updateCareer { it.copy(tactics = it.tactics.copy(defensiveLine = line)) }

    fun setTempo(tempo: com.footymanager.simulator.domain.model.Tempo) =
        updateCareer { it.copy(tactics = it.tactics.copy(tempo = tempo)) }

    fun setWidth(width: com.footymanager.simulator.domain.model.Width) =
        updateCareer { it.copy(tactics = it.tactics.copy(width = width)) }

    fun setPressing(pressing: com.footymanager.simulator.domain.model.Pressing) =
        updateCareer { it.copy(tactics = it.tactics.copy(pressing = pressing)) }

    fun setPassingStyle(passing: com.footymanager.simulator.domain.model.PassingStyle) =
        updateCareer { it.copy(tactics = it.tactics.copy(passingStyle = passing)) }

    fun setBuildUp(buildUp: com.footymanager.simulator.domain.model.BuildUp) =
        updateCareer { it.copy(tactics = it.tactics.copy(buildUp = buildUp)) }

    fun setCounterAttack(counter: com.footymanager.simulator.domain.model.CounterAttack) =
        updateCareer { it.copy(tactics = it.tactics.copy(counterAttack = counter)) }

    fun setPossessionFocus(focus: com.footymanager.simulator.domain.model.PossessionFocus) =
        updateCareer { it.copy(tactics = it.tactics.copy(possessionFocus = focus)) }

    fun setCrossing(crossing: com.footymanager.simulator.domain.model.Crossing) =
        updateCareer { it.copy(tactics = it.tactics.copy(crossing = crossing)) }

    fun setAggression(aggression: com.footymanager.simulator.domain.model.Aggression) =
        updateCareer { it.copy(tactics = it.tactics.copy(aggression = aggression)) }

    /** Applies a one-tap preset to the team instructions, keeping the formation. */
    fun applyPreset(preset: com.footymanager.simulator.domain.model.TacticalPreset) {
        updateCareer { it.copy(tactics = preset.applyTo(it.tactics)) }
    }

    /** Sets (or clears) an individual instruction for one player. */
    fun setIndividualInstruction(
        playerId: Long,
        instruction: com.footymanager.simulator.domain.model.IndividualInstruction
    ) {
        updateCareer { career ->
            val updated = career.tactics.playerInstructions.toMutableMap()
            if (instruction == com.footymanager.simulator.domain.model.IndividualInstruction.DEFAULT) {
                updated.remove(playerId)
            } else {
                updated[playerId] = instruction
            }
            career.copy(tactics = career.tactics.copy(playerInstructions = updated))
        }
    }

    /** Sets the designated set-piece takers, wiring them into the match selection. */
    fun setSetPieces(takers: com.footymanager.simulator.domain.model.SetPieceTakers) {
        updateCareer { career ->
            career.copy(
                tactics = career.tactics.copy(setPieces = takers),
                // The engine reads the selection's penalty/free-kick takers, so keep
                // them in lock-step with the set-piece screen.
                selection = career.selection.copy(
                    penaltyTakerId = takers.penaltyTakerId,
                    freeKickTakerId = takers.freeKickTakerId
                )
            )
        }
    }

    /** Applies a fully-formed tactics object (used by the in-depth editor). */
    fun setTactics(tactics: Tactics) = updateCareer { career ->
        val repaired = SelectionRepair.repair(
            career.selection, career.squadOf(career.userClubId), tactics.formation
        )
        career.copy(tactics = tactics, selection = repaired)
    }

    fun setTrainingFocus(focus: TrainingFocus) =
        updateCareer { it.copy(trainingFocus = focus) }

    // ------------------------------------------------------------- selection

    fun autoPickSelection() {
        updateCareer { career ->
            val squad = career.squadOf(career.userClubId)
            val selection = SelectionHelper.autoPickBest(squad, career.tactics.formation)
            career.copy(selection = selection)
        }
    }

    /**
     * Places a player into a slot. If the player already occupies another slot the
     * two are swapped, which matches how managers expect a drag/drop to behave.
     */
    fun assignPlayerToSlot(slotIndex: Int, playerId: Long) {
        updateCareer { career ->
            val squad = career.squadOf(career.userClubId)
            val byId = squad.associateBy { it.id }
            val formation = career.tactics.formation
            if (slotIndex !in formation.roles.indices) return@updateCareer career

            val existingSlotIndex = career.selection.startingXi
                .firstOrNull { it.playerId == playerId }?.slotIndex
            val displaced = career.selection.startingXi
                .firstOrNull { it.slotIndex == slotIndex }?.playerId

            val slots = career.selection.startingXi.toMutableList()
            slots.removeAll { it.playerId == playerId || it.slotIndex == slotIndex }

            val player = byId[playerId] ?: return@updateCareer career
            slots += LineupSlot(
                slotIndex = slotIndex,
                playerId = playerId,
                outOfPosition = player.position != formation.roles[slotIndex].naturalPosition
            )
            // Swap the displaced player into the incoming player's old slot.
            if (existingSlotIndex != null && displaced != null && displaced != playerId) {
                val displacedPlayer = byId[displaced]
                if (displacedPlayer != null) {
                    slots += LineupSlot(
                        slotIndex = existingSlotIndex,
                        playerId = displaced,
                        outOfPosition = displacedPlayer.position !=
                            formation.roles[existingSlotIndex].naturalPosition
                    )
                }
            }

            val bench = career.selection.substitutes.filter { id ->
                slots.none { it.playerId == id } && id != playerId
            }
            career.copy(selection = career.selection.copy(startingXi = slots.sortedBy { it.slotIndex }, substitutes = bench))
        }
    }

    /**
     * Swaps the players occupying two slots. Used by the drag-and-drop tactical
     * board; a no-op when either slot is empty or both hold the same player.
     */
    fun swapSlots(slotA: Int, slotB: Int) {
        if (slotA == slotB) return
        updateCareer { career ->
            val formation = career.tactics.formation
            if (slotA !in formation.roles.indices || slotB !in formation.roles.indices) {
                return@updateCareer career
            }
            val byId = career.squadOf(career.userClubId).associateBy { it.id }
            val slotAPlayer = career.selection.startingXi.firstOrNull { it.slotIndex == slotA }?.playerId
            val slotBPlayer = career.selection.startingXi.firstOrNull { it.slotIndex == slotB }?.playerId
            if (slotAPlayer == null && slotBPlayer == null) return@updateCareer career

            val slots = career.selection.startingXi.filterNot {
                it.slotIndex == slotA || it.slotIndex == slotB
            }.toMutableList()
            if (slotBPlayer != null) {
                val p = byId[slotBPlayer]
                slots += LineupSlot(
                    slotIndex = slotA,
                    playerId = slotBPlayer,
                    outOfPosition = p != null && p.position != formation.roles[slotA].naturalPosition
                )
            }
            if (slotAPlayer != null) {
                val p = byId[slotAPlayer]
                slots += LineupSlot(
                    slotIndex = slotB,
                    playerId = slotAPlayer,
                    outOfPosition = p != null && p.position != formation.roles[slotB].naturalPosition
                )
            }
            career.copy(
                selection = career.selection.copy(startingXi = slots.sortedBy { it.slotIndex })
            )
        }
    }

    fun removePlayerFromSlot(slotIndex: Int) {
        updateCareer { career ->
            val removed = career.selection.startingXi.firstOrNull { it.slotIndex == slotIndex }?.playerId
            val slots = career.selection.startingXi.filterNot { it.slotIndex == slotIndex }
            val bench = if (removed != null && removed !in career.selection.substitutes) {
                career.selection.substitutes + removed
            } else career.selection.substitutes
            career.copy(selection = career.selection.copy(startingXi = slots, substitutes = bench))
        }
    }

    fun setCaptain(playerId: Long?) =
        updateCareer { it.copy(selection = it.selection.copy(captainId = playerId)) }

    fun toggleSubstitute(playerId: Long) {
        updateCareer { career ->
            val current = career.selection.substitutes
            val onPitch = career.selection.startingXi.any { it.playerId == playerId }
            if (onPitch) return@updateCareer career
            val updated = if (playerId in current) current - playerId else current + playerId
            career.copy(selection = career.selection.copy(substitutes = updated.take(9)))
        }
    }

    // -------------------------------------------------------------- matchday

    /**
     * Prepares the match-day screen for the next fixture. Nothing is simulated
     * here: the manager gets a preview and the engine is only created when they
     * choose Play Match or Quick Sim.
     */
    fun prepareNextMatch(): Boolean {
        val career = _career.value ?: return false
        val matchday = career.matchdayIndex + 1
        val candidates = career.fixtures.filter {
            !it.isPlayed && it.involves(career.userClubId) && it.matchday == matchday
        }
        val match = candidates.minByOrNull { it.competition.ordinal } ?: return false

        // If a match is already paused mid-play for this fixture (the manager left
        // and came back, or the app was restarted), rebuild the live state instead
        // of presenting a fresh kick-off — the game resumes exactly where it was.
        val pending = career.inProgressMatch
        if (pending != null && pending.matchId == match.id && !pending.isFinished) {
            return restoreInProgressMatch()
        }

        val opponentId = match.opponentOf(career.userClubId)
        val opponent = career.clubOrThrow(opponentId)
        val opponentTactics = SeasonEngine.tacticsFor(career, opponentId, match.matchday)
        val isHome = match.isHomeFor(career.userClubId)
        _matchDay.value = MatchDayState(
            match = match,
            opponent = opponent,
            isHome = isHome,
            opponentFormationName = opponentTactics.formation.name,
            userFormationName = career.tactics.formation.name,
            pendingOtherFixtures = candidates.filter { it.id != match.id },
            attendanceEstimate = if (isHome) {
                val ppg = AiManager.recentPointsPerGame(
                    career.fixtures.filter { it.isPlayed && it.involves(career.userClubId) },
                    career.userClubId
                )
                StadiumEngine.estimate(
                    stadium = career.stadium,
                    reputation = career.userClub.reputation,
                    opponentReputation = opponent.reputation,
                    recentPointsPerGame = ppg,
                    fanSatisfaction = career.fanSatisfaction,
                    isRival = career.userClub.rivalClubId == opponent.id
                )
            } else null,
            positionBefore = career.userLeaguePosition
        )
        return true
    }

    /** True when a save holds a match that was left unfinished. */
    val hasPendingMatch: Boolean get() = _career.value?.inProgressMatch?.isFinished == false

    /**
     * Rebuilds the live engine and match-day screen from a persisted snapshot.
     * Every counter (score, stats, cards, substitutions, fitness, the random
     * stream) comes straight from the snapshot, so the resumed match plays out
     * identically to an uninterrupted one.
     */
    fun restoreInProgressMatch(): Boolean {
        val career = _career.value ?: return false
        val snap = career.inProgressMatch ?: return false
        val match = career.fixtures.firstOrNull { it.id == snap.matchId }
        if (match == null || match.isPlayed) {
            _career.value = career.copy(inProgressMatch = null)
            return false
        }
        val opponentId = match.opponentOf(career.userClubId)
        val opponent = career.clubOrThrow(opponentId)
        val (homeInput, awayInput) = SeasonEngine.buildTeamInputs(career, match)
        val engine = ProgressiveMatchEngine(
            home = homeInput,
            away = awayInput,
            seedRandom = SerializableRandom(snap.rngState),
            homeAdvantage = snap.homeAdvantage,
            determinism = snap.determinism,
            rules = MatchRules(
                allowExtraTime = snap.allowExtraTime,
                allowShootout = snap.allowShootout,
                maxSubstitutions = snap.maxSubstitutions,
                maxSubstitutionWindows = snap.maxSubstitutionWindows
            )
        )
        engine.restoreFrom(snap)
        _engine = engine
        _engineCareer = career
        _liveXi = snap.liveXi
        _liveBench = snap.liveBench

        val (hStats, aStats) = engine.stats()
        val (hg, ag) = engine.score()
        val (domHome, _) = engine.domination()
        val phase = engine.currentPhase
        val feed = snap.events.map {
            MatchFeedItem(it.displayMinute, it.type, feedText(it), it.clubId == career.userClubId)
        }
        _matchDay.value = MatchDayState(
            match = match,
            opponent = opponent,
            isHome = snap.userIsHome,
            opponentFormationName = SeasonEngine.tacticsFor(career, opponentId, match.matchday).formation.name,
            userFormationName = career.tactics.formation.name,
            mode = if (snap.mode == MatchModePersist.QUICK) MatchMode.QUICK else MatchMode.PLAY,
            started = true,
            simulating = false,
            phase = phase,
            minute = engine.clockMinute,
            homeGoals = hg,
            awayGoals = ag,
            homeStats = hStats,
            awayStats = aStats,
            feed = feed.takeLast(80),
            homeOnPitch = engine.activeInput(true).selection.startingXi.map { it.playerId },
            awayOnPitch = engine.activeInput(false).selection.startingXi.map { it.playerId },
            awaitingHalfTime = phase == MatchPhase.HALF_TIME,
            awaitingExtraTime = engine.needsExtraTime,
            substitutionsMade = engine.substitutionsMade(career.userClubId),
            substitutionWindowsUsed = engine.windowsConsumed(career.userClubId),
            homeDomination = domHome,
            benchIds = _liveBench,
            maxSubstitutions = snap.maxSubstitutions,
            maxSubstitutionWindows = snap.maxSubstitutionWindows
        )
        return true
    }

    /**
     * Attaches the live engine's snapshot to a career so a partially played match
     * is written to disk with everything else. Returns the career unchanged when
     * no match is active.
     */
    private fun snapshotInto(career: Career): Career {
        val engine = _engine ?: return career
        val matchDay = _matchDay.value ?: return career
        val userIsHome = matchDay.match.homeClubId == career.userClubId
        val mode = if (matchDay.mode == MatchMode.QUICK) MatchModePersist.QUICK else MatchModePersist.PLAY
        val snap = engine.snapshot(
            matchId = matchDay.match.id,
            leagueId = matchDay.match.leagueId,
            matchday = matchDay.match.matchday,
            userIsHome = userIsHome,
            mode = mode,
            liveXi = _liveXi,
            liveBench = _liveBench
        ).copy(suspended = !matchDay.simulating)
        return career.copy(inProgressMatch = snap)
    }

    /** Queues a substitution. Applied at the next break (half time or immediately). */
    fun planSubstitution(playerOffId: Long, playerOnId: Long) {
        val matchDay = _matchDay.value ?: return
        val existing = matchDay.plannedSubstitutions
            .filterNot { it.playerOffId == playerOffId || it.playerOnId == playerOnId }
        _matchDay.value = matchDay.copy(
            plannedSubstitutions = existing + PlannedSubstitution(playerOffId, playerOnId, minute = 45)
        )
    }

    /** Removes a queued substitution. */
    fun cancelSubstitution(playerOnId: Long) {
        val matchDay = _matchDay.value ?: return
        _matchDay.value = matchDay.copy(
            plannedSubstitutions = matchDay.plannedSubstitutions.filterNot { it.playerOnId == playerOnId }
        )
    }

    /**
     * Starts the prepared fixture.
     *
     * [MatchMode.PLAY] advances the engine minute by minute over roughly ten real
     * minutes; [MatchMode.QUICK] compresses the same match into about ten seconds.
     * In both cases the engine stops at half time and waits for the manager.
     */
    fun startMatch(mode: MatchMode = MatchMode.PLAY) {
        val career = _career.value ?: return
        val matchDay = _matchDay.value ?: return
        if (matchDay.started || matchDay.isPlayed) return

        // Keep the user's XI legal before kick-off.
        val repaired = SelectionRepair.repair(
            career.selection, career.squadOf(career.userClubId), career.tactics.formation
        )
        val base = career.copy(selection = repaired)
        val (homeInput, awayInput) = SeasonEngine.buildTeamInputs(base, matchDay.match)

        val engine = ProgressiveMatchEngine(
            home = homeInput,
            away = awayInput,
            seedRandom = SerializableRandom(rngFor(base).nextLong()),
            homeAdvantage = true,
            determinism = SeasonEngine.determinismFor(base),
            rules = SeasonEngine.rulesFor(matchDay.match)
        )
        _engine = engine
        _engineCareer = base
        val userSelection = if (matchDay.isHome) homeInput.selection else awayInput.selection
        _liveXi = userSelection.startingXi.map { it.playerId }
        _liveBench = userSelection.substitutes
        _matchDay.value = matchDay.copy(
            started = true,
            simulating = true,
            mode = mode,
            userFormationName = base.tactics.formation.name,
            homeOnPitch = homeInput.selection.startingXi.map { it.playerId },
            awayOnPitch = awayInput.selection.startingXi.map { it.playerId },
            benchIds = userSelection.substitutes,
            maxSubstitutions = SeasonEngine.rulesFor(matchDay.match).maxSubstitutions,
            maxSubstitutionWindows = SeasonEngine.rulesFor(matchDay.match).maxSubstitutionWindows
        )
        // Clear any stale pending match and write the fresh snapshot immediately,
        // so an app restart in the first minute still resumes this match.
        _career.value = base.copy(inProgressMatch = null)
        persistSnapshotIfAny()
        runEngineLoop()
    }

    /**
     * The live loop. It repeatedly asks the engine to advance, then republishes the
     * state. The cadence differs by mode but the underlying engine is identical.
     */
    private fun runEngineLoop() {
        engineJob?.cancel()
        engineJob = viewModelScope.launch {
            val engine = _engine ?: return@launch
            try {
                while (true) {
                    // If the match-day screen was dismissed (and the snapshot saved),
                    // stop advancing: an invisible match must never run on.
                    val mode = _matchDay.value?.mode ?: return@launch
                    val slice = if (mode == MatchMode.QUICK) 45 else 1
                    // Play Match: 90 minutes spread over ~9-10 real minutes at the
                    // normal animation speed; Quick Sim: two 45-minute chunks.
                    val delayMs = if (mode == MatchMode.QUICK) 1500L
                    else (animationMultiplier() * 2L).coerceAtLeast(60L)

                    val produced = engine.advance(slice)
                    publishEngineState(engine, produced)
                    maybeAiReact(engine)
                    when {
                        engine.isHalfTime -> {
                            val updated = _matchDay.value ?: return@launch
                            _matchDay.value = updated.copy(
                                simulating = false,
                                awaitingHalfTime = true,
                                phase = MatchPhase.HALF_TIME
                            )
                            playSound(SoundCue.WHISTLE)
                            persistSnapshotIfAny()
                            return@launch
                        }
                        engine.needsExtraTime -> {
                            val updated = _matchDay.value ?: return@launch
                            _matchDay.value = updated.copy(simulating = false, awaitingExtraTime = true)
                            persistSnapshotIfAny()
                            return@launch
                        }
                        engine.isFinished -> {
                            completeMatch(engine)
                            return@launch
                        }
                    }
                    delay(delayMs)
                }
            } finally {
                // Whatever happened — normal finish, cancellation, or an unexpected
                // error — the busy flag must never be left set, otherwise the whole
                // UI appears frozen and no button responds.
                if (!(_matchDay.value?.isPlayed ?: false)) _isBusy.value = false
            }
        }
    }

    /** Republishes the engine's live state into the observable match-day state. */
    private fun publishEngineState(engine: ProgressiveMatchEngine, produced: List<MatchEvent>) {
        val matchDay = _matchDay.value ?: return
        val (homeStats, awayStats) = engine.stats()
        val (hg, ag) = engine.score()
        val (domHome, _) = engine.domination()
        val feed = matchDay.feed + produced.map { event ->
            MatchFeedItem(
                minute = event.displayMinute,
                type = event.type,
                text = feedText(event),
                isUserClub = event.clubId == (careerUserClubId() ?: -1L)
            )
        }
        _matchDay.value = matchDay.copy(
            minute = engine.clockMinute,
            phase = engine.currentPhase,
            homeGoals = hg,
            awayGoals = ag,
            homeStats = homeStats,
            awayStats = awayStats,
            feed = feed.takeLast(80),
            homeDomination = domHome,
            lastEventMinute = produced.lastOrNull()?.minute ?: matchDay.lastEventMinute,
            substitutionsMade = engine.substitutionsMade(careerUserClubId() ?: -1L),
            substitutionWindowsUsed = engine.windowsConsumed(careerUserClubId() ?: -1L)
        )
        // A goal is worth hearing about.
        if (produced.any { it.type == MatchEventType.GOAL }) playSound(SoundCue.GOAL)
    }

    private fun careerUserClubId(): Long? = _career.value?.userClubId

    /**
     * Lightweight AI bench management during a live match. The AI side reacts to a
     * tiring outfielder once per ten minutes, and only while the match is running.
     * Reads the engine's own active input so it stays consistent with the state the
     * engine is simulating, rather than rebuilding a fresh selection.
     */
    private fun maybeAiReact(engine: ProgressiveMatchEngine) {
        val matchDay = _matchDay.value ?: return
        if (engine.isHalfTime || engine.isFinished || engine.needsExtraTime) return
        val minute = matchDay.minute
        if (minute < 55 || minute % 10 != 0) return
        val career = _career.value ?: return
        val userIsHome = matchDay.match.homeClubId == career.userClubId
        val events = engine.reactToFatigue(engine.activeInput(isHome = !userIsHome), isHome = !userIsHome)
        if (events.isEmpty()) return
        val latest = _matchDay.value ?: return
        _matchDay.value = latest.copy(
            feed = (latest.feed + events.map {
                MatchFeedItem(it.displayMinute, it.type, feedText(it), false)
            }).takeLast(80)
        )
    }

    private fun feedText(event: MatchEvent): String = when (event.type) {
        MatchEventType.KICK_OFF -> event.detail
        MatchEventType.GOAL -> "GOAL - ${event.playerName}" +
            (if (event.secondaryPlayerName.isNotBlank()) " (assist ${event.secondaryPlayerName})" else "")
        MatchEventType.YELLOW_CARD -> "Yellow card - ${event.playerName}"
        MatchEventType.SECOND_YELLOW -> "Second yellow - ${event.playerName} sent off"
        MatchEventType.RED_CARD -> "Red card - ${event.playerName} sent off"
        MatchEventType.SUBSTITUTION -> "Substitution - ${event.detail}"
        MatchEventType.INJURY -> "Injury - ${event.playerName} ${event.detail}"
        MatchEventType.HALF_TIME -> event.detail
        MatchEventType.FULL_TIME -> event.detail
        MatchEventType.EXTRA_TIME_START -> event.detail
        MatchEventType.PENALTY_SHOOTOUT_GOAL -> event.detail
        MatchEventType.INFO -> event.detail
        else -> event.detail.ifBlank { event.type.name }
    }

    /**
     * Half-time break. The manager can take as long as they like; nothing advances
     * until they press continue. Their tactical and substitution changes are handed
     * to the engine, which uses them for the whole second half.
     */
    fun continueSecondHalf() {
        val engine = _engine ?: return
        val matchDay = _matchDay.value ?: return
        if (!matchDay.awaitingHalfTime) return

        val career = _career.value ?: return
        val userIsHome = matchDay.match.homeClubId == career.userClubId

        // Build the updated input for the user's side from the current career state.
        val updatedUser = buildUserInput(career, matchDay, userIsHome)
        val (_, baseAway) = SeasonEngine.buildTeamInputs(career, matchDay.match)
        val awayInput = if (userIsHome) baseAway else updatedUser

        // AI side reacts at the break on its own.
        val aiInput = if (userIsHome) awayInput else updatedUser
        engine.applyDefaultHalfTimeSubs(aiInput, isHome = !userIsHome)

        val changeEvents = engine.applyHalfTimeChanges(
            homeInput = if (userIsHome) updatedUser else null,
            awayInput = if (userIsHome) null else updatedUser
        )

        val started = engine.beginSecondHalf()
        val (hg, ag) = engine.score()
        val (hStats, aStats) = engine.stats()
        // Half-time changes may have swapped personnel; resync the tracked XI/bench.
        val liveNow = engine.activeInput(userIsHome).selection
        _liveXi = liveNow.startingXi.map { it.playerId }
        _liveBench = liveNow.substitutes
        _matchDay.value = matchDay.copy(
            awaitingHalfTime = false,
            simulating = true,
            phase = MatchPhase.SECOND_HALF,
            homeGoals = hg,
            awayGoals = ag,
            homeStats = hStats,
            awayStats = aStats,
            homeOnPitch = engine.activeInput(true).selection.startingXi.map { it.playerId },
            awayOnPitch = engine.activeInput(false).selection.startingXi.map { it.playerId },
            plannedSubstitutions = emptyList(),
            feed = (matchDay.feed + (changeEvents + started).map {
                MatchFeedItem(it.displayMinute, it.type, feedText(it), it.clubId == career.userClubId)
            }).takeLast(80)
        )
        persistSnapshotIfAny()
        runEngineLoop()
    }

    /** Continues into extra time when a knockout tie is level after ninety. */
    fun continueExtraTime() {
        val engine = _engine ?: return
        val matchDay = _matchDay.value ?: return
        if (!matchDay.awaitingExtraTime) return
        val events = engine.beginExtraTime()
        _matchDay.value = matchDay.copy(
            awaitingExtraTime = false,
            simulating = true,
            phase = MatchPhase.EXTRA_TIME_FIRST,
            feed = (matchDay.feed + events.map {
                MatchFeedItem(it.displayMinute, it.type, feedText(it), it.clubId == (careerUserClubId() ?: -1L))
            }).takeLast(80)
        )
        persistSnapshotIfAny()
        runEngineLoop()
    }

    /**
     * Applies a live substitution to the players actually on the pitch.
     *
     * The substitution is applied directly to the engine's active selection rather
     * than queued, and the tracked live XI/bench are updated, so a second change
     * sees the true on-pitch eleven and can never re-introduce a player who has
     * already been withdrawn.
     */
    fun makeLiveSubstitution(playerOffId: Long, playerOnId: Long) {
        val engine = _engine ?: return
        val matchDay = _matchDay.value ?: return
        val career = _career.value ?: return
        val userIsHome = matchDay.match.homeClubId == career.userClubId

        if (playerOffId !in _liveXi) return
        if (playerOnId !in _liveBench) return

        val current = engine.activeInput(userIsHome)
        val newXi = _liveXi.map { if (it == playerOffId) playerOnId else it }
        val newBench = _liveBench - playerOnId

        val updatedSelection = current.selection.copy(
            startingXi = current.selection.startingXi.map { slot ->
                if (slot.playerId == playerOffId) slot.copy(playerId = playerOnId) else slot
            },
            substitutes = newBench
        )

        val events = engine.applyDirectSubstitution(
            current.copy(selection = updatedSelection),
            isHome = userIsHome,
            playerOffId = playerOffId,
            playerOnId = playerOnId
        )
        if (events.isEmpty()) return

        _liveXi = newXi
        _liveBench = newBench
        _engineCareer = _engineCareer?.copy(selection = updatedSelection)

        val latest = _matchDay.value ?: return
        _matchDay.value = latest.copy(
            homeOnPitch = if (userIsHome) newXi else latest.homeOnPitch,
            awayOnPitch = if (!userIsHome) newXi else latest.awayOnPitch,
            feed = (latest.feed + events.map {
                MatchFeedItem(it.displayMinute, it.type, feedText(it), true)
            }).takeLast(80),
            lastEventMinute = events.last().minute,
            substitutionsMade = engine.substitutionsMade(career.userClubId),
            substitutionWindowsUsed = engine.windowsConsumed(career.userClubId)
        )
        playSound(SoundCue.CLICK)
        persistSnapshotIfAny()
    }

    /** The user's XI currently on the pitch, for the live substitution screen. */
    fun liveXi(): List<Long> = _liveXi

    /** The bench still available, for the live substitution screen. */
    fun liveBench(): List<Long> = _liveBench

    /**
     * The tactics the live engine is actually using right now, which is the
     * authoritative set for a paused match (the career copy is written in step,
     * but the engine may hold a repaired selection the career does not yet).
     */
    fun liveTactics(): Tactics {
        val engine = _engine ?: return _career.value?.tactics ?: Tactics.DEFAULT
        val userIsHome = _matchDay.value?.let { it.match.homeClubId == _career.value?.userClubId } ?: true
        return engine.activeInput(userIsHome).tactics
    }

    /** Builds the user's current team input, optionally overriding their XI. */
    private fun buildUserInput(
        career: Career,
        matchDay: MatchDayState,
        userIsHome: Boolean,
        plannedSubstitutions: List<PlannedSubstitution> = matchDay.plannedSubstitutions
    ): MatchTeamInput {
        val (homeInput, awayInput) = SeasonEngine.buildTeamInputs(career, matchDay.match)
        val user = if (userIsHome) homeInput else awayInput
        return user.copy(plannedSubstitutions = plannedSubstitutions)
    }

    /**
     * Applies a tactical change to the live match immediately. The engine uses the
     * new instructions for every remaining minute, so changing mentality, tempo or
     * formation during play genuinely reshapes the game.
     */
    fun applyLiveTactics(tactics: Tactics) {
        val engine = _engine ?: return
        val career = _career.value ?: return
        val matchDay = _matchDay.value ?: return
        val userIsHome = matchDay.match.homeClubId == career.userClubId

        // Repair the selection for the new shape so the engine receives a legal XI.
        val repaired = SelectionRepair.repair(
            career.selection, career.squadOf(career.userClubId), tactics.formation
        )
        val updatedCareer = career.copy(tactics = tactics, selection = repaired)
        val current = engine.activeInput(userIsHome)
        val (homeInput, awayInput) = SeasonEngine.buildTeamInputs(updatedCareer, matchDay.match)
        val rebuilt = if (userIsHome) homeInput else awayInput

        // A change of shape alters which players occupy which roles; a pure tactical
        // tweak (mentality, tempo, pressing) keeps the same XI on the pitch.
        val events = if (tactics.formationId != current.tactics.formationId) {
            engine.applyFormationChange(
                rebuilt.copy(plannedSubstitutions = current.plannedSubstitutions),
                isHome = userIsHome
            )
        } else {
            engine.updateActiveInput(
                rebuilt.copy(plannedSubstitutions = current.plannedSubstitutions),
                isHome = userIsHome
            )
            emptyList()
        }
        // Persist so the change survives the match and is used next week.
        updateCareer { it.copy(tactics = tactics, selection = repaired) }

        // A formation change can swap personnel; keep the tracked live XI/bench
        // honest so subsequent substitutions work from the real on-pitch eleven.
        if (tactics.formationId != current.tactics.formationId) {
            val liveNow = engine.activeInput(userIsHome).selection
            _liveXi = liveNow.startingXi.map { it.playerId }
            _liveBench = liveNow.substitutes
        }

        val latest = _matchDay.value ?: return
        _matchDay.value = latest.copy(
            homeOnPitch = if (userIsHome) engine.activeInput(true).selection.startingXi.map { it.playerId }
            else latest.homeOnPitch,
            awayOnPitch = if (!userIsHome) engine.activeInput(false).selection.startingXi.map { it.playerId }
            else latest.awayOnPitch,
            feed = (latest.feed + events.map {
                MatchFeedItem(it.displayMinute, it.type, feedText(it), it.clubId == career.userClubId)
            }).takeLast(80),
            substitutionsMade = engine.substitutionsMade(career.userClubId),
            substitutionWindowsUsed = engine.windowsConsumed(career.userClubId)
        )
        persistSnapshotIfAny()
    }

    /** Called when the engine reports the match is over. */
    private fun completeMatch(engine: ProgressiveMatchEngine) {
        val matchDay = _matchDay.value ?: return
        val career = _engineCareer ?: _career.value ?: return
        // The live match is settled: clear the pending snapshot so a restart never
        // replays a fixture that has already produced a result.
        val careerForSettlement = career.copy(inProgressMatch = null)
        viewModelScope.launch {
            _isBusy.value = true
            val outcome = runCatching {
                withContext(Dispatchers.Default) {
                    val random = rngFor(careerForSettlement)
                    val result = engine.toResult(
                        matchId = matchDay.match.id,
                        leagueId = matchDay.match.leagueId,
                        matchday = matchDay.match.matchday
                    )
                    // Settle the rest of the matchday card, then fold the result in.
                    var current = SeasonEngine.simulateOtherFixtures(careerForSettlement, random)
                    current = SeasonEngine.applyResult(current, matchDay.match, result, random, userMatch = true)
                    current to result
                }
            }
            val settled = outcome.getOrNull()
            if (settled == null) {
                _isBusy.value = false
                _message.value = "The match could not be completed. Please try again."
                return@launch
            }
            _career.value = settled.first.copy(inProgressMatch = null)
            _matchDay.value = matchDay.copy(
                simulating = false,
                phase = MatchPhase.FINISHED,
                result = settled.second,
                positionAfter = settled.first.userLeaguePosition,
                matchdayFinance = settled.first.lastMatchdayFinance,
                feed = matchDay.feed + MatchFeedItem("FT", MatchEventType.FULL_TIME,
                    "Full time: ${settled.second.homeGoals} - ${settled.second.awayGoals}",
                    true)
            )
            _engine = null
            _engineCareer = null
            _isBusy.value = false
            playSound(SoundCue.WHISTLE)
            persist(settled.first.copy(inProgressMatch = null))
        }
    }

    /** Pauses the live match so the manager can make changes without losing time. */
    fun pauseMatch() {
        engineJob?.cancel()
        // Cancel only signals the loop; the current slice may have already advanced
        // the engine and mutated the match state without publishing yet. Pull the
        // authoritative clock/score/stats back out before saving, so the snapshot we
        // persist matches the engine the resume will rebuild from.
        syncClockFromEngine()
        _matchDay.value = _matchDay.value?.copy(simulating = false)
        persistSnapshotIfAny()
    }

    /**
     * Mirrors the engine's current clock and score into the observable state. The
     * loop keeps these in step each slice, but a pause can land between an engine
     * advance and its publish, so the snapshot must re-read the engine directly.
     */
    private fun syncClockFromEngine() {
        val engine = _engine ?: return
        val matchDay = _matchDay.value ?: return
        val (hg, ag) = engine.score()
        _matchDay.value = matchDay.copy(
            minute = engine.clockMinute,
            phase = engine.currentPhase,
            homeGoals = hg,
            awayGoals = ag
        )
    }

    /** Resumes a paused live match. */
    fun resumeMatch() {
        val matchDay = _matchDay.value ?: return
        if (!matchDay.started || matchDay.isPlayed || matchDay.simulating) return
        if (matchDay.awaitingHalfTime || matchDay.awaitingExtraTime) return
        _matchDay.value = matchDay.copy(simulating = true)
        runEngineLoop()
    }

    /**
     * Switches a paused Play Match into compressed Quick Sim for the rest of the
     * current match — from this exact minute, score, set of cards and
     * substitutions onward. The engine is not rebuilt, so nothing is lost or
     * regenerated: only the pacing changes from one-minute slices to whole halves.
     */
    fun quickSimFromHere() {
        val matchDay = _matchDay.value ?: return
        if (!matchDay.started || matchDay.isPlayed) return
        // Already running fast, or at a break the manager must action themselves.
        if (matchDay.awaitingHalfTime || matchDay.awaitingExtraTime) return
        if (matchDay.mode == MatchMode.QUICK && matchDay.simulating) return
        _matchDay.value = matchDay.copy(mode = MatchMode.QUICK, simulating = true)
        runEngineLoop()
    }

    /**
     * Leaves a live match without finishing it. Nothing is conceded: the engine
     * state is written into the save, so opening the next match resumes at the
     * same minute with the same score. This is what the system back button calls,
     * so navigating away can never abandon a match in progress.
     */
    fun leaveMatch() {
        engineJob?.cancel()
        _matchDay.value = _matchDay.value?.copy(simulating = false)
        persistSnapshotIfAny()
        _matchDay.value = null
    }

    /** True while a live match is in progress and can be paused or managed. */
    val hasLiveMatch: Boolean get() = _engine != null

    /** Writes the current live engine state into the save if a match is active. */
    private fun persistSnapshotIfAny() {
        val career = _career.value ?: return
        if (_engine == null || _matchDay.value?.isPlayed == true) return
        val snapshotted = snapshotInto(career)
        _career.value = snapshotted
        persist(snapshotted)
    }

    /**
     * Plays the prepared fixture instantly (legacy/test path). Simulates the whole
     * match and folds it in, bypassing the live presentation.
     */
    fun playMatch(onComplete: (MatchResult) -> Unit = {}) {
        val career = _career.value ?: return
        val matchDay = _matchDay.value ?: return
        if (matchDay.isPlayed) return

        viewModelScope.launch {
            _isBusy.value = true
            val outcome = withContext(Dispatchers.Default) {
                var current = career
                val random = rngFor(current)
                val repaired = SelectionRepair.repair(
                    current.selection, current.squadOf(current.userClubId), current.tactics.formation
                )
                current = current.copy(selection = repaired)
                current = SeasonEngine.simulateOtherFixtures(current, random)
                val (updated, result) = SeasonEngine.simulateFixture(
                    current, matchDay.match, random, userMatch = true,
                    plannedSubstitutions = matchDay.plannedSubstitutions
                )
                updated to result
            }
            _career.value = outcome.first
            _matchDay.value = matchDay.copy(
                started = true, phase = MatchPhase.FINISHED, result = outcome.second,
                homeGoals = outcome.second.homeGoals, awayGoals = outcome.second.awayGoals,
                homeStats = outcome.second.homeStats, awayStats = outcome.second.awayStats,
                positionAfter = outcome.first.userLeaguePosition,
                matchdayFinance = outcome.first.lastMatchdayFinance
            )
            _isBusy.value = false
            persist(outcome.first.copy(inProgressMatch = null))
            onComplete(outcome.second)
        }
    }

    /**
     * Plays any remaining fixtures in the same matchday week that are not the
     * headline fixture, so a busy week (a league game plus a European tie) is
     * fully resolved before the calendar advances.
     */
    fun finishMatchday() {
        val career = _career.value ?: return
        val matchDay = _matchDay.value ?: return
        val remaining = matchDay.pendingOtherFixtures
        if (remaining.isEmpty()) return

        viewModelScope.launch {
            _isBusy.value = true
            val updated = withContext(Dispatchers.Default) {
                var current = career
                val random = rngFor(current)
                for (fixture in remaining) {
                    if (fixture.isPlayed) continue
                    val (next, _) = SeasonEngine.simulateFixture(current, fixture, random, userMatch = true)
                    current = next
                }
                current
            }
            _career.value = updated
            _isBusy.value = false
            persist(updated)
        }
    }

    /** Called after the match-day screen is dismissed: trains, recovers, advances. */
    fun advanceAfterMatch() {
        val career = _career.value ?: return
        engineJob?.cancel()
        _engine = null
        _engineCareer = null
        val base = career.copy(inProgressMatch = null)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val updated = withContext(Dispatchers.Default) {
                    var current = base
                    val random = rngFor(current)
                    val matchday = current.matchdayIndex + 1

                    // Any of the user's fixtures this matchday that have not yet been
                    // played (typically a European tie) are resolved now.
                    val leftover = current.fixtures.filter {
                        !it.isPlayed && it.involves(current.userClubId) && it.matchday == matchday
                    }
                    for (fixture in leftover) {
                        val (next, _) = SeasonEngine.simulateFixture(current, fixture, random, userMatch = true)
                        current = next
                    }

                    // Ensure every other league and the rest of the European card has
                    // been played for this matchday before the calendar advances.
                    current = SeasonEngine.simulateOtherFixtures(current, random)
                    current = SeasonEngine.advanceWeek(current, random)
                    current = FinanceEngine.applyFinancialPressure(current)
                    // Progress the Champions League bracket once its rounds complete.
                    var idc = current.idCounter
                    current = ChampionsLeagueEngine.progress(current, random) { ++idc }
                    current = current.copy(idCounter = idc)
                    if (current.matchdayIndex >= current.totalMatchdays()) {
                        current = current.copy(phase = GamePhase.SEASON_ENDED)
                    }
                    current
                }
                _career.value = updated
                _matchDay.value = null
                persist(updated)
            } catch (t: Throwable) {
                _message.value = "The week could not be advanced. Please try again."
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Abandons an unplayed match-day screen. Nothing has been simulated yet, so
     * the fixture simply stays as the next match.
     */
    fun cancelMatch() {
        engineJob?.cancel()
        _engine = null
        _engineCareer = null
        _matchDay.value = null
    }

    /** The animation speed multiplier from the player's settings. */
    private fun animationMultiplier(): Long =
        (settings.value.animationSpeed.multiplier * 1000f).toLong().coerceAtLeast(60L)

    /**
     * Instant quick-sim: prepares and fully simulates the next fixture in one
     * step, then advances the week. Used for internal/test flows; the player-facing
     * Quick Sim runs the live engine in compressed form instead.
     */
    fun quickSimNextMatch() {
        if (!prepareNextMatch()) return
        playMatch { advanceAfterMatch() }
    }

    fun startNextSeason() {
        val career = _career.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val updated = withContext(Dispatchers.Default) {
                SeasonEngine.endSeason(career, rngFor(career))
            }
            _career.value = updated
            _matchDay.value = null
            _isBusy.value = false
            persist(updated)
        }
    }

    // -------------------------------------------------------------- transfers

    fun searchTransferMarket(query: String, position: com.footymanager.simulator.domain.model.Position?): List<Player> {
        val career = _career.value ?: return emptyList()
        val clubNames = career.clubs.associate { it.id to it.name.lowercase() }
        val q = query.trim().lowercase()
        return TransferEngine.marketPlayers(career)
            .filter { player ->
                q.isEmpty() ||
                    player.matches(query) ||
                    (player.clubId?.let { clubNames[it]?.contains(q) } == true)
            }
            .filter { position == null || it.position == position }
    }

    fun makeOffer(playerId: Long, fee: Long, wage: Long, contractYears: Int) {
        playSound(SoundCue.CLICK)
        updateCareer { career ->
            val player = career.player(playerId) ?: return@updateCareer career
            TransferEngine.createUserOffer(
                career = career,
                player = player,
                offerPackage = com.footymanager.simulator.domain.model.TransferPackage(fee = fee),
                terms = com.footymanager.simulator.domain.model.ContractTerms(
                    wagePerWeek = wage,
                    contractYears = contractYears
                )
            )
        }
        soundForLatestOffer()
    }

    /** Submits a bid as a package (cash plus an optional makeweight) at once. */
    fun makeOfferPackage(
        playerId: Long,
        offerPackage: com.footymanager.simulator.domain.model.TransferPackage,
        terms: com.footymanager.simulator.domain.model.ContractTerms
    ) {
        playSound(SoundCue.CLICK)
        updateCareer { career ->
            val player = career.player(playerId) ?: return@updateCareer career
            TransferEngine.createUserOffer(career, player, offerPackage, terms)
        }
        soundForLatestOffer()
    }

    /** Accepts the selling club's counter-offer and immediately tries the player. */
    fun acceptCounter(offerId: Long) {
        playSound(SoundCue.CLICK)
        updateCareer { career -> TransferEngine.acceptCounter(career, offerId) }
        soundForLatestOffer()
    }

    /** Submits improved personal terms after the player asked for more. */
    fun submitPlayerTerms(offerId: Long, terms: com.footymanager.simulator.domain.model.ContractTerms) {
        updateCareer { career -> TransferEngine.submitPlayerTerms(career, offerId, terms) }
        soundForLatestOffer()
    }

    /** Cancels a bid at any stage of the negotiation. */
    fun cancelOffer(offerId: Long) {
        updateCareer { career -> TransferEngine.cancelOffer(career, offerId) }
    }

    private fun soundForLatestOffer() {
        when (_career.value?.pendingOffers?.lastOrNull()?.status) {
            OfferStatus.COMPLETED, OfferStatus.ACCEPTED -> playSound(SoundCue.SUCCESS)
            OfferStatus.REJECTED, OfferStatus.COLLAPSED -> playSound(SoundCue.FAILURE)
            else -> Unit
        }
    }

    /** The package the selling club would require right now, for the UI. */
    fun requiredPackageFor(player: Player): com.footymanager.simulator.domain.model.TransferPackage {
        val career = _career.value ?: return com.footymanager.simulator.domain.model.TransferPackage(player.value)
        return TransferEngine.requiredPackage(career, player, career.userClubId)
    }

    fun resolveOffer(offerId: Long) {
        updateCareer { career -> TransferEngine.resolveOffer(career, offerId, rngFor(career)) }
        soundForLatestOffer()
    }

    fun withdrawOffer(offerId: Long) {
        updateCareer { career -> TransferEngine.cancelOffer(career, offerId) }
    }

    fun sellPlayer(playerId: Long, fee: Long, buyerClubId: Long) {
        updateCareer { career -> TransferEngine.sellPlayer(career, playerId, fee, buyerClubId) }
    }

    fun releasePlayer(playerId: Long) {
        updateCareer { career -> TransferEngine.releasePlayer(career, playerId) }
    }

    fun interestedBuyers(playerId: Long): List<Pair<Club, Long>> {
        val career = _career.value ?: return emptyList()
        return TransferEngine.interestedBuyers(career, playerId, rngFor(career))
    }

    fun askingPriceFor(player: Player): Long {
        val career = _career.value ?: return player.value
        return TransferEngine.askingPrice(career, player, player.clubId)
    }

    fun expectedWageFor(player: Player): Long {
        val career = _career.value ?: return player.wagePerWeek
        return TransferEngine.expectedWage(career, player, career.userClubId)
    }

    fun pendingOffers(): List<TransferOffer> =
        _career.value?.pendingOffers?.filter {
            it.status == OfferStatus.PENDING ||
                it.status == OfferStatus.REJECTED ||
                it.status == OfferStatus.COUNTERED
        } ?: emptyList()

    // -------------------------------------------------------------- selling

    /** The live sale negotiation, if one is open. */
    fun pendingSale(): SaleNegotiation? = _career.value?.pendingSale

    /** Every player the manager has listed for sale, with their asking price. */
    fun transferListings(): List<TransferListing> = _career.value?.transferListings.orEmpty()

    /** Lists a player for sale and generates the interested clubs immediately. */
    fun listPlayerForSale(playerId: Long, askingPrice: Long) {
        playSound(SoundCue.CLICK)
        val career = _career.value ?: return
        updateCareer { c -> TransferEngine.listPlayer(c, playerId, askingPrice, rngFor(c)) }
        when (_career.value?.pendingSale?.bids?.size) {
            0 -> playSound(SoundCue.FAILURE)
            else -> playSound(SoundCue.SUCCESS)
        }
    }

    /** Changes the asking price for a listed player and refreshes the interest. */
    fun setAskingPrice(playerId: Long, askingPrice: Long) {
        val career = _career.value ?: return
        updateCareer { c -> TransferEngine.updateAskingPrice(c, playerId, askingPrice, rngFor(c)) }
    }

    /** Counts how many clubs would be interested at a given asking price. */
    fun interestedClubCount(player: Player, askingPrice: Long): Int {
        val career = _career.value ?: return 0
        return TransferEngine.interestedClubCount(career, player, askingPrice)
    }

    /** Counters one club's bid; every club responds immediately. */
    fun counterSaleBid(clubId: Long, amount: Long) {
        playSound(SoundCue.CLICK)
        val career = _career.value ?: return
        updateCareer { c -> TransferEngine.counterSaleBid(c, clubId, amount, rngFor(c)) }
        when (_career.value?.pendingSale?.acceptedBid) {
            null -> Unit
            else -> playSound(SoundCue.SUCCESS)
        }
    }

    /** Accepts a club's bid and completes the sale at once. */
    fun acceptSaleBid(clubId: Long) {
        playSound(SoundCue.SUCCESS)
        updateCareer { c -> TransferEngine.acceptSaleBid(c, clubId) }
    }

    /** Rejects one club's bid. */
    fun rejectSaleBid(clubId: Long) {
        val career = _career.value ?: return
        updateCareer { c -> TransferEngine.rejectSaleBid(c, clubId) }
    }

    /** Abandons the sale and clears the listing. */
    fun cancelSale() {
        val career = _career.value ?: return
        updateCareer { c -> TransferEngine.cancelSale(c) }
    }

    // -------------------------------------------------------------- finances

    fun financeSummary(): FinanceEngine.FinanceSummary? {
        val career = _career.value ?: return null
        return FinanceEngine.summarise(career)
    }

    // --------------------------------------------------------------- stadium

    /** Starts a stadium expansion, deducting the cost from the club balance. */
    fun upgradeStadium(): String {
        val career = _career.value ?: return "No active career"
        val stadium = career.stadium
        if (!stadium.canExpand) return "The stadium cannot be expanded further right now."
        val (updated, cost) = StadiumEngine.beginExpansion(stadium)
        if (cost <= 0) return "The stadium cannot be expanded further right now."
        if (career.userClub.balance < cost) {
            return "Not enough money: £${TransferEngine.formatMoney(cost)} needed."
        }
        updateCareer { c ->
            var idc = c.idCounter
            idc++
            c.copy(
                stadium = updated,
                clubs = c.clubs.map {
                    if (it.id == c.userClubId) it.copy(balance = it.balance - cost) else it
                },
                ledger = (c.ledger + com.footymanager.simulator.domain.model.FinanceLedgerEntry(
                    id = idc,
                    date = c.date,
                    season = c.season,
                    description = "Stadium expansion to ${"%,d".format(updated.expansionTargetCapacity)} seats",
                    amount = -cost,
                    category = com.footymanager.simulator.domain.model.LedgerCategory.STADIUM
                )).takeLast(400),
                idCounter = idc
            )
        }
        return "Expansion started. It will be complete in ${com.footymanager.simulator.domain.model.Stadium.EXPANSION_WEEKS} weeks."
    }

    /** Sets the per-seat ticket price. */
    fun setTicketPrice(price: Int) {
        updateCareer { career ->
            career.copy(stadium = career.stadium.copy(ticketPrice = price.coerceIn(5, 150)))
        }
    }

    // ------------------------------------------------------------- sponsors

    /** Signs a sponsorship offer for the current season. */
    fun signSponsorship(offerId: Long): String {
        val career = _career.value ?: return "No active career"
        val offer = career.sponsorOffers.firstOrNull { it.id == offerId }
            ?: return "That sponsorship offer is no longer available."
        val sponsorship = SponsorshipEngine.sign(offer)
        updateCareer { c ->
            var idc = c.idCounter
            idc++
            c.copy(
                sponsorship = sponsorship,
                sponsorshipSeason = c.seasonNumber,
                sponsorOffers = emptyList(),
                clubs = c.clubs.map {
                    if (it.id == c.userClubId) it.copy(balance = it.balance + offer.upfront) else it
                },
                ledger = (c.ledger + com.footymanager.simulator.domain.model.FinanceLedgerEntry(
                    id = idc,
                    date = c.date,
                    season = c.season,
                    description = "${offer.name} sponsorship (upfront)",
                    amount = offer.upfront,
                    category = com.footymanager.simulator.domain.model.LedgerCategory.SPONSORSHIP
                )).takeLast(400),
                news = (c.news + com.footymanager.simulator.domain.model.NewsItem(
                    id = idc,
                    category = com.footymanager.simulator.domain.model.NewsCategory.TRANSFER,
                    headline = "${offer.name} becomes main sponsor",
                    body = "${c.userClub.name} have signed a ${offer.tier.lowercase()} deal with ${offer.name} " +
                        "worth £${TransferEngine.formatMoney(offer.upfront)} up front plus " +
                        "£${TransferEngine.formatMoney(offer.seasonal)} across the season. " +
                        "Bonus: ${offer.bonusCondition} (£${TransferEngine.formatMoney(offer.bonusAmount)}).",
                    date = c.date,
                    season = c.season,
                    clubId = c.userClubId
                )).takeLast(120),
                idCounter = idc,
                // Signing the deal completes the pre-season and opens the campaign.
                phase = GamePhase.IN_SEASON
            )
        }
        return "Signed a deal with ${offer.name}."
    }

    // ------------------------------------------------------------ rewarded ads

    /**
     * Grants the reward for a successfully completed rewarded ad. The allowance
     * resets once per calendar day and no reward is given once it is exhausted.
     */
    fun claimAdReward(epochDay: Long = System.currentTimeMillis() / 86_400_000L): Long {
        val career = _career.value ?: return 0L
        val (updated, reward) = career.adRewards.recordAd(epochDay)
        if (reward <= 0L) return 0L
        updateCareer { c ->
            var idc = c.idCounter
            idc++
            c.copy(
                adRewards = updated,
                clubs = c.clubs.map {
                    if (it.id == c.userClubId) it.copy(balance = it.balance + reward) else it
                },
                ledger = (c.ledger + com.footymanager.simulator.domain.model.FinanceLedgerEntry(
                    id = idc,
                    date = c.date,
                    season = c.season,
                    description = "Rewarded ad bonus",
                    amount = reward,
                    category = com.footymanager.simulator.domain.model.LedgerCategory.OTHER
                )).takeLast(400),
                idCounter = idc
            )
        }
        return reward
    }

    /** Refreshes the ad allowance for a new day without granting anything. */
    fun refreshAdAllowance(epochDay: Long = System.currentTimeMillis() / 86_400_000L) {
        val career = _career.value ?: return
        if (career.adRewards.allowanceDay == epochDay) return
        updateCareer { c -> c.copy(adRewards = c.adRewards.withDay(epochDay)) }
    }

    // ---------------------------------------------------------------- helpers

    private fun updateCareer(transform: (Career) -> Career) {
        val current = _career.value ?: return
        val updated = transform(current)
        _career.value = updated
        persist(updated)
    }

    /** Queues a write; only the most recent career is persisted when busy. */
    private fun persist(career: Career) {
        persistRequests.trySend(PersistRequest.Write(career))
    }

    /** Queues a delete on the same ordered channel as writes. */
    private fun persistDelete() {
        persistRequests.trySend(PersistRequest.Delete)
    }

    /**
     * A deterministic seed derived from the save and the current matchday. This
     * means reloading a save and replaying the same fixture produces the same
     * result, which keeps the simulation honest and prevents save-scumming.
     */
    private fun rngFor(career: Career): Random {
        val base = career.saveId.hashCode().toLong()
        val seed = base * 1_000_003L +
            career.seasonNumber * 7_919L +
            career.matchdayIndex * 104_729L +
            career.results.size * 1_299_709L
        return Random(seed)
    }

    fun formation(): Formation = _career.value?.tactics?.formation ?: Formation.F4231

    fun selection(): TeamSelection? = _career.value?.selection

    fun tactics(): Tactics = _career.value?.tactics ?: Tactics.DEFAULT

    override fun onCleared() {
        // Best-effort save when the process is torn down, using the same store
        // the rest of the game writes to.
        _career.value?.let { current ->
            kotlinx.coroutines.runBlocking {
                runCatching { saveRepository.save(current) }
            }
        }
        persistenceScope.cancel()
        sound.release()
        super.onCleared()
    }
}
