package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.data.SelectionHelper
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.DefensiveLine
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Tactics
import com.footymanager.simulator.domain.model.TeamSelection
import com.footymanager.simulator.domain.model.Tempo
import kotlin.math.abs

/**
 * Drives every club the player does not control: formation choice, team
 * selection and (during transfer windows) squad building.
 *
 * Everything here is derived deterministically from club id, reputation and
 * current form, so AI clubs behave consistently without needing to persist a
 * tactics object per club.
 */
object AiManager {

    /** Picks a formation for an AI club based on quality and recent results. */
    fun tacticsFor(club: Club, matchdayIndex: Int, recentPointsPerGame: Double): Tactics {
        val seed = (club.id * 31 + matchdayIndex * 7).toInt()
        val performing = recentPointsPerGame >= 1.6
        val struggling = recentPointsPerGame in 0.0..0.7

        val formationId = when {
            club.reputation >= 88 -> if (seed % 3 == 0) Formation.F433.id else Formation.F4231.id
            club.reputation >= 80 -> when (seed % 3) {
                0 -> Formation.F433.id
                1 -> Formation.F4231.id
                else -> Formation.F442.id
            }
            club.reputation >= 70 -> when (seed % 3) {
                0 -> Formation.F4231.id
                1 -> Formation.F442.id
                else -> Formation.F352.id
            }
            club.reputation >= 62 -> when (seed % 4) {
                0 -> Formation.F442.id
                1 -> Formation.F352.id
                2 -> Formation.F532.id
                else -> Formation.F4231.id
            }
            else -> if (seed % 2 == 0) Formation.F532.id else Formation.F442.id
        }

        // Struggling sides become more conservative; in-form sides push on.
        val mentality = when {
            struggling -> if (seed % 2 == 0) Mentality.DEFENSIVE else Mentality.BALANCED
            performing && club.reputation >= 78 -> Mentality.ATTACKING
            else -> Mentality.BALANCED
        }

        val style = when {
            club.reputation >= 86 -> if (seed % 2 == 0) PlayStyle.POSSESSION else PlayStyle.HIGH_PRESS
            club.reputation >= 76 -> if (seed % 3 == 0) PlayStyle.POSSESSION else PlayStyle.BALANCED
            club.reputation >= 66 -> if (seed % 2 == 0) PlayStyle.BALANCED else PlayStyle.COUNTER_ATTACK
            else -> if (seed % 3 == 0) PlayStyle.DIRECT else PlayStyle.COUNTER_ATTACK
        }

        val line = when {
            mentality == Mentality.DEFENSIVE || mentality == Mentality.VERY_DEFENSIVE -> DefensiveLine.DEEP
            style == PlayStyle.HIGH_PRESS -> DefensiveLine.HIGH
            else -> DefensiveLine.NORMAL
        }

        val tempo = when {
            style == PlayStyle.DIRECT -> Tempo.FAST
            style == PlayStyle.POSSESSION -> Tempo.SLOW
            else -> Tempo.NORMAL
        }

        return Tactics(
            formationId = formationId,
            mentality = mentality,
            style = style,
            defensiveLine = line,
            tempo = tempo
        )
    }

    fun selectionFor(squad: List<Player>, tactics: Tactics): TeamSelection =
        SelectionHelper.autoPickBest(squad, Formation.byId(tactics.formationId))

    /**
     * Chooses which of an AI club's players to list for transfer. Squad players
     * who are surplus to requirements, ageing, or unhappy are made available.
     */
    fun playersForSale(squad: List<Player>, club: Club): List<Player> {
        val byPosition = squad.groupBy { it.position }
        val surplus = mutableListOf<Player>()

        for ((position, group) in byPosition) {
            val depth = requiredDepth(position)
            val sorted = group.sortedByDescending { it.overall }
            // Everything beyond the required depth is expendable.
            surplus += sorted.drop(depth)
        }

        // Veterans on high wages are always available at the right price.
        surplus += squad.filter { it.age >= 32 && it.overall < 76 }
        // Deeply unhappy players agitate for a move.
        surplus += squad.filter { it.moraleScore < 2.2 }

        return surplus.distinctBy { it.id }.sortedByDescending { it.value }
    }

    private fun requiredDepth(position: com.footymanager.simulator.domain.model.Position): Int =
        when (position) {
            com.footymanager.simulator.domain.model.Position.GK -> 2
            com.footymanager.simulator.domain.model.Position.CB -> 4
            com.footymanager.simulator.domain.model.Position.RB,
            com.footymanager.simulator.domain.model.Position.LB -> 2
            com.footymanager.simulator.domain.model.Position.CDM,
            com.footymanager.simulator.domain.model.Position.CM -> 4
            com.footymanager.simulator.domain.model.Position.CAM -> 2
            com.footymanager.simulator.domain.model.Position.RW,
            com.footymanager.simulator.domain.model.Position.LW -> 2
            com.footymanager.simulator.domain.model.Position.ST -> 3
        }

    /** Fee an AI club wants before it will sell, scaled by difficulty. */
    fun askingPrice(player: Player, sellingClub: Club, difficulty: Double): Long {
        val surplus = playersForSale(listOf(player), sellingClub).any { it.id == player.id }
        val reluctance = when {
            surplus -> 0.95
            player.overall >= 84 -> 1.55
            player.overall >= 78 -> 1.38
            player.overall >= 72 -> 1.24
            player.age <= 21 -> 1.30
            else -> 1.14
        }
        val fee = (player.value * reluctance * difficulty).toLong()
        return fee.coerceAtLeast(player.value)
    }

    /** Wage a player will ask for, based on ability and their current deal. */
    fun expectedWage(player: Player, buyingClubReputation: Int, difficulty: Double): Long {
        val marketWage = player.recomputeWage()
        val reputationPremium = 1.0 + (buyingClubReputation - 70) * 0.004
        val unhappyDiscount = if (player.moraleScore < 2.5) 0.94 else 1.0
        val wage = (marketWage * reputationPremium * difficulty * unhappyDiscount).toLong()
        return wage.coerceAtLeast(player.wagePerWeek)
    }

    /** True when an AI club should try to sign a replacement in this position. */
    fun needsStrengthening(squad: List<Player>): Boolean {
        val firstTeamQuality = squad.sortedByDescending { it.overall }.take(11).map { it.overall }
        if (firstTeamQuality.isEmpty()) return true
        val weakest = firstTeamQuality.min()
        val average = firstTeamQuality.average()
        // A large gap between the average and the weakest starter signals a hole.
        return (average - weakest) > 9.0 || squad.count { it.overall >= 70 } < 8
    }

    /** Position an AI club is most short in. */
    fun weakestPosition(squad: List<Player>): com.footymanager.simulator.domain.model.Position {
        val positions = com.footymanager.simulator.domain.model.Position.entries
        return positions
            .filter { it != com.footymanager.simulator.domain.model.Position.GK || squad.count { p -> p.position == it } < 2 }
            .maxByOrNull { position ->
                val depth = squad.count { it.position == position }
                val quality = squad.filter { it.position == position }.maxOfOrNull { it.overall } ?: 0
                (requiredDepth(position) - depth) * 12.0 + (78 - quality).coerceAtLeast(0) * 0.5
            } ?: com.footymanager.simulator.domain.model.Position.CM
    }

    /** Recent points per game, used to shape AI tactics. */
    fun recentPointsPerGame(
        results: List<com.footymanager.simulator.domain.model.Match>,
        clubId: Long,
        lastN: Int = 6
    ): Double {
        val recent = results.takeLast(lastN)
        if (recent.isEmpty()) return 1.2
        val points: Int = recent.sumOf { m: com.footymanager.simulator.domain.model.Match ->
            val scored = if (m.homeClubId == clubId) m.homeGoals else m.awayGoals
            val conceded = if (m.homeClubId == clubId) m.awayGoals else m.homeGoals
            val matchPoints: Int = when {
                scored > conceded -> 3
                scored == conceded -> 1
                else -> 0
            }
            matchPoints
        }
        return points.toDouble() / recent.size
    }

    /**
     * Board confidence adjustment for AI clubs is intentionally shallow: it only
     * feeds news, never sacking, so the simulation stays cheap.
     */
    fun isInCrisis(results: List<com.footymanager.simulator.domain.model.Match>, clubId: Long): Boolean =
        abs(recentPointsPerGame(results, clubId, 8) - 1.0) > 0.9 &&
            recentPointsPerGame(results, clubId, 8) < 0.6
}
