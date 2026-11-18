package com.metrolist.desktop.ui.components

import java.awt.Rectangle

private const val MIN_VISIBLE_WIDTH = 120
private const val MIN_VISIBLE_HEIGHT = 40

/** Where the floating lyrics window was left, in dp, stored as "x,y,width,height". */
internal data class FloatingLyricsBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun format(): String = "$x,$y,$width,$height"

    /** True when enough of the window overlaps a screen to grab it, so an unplugged monitor cannot strand it off-screen. */
    fun isReachableOn(screens: List<Rectangle>): Boolean {
        val window = Rectangle(x, y, width, height)
        return screens.any {
            val overlap = it.intersection(window)
            overlap.width >= MIN_VISIBLE_WIDTH && overlap.height >= MIN_VISIBLE_HEIGHT
        }
    }

    companion object {
        fun parse(text: String): FloatingLyricsBounds? {
            val parts = text.split(',').map { it.trim().toIntOrNull() ?: return null }
            if (parts.size != 4 || parts[2] <= 0 || parts[3] <= 0) return null
            return FloatingLyricsBounds(parts[0], parts[1], parts[2], parts[3])
        }
    }
}
