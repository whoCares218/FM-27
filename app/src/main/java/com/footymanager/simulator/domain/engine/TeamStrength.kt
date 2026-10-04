package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.PositionSuitability
import com.footymanager.simulator.domain.model.SlotRole
import kotlin.math.pow

/**
 * A player as they appear on the pitch for one match, with every situational
 * modifier already applied. The simulation only reads from this object, which
 * keeps the engine free of squad/selection bookkeeping.
 */
data class MatchPlayer(
    val player: Player,
    val slot: SlotRole,
    /** Ability in the assigned slot after out-of-position penalties. */
    val effectiveAbility: Double,
    /** Fitness-derived multiplier applied to attacking and defensive actions. */
    val conditionFactor: Double,
    /** Morale/form multiplier applied to decisive moments. */
    val confidenceFactor: Double,
    /** The manager's individual instruction for this player, if any. */
    val instruction: com.footymanager.simulator.domain.model.IndividualInstruction =
        com.footymanager.simulator.domain.model.IndividualInstruction.DEFAULT
) {
    val id: Long get() = player.id
    val name: String get() = player.name
    val isGoalkeeper: Boolean get() = slot == SlotRole.GK

    /** Attacking contribution, used to weight who takes chances. */
    val attackWeight: Double
        get() {
            if (isGoalkeeper) return 0.0
            val a = player.attributes
            val roleBias = when {
                slot.isAttacking -> 1.30
                slot.isDefensive -> 0.55
                else -> 1.0
            }
            return (a.shooting * 0.5 + a.dribbling * 0.3 + a.pace * 0.2) *
                roleBias * effectiveAbility / 70.0 * instruction.attackModifier
        }

    /** Chance creation contribution, used to weight who provides assists. */
    val creativityWeight: Double
        get() {
            if (isGoalkeeper) return 0.0
            val a = player.attributes
            val roleBias = when {
                slot.isAttacking -> 1.20
                slot.isDefensive -> 0.60
                else -> 1.05
            }
            return (a.passing * 0.6 + a.dribbling * 0.4) * roleBias * effectiveAbility / 70.0 *
                instruction.attackModifier
        }

    /** Defensive contribution, used to suppress opponent chance quality. */
    val defensiveWeight: Double
        get() {
            if (isGoalkeeper) return 0.0
            val a = player.attributes
            val roleBias = when {
                slot.isDefensive -> 1.25
                slot.isAttacking -> 0.70
                else -> 1.0
            }
            return (a.defending * 0.7 + a.physical * 0.3) * roleBias * effectiveAbility / 70.0 *
                instruction.defenceModifier
        }
}

/** Aggregated strength of one side, produced once per match. */
data class TeamStrength(
    val clubId: Long,
    /** 0..100 style rating used for the pre-match difficulty readout. */
    val overall: Double,
    val attack: Double,
    val midfield: Double,
    val defence: Double,
    val goalkeeping: Double,
    /** Average effective ability of the XI, shown in the UI. */
    val averageRating: Double
)

/**
 * Turns a selection and a set of tactics into the per-player and per-unit
 * numbers the match loop consumes.
 */
object TeamStrengthCalculator {

    fun build(
        clubId: Long,
        players: List<MatchPlayer>,
        reputation: Int
    ): TeamStrength {
        val outfield = players.filter { !it.isGoalkeeper }
        val defenders = players.filter { it.slot.isDefensive }
        val midfielders = players.filter { !it.slot.isDefensive && !it.slot.isAttacking && !it.isGoalkeeper }
        val attackers = players.filter { it.slot.isAttacking }
        val keepers = players.filter { it.isGoalkeeper }

        fun avg(list: List<MatchPlayer>, selector: (MatchPlayer) -> Double): Double =
            if (list.isEmpty()) 0.0 else list.sumOf { selector(it) } / list.size

        // Weight each unit by how many players the formation dedicates to it, so a
        // five-man defence really does defend better than a front three.
        val attack = unitRating(attackers, midfielders) { it.attackWeight } *
            (0.72 + attackers.size * 0.07)
        val midfield = unitRating(midfielders, attackers) { (it.attackWeight + it.defensiveWeight) / 2.0 } *
            (0.80 + midfielders.size * 0.05)
        val defence = unitRating(defenders, midfielders) { it.defensiveWeight } *
            (0.74 + defenders.size * 0.045)
        val goalkeeping = if (keepers.isEmpty()) 45.0 else avg(keepers) { it.effectiveAbility }

        val avgRating = if (outfield.isEmpty()) 0.0 else avg(outfield) { it.effectiveAbility }

        val reputationNudge = (reputation - 70) * 0.035

        return TeamStrength(
            clubId = clubId,
            overall = (attack * 0.34 + midfield * 0.28 + defence * 0.30 + goalkeeping * 0.08) + reputationNudge,
            attack = attack + reputationNudge,
            midfield = midfield + reputationNudge,
            defence = defence + reputationNudge,
            goalkeeping = goalkeeping,
            averageRating = avgRating
        )
    }

    private fun unitRating(
        primary: List<MatchPlayer>,
        fallback: List<MatchPlayer>,
        selector: (MatchPlayer) -> Double
    ): Double {
        val source = primary.ifEmpty { fallback }
        if (source.isEmpty()) return 48.0
        return source.sumOf { selector(it) } / source.size
    }

    /**
     * Converts a squad + formation into [MatchPlayer]s, applying out-of-position,
     * fitness and confidence modifiers.
     */
    fun toMatchPlayers(
        squadById: Map<Long, Player>,
        selection: com.footymanager.simulator.domain.model.TeamSelection,
        formation: com.footymanager.simulator.domain.model.Formation,
        instructions: Map<Long, com.footymanager.simulator.domain.model.IndividualInstruction> = emptyMap()
    ): List<MatchPlayer> {
        val result = mutableListOf<MatchPlayer>()
        for (slot in selection.startingXi) {
            val player = squadById[slot.playerId] ?: continue
            val role = formation.roles.getOrNull(slot.slotIndex) ?: continue
            val suitability = PositionSuitability.factor(player.position, role.naturalPosition)
            val effective = player.overall * suitability
            // A keeper played outfield, or an outfielder in goal, is heavily punished.
            val emergencyPenalty = if ((role == SlotRole.GK) != (player.position == Position.GK)) 0.72 else 1.0

            result += MatchPlayer(
                player = player,
                slot = role,
                effectiveAbility = effective * emergencyPenalty,
                conditionFactor = conditionFactor(player.fitness),
                confidenceFactor = confidenceFactor(player.form, player.moraleScore),
                instruction = instructions[player.id]
                    ?: com.footymanager.simulator.domain.model.IndividualInstruction.DEFAULT
            )
        }
        return result
    }

    /** Fit players perform at full ability; exhausted players are badly hampered. */
    fun conditionFactor(fitness: Int): Double {
        val f = fitness.coerceIn(0, 100) / 100.0
        return 0.55 + 0.45 * f.pow(0.85)
    }

    fun confidenceFactor(form: Double, moraleScore: Double): Double {
        val formPart = (form - 5.0) * 0.022
        val moralePart = (moraleScore - 3.0) * 0.030
        return (1.0 + formPart + moralePart).coerceIn(0.84, 1.16)
    }
}
