package com.metrolist.desktop.ui.components

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloatingLyricsBoundsTest {
    private val screen = Rectangle(0, 0, 1920, 1080)

    @Test
    fun roundTripsThroughText() {
        val bounds = FloatingLyricsBounds(100, 200, 460, 420)
        assertEquals(bounds, FloatingLyricsBounds.parse(bounds.format()))
    }

    @Test
    fun malformedTextIsRejected() {
        assertNull(FloatingLyricsBounds.parse(""))
        assertNull(FloatingLyricsBounds.parse("1,2,3"))
        assertNull(FloatingLyricsBounds.parse("a,b,c,d"))
        assertNull(FloatingLyricsBounds.parse("1,2,0,50"))
    }

    @Test
    fun windowOnAScreenIsReachable() {
        assertTrue(FloatingLyricsBounds(1400, 600, 460, 420).isReachableOn(listOf(screen)))
    }

    @Test
    fun windowLeftOnAnUnpluggedMonitorIsNotReachable() {
        assertFalse(FloatingLyricsBounds(2500, 100, 460, 420).isReachableOn(listOf(screen)))
    }

    @Test
    fun windowOnlyGrazingAnEdgeIsNotReachable() {
        assertFalse(FloatingLyricsBounds(1900, 100, 460, 420).isReachableOn(listOf(screen)))
    }
}
