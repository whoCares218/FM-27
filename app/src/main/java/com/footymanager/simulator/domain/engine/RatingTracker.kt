package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayerMatchRating
import com.footymanager.simulator.domain.model.RatingSnapshot
import com.footymanager.simulator.domain.model.SlotRole
import com.footymanager.simulator.domain.model.TeamMatchStats
import com.footymanager.simulator.domain.model.TeamSelection
import kotlin.math.roundToInt

/**
 * Accumulates per-player contributions during a match and converts them into
 * match ratings on the familiar 4.0 - 10.0 scale.
 *
 * The tracker is self-contained: it stores the [MatchPlayer] it was told about,
 * so a snapshot can rebuild the whole rating picture — including players who
 * have already been substituted and are no longer on the pitch — without
 * needing them to be re-resolved from the live XI.
 */
internal class RatingTracker {

    private data class Entry(
        val player: MatchPlayer,
        val isHome: Boolean,
        var goals: Int = 0,
        var assists: Int = 0,
        var yellows: Int = 0,
        var reds: Int = 0,
        var minutes: Int = 90
    )

    private val entries = mutableMapOf<Long, Entry>()

    /**
     * Ensures an entry exists for every player. Idempotent: re-registering a
     * player leaves their accumulated counters intact, which is what lets a
     * substitute be registered the moment they come on and lets a restored
     * snapshot re-register the XI without losing goals already scored.
     */
    fun register(players: List<MatchPlayer>, isHome: Boolean) {
        for (p in players) {
            entries.getOrPut(p.id) { Entry(p, isHome) }
        }
    }

    /** Captures each entry so a snapshot can rebuild it exactly. */
    fun snapshot(): List<RatingSnapshot> = entries.values.map {
        RatingSnapshot(
            playerId = it.player.id,
            isHome = it.isHome,
            goals = it.goals,
            assists = it.assists,
            yellows = it.yellows,
            reds = it.reds,
            minutes = it.minutes,
            ability = it.player.effectiveAbility,
            isGoalkeeper = it.player.isGoalkeeper
        )
    }

    /**
     * Rebuilds every entry from a snapshot using the supplied squad lookups, so
     * players who are no longer on the pitch still contribute their rating.
     */
    fun restore(
        snapshots: List<RatingSnapshot>,
        homeSquad: Map<Long, Player>,
        awaySquad: Map<Long, Player>
    ) {
        entries.clear()
        for (s in snapshots) {
            val squad = if (s.isHome) homeSquad else awaySquad
            val player = squad[s.playerId] ?: continue
            val matchPlayer = MatchPlayer(
                player = player,
                slot = if (s.isGoalkeeper) SlotRole.GK else SlotRole.CM,
                effectiveAbility = s.ability,
                conditionFactor = 1.0,
                confidenceFactor = 1.0
            )
            entries[s.playerId] = Entry(
                player = matchPlayer,
                isHome = s.isHome,
                goals = s.goals,
                assists = s.assists,
                yellows = s.yellows,
                reds = s.reds,
                minutes = s.minutes
            )
        }
    }

    /** Records how long a player was actually on the pitch. */
    fun setMinutes(playerId: Long, minutes: Int) {
        entries[playerId]?.minutes = minutes.coerceIn(1, 120)
    }

    fun addGoal(playerId: Long) {
        entries[playerId]?.let { it.goals++ }
    }

    fun addAssist(playerId: Long) {
        entries[playerId]?.let { it.assists++ }
    }

    fun addYellow(playerId: Long) {
        entries[playerId]?.let { it.yellows++ }
    }

    fun addRed(playerId: Long) {
        entries[playerId]?.let { it.reds++ }
    }

    fun finish(
        homeStats: TeamMatchStats,
        awayStats: TeamMatchStats,
        homeStrength: TeamStrength,
        awayStrength: TeamStrength,
        homeSelection: TeamSelection,
        awaySelection: TeamSelection
    ): List<PlayerMatchRating> {
        val homeGoalDiff = homeStats.goals - awayStats.goals
        val awayGoalDiff = -homeGoalDiff

        val ratings = entries.values.map { entry ->
            val strength = if (entry.isHome) homeStrength else awayStrength
            val opponentStrength = if (entry.isHome) awayStrength else homeStrength
            val stats = if (entry.isHome) homeStats else awayStats
            val goalDiff = if (entry.isHome) homeGoalDiff else awayGoalDiff

            var rating = if (entry.player.isGoalkeeper) 6.8 else 6.5

            // Individual contributions.
            rating += entry.goals * 0.95
            rating += entry.assists * 0.60
            rating -= entry.yellows * 0.25
            rating -= entry.reds * 1.60

            // Team result effect, dampened when the opponent was much stronger.
            val qualityGap = (strength.overall - opponentStrength.overall) / 20.0
            val resultWeight = (1.0 - qualityGap * 0.5).coerceIn(0.45, 1.35)
            rating += goalDiff.coerceIn(-4, 4) * 0.12 * resultWeight

            // Defensive rewards: clean sheets for keepers and defenders.
            if (entry.player.slot.isDefensive || entry.player.isGoalkeeper) {
                if (stats.goals == 0) rating += 0.35
                rating -= stats.goals * 0.18
            }
            // Goalkeepers get credit for the saves implied by shots faced.
            if (entry.player.isGoalkeeper) {
                val saves = (stats.shotsOnTarget - stats.goals).coerceAtLeast(0)
                rating += saves * 0.07
            }

            // Players who performed below the team's level drift toward the mean.
            val abilityDelta = (entry.player.effectiveAbility - strength.averageRating) / 30.0
            rating += abilityDelta * 0.25

            PlayerMatchRating(
                playerId = entry.player.id,
                playerName = entry.player.name,
                rating = (rating.coerceIn(3.5, 10.0) * 10).roundToInt() / 10.0,
                goals = entry.goals,
                assists = entry.assists,
                minutesPlayed = entry.minutes,
                yellowCards = entry.yellows,
                redCards = entry.reds
            )
        }.toMutableList()

        // Flag the best performer as man of the match, preferring the winning side.
        val winnerIds = when {
            homeStats.goals > awayStats.goals -> homeSelection.startingXi.map { it.playerId }.toSet()
            awayStats.goals > homeStats.goals -> awaySelection.startingXi.map { it.playerId }.toSet()
            else -> emptySet()
        }
        val best = ratings
            .filter { winnerIds.isEmpty() || it.playerId in winnerIds }
            .maxByOrNull { it.rating }
            ?: ratings.maxByOrNull { it.rating }

        return ratings.map { r ->
            if (r.playerId == best?.playerId) r.copy(isManOfTheMatch = true) else r
        }
    }
}
