package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.ObjectiveStatus
import com.footymanager.simulator.domain.model.SeasonSummary

/** Builds the end-of-season report shown to the manager. */
object SeasonSummaryBuilder {

    fun build(career: Career): SeasonSummary {
        val league = League.byId(career.userLeagueId)
        val sorted = career.sortedTable(career.userLeagueId)
        val position = sorted.indexOfFirst { it.clubId == career.userClubId } + 1
        val row = sorted.firstOrNull { it.clubId == career.userClubId }

        val squad = career.squadOf(career.userClubId)
        val topScorer = squad.maxByOrNull { it.seasonStats.goals }
        val topAssister = squad.maxByOrNull { it.seasonStats.assists }
        val bestPlayer = squad
            .filter { it.seasonStats.ratedMatches >= 5 }
            .maxByOrNull { it.seasonStats.averageRating }

        val champion = sorted.firstOrNull()?.let { career.club(it.clubId)?.name } ?: ""
        val trophies = mutableListOf<String>()
        if (position == 1) trophies += "${league.name} title"
        else if (position <= league.championsLeaguePlaces && league.championsLeaguePlaces > 0) {
            trophies += "Champions League qualification"
        } else if (position <= 6) trophies += "European qualification"

        val boardEvaluation = buildBoardEvaluation(career, position)

        return SeasonSummary(
            season = career.season,
            leagueName = league.name,
            clubName = career.userClub.name,
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
            playerOfSeasonName = bestPlayer?.name ?: "-",
            playerOfSeasonRating = bestPlayer?.seasonStats?.averageRating ?: 0.0,
            transferSpend = career.transferSpendThisSeason,
            transferIncome = career.transferIncomeThisSeason,
            boardEvaluation = boardEvaluation,
            trophies = trophies,
            championName = champion,
            sacked = career.board.managerSacked
        )
    }

    private fun buildBoardEvaluation(career: Career, position: Int): String {
        val target = career.userClub.targetLeaguePosition
        val objectiveStatus = career.board.objectives
            .firstOrNull { it.title == "League" }?.status

        val performance = when {
            position == 1 -> "The board is ecstatic: a league title."
            position <= 4 -> "The board is delighted with a top-four finish."
            position <= target -> "The board is pleased the season objective was met."
            position <= target + 4 -> "The board accepts the finish but expected slightly better."
            position <= target + 8 -> "The board is disappointed the objective was missed."
            else -> "The board is deeply unhappy with the final league position."
        }

        val financials = if (career.userClub.balance >= 0) {
            " Financially the club remains healthy."
        } else {
            " The club finished the season in debt, which concerns the board."
        }

        val objectiveNote = when (objectiveStatus) {
            ObjectiveStatus.ACHIEVED -> " The stated objective was achieved."
            ObjectiveStatus.AT_RISK -> " The stated objective was narrowly missed."
            ObjectiveStatus.FAILED -> " The stated objective was not met."
            else -> ""
        }

        return performance + objectiveNote + financials
    }
}
