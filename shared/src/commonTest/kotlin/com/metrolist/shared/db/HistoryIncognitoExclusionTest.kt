package com.metrolist.shared.db

import com.metrolist.shared.settings.BlockedArtist
import com.metrolist.shared.settings.MarkKind
import com.metrolist.shared.settings.MarkedItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryIncognitoExclusionTest {
    private val marks =
        listOf(
            MarkedItem(MarkKind.SONG, "song1", "One"),
            MarkedItem(MarkKind.ARTIST, "artist1", "Artist"),
            MarkedItem(MarkKind.SONG, "gone", "Unmarked", removed = true),
        )

    @Test
    fun markedSongsAndArtistsAreExcludedWhenIncognitoIsEnabled() {
        val result = historyExcludedContent(emptyList(), setOf("blocked"), incognitoEnabled = true, incognitoMarks = marks)
        assertEquals(setOf("blocked", "song1"), result.songIds)
        assertEquals(listOf("artist1"), result.artists.map { it.id })
    }

    @Test
    fun marksAreInertWhenIncognitoIsDisabled() {
        val result = historyExcludedContent(emptyList(), emptySet(), incognitoEnabled = false, incognitoMarks = marks)
        assertEquals(emptySet(), result.songIds)
        assertEquals(emptyList(), result.artists)
    }

    @Test
    fun anArtistBothBlockedAndMarkedIsListedOnce() {
        val result =
            historyExcludedContent(listOf(BlockedArtist("artist1", "Artist")), emptySet(), incognitoEnabled = true, incognitoMarks = marks)
        assertEquals(listOf("artist1"), result.artists.map { it.id })
    }
}
