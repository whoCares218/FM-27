package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/**
 * A single observation of the device's notion of time, captured when the
 * rewarded-ad allowance is checked.
 *
 * Two independent signals are kept because neither is trustworthy alone:
 *  - [wallClockMs] is the user-settable clock (`System.currentTimeMillis`).
 *  - [elapsedRealtimeMs] is monotonic since boot (`SystemClock.elapsedRealtime`)
 *    and cannot be moved by the user, but it resets on reboot.
 *
 * [localDay] is the calendar day in the device's own time zone, so the daily
 * reset lands on local midnight rather than a UTC boundary, and [utcDay] is kept
 * only so a legacy save (written against the old UTC scheme) can be recognised.
 */
@Serializable
data class RewardClock(
    val localDay: Long,
    val utcDay: Long,
    val elapsedRealtimeMs: Long,
    val wallClockMs: Long
)

/**
 * Decides whether the daily rewarded-ad allowance may be refreshed.
 *
 * The naive rule — "is it a new UTC day?" — is both wrong for users (the reset
 * happens at the wrong moment) and trivially exploitable (move the clock forward
 * and claim again). This guard requires a new *local* day **and** evidence that
 * real time has actually passed, using the monotonic clock when it is available
 * and falling back to the wall clock only across a reboot.
 */
object RewardClockGuard {

    /** Minimum real time between two allowance refreshes (a little under a day). */
    const val MIN_REFRESH_INTERVAL_MS = 20L * 60L * 60L * 1000L

    /**
     * A forward wall-clock jump larger than this is not a plausible day change
     * (it implies more than a day and a half passed while the app was open), so
     * it is treated as a suspicious manual clock change.
     */
    const val MAX_PLAUSIBLE_WALL_JUMP_MS = 36L * 60L * 60L * 1000L

    /** The numbering scheme written by the current build. */
    const val SCHEME_LOCAL = 1

    /** The old UTC-epoch-day scheme (and the default on a fresh/legacy state). */
    const val SCHEME_LEGACY = 0

    /**
     * True when [now] justifies refreshing the allowance given the [last]
     * observation. A null [last] (first run, or a migrated legacy save) always
     * allows a single refresh to establish a baseline.
     */
    fun canRefresh(last: RewardClock?, now: RewardClock): Boolean {
        if (last == null) return true
        // A new allowance only ever starts on a strictly later local day, so a
        // backward clock change (or a replay within the same day) is refused.
        if (now.localDay <= last.localDay) return false

        val elapsed = now.elapsedRealtimeMs - last.elapsedRealtimeMs
        val wall = now.wallClockMs - last.wallClockMs

        return if (elapsed >= 0) {
            // Monotonic clock is intact: demand real elapsed time. This blocks the
            // "set the clock forward" exploit within a single boot session.
            elapsed >= MIN_REFRESH_INTERVAL_MS
        } else {
            // The monotonic clock went backwards, which means the device rebooted.
            // Trust the wall clock, but only for a plausible day-sized jump.
            wall in 1L..MAX_PLAUSIBLE_WALL_JUMP_MS
        }
    }
}
