package com.metrolist.android.outputswitcher

import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerDevice
import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwitcherTargetsTest {
    private fun peer(id: String, name: String, state: PeerState) = PeerDevice(id, name, "10.0.0.1", 1234, state)

    @Test
    fun onlyConnectedPeersBecomeTargets_sortedByName() {
        val targets =
            listOf(
                peer("b", "Zeta", PeerState.CONNECTED),
                peer("a", "alpha", PeerState.CONNECTED),
                peer("c", "Gone", PeerState.DISCOVERED),
            ).toSwitcherTargets()
        assertEquals(listOf("alpha", "Zeta"), targets.map { it.name })
        assertEquals("${ROUTE_ID_PREFIX}a", targets.first().routeId)
    }

    @Test
    fun routeIdRoundTrips() {
        assertEquals("dev-1", routeIdToDeviceId(ROUTE_ID_PREFIX + "dev-1"))
        assertNull(routeIdToDeviceId("other:dev-1"))
        assertNull(routeIdToDeviceId(ROUTE_ID_PREFIX))
        assertNull(routeIdToDeviceId(null))
    }
}
