package com.metrolist.desktop.state

import com.metrolist.desktop.state.app.PlayerPanelState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerPanelRestoreTest {
    @Test
    fun restoreReplacesWhateverWasOpen() {
        val panels = PlayerPanelState().apply { showQueue = true }
        panels.restore(queue = false, lyrics = true)
        assertFalse(panels.showQueue)
        assertTrue(panels.showLyrics)
    }

    @Test
    fun restoreWithNothingSavedClosesBoth() {
        val panels = PlayerPanelState().apply { canShowBoth = true }
        panels.showQueue = true
        panels.showLyrics = true
        panels.restore(queue = false, lyrics = false)
        assertFalse(panels.showQueue)
        assertFalse(panels.showLyrics)
    }

    @Test
    fun bothComeBackWhenTheyFitAndLyricsWinsWhenTheyDont() {
        val wide = PlayerPanelState().apply { canShowBoth = true }
        wide.restore(queue = true, lyrics = true)
        assertEquals(true to true, wide.showQueue to wide.showLyrics)

        val narrow = PlayerPanelState()
        narrow.restore(queue = true, lyrics = true)
        assertEquals(false to true, narrow.showQueue to narrow.showLyrics)
    }
}
