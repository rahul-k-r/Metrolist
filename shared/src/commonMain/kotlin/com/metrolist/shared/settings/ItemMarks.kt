package com.metrolist.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.shared.model.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

enum class MarkKind { SONG, ARTIST }

/**
 * [updatedAt] and [removed] let two devices merge their marks: the newest change to an item wins, and an
 * unmark is kept as a tombstone so a device that still has the mark cannot bring it back.
 */
@Serializable
data class MarkedItem(
    val kind: MarkKind,
    val id: String,
    val title: String,
    val thumbnail: String? = null,
    val updatedAt: Long = 0L,
    val removed: Boolean = false,
)

/** Newest change per item wins; on a tie the local entry is kept. */
fun mergeMarks(
    local: List<MarkedItem>,
    remote: List<MarkedItem>,
): List<MarkedItem> {
    val merged = LinkedHashMap<Pair<MarkKind, String>, MarkedItem>()
    local.forEach { merged[it.kind to it.id] = it }
    remote.forEach { incoming ->
        val key = incoming.kind to incoming.id
        val existing = merged[key]
        if (existing == null || incoming.updatedAt > existing.updatedAt) merged[key] = incoming
    }
    return merged.values.toList()
}

/** A small set of user-marked items persisted as one JSON setting, so marks follow backups and need no DB migration. */
open class MarkRegistry(
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val markArtists: Boolean,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private class Parsed(
        val source: String,
        val all: List<MarkedItem>,
    ) {
        val items = all.filterNot { it.removed }
        val songIds = items.filter { it.kind == MarkKind.SONG }.mapTo(mutableSetOf()) { it.id }
        val artistIds = items.filter { it.kind == MarkKind.ARTIST }.mapTo(mutableSetOf()) { it.id }
    }

    private var cached: Parsed? = null

    private fun parsed(): Parsed {
        val source = read()
        cached?.takeIf { it.source == source }?.let { return it }
        val items =
            try {
                json.decodeFromString<List<MarkedItem>>(source.ifBlank { "[]" })
            } catch (_: Exception) {
                emptyList()
            }
        return Parsed(source, items).also { cached = it }
    }

    fun items(): List<MarkedItem> = parsed().items

    /** Everything that is synced, including tombstones. */
    fun entries(): List<MarkedItem> = parsed().all

    /** Folds another device's entries in; returns true when anything here changed. */
    fun mergeFrom(remote: List<MarkedItem>): Boolean {
        val current = entries()
        val merged = mergeMarks(current, remote)
        if (merged == current) return false
        save(merged)
        return true
    }

    fun isMarked(
        kind: MarkKind,
        id: String?,
    ): Boolean {
        if (id.isNullOrBlank()) return false
        val parsed = parsed()
        return id in if (kind == MarkKind.SONG) parsed.songIds else parsed.artistIds
    }

    /** A song matches by its own id and, where this registry marks artists, by any credited artist. */
    fun matches(song: SongItem): Boolean {
        val parsed = parsed()
        if (song.id in parsed.songIds) return true
        return markArtists && song.artists.any { artist -> artist.id?.let { it in parsed.artistIds } == true }
    }

    fun mark(item: MarkedItem) {
        if (item.id.isBlank()) return
        val stamped = item.copy(updatedAt = now(), removed = false)
        save(listOf(stamped) + entries().filterNot { it.kind == item.kind && it.id == item.id })
    }

    fun unmark(
        kind: MarkKind,
        id: String,
    ) {
        val existing = entries().firstOrNull { it.kind == kind && it.id == id && !it.removed } ?: return
        save(
            listOf(existing.copy(updatedAt = now(), removed = true)) +
                entries().filterNot { it.kind == kind && it.id == id },
        )
    }

    fun toggle(item: MarkedItem) {
        if (isMarked(item.kind, item.id)) unmark(item.kind, item.id) else mark(item)
    }

    private fun save(items: List<MarkedItem>) {
        val cutoff = now() - TOMBSTONE_TTL_MS
        write(json.encodeToString(items.filterNot { it.removed && it.updatedAt < cutoff }))
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val TOMBSTONE_TTL_MS = 90L * 24 * 60 * 60 * 1000
    }
}

/** Songs and artists whose plays are hidden from history, scrobbles and YouTube. Inert unless incognito mode is enabled. */
object IncognitoMarks : MarkRegistry(
    read = { Settings.shared.incognitoMarksJson },
    write = { Settings.shared.incognitoMarksJson = it },
    markArtists = true,
)

/** Songs that queues built by the app leave out; adding a song to the queue by hand still includes it. */
object SkipMarks : MarkRegistry(
    read = { Settings.shared.skipMarksJson },
    write = { Settings.shared.skipMarksJson = it },
    markArtists = false,
) {
    class Filtered(
        val songs: List<SongItem>,
        val startIndex: Int,
    )

    /** Drops marked songs from a queue the app is building, keeping the song the user explicitly chose to start from. */
    fun filterForQueue(
        songs: List<SongItem>,
        startIndex: Int = -1,
    ): Filtered {
        if (!Settings.shared.skipMarkingEnabled || items().isEmpty()) return Filtered(songs, startIndex)
        val kept = songs.filterIndexed { index, song -> index == startIndex || !matches(song) }
        val newStart = if (startIndex < 0) startIndex else songs.take(startIndex).count { !matches(it) }
        return Filtered(kept, newStart)
    }
}

/** The song playing right now, so sinks that only know "is history paused" can honour per-song marks. */
object IncognitoPlayback {
    private var song by mutableStateOf<SongItem?>(null)

    fun onCurrentSongChanged(current: SongItem?) {
        song = current
    }

    fun hidesCurrent(settings: Settings): Boolean = settings.incognitoModeEnabled && song?.let(IncognitoMarks::matches) == true
}
