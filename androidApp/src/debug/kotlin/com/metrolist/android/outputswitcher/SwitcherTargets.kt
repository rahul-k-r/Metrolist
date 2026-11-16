package com.metrolist.android.outputswitcher

import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerDevice
import com.metrolist.shared.deviceconnect.DeviceConnectManager.PeerState

internal const val ROUTE_ID_PREFIX = "metrolist-dc:"

internal data class SwitcherTarget(
    val routeId: String,
    val deviceId: String,
    val name: String,
)

/** Only connected peers can be transferred to (`remote.transferTo` needs a live link). */
internal fun List<PeerDevice>.toSwitcherTargets(): List<SwitcherTarget> =
    filter { it.state == PeerState.CONNECTED }
        .map { SwitcherTarget(ROUTE_ID_PREFIX + it.id, it.id, it.name.ifBlank { it.model.ifBlank { it.id } }) }
        .sortedBy { it.name.lowercase() }

internal fun routeIdToDeviceId(routeId: String?): String? =
    routeId?.takeIf { it.startsWith(ROUTE_ID_PREFIX) }?.removePrefix(ROUTE_ID_PREFIX)?.takeIf { it.isNotEmpty() }
