package com.metrolist.shared.deviceconnect

import com.metrolist.shared.cast.CastConnectionStatus
import com.metrolist.shared.cast.CastState

/** Name of the device audio is routed to, or null when it plays on this device. Cast wins over following. */
fun resolvePlayingOnDevice(
    castDeviceName: String?,
    followingPeerName: String?,
): String? = castDeviceName?.takeIf { it.isNotBlank() } ?: followingPeerName?.takeIf { it.isNotBlank() }

fun castDeviceNameOrNull(
    state: CastState,
    castEnabled: Boolean = true,
): String? = state.device?.name?.takeIf { castEnabled && state.status != CastConnectionStatus.DISCONNECTED }

fun followingPeerNameOrNull(
    targetId: String?,
    localDeviceId: String?,
    peers: List<DeviceConnectManager.PeerDevice>,
): String? {
    if (targetId == null || targetId == localDeviceId) return null
    return peers.firstOrNull { it.id == targetId && it.state == DeviceConnectManager.PeerState.CONNECTED }?.name
}
