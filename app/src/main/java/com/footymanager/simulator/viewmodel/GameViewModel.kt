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
import com.footymanager.simulator.domain.engine.FinanceEngine
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.SelectionRepair
import com.footymanager.simulator.domain.engine.TransferEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.GamePhase
import com.footymanager.simulator.domain.model.LineupSlot
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.MatchResult
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.TeamSelection
import com.footymanager.simulator.domain.model.TrainingFocus
import com.footymanager.simulator.domain.model.TransferOffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Transient state for the match-day experience. */
data class MatchDayState(
    val match: Match,
    val opponent: Club,
    val isHome: Boolean,
    val opponentFormationName: String,
    val result: MatchResult? = null
) {
    val isPlayed: Boolean get() = result != null
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

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
            settingsRepository.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            _hasSave.value = saveRepository.hasSave()
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
        _career.value?.let { persist(it) }
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

    /** Prepares the match-day screen for the next fixture. */
    fun prepareNextMatch(): Boolean {
        val career = _career.value ?: return false
        val match = career.nextMatch() ?: return false
        val opponentId = match.opponentOf(career.userClubId)
        val opponent = career.clubOrThrow(opponentId)
        val opponentTactics = SeasonEngine.tacticsFor(career, opponentId, match.matchday)
        _matchDay.value = MatchDayState(
            match = match,
            opponent = opponent,
            isHome = match.isHomeFor(career.userClubId),
            opponentFormationName = opponentTactics.formation.name
        )
        return true
    }

    /**
     * Plays the prepared fixture. All other matches on the same matchday are
     * simulated at the same time so the league table stays consistent.
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

                // Keep the user's XI legal before kick-off.
                val repaired = SelectionRepair.repair(
                    current.selection,
                    current.squadOf(current.userClubId),
                    current.tactics.formation
                )
                current = current.copy(selection = repaired)

                current = SeasonEngine.simulateOtherFixtures(current, random)
                val (updated, result) = SeasonEngine.simulateFixture(
                    current, matchDay.match, random, userMatch = true
                )
                updated to result
            }
            _career.value = outcome.first
            _matchDay.value = matchDay.copy(result = outcome.second)
            _isBusy.value = false
            persist(outcome.first)
            onComplete(outcome.second)
        }
    }

    /** Called after the match-day screen is dismissed: trains, recovers, advances. */
    fun advanceAfterMatch() {
        val career = _career.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val updated = withContext(Dispatchers.Default) {
                var current = SeasonEngine.advanceWeek(career, rngFor(career))
                current = FinanceEngine.applyFinancialPressure(current)
                if (current.matchdayIndex >= current.totalMatchdays()) {
                    current = current.copy(phase = GamePhase.SEASON_ENDED)
                }
                current
            }
            _career.value = updated
            _matchDay.value = null
            _isBusy.value = false
            persist(updated)
        }
    }

    /**
     * Abandons an unplayed match-day screen. Nothing has been simulated yet, so
     * the fixture simply stays as the next match.
     */
    fun cancelMatch() {
        _matchDay.value = null
    }

    /** Quick-sim convenience: plays the next fixture and advances the week. */
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
        updateCareer { career ->
            val player = career.player(playerId) ?: return@updateCareer career
            TransferEngine.createUserOffer(career, player, fee, wage, contractYears)
        }
    }

    fun resolveOffer(offerId: Long) {
        updateCareer { career -> TransferEngine.resolveOffer(career, offerId, rngFor(career)) }
    }

    fun withdrawOffer(offerId: Long) {
        updateCareer { career ->
            career.copy(
                pendingOffers = career.pendingOffers.map {
                    if (it.id == offerId) it.copy(status = OfferStatus.WITHDRAWN, message = "Offer withdrawn") else it
                }
            )
        }
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
        _career.value?.pendingOffers?.filter { it.status == OfferStatus.PENDING || it.status == OfferStatus.REJECTED }
            ?: emptyList()

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
        super.onCleared()
    }
}
