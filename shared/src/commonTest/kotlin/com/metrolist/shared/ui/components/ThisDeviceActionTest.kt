package com.metrolist.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThisDeviceActionTest {
    @Test
    fun noRowWhenPlaybackIsAlreadyLocal() {
        assertNull(thisDeviceAction(castActive = false, peerActive = false))
    }

    @Test
    fun peerTargetIsPulledBackWithoutDisconnecting() {
        assertEquals(ThisDeviceAction.PULL_BACK, thisDeviceAction(castActive = false, peerActive = true))
    }

    @Test
    fun castTargetIsLeftByDisconnecting() {
        assertEquals(ThisDeviceAction.DISCONNECT_CAST, thisDeviceAction(castActive = true, peerActive = false))
    }
}
