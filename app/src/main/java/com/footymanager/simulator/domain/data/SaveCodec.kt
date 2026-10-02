package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.Career
import kotlinx.serialization.json.Json

/**
 * Pure serialization for a career save.
 *
 * Kept free of any Android dependency so the save format can be unit tested
 * directly, and so the storage mechanism (currently DataStore) can be swapped
 * without touching the format.
 */
object SaveCodec {

    /**
     * `ignoreUnknownKeys` lets a save written by a newer version load in an older
     * build, and `encodeDefaults` guarantees fields are written explicitly so a
     * renamed or reordered property cannot silently corrupt a save.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
        allowStructuredMapKeys = true
    }

    fun encode(career: Career): String = json.encodeToString(Career.serializer(), career)

    /** Returns null when the payload is unreadable rather than throwing. */
    fun decode(payload: String): Career? = try {
        json.decodeFromString(Career.serializer(), payload)
    } catch (t: Throwable) {
        null
    }
}
