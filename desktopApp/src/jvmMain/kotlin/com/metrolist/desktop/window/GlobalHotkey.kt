package com.metrolist.desktop.window

import com.metrolist.shared.settings.FloatingLyricsShortcut
import com.metrolist.shared.utils.Logger
import com.metrolist.shared.utils.OsUtil

/** A registered system-wide hotkey; [close] releases it. */
internal class GlobalHotkey(
    private val release: () -> Unit,
) : AutoCloseable {
    override fun close() = release()
}

/**
 * Registers [shortcut] as a hotkey that fires even while another app has focus: Win32 `RegisterHotKey` on Windows,
 * Carbon `RegisterEventHotKey` on macOS, `XGrabKey` on Linux/X11. Returns null where that isn't possible (native
 * Wayland, an unsupported OS, or a combination another program already owns). [onPressed] may run on any thread.
 */
internal fun registerGlobalHotkey(
    shortcut: FloatingLyricsShortcut,
    onPressed: () -> Unit,
): GlobalHotkey? =
    runCatching {
        when {
            OsUtil.isWindows -> registerWindowsHotkey(shortcut, onPressed)
            OsUtil.isMac -> registerMacHotkey(shortcut, onPressed)
            OsUtil.isLinux -> registerX11Hotkey(shortcut, onPressed)
            else -> null
        }
    }.onFailure { Logger.w("GlobalHotkey", "Could not register ${shortcut.format()}: ${it::class.simpleName}") }.getOrNull()
