/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Lyrics timing offsets keyed by song and provider, stored as JSON in settings.
 */

package com.metrolist.shared.settings

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A song's lyrics can be late from one provider and fine from another, so an offset is
 * remembered per `songId|provider`. When there is no provider-specific value the caller
 * falls back to the per-song offset already stored with the cached lyrics.
 */
object ProviderLyricsOffsets {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    fun key(
        songId: String,
        provider: String,
    ): String = "$songId|$provider"

    fun parse(raw: String): Map<String, Int> =
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())

    fun get(
        raw: String,
        songId: String,
        provider: String,
    ): Int? = parse(raw)[key(songId, provider)]

    /** Returns the new JSON. Zero is kept: it is an explicit "no offset" that must beat the per-song fallback. */
    fun set(
        raw: String,
        songId: String,
        provider: String,
        offsetMs: Int,
    ): String {
        val map = parse(raw).toMutableMap()
        map[key(songId, provider)] = offsetMs
        return json.encodeToString(serializer, map)
    }
}
