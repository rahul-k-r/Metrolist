package com.metrolist.shared.deviceconnect

import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerDevice
import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerState

/** One device playback can be moved to. [name] is blank for this device, so the caller can label it. */
data class DeviceTarget(
    val id: String,
    val name: String,
    val isLocal: Boolean,
    val isCurrent: Boolean,
)

/**
 * This device plus every connected peer (by name), marking where audio plays now. Empty when no peer is
 * connected, so the tray shows nothing to switch to.
 */
fun deviceTargets(
    localDeviceId: String,
    sessionActive: Boolean,
    sessionTargetId: String?,
    peers: List<PeerDevice>,
): List<DeviceTarget> {
    if (localDeviceId.isEmpty()) return emptyList()
    val connected = peers.filter { it.state == PeerState.CONNECTED && it.id != localDeviceId }.sortedBy { it.name.lowercase() }
    if (connected.isEmpty()) return emptyList()
    val currentId = sessionTargetId?.takeIf { sessionActive }?.takeIf { id -> id == localDeviceId || connected.any { it.id == id } } ?: localDeviceId
    return buildList {
        add(DeviceTarget(localDeviceId, "", isLocal = true, isCurrent = currentId == localDeviceId))
        connected.forEach { add(DeviceTarget(it.id, it.name.ifBlank { it.model }, isLocal = false, isCurrent = currentId == it.id)) }
    }
}
