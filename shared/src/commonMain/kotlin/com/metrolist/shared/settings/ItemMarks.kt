package com.metrolist.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.shared.model.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class MarkKind { SONG, ARTIST }

@Serializable
data class MarkedItem(
    val kind: MarkKind,
    val id: String,
    val title: String,
    val thumbnail: String? = null,
)

/** A small set of user-marked items persisted as one JSON setting, so marks follow backups and need no DB migration. */
open class MarkRegistry(
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val markArtists: Boolean,
) {
    private class Parsed(
        val source: String,
        val items: List<MarkedItem>,
    ) {
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
        save(listOf(item) + items().filterNot { it.kind == item.kind && it.id == item.id })
    }

    fun unmark(
        kind: MarkKind,
        id: String,
    ) = save(items().filterNot { it.kind == kind && it.id == id })

    fun toggle(item: MarkedItem) {
        if (isMarked(item.kind, item.id)) unmark(item.kind, item.id) else mark(item)
    }

    private fun save(items: List<MarkedItem>) = write(json.encodeToString(items))

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
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
