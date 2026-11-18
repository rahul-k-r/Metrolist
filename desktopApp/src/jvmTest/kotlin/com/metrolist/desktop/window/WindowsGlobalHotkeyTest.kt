package com.metrolist.desktop.window

import com.metrolist.shared.settings.FloatingLyricsShortcut
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertNotNull

class WindowsGlobalHotkeyTest {
    @Test
    fun closingReleasesTheCombinationBeforeItReturnsSoItCanBeRegisteredAgain() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val shortcut = FloatingLyricsShortcut.parse("Ctrl+Alt+Shift+F12")!!
        repeat(30) { attempt ->
            val hotkey = registerWindowsHotkey(shortcut) {}
            assertNotNull(hotkey, "re-registration $attempt failed")
            hotkey.close()
        }
    }
}
