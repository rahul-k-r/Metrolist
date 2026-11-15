package com.metrolist.desktop.utils

import com.metrolist.desktop.state.app.AppState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.shared.state.GlobalYouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Starts a library playlist from the tray without opening its page. */
internal object TrayPlaylistPlayer {
    fun play(playlistId: String) {
        AppState.scope.launch(Dispatchers.IO) {
            val songs =
                try {
                    GlobalYouTubeRepository.instance
                        .getPlaylistPlaybackPage(playlistId)
                        .songs
                        .filterNot { it.isUnavailable }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            val first = songs.firstOrNull() ?: return@launch
            withContext(Dispatchers.Main) { PlayerState.playTrack(first, songs) }
        }
    }
}
