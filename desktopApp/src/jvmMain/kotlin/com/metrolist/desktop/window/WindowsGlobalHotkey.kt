package com.metrolist.desktop.window

import com.metrolist.shared.settings.FloatingLyricsShortcut
import com.metrolist.shared.utils.Logger
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val MOD_ALT = 0x1
private const val MOD_CONTROL = 0x2
private const val MOD_SHIFT = 0x4
private const val MOD_WIN = 0x8
private const val MOD_NOREPEAT = 0x4000
private const val HOTKEY_ID = 0x4D4C
private const val WM_HOTKEY = 0x0312
private const val WM_QUIT = 0x0012

/** The Win32 modifier mask for [shortcut], without MOD_NOREPEAT. */
internal fun win32Modifiers(shortcut: FloatingLyricsShortcut): Int =
    (if (shortcut.ctrl) MOD_CONTROL else 0) or
        (if (shortcut.alt) MOD_ALT else 0) or
        (if (shortcut.shift) MOD_SHIFT else 0) or
        (if (shortcut.meta) MOD_WIN else 0)

/** Virtual-key code for letters, digits and F1..F12 (the keys [FloatingLyricsShortcut] allows). */
internal fun win32VirtualKey(key: String): Int? =
    when {
        key.length == 1 && (key[0] in 'A'..'Z' || key[0] in '0'..'9') -> key[0].code
        key.length in 2..3 && key[0] == 'F' -> key.drop(1).toIntOrNull()?.takeIf { it in 1..12 }?.let { 0x70 + it - 1 }
        else -> null
    }

/** Windows implementation of [registerGlobalHotkey]: `RegisterHotKey` on a dedicated message-pump thread. */
internal fun registerWindowsHotkey(
    shortcut: FloatingLyricsShortcut,
    onPressed: () -> Unit,
): GlobalHotkey? {
    val virtualKey = win32VirtualKey(shortcut.key) ?: return null
    val registered = CompletableFuture<Int?>()
    val worker = thread(isDaemon = true, name = "floating-lyrics-hotkey") {
        val user32 = User32.INSTANCE
        val message = WinUser.MSG()
        // Forces this thread's message queue to exist before the hotkey can post to it.
        user32.PeekMessage(message, null, 0, 0, 0)
        val ok = user32.RegisterHotKey(null, HOTKEY_ID, win32Modifiers(shortcut) or MOD_NOREPEAT, virtualKey)
        registered.complete(if (ok) Kernel32.INSTANCE.GetCurrentThreadId() else null)
        if (!ok) return@thread
        try {
            while (user32.GetMessage(message, null, 0, 0) > 0) {
                if (message.message == WM_HOTKEY) runCatching(onPressed)
            }
        } finally {
            user32.UnregisterHotKey(null, HOTKEY_ID)
        }
    }
    val threadId = runCatching { registered.get(2, TimeUnit.SECONDS) }.getOrNull()
    if (threadId == null) {
        Logger.w("GlobalHotkey", "Could not register ${shortcut.format()}; another program may be using it")
        return null
    }
    return GlobalHotkey {
        User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, WPARAM(0), LPARAM(0))
        // The combination is only free once the pump thread has unregistered it, so a re-register must wait for that.
        worker.join(1_000)
    }
}
