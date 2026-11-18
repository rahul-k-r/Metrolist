package com.metrolist.shared.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * What Device Connect carries for one song: the user's own lyrics, offsets and an explicit provider pick.
 * Fetched lyrics are not synced; a pick carries its text so the other device needs no refetch.
 */
@Serializable
data class LyricsSyncItem(
    val songId: String,
    val updatedAt: Long,
    val userLyrics: String? = null,
    val offsetMs: Int = 0,
    /** Per-provider offsets for this song, keyed by provider name. */
    val providerOffsets: Map<String, Int> = emptyMap(),
    val provider: String? = null,
    val providerLyrics: String? = null,
)

internal const val MAX_LYRICS_SYNC_SONGS = 5000
private const val MAX_FUTURE_SKEW_MS = 5L * 60 * 1000
private const val MAX_ID_LENGTH = 2048

/**
 * Whether a remote change should replace the local one. Ties keep local. A stamp far in the future is
 * rejected rather than clamped, or a peer could pin a song's lyrics by resending it forever.
 */
internal fun shouldApplyRemoteLyrics(
    local: Long,
    remote: Long,
    now: Long,
): Boolean = remote in 1..(now + MAX_FUTURE_SKEW_MS) && remote > local

private const val MAX_LYRICS_CHARS = 2_000_000

internal fun LyricsSyncItem.isAcceptable(): Boolean =
    songId.isNotBlank() && songId.length <= MAX_ID_LENGTH &&
        (userLyrics?.length ?: 0) <= MAX_LYRICS_CHARS && (providerLyrics?.length ?: 0) <= MAX_LYRICS_CHARS &&
        providerOffsets.size <= 64

/** Last-change time per song, kept as one JSON setting so it follows backups and needs no database migration. */
class LyricsSyncStamps(
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Long.serializer())

    fun all(): Map<String, Long> =
        read().takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun stamp(songId: String): Long = all()[songId] ?: 0L

    /** Records a local change to [songId]. */
    fun touch(songId: String) = set(songId, now())

    /** Records a change that arrived from another device, keeping its time so both sides converge. */
    fun accept(
        songId: String,
        remoteUpdatedAt: Long,
    ) = set(songId, remoteUpdatedAt)

    fun shouldApply(
        songId: String,
        remoteUpdatedAt: Long,
    ): Boolean = shouldApplyRemoteLyrics(stamp(songId), remoteUpdatedAt, now())

    private fun set(
        songId: String,
        value: Long,
    ) {
        val map = all().toMutableMap()
        if (songId !in map && map.size >= MAX_LYRICS_SYNC_SONGS) {
            map.minByOrNull { it.value }?.let { map.remove(it.key) }
        }
        map[songId] = value
        write(json.encodeToString(serializer, map))
    }
}
