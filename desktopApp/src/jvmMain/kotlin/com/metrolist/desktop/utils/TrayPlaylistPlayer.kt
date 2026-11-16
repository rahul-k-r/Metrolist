package com.metrolist.desktop.utils

import com.metrolist.desktop.state.app.AppState
import com.metrolist.desktop.state.content.LibraryContentState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.desktop.ui.components.loadMediaSongs
import com.metrolist.shared.model.UserPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plays, queues next or appends a library playlist from the tray without opening its page. */
internal object TrayPlaylistPlayer {
    const val MODE_PLAY = 0
    const val MODE_PLAY_NEXT = 1
    const val MODE_ADD_TO_QUEUE = 2

    fun run(
        playlist: UserPlaylist,
        mode: Int,
    ) {
        AppState.scope.launch(Dispatchers.IO) {
            // The same loader the playlist menus use, so a long playlist or mix arrives complete, not as its first page.
            val songs =
                try {
                    loadMediaSongs(LibraryContentState.playlistItemFor(playlist), emptyList()).filterNot { it.isUnavailable }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            val first = songs.firstOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                when (mode) {
                    MODE_PLAY_NEXT -> PlayerState.playNext(songs)
                    MODE_ADD_TO_QUEUE -> PlayerState.addToQueue(songs)
                    else -> PlayerState.playTrack(first, songs)
                }
            }
        }
    }
}
