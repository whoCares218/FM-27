package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.data.SeasonCalendar
import com.footymanager.simulator.domain.data.SelectionHelper
import com.footymanager.simulator.domain.model.BoardObjective
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.ChampionsLeagueState
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.europeanCompetitions
import com.footymanager.simulator.domain.model.isEuropean
import com.footymanager.simulator.domain.model.FinanceLedgerEntry
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.LedgerCategory
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.MatchResult
import com.footymanager.simulator.domain.model.MatchStatus
import com.footymanager.simulator.domain.model.NewsCategory
import com.footymanager.simulator.domain.model.NewsItem
import com.footymanager.simulator.domain.model.ObjectiveStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayerSeasonStats
import com.footymanager.simulator.domain.model.TableRow
import com.footymanager.simulator.domain.model.TrainingFocus
import kotlin.random.Random

/**
 * Owns progression through a season: simulating matchdays, applying weekly
 * training and recovery, and rolling the world over into the next season.
 */
object SeasonEngine {

    /** One matchday is seven days long, so the calendar advances consistently. */
    const val DAYS_PER_MATCHDAY = 7

    // ------------------------------------------------------- match simulation

    /**
     * Builds the two sides' inputs for a fixture. Exposed so the live match engine
     * (Play Match / Quick Sim) can run the exact same setup the fast simulator uses.
     */
    fun buildTeamInputs(
        career: Career,
        match: Match,
        plannedSubstitutions: List<PlannedSubstitution> = emptyList()
    ): Pair<MatchTeamInput, MatchTeamInput> {
        val homeClub = career.clubOrThrow(match.homeClubId)
        val awayClub = career.clubOrThrow(match.awayClubId)

        val homeSelection = selectionFor(career, homeClub.id, match.matchday)
        val awaySelection = selectionFor(career, awayClub.id, match.matchday)
        val homeTactics = tacticsFor(career, homeClub.id, match.matchday)
        val awayTactics = tacticsFor(career, awayClub.id, match.matchday)

        val homeIsUser = homeClub.id == career.userClubId
        val awayIsUser = awayClub.id == career.userClubId

        val homeInput = MatchTeamInput(
            clubId = homeClub.id,
            clubName = homeClub.name,
            reputation = homeClub.reputation,
            tactics = homeTactics,
            selection = homeSelection,
            squadById = career.players.filter { it.clubId == homeClub.id }.associateBy { it.id },
            strengthMultiplier = aiMultiplier(career, homeClub.id),
            plannedSubstitutions = if (homeIsUser) plannedSubstitutions else emptyList()
        )
        val awayInput = MatchTeamInput(
            clubId = awayClub.id,
            clubName = awayClub.name,
            reputation = awayClub.reputation,
            tactics = awayTactics,
            selection = awaySelection,
            squadById = career.players.filter { it.clubId == awayClub.id }.associateBy { it.id },
            strengthMultiplier = aiMultiplier(career, awayClub.id),
            plannedSubstitutions = if (awayIsUser) plannedSubstitutions else emptyList()
        )
        return homeInput to awayInput
    }

    /** The determinism setting implied by the career's difficulty. */
    fun determinismFor(career: Career): Double = when (career.difficulty) {
        com.footymanager.simulator.domain.model.Difficulty.EASY -> 1.35
        com.footymanager.simulator.domain.model.Difficulty.NORMAL -> 1.0
        com.footymanager.simulator.domain.model.Difficulty.HARD -> 0.85
    }

    fun rulesFor(match: Match): MatchRules =
        if (match.isKnockout) MatchRules.KNOCKOUT else MatchRules.LEAGUE

    /** Folds an externally produced result into the career state. */
    fun applyResult(
        career: Career,
        match: Match,
        result: MatchResult,
        random: Random,
        userMatch: Boolean
    ): Career = applyResultToCareer(career, match, result, random, userMatch)

    /**
     * Simulates a single fixture using the full engine and folds the outcome into
     * the career (table, results, player statistics, cards, injuries, fitness).
     */
    fun simulateFixture(
        career: Career,
        match: Match,
        random: Random,
        userMatch: Boolean,
        plannedSubstitutions: List<PlannedSubstitution> = emptyList()
    ): Pair<Career, MatchResult> {
        val (homeInput, awayInput) = buildTeamInputs(career, match, plannedSubstitutions)

        val sim = MatchEngine.simulate(
            homeInput,
            awayInput,
            random,
            homeAdvantage = true,
            determinism = determinismFor(career),
            rules = rulesFor(match)
        )

        val result = MatchResult(
            matchId = match.id,
            leagueId = match.leagueId,
            matchday = match.matchday,
            homeClubId = homeInput.clubId,
            awayClubId = awayInput.clubId,
            homeGoals = sim.homeGoals,
            awayGoals = sim.awayGoals,
            homeStats = sim.homeStats,
            awayStats = sim.awayStats,
            events = sim.events,
            playerRatings = sim.ratings,
            playerOfTheMatchId = sim.playerOfTheMatchId,
            penaltyShootoutHome = sim.shootoutHome,
            penaltyShootoutAway = sim.shootoutAway,
            momentum = sim.momentum,
            extraTime = sim.shootoutHome != null || sim.homeStats.goals > sim.homeGoals ||
                sim.awayStats.goals > sim.awayGoals
        )

        val updated = applyResultToCareer(career, match, result, random, userMatch)
        return updated to result
    }

    /** Folds a simulated result into the career state. */
    private fun applyResultToCareer(
        career: Career,
        match: Match,
        result: MatchResult,
        random: Random,
        userMatch: Boolean
    ): Career {
        // ---- Fixture ----
        val updatedFixtures = career.fixtures.map { m ->
            if (m.id == match.id) {
                m.copy(
                    homeGoals = result.homeGoals - (result.homeStats.goals - result.homeGoals),
                    awayGoals = result.awayGoals - (result.awayStats.goals - result.awayGoals),
                    homeGoalsExtraTime = result.homeStats.goals - result.homeGoals,
                    awayGoalsExtraTime = result.awayStats.goals - result.awayGoals,
                    shootoutHome = result.penaltyShootoutHome,
                    shootoutAway = result.penaltyShootoutAway,
                    status = MatchStatus.PLAYED,
                    resultId = match.id
                )
            } else m
        }

        // ---- League table (domestic leagues only; the UCL table is separate) ----
        val table = career.table.toMutableMap()
        if (match.competition == CompetitionType.LEAGUE) {
            val rows = table[match.leagueId]?.toMutableList()
            if (rows != null) {
                val homeIdx = rows.indexOfFirst { it.clubId == match.homeClubId }
                val awayIdx = rows.indexOfFirst { it.clubId == match.awayClubId }
                if (homeIdx >= 0) rows[homeIdx] = rows[homeIdx].applyResult(result.homeGoals, result.awayGoals)
                if (awayIdx >= 0) rows[awayIdx] = rows[awayIdx].applyResult(result.awayGoals, result.homeGoals)
                table[match.leagueId] = rows
            }
        }

        // ---- Player statistics, condition, cards and injuries ----
        val ratingById = result.playerRatings.associateBy { it.playerId }
        val homePlayers = career.players.filter { it.clubId == match.homeClubId }
        val awayPlayers = career.players.filter { it.clubId == match.awayClubId }

        val fatigueStyle = if (match.homeClubId == career.userClubId) {
            career.tactics.style.fatigueBias
        } else 1.0

        val updatedPlayers = career.players.map { player ->
            when (player.clubId) {
                match.homeClubId -> applyPlayerOutcome(
                    player, ratingById[player.id], result.homeGoals, result.awayGoals,
                    styleFatigue(career, match.homeClubId, fatigueStyle), random
                )
                match.awayClubId -> applyPlayerOutcome(
                    player, ratingById[player.id], result.awayGoals, result.homeGoals,
                    styleFatigue(career, match.awayClubId, fatigueStyle), random
                )
                else -> player
            }
        }

        // ---- News for the user's matches ----
        var news = career.news
        var board = career.board
        var ledger = career.ledger
        var idCounter = career.idCounter
        var stadium = career.stadium
        var managerRecord = career.managerRecord
        var matchdayFinance = career.lastMatchdayFinance
        var matchdayNet = 0L

        val isUserFixture = match.involves(career.userClubId)
        if (isUserFixture) {
            val userIsHome = match.homeClubId == career.userClubId
            val opponentId = match.opponentOf(career.userClubId)
            val opponent = career.clubOrThrow(opponentId)
            val userGoals = if (userIsHome) result.homeGoals else result.awayGoals
            val oppGoals = if (userIsHome) result.awayGoals else result.homeGoals

            idCounter++
            val headline = when {
                userGoals > oppGoals -> "Match report: ${career.userClub.name} win ${userGoals}-${oppGoals}"
                userGoals == oppGoals -> "Match report: ${userGoals}-${oppGoals} draw with ${opponent.name}"
                else -> "Match report: defeat to ${opponent.name}"
            }
            val body = buildString {
                append("${career.userClub.name} ")
                append(if (userIsHome) "hosted" else "travelled to")
                append(" ${opponent.name} and ")
                append(
                    when {
                        userGoals > oppGoals -> "claimed all three points"
                        userGoals == oppGoals -> "shared the points"
                        else -> "were beaten"
                    }
                )
                append(". Final score ${userGoals}-${oppGoals}. ")
                append("Possession ${if (userIsHome) result.homeStats.possession else result.awayStats.possession}% ")
                append("with ${if (userIsHome) result.homeStats.shots else result.awayStats.shots} shots.")
            }
            news = news + NewsItem(
                id = idCounter,
                category = NewsCategory.MATCH,
                headline = headline,
                body = body,
                date = career.date,
                season = career.season,
                clubId = career.userClubId
            )

            // Matchday revenue (home fixtures only) and stadium attendance.
            if (userIsHome) {
                val ppg = AiManager.recentPointsPerGame(
                    career.fixtures.filter { it.isPlayed && it.involves(career.userClubId) },
                    career.userClubId
                )
                val isRival = career.userClub.rivalClubId == opponent.id
                val competitionFactor = when (match.competition) {
                    CompetitionType.CHAMPIONS_LEAGUE -> 1.10
                    CompetitionType.DOMESTIC_CUP -> 1.05
                    else -> 1.0
                }
                val attendance = StadiumEngine.attendance(
                    stadium = career.stadium,
                    reputation = career.userClub.reputation,
                    opponentReputation = opponent.reputation,
                    recentPointsPerGame = ppg,
                    random = random,
                    fanSatisfaction = career.fanSatisfaction,
                    isRival = isRival,
                    competitionFactor = competitionFactor
                )
                val finance = StadiumEngine.matchdayFinance(
                    stadium = career.stadium,
                    matchId = match.id,
                    opponentName = opponent.name,
                    competitionLabel = match.competitionLabel,
                    attendance = attendance,
                    reputation = career.userClub.reputation
                )
                val playedHome = career.fixtures.count {
                    it.isPlayed && it.homeClubId == career.userClubId
                }
                stadium = career.stadium.copy(
                    lastAttendance = attendance,
                    averageAttendance = StadiumEngine.updatedAverage(
                        career.stadium.averageAttendance, attendance, playedHome
                    ),
                    totalMatchdayIncome = career.stadium.totalMatchdayIncome + finance.totalRevenue
                )
                idCounter++
                ledger = ledger + FinanceLedgerEntry(
                    id = idCounter,
                    date = career.date,
                    season = career.season,
                    description = "Matchday revenue vs ${opponent.name} (${"%,d".format(attendance)} fans)",
                    amount = finance.totalRevenue,
                    category = LedgerCategory.MATCHDAY
                )
                idCounter++
                ledger = ledger + FinanceLedgerEntry(
                    id = idCounter,
                    date = career.date,
                    season = career.season,
                    description = "Matchday costs vs ${opponent.name}",
                    amount = -finance.totalExpenses,
                    category = LedgerCategory.MATCHDAY_EXPENSES
                )
                matchdayFinance = finance
                matchdayNet = finance.netProfit
            }

            // Board reacts to every user result.
            val confidenceDelta = boardConfidenceDelta(userGoals, oppGoals, career, opponent)
            board = evaluateBoard(career, board, confidenceDelta)

            // The manager's own record spans every club and season.
            val record = career.managerRecord
            managerRecord = record.copy(
                matchesManaged = record.matchesManaged + 1,
                wins = record.wins + if (userGoals > oppGoals) 1 else 0,
                draws = record.draws + if (userGoals == oppGoals) 1 else 0,
                losses = record.losses + if (userGoals < oppGoals) 1 else 0
            )
        }

        // ---- Injury news for the user's squad ----
        val newlyInjured = updatedPlayers.filter { p ->
            p.clubId == career.userClubId &&
                p.isInjured &&
                career.players.firstOrNull { it.id == p.id }?.isInjured == false
        }
        for (injured in newlyInjured) {
            idCounter++
            news = news + NewsItem(
                id = idCounter,
                category = NewsCategory.INJURY,
                headline = "Injury: ${injured.name} out for ${injured.injury.weeksRemaining} week(s)",
                body = "${injured.name} picked up a ${injured.injury.type.label.lowercase()} " +
                    "and is expected to be sidelined for ${injured.injury.weeksRemaining} week(s).",
                date = career.date,
                season = career.season,
                clubId = career.userClubId
            )
        }

        // ---- Suspension news ----
        val newlySuspended = updatedPlayers.filter { p ->
            p.clubId == career.userClubId && p.isSuspended &&
                (career.players.firstOrNull { it.id == p.id }?.suspensionWeeks ?: 0) == 0
        }
        for (player in newlySuspended) {
            idCounter++
            news = news + NewsItem(
                id = idCounter,
                category = NewsCategory.MATCH,
                headline = "${player.name} suspended",
                body = "${player.name} will serve a ${player.suspensionWeeks}-match suspension " +
                    "after accumulating cards.",
                date = career.date,
                season = career.season,
                clubId = career.userClubId
            )
        }

        val withLedger = career.copy(
            fixtures = updatedFixtures,
            table = table,
            players = updatedPlayers,
            results = career.results + result,
            news = news.takeLast(120),
            board = board,
            ledger = ledger.takeLast(600),
            idCounter = idCounter,
            stadium = stadium,
            managerRecord = managerRecord,
            lastMatchdayFinance = matchdayFinance,
            clubs = if (matchdayNet != 0L) {
                career.clubs.map {
                    if (it.id == career.userClubId) it.copy(balance = it.balance + matchdayNet) else it
                }
            } else career.clubs
        )

        // ---- Continental football: update the correct competition's table ----
        val withEurope = if (match.competition.isEuropean && match.tieId == null) {
            val state = withLedger.europeanState(match.competition)
            withLedger.withEuropeanState(
                match.competition,
                ChampionsLeagueEngine.applyLeagueResult(state, match)
            )
        } else withLedger

        // Wages are paid once per week by advanceWeek, not once per fixture.
        return withEurope
    }

    private fun styleFatigue(career: Career, clubId: Long, fallback: Double): Double =
        if (clubId == career.userClubId) career.tactics.fatigueMultiplier else fallback

    private fun applyPlayerOutcome(
        player: Player,
        rating: com.footymanager.simulator.domain.model.PlayerMatchRating?,
        teamGoals: Int,
        opponentGoals: Int,
        styleFatigue: Double,
        random: Random
    ): Player {
        val selectionAppearance = rating != null
        var updated = player

        if (selectionAppearance) {
            val isStart = rating!!.minutesPlayed >= 60
            // ---- Season statistics ----
            var stats = updated.seasonStats.addAppearance(
                rating = rating.rating,
                isStart = isStart,
                minutes = rating.minutesPlayed
            )
            stats = stats.copy(
                goals = stats.goals + rating.goals,
                assists = stats.assists + rating.assists,
                yellowCards = stats.yellowCards + rating.yellowCards,
                redCards = stats.redCards + rating.redCards,
                manOfTheMatch = stats.manOfTheMatch + if (rating.isManOfTheMatch) 1 else 0
            )
            if (updated.position == com.footymanager.simulator.domain.model.Position.GK) {
                stats = stats.copy(
                    cleanSheets = stats.cleanSheets + if (opponentGoals == 0) 1 else 0,
                    goalsConceded = stats.goalsConceded + opponentGoals
                )
            }
            updated = updated.copy(seasonStats = stats)

            // ---- Condition, form and morale ----
            updated = DevelopmentEngine.applyMatchEffects(
                player = updated,
                minutes = rating.minutesPlayed,
                rating = rating.rating,
                teamGoals = teamGoals,
                opponentGoals = opponentGoals,
                styleFatigue = styleFatigue,
                isStarter = isStart
            )

            // ---- Cards and suspensions ----
            updated = DevelopmentEngine.applyCards(updated, rating.yellowCards, rating.redCards)

            // ---- Injuries ----
            val injuryChance = injuryChanceFor(updated)
            if (random.nextDouble() < injuryChance) {
                updated = DevelopmentEngine.inflictInjury(updated, random)
            }
        } else {
            // Players who did not feature: rest, minor sharpness loss, morale drift.
            updated = DevelopmentEngine.applyBenchEffects(updated, random)
        }

        return updated
    }

    /** Fit, heavily-used players are likelier to break down. */
    private fun injuryChanceFor(player: Player): Double {
        var chance = 0.018
        if (player.fitness < 60) chance += 0.045
        if (player.fitness < 40) chance += 0.055
        if (player.age >= 32) chance += 0.012
        if (player.attributes.physical < 60) chance += 0.008
        return chance
    }

    // ------------------------------------------------------------ selections

    /** The XI for a club in a given matchday, using the user's picks where relevant. */
    fun selectionFor(career: Career, clubId: Long, matchday: Int): com.footymanager.simulator.domain.model.TeamSelection {
        val squad = career.players.filter { it.clubId == clubId }
        return if (clubId == career.userClubId) {
            // Repair the user's selection if injuries or suspensions broke it.
            SelectionRepair.repair(career.selection, squad, career.tactics.formation)
        } else {
            AiManager.selectionFor(squad, tacticsFor(career, clubId, matchday))
        }
    }

    fun tacticsFor(career: Career, clubId: Long, matchday: Int): com.footymanager.simulator.domain.model.Tactics {
        if (clubId == career.userClubId) return career.tactics
        val club = career.clubOrThrow(clubId)
        val results = career.fixtures
            .filter { it.isPlayed && it.involves(clubId) }
            .sortedBy { it.matchday }
        val ppg = AiManager.recentPointsPerGame(results, clubId)
        return AiManager.tacticsFor(club, matchday, ppg)
    }

    private fun aiMultiplier(career: Career, clubId: Long): Double =
        if (clubId == career.userClubId) 1.0 else career.difficulty.aiStrength

    // -------------------------------------------------------- matchday flow

    /**
     * Simulates every fixture on the current matchday for AI clubs.
     * The user's own fixture is handled separately so it can be presented.
     */
    fun simulateOtherFixtures(career: Career, random: Random): Career {
        var current = career
        val matchday = career.matchdayIndex + 1

        // Every domestic league plays on the same round, so results across all
        // divisions must be simulated together to keep the world consistent.
        val pending = career.fixtures.filter {
            it.competition == CompetitionType.LEAGUE &&
                it.matchday == matchday &&
                !it.isPlayed &&
                !it.involves(career.userClubId)
        }
        for (match in pending) {
            val (updated, _) = simulateFixture(current, match, random, userMatch = false)
            current = updated
        }

        // A continental matchday shares this domestic round, so every European
        // fixture must be played at the same time to keep each competition's table
        // and bracket consistent.
        for (competition in europeanCompetitions) {
            val round = europeanMatchdayForRound(competition, matchday) ?: continue
            val pendingEuropean = current.fixtures.filter {
                it.competition == competition &&
                    it.competitionRound == round &&
                    it.tieId == null &&
                    !it.isPlayed
            }
            for (match in pendingEuropean) {
                val (updated, _) = simulateFixture(current, match, random, userMatch = false)
                current = updated
            }
        }

        // Knockout legs are scheduled on their own matchdays and involve AI clubs
        // that may still be alive in the competition.
        val knockoutPending = current.fixtures.filter {
            it.competition.isEuropean &&
                it.tieId != null &&
                it.matchday == matchday &&
                !it.isPlayed &&
                !it.involves(career.userClubId)
        }
        for (match in knockoutPending) {
            val (updated, _) = simulateFixture(current, match, random, userMatch = false)
            current = updated
        }
        return current
    }

    /** Maps a domestic round onto the UCL league-phase matchday it hosts, if any. */
    fun uclMatchdayForRound(domesticRound: Int): Int? =
        europeanMatchdayForRound(CompetitionType.CHAMPIONS_LEAGUE, domesticRound)

    /** Maps a domestic round onto a competition's league-phase matchday, if any. */
    fun europeanMatchdayForRound(competition: CompetitionType, domesticRound: Int): Int? {
        val idx = SeasonCalendar.europeanRoundSchedule(competition).indexOf(domesticRound)
        return if (idx >= 0) idx + 1 else null
    }

    /**
     * Applies the between-match week: training, recovery, development, injury
     * recovery, suspensions, and contract countdown. Then advances the calendar.
     */
    fun advanceWeek(career: Career, random: Random): Career {
        var idCounter = career.idCounter
        val focus = career.trainingFocus

        // Minutes played this matchday, so development rewards game time.
        val minutesByPlayer = career.results
            .filter { it.matchday == career.matchdayIndex + 1 }
            .flatMap { it.playerRatings }
            .groupBy { it.playerId }
            .mapValues { entry -> entry.value.sumOf { it.minutesPlayed } }

        val updatedPlayers = career.players.map { player ->
            var p = player

            // Injury and suspension timers tick down every week.
            p = DevelopmentEngine.progressInjury(p)
            p = DevelopmentEngine.progressSuspension(p)

            val minutes = minutesByPlayer[p.id] ?: 0
            val playedThisWeek = minutes > 0

            // Fitness recovery only for players who did not complete a full match.
            if (!playedThisWeek || minutes < 60) {
                p = DevelopmentEngine.weeklyRecovery(p, focus, random)
            }

            // Contract loyalty and playing-time morale.
            p = p.copy(
                moraleScore = contractMoraleAdjust(p).coerceIn(1.0, 5.0)
            )

            // Long-term development.
            p = DevelopmentEngine.develop(
                player = p,
                focus = focus,
                minutesThisWeek = minutes,
                developmentRate = career.difficulty.developmentRate,
                random = random
            )

            p
        }

        // Players signed in a previous window become sellable again.
        val normalised = updatedPlayers.map { if (it.signedThisWindow) it.copy(signedThisWindow = false) else it }

        var news = career.news
        // Notify the manager about players returning from injury.
        val recovered = normalised.filter { p ->
            p.clubId == career.userClubId && !p.isInjured &&
                career.players.firstOrNull { it.id == p.id }?.isInjured == true
        }
        for (player in recovered) {
            idCounter++
            news = news + NewsItem(
                id = idCounter,
                category = NewsCategory.INJURY,
                headline = "${player.name} returns to training",
                body = "${player.name} has recovered from injury and is available for selection.",
                date = career.date,
                season = career.season,
                clubId = career.userClubId
            )
        }

        val newDate = career.date.plusDays(DAYS_PER_MATCHDAY)
        val newIndex = career.matchdayIndex + 1

        var updated = career.copy(
            players = normalised,
            date = newDate,
            matchdayIndex = newIndex,
            news = news.takeLast(120),
            idCounter = idCounter
        )

        // ---- Continental matchday ticks forward on its designated rounds ----
        val playedRound = career.matchdayIndex + 1
        for (competition in europeanCompetitions) {
            val played = europeanMatchdayForRound(competition, playedRound) ?: continue
            val state = updated.europeanState(competition)
            if (!state.active) continue
            updated = updated.withEuropeanState(
                competition,
                state.copy(
                    currentMatchday = (played + 1).coerceAtMost(state.leaguePhaseMatchdays + 1)
                )
            )
        }

        // ---- Stadium expansion, sponsorship income and fan mood ----
        updated = weeklyClubOperations(updated, random)

        // ---- Wages are paid once per week ----
        updated = FinanceEngine.payWeeklyWages(updated, updated.idCounter)

        // ---- Commercial and broadcasting income is banked every week ----
        updated = FinanceEngine.payWeeklyCommercialIncome(updated, updated.idCounter)

        // ---- Non-wage operating costs are charged every week ----
        updated = FinanceEngine.payWeeklyOperatingCosts(updated, updated.idCounter)

        // AI clubs trade with each other during open windows.
        updated = TransferEngine.runAiTransferActivity(updated, random)

        return updated
    }

    /**
     * The weekly club-housekeeping pass: an in-progress stadium expansion
     * advances, the sponsorship pays its instalment, and fan satisfaction drifts
     * in response to results and ticket pricing.
     */
    private fun weeklyClubOperations(career: Career, random: Random): Career {
        var idCounter = career.idCounter
        var ledger = career.ledger
        var news = career.news

        // ---- Stadium expansion ----
        var stadium = career.stadium
        if (stadium.expansionWeeksRemaining > 0) {
            val before = stadium
            stadium = StadiumEngine.progressExpansion(stadium)
            if (before.expansionWeeksRemaining > 0 && stadium.expansionWeeksRemaining == 0) {
                idCounter++
                news = news + NewsItem(
                    id = idCounter,
                    category = NewsCategory.GENERAL,
                    headline = "Stadium expansion complete",
                    body = "${stadium.name} has been expanded to a capacity of " +
                        "${"%,d".format(stadium.capacity)}.",
                    date = career.date,
                    season = career.season,
                    clubId = career.userClubId
                )
            }
        }

        // ---- Sponsorship weekly instalment ----
        val sponsorship = career.sponsorship
        if (sponsorship != null && sponsorship.weeklyInstalment > 0) {
            idCounter++
            ledger = ledger + FinanceLedgerEntry(
                id = idCounter,
                date = career.date,
                season = career.season,
                description = "${sponsorship.name} sponsorship instalment",
                amount = sponsorship.weeklyInstalment,
                category = LedgerCategory.SPONSORSHIP
            )
        }

        // ---- Fan satisfaction ----
        val ppg = AiManager.recentPointsPerGame(
            career.fixtures.filter { it.isPlayed && it.involves(career.userClubId) },
            career.userClubId
        )
        val satisfaction = StadiumEngine.fanSatisfaction(
            current = career.fanSatisfaction,
            stadium = stadium,
            reputation = career.userClub.reputation,
            recentPointsPerGame = ppg,
            random = random
        )

        // ---- Amortisation: transfer fees are written down week by week ----
        val agedBook = career.amortisationBook.mapNotNull {
            val remaining = it.weeksRemaining - 1
            if (remaining <= 0) null else it.copy(weeksRemaining = remaining)
        }

        val updatedClubs = career.clubs.map {
            if (it.id == career.userClubId) it.copy(balance = it.balance + (sponsorship?.weeklyInstalment ?: 0L))
            else it
        }

        return career.copy(
            stadium = stadium,
            clubs = updatedClubs,
            ledger = ledger.takeLast(600),
            news = news.takeLast(120),
            idCounter = idCounter,
            fanSatisfaction = satisfaction,
            amortisationBook = agedBook,
            board = career.board.copy(confidence = satisfaction.coerceIn(career.board.confidence - 3, career.board.confidence + 3))
        )
    }

    private fun contractMoraleAdjust(player: Player): Double {
        var morale = player.moraleScore
        // Players entering the final year of a deal grow restless.
        if (player.contractYearsRemaining <= 1 && player.age >= 27) morale -= 0.02
        if (player.contractYearsRemaining == 0) morale -= 0.05
        // Long-serving players are happier.
        if (player.seasonsAtClub >= 3) morale += 0.01
        return morale
    }

    // ------------------------------------------------------------- the board

    private fun boardConfidenceDelta(
        userGoals: Int,
        oppGoals: Int,
        career: Career,
        opponent: com.footymanager.simulator.domain.model.Club
    ): Int {
        val resultDelta = when {
            userGoals - oppGoals >= 3 -> 7
            userGoals > oppGoals -> 5
            userGoals == oppGoals -> 0
            oppGoals - userGoals == 1 -> -4
            else -> -7
        }
        // Beating a stronger side counts for more; losing to a weaker side hurts more.
        val qualityGap = (opponent.reputation - career.userClub.reputation) / 10.0
        val adjusted = (resultDelta - qualityGap * 0.9).toInt()
        return adjusted
    }

    private fun evaluateBoard(career: Career, board: com.footymanager.simulator.domain.model.BoardState, delta: Int): com.footymanager.simulator.domain.model.BoardState {
        val leniency = career.difficulty.boardLeniency
        val applied = if (delta > 0) (delta * leniency).toInt() else delta
        val newConfidence = (board.confidence + applied).coerceIn(0, 100)

        val position = career.userLeaguePosition
        val objective = career.userClub.boardExpectation
        val onTrack = position <= career.userClub.targetLeaguePosition + 3

        val objectives = board.objectives.map { obj ->
            if (obj.title == "League") {
                obj.copy(
                    description = objective,
                    status = when {
                        career.matchdayIndex < 4 -> ObjectiveStatus.ON_TRACK
                        position <= career.userClub.targetLeaguePosition -> ObjectiveStatus.ON_TRACK
                        position <= career.userClub.targetLeaguePosition + 4 -> ObjectiveStatus.AT_RISK
                        else -> ObjectiveStatus.FAILED
                    }
                )
            } else obj
        }

        val evaluation = when {
            newConfidence >= 75 -> "The board is delighted with recent results."
            newConfidence >= 55 -> "The board is satisfied with the direction of the club."
            newConfidence >= 35 -> "The board expects an improvement in results."
            newConfidence >= 20 -> "The board has concerns about the team's form."
            else -> "The board has issued a warning about the club's league position."
        }

        return board.copy(
            confidence = newConfidence,
            objectives = objectives,
            lastEvaluation = evaluation,
            managerSacked = newConfidence <= 0
        )
    }

    // -------------------------------------------------------- season turnover

    /**
     * Closes the season: builds the summary, applies prize money, promotes and
     * relegates clubs, ages players and generates a fresh fixture list.
     */
    fun endSeason(career: Career, random: Random): Career {
        // The season's summary is built from the final table before the books roll
        // over. Prize money is then applied and the closing position recorded, so
        // the financial history includes the prize money the club actually earned.
        val summary = SeasonSummaryBuilder.build(career)
        var idCounter = career.idCounter

        // ---- Prize money ----
        var ledger = career.ledger
        val league = League.byId(career.userLeagueId)
        val position = summary.finalPosition
        val prizeMoney = (league.prizeMoneyPerPlace * (career.clubs.count { it.leagueId == league.id } - position + 1))
        if (prizeMoney > 0) {
            idCounter++
            ledger = ledger + FinanceLedgerEntry(
                id = idCounter,
                date = career.date,
                season = career.season,
                description = "Prize money for finishing ${position}th in the ${league.name}",
                amount = prizeMoney,
                category = LedgerCategory.PRIZE_MONEY
            )
        }

        val clubsWithPrize = career.clubs.map { club ->
            if (club.leagueId == league.id) {
                val row = career.sortedTable(league.id).indexOfFirst { it.clubId == club.id } + 1
                val money = league.prizeMoneyPerPlace * (career.clubs.count { it.leagueId == league.id } - row + 1)
                club.copy(balance = club.balance + money)
            } else club
        }

        // Bank the season's final financial position, prize money included.
        val withHistory = FinanceEngine.recordSeason(
            career.copy(clubs = clubsWithPrize, ledger = ledger.takeLast(600), idCounter = idCounter)
        )
        ledger = withHistory.ledger
        idCounter = withHistory.idCounter

        // ---- Age players, expire contracts, reset season stats ----
        val agedPlayers = career.players.map { DevelopmentEngine.applySeasonTurnover(it) }

        // Expiring contracts: clubs keep the players they still want and let the
        // rest leave as free agents. Without this the whole world's squads would
        // drain to nothing within a handful of seasons.
        val renewedPlayers = agedPlayers.map { p ->
            if (p.contractYearsRemaining > 0 || p.clubId == null) p
            else {
                val squad = agedPlayers.filter { it.clubId == p.clubId }.sortedByDescending { it.overall }
                val rank = squad.indexOfFirst { it.id == p.id }
                val wanted = rank < 22 && p.age <= 34
                val emerging = p.potential > p.overall && p.age <= 28
                if (wanted || emerging) {
                    p.copy(contractYearsRemaining = 2 + random.nextInt(3))
                } else {
                    p.copy(clubId = null)
                }
            }
        }
        val retainedPlayers = renewedPlayers
        val released = renewedPlayers.filter { p ->
            p.clubId == null && agedPlayers.firstOrNull { it.id == p.id }?.clubId != null
        }

        var news = career.news
        for (player in released.filter { it.clubId == career.userClubId }) {
            idCounter++
            news = news + NewsItem(
                id = idCounter,
                category = NewsCategory.TRANSFER,
                headline = "${player.name} leaves on a free transfer",
                body = "${player.name}'s contract has expired and he has left ${career.userClub.name}.",
                date = career.date,
                season = career.season,
                clubId = career.userClubId
            )
        }

        // ---- Promotion and relegation ----
        val (promotedRelegatedClubs, updatedTables) = applyPromotionRelegation(
            clubsWithPrize, career, random
        )

        // ---- New season ----
        val nextSeasonNumber = career.seasonNumber + 1
        val nextSeasonLabel = SeasonLabel.forNumber(nextSeasonNumber)
        val newStartDate = SeasonCalendar.SEASON_START.plusDays(
            (nextSeasonNumber - 1) * 364
        )

        // Retain last season's finishing positions for European qualification.
        val previousStandings = career.table.mapValues { (_, rows) ->
            rows.sortedWith(TableRow.comparator).map { it.clubId }
        }

        val fixtures = mutableListOf<Match>()
        val table = mutableMapOf<String, List<TableRow>>()
        for (lg in League.domestic) {
            val leagueClubs = promotedRelegatedClubs.filter { it.leagueId == lg.id }
            if (leagueClubs.isEmpty()) continue
            fixtures += com.footymanager.simulator.domain.data.FixtureGenerator.generateLeagueFixtures(
                lg, leagueClubs, random
            ) { ++idCounter }
            table[lg.id] = leagueClubs.map { TableRow(clubId = it.id) }
        }

        // Refresh budgets from the board based on the new division.
        val refreshedClubs = promotedRelegatedClubs.map { club ->
            FinanceEngine.refreshSeasonBudgets(club, nextSeasonNumber)
        }

        val userClub = refreshedClubs.first { it.id == career.userClubId }
        val userSquad = retainedPlayers.filter { it.clubId == userClub.id }
        val newSelection = SelectionHelper.autoPickBest(userSquad, Formation.byId(career.tactics.formationId))

        // ---- Sponsorship: pay any bonus, then offer a fresh set of deals ----
        val previousSponsorship = career.sponsorship
        if (previousSponsorship != null) {
            val bonus = if (SponsorshipEngine.bonusEarned(previousSponsorship.bonusCondition, position)) {
                previousSponsorship.bonusAmount
            } else 0L
            if (bonus > 0) {
                idCounter++
                ledger = ledger + FinanceLedgerEntry(
                    id = idCounter,
                    date = career.date,
                    season = career.season,
                    description = "${previousSponsorship.name} bonus (${previousSponsorship.bonusCondition})",
                    amount = bonus,
                    category = LedgerCategory.SPONSORSHIP
                )
                idCounter++
                news = news + NewsItem(
                    id = idCounter,
                    category = NewsCategory.BOARD,
                    headline = "Sponsorship bonus earned",
                    body = "${previousSponsorship.name} have paid a " +
                        "£${"%,d".format(bonus)} bonus after ${previousSponsorship.bonusCondition.lowercase()}.",
                    date = career.date,
                    season = career.season,
                    clubId = userClub.id
                )
            }
        }
        val newSponsorOffers = SponsorshipEngine.generateOffers(userClub, random)

        // ---- Continental football for the new season ----
        val seedCareer = career.copy(
            season = nextSeasonLabel,
            seasonNumber = nextSeasonNumber,
            clubs = refreshedClubs,
            lastStandings = previousStandings,
            championsLeague = ChampionsLeagueState(
                season = nextSeasonLabel,
                competition = CompetitionType.CHAMPIONS_LEAGUE,
                active = false
            ),
            europaLeague = ChampionsLeagueState(
                season = nextSeasonLabel,
                competition = CompetitionType.EUROPA_LEAGUE,
                active = false
            ),
            conferenceLeague = ChampionsLeagueState(
                season = nextSeasonLabel,
                competition = CompetitionType.CONFERENCE_LEAGUE,
                active = false
            )
        )
        var uclState = ChampionsLeagueState(season = nextSeasonLabel, active = false)
        var uelState = ChampionsLeagueState(
            season = nextSeasonLabel, competition = CompetitionType.EUROPA_LEAGUE, active = false
        )
        var ueclState = ChampionsLeagueState(
            season = nextSeasonLabel, competition = CompetitionType.CONFERENCE_LEAGUE, active = false
        )
        for (competition in europeanCompetitions) {
            val (state, matches) = ChampionsLeagueEngine.createSeason(
                career = seedCareer,
                season = nextSeasonLabel,
                startDate = newStartDate,
                random = random,
                idProvider = { ++idCounter },
                competition = competition
            )
            fixtures += matches
            when (competition) {
                CompetitionType.EUROPA_LEAGUE -> uelState = state
                CompetitionType.CONFERENCE_LEAGUE -> ueclState = state
                else -> uclState = state
            }
        }

        val datedFixtures = SeasonCalendar.assignDates(fixtures, random)

        // Rebuild the board objectives for the new campaign.
        val newBoard = career.board.copy(
            confidence = (career.board.confidence + 12).coerceAtMost(85),
            objectives = listOf(
                BoardObjective("League", userClub.boardExpectation, ObjectiveStatus.ON_TRACK),
                BoardObjective(
                    "Finances",
                    "Keep the wage bill within the £${"%,d".format(userClub.wageBudget)} weekly budget",
                    ObjectiveStatus.ON_TRACK
                ),
                BoardObjective(
                    "Squad",
                    "Develop at least one young player into a first-team regular",
                    ObjectiveStatus.ON_TRACK
                )
            ),
            lastEvaluation = "Pre-season ${nextSeasonLabel}: the board expects ${userClub.boardExpectation.lowercase()}."
        )

        idCounter++
        news = news + NewsItem(
            id = idCounter,
            category = NewsCategory.GENERAL,
            headline = "Season ${nextSeasonLabel} begins",
            body = "The ${nextSeasonLabel} campaign is underway. " +
                "Board objective: ${userClub.boardExpectation}.",
            date = newStartDate,
            season = nextSeasonLabel,
            clubId = userClub.id
        )

        return career.copy(
            season = nextSeasonLabel,
            seasonNumber = nextSeasonNumber,
            date = newStartDate,
            phase = com.footymanager.simulator.domain.model.GamePhase.PRE_SEASON,
            matchdayIndex = 0,
            clubs = refreshedClubs,
            players = retainedPlayers,
            fixtures = datedFixtures,
            table = table,
            results = emptyList(),
            news = news.takeLast(120),
            ledger = ledger.takeLast(400),
            board = newBoard,
            selection = newSelection,
            pendingOffers = emptyList(),
            lastSeasonSummary = summary,
            awards = emptyList(),
            transferSpendThisSeason = 0L,
            transferIncomeThisSeason = 0L,
            transferListings = emptyList(),
            pendingSale = null,
            lastMatchdayFinance = null,
            idCounter = idCounter,
            championsLeague = uclState,
            europaLeague = uelState,
            conferenceLeague = ueclState,
            lastStandings = previousStandings,
            sponsorOffers = newSponsorOffers,
            sponsorship = null,
            sponsorshipSeason = 0,
            stadium = StadiumEngine.progressExpansion(career.stadium),
            financialHistory = withHistory.financialHistory,
            amortisationBook = career.amortisationBook,
            managerRecord = career.managerRecord.copy(
                trophies = career.managerRecord.trophies + summary.trophies
            )
        )
    }

    private fun applyPromotionRelegation(
        clubs: List<com.footymanager.simulator.domain.model.Club>,
        career: Career,
        random: Random
    ): Pair<List<com.footymanager.simulator.domain.model.Club>, Map<String, List<TableRow>>> {
        val updated = clubs.associateBy { it.id }.toMutableMap()
        val tables = mutableMapOf<String, List<TableRow>>()

        // English pyramid: Premier League <-> Championship.
        val premier = career.sortedTable(League.PREMIER_LEAGUE.id)
        val championship = career.sortedTable(League.CHAMPIONSHIP.id)

        if (premier.isNotEmpty() && championship.isNotEmpty()) {
            val relegated = premier.takeLast(League.PREMIER_LEAGUE.relegationPlaces).map { it.clubId }
            val promoted = championship.take(League.CHAMPIONSHIP.promotionPlaces).map { it.clubId }
            for (id in relegated) {
                updated[id]?.let { updated[id] = it.copy(leagueId = League.CHAMPIONSHIP.id) }
            }
            for (id in promoted) {
                updated[id]?.let { updated[id] = it.copy(leagueId = League.PREMIER_LEAGUE.id) }
            }
        }

        // Continental leagues relegate to a virtual lower division and promote
        // the strongest of the promoted candidates from within the same table.
        for (league in listOf(League.LA_LIGA, League.SERIE_A, League.BUNDESLIGA, League.LIGUE_1)) {
            val table = career.sortedTable(league.id)
            if (table.isEmpty()) continue
            // Relegated clubs are refreshed (new signings, new budget) rather than
            // replaced, which keeps the league size stable without a second division.
            val relegatedIds = table.takeLast(league.relegationPlaces).map { it.clubId }
            for (id in relegatedIds) {
                updated[id]?.let { club ->
                    updated[id] = club.copy(
                        reputation = (club.reputation - 2).coerceAtLeast(45),
                        transferBudget = (club.transferBudget * 80L / 100L).coerceAtLeast(500_000L)
                    )
                }
            }
        }

        return updated.values.toList() to tables
    }
}

/** Maps a season number onto its display label, e.g. 1 -> "2026/27". */
object SeasonLabel {
    fun forNumber(n: Int): String {
        val startYear = 2025 + n
        val endYear = startYear + 1
        return "$startYear/${endYear.toString().takeLast(2)}"
    }
}
