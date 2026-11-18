package com.metrolist.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsSyncTest {
    private fun stamps(clock: () -> Long): LyricsSyncStamps {
        var json = ""
        return LyricsSyncStamps({ json }, { json = it }, clock)
    }

    @Test
    fun touchRecordsTheLocalChangeTime() {
        var t = 100L
        val stamps = stamps { t }
        stamps.touch("a")
        t = 200L
        stamps.touch("b")
        assertEquals(mapOf("a" to 100L, "b" to 200L), stamps.all())
        assertEquals(0L, stamps.stamp("missing"))
    }

    @Test
    fun onlyAStrictlyNewerRemoteChangeApplies() {
        val stamps = stamps { 1_000L }
        stamps.accept("a", 500L)
        assertTrue(stamps.shouldApply("a", 501L))
        assertFalse(stamps.shouldApply("a", 500L))
        assertFalse(stamps.shouldApply("a", 400L))
        assertTrue(stamps.shouldApply("new", 1L))
    }

    @Test
    fun aFarFutureRemoteStampIsRejectedSoItCannotPinTheSong() {
        val stamps = stamps { 1_000L }
        assertFalse(stamps.shouldApply("a", Long.MAX_VALUE))
        assertFalse(stamps.shouldApply("a", 1_000L + 10 * 60 * 1000L))
        assertTrue(stamps.shouldApply("a", 1_000L + 60 * 1000L))
        assertFalse(stamps.shouldApply("a", 0L))
    }

    @Test
    fun garbageStampsReadAsEmptyAndOversizedItemsAreRejected() {
        var json = "not json"
        val stamps = LyricsSyncStamps({ json }, { json = it }, { 1L })
        assertTrue(stamps.all().isEmpty())
        stamps.touch("a")
        assertEquals(setOf("a"), stamps.all().keys)
        assertFalse(LyricsSyncItem("", 1).isAcceptable())
        assertFalse(LyricsSyncItem("a", 1, userLyrics = "x".repeat(2_000_001)).isAcceptable())
        assertTrue(LyricsSyncItem("a", 1, userLyrics = "ok").isAcceptable())
    }

    @Test
    fun aFullStampTableEvictsTheOldestSongInsteadOfDroppingTheNewEdit() {
        var json = (1..MAX_LYRICS_SYNC_SONGS).joinToString(",", "{", "}") { "\"s$it\":$it" }
        val stamps = LyricsSyncStamps({ json }, { json = it }, { 1_000_000L })
        stamps.touch("fresh")
        val all = stamps.all()
        assertEquals(MAX_LYRICS_SYNC_SONGS, all.size)
        assertEquals(1_000_000L, all["fresh"])
        assertFalse("s1" in all)
        assertTrue("s2" in all)
        stamps.touch("s2")
        assertEquals(MAX_LYRICS_SYNC_SONGS, stamps.all().size, "updating a tracked song does not evict")
    }

    @Test
    fun lyricsStampsStayOnThisDeviceWhenSettingsAreSynced() {
        val settings = Settings(com.russhwolf.settings.MapSettings())
        settings.lyricsSyncStampsJson = """{"a":1}"""
        assertTrue(settings.exportSyncSettings().entries.none { it.key == "lyricsSyncStampsJson" })
    }
}
