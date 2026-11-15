package com.metrolist.desktop.state

import com.metrolist.desktop.state.app.PlayerPresentationState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerPresentationStateTest {
    @Test
    fun paneAndFullViewAreMutuallyExclusive() {
        val state = PlayerPresentationState()

        state.enterPaneExpanded()
        assertTrue(state.isPaneExpanded)
        assertFalse(state.isExpanded)

        state.enterExpanded()
        assertTrue(state.isExpanded)
        assertFalse(state.isPaneExpanded)

        state.enterPaneExpanded()
        assertTrue(state.isPaneExpanded)
        assertFalse(state.isExpanded)
    }

    @Test
    fun closingThePlayerLeavesNeitherOpen() {
        val state = PlayerPresentationState()
        state.enterPaneExpanded()

        state.closePlayer()

        assertFalse(state.isPaneExpanded)
        assertFalse(state.isExpanded)
    }
}
