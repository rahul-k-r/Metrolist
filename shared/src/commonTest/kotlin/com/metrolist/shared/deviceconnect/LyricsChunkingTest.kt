package com.metrolist.shared.deviceconnect

import com.metrolist.shared.settings.LyricsSyncItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsChunkingTest {
    private fun item(
        id: String,
        chars: Int,
    ) = LyricsSyncItem(songId = id, updatedAt = 1, userLyrics = "x".repeat(chars))

    private fun wireSize(chunk: List<LyricsSyncItem>) =
        DcJson.encodeToString(DcDataMessage(DcDataKinds.LYRICS, lyrics = chunk)).encodeToByteArray().size

    @Test
    fun everyChunkStaysWithinTheBudgetAndNoItemIsReordered() {
        val items = (1..40).map { item("s$it", 6_000) }
        val chunks = chunkLyricsItems(items)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { wireSize(it) <= LYRICS_MESSAGE_BUDGET_BYTES })
        assertEquals(items, chunks.flatten())
    }

    @Test
    fun anItemTooLargeForOneFrameIsSkippedWithoutDroppingItsNeighbours() {
        val items = listOf(item("a", 100), item("huge", 200_000), item("b", 100))
        assertEquals(listOf("a", "b"), chunkLyricsItems(items).flatten().map { it.songId })
    }

    @Test
    fun smallItemsShareOneMessage() {
        assertEquals(1, chunkLyricsItems((1..30).map { item("s$it", 50) }).size)
        assertEquals(emptyList(), chunkLyricsItems(emptyList()))
    }
}
