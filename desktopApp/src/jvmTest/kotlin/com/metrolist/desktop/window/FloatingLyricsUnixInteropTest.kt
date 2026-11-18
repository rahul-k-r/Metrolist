package com.metrolist.desktop.window

import com.metrolist.shared.settings.FloatingLyricsShortcut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloatingLyricsUnixInteropTest {
    private val ctrlShiftL = shortcut(ctrl = true, shift = true, key = "L")
    private val altMetaA = shortcut(alt = true, meta = true, key = "A")

    private fun shortcut(
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        key: String,
    ) = FloatingLyricsShortcut(ctrl, alt, shift, meta, key)

    @Test
    fun macModifiersUseCarbonMasksWithMetaAsCommand() {
        assertEquals(0x1000 or 0x200, macModifiers(ctrlShiftL))
        assertEquals(0x100 or 0x800, macModifiers(altMetaA))
    }

    @Test
    fun macVirtualKeysMatchTheAnsiLayout() {
        assertEquals(0x25, macVirtualKey("L"))
        assertEquals(0x00, macVirtualKey("A"))
        assertEquals(0x06, macVirtualKey("Z"))
        assertEquals(0x1D, macVirtualKey("0"))
        assertEquals(0x19, macVirtualKey("9"))
        assertEquals(0x7A, macVirtualKey("F1"))
        assertEquals(0x6F, macVirtualKey("F12"))
        assertNull(macVirtualKey("F13"))
        assertNull(macVirtualKey("Esc"))
    }

    @Test
    fun everyLetterAndDigitHasADistinctMacKeyCode() {
        val keys = ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() } + (1..12).map { "F$it" }
        val codes = keys.map { checkNotNull(macVirtualKey(it)) { it } }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun x11ModifiersUseSuperForMeta() {
        assertEquals(4 or 1, x11Modifiers(ctrlShiftL))
        assertEquals(8 or 64, x11Modifiers(altMetaA))
    }

    @Test
    fun x11GrabsEveryNumLockAndCapsLockCombination() {
        val variants = x11GrabVariants(4 or 1)
        assertEquals(listOf(5, 7, 21, 23), variants)
        assertEquals(4, variants.toSet().size)
    }

    @Test
    fun x11KeysymNamesAreLowercaseLettersDigitsAndFunctionKeys() {
        assertEquals("l", x11KeysymName("L"))
        assertEquals("5", x11KeysymName("5"))
        assertEquals("F8", x11KeysymName("F8"))
        assertEquals("F12", x11KeysymName("F12"))
        assertNull(x11KeysymName("F13"))
        assertNull(x11KeysymName("Esc"))
    }
}
