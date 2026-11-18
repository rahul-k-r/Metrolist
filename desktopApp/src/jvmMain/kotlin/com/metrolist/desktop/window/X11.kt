package com.metrolist.desktop.window

import com.metrolist.shared.settings.FloatingLyricsShortcut
import com.metrolist.shared.utils.Logger
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Window
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// Xlib/XShape bindings for Linux (X11 or XWayland). Window, KeySym and XID are `unsigned long`, which is 64-bit on the
// 64-bit Linux targets this ships for, so they bind to Kotlin Long. Loaded lazily, only ever touched on Linux.
internal interface X11Lib : Library {
    fun XOpenDisplay(name: String?): Pointer?

    fun XCloseDisplay(display: Pointer): Int

    fun XDefaultRootWindow(display: Pointer): Long

    fun XStringToKeysym(name: String): Long

    fun XKeysymToKeycode(
        display: Pointer,
        keysym: Long,
    ): Byte

    fun XGrabKey(
        display: Pointer,
        keycode: Int,
        modifiers: Int,
        grabWindow: Long,
        ownerEvents: Int,
        pointerMode: Int,
        keyboardMode: Int,
    ): Int

    fun XUngrabKey(
        display: Pointer,
        keycode: Int,
        modifiers: Int,
        grabWindow: Long,
    ): Int

    fun XSelectInput(
        display: Pointer,
        window: Long,
        eventMask: Long,
    ): Int

    fun XPending(display: Pointer): Int

    fun XNextEvent(
        display: Pointer,
        event: Pointer,
    ): Int

    fun XSync(
        display: Pointer,
        discard: Int,
    ): Int

    fun XFlush(display: Pointer): Int

    fun XSetErrorHandler(handler: Callback): Pointer?

    /** Reinstalls a handler returned earlier (null restores Xlib's default). */
    fun XSetErrorHandler(handler: Pointer?): Pointer?
}

internal interface XextLib : Library {
    fun XShapeCombineRectangles(
        display: Pointer,
        dest: Long,
        destKind: Int,
        xOff: Int,
        yOff: Int,
        rectangles: Pointer?,
        count: Int,
        op: Int,
        ordering: Int,
    )

    fun XShapeCombineMask(
        display: Pointer,
        dest: Long,
        destKind: Int,
        xOff: Int,
        yOff: Int,
        source: Long,
        op: Int,
    )
}

internal fun interface XErrorHandler : Callback {
    fun invoke(
        display: Pointer?,
        event: Pointer?,
    ): Int
}

private const val SHIFT_MASK = 1
private const val LOCK_MASK = 2
private const val CONTROL_MASK = 4
private const val MOD1_MASK = 8
private const val MOD2_MASK = 16
private const val MOD4_MASK = 64
private const val KEY_PRESS = 2
private const val GRAB_MODE_ASYNC = 1
private const val X_GRAB_KEY_REQUEST = 33
private const val BAD_ACCESS = 10
private const val SHAPE_INPUT = 2
private const val SHAPE_SET = 0
private const val UNSORTED = 0

// Byte offsets into the LP64 XEvent / XKeyEvent / XErrorEvent structs.
private const val XKEY_EVENT_KEYCODE_OFFSET = 84L
private const val XERROR_EVENT_ERROR_CODE_OFFSET = 32L
private const val XERROR_EVENT_REQUEST_CODE_OFFSET = 33L
private const val X_EVENT_BYTES = 192L

/** The X11 modifier mask for [shortcut]; Super (Mod4) stands in for Meta. */
internal fun x11Modifiers(shortcut: FloatingLyricsShortcut): Int =
    (if (shortcut.ctrl) CONTROL_MASK else 0) or
        (if (shortcut.alt) MOD1_MASK else 0) or
        (if (shortcut.shift) SHIFT_MASK else 0) or
        (if (shortcut.meta) MOD4_MASK else 0)

/**
 * X delivers a grab only for the exact modifier state, so NumLock (Mod2) and CapsLock (Lock) would silently disable
 * the hotkey. The same grab is therefore repeated for every combination of those two.
 */
internal fun x11GrabVariants(modifiers: Int): List<Int> =
    listOf(0, LOCK_MASK, MOD2_MASK, LOCK_MASK or MOD2_MASK).map { modifiers or it }

/** The keysym name XStringToKeysym resolves for [key]: lowercase letters, digits and F1..F12 as is. */
internal fun x11KeysymName(key: String): String? =
    when {
        key.length == 1 && key[0] in 'A'..'Z' -> key.lowercase()
        key.length == 1 && key[0] in '0'..'9' -> key
        key.length in 2..3 && key[0] == 'F' && key.drop(1).toIntOrNull() in 1..12 -> key
        else -> null
    }

private val x11: X11Lib by lazy { Native.load("X11", X11Lib::class.java) }
private val xext: XextLib by lazy { Native.load("Xext", XextLib::class.java) }

/** Linux implementation of [registerGlobalHotkey]: `XGrabKey` on the root window, polled on its own display connection. */
internal fun registerX11Hotkey(
    shortcut: FloatingLyricsShortcut,
    onPressed: () -> Unit,
): GlobalHotkey? {
    val keysymName = x11KeysymName(shortcut.key) ?: return null
    val ready = CompletableFuture<Boolean>()
    val running = AtomicBoolean(true)
    thread(isDaemon = true, name = "floating-lyrics-hotkey") {
        // A private connection: AWT's own display must never be touched from this thread.
        val display = x11.XOpenDisplay(null)
        if (display == null) {
            ready.complete(false)
            return@thread
        }
        val root = x11.XDefaultRootWindow(display)
        val keycode = x11.XKeysymToKeycode(display, x11.XStringToKeysym(keysymName)).toInt() and 0xFF
        val variants = x11GrabVariants(x11Modifiers(shortcut))
        var failed = keycode == 0
        if (!failed) {
            // Xlib's error handler is process-wide and AWT has its own; ours is swapped in only around the grabs.
            val handler =
                XErrorHandler { _, event ->
                    val isGrabDenied =
                        event != null &&
                            event.getByte(XERROR_EVENT_REQUEST_CODE_OFFSET).toInt() == X_GRAB_KEY_REQUEST &&
                            event.getByte(XERROR_EVENT_ERROR_CODE_OFFSET).toInt() == BAD_ACCESS
                    if (isGrabDenied) failed = true
                    0
                }
            val previous = x11.XSetErrorHandler(handler)
            variants.forEach { x11.XGrabKey(display, keycode, it, root, 0, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC) }
            x11.XSync(display, 0)
            x11.XSetErrorHandler(previous)
        }
        if (failed) {
            variants.forEach { x11.XUngrabKey(display, keycode, it, root) }
            x11.XCloseDisplay(display)
            ready.complete(false)
            return@thread
        }
        ready.complete(true)
        val event = Memory(X_EVENT_BYTES)
        var lastFiredAt = 0L
        try {
            while (running.get()) {
                if (x11.XPending(display) == 0) {
                    Thread.sleep(25)
                    continue
                }
                x11.XNextEvent(display, event)
                val isOurPress = event.getInt(0) == KEY_PRESS && event.getInt(XKEY_EVENT_KEYCODE_OFFSET) == keycode
                // Holding the key auto-repeats presses, which would flip the toggle back and forth.
                val now = System.currentTimeMillis()
                if (isOurPress && now - lastFiredAt > 300) {
                    lastFiredAt = now
                    runCatching(onPressed)
                }
            }
        } finally {
            variants.forEach { x11.XUngrabKey(display, keycode, it, root) }
            x11.XCloseDisplay(display)
        }
    }
    val ok = runCatching { ready.get(2, TimeUnit.SECONDS) }.getOrDefault(false)
    if (!ok) {
        running.set(false)
        Logger.w("GlobalHotkey", "Could not grab ${shortcut.format()} on X11; no X server, or another program owns it")
        return null
    }
    return GlobalHotkey { running.set(false) }
}

/** Lets pointer input fall through [window] on X11/XWayland by giving it an empty input shape. */
internal fun setX11ClickThrough(
    window: Window,
    enabled: Boolean,
) {
    val id = Native.getWindowID(window)
    val display = x11.XOpenDisplay(null) ?: return
    try {
        if (enabled) {
            xext.XShapeCombineRectangles(display, id, SHAPE_INPUT, 0, 0, null, 0, SHAPE_SET, UNSORTED)
        } else {
            // A null mask resets the input region to the whole window.
            xext.XShapeCombineMask(display, id, SHAPE_INPUT, 0, 0, 0L, SHAPE_SET)
        }
        x11.XSync(display, 0)
    } finally {
        x11.XCloseDisplay(display)
    }
}
