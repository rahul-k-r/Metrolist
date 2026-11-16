package com.metrolist.android.playback

import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerDevice
import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerState

internal data class DeviceSwitchAction(
    val targetDeviceId: String,
    val label: String,
)

/**
 * The one transfer the notification button offers: back to this phone while playing elsewhere, otherwise to the
 * first connected peer. Null when there is nothing to switch to.
 */
internal fun deviceSwitchAction(
    localDeviceId: String,
    sessionActive: Boolean,
    sessionTargetId: String?,
    peers: List<PeerDevice>,
): DeviceSwitchAction? {
    if (localDeviceId.isEmpty()) return null
    if (sessionActive && sessionTargetId != null && sessionTargetId != localDeviceId) {
        return DeviceSwitchAction(localDeviceId, "Play on this phone")
    }
    val peer =
        peers
            .filter { it.state == PeerState.CONNECTED }
            .minByOrNull { it.name.lowercase() }
            ?: return null
    return DeviceSwitchAction(peer.id, "Play on ${peer.name.ifBlank { peer.model.ifBlank { "other device" } }}")
}
