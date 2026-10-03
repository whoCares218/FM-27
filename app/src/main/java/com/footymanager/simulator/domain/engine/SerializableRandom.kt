package com.footymanager.simulator.domain.engine

import kotlin.random.Random

/**
 * A [Random] whose entire state is a single `Long`, so a match's randomness can
 * be serialized and resumed exactly.
 *
 * This is the key to resuming an in-progress match: because every draw advances
 * one internal counter that is saved with the rest of the match state, a match
 * left at minute 30 produces byte-identical future events whether it is
 * continued immediately or after the app has been force-stopped and reopened.
 *
 * The algorithm is SplitMix64 — fast, well-distributed, and trivially
 * serializable (unlike `java.util.Random`, whose state is a multi-word object).
 */
class SerializableRandom(seed: Long) : Random() {

    /** The full generator state. Saved and restored with the match snapshot. */
    var state: Long = seed
        private set

    /** Restores a generator from a previously captured state. */
    constructor(state: Long, fromState: Boolean) : this(state)

    fun restore(savedState: Long) {
        state = savedState
    }

    override fun nextBits(bitCount: Int): Int {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        z = z xor (z ushr 31)
        // Take the high bits; the low bits of SplitMix64 are the weakest.
        return (z ushr (64 - bitCount)).toInt()
    }

    private companion object {
        // Standard SplitMix64 constants, written as unsigned literals because
        // they exceed Long.MAX_VALUE.
        val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        val MIX_1 = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX_2 = 0x94D049BB133111EBuL.toLong()
    }
}
