package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.ClubHistory
import com.footymanager.simulator.domain.model.ClubSeasonRecord
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.FinanceModel
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.PlayerCareerRecord
import com.footymanager.simulator.domain.model.PlayerSeasonRecord
import com.footymanager.simulator.domain.model.SeasonRecordHolder
import com.footymanager.simulator.domain.model.TransferRecordEntry
import com.footymanager.simulator.domain.model.TrophyRecord
import com.footymanager.simulator.domain.model.europeanCompetitions
import kotlin.math.max

/**
 * Builds and maintains the user's club history across seasons.
 *
 * Everything is written from the *current* career state at season turnover: the
 * final table, each player's season statistics, the season's wages and injuries,
 * the competition outcomes and the money that moved. The history is a record of
 * what actually happened, so it is appended to and never recomputed, which keeps
 * past seasons stable no matter how the live game changes.
 */
object ClubHistoryEngine {

    /** Weeks in a season, used to turn a weekly wage into a season's wage bill. */
    private const val WEEKS_PER_SEASON = 52

    /**
     * Records the season that is about to end into [career]'s club history and
     * returns the career with the updated history. Must be called *before* the
     * season rolls over, while the final tables and season stats are still live.
     */
    fun recordSeason(career: Career): Career {
        val clubId = career.userClubId
        val league = League.byId(career.userLeagueId)
        val sorted = career.sortedTable(career.userLeagueId)
        val position = sorted.indexOfFirst { it.clubId == clubId } + 1
        val row = sorted.firstOrNull { it.clubId == clubId }
        val squad = career.squadOf(clubId)

        val topScorer = squad.maxByOrNull { it.seasonStats.goals }
        val topAssister = squad.maxByOrNull { it.seasonStats.assists }
        val bestRating = squad.filter { it.seasonStats.ratedMatches >= 5 }
            .maxByOrNull { it.seasonStats.averageRating }

        val finance = career.financialHistory.firstOrNull { it.seasonNumber == career.seasonNumber }
        val trophies = seasonTrophies(career, position)

        val record = ClubSeasonRecord(
            season = career.season,
            seasonNumber = career.seasonNumber,
            leagueName = league.name,
            leagueId = league.id,
            finalPosition = position,
            played = row?.played ?: 0,
            won = row?.won ?: 0,
            drawn = row?.drawn ?: 0,
            lost = row?.lost ?: 0,
            goalsFor = row?.goalsFor ?: 0,
            goalsAgainst = row?.goalsAgainst ?: 0,
            points = row?.points ?: 0,
            topScorerName = topScorer?.name ?: "-",
            topScorerGoals = topScorer?.seasonStats?.goals ?: 0,
            topAssisterName = topAssister?.name ?: "-",
            topAssisterAssists = topAssister?.seasonStats?.assists ?: 0,
            bestRatingName = bestRating?.name ?: "-",
            bestRating = bestRating?.seasonStats?.averageRating ?: 0.0,
            trophies = trophies,
            europeanSummary = europeanSummary(career),
            transferSpend = career.transferSpendThisSeason,
            transferIncome = career.transferIncomeThisSeason,
            wageSpend = finance?.wageSpend ?: 0L,
            revenue = finance?.totalIncome ?: 0L,
            profit = finance?.netProfit ?: 0L,
            averageAttendance = career.stadium.averageAttendance,
            championName = sorted.firstOrNull()?.let { career.club(it.clubId)?.name } ?: ""
        )

        val history = career.clubHistory.copy(
            seasons = (career.clubHistory.seasons.filter { it.seasonNumber != career.seasonNumber } + record)
                .sortedBy { it.seasonNumber }
        )
        return career.copy(clubHistory = history)
    }

    /**
     * Folds every player's season into their career record and rebuilds the
     * leaderboards. Called at turnover, after the season record is written, so
     * the player totals and the season history stay in step.
     */
    fun finalisePlayers(career: Career): Career {
        val clubId = career.userClubId
        val seasonRecord = career.clubHistory.seasons.lastOrNull { it.seasonNumber == career.seasonNumber }
        val seasonTrophies = seasonRecord?.trophies ?: emptyList()

        // The squad at the moment of turnover, plus any player the club has a
        // career record for (so departed or retired players are preserved).
        val squad = career.squadOf(clubId).associateBy { it.id }
        val players = career.clubHistory.players.toMutableMap()

        for (player in squad.values) {
            val stats = player.seasonStats
            val existing = players[player.id]
            players[player.id] = PlayerCareerRecord(
                playerId = player.id,
                playerName = player.name,
                position = player.position,
                firstSeason = existing?.firstSeason?.ifBlank { career.season } ?: career.season,
                lastSeason = career.season,
                seasons = (existing?.seasons ?: 0) + 1,
                appearances = (existing?.appearances ?: 0) + stats.appearances,
                starts = (existing?.starts ?: 0) + stats.starts,
                substituteAppearances = (existing?.substituteAppearances ?: 0) +
                    (stats.appearances - stats.starts).coerceAtLeast(0),
                minutesPlayed = (existing?.minutesPlayed ?: 0) + stats.minutesPlayed,
                goals = (existing?.goals ?: 0) + stats.goals,
                assists = (existing?.assists ?: 0) + stats.assists,
                yellowCards = (existing?.yellowCards ?: 0) + stats.yellowCards,
                redCards = (existing?.redCards ?: 0) + stats.redCards,
                cleanSheets = (existing?.cleanSheets ?: 0) + stats.cleanSheets,
                manOfTheMatch = (existing?.manOfTheMatch ?: 0) + stats.manOfTheMatch,
                ratingSum = (existing?.ratingSum ?: 0.0) + stats.ratingSum,
                ratedMatches = (existing?.ratedMatches ?: 0) + stats.ratedMatches,
                // The real, week-by-week wage total, not a wage estimate.
                totalWagesPaid = player.wagesPaidCareer,
                // Career injury totals are accumulated as injuries happen.
                injuryDays = player.injuryDaysCareer,
                injuries = (existing?.injuries ?: 0) + stats.injuriesSuffered,
                longestInjuryDays = max(
                    existing?.longestInjuryDays ?: 0,
                    player.longestInjuryDaysCareer
                ),
                trophies = (existing?.trophies ?: emptyList()) + seasonTrophies,
                retired = player.clubId == null && player.age >= 34,
                active = player.clubId != null
            )
        }

        // Any player with a record who has since left the club is no longer active.
        for ((id, record) in players) {
            if (id !in squad) players[id] = record.copy(active = false)
        }

        val playerSeasons = career.clubHistory.playerSeasons + squad.values.map { player ->
            PlayerSeasonRecord(
                season = career.season,
                seasonNumber = career.seasonNumber,
                playerId = player.id,
                playerName = player.name,
                clubId = clubId,
                clubName = career.userClub.name,
                appearances = player.seasonStats.appearances,
                starts = player.seasonStats.starts,
                minutesPlayed = player.seasonStats.minutesPlayed,
                goals = player.seasonStats.goals,
                assists = player.seasonStats.assists,
                yellowCards = player.seasonStats.yellowCards,
                redCards = player.seasonStats.redCards,
                cleanSheets = player.seasonStats.cleanSheets,
                goalsConceded = player.seasonStats.goalsConceded,
                ratingSum = player.seasonStats.ratingSum,
                ratedMatches = player.seasonStats.ratedMatches,
                manOfTheMatch = player.seasonStats.manOfTheMatch,
                wagePaid = player.wagePerWeek * WEEKS_PER_SEASON,
                injuryDays = player.seasonStats.injuryDays,
                injuries = player.seasonStats.injuriesSuffered,
                longestInjuryDays = player.seasonStats.longestInjuryDays,
                trophies = seasonTrophies
            )
        }

        val history = career.clubHistory.copy(
            players = players,
            playerSeasons = playerSeasons
        )
        return career.copy(clubHistory = rebuildRecords(history, career))
    }

    /** Appends the season's trophy cabinet and transfer records, then refreshes boards. */
    fun recordTrophiesAndTransfers(career: Career): Career {
        val clubId = career.userClubId
        val seasonRecord = career.clubHistory.seasons.lastOrNull { it.seasonNumber == career.seasonNumber }
        val newTrophies = (seasonRecord?.trophies ?: emptyList()).map {
            TrophyRecord(season = career.season, competition = it, label = it)
        }

        val seasonTransfers = career.transferHistory.filter { it.season == career.season }
        val paid = seasonTransfers
            .filter { it.toClubId == clubId }
            .map {
                TransferRecordEntry(
                    playerId = it.playerId,
                    playerName = it.playerName,
                    clubName = it.toClubName,
                    otherClubName = it.fromClubName,
                    fee = it.fee,
                    season = it.season,
                    received = false
                )
            }
        val received = seasonTransfers
            .filter { it.fromClubId == clubId }
            .map {
                TransferRecordEntry(
                    playerId = it.playerId,
                    playerName = it.playerName,
                    clubName = it.fromClubName,
                    otherClubName = it.toClubName,
                    fee = it.fee,
                    season = it.season,
                    received = true
                )
            }

        val history = career.clubHistory.copy(
            trophies = (career.clubHistory.trophies + newTrophies)
                .distinctBy { it.season to it.competition },
            transfersPaid = (career.clubHistory.transfersPaid + paid)
                .distinctBy { it.playerId to it.season to it.fee }
                .sortedByDescending { it.fee }
                .take(25),
            transfersReceived = (career.clubHistory.transfersReceived + received)
                .distinctBy { it.playerId to it.season to it.fee }
                .sortedByDescending { it.fee }
                .take(25)
        )
        return career.copy(clubHistory = history)
    }

    /** Recomputes the single-season record boards from the stored season lines. */
    fun rebuildRecords(history: ClubHistory, career: Career): ClubHistory {
        val seasons = history.seasons
        if (seasons.isEmpty()) return history

        val bestGoals = seasons.maxByOrNull { it.topScorerGoals }
            ?.takeIf { it.topScorerGoals > 0 }
            ?.let {
                SeasonRecordHolder(
                    playerId = playerIdFor(history, it.topScorerName),
                    playerName = it.topScorerName,
                    season = it.season,
                    value = it.topScorerGoals.toDouble(),
                    clubName = career.userClub.name
                )
            }
        val bestAssists = seasons.maxByOrNull { it.topAssisterAssists }
            ?.takeIf { it.topAssisterAssists > 0 }
            ?.let {
                SeasonRecordHolder(
                    playerId = playerIdFor(history, it.topAssisterName),
                    playerName = it.topAssisterName,
                    season = it.season,
                    value = it.topAssisterAssists.toDouble(),
                    clubName = career.userClub.name
                )
            }
        val bestRating = seasons.maxByOrNull { it.bestRating }
            ?.takeIf { it.bestRating > 0 }
            ?.let {
                SeasonRecordHolder(
                    playerId = playerIdFor(history, it.bestRatingName),
                    playerName = it.bestRatingName,
                    season = it.season,
                    value = it.bestRating,
                    clubName = career.userClub.name
                )
            }
        val mostPoints = seasons.maxByOrNull { it.points }
            ?.takeIf { it.points > 0 }
            ?.let { SeasonRecordHolder(0L, it.championName.ifBlank { career.userClub.name }, it.season, it.points.toDouble(), career.userClub.name) }
        val fewestPoints = seasons.filter { it.played > 0 }.minByOrNull { it.points }
            ?.let { SeasonRecordHolder(0L, career.userClub.name, it.season, it.points.toDouble(), career.userClub.name) }
        val mostGoals = seasons.maxByOrNull { it.goalsFor }
            ?.takeIf { it.goalsFor > 0 }
            ?.let { SeasonRecordHolder(0L, career.userClub.name, it.season, it.goalsFor.toDouble(), career.userClub.name) }
        val fewestConceded = seasons.filter { it.played > 0 }.minByOrNull { it.goalsAgainst }
            ?.let { SeasonRecordHolder(0L, career.userClub.name, it.season, it.goalsAgainst.toDouble(), career.userClub.name) }

        // Single-season appearance, clean-sheet and MOTM records come from the
        // per-season player lines, which are richer than the club season summary.
        val seasonLines = history.playerSeasons
        val appearances = seasonLines.maxByOrNull { it.appearances }
            ?.takeIf { it.appearances > 0 }
            ?.let { SeasonRecordHolder(it.playerIdFor(history), it.playerNameFor(history), it.season, it.appearances.toDouble(), it.clubName) }
        val cleanSheets = seasonLines.filter { it.cleanSheets > 0 }.maxByOrNull { it.cleanSheets }
            ?.let { SeasonRecordHolder(it.playerIdFor(history), it.playerNameFor(history), it.season, it.cleanSheets.toDouble(), it.clubName) }
        val manOfTheMatch = seasonLines.maxByOrNull { it.manOfTheMatch }
            ?.takeIf { it.manOfTheMatch > 0 }
            ?.let { SeasonRecordHolder(it.playerIdFor(history), it.playerNameFor(history), it.season, it.manOfTheMatch.toDouble(), it.clubName) }

        return history.copy(
            recordGoalsInSeason = bestGoals,
            recordAssistsInSeason = bestAssists,
            recordRatingInSeason = bestRating,
            recordAppearancesInSeason = appearances,
            recordCleanSheetsInSeason = cleanSheets,
            recordManOfTheMatchInSeason = manOfTheMatch,
            recordHighestPoints = mostPoints,
            recordLowestPoints = fewestPoints,
            recordMostGoals = mostGoals,
            recordFewestConceded = fewestConceded
        )
    }

    private fun PlayerSeasonRecord.playerIdFor(history: ClubHistory): Long =
        history.players.values.firstOrNull { it.playerName == playerName }?.playerId ?: playerId

    private fun PlayerSeasonRecord.playerNameFor(history: ClubHistory): String = playerName

    private fun playerIdFor(history: ClubHistory, name: String): Long =
        history.players.values.firstOrNull { it.playerName == name }?.playerId ?: 0L

    /** The trophies the user's club won this season, named by competition. */
    private fun seasonTrophies(career: Career, position: Int): List<String> {
        val out = mutableListOf<String>()
        val league = League.byId(career.userLeagueId)
        if (position == 1) out += "${league.name} title"
        if (career.cup.winnerClubId == career.userClubId) out += "Domestic Cup"
        for (competition in europeanCompetitions) {
            if (career.europeanState(competition).winnerClubId == career.userClubId) {
                out += competition.label
            }
        }
        return out
    }

    /** A compact description of how the club's European campaign ended. */
    private fun europeanSummary(career: Career): String {
        val parts = mutableListOf<String>()
        for (competition in europeanCompetitions) {
            val state = career.europeanState(competition)
            if (!state.active) continue
            val position = state.positionOf(career.userClubId)
            val stage = CompetitionStatus.europeanStatus(career, career.userClubId).detail
            if (state.participantIds.contains(career.userClubId)) {
                parts += "${competition.label}: ${if (position > 0) "${position}th" else stage}"
            }
        }
        return parts.joinToString(" · ")
    }
}
