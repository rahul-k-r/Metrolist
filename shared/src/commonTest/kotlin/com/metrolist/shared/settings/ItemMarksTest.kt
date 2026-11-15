package com.metrolist.shared.settings

import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.MusicQueue
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemMarksTest {
    @BeforeTest
    fun setUp() {
        Settings.replaceSharedForTest(Settings(MapSettings()))
        IncognitoPlayback.onCurrentSongChanged(null)
    }

    @AfterTest
    fun tearDown() {
        IncognitoPlayback.onCurrentSongChanged(null)
        Settings.replaceSharedForTest(null)
    }

    private fun song(
        id: String,
        artistId: String? = "artist-$id",
    ) = SongItem(id, "Song $id", null, listOf(ArtistTiny(artistId, "Artist $id")), null)

    private fun marked(
        kind: MarkKind,
        id: String,
    ) = MarkedItem(kind, id, id)

    @Test
    fun registryMarksTogglesAndSurvivesGarbage() {
        IncognitoMarks.mark(marked(MarkKind.SONG, "a"))
        assertTrue(IncognitoMarks.isMarked(MarkKind.SONG, "a"))
        assertFalse(IncognitoMarks.isMarked(MarkKind.ARTIST, "a"))
        IncognitoMarks.toggle(marked(MarkKind.SONG, "a"))
        assertFalse(IncognitoMarks.isMarked(MarkKind.SONG, "a"))
        Settings.shared.incognitoMarksJson = "not json"
        assertTrue(IncognitoMarks.items().isEmpty())
    }

    @Test
    fun remarkingReplacesInsteadOfDuplicating() {
        IncognitoMarks.mark(MarkedItem(MarkKind.SONG, "a", "Old"))
        IncognitoMarks.mark(MarkedItem(MarkKind.SONG, "a", "New"))
        assertEquals(listOf("New"), IncognitoMarks.items().map { it.title })
    }

    @Test
    fun incognitoMarksMatchBySongOrArtistButSkipMarksOnlyBySong() {
        IncognitoMarks.mark(marked(MarkKind.ARTIST, "artist-x"))
        SkipMarks.mark(marked(MarkKind.ARTIST, "artist-x"))
        assertTrue(IncognitoMarks.matches(song("x")))
        assertFalse(SkipMarks.matches(song("x")))
        assertFalse(IncognitoMarks.matches(song("y")))
        SkipMarks.mark(marked(MarkKind.SONG, "y"))
        assertTrue(SkipMarks.matches(song("y")))
    }

    @Test
    fun marksAreInertUntilIncognitoModeIsEnabled() {
        IncognitoMarks.mark(marked(MarkKind.SONG, "a"))
        assertFalse(Settings.shared.isIncognitoFor(song("a")))
        IncognitoPlayback.onCurrentSongChanged(song("a"))
        assertFalse(Settings.shared.listenHistoryPaused)

        Settings.shared.incognitoModeEnabled = true
        assertTrue(Settings.shared.isIncognitoFor(song("a")))
        assertFalse(Settings.shared.isIncognitoFor(song("b")))
        assertTrue(Settings.shared.listenHistoryPaused)
        assertFalse(Settings.shared.searchHistoryPaused)
    }

    @Test
    fun historyResumesWhenPlaybackMovesToAnUnmarkedSong() {
        Settings.shared.incognitoModeEnabled = true
        IncognitoMarks.mark(marked(MarkKind.SONG, "a"))
        IncognitoPlayback.onCurrentSongChanged(song("a"))
        assertTrue(Settings.shared.hidesCurrentPlayback)
        IncognitoPlayback.onCurrentSongChanged(song("b"))
        assertFalse(Settings.shared.hidesCurrentPlayback)
        IncognitoPlayback.onCurrentSongChanged(null)
        assertFalse(Settings.shared.listenHistoryPaused)
    }

    @Test
    fun unmarkingTheCurrentSongTakesEffectImmediately() {
        Settings.shared.incognitoModeEnabled = true
        IncognitoMarks.mark(marked(MarkKind.SONG, "a"))
        IncognitoPlayback.onCurrentSongChanged(song("a"))
        IncognitoMarks.unmark(MarkKind.SONG, "a")
        assertFalse(Settings.shared.hidesCurrentPlayback)
    }

    @Test
    fun skipFilterIsOffUntilTheSettingIsOn() {
        SkipMarks.mark(marked(MarkKind.SONG, "b"))
        val songs = listOf(song("a"), song("b"), song("c"))
        assertEquals(songs, SkipMarks.filterForQueue(songs, 0).songs)
        Settings.shared.skipMarkingEnabled = true
        assertEquals(listOf("a", "c"), SkipMarks.filterForQueue(songs, 0).songs.map { it.id })
    }

    @Test
    fun skipFilterKeepsTheChosenStartSongAndRemapsItsIndex() {
        Settings.shared.skipMarkingEnabled = true
        SkipMarks.mark(marked(MarkKind.SONG, "a"))
        SkipMarks.mark(marked(MarkKind.SONG, "c"))
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))
        val filtered = SkipMarks.filterForQueue(songs, startIndex = 2)
        assertEquals(listOf("b", "c", "d"), filtered.songs.map { it.id })
        assertEquals(1, filtered.startIndex)
        assertEquals(-1, SkipMarks.filterForQueue(songs, startIndex = -1).startIndex)
    }

    @Test
    fun playbackQueueLeavesOutMarkedSongsButManualAddsStillLand() {
        Settings.shared.skipMarkingEnabled = true
        SkipMarks.mark(marked(MarkKind.SONG, "b"))
        SkipMarks.mark(marked(MarkKind.SONG, "e"))
        val queue = MusicQueue().apply { leavesOutSkipMarked = true }
        queue.setQueue(listOf(song("a"), song("b"), song("c")), 0)
        assertEquals(listOf("a", "c"), queue.items.value.map { it.id })
        queue.appendPage(listOf(song("d"), song("e")))
        assertEquals(listOf("a", "c", "d"), queue.items.value.map { it.id })
        queue.addToQueue(song("e"))
        assertEquals(listOf("a", "c", "d", "e"), queue.items.value.map { it.id })
    }

    @Test
    fun otherQueuesKeepEveryEntry() {
        Settings.shared.skipMarkingEnabled = true
        SkipMarks.mark(marked(MarkKind.SONG, "b"))
        val queue = MusicQueue()
        queue.setQueue(listOf(song("a"), song("b")), 0)
        assertEquals(listOf("a", "b"), queue.items.value.map { it.id })
    }
}
