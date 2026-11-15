package com.metrolist.desktop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.state.app.DesktopNavigation
import com.metrolist.desktop.state.content.LibraryContentState
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.playlists
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.navigation.Screen
import org.jetbrains.compose.resources.stringResource

/** The signed-in user's playlists, listed under the navigation items while the sidebar is expanded. */
@Composable
fun SidebarPlaylists(isExpanded: Boolean) {
    val playlists = LibraryContentState.userPlaylists
    if (!isExpanded || playlists.isEmpty()) return
    Column {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Text(
            text = stringResource(Res.string.playlists),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        playlists.forEach { playlist ->
            NavigationDrawerItem(
                selected = false,
                onClick = { DesktopNavigation.navigate(Screen.Playlist(playlist.id)) },
                icon = { Icon(MetrolistIcons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                label = {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
