package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.engine.ChampionsLeagueEngine
import com.footymanager.simulator.domain.engine.SponsorshipEngine
import com.footymanager.simulator.domain.model.BoardObjective
import com.footymanager.simulator.domain.model.BoardState
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.ChampionsLeagueState
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.europeanCompetitions
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.GamePhase
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.NewsCategory
import com.footymanager.simulator.domain.model.NewsItem
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.Stadium
import com.footymanager.simulator.domain.model.TableRow
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.TeamSelection
import com.footymanager.simulator.domain.model.TransferWindowState
import kotlin.random.Random

/**
 * Assembles a brand new career: clubs, generated squads, fixtures, an empty
 * league table, an initial starting XI and the board's season objectives.
 */
object CareerFactory {

    data class NewCareerRequest(
        val managerName: String,
        val clubId: Long,
        val difficulty: Difficulty,
        val seed: Long
    )

    fun create(request: NewCareerRequest): Career {
        val random = Random(request.seed)
        var idCounter = 0L
        val idProvider: () -> Long = { ++idCounter }

        val clubs = ClubDatabase.buildAll()

        val players = mutableListOf<Player>()
        for (club in clubs) {
            val curated = CuratedSquads.byClubName[club.name]
            players += if (curated != null) {
                // A hand-authored squad, topped up with generated cover so the
                // club always has a full 24 to pick from.
                val authored = CuratedSquadBuilder.build(
                    club = club,
                    designs = curated,
                    random = random,
                    idProvider = idProvider,
                    difficulty = request.difficulty.aiStrength
                )
                CuratedSquadBuilder.topUp(
                    club = club,
                    curated = authored,
                    template = PlayerGenerator.squadTemplate,
                    random = random,
                    idProvider = idProvider
                )
            } else {
                PlayerGenerator.generateSquad(
                    club = club,
                    random = random,
                    idProvider = idProvider,
                    difficulty = request.difficulty.aiStrength
                )
            }
        }

        val fixtures = mutableListOf<com.footymanager.simulator.domain.model.Match>()
        val table = mutableMapOf<String, List<TableRow>>()
        for (league in League.domestic) {
            val leagueClubs = clubs.filter { it.leagueId == league.id }
            if (leagueClubs.isEmpty()) continue
            fixtures += FixtureGenerator.generateLeagueFixtures(league, leagueClubs, random, idProvider)
            table[league.id] = leagueClubs.map { TableRow(clubId = it.id) }
        }

        // ---- Continental competitions (current 36-team league-phase format) ----
        val userClub = clubs.first { it.id == request.clubId }
        val baseCareer = Career(
            saveId = "seed",
            managerName = request.managerName,
            userClubId = userClub.id,
            season = Career.CURRENT_SEASON,
            seasonNumber = 1,
            difficulty = request.difficulty,
            date = SeasonCalendar.SEASON_START,
            clubs = clubs,
            players = emptyList(),
            fixtures = emptyList(),
            table = emptyMap()
        )
        val europeanStates = mutableMapOf<CompetitionType, ChampionsLeagueState>()
        for (competition in europeanCompetitions) {
            val (state, competitionFixtures) = ChampionsLeagueEngine.createSeason(
                career = baseCareer,
                season = Career.CURRENT_SEASON,
                startDate = SeasonCalendar.SEASON_START,
                random = random,
                idProvider = idProvider,
                competition = competition
            )
            europeanStates[competition] = state
            fixtures += competitionFixtures
        }
        val uclState = europeanStates.getValue(CompetitionType.CHAMPIONS_LEAGUE)
        val uelState = europeanStates.getValue(CompetitionType.EUROPA_LEAGUE)
        val ueclState = europeanStates.getValue(CompetitionType.CONFERENCE_LEAGUE)

        // Every fixture gets a real calendar date.
        val datedFixtures = SeasonCalendar.assignDates(fixtures, random)

        val userSquad = players.filter { it.clubId == userClub.id }
        val tactics = Tactics(formationId = userClub.formationId)
        val selection = SelectionHelper.autoPickBest(
            squad = userSquad,
            formation = Formation.byId(tactics.formationId)
        )

        val board = buildInitialBoard(userClub, request.difficulty)
        val stadium = Stadium.initial(userClub.stadiumName, userClub.stadiumCapacity, userClub.reputation)
        val sponsorOffers = SponsorshipEngine.generateOffers(userClub, random)

        val news = mutableListOf(
            NewsItem(
                id = idProvider(),
                category = NewsCategory.GENERAL,
                headline = "${request.managerName} appointed at ${userClub.name}",
                body = "The board has confirmed the appointment of ${request.managerName} as first-team manager. " +
                    "Season objective: ${userClub.boardExpectation}.",
                date = SeasonCalendar.SEASON_START,
                season = Career.CURRENT_SEASON,
                clubId = userClub.id
            ),
            NewsItem(
                id = idProvider(),
                category = NewsCategory.TRANSFER_WINDOW,
                headline = "Summer transfer window open",
                body = "Clubs may now buy and sell players. The window closes after matchday 3.",
                date = SeasonCalendar.SEASON_START,
                season = Career.CURRENT_SEASON
            ),
            NewsItem(
                id = idProvider(),
                category = NewsCategory.GENERAL,
                headline = "Sponsorship offers on the table",
                body = "The board has received sponsorship offers for the ${Career.CURRENT_SEASON} season. " +
                    "Choose one from the Sponsors screen.",
                date = SeasonCalendar.SEASON_START,
                season = Career.CURRENT_SEASON,
                clubId = userClub.id
            )
        )

        return Career(
            saveId = "career-" + request.seed.toString(16),
            managerName = request.managerName,
            userClubId = userClub.id,
            season = Career.CURRENT_SEASON,
            seasonNumber = 1,
            difficulty = request.difficulty,
            date = SeasonCalendar.SEASON_START,
            phase = GamePhase.PRE_SEASON,
            matchdayIndex = 0,
            tactics = tactics,
            selection = selection,
            clubs = clubs,
            players = players,
            fixtures = datedFixtures,
            table = table,
            news = news,
            board = board,
            transferWindow = TransferWindowState(),
            idCounter = idCounter,
            createdAtEpochMs = System.currentTimeMillis(),
            lastSavedEpochMs = System.currentTimeMillis(),
            stadium = stadium,
            sponsorOffers = sponsorOffers,
            championsLeague = uclState,
            europaLeague = uelState,
            conferenceLeague = ueclState
        )
    }

    private fun buildInitialBoard(club: Club, difficulty: Difficulty): BoardState {
        val objectives = mutableListOf(
            BoardObjective(
                title = "League",
                description = club.boardExpectation,
                status = com.footymanager.simulator.domain.model.ObjectiveStatus.ON_TRACK
            )
        )
        objectives += BoardObjective(
            title = "Finances",
            description = "Keep the wage bill within the £${"%,d".format(club.wageBudget)} weekly budget",
            status = com.footymanager.simulator.domain.model.ObjectiveStatus.ON_TRACK
        )
        objectives += BoardObjective(
            title = "Squad",
            description = "Develop at least one young player into a first-team regular",
            status = com.footymanager.simulator.domain.model.ObjectiveStatus.ON_TRACK
        )
        val confidence = (60 * difficulty.boardLeniency).toInt().coerceIn(35, 80)
        return BoardState(
            confidence = confidence,
            objectives = objectives,
            lastEvaluation = "The board welcomes you to ${club.name} and expects ${club.boardExpectation.lowercase()}."
        )
    }
}

/** Shared logic for filling a formation with the best available players. */
object SelectionHelper {

    /**
     * Picks the strongest legal XI for a formation, respecting injuries and
     * suspensions, and preferring players in their natural position.
     */
    fun autoPickBest(
        squad: List<Player>,
        formation: Formation
    ): TeamSelection {
        val available = squad.filter { it.isAvailable }
        if (available.isEmpty()) return TeamSelection()

        val used = mutableSetOf<Long>()
        val slots = mutableListOf<com.footymanager.simulator.domain.model.LineupSlot>()

        for ((index, role) in formation.roles.withIndex()) {
            val natural = role.naturalPosition
            val candidate = available
                .filter { it.id !in used }
                .maxByOrNull { scoreFor(it, natural) }
            if (candidate != null) {
                used += candidate.id
                slots += com.footymanager.simulator.domain.model.LineupSlot(
                    slotIndex = index,
                    playerId = candidate.id,
                    outOfPosition = candidate.position != natural
                )
            }
        }

        val subs = available
            .filter { it.id !in used }
            .sortedByDescending { it.overall }
            .take(9)
            .map { it.id }

        val captain = slots
            .mapNotNull { squad.firstOrNull { p -> p.id == it.playerId } }
            .maxByOrNull { it.overall + it.age * 0.35 + it.moraleScore * 1.5 }
            ?.id

        val penaltyTaker = slots
            .mapNotNull { squad.firstOrNull { p -> p.id == it.playerId } }
            .maxByOrNull { it.attributes.shooting * 1.0 + it.attributes.passing * 0.3 }
            ?.id

        val freeKickTaker = slots
            .mapNotNull { squad.firstOrNull { p -> p.id == it.playerId } }
            .maxByOrNull { it.attributes.passing * 1.0 + it.attributes.shooting * 0.5 }
            ?.id

        return TeamSelection(
            startingXi = slots,
            substitutes = subs,
            captainId = captain,
            penaltyTakerId = penaltyTaker,
            freeKickTakerId = freeKickTaker
        )
    }

    /** Higher is better. Rewards natural fit, current ability and availability. */
    fun scoreFor(player: Player, slot: Position): Double {
        val suitability = com.footymanager.simulator.domain.model.PositionSuitability.factor(player.position, slot)
        val fitnessFactor = 0.65 + (player.fitness / 100.0) * 0.35
        val formFactor = 0.90 + (player.form - 5.0) * 0.02
        val moraleFactor = 0.95 + (player.moraleScore - 3.0) * 0.02
        return player.overall * suitability * fitnessFactor * formFactor * moraleFactor
    }
}
