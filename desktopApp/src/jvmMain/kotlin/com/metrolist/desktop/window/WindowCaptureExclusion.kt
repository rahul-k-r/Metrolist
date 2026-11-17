package com.metrolist.desktop.window

import com.metrolist.desktop.window.jna.User32Extend
import com.metrolist.shared.utils.Logger
import com.metrolist.shared.utils.OsUtil
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Window

private const val WDA_NONE = 0x0
private const val WDA_EXCLUDEFROMCAPTURE = 0x11

internal fun displayAffinityFor(hideFromCapture: Boolean): Int = if (hideFromCapture) WDA_EXCLUDEFROMCAPTURE else WDA_NONE

/**
 * Hides [window] from screenshots and screen recordings. Windows only (10 2004+); a no-op elsewhere, and a logged
 * failure on older builds where the exclusion flag is unsupported.
 */
internal fun setWindowHiddenFromCapture(
    window: Window,
    hidden: Boolean,
) {
    if (!OsUtil.isWindows) return
    runCatching {
        val handle = HWND(Native.getComponentPointer(window))
        val applied = User32Extend.instance?.SetWindowDisplayAffinity(handle, displayAffinityFor(hidden)) == true
        if (!applied) Logger.w("WindowCaptureExclusion", "SetWindowDisplayAffinity was rejected")
    }.onFailure { Logger.w("WindowCaptureExclusion", "Could not change capture exclusion: ${it::class.simpleName}") }
}
