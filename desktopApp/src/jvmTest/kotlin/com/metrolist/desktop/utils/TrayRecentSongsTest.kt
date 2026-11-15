package com.metrolist.desktop.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class TrayRecentSongsTest {
    @Test
    fun keepsNewestOccurrenceOfEachSongInOrder() {
        val plays = sequenceOf("c", "a", "c", "b", "a")
        assertEquals(listOf("c", "a", "b"), recentDistinct(plays, { it }, 10))
    }

    @Test
    fun stopsAtTheLimit() {
        assertEquals(listOf("a", "b"), recentDistinct(sequenceOf("a", "b", "c"), { it }, 2))
    }

    @Test
    fun emptyHistoryGivesAnEmptyList() {
        assertEquals(emptyList(), recentDistinct(emptySequence<String>(), { it }, 5))
    }
}
