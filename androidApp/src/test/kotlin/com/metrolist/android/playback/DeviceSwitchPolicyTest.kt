package com.metrolist.android.playback

import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerDevice
import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSwitchPolicyTest {
    private fun peer(
        id: String,
        name: String,
        state: PeerState = PeerState.CONNECTED,
    ) = PeerDevice(id, name, "host", 1, state)

    @Test
    fun offersFirstConnectedPeerWhilePlayingLocally() {
        val action = deviceSwitchAction("me", false, null, listOf(peer("b", "Zed"), peer("a", "Desk"), peer("c", "Off", PeerState.DISCOVERED)))
        assertEquals(DeviceSwitchAction("a", "Play on Desk"), action)
    }

    @Test
    fun offersThisPhoneWhilePlayingOnAnotherDevice() {
        val action = deviceSwitchAction("me", true, "a", listOf(peer("a", "Desk")))
        assertEquals(DeviceSwitchAction("me", "Play on this phone"), action)
    }

    @Test
    fun treatsASessionTargetingThisPhoneAsLocal() {
        val action = deviceSwitchAction("me", true, "me", listOf(peer("a", "Desk")))
        assertEquals(DeviceSwitchAction("a", "Play on Desk"), action)
    }

    @Test
    fun offersNothingWithoutAConnectedPeer() {
        assertNull(deviceSwitchAction("me", false, null, listOf(peer("a", "Desk", PeerState.DISCONNECTED))))
        assertNull(deviceSwitchAction("", false, null, listOf(peer("a", "Desk"))))
    }
}
