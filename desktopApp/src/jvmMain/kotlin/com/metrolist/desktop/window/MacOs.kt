package com.metrolist.desktop.window

import com.metrolist.shared.settings.FloatingLyricsShortcut
import com.metrolist.shared.utils.Logger
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import java.awt.Frame
import java.awt.Window
import java.lang.ref.Reference
import java.util.concurrent.ConcurrentHashMap

// Carbon hotkeys and AppKit window flags for macOS, bound with JNA. Only ever loaded on macOS.

/** Carbon's `EventHotKeyID`: two 32-bit words, passed by value. */
@Structure.FieldOrder("signature", "id")
open class EventHotKeyID : Structure() {
    @JvmField var signature: Int = 0

    @JvmField var id: Int = 0

    class ByValue :
        EventHotKeyID(),
        Structure.ByValue
}

internal fun interface CarbonEventHandler : Callback {
    fun invoke(
        nextHandler: Pointer?,
        event: Pointer?,
        userData: Pointer?,
    ): Int
}

internal interface CarbonLib : Library {
    fun GetApplicationEventTarget(): Pointer

    fun InstallEventHandler(
        target: Pointer,
        handler: Callback,
        count: Int,
        types: Pointer,
        userData: Pointer?,
        outRef: PointerByReference,
    ): Int

    fun RemoveEventHandler(ref: Pointer): Int

    fun RegisterEventHotKey(
        keyCode: Int,
        modifiers: Int,
        id: EventHotKeyID.ByValue,
        target: Pointer,
        options: Int,
        outRef: PointerByReference,
    ): Int

    fun UnregisterEventHotKey(ref: Pointer): Int

    fun GetEventParameter(
        event: Pointer,
        name: Int,
        type: Int,
        actualType: Pointer?,
        size: Int,
        actualSize: Pointer?,
        data: Pointer,
    ): Int
}

// objc_msgSend is one symbol that must be called with the signature of the method being sent, so each distinct
// shape gets its own binding of the same symbol.
internal interface ObjCPointerSend : Library {
    fun objc_getClass(name: String): Pointer?

    fun sel_registerName(name: String): Pointer

    fun objc_msgSend(
        receiver: Pointer,
        selector: Pointer,
    ): Pointer?

    fun objc_msgSend(
        receiver: Pointer,
        selector: Pointer,
        index: Long,
    ): Pointer?

    fun objc_msgSend(
        receiver: Pointer,
        selector: Pointer,
        flag: Byte,
    ): Pointer?
}

internal interface ObjCLongSend : Library {
    fun objc_msgSend(
        receiver: Pointer,
        selector: Pointer,
    ): Long
}

internal fun interface DispatchWork : Callback {
    fun invoke(context: Pointer?)
}

internal interface DispatchLib : Library {
    fun dispatch_async_f(
        queue: Pointer,
        context: Pointer?,
        work: Callback,
    )
}

private const val CARBON_PATH = "/System/Library/Frameworks/Carbon.framework/Carbon"
private const val EVENT_CLASS_KEYBOARD = 0x6B657962 // 'keyb'
private const val EVENT_HOT_KEY_PRESSED = 5
private const val EVENT_PARAM_DIRECT_OBJECT = 0x2D2D2D2D // '----'
private const val TYPE_EVENT_HOT_KEY_ID = 0x686B6964 // 'hkid'
private const val HOTKEY_SIGNATURE = 0x4D4C686B // 'MLhk'
private const val HOTKEY_ID = 1
private const val EVENT_NOT_HANDLED = -9874

private const val CMD_KEY = 0x100
private const val SHIFT_KEY = 0x200
private const val OPTION_KEY = 0x800
private const val CONTROL_KEY = 0x1000

/** The Carbon modifier mask for [shortcut]; Meta is Command. */
internal fun macModifiers(shortcut: FloatingLyricsShortcut): Int =
    (if (shortcut.meta) CMD_KEY else 0) or
        (if (shortcut.shift) SHIFT_KEY else 0) or
        (if (shortcut.alt) OPTION_KEY else 0) or
        (if (shortcut.ctrl) CONTROL_KEY else 0)

private val MAC_LETTER_CODES =
    intArrayOf(
        0x00, 0x0B, 0x08, 0x02, 0x0E, 0x03, 0x05, 0x04, 0x22, 0x26, 0x28, 0x25, 0x2E,
        0x2D, 0x1F, 0x23, 0x0C, 0x0F, 0x01, 0x11, 0x20, 0x09, 0x0D, 0x07, 0x10, 0x06,
    )
private val MAC_DIGIT_CODES = intArrayOf(0x1D, 0x12, 0x13, 0x14, 0x15, 0x17, 0x16, 0x1A, 0x1C, 0x19)
private val MAC_FUNCTION_CODES = intArrayOf(0x7A, 0x78, 0x63, 0x76, 0x60, 0x61, 0x62, 0x64, 0x65, 0x6D, 0x67, 0x6F)

/** ANSI virtual key code (`kVK_ANSI_*` / `kVK_F*`) for letters, digits and F1..F12. */
internal fun macVirtualKey(key: String): Int? =
    when {
        key.length == 1 && key[0] in 'A'..'Z' -> MAC_LETTER_CODES[key[0] - 'A']
        key.length == 1 && key[0] in '0'..'9' -> MAC_DIGIT_CODES[key[0] - '0']
        key.length in 2..3 && key[0] == 'F' -> key.drop(1).toIntOrNull()?.takeIf { it in 1..12 }?.let { MAC_FUNCTION_CODES[it - 1] }
        else -> null
    }

private val carbon: CarbonLib by lazy { Native.load(CARBON_PATH, CarbonLib::class.java) }
private val objc: ObjCPointerSend by lazy { Native.load("objc", ObjCPointerSend::class.java) }
private val objcLong: ObjCLongSend by lazy { Native.load("objc", ObjCLongSend::class.java) }
private val dispatch: DispatchLib by lazy { Native.load("System", DispatchLib::class.java) }

/**
 * macOS implementation of [registerGlobalHotkey]: a Carbon hotkey, which needs no Accessibility permission. Events
 * arrive on the AppKit main thread, which AWT already runs, so no event loop is started here.
 */
internal fun registerMacHotkey(
    shortcut: FloatingLyricsShortcut,
    onPressed: () -> Unit,
): GlobalHotkey? {
    val keyCode = macVirtualKey(shortcut.key) ?: return null
    val handler =
        CarbonEventHandler { _, event, _ ->
            val id = Memory(8)
            val read =
                event != null &&
                    carbon.GetEventParameter(event, EVENT_PARAM_DIRECT_OBJECT, TYPE_EVENT_HOT_KEY_ID, null, 8, null, id) == 0
            if (read && id.getInt(0) == HOTKEY_SIGNATURE && id.getInt(4) == HOTKEY_ID) {
                runCatching(onPressed)
                0
            } else {
                EVENT_NOT_HANDLED
            }
        }
    val eventType = Memory(8).apply { setInt(0, EVENT_CLASS_KEYBOARD) ; setInt(4, EVENT_HOT_KEY_PRESSED) }
    val target = carbon.GetApplicationEventTarget()
    val handlerRef = PointerByReference()
    if (carbon.InstallEventHandler(target, handler, 1, eventType, null, handlerRef) != 0) {
        Logger.w("GlobalHotkey", "Could not install the Carbon event handler")
        return null
    }
    val hotKeyId = EventHotKeyID.ByValue().apply { signature = HOTKEY_SIGNATURE ; id = HOTKEY_ID }
    val hotKeyRef = PointerByReference()
    val status = carbon.RegisterEventHotKey(keyCode, macModifiers(shortcut), hotKeyId, target, 0, hotKeyRef)
    if (status != 0) {
        carbon.RemoveEventHandler(handlerRef.value)
        Logger.w("GlobalHotkey", "Could not register ${shortcut.format()} (status $status); another app may own it")
        return null
    }
    return GlobalHotkey {
        carbon.UnregisterEventHotKey(hotKeyRef.value)
        carbon.RemoveEventHandler(handlerRef.value)
        // The native side holds the callback until it is removed above; keep it reachable until then.
        Reference.reachabilityFence(handler)
    }
}

// Work items stay referenced until they have run, or the callback could be collected while queued.
private val pendingMainThreadWork = ConcurrentHashMap.newKeySet<DispatchWork>()

private fun runOnMainThread(block: () -> Unit) {
    val queue = NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q")
    lateinit var work: DispatchWork
    work =
        DispatchWork {
            try {
                block()
            } finally {
                pendingMainThreadWork.remove(work)
            }
        }
    pendingMainThreadWork.add(work)
    dispatch.dispatch_async_f(queue, null, work)
}

private fun nsStringToKotlin(string: Pointer?): String? = string?.let { objc.objc_msgSend(it, objc.sel_registerName("UTF8String")) }?.getString(0)

/**
 * Lets mouse input fall through [window] on macOS. AWT does not expose its NSWindow without opening JDK internals, so
 * the window is found among `[NSApp windows]` by its title, then `setIgnoresMouseEvents:` is sent on the main thread.
 */
internal fun setMacClickThrough(
    window: Window,
    enabled: Boolean,
) {
    val title = (window as? Frame)?.title?.takeIf { it.isNotBlank() } ?: return
    runOnMainThread {
        runCatching {
            val app = objc.objc_msgSend(objc.objc_getClass("NSApplication") ?: return@runCatching, objc.sel_registerName("sharedApplication"))
                ?: return@runCatching
            val windows = objc.objc_msgSend(app, objc.sel_registerName("windows")) ?: return@runCatching
            val count = objcLong.objc_msgSend(windows, objc.sel_registerName("count"))
            val at = objc.sel_registerName("objectAtIndex:")
            for (index in 0 until count) {
                val candidate = objc.objc_msgSend(windows, at, index) ?: continue
                if (nsStringToKotlin(objc.objc_msgSend(candidate, objc.sel_registerName("title"))) == title) {
                    objc.objc_msgSend(candidate, objc.sel_registerName("setIgnoresMouseEvents:"), if (enabled) 1.toByte() else 0.toByte())
                    return@runCatching
                }
            }
        }.onFailure { Logger.w("WindowClickThrough", "Could not change click-through: ${it::class.simpleName}") }
    }
}
