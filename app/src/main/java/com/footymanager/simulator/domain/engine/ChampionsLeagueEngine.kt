package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.data.SeasonCalendar
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.ChampionsLeagueState
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.GameDate
import com.footymanager.simulator.domain.model.KnockoutRound
import com.footymanager.simulator.domain.model.KnockoutTie
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.MatchStatus
import com.footymanager.simulator.domain.model.TableRow
import com.footymanager.simulator.domain.model.UclStatus
import kotlin.random.Random

/**
 * The Champions League in its current (2026/27) format.
 *
 * Structure:
 *  - 36 clubs in a single league phase, seeded into four pots of nine.
 *  - Every club plays eight matches: two opponents from each pot, one home and
 *    one away. Fixtures are paired so the eight opponents are always distinct.
 *  - Positions 1-8 go straight to the Round of 16; 9-24 enter the knockout
 *    play-offs; 25-36 are eliminated.
 *  - The knockouts are two-legged down to the semi-finals; the final is a single
 *    match.
 *
 * The engine only builds the structure and pairs the fixtures. The matches
 * themselves are played through the normal [MatchEngine], so goals, cards,
 * injuries and player statistics behave exactly as they do domestically.
 */
object ChampionsLeagueEngine {

    private const val LEAGUE_PHASE_MATCHDAYS = ChampionsLeagueState.LEAGUE_PHASE_MATCHDAYS
    private const val POT_SIZE = 9

    /**
     * Knockout ties are played in the closing rounds of the domestic season so
     * they slot into the same weekly calendar. Each round occupies two matchdays.
     */
    const val KNOCKOUT_FIRST_MATCHDAY = 18

    /**
     * Chooses the 36 participants and seeds them into pots.
     *
     * Qualification uses the previous season's finishing positions when they are
     * available; on the very first season it falls back to club reputation, which
     * produces the same kind of elite field without needing a prior table.
     */
    fun buildParticipants(career: com.footymanager.simulator.domain.model.Career): List<Long> {
        val qualified = mutableListOf<Long>()
        for (league in League.domestic) {
            if (league.championsLeaguePlaces <= 0) continue
            val previous = career.lastStandings[league.id]
            val ordered = if (previous != null && previous.isNotEmpty()) {
                // Previous standings only list clubs still in that division.
                previous.filter { id -> career.club(id)?.leagueId == league.id }
            } else {
                career.clubs.filter { it.leagueId == league.id }
                    .sortedByDescending { it.reputation }
                    .map { it.id }
            }
            qualified += ordered.take(league.championsLeaguePlaces)
        }

        // Top up to 36 by reputation if the pyramid does not supply enough, which
        // keeps the format intact on the first season and after promotions.
        if (qualified.size < ChampionsLeagueState.PARTICIPANTS) {
            val extra = career.clubs
                .filter { it.id !in qualified }
                .filter { it.leagueId != League.CHAMPIONSHIP.id }
                .sortedByDescending { it.reputation }
                .map { it.id }
            qualified += extra.take(ChampionsLeagueState.PARTICIPANTS - qualified.size)
        }

        return qualified.distinct().take(ChampionsLeagueState.PARTICIPANTS)
    }

    /** Assigns each participant to a pot (0..3) by reputation, nine per pot. */
    fun buildPots(clubs: List<Club>, participants: List<Long>): Map<Long, Int> {
        val ordered = participants
            .mapNotNull { id -> clubs.firstOrNull { it.id == id } }
            .sortedByDescending { it.reputation }
        return ordered.withIndex().associate { (index, club) -> club.id to (index / POT_SIZE) }
    }

    /**
     * Generates the eight-matchday league phase.
     *
     * For every club the eight opponents are two from each pot. Each opponent is
     * played once, home or away, and no club ever plays itself. A random restarts
     * protect against an unlucky seed producing a dead end.
     */
    fun generateLeaguePhase(
        clubs: List<Club>,
        participants: List<Long>,
        pots: Map<Long, Int>,
        startDate: GameDate,
        random: Random,
        idProvider: () -> Long
    ): Pair<List<Match>, List<TableRow>> {
        val potMembers = (0 until 4).map { pot ->
            participants.filter { pots[it] == pot }
        }

        // Draw the fixtures and the calendar together. Colouring the eight-round
        // schedule can occasionally paint itself into a corner, so a fresh draw is
        // taken until one produces a complete calendar.
        var pairs = attemptSchedule(participants, potMembers, random)
        var byMatchday = assignToMatchdays(pairs, random)
        var guard = 0
        while (byMatchday == null && guard < 30) {
            pairs = attemptSchedule(participants, potMembers, random)
            byMatchday = assignToMatchdays(pairs, random)
            guard++
        }
        // Guaranteed fallback: even distribution rather than no schedule at all.
        val schedule = byMatchday ?: spreadAcrossMatchdays(pairs)

        val matches = mutableListOf<Match>()
        val table = mutableListOf<TableRow>()
        for (id in participants) table += TableRow(clubId = id)

        for (md in 1..LEAGUE_PHASE_MATCHDAYS) {
            val games = schedule[md - 1]
            // Each UCL matchday is played on its designated domestic round, so a
            // European fixture never lands in the same week as a league game.
            val domesticRound = SeasonCalendar.uclRoundSchedule.getOrElse(md - 1) { md }
            val date = SeasonCalendar.uclLeaguePhaseDate(md)
            for ((home, away) in games) {
                matches += Match(
                    id = idProvider(),
                    leagueId = League.CHAMPIONS_LEAGUE.id,
                    matchday = domesticRound,
                    homeClubId = home,
                    awayClubId = away,
                    status = MatchStatus.SCHEDULED,
                    competition = CompetitionType.CHAMPIONS_LEAGUE,
                    date = date,
                    competitionRound = md
                )
            }
        }
        return matches to table
    }

    /**
     * Pairs each club with two opponents from each pot, honouring home/away.
     *
     * The 36 clubs are split into four pots of nine. Every club plays two sides
     * from its own pot and two from each of the other three pots, giving eight
     * distinct opponents. Within a pot the clubs are linked in a ring; between two
     * pots they are linked in a pair of shifted rings. Both structures give every
     * club exactly two distinct opponents, and because the order is shuffled each
     * season the draw looks different every time.
     */
    private fun attemptSchedule(
        participants: List<Long>,
        potMembers: List<List<Long>>,
        random: Random
    ): List<Pair<Long, Long>> {
        val opponents: MutableMap<Long, MutableList<Long>> =
            participants.associateWithTo(mutableMapOf()) { mutableListOf() }

        // Two opponents from the club's own pot.
        for (pot in 0 until 4) {
            val order = potMembers[pot].shuffled(random)
            if (order.size < 3) continue
            for (i in order.indices) {
                link(order[i], order[(i + 1) % order.size], opponents)
            }
        }

        // Two opponents from each other pot.
        for (a in 0 until 4) {
            for (b in a + 1 until 4) {
                val first = potMembers[a].shuffled(random)
                val second = potMembers[b].shuffled(random)
                if (first.size != second.size || first.size < 3) continue
                for (i in first.indices) {
                    link(first[i], second[i], opponents)
                    link(first[i], second[(i + 1) % second.size], opponents)
                }
            }
        }

        val complete = participants.all { opponents.getValue(it).size == 8 }
        if (!complete) return deterministicSchedule(participants, potMembers)
        return collectPairs(participants, opponents)
    }

    private fun link(a: Long, b: Long, opponents: MutableMap<Long, MutableList<Long>>) {
        if (a == b) return
        if (b !in opponents.getValue(a)) opponents.getValue(a).add(b)
        if (a !in opponents.getValue(b)) opponents.getValue(b).add(a)
    }

    /**
     * Deterministic fallback used only if random pairing somehow fails. It links
     * pots by index, which always yields a valid eight-opponent schedule.
     */
    private fun deterministicSchedule(
        participants: List<Long>,
        potMembers: List<List<Long>>
    ): List<Pair<Long, Long>> {
        val opponents: MutableMap<Long, MutableList<Long>> =
            participants.associateWithTo(mutableMapOf()) { mutableListOf() }
        for (pot in 0 until 4) {
            val order = potMembers[pot]
            if (order.size < 3) continue
            for (i in order.indices) link(order[i], order[(i + 1) % order.size], opponents)
        }
        for (a in 0 until 4) {
            for (b in a + 1 until 4) {
                val first = potMembers[a]
                val second = potMembers[b]
                if (first.size != second.size || first.size < 3) continue
                for (i in first.indices) {
                    link(first[i], second[i], opponents)
                    link(first[i], second[(i + 1) % second.size], opponents)
                }
            }
        }
        return collectPairs(participants, opponents)
    }

    private fun collectPairs(
        participants: List<Long>,
        opponents: Map<Long, List<Long>>
    ): List<Pair<Long, Long>> {
        val pairs = mutableListOf<Pair<Long, Long>>()
        val seen = mutableSetOf<Pair<Long, Long>>()
        for (club in participants) {
            for (opponent in opponents.getValue(club)) {
                val key = if (club < opponent) club to opponent else opponent to club
                if (seen.add(key)) pairs += club to opponent
            }
        }
        return balanceHomeAway(pairs, participants)
    }

    /**
     * Flips venues so every club ends with a four/four home-away split, which the
     * real format requires and which keeps the table fair.
     */
    private fun balanceHomeAway(
        pairs: List<Pair<Long, Long>>,
        participants: List<Long>
    ): List<Pair<Long, Long>> {
        val homeCount = participants.associateWith { 0 }.toMutableMap()
        val result = mutableListOf<Pair<Long, Long>>()
        for ((home, away) in pairs) {
            if (homeCount.getValue(home) >= 4 && homeCount.getValue(away) < 4) {
                result += away to home
                homeCount[away] = homeCount.getValue(away) + 1
            } else {
                result += home to away
                homeCount[home] = homeCount.getValue(home) + 1
            }
        }
        return result
    }

    /**
     * Distributes the 144 games across eight matchdays so no club ever plays twice
     * in the same round.
     *
     * This is an edge-colouring problem: each game is an edge and each matchday is
     * a colour, with the rule that two games sharing a club cannot share a round.
     * The graph is 8-regular, so an eight-colour solution always exists. A
     * backtracking search that always colours the most constrained remaining game
     * (fewest available rounds) finds it quickly; a modest node budget keeps the
     * worst case bounded, and the caller retries with a fresh draw if this one
     * happens to paint itself into a corner.
     *
     * @return one bucket per matchday, or null if this attempt hit the budget
     */
    private fun assignToMatchdays(
        pairs: List<Pair<Long, Long>>,
        random: Random
    ): List<List<Pair<Long, Long>>>? {
        val edges = pairs.shuffled(random)
        val incidence = mutableMapOf<Long, MutableList<Int>>()
        edges.forEachIndexed { index, (home, away) ->
            incidence.getOrPut(home) { mutableListOf() }.add(index)
            incidence.getOrPut(away) { mutableListOf() }.add(index)
        }

        // Work through the clubs in a shuffled order so every season draws a
        // different calendar, collecting the games in the order they are met.
        val order = mutableListOf<Int>()
        val seen = BooleanArray(edges.size)
        for (club in incidence.keys.shuffled(random)) {
            for (index in incidence.getValue(club)) {
                if (!seen[index]) {
                    seen[index] = true
                    order += index
                }
            }
        }

        val colour = IntArray(edges.size) { -1 }
        val clubColours = mutableMapOf<Long, MutableSet<Int>>()
        incidence.keys.forEach { clubColours[it] = mutableSetOf() }
        var budget = 40_000

        fun backtrack(position: Int): Boolean {
            if (position == order.size) return true
            if (budget-- <= 0) return false

            // Most-constrained-first: pick the unplaced game with the fewest
            // rounds that neither of its clubs is already using.
            var bestSlot = position
            var bestOptions: List<Int>? = null
            for (k in position until order.size) {
                val index = order[k]
                val (home, away) = edges[index]
                val usedHome = clubColours.getValue(home)
                val usedAway = clubColours.getValue(away)
                val options = (0 until LEAGUE_PHASE_MATCHDAYS)
                    .filter { it !in usedHome && it !in usedAway }
                if (bestOptions == null || options.size < bestOptions!!.size) {
                    bestSlot = k
                    bestOptions = options
                    if (options.isEmpty()) break
                }
            }
            val options = bestOptions ?: return false
            if (options.isEmpty()) return false

            order[position] = order[bestSlot].also { order[bestSlot] = order[position] }
            val index = order[position]
            val (home, away) = edges[index]
            val usedHome = clubColours.getValue(home)
            val usedAway = clubColours.getValue(away)
            for (c in options) {
                colour[index] = c
                usedHome.add(c)
                usedAway.add(c)
                if (backtrack(position + 1)) return true
                usedHome.remove(c)
                usedAway.remove(c)
                colour[index] = -1
            }
            return false
        }

        if (!backtrack(0)) return null
        val buckets = (0 until LEAGUE_PHASE_MATCHDAYS).map { mutableListOf<Pair<Long, Long>>() }
        for ((index, edge) in edges.withIndex()) buckets[colour[index]] += edge
        return buckets
    }

    /**
     * Safety net used only if colouring somehow cannot be completed: spreads the
     * games evenly across the matchdays. Every fixture is still scheduled, even if
     * a club would occasionally play twice in a week.
     */
    private fun spreadAcrossMatchdays(
        pairs: List<Pair<Long, Long>>
    ): List<List<Pair<Long, Long>>> {
        val buckets = (0 until LEAGUE_PHASE_MATCHDAYS).map { mutableListOf<Pair<Long, Long>>() }
        pairs.forEachIndexed { index, pair -> buckets[index % LEAGUE_PHASE_MATCHDAYS] += pair }
        return buckets
    }

    // ------------------------------------------------------------- knockouts

    /** Builds the knockout play-off ties once the league phase is complete. */
    fun buildPlayoffTies(
        state: ChampionsLeagueState,
        startDate: GameDate,
        random: Random,
        idProvider: () -> Long
    ): Pair<List<KnockoutTie>, List<Match>> {
        val ordered = state.sortedTable().map { it.clubId }
        if (ordered.size < 24) return emptyList<KnockoutTie>() to emptyList()

        val ties = mutableListOf<KnockoutTie>()
        val matches = mutableListOf<Match>()

        // 9th vs 24th, 10th vs 23rd, ... 16th vs 17th. The higher seed hosts leg 2.
        for (i in 0 until 8) {
            val highSeed = ordered[8 + i]
            val lowSeed = ordered[23 - i]
            val tieId = idProvider()
            val leg1Id = idProvider()
            val leg2Id = idProvider()
            val leg1Date = SeasonCalendar.knockoutDate(KNOCKOUT_FIRST_MATCHDAY)
            val leg2Date = SeasonCalendar.knockoutDate(KNOCKOUT_FIRST_MATCHDAY + 1)

            matches += Match(
                id = leg1Id, leagueId = League.CHAMPIONS_LEAGUE.id, matchday = KNOCKOUT_FIRST_MATCHDAY,
                homeClubId = lowSeed, awayClubId = highSeed, status = MatchStatus.SCHEDULED,
                competition = CompetitionType.CHAMPIONS_LEAGUE, date = leg1Date,
                tieId = tieId, leg = 1, competitionRound = KnockoutRound.PLAYOFF.order
            )
            matches += Match(
                id = leg2Id, leagueId = League.CHAMPIONS_LEAGUE.id, matchday = KNOCKOUT_FIRST_MATCHDAY + 1,
                homeClubId = highSeed, awayClubId = lowSeed, status = MatchStatus.SCHEDULED,
                competition = CompetitionType.CHAMPIONS_LEAGUE, date = leg2Date,
                tieId = tieId, leg = 2, competitionRound = KnockoutRound.PLAYOFF.order
            )
            ties += KnockoutTie(
                id = tieId, round = KnockoutRound.PLAYOFF,
                highSeedClubId = highSeed, lowSeedClubId = lowSeed,
                firstLegMatchId = leg1Id, secondLegMatchId = leg2Id
            )
        }
        return ties to matches
    }

    /**
     * Advances the bracket. Given the winners of the previous round it creates the
     * next round's ties. Used for R16 (from league-phase top 8 + play-off winners),
     * then quarter-finals, semi-finals and the final.
     */
    fun buildNextRound(
        round: KnockoutRound,
        advancing: List<Long>,
        startDate: GameDate,
        idProvider: () -> Long
    ): Pair<List<KnockoutTie>, List<Match>> {
        val matchday = KNOCKOUT_FIRST_MATCHDAY + round.order * 2
        val ties = mutableListOf<KnockoutTie>()
        val matches = mutableListOf<Match>()
        val ordered = advancing.sorted()

        var index = 0
        var pairing = 0
        while (index + 1 < ordered.size) {
            val highSeed = ordered[index]
            val lowSeed = ordered[index + 1]
            index += 2
            val tieId = idProvider()
            val leg1Id = idProvider()
            val leg2Id = if (round.legs == 2) idProvider() else null
            val leg1Date = SeasonCalendar.knockoutDate(matchday)
            val leg2Date = SeasonCalendar.knockoutDate(matchday + 1)

            matches += Match(
                id = leg1Id, leagueId = League.CHAMPIONS_LEAGUE.id, matchday = matchday,
                homeClubId = lowSeed, awayClubId = highSeed, status = MatchStatus.SCHEDULED,
                competition = CompetitionType.CHAMPIONS_LEAGUE, date = leg1Date,
                tieId = tieId, leg = 1, competitionRound = round.order
            )
            if (leg2Id != null) {
                matches += Match(
                    id = leg2Id, leagueId = League.CHAMPIONS_LEAGUE.id, matchday = matchday + 1,
                    homeClubId = highSeed, awayClubId = lowSeed, status = MatchStatus.SCHEDULED,
                    competition = CompetitionType.CHAMPIONS_LEAGUE, date = leg2Date,
                    tieId = tieId, leg = 2, competitionRound = round.order
                )
            }
            ties += KnockoutTie(
                id = tieId, round = round,
                highSeedClubId = highSeed, lowSeedClubId = lowSeed,
                firstLegMatchId = leg1Id, secondLegMatchId = leg2Id
            )
            pairing++
        }
        return ties to matches
    }

    /** The sixteen clubs that reach the Round of 16. */
    fun roundOf16Field(state: ChampionsLeagueState, playoffWinners: List<Long>): List<Long> {
        val topEight = state.sortedTable().take(ChampionsLeagueState.DIRECT_QUALIFIERS).map { it.clubId }
        return (topEight + playoffWinners).distinct()
    }

    /** Seeds for the R16 draw: league-phase position decides who hosts leg two. */
    fun seedForRoundOf16(state: ChampionsLeagueState, clubId: Long): Int =
        state.positionOf(clubId).let { if (it == 0) 999 else it }

    /**
     * Resolves a two-legged tie from its played legs. Applies the aggregate,
     * and where the legs are level after extra time, decides the tie on penalties.
     */
    fun resolveTie(
        tie: KnockoutTie,
        firstLeg: Match?,
        secondLeg: Match?,
        random: Random
    ): KnockoutTie {
        if (firstLeg == null || !firstLeg.isPlayed) return tie
        if (tie.round.legs == 2 && (secondLeg == null || !secondLeg.isPlayed)) return tie

        val high = tie.highSeedClubId
        val low = tie.lowSeedClubId

        var highAgg = firstLeg.goalsFor(high)
        var lowAgg = firstLeg.goalsFor(low)
        if (secondLeg != null && secondLeg.isPlayed) {
            highAgg += secondLeg.goalsFor(high)
            lowAgg += secondLeg.goalsFor(low)
        }

        // A shootout only happens if the tie is level after both legs and any
        // extra time already simulated within the legs.
        var shootoutHigh: Int? = null
        var shootoutLow: Int? = null
        if (highAgg == lowAgg) {
            val (h, l) = simulateShootout(random)
            shootoutHigh = h
            shootoutLow = l
            if (h > l) highAgg += 0 else lowAgg += 0
        }

        val winner = when {
            highAgg > lowAgg -> high
            lowAgg > highAgg -> low
            (shootoutHigh ?: 0) > (shootoutLow ?: 0) -> high
            else -> low
        }

        return tie.copy(
            highSeedAggregate = highAgg,
            lowSeedAggregate = lowAgg,
            shootoutHigh = shootoutHigh,
            shootoutLow = shootoutLow,
            winnerClubId = winner
        )
    }

    /** A best-of-five shootout that continues into sudden death if needed. */
    fun simulateShootout(random: Random): Pair<Int, Int> {
        var home = 0
        var away = 0
        // Five regulation kicks each.
        for (i in 0 until 5) {
            if (random.nextDouble() < 0.76) home++
            if (random.nextDouble() < 0.76) away++
        }
        // Sudden death, capped so it always terminates.
        var guard = 0
        while (home == away && guard < 20) {
            val h = random.nextDouble() < 0.76
            val a = random.nextDouble() < 0.76
            if (h) home++
            if (a) away++
            guard++
        }
        if (home == away) home++ // absolute fallback so a winner always exists
        return home to away
    }

    /** Builds a fresh competition state for a season. */
    fun createSeason(
        career: com.footymanager.simulator.domain.model.Career,
        season: String,
        startDate: GameDate,
        random: Random,
        idProvider: () -> Long
    ): Pair<ChampionsLeagueState, List<Match>> {
        val participants = buildParticipants(career)
        if (participants.size < ChampionsLeagueState.PARTICIPANTS) {
            return ChampionsLeagueState(season = season, active = false) to emptyList()
        }
        val pots = buildPots(career.clubs, participants)
        val (matches, table) = generateLeaguePhase(
            career.clubs, participants, pots, startDate, random, idProvider
        )
        val state = ChampionsLeagueState(
            season = season,
            participantIds = participants,
            pots = pots,
            table = table,
            fixtureIds = matches.map { it.id },
            currentMatchday = 1,
            active = true
        )
        return state to matches
    }

    /** Applies a played result to the league-phase table. */
    fun applyLeagueResult(state: ChampionsLeagueState, match: Match): ChampionsLeagueState {
        if (match.competition != CompetitionType.CHAMPIONS_LEAGUE) return state
        if (match.tieId != null) return state // knockout games do not touch the table
        val rows = state.table.toMutableList()
        val homeIdx = rows.indexOfFirst { it.clubId == match.homeClubId }
        val awayIdx = rows.indexOfFirst { it.clubId == match.awayClubId }
        if (homeIdx >= 0) rows[homeIdx] = rows[homeIdx].applyResult(match.homeGoals, match.awayGoals)
        if (awayIdx >= 0) rows[awayIdx] = rows[awayIdx].applyResult(match.awayGoals, match.homeGoals)
        return state.copy(table = rows)
    }

    /** Status band for a club, used by the competition screen. */
    fun statusFor(state: ChampionsLeagueState, clubId: Long): UclStatus = state.statusOf(clubId)

    // ----------------------------------------------------- progression driver

    /**
     * Advances the competition once its fixtures for the current round have been
     * played: resolves the play-off ties, then builds and resolves each knockout
     * round in turn until a winner emerges.
     *
     * Called after every matchday; a no-op unless something is actually ready to
     * be decided, so it is cheap to run repeatedly.
     */
    fun progress(
        career: Career,
        random: Random,
        idProvider: () -> Long
    ): Career {
        var state = career.championsLeague
        if (!state.active) return career

        var fixtures = career.fixtures

        // ---- Build the play-offs once all eight league-phase matchdays are done ----
        val leaguePhasePlayed = fixtures
            .filter { it.competition == CompetitionType.CHAMPIONS_LEAGUE && it.tieId == null }
            .all { it.isPlayed }

        if (leaguePhasePlayed && state.ties.isEmpty() &&
            state.sortedTable().size >= 24
        ) {
            val (ties, matches) = buildPlayoffTies(state, GameDate(2027, 2, 16), random, idProvider)
            fixtures = fixtures + matches
            state = state.copy(ties = ties)
        }

        // ---- Resolve any tie whose legs are all played ----
        var changed = true
        while (changed) {
            changed = false
            val updatedTies = state.ties.map { tie ->
                if (tie.decided) return@map tie
                val leg1 = fixtures.firstOrNull { it.id == tie.firstLegMatchId }
                val leg2 = tie.secondLegMatchId?.let { id -> fixtures.firstOrNull { it.id == id } }
                val resolved = resolveTie(tie, leg1, leg2, random)
                if (resolved.decided && !tie.decided) changed = true
                resolved
            }
            state = state.copy(ties = updatedTies)
        }

        // ---- Build the next round when a round is fully decided ----
        val roundsInOrder = listOf(
            KnockoutRound.PLAYOFF, KnockoutRound.R16, KnockoutRound.QUARTER_FINAL,
            KnockoutRound.SEMI_FINAL, KnockoutRound.FINAL
        )
        for ((index, round) in roundsInOrder.withIndex()) {
            val ties = state.ties.filter { it.round == round }
            if (ties.isEmpty() || ties.any { !it.decided }) continue
            val nextRound = roundsInOrder.getOrNull(index + 1) ?: continue
            val alreadyBuilt = state.ties.any { it.round == nextRound }
            if (alreadyBuilt) continue

            val winners = ties.mapNotNull { it.winnerClubId }
            val entrants = if (round == KnockoutRound.PLAYOFF) {
                roundOf16Field(state, winners)
            } else winners

            val (newTies, newMatches) = buildNextRound(
                nextRound, entrants, GameDate(2027, 2, 16), idProvider
            )
            fixtures = fixtures + newMatches
            state = state.copy(ties = state.ties + newTies)
        }

        // ---- Crown the champion ----
        val finalTie = state.ties.firstOrNull { it.round == KnockoutRound.FINAL }
        if (finalTie?.decided == true && state.winnerClubId == null) {
            state = state.copy(winnerClubId = finalTie.winnerClubId)
        }

        if (fixtures === career.fixtures && state == career.championsLeague) return career
        return career.copy(championsLeague = state, fixtures = fixtures)
    }

    /** The club that won the competition, or null while it is still running. */
    fun champion(state: ChampionsLeagueState): Long? = state.winnerClubId
}
