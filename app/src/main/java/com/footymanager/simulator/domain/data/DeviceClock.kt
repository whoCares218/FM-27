package com.footymanager.simulator.domain.data

import android.os.SystemClock
import com.footymanager.simulator.domain.model.RewardClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads the device's two clocks into a [RewardClock].
 *
 * The local day is derived with [ZoneId.systemDefault] so the daily reward resets
 * at the user's own midnight, and the monotonic [SystemClock.elapsedRealtime]
 * gives the tamper guard a signal that a manual clock change cannot forge.
 */
object DeviceClock {

    fun now(): RewardClock {
        val instant = Instant.ofEpochMilli(System.currentTimeMillis())
        val zone = ZoneId.systemDefault()
        return RewardClock(
            localDay = LocalDate.ofInstant(instant, zone).toEpochDay(),
            utcDay = LocalDate.ofInstant(instant, java.time.ZoneOffset.UTC).toEpochDay(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            wallClockMs = instant.toEpochMilli()
        )
    }
}
