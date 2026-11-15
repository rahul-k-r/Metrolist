package com.metrolist.shared.deviceconnect

import com.metrolist.shared.cast.CastConnectionStatus
import com.metrolist.shared.cast.CastDevice
import com.metrolist.shared.cast.CastProtocol
import com.metrolist.shared.cast.CastState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayingOnDeviceTest {
    private fun peer(
        id: String,
        name: String,
        state: DeviceConnectManager.PeerState = DeviceConnectManager.PeerState.CONNECTED,
    ) = DeviceConnectManager.PeerDevice(id = id, name = name, host = "h", port = 1, state = state)

    @Test
    fun nothingActiveIsNull() {
        assertNull(resolvePlayingOnDevice(null, null))
        assertNull(resolvePlayingOnDevice(" ", ""))
    }

    @Test
    fun castWinsOverFollowing() {
        assertEquals("TV", resolvePlayingOnDevice("TV", "Phone"))
        assertEquals("Phone", resolvePlayingOnDevice(null, "Phone"))
        assertEquals("Phone", resolvePlayingOnDevice("", "Phone"))
    }

    @Test
    fun followingIgnoresLocalAndUnknownTargets() {
        val peers = listOf(peer("a", "Phone"), peer("b", "Down", DeviceConnectManager.PeerState.DISCONNECTED))
        assertEquals("Phone", followingPeerNameOrNull("a", "me", peers))
        assertNull(followingPeerNameOrNull("me", "me", peers))
        assertNull(followingPeerNameOrNull(null, "me", peers))
        assertNull(followingPeerNameOrNull("b", "me", peers))
        assertNull(followingPeerNameOrNull("zzz", "me", peers))
    }

    @Test
    fun castNameRequiresConnectionAndEnabled() {
        val device = CastDevice("d", "Living room", CastProtocol.entries.first(), "h", 1)
        val connected = CastState(status = CastConnectionStatus.entries.first { it != CastConnectionStatus.DISCONNECTED }, device = device)
        assertEquals("Living room", castDeviceNameOrNull(connected))
        assertNull(castDeviceNameOrNull(connected, castEnabled = false))
        assertNull(castDeviceNameOrNull(CastState(device = device)))
    }
}
