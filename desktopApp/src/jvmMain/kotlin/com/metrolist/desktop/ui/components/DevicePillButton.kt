@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.desktop.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.metrolist.desktop.deviceconnect.DesktopDeviceConnect
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.shared.deviceconnect.castDeviceNameOrNull
import com.metrolist.shared.deviceconnect.followingPeerNameOrNull
import com.metrolist.shared.deviceconnect.resolvePlayingOnDevice
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cast
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

/** Opens the device panel; becomes a pill naming the target while audio plays on another device. */
@Composable
fun DevicePillButton(modifier: Modifier = Modifier) {
    val castState by PlayerState.castManager.state.collectAsState()
    val syncState by DesktopDeviceConnect.manager.remote.state
        .collectAsState()
    val dcState by DesktopDeviceConnect.manager.state
        .collectAsState()
    val target =
        resolvePlayingOnDevice(
            castDeviceNameOrNull(castState, Settings.shared.castEnabled),
            followingPeerNameOrNull(syncState.targetId, dcState.localDeviceId, dcState.peers),
        )
    val label = stringResource(Res.string.cast)

    if (target == null) {
        FilledTonalIconButton(
            onClick = UiState::toggleDeviceConnectPanel,
            modifier = modifier.size(40.dp),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(MetrolistIcons.Outlined.Devices, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    } else {
        val description = "$label: $target"
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
                modifier
                    .widthIn(max = 160.dp)
                    .clickable(onClick = UiState::toggleDeviceConnectPanel)
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                    },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(MetrolistIcons.Outlined.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = target,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(),
                )
            }
        }
    }
}
