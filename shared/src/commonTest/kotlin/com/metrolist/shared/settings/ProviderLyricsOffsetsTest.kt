package com.metrolist.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderLyricsOffsetsTest {
    @Test
    fun `offset is isolated per provider`() {
        var raw = ProviderLyricsOffsets.set("", "song", "LrcLib", 250)
        raw = ProviderLyricsOffsets.set(raw, "song", "Kugou", -100)
        assertEquals(250, ProviderLyricsOffsets.get(raw, "song", "LrcLib"))
        assertEquals(-100, ProviderLyricsOffsets.get(raw, "song", "Kugou"))
        assertNull(ProviderLyricsOffsets.get(raw, "other", "LrcLib"))
    }

    @Test
    fun `zero offset is stored explicitly`() {
        val raw = ProviderLyricsOffsets.set("", "song", "LrcLib", 250)
        assertEquals(0, ProviderLyricsOffsets.get(ProviderLyricsOffsets.set(raw, "song", "LrcLib", 0), "song", "LrcLib"))
    }

    @Test
    fun `corrupt json reads as empty`() {
        assertNull(ProviderLyricsOffsets.get("{not json", "song", "LrcLib"))
    }
}
