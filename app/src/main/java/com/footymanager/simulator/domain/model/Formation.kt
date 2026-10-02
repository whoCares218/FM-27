package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/** The 11 slots a formation defines. Index 0 is always the goalkeeper. */
@Serializable
enum class SlotRole(val short: String, val longName: String) {
    GK("GK", "Goalkeeper"),
    LB("LB", "Left Back"),
    LCB("CB", "Centre Back"),
    CB("CB", "Centre Back"),
    RCB("CB", "Centre Back"),
    RB("RB", "Right Back"),
    LWB("LWB", "Left Wing Back"),
    RWB("RWB", "Right Wing Back"),
    CDM("CDM", "Defensive Midfield"),
    LCM("CM", "Central Midfield"),
    RCM("CM", "Central Midfield"),
    CM("CM", "Central Midfield"),
    CAM("CAM", "Attacking Midfield"),
    LM("LM", "Left Midfield"),
    RM("RM", "Right Midfield"),
    LW("LW", "Left Winger"),
    RW("RW", "Right Winger"),
    LST("ST", "Striker"),
    RST("ST", "Striker"),
    ST("ST", "Striker");

    /** The natural [Position] a player should hold to fill this slot. */
    val naturalPosition: Position
        get() = when (this) {
            GK -> Position.GK
            LB, LWB -> Position.LB
            LCB, CB, RCB -> Position.CB
            RB, RWB -> Position.RB
            CDM -> Position.CDM
            LCM, RCM, CM -> Position.CM
            CAM -> Position.CAM
            LM, LW -> Position.LW
            RM, RW -> Position.RW
            LST, RST, ST -> Position.ST
        }

    val isDefensive: Boolean get() = this in setOf(LB, LCB, CB, RCB, RB, LWB, RWB, CDM)
    val isAttacking: Boolean get() = this in setOf(CAM, LW, RW, LST, RST, ST)
}

/**
 * A formation is a list of [SlotRole] plus a normalized (x, y) pitch coordinate for
 * each slot, where x/y are in 0..1 with y = 0 at the team's own goal line.
 * Coordinates drive both the tactical pitch view and the simulation's
 * positional structure.
 */
@Serializable
data class Formation(
    val id: String,
    val name: String,
    val roles: List<SlotRole>,
    val coordinates: List<PitchPoint>,
    val description: String
) {
    val defenderCount: Int get() = roles.count { it.isDefensive }
    val attackerCount: Int get() = roles.count { it.isAttacking }
    val midfielderCount: Int get() = 10 - defenderCount - attackerCount

    /**
     * Groups slot indices into horizontal lines for the pitch view, ordered from
     * the attacking line (top of the screen) down to the goalkeeper. Rows are
     * derived from the slot coordinates rather than hard-coded per formation, so
     * new formations render correctly without extra work.
     */
    fun pitchLines(): List<List<Int>> {
        val indexed = roles.indices
            .map { it to coordinates.getOrElse(it) { PitchPoint(0.5f, 0.5f) } }
            .sortedByDescending { it.second.y }

        val lines = mutableListOf<MutableList<Int>>()
        var currentY = Float.NaN
        for ((index, point) in indexed) {
            if (lines.isEmpty() || kotlin.math.abs(currentY - point.y) > 0.10f) {
                lines += mutableListOf(index)
                currentY = point.y
            } else {
                lines.last() += index
            }
        }
        return lines.map { line -> line.sortedBy { coordinates[it].x } }
    }

    companion object {
        private fun pt(x: Float, y: Float) = PitchPoint(x, y)

        val F433 = Formation(
            id = "4-3-3",
            name = "4-3-3",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LB, SlotRole.LCB, SlotRole.RCB, SlotRole.RB,
                SlotRole.CDM, SlotRole.LCM, SlotRole.RCM,
                SlotRole.LW, SlotRole.ST, SlotRole.RW
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.14f, 0.26f), pt(0.36f, 0.20f), pt(0.64f, 0.20f), pt(0.86f, 0.26f),
                pt(0.50f, 0.42f), pt(0.30f, 0.54f), pt(0.70f, 0.54f),
                pt(0.14f, 0.76f), pt(0.50f, 0.86f), pt(0.86f, 0.76f)
            ),
            description = "Balanced possession shape with a holding midfielder and two wide forwards."
        )

        val F4231 = Formation(
            id = "4-2-3-1",
            name = "4-2-3-1",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LB, SlotRole.LCB, SlotRole.RCB, SlotRole.RB,
                SlotRole.CDM, SlotRole.CM,
                SlotRole.LW, SlotRole.CAM, SlotRole.RW,
                SlotRole.ST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.14f, 0.26f), pt(0.36f, 0.20f), pt(0.64f, 0.20f), pt(0.86f, 0.26f),
                pt(0.38f, 0.44f), pt(0.62f, 0.44f),
                pt(0.16f, 0.70f), pt(0.50f, 0.66f), pt(0.84f, 0.70f),
                pt(0.50f, 0.88f)
            ),
            description = "Modern default: double pivot protects the back four behind a creative ten."
        )

        val F442 = Formation(
            id = "4-4-2",
            name = "4-4-2",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LB, SlotRole.LCB, SlotRole.RCB, SlotRole.RB,
                SlotRole.LM, SlotRole.LCM, SlotRole.RCM, SlotRole.RM,
                SlotRole.LST, SlotRole.RST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.14f, 0.26f), pt(0.36f, 0.20f), pt(0.64f, 0.20f), pt(0.86f, 0.26f),
                pt(0.12f, 0.52f), pt(0.38f, 0.48f), pt(0.62f, 0.48f), pt(0.88f, 0.52f),
                pt(0.38f, 0.82f), pt(0.62f, 0.82f)
            ),
            description = "Classic two banks of four with a strike partnership."
        )

        val F352 = Formation(
            id = "3-5-2",
            name = "3-5-2",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LCB, SlotRole.CB, SlotRole.RCB,
                SlotRole.LWB, SlotRole.CDM, SlotRole.CM, SlotRole.CAM, SlotRole.RWB,
                SlotRole.LST, SlotRole.RST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.28f, 0.20f), pt(0.50f, 0.17f), pt(0.72f, 0.20f),
                pt(0.08f, 0.50f), pt(0.38f, 0.44f), pt(0.62f, 0.46f), pt(0.50f, 0.62f), pt(0.92f, 0.50f),
                pt(0.38f, 0.82f), pt(0.62f, 0.82f)
            ),
            description = "Wing backs provide the width; a three-man midfield overloads the centre."
        )

        val F343 = Formation(
            id = "3-4-3",
            name = "3-4-3",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LCB, SlotRole.CB, SlotRole.RCB,
                SlotRole.LWB, SlotRole.LCM, SlotRole.RCM, SlotRole.RWB,
                SlotRole.LW, SlotRole.ST, SlotRole.RW
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.28f, 0.20f), pt(0.50f, 0.17f), pt(0.72f, 0.20f),
                pt(0.08f, 0.52f), pt(0.40f, 0.48f), pt(0.60f, 0.48f), pt(0.92f, 0.52f),
                pt(0.16f, 0.78f), pt(0.50f, 0.88f), pt(0.84f, 0.78f)
            ),
            description = "Aggressive front three with wing backs stretching the pitch."
        )

        val F532 = Formation(
            id = "5-3-2",
            name = "5-3-2",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LWB, SlotRole.LCB, SlotRole.CB, SlotRole.RCB, SlotRole.RWB,
                SlotRole.CDM, SlotRole.LCM, SlotRole.RCM,
                SlotRole.LST, SlotRole.RST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.08f, 0.34f), pt(0.30f, 0.20f), pt(0.50f, 0.17f), pt(0.70f, 0.20f), pt(0.92f, 0.34f),
                pt(0.50f, 0.48f), pt(0.34f, 0.58f), pt(0.66f, 0.58f),
                pt(0.38f, 0.82f), pt(0.62f, 0.82f)
            ),
            description = "Low block with five defenders: hard to break down, reliant on the counter."
        )

        val F4141 = Formation(
            id = "4-1-4-1",
            name = "4-1-4-1",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LB, SlotRole.LCB, SlotRole.RCB, SlotRole.RB,
                SlotRole.CDM,
                SlotRole.LM, SlotRole.LCM, SlotRole.RCM, SlotRole.RM,
                SlotRole.ST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.14f, 0.26f), pt(0.36f, 0.20f), pt(0.64f, 0.20f), pt(0.86f, 0.26f),
                pt(0.50f, 0.42f),
                pt(0.12f, 0.62f), pt(0.38f, 0.58f), pt(0.62f, 0.58f), pt(0.88f, 0.62f),
                pt(0.50f, 0.86f)
            ),
            description = "A lone striker with a bank of four and a screening midfielder."
        )

        val F4411 = Formation(
            id = "4-4-1-1",
            name = "4-4-1-1",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LB, SlotRole.LCB, SlotRole.RCB, SlotRole.RB,
                SlotRole.LM, SlotRole.LCM, SlotRole.RCM, SlotRole.RM,
                SlotRole.CAM, SlotRole.ST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.14f, 0.26f), pt(0.36f, 0.20f), pt(0.64f, 0.20f), pt(0.86f, 0.26f),
                pt(0.12f, 0.52f), pt(0.38f, 0.48f), pt(0.62f, 0.48f), pt(0.88f, 0.52f),
                pt(0.50f, 0.70f), pt(0.50f, 0.88f)
            ),
            description = "Two banks of four behind a withdrawn forward supporting the striker."
        )

        val F4222 = Formation(
            id = "4-2-2-2",
            name = "4-2-2-2",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LB, SlotRole.LCB, SlotRole.RCB, SlotRole.RB,
                SlotRole.CDM, SlotRole.CM,
                SlotRole.LW, SlotRole.RW,
                SlotRole.LST, SlotRole.RST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.14f, 0.26f), pt(0.36f, 0.20f), pt(0.64f, 0.20f), pt(0.86f, 0.26f),
                pt(0.38f, 0.44f), pt(0.62f, 0.44f),
                pt(0.26f, 0.66f), pt(0.74f, 0.66f),
                pt(0.38f, 0.86f), pt(0.62f, 0.86f)
            ),
            description = "The 'magic square': a double pivot, two narrow creators and two strikers."
        )

        val F541 = Formation(
            id = "5-4-1",
            name = "5-4-1",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LWB, SlotRole.LCB, SlotRole.CB, SlotRole.RCB, SlotRole.RWB,
                SlotRole.LM, SlotRole.LCM, SlotRole.RCM, SlotRole.RM,
                SlotRole.ST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.08f, 0.34f), pt(0.30f, 0.20f), pt(0.50f, 0.17f), pt(0.70f, 0.20f), pt(0.92f, 0.34f),
                pt(0.14f, 0.56f), pt(0.38f, 0.52f), pt(0.62f, 0.52f), pt(0.86f, 0.56f),
                pt(0.50f, 0.84f)
            ),
            description = "Ultra-defensive five across the back with a single outlet up front."
        )

        val F4312 = Formation(
            id = "4-3-1-2",
            name = "4-3-1-2",
            roles = listOf(
                SlotRole.GK,
                SlotRole.LB, SlotRole.LCB, SlotRole.RCB, SlotRole.RB,
                SlotRole.CDM, SlotRole.LCM, SlotRole.RCM,
                SlotRole.CAM,
                SlotRole.LST, SlotRole.RST
            ),
            coordinates = listOf(
                pt(0.50f, 0.05f),
                pt(0.14f, 0.26f), pt(0.36f, 0.20f), pt(0.64f, 0.20f), pt(0.86f, 0.26f),
                pt(0.50f, 0.42f), pt(0.30f, 0.54f), pt(0.70f, 0.54f),
                pt(0.50f, 0.68f),
                pt(0.38f, 0.86f), pt(0.62f, 0.86f)
            ),
            description = "Narrow midfield diamond behind a front two; width comes from the full backs."
        )

        val all: List<Formation> = listOf(
            F433, F4231, F442, F352, F343, F532, F4141, F4411, F4222, F541, F4312
        )

        fun byId(id: String): Formation = all.firstOrNull { it.id == id } ?: F4231
    }
}

/** Normalized pitch coordinate: x = 0 (left) .. 1 (right), y = 0 (own goal) .. 1 (opponent goal). */
@Serializable
data class PitchPoint(val x: Float, val y: Float)
