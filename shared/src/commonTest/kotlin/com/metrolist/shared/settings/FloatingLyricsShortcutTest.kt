package com.metrolist.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloatingLyricsShortcutTest {
    @Test
    fun defaultTextParsesToDefault() {
        assertEquals(FloatingLyricsShortcut.DEFAULT, FloatingLyricsShortcut.parse(FloatingLyricsShortcut.DEFAULT_TEXT))
        assertEquals(FloatingLyricsShortcut.DEFAULT_TEXT, FloatingLyricsShortcut.DEFAULT.format())
    }

    @Test
    fun parseIsCaseAndOrderTolerantAndFormatIsCanonical() {
        val parsed = FloatingLyricsShortcut.parse("shift + alt+ctrl + f9")
        assertNotNull(parsed)
        assertEquals("Ctrl+Alt+Shift+F9", parsed.format())
    }

    @Test
    fun combinationWithoutCtrlAltOrMetaIsRejected() {
        assertNull(FloatingLyricsShortcut.parse("L"))
        assertNull(FloatingLyricsShortcut.parse("Shift+L"))
    }

    @Test
    fun unknownModifiersAndKeysAreRejected() {
        assertNull(FloatingLyricsShortcut.parse("Super+L"))
        assertNull(FloatingLyricsShortcut.parse("Ctrl+Enter"))
        assertNull(FloatingLyricsShortcut.parse("Ctrl+F13"))
        assertNull(FloatingLyricsShortcut.parse(""))
        assertNull(FloatingLyricsShortcut.parse("Ctrl+"))
    }

    @Test
    fun metaAloneCountsAsAModifier() {
        assertTrue(FloatingLyricsShortcut.parse("Meta+Shift+K")?.isValid == true)
    }
}
