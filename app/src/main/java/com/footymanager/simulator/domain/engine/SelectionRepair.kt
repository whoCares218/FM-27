package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.data.SelectionHelper
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.LineupSlot
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.TeamSelection

/**
 * Keeps the user's team selection legal as the world changes around it.
 *
 * Players get injured, suspended, sold or run out of contract, so the stored XI
 * can silently become invalid. Rather than blocking the player at kick-off, the
 * selection is repaired in place: the smallest possible number of changes are
 * made to produce a legal, available eleven.
 */
object SelectionRepair {

    fun repair(
        selection: TeamSelection,
        squad: List<Player>,
        formation: Formation
    ): TeamSelection {
        val byId = squad.associateBy { it.id }
        val slotCount = formation.roles.size

        val assigned = mutableMapOf<Int, Long>()
        val used = mutableSetOf<Long>()

        // 1. Keep every slot whose occupant is still available and still at the club.
        for (slot in selection.startingXi) {
            if (slot.slotIndex !in 0 until slotCount) continue
            if (slot.slotIndex in assigned) continue
            val player = byId[slot.playerId] ?: continue
            if (!player.isAvailable) continue
            assigned[slot.slotIndex] = player.id
            used += player.id
        }

        // 2. Fill any empty slot with the best remaining available player.
        val available = squad.filter { it.isAvailable && it.id !in used }
        if (available.isNotEmpty()) {
            for (index in 0 until slotCount) {
                if (index in assigned) continue
                val role = formation.roles[index]
                val candidate = available
                    .filter { it.id !in used }
                    .maxByOrNull { SelectionHelper.scoreFor(it, role.naturalPosition) }
                    ?: continue
                assigned[index] = candidate.id
                used += candidate.id
            }
        }

        val startingXi = assigned.entries
            .sortedBy { it.key }
            .map { (index, playerId) ->
                val player = byId[playerId]!!
                LineupSlot(
                    slotIndex = index,
                    playerId = playerId,
                    outOfPosition = player.position != formation.roles[index].naturalPosition
                )
            }

        // 3. Rebuild the bench from whoever is left, keeping the user's picks first.
        val remaining = squad.filter { it.isAvailable && it.id !in used }
        val preferredBench = selection.substitutes.filter { id -> remaining.any { it.id == id } }
        val extraBench = remaining
            .filter { it.id !in preferredBench }
            .sortedByDescending { it.overall }
            .map { it.id }
        val substitutes = (preferredBench + extraBench).take(9)

        // 4. Captain must be on the pitch.
        val onPitch = startingXi.map { it.playerId }.toSet()
        val captain = selection.captainId?.takeIf { it in onPitch }
            ?: startingXi.mapNotNull { byId[it.playerId] }
                .maxByOrNull { it.overall + it.age * 0.3 }
                ?.id

        return selection.copy(
            startingXi = startingXi,
            substitutes = substitutes,
            captainId = captain,
            penaltyTakerId = selection.penaltyTakerId?.takeIf { it in onPitch } ?: captain,
            freeKickTakerId = selection.freeKickTakerId?.takeIf { it in onPitch } ?: captain
        )
    }

    /** Returns human-readable warnings about a selection, used on the tactics screen. */
    fun warnings(selection: TeamSelection, squad: List<Player>, formation: Formation): List<String> {
        val byId = squad.associateBy { it.id }
        val messages = mutableListOf<String>()

        if (selection.startingXi.size < formation.roles.size) {
            messages += "Only ${selection.startingXi.size} of ${formation.roles.size} positions filled"
        }
        val outOfPosition = selection.startingXi.count { it.outOfPosition }
        if (outOfPosition > 0) {
            messages += "$outOfPosition player(s) out of position"
        }
        val lowFitness = selection.startingXi
            .mapNotNull { byId[it.playerId] }
            .count { it.fitness < 65 }
        if (lowFitness > 0) {
            messages += "$lowFitness starter(s) with low fitness"
        }
        val goalkeeperSlot = selection.startingXi.firstOrNull { it.slotIndex == 0 }
        val gk = goalkeeperSlot?.let { byId[it.playerId] }
        if (gk == null || !gk.position.isGoalkeeper) {
            messages += "No recognised goalkeeper selected"
        }
        if (selection.substitutes.isEmpty()) {
            messages += "No substitutes selected"
        }
        return messages
    }
}
