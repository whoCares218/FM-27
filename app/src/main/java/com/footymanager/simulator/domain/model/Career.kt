package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase {
    PRE_SEASON,
    IN_SEASON,
    SEASON_ENDED
}

@Serializable
data class TransferWindowState(
    /** Matchday index (0-based) at which the summer window closes. */
    val summerClosesAfterMatchday: Int = 3,
    /** Matchday index (0-based) after which the winter window opens. */
    val winterOpensAfterMatchday: Int = 17,
    val winterClosesAfterMatchday: Int = 21
) {
    fun isOpen(currentMatchdayIndex: Int): Boolean =
        currentMatchdayIndex < summerClosesAfterMatchday ||
            (currentMatchdayIndex > winterOpensAfterMatchday &&
                currentMatchdayIndex <= winterClosesAfterMatchday)

    fun label(currentMatchdayIndex: Int): String = when {
        currentMatchdayIndex < summerClosesAfterMatchday -> "Summer window open"
        currentMatchdayIndex in (winterOpensAfterMatchday + 1)..winterClosesAfterMatchday ->
            "Winter window open"
        else -> "Window closed"
    }
}

/**
 * The complete serialized save game. Everything the player can change lives here
 * so persistence is a single atomic write.
 */
@Serializable
data class Career(
    val saveVersion: Int = SAVE_VERSION,
    val saveId: String,
    val managerName: String,
    val userClubId: Long,
    val season: String,
    val seasonNumber: Int,
    val difficulty: Difficulty,
    val date: GameDate,
    val phase: GamePhase = GamePhase.IN_SEASON,
    /** 0-based index into the current season's matchday list. */
    val matchdayIndex: Int = 0,
    val tactics: Tactics = Tactics.DEFAULT,
    val trainingFocus: TrainingFocus = TrainingFocus.BALANCED,
    val selection: TeamSelection = TeamSelection(),
    val clubs: List<Club>,
    val players: List<Player>,
    val fixtures: List<Match>,
    val table: Map<String, List<TableRow>>,
    val results: List<MatchResult> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val ledger: List<FinanceLedgerEntry> = emptyList(),
    val board: BoardState = BoardState(),
    val pendingOffers: List<TransferOffer> = emptyList(),
    val lastSeasonSummary: SeasonSummary? = null,
    val transferWindow: TransferWindowState = TransferWindowState(),
    val awards: List<AwardRecord> = emptyList(),
    val transferSpendThisSeason: Long = 0L,
    val transferIncomeThisSeason: Long = 0L,
    val idCounter: Long = 1L,
    val createdAtEpochMs: Long = 0L,
    val lastSavedEpochMs: Long = 0L,
    /** Stadium state for the user's club. */
    val stadium: Stadium = Stadium("", 30_000),
    /** Signed sponsorship for the current season, if any. */
    val sponsorship: Sponsorship? = null,
    /** Offers the board is considering before a new season. */
    val sponsorOffers: List<SponsorOffer> = emptyList(),
    /** Champions League state, or [ChampionsLeagueState.EMPTY] when not involved. */
    val championsLeague: ChampionsLeagueState = ChampionsLeagueState.EMPTY,
    /** Rewarded-ad allowance and totals. */
    val adRewards: AdRewardState = AdRewardState(),
    /** Season number the sponsorship was signed for, so it expires correctly. */
    val sponsorshipSeason: Int = 0,
    /**
     * Final domestic standings of the previous season, leagueId -> ordered club ids.
     * Drives Champions League qualification in the following campaign.
     */
    val lastStandings: Map<String, List<Long>> = emptyMap(),
    /** Prize money and competition revenue already banked this season. */
    val competitionRevenueThisSeason: Long = 0L,
    /** Fan mood (0..100). Drives attendance, atmosphere and gate receipts. */
    val fanSatisfaction: Int = 60,
    /** The manager's cumulative record across the whole career. */
    val managerRecord: ManagerRecord = ManagerRecord(),
    /**
     * The live match currently paused or in progress, if any. Persisted with the
     * career so interruption at minute 30 resumes at minute 30 rather than
     * restarting. Null when no match is active.
     */
    val inProgressMatch: InProgressMatchState? = null,
    /** The financial report for the user's most recent home match. */
    val lastMatchdayFinance: MatchdayFinance? = null,
    /** The manager's live sale negotiation, if one is open. */
    val pendingSale: SaleNegotiation? = null,
    /** Players the manager has listed for sale, with asking prices. */
    val transferListings: List<TransferListing> = emptyList(),
    /** Season-by-season revenue and expense records for the financial history. */
    val financialHistory: List<SeasonFinanceRecord> = emptyList(),
    /**
     * Outstanding transfer-fee amortisation, one charge per signing. The sum is
     * what feeds the squad-cost ratio alongside the wage bill.
     */
    val amortisationBook: List<AmortisationCharge> = emptyList()
) {
    /** Outstanding transfer-fee amortisation carried this season, per week. */
    val weeklyAmortisation: Long
        get() = amortisationBook.sumOf { it.weeklyCharge }
    val userClub: Club
        get() = clubs.first { it.id == userClubId }

    fun club(id: Long): Club? = clubs.firstOrNull { it.id == id }

    fun clubOrThrow(id: Long): Club = club(id) ?: error("Unknown club $id")

    fun player(id: Long): Player? = players.firstOrNull { it.id == id }

    fun squadOf(clubId: Long): List<Player> =
        players.filter { it.clubId == clubId }.sortedBy { Position.sortOrder(it.position) }

    val userSquad: List<Player> get() = squadOf(userClubId)

    fun leagueTable(leagueId: String): List<TableRow> = table[leagueId].orEmpty()

    /** Table rows for a league ordered with standard football tiebreakers. */
    fun sortedTable(leagueId: String): List<TableRow> =
        leagueTable(leagueId).sortedWith(TableRow.comparator)

    fun positionOf(clubId: Long, leagueId: String): Int {
        val sorted = sortedTable(leagueId)
        val idx = sorted.indexOfFirst { it.clubId == clubId }
        return if (idx < 0) 0 else idx + 1
    }

    val userLeagueId: String get() = userClub.leagueId

    val userLeaguePosition: Int get() = positionOf(userClubId, userLeagueId)

    fun nextMatch(): Match? =
        fixtures.filter { it.leagueId == userLeagueId && !it.isPlayed && it.involves(userClubId) }
            .minByOrNull { it.matchday }

    /** The user's next fixture in any competition, ordered by date then id. */
    fun nextFixtureAnyCompetition(): Match? =
        fixtures
            .filter { !it.isPlayed && it.involves(userClubId) }
            .sortedWith(compareBy({ it.date?.let { d -> dateSortKey(d) } ?: Int.MAX_VALUE }, { it.id }))
            .firstOrNull()

    /** Every unplayed fixture in the world, ordered by date. */
    fun upcomingFixtures(limit: Int = 20): List<Match> =
        fixtures
            .filter { !it.isPlayed }
            .sortedWith(compareBy({ it.date?.let { d -> dateSortKey(d) } ?: Int.MAX_VALUE }, { it.id }))
            .take(limit)

    /** All of a club's fixtures in a given competition. */
    fun fixturesIn(clubId: Long, competition: CompetitionType): List<Match> =
        fixturesForClub(clubId).filter { it.competition == competition }

    /** Champions League fixtures for the user's club. */
    fun uclFixturesFor(clubId: Long): List<Match> =
        fixtures.filter { it.competition == CompetitionType.CHAMPIONS_LEAGUE && it.involves(clubId) }
            .sortedWith(compareBy({ it.matchday }, { it.leg }))

    /** The user's next Champions League fixture, if any. */
    fun nextUclFixture(): Match? =
        uclFixturesFor(userClubId).firstOrNull { !it.isPlayed }

    private fun dateSortKey(d: GameDate): Int = d.year * 10_000 + d.month * 100 + d.day

    fun fixturesForClub(clubId: Long): List<Match> =
        fixtures.filter { it.involves(clubId) }.sortedBy { it.matchday }

    fun resultsForClub(clubId: Long): List<Match> =
        fixturesForClub(clubId).filter { it.isPlayed }

    fun leagueMatchesFor(leagueId: String, matchday: Int): List<Match> =
        fixtures.filter { it.leagueId == leagueId && it.matchday == matchday }

    /** Most recent results first, capped at [count]. */
    fun recentForm(clubId: Long, count: Int = 5): List<Match> =
        resultsForClub(clubId).takeLast(count).reversed()

    /**
     * Number of matchdays in the user's own league. Leagues have different sizes,
     * so this must be scoped to the competition the player is actually in rather
     * than the longest schedule in the world.
     */
    fun totalMatchdays(): Int =
        fixtures.filter { it.leagueId == userLeagueId }
            .maxOfOrNull { it.matchday }
            ?: fixtures.maxOfOrNull { it.matchday }
            ?: 0

    fun nextMatchdayNumber(): Int = (matchdayIndex + 1).coerceAtMost(totalMatchdays().coerceAtLeast(1))

    fun wageBill(clubId: Long): Long = players.filter { it.clubId == clubId }.sumOf { it.wagePerWeek }

    fun totalGoalsFor(clubId: Long): Int =
        fixtures.filter { it.isPlayed && it.homeClubId == clubId }.sumOf { it.homeGoals } +
            fixtures.filter { it.isPlayed && it.awayClubId == clubId }.sumOf { it.awayGoals }

    fun nextId(): Long = idCounter + 1

    companion object {
        const val SAVE_VERSION = 1
        const val CURRENT_SEASON = "2026/27"
        const val NEXT_SEASON = "2027/28"
    }
}
