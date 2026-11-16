package com.metrolist.shared.deviceconnect

import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerDevice
import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrayDeviceTargetsTest {
    private fun peer(
        id: String,
        name: String,
        state: PeerState = PeerState.CONNECTED,
    ) = PeerDevice(id, name, "host", 1, state)

    @Test
    fun noConnectedPeerMeansNothingToSwitchTo() {
        assertTrue(deviceTargets("me", false, null, emptyList()).isEmpty())
        assertTrue(deviceTargets("me", false, null, listOf(peer("a", "Phone", PeerState.DISCOVERED))).isEmpty())
        assertTrue(deviceTargets("", false, null, listOf(peer("a", "Phone"))).isEmpty())
    }

    @Test
    fun playingLocallyMarksThisDeviceAndListsPeersByName() {
        val targets = deviceTargets("me", false, null, listOf(peer("b", "tablet"), peer("a", "Phone")))
        assertEquals(listOf("me", "a", "b"), targets.map { it.id })
        assertEquals(listOf(true, false, false), targets.map { it.isCurrent })
        assertTrue(targets.first().isLocal)
    }

    @Test
    fun followingAPeerMarksThatPeer() {
        val targets = deviceTargets("me", true, "a", listOf(peer("a", "Phone"), peer("b", "Tablet")))
        assertEquals(listOf(false, true, false), targets.map { it.isCurrent })
    }

    @Test
    fun sessionTargetingThisDeviceOrAVanishedPeerFallsBackToThisDevice() {
        assertTrue(deviceTargets("me", true, "me", listOf(peer("a", "Phone"))).first().isCurrent)
        assertTrue(deviceTargets("me", true, "gone", listOf(peer("a", "Phone"))).first().isCurrent)
    }
}
