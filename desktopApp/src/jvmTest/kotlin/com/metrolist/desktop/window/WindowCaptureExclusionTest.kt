package com.metrolist.desktop.window

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowCaptureExclusionTest {
    @Test
    fun hiddenWindowsUseExcludeFromCaptureNotMonitorAffinity() {
        // WDA_MONITOR (0x1) would show a black window to recorders; only 0x11 removes it from the capture.
        assertEquals(0x11, displayAffinityFor(hideFromCapture = true))
        assertEquals(0x0, displayAffinityFor(hideFromCapture = false))
    }
}
