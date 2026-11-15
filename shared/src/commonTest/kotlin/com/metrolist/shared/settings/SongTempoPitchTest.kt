package com.metrolist.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongTempoPitchTest {
    @Test
    fun savedValueOverridesGlobalOnlyForThatSong() {
        val raw = SongTempoPitch.set("", "a", SongTempoPitchValue(1.25f, 0.9f))
        assertEquals(SongTempoPitchValue(1.25f, 0.9f), SongTempoPitch.effective(raw, "a", 1f, 1f))
        assertEquals(SongTempoPitchValue(1.5f, 1.1f), SongTempoPitch.effective(raw, "b", 1.5f, 1.1f))
        assertEquals(SongTempoPitchValue(1f, 1f), SongTempoPitch.effective(raw, null, 1f, 1f))
    }

    @Test
    fun explicitNormalSpeedStillOverridesAGlobalChange() {
        val raw = SongTempoPitch.set("", "a", SongTempoPitchValue(1f, 1f))
        assertEquals(SongTempoPitchValue(1f, 1f), SongTempoPitch.effective(raw, "a", 1.5f, 1.2f))
    }

    @Test
    fun removeClearsTheSongAndEmptiesTheBlob() {
        val raw = SongTempoPitch.set(SongTempoPitch.set("", "a", SongTempoPitchValue(1.2f, 1f)), "b", SongTempoPitchValue(0.8f, 1f))
        assertNull(SongTempoPitch.get(SongTempoPitch.remove(raw, "a"), "a"))
        assertEquals("", SongTempoPitch.remove(SongTempoPitch.remove(raw, "a"), "b"))
    }

    @Test
    fun corruptJsonReadsAsNoOverride() {
        assertNull(SongTempoPitch.get("{nope", "a"))
    }
}
