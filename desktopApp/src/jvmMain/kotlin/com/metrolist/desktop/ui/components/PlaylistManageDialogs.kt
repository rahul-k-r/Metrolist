package com.metrolist.desktop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.metrolist.desktop.state.content.LibraryContentState
import com.metrolist.desktop.ui.screens.PlaylistNameDialog
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_cannot_be_undone
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.delete
import com.metrolist.shared.generated.resources.delete_playlist_confirm
import com.metrolist.shared.generated.resources.error_occurred
import com.metrolist.shared.generated.resources.playlist_rename_action
import com.metrolist.shared.generated.resources.remove_from_library
import com.metrolist.shared.generated.resources.rename_playlist_dialog_title
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.ui.components.AlertDialog
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlaylistRenameDialog(
    playlist: PlaylistItem,
    onDismiss: () -> Unit,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.title) }
    PlaylistNameDialog(
        title = stringResource(Res.string.rename_playlist_dialog_title),
        confirmActionLabel = stringResource(Res.string.playlist_rename_action),
        value = name,
        onValueChange = { name = it },
        onDismiss = onDismiss,
    ) {
        LibraryContentState.renamePlaylist(playlist, name) {}
        onDismiss()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaylistDeleteDialog(
    playlist: PlaylistItem,
    onDismiss: () -> Unit,
) {
    // A playlist the user owns is deleted; anything else is only removed from the library.
    val deletesPlaylist = playlist.isLocal || playlist.isEditable
    var failed by remember(playlist.id) { mutableStateOf(false) }
    val cancelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(playlist.id) { cancelFocusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deletesPlaylist) stringResource(Res.string.delete_playlist_confirm) else playlist.title) },
        text = {
            Column {
                Text(stringResource(if (deletesPlaylist) Res.string.action_cannot_be_undone else Res.string.remove_from_library))
                if (failed) Text(stringResource(Res.string.error_occurred), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    LibraryContentState.deletePlaylist(playlist) { result ->
                        result.onSuccess { onDismiss() }
                        result.onFailure { failed = true }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(if (deletesPlaylist) Res.string.delete else Res.string.remove_from_library))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancelFocusRequester),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
