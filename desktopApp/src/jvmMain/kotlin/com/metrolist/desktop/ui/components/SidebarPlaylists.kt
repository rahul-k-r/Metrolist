package com.metrolist.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.state.app.DesktopNavigation
import com.metrolist.desktop.state.content.LibraryContentState
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.playlists
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.model.UserPlaylist
import com.metrolist.shared.ui.components.PopupContent
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.menu.cursorContextMenu
import com.metrolist.shared.ui.menu.rememberCursorContextMenuState
import com.metrolist.shared.ui.navigation.Screen
import org.jetbrains.compose.resources.stringResource

/**
 * The signed-in user's playlists under the navigation items: art plus name when the sidebar is expanded,
 * art only when it is collapsed. Right-click opens the same menu as the Library page.
 */
@Composable
fun SidebarPlaylists(isExpanded: Boolean) {
    val playlists = LibraryContentState.userPlaylists
    if (playlists.isEmpty()) return
    var playlistToRename by remember { mutableStateOf<PlaylistItem?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistItem?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isExpanded) 0.dp else 4.dp),
    ) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (isExpanded) {
            Text(
                text = stringResource(Res.string.playlists),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        playlists.forEach { playlist ->
            key(playlist.id) {
                SidebarPlaylistEntry(
                    playlist = playlist,
                    isExpanded = isExpanded,
                    onRename = { playlistToRename = it },
                    onDelete = { playlistToDelete = it },
                )
            }
        }
    }
    PopupContent(playlistToRename) { PlaylistRenameDialog(it) { playlistToRename = null } }
    PopupContent(playlistToDelete) { PlaylistDeleteDialog(it) { playlistToDelete = null } }
}

@Composable
private fun SidebarPlaylistEntry(
    playlist: UserPlaylist,
    isExpanded: Boolean,
    onRename: (PlaylistItem) -> Unit,
    onDelete: (PlaylistItem) -> Unit,
) {
    val contextMenu = rememberCursorContextMenuState()
    val open = { DesktopNavigation.navigate(Screen.Playlist(playlist.id)) }
    if (isExpanded) {
        NavigationDrawerItem(
            selected = false,
            onClick = open,
            icon = { SidebarPlaylistArt(playlist.thumbnailUrl, 32.dp) },
            label = {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).cursorContextMenu(contextMenu),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .cursorContextMenu(contextMenu)
                    .clickable(onClick = open)
                    .padding(4.dp)
                    .semantics { contentDescription = playlist.name },
        ) {
            SidebarPlaylistArt(playlist.thumbnailUrl, 48.dp)
        }
    }
    if (contextMenu.expanded) {
        // Resolved when the menu opens: the item carries the edit rights the sidebar entry does not.
        val item = remember(playlist.id) { LibraryContentState.playlistItemFor(playlist) }
        PlaylistContextMenu(
            playlist = item,
            expanded = contextMenu.expanded,
            offset = contextMenu.offset,
            pinnedId = item.id,
            onRename = { onRename(item) }.takeIf { item.isEditable },
            onDelete = { onDelete(item) },
            onDismiss = contextMenu::dismiss,
        )
    }
}

@Composable
private fun SidebarPlaylistArt(
    url: String?,
    size: Dp,
) {
    val shape = RoundedCornerShape(if (size > 40.dp) 10.dp else 6.dp)
    Box(
        modifier = Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                MetrolistIcons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.size(size / 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(url = url, modifier = Modifier.size(size), shape = shape)
        }
    }
}
