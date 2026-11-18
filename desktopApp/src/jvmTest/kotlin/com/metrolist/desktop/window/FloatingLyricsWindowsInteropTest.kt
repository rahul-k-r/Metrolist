package com.metrolist.desktop.window

import com.metrolist.shared.settings.FloatingLyricsShortcut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloatingLyricsWindowsInteropTest {
    @Test
    fun modifierMaskCombinesTheHeldModifiers() {
        assertEquals(0x2 or 0x4, win32Modifiers(FloatingLyricsShortcut.DEFAULT))
        assertEquals(0x1 or 0x8, win32Modifiers(FloatingLyricsShortcut(ctrl = false, alt = true, shift = false, meta = true, key = "K")))
    }

    @Test
    fun virtualKeysMatchWin32Codes() {
        assertEquals(0x4C, win32VirtualKey("L"))
        assertEquals(0x35, win32VirtualKey("5"))
        assertEquals(0x70, win32VirtualKey("F1"))
        assertEquals(0x7B, win32VirtualKey("F12"))
        assertNull(win32VirtualKey("F13"))
        assertNull(win32VirtualKey("Enter"))
    }

    @Test
    fun clickThroughAddsAndRemovesOnlyItsOwnBits() {
        val base = 0x00000008 // some unrelated style bit
        val on = clickThroughExStyle(base, enabled = true)
        assertEquals(base or 0x20 or 0x80000, on)
        // Layered stays: the window may have been layered before click-through was requested.
        assertEquals(base or 0x80000, clickThroughExStyle(on, enabled = false))
    }
}
