package com.metrolist.desktop.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniplayerSizingTest {
    @Test
    fun heightIsClampedToTheQueueRange() {
        assertEquals(MiniplayerMinQueueHeight, clampMiniplayerHeight(10.dp))
        assertEquals(MiniplayerMaxQueueHeight, clampMiniplayerHeight(5000.dp))
        assertEquals(600.dp, clampMiniplayerHeight(600.dp))
    }

    @Test
    fun growingWindowIsPulledBackAboveTheScreenBottom() {
        assertEquals(500, keepVerticallyOnScreen(y = 500, height = 300, top = 0, bottom = 1080))
        assertEquals(520, keepVerticallyOnScreen(y = 900, height = 560, top = 0, bottom = 1080))
        assertEquals(40, keepVerticallyOnScreen(y = 10, height = 300, top = 40, bottom = 1080))
        // Taller than the screen: the title edge stays reachable.
        assertEquals(0, keepVerticallyOnScreen(y = 300, height = 2000, top = 0, bottom = 1080))
    }

    @Test
    fun queueHeightLeavesRoomBeyondTheCompactPlayer() {
        assertTrue(MiniplayerMinQueueHeight > MiniplayerCompactHeight)
    }
}
