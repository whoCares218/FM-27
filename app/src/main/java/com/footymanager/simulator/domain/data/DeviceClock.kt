package com.footymanager.simulator.domain.data

import android.os.SystemClock
import com.footymanager.simulator.domain.model.RewardClock
import java.util.TimeZone

/**
 * Reads the device's two clocks into a [RewardClock].
 *
 * The local day is derived with the device's own time zone so the daily reward
 * resets at the user's own midnight rather than a UTC boundary, and the monotonic
 * [SystemClock.elapsedRealtime] gives the tamper guard a signal that a manual
 * clock change cannot forge.
 *
 * The calendar day is computed from the epoch-millisecond clock and the zone
 * offset directly rather than via `java.time`, because the app supports API 24
 * and core library desugaring is not enabled.
 */
object DeviceClock {

    private const val MILLIS_PER_DAY = 86_400_000L

    fun now(): RewardClock {
        val wall = System.currentTimeMillis()
        val offset = TimeZone.getDefault().getOffset(wall).toLong()
        return RewardClock(
            localDay = Math.floorDiv(wall + offset, MILLIS_PER_DAY),
            utcDay = Math.floorDiv(wall, MILLIS_PER_DAY),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            wallClockMs = wall
        )
    }
}
