package com.metrolist.shared.settings

import com.metrolist.shared.db.historyExcludedContent
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.SongItem
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionMarksTest {
    @BeforeTest
    fun setUp() {
        Settings.replaceSharedForTest(Settings(MapSettings()))
    }

    @AfterTest
    fun tearDown() {
        Settings.replaceSharedForTest(null)
    }

    private fun song(id: String) = SongItem(id, "Song $id", null, listOf(ArtistTiny("artist-$id", "Artist $id")), null)

    private fun mark(
        kind: MarkKind,
        id: String,
    ) = MarkedItem(kind, id, "Title $id")

    @Test
    fun playlistMarkMatchesItsSongsOnceLoaded() {
        IncognitoMarks.mark(mark(MarkKind.PLAYLIST, "PL1"))
        assertFalse(IncognitoMarks.matches(song("a")), "members are unknown until the playlist is loaded")

        CollectionMarkMembers.set(MarkKind.PLAYLIST, "PL1", listOf("a", "b"))
        assertTrue(IncognitoMarks.matches(song("a")))
        assertTrue(IncognitoMarks.matches(song("b")))
        assertFalse(IncognitoMarks.matches(song("c")))
        assertFalse(SkipMarks.matches(song("a")), "the skip registry has its own marks")
    }

    @Test
    fun albumMarkMatchesAndUnmarkingStopsIt() {
        SkipMarks.mark(mark(MarkKind.ALBUM, "MPRE1"))
        CollectionMarkMembers.set(MarkKind.ALBUM, "MPRE1", listOf("a"))
        assertTrue(SkipMarks.matches(song("a")))
        SkipMarks.unmark(MarkKind.ALBUM, "MPRE1")
        assertFalse(SkipMarks.matches(song("a")))
    }

    @Test
    fun sameIdInDifferentKindsDoesNotCollide() {
        IncognitoMarks.mark(mark(MarkKind.PLAYLIST, "X"))
        assertTrue(IncognitoMarks.isMarked(MarkKind.PLAYLIST, "X"))
        assertFalse(IncognitoMarks.isMarked(MarkKind.ALBUM, "X"))
    }

    @Test
    fun emptyOrFailedLoadKeepsWhatWasKnown() =
        runTest {
            CollectionMarkMembers.set(MarkKind.PLAYLIST, "PL1", listOf("a"))
            CollectionMarkMembers.set(MarkKind.PLAYLIST, "PL1", emptyList())
            CollectionMarkMembers.refresh(MarkKind.PLAYLIST, "PL1") { error("network down") }
            assertEquals(setOf("a"), CollectionMarkMembers.songIds(listOf(CollectionMarkMembers.key(MarkKind.PLAYLIST, "PL1"))))
        }

    @Test
    fun refreshMarkedLoadsMarkedCollectionsAndForgetsUnmarkedOnes() =
        runTest {
            IncognitoMarks.mark(mark(MarkKind.PLAYLIST, "PL1"))
            SkipMarks.mark(mark(MarkKind.ALBUM, "MPRE1"))
            CollectionMarkMembers.set(MarkKind.PLAYLIST, "stale", listOf("z"))

            CollectionMarkMembers.refreshMarked { item ->
                when (item.id) {
                    "PL1" -> listOf(song("a"))
                    "MPRE1" -> listOf(song("b"))
                    else -> null
                }
            }

            assertTrue(IncognitoMarks.matches(song("a")))
            assertTrue(SkipMarks.matches(song("b")))
            assertFalse(CollectionMarkMembers.has(MarkKind.PLAYLIST, "stale"))
        }

    @Test
    fun membersAreNotSynced() {
        CollectionMarkMembers.set(MarkKind.PLAYLIST, "PL1", listOf("a"))
        assertTrue(Settings.shared.collectionMarkMembersJson.isNotBlank())
        assertTrue(Settings.shared.exportSyncSettings().entries.none { it.key == "collectionMarkMembersJson" })
    }

    @Test
    fun skipFilterDropsMarkedPlaylistSongsButNeverEmptiesAnExplicitPlay() {
        Settings.shared.skipMarkingEnabled = true
        SkipMarks.mark(mark(MarkKind.PLAYLIST, "PL1"))
        CollectionMarkMembers.set(MarkKind.PLAYLIST, "PL1", listOf("a", "b"))

        assertEquals(listOf("c"), SkipMarks.filterForQueue(listOf(song("a"), song("c"), song("b"))).songs.map { it.id })
        val all = listOf(song("a"), song("b"))
        assertEquals(all, SkipMarks.filterForQueue(all).songs, "playing the marked playlist itself still plays it")
    }

    @Test
    fun statisticsExcludeSongsOfIncognitoMarkedCollections() {
        val excluded =
            historyExcludedContent(
                blockedArtists = emptyList(),
                blockedSongIds = setOf("blocked"),
                incognitoEnabled = true,
                incognitoMarks = listOf(mark(MarkKind.PLAYLIST, "PL1")),
                collectionSongIds = setOf("a", "b"),
            )
        assertEquals(setOf("blocked", "a", "b"), excluded.songIds)
    }
}
