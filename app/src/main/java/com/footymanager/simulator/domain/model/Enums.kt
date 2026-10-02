package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/** Playing position. Order matters: it drives natural squad sorting. */
@Serializable
enum class Position(val short: String, val longName: String, val category: PositionCategory) {
    GK("GK", "Goalkeeper", PositionCategory.GOALKEEPER),
    RB("RB", "Right Back", PositionCategory.DEFENDER),
    LB("LB", "Left Back", PositionCategory.DEFENDER),
    CB("CB", "Centre Back", PositionCategory.DEFENDER),
    CDM("CDM", "Defensive Midfielder", PositionCategory.MIDFIELDER),
    CM("CM", "Central Midfielder", PositionCategory.MIDFIELDER),
    CAM("CAM", "Attacking Midfielder", PositionCategory.MIDFIELDER),
    RW("RW", "Right Winger", PositionCategory.FORWARD),
    LW("LW", "Left Winger", PositionCategory.FORWARD),
    ST("ST", "Striker", PositionCategory.FORWARD);

    val isGoalkeeper: Boolean get() = this == GK
    val isDefender: Boolean get() = category == PositionCategory.DEFENDER
    val isMidfielder: Boolean get() = category == PositionCategory.MIDFIELDER
    val isForward: Boolean get() = category == PositionCategory.FORWARD

    companion object {
        /** Sort weight so squads list GK -> DEF -> MID -> ATT. */
        fun sortOrder(p: Position): Int = p.ordinal

        val outfield: List<Position> = entries.filter { it != GK }
    }
}

@Serializable
enum class PositionCategory { GOALKEEPER, DEFENDER, MIDFIELDER, FORWARD }

@Serializable
enum class PreferredFoot(val label: String) {
    RIGHT("Right"), LEFT("Left"), BOTH("Both")
}

/**
 * How well a player copes in a slot that is not their natural position.
 * 1.0 = natural, lower = increasingly uncomfortable.
 */
object PositionSuitability {

    private val related: Map<Position, Set<Position>> = mapOf(
        Position.GK to emptySet(),
        Position.RB to setOf(Position.LB, Position.CB, Position.RW),
        Position.LB to setOf(Position.RB, Position.CB, Position.LW),
        Position.CB to setOf(Position.RB, Position.LB, Position.CDM),
        Position.CDM to setOf(Position.CM, Position.CB),
        Position.CM to setOf(Position.CDM, Position.CAM),
        Position.CAM to setOf(Position.CM, Position.ST, Position.LW, Position.RW),
        Position.RW to setOf(Position.LW, Position.ST, Position.CAM, Position.RB),
        Position.LW to setOf(Position.RW, Position.ST, Position.CAM, Position.LB),
        Position.ST to setOf(Position.CAM, Position.RW, Position.LW)
    )

    /** Multiplier applied to a player's contribution when fielded out of position. */
    fun factor(natural: Position, slot: Position): Double {
        if (natural == slot) return 1.0
        // A goalkeeper can never play outfield (and vice versa) without a heavy penalty.
        if (natural.isGoalkeeper != slot.isGoalkeeper) return 0.30
        if (related[natural]?.contains(slot) == true) return 0.86
        if (natural.category == slot.category) return 0.80
        return 0.68
    }
}
