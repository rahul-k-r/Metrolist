@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrolist.shared.deviceconnect.DataSyncStatus
import com.metrolist.shared.deviceconnect.DeviceConnectDataSync
import com.metrolist.shared.deviceconnect.DeviceConnectManager
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.sync_settings_apply
import com.metrolist.shared.generated.resources.sync_settings_applied
import com.metrolist.shared.generated.resources.sync_settings_decline
import com.metrolist.shared.generated.resources.sync_settings_failed
import com.metrolist.shared.generated.resources.sync_settings_hint
import com.metrolist.shared.generated.resources.sync_settings_incoming_body
import com.metrolist.shared.generated.resources.sync_settings_incoming_title
import com.metrolist.shared.generated.resources.sync_settings_pull
import com.metrolist.shared.generated.resources.sync_settings_push
import com.metrolist.shared.generated.resources.sync_settings_sent
import com.metrolist.shared.generated.resources.sync_settings_title
import org.jetbrains.compose.resources.stringResource

/**
 * One-time settings sync with a connected device: send this device's settings, or fetch the other's.
 * With several connected devices the user picks which one first.
 */
@Composable
internal fun SettingsSyncSection(
    dataSync: DeviceConnectDataSync,
    peers: List<DeviceConnectManager.PeerDevice>,
) {
    val dataPeerIds by dataSync.connectedPeers.collectAsState()
    val available = peers.filter { it.id in dataPeerIds }
    val incoming by dataSync.incomingPush.collectAsState()
    val status by dataSync.status.collectAsState()
    val names = peers.associate { it.id to it.name }

    incoming?.let { push ->
        AlertDialog(
            onDismissRequest = dataSync::rejectIncoming,
            title = { Text(stringResource(Res.string.sync_settings_incoming_title, names[push.fromDeviceId] ?: push.fromDeviceId)) },
            text = { Text(stringResource(Res.string.sync_settings_incoming_body, push.changedCount)) },
            confirmButton = { TextButton(onClick = dataSync::acceptIncoming) { Text(stringResource(Res.string.sync_settings_apply)) } },
            dismissButton = { TextButton(onClick = dataSync::rejectIncoming) { Text(stringResource(Res.string.sync_settings_decline)) } },
        )
    }
    if (available.isEmpty()) return

    var chosen by remember { mutableStateOf<String?>(null) }
    val target = available.firstOrNull { it.id == chosen } ?: available.singleOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.sync_settings_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        if (available.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { peer ->
                    FilterChip(
                        selected = target?.id == peer.id,
                        onClick = {
                            chosen = peer.id
                            dataSync.clearStatus()
                        },
                        label = { Text(peer.name) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { target?.let { dataSync.pushSettings(it.id) } },
                enabled = target != null,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(Res.string.sync_settings_push)) }
            OutlinedButton(
                onClick = { target?.let { dataSync.pullSettings(it.id) } },
                enabled = target != null,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(Res.string.sync_settings_pull)) }
        }
        val message =
            when (val current = status) {
                is DataSyncStatus.Applied -> stringResource(Res.string.sync_settings_applied, current.changedCount)
                is DataSyncStatus.Sent -> stringResource(Res.string.sync_settings_sent)
                is DataSyncStatus.Failed -> stringResource(Res.string.sync_settings_failed)
                null -> stringResource(Res.string.sync_settings_hint)
            }
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (status is DataSyncStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
