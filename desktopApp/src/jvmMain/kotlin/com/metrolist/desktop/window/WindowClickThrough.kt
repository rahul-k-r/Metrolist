package com.metrolist.desktop.window

import com.metrolist.shared.utils.Logger
import com.metrolist.shared.utils.OsUtil
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.Window

private const val WS_EX_TRANSPARENT = 0x20
private const val WS_EX_LAYERED = 0x80000
private const val LWA_ALPHA = 0x2

/** The extended style with mouse pass-through on or off; the layered bit is required for the transparent bit to take effect. */
internal fun clickThroughExStyle(
    current: Int,
    enabled: Boolean,
): Int = if (enabled) current or WS_EX_TRANSPARENT or WS_EX_LAYERED else current and WS_EX_TRANSPARENT.inv()

/**
 * Lets mouse input fall through [window] to whatever is beneath it: `WS_EX_TRANSPARENT` on Windows, `ignoresMouseEvents`
 * on macOS, an empty XShape input region on X11/XWayland. A native-Wayland JVM has no equivalent and logs a warning.
 */
internal fun setWindowClickThrough(
    window: Window,
    enabled: Boolean,
) {
    when {
        OsUtil.isWindows -> setWindowsClickThrough(window, enabled)
        OsUtil.isMac -> setMacClickThrough(window, enabled)
        OsUtil.isLinux ->
            runCatching { setX11ClickThrough(window, enabled) }
                .onFailure { Logger.w("WindowClickThrough", "Click-through is unavailable here: ${it::class.simpleName}") }
    }
}

private fun setWindowsClickThrough(
    window: Window,
    enabled: Boolean,
) {
    runCatching {
        val user32 = User32.INSTANCE
        val handle = HWND(Native.getComponentPointer(window))
        val current = user32.GetWindowLong(handle, WinUser.GWL_EXSTYLE)
        user32.SetWindowLong(handle, WinUser.GWL_EXSTYLE, clickThroughExStyle(current, enabled))
        // A window that was not layered before would otherwise turn invisible.
        if (enabled && current and WS_EX_LAYERED == 0) user32.SetLayeredWindowAttributes(handle, 0, 255.toByte(), LWA_ALPHA)
    }.onFailure { Logger.w("WindowClickThrough", "Could not change click-through: ${it::class.simpleName}") }
}
