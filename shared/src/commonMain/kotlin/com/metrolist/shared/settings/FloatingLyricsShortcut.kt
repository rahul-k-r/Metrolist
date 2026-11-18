package com.metrolist.shared.settings

/**
 * A modifier+key combination stored as text such as "Ctrl+Shift+L". [key] is one upper-case letter, one digit or
 * F1..F12. A combination needs Ctrl, Alt or Meta, so a plain typing key can never become a global shortcut.
 */
data class FloatingLyricsShortcut(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean,
    val key: String,
) {
    val isValid: Boolean
        get() = (ctrl || alt || meta) && isSupportedKey(key)

    fun format(): String =
        buildList {
            if (ctrl) add("Ctrl")
            if (alt) add("Alt")
            if (shift) add("Shift")
            if (meta) add("Meta")
            add(key)
        }.joinToString("+")

    companion object {
        const val DEFAULT_TEXT = "Ctrl+Shift+L"
        val DEFAULT = FloatingLyricsShortcut(ctrl = true, alt = false, shift = true, meta = false, key = "L")

        fun isSupportedKey(key: String): Boolean =
            (key.length == 1 && (key[0] in 'A'..'Z' || key[0] in '0'..'9')) ||
                (key.length in 2..3 && key[0] == 'F' && key.drop(1).toIntOrNull() in 1..12)

        /** Null when [text] is not a valid combination. */
        fun parse(text: String): FloatingLyricsShortcut? {
            val parts = text.split('+').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val key = parts.last().uppercase()
            val modifiers = parts.dropLast(1).map { it.lowercase() }
            if (modifiers.any { it !in setOf("ctrl", "alt", "shift", "meta") }) return null
            val parsed =
                FloatingLyricsShortcut(
                    ctrl = "ctrl" in modifiers,
                    alt = "alt" in modifiers,
                    shift = "shift" in modifiers,
                    meta = "meta" in modifiers,
                    key = key,
                )
            return parsed.takeIf { it.isValid }
        }
    }
}
