package com.metrolist.shared.db

import com.metrolist.shared.settings.BlockedArtist
import com.metrolist.shared.settings.MarkKind
import com.metrolist.shared.settings.MarkedItem

/** Songs and artists history statistics leave out: the user's blocklist plus anything marked incognito. */
internal class HistoryExcludedContent(
    val artists: List<BlockedArtist>,
    val songIds: Set<String>,
)

/**
 * Marked items stay out of statistics even for plays that were recorded before the mark was added.
 * Marks are inert unless incognito mode is enabled, matching every other incognito sink.
 */
internal fun historyExcludedContent(
    blockedArtists: List<BlockedArtist>,
    blockedSongIds: Set<String>,
    incognitoEnabled: Boolean,
    incognitoMarks: List<MarkedItem>,
): HistoryExcludedContent {
    if (!incognitoEnabled || incognitoMarks.isEmpty()) return HistoryExcludedContent(blockedArtists, blockedSongIds)
    val marked = incognitoMarks.filterNot { it.removed }
    val knownArtists = blockedArtists.mapTo(mutableSetOf()) { it.id }
    val artists =
        blockedArtists +
            marked
                .filter { it.kind == MarkKind.ARTIST && it.id !in knownArtists }
                .distinctBy { it.id }
                .map { BlockedArtist(it.id, it.title, it.thumbnail) }
    val songIds = blockedSongIds + marked.filter { it.kind == MarkKind.SONG }.map { it.id }
    return HistoryExcludedContent(artists, songIds)
}
