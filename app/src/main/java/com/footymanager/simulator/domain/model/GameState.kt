package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty(
    val label: String,
    val description: String,
    /** Multiplier on the AI-controlled teams' effective strength. */
    val aiStrength: Double,
    /** Extra fee a selling club demands before accepting. */
    val transferDifficulty: Double,
    /** Board is more forgiving when this is higher. */
    val boardLeniency: Double,
    /** Scales the wage/transfer budget the board grants. */
    val financialPressure: Double,
    /** Scales how quickly players develop. */
    val developmentRate: Double
) {
    EASY(
        label = "Easy",
        description = "Generous board, easier negotiations, AI teams slightly weaker.",
        aiStrength = 0.94,
        transferDifficulty = 0.90,
        boardLeniency = 1.25,
        financialPressure = 1.20,
        developmentRate = 1.15
    ),
    NORMAL(
        label = "Normal",
        description = "The intended experience: balanced boards, transfers and AI.",
        aiStrength = 1.0,
        transferDifficulty = 1.0,
        boardLeniency = 1.0,
        financialPressure = 1.0,
        developmentRate = 1.0
    ),
    HARD(
        label = "Hard",
        description = "Demanding board, tougher negotiations, stronger AI squads.",
        aiStrength = 1.06,
        transferDifficulty = 1.12,
        boardLeniency = 0.82,
        financialPressure = 0.88,
        developmentRate = 0.92
    )
}

@Serializable
enum class TrainingFocus(val label: String, val description: String) {
    ATTACK("Attack", "Sharpen shooting and movement in the final third"),
    DEFENCE("Defence", "Improve positioning, tackling and concentration"),
    FITNESS("Fitness", "Build stamina so players recover faster"),
    POSSESSION("Possession", "Passing drills and keeping the ball"),
    BALANCED("Balanced", "Even spread across every area")
}

/** A simple calendar date; only what the season flow needs. */
@Serializable
data class GameDate(val year: Int, val month: Int, val day: Int) {
    val dayOfWeekIndex: Int
        get() = dayOfWeek(year, month, day)

    val dayName: String get() = DAY_NAMES[dayOfWeekIndex]

    fun display(): String = "$dayName $day ${MONTH_NAMES[month - 1]} $year"

    fun short(): String = "$day ${MONTH_NAMES[month - 1].take(3)}"

    fun plusDays(days: Int): GameDate {
        var d = day
        var m = month
        var y = year
        var remaining = days
        while (remaining > 0) {
            val dim = daysInMonth(y, m)
            if (d + remaining <= dim) {
                d += remaining
                remaining = 0
            } else {
                remaining -= (dim - d + 1)
                d = 1
                m += 1
                if (m > 12) {
                    m = 1
                    y += 1
                }
            }
        }
        return GameDate(y, m, d)
    }

    companion object {
        val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        fun isLeapYear(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

        fun daysInMonth(y: Int, m: Int): Int = when (m) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(y)) 29 else 28
            else -> 30
        }

        /** Day-of-week index where 0 = Monday. */
        fun dayOfWeek(y: Int, m: Int, d: Int): Int {
            val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
            val yy = if (m < 3) y - 1 else y
            val idx = (yy + yy / 4 - yy / 100 + yy / 400 + t[m - 1] + d) % 7
            // Zeller yields 0 = Sunday; convert to 0 = Monday.
            return (idx + 6) % 7
        }
    }
}

@Serializable
enum class NewsCategory { TRANSFER, INJURY, MATCH, BOARD, GENERAL, TRANSFER_WINDOW, TRAINING, AWARD }

@Serializable
data class NewsItem(
    val id: Long,
    val category: NewsCategory,
    val headline: String,
    val body: String,
    val date: GameDate,
    val season: String,
    /** Club the story is about, when relevant. */
    val clubId: Long? = null
)

@Serializable
enum class LedgerCategory {
    TRANSFER_IN, TRANSFER_OUT, WAGES, MATCHDAY, PRIZE_MONEY, BOARD_INJECTION, BOARD_ADJUSTMENT, OTHER;

    val label: String
        get() = when (this) {
            TRANSFER_IN -> "Transfer"
            TRANSFER_OUT -> "Sale"
            WAGES -> "Wages"
            MATCHDAY -> "Matchday"
            PRIZE_MONEY -> "Prize money"
            BOARD_INJECTION -> "Board investment"
            BOARD_ADJUSTMENT -> "Board adjustment"
            OTHER -> "Other"
        }
}

@Serializable
data class FinanceLedgerEntry(
    val id: Long,
    val date: GameDate,
    val season: String,
    val description: String,
    /** Positive = money in, negative = money out. */
    val amount: Long,
    val category: LedgerCategory
)

@Serializable
enum class ObjectiveStatus {
    ON_TRACK, AT_RISK, FAILED, ACHIEVED;

    val label: String
        get() = when (this) {
            ON_TRACK -> "On track"
            AT_RISK -> "At risk"
            FAILED -> "Failed"
            ACHIEVED -> "Achieved"
        }
}

@Serializable
data class BoardObjective(
    val title: String,
    val description: String,
    val status: ObjectiveStatus = ObjectiveStatus.ON_TRACK
)

@Serializable
data class BoardState(
    /** 0..100; below 25 the manager is sacked. */
    val confidence: Int = 60,
    val objectives: List<BoardObjective> = emptyList(),
    val warningsIssued: Int = 0,
    val managerSacked: Boolean = false,
    val lastEvaluation: String = "The board is satisfied with the direction of the club."
)

@Serializable
enum class OfferStatus { PENDING, ACCEPTED, REJECTED, WITHDRAWN, COMPLETED, COLLAPSED }

/** A transfer negotiation, kept deliberately lightweight for mobile play. */
@Serializable
data class TransferOffer(
    val id: Long,
    val playerId: Long,
    val playerName: String,
    val fromClubId: Long,
    val toClubId: Long,
    val fee: Long,
    val wagePerWeek: Long,
    val contractYears: Int = 3,
    val status: OfferStatus = OfferStatus.PENDING,
    /** Fee the selling club will accept right now. */
    val askingPrice: Long,
    /** Wage the player will accept right now. */
    val expectedWage: Long,
    val isUserInitiated: Boolean,
    val isLoan: Boolean = false,
    val createdMatchday: Int = 1,
    val message: String = ""
)

@Serializable
data class SeasonSummary(
    val season: String,
    val leagueName: String,
    val clubName: String,
    val finalPosition: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int,
    val topScorerName: String,
    val topScorerGoals: Int,
    val topAssisterName: String,
    val topAssisterAssists: Int,
    val playerOfSeasonName: String,
    val playerOfSeasonRating: Double,
    val transferSpend: Long,
    val transferIncome: Long,
    val boardEvaluation: String,
    val trophies: List<String> = emptyList(),
    val championName: String = "",
    val sacked: Boolean = false
)

/** Player of the matchday awards and end-of-season honours, kept per season. */
@Serializable
data class AwardRecord(
    val season: String,
    val playerId: Long,
    val playerName: String,
    val clubName: String,
    val description: String
)
