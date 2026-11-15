/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Playback speed and pitch remembered for individual songs, stored as JSON in settings.
 */

package com.metrolist.shared.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class SongTempoPitchValue(
    val speed: Float,
    val pitch: Float,
)

/**
 * A saved value replaces the global speed/pitch while that song plays; absence means "use the global value",
 * so an explicit 1.0x still counts as an override.
 */
object SongTempoPitch {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), SongTempoPitchValue.serializer())

    fun parse(raw: String): Map<String, SongTempoPitchValue> =
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())

    fun get(
        raw: String,
        songId: String,
    ): SongTempoPitchValue? = parse(raw)[songId]

    fun set(
        raw: String,
        songId: String,
        value: SongTempoPitchValue,
    ): String = json.encodeToString(serializer, parse(raw) + (songId to value))

    fun remove(
        raw: String,
        songId: String,
    ): String {
        val map = parse(raw) - songId
        return if (map.isEmpty()) "" else json.encodeToString(serializer, map)
    }

    /** What should play for [songId]: its saved value, else the supplied global values. */
    fun effective(
        raw: String,
        songId: String?,
        globalSpeed: Float,
        globalPitch: Float,
    ): SongTempoPitchValue = songId?.let { get(raw, it) } ?: SongTempoPitchValue(globalSpeed, globalPitch)
}
