package com.metrolist.desktop.utils

import com.metrolist.desktop.state.app.AppState
import com.metrolist.desktop.state.content.LibraryContentState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.desktop.ui.components.loadMediaSongs
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.UserPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plays, queues next or appends what the user picks in the tray, without opening the app. */
internal object TrayPlaylistPlayer {
    const val MODE_PLAY = 0
    const val MODE_PLAY_NEXT = 1
    const val MODE_ADD_TO_QUEUE = 2

    /** [startFrom] is the song a fresh queue begins with; [songs] is everything the queue receives. */
    fun apply(
        songs: List<SongItem>,
        mode: Int,
        startFrom: SongItem? = songs.firstOrNull(),
    ) {
        val first = startFrom ?: return
        // With nothing queued there is nothing to go next to or after, so every mode starts a fresh queue.
        val fresh = mode == MODE_PLAY || PlayerState.queue.items.value.isEmpty()
        when {
            fresh -> PlayerState.playTrack(first, songs)
            mode == MODE_PLAY_NEXT -> PlayerState.playNext(songs)
            else -> PlayerState.addToQueue(songs)
        }
    }

    /** A single song from the recently played list; a fresh queue gets the whole list so playback continues through it. */
    fun applySong(
        song: SongItem,
        recent: List<SongItem>,
        mode: Int,
    ) {
        if (mode == MODE_PLAY || PlayerState.queue.items.value.isEmpty()) {
            PlayerState.playTrack(song, recent)
        } else {
            apply(listOf(song), mode)
        }
    }

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
            if (songs.isEmpty()) return@launch
            withContext(Dispatchers.Main) { apply(songs, mode) }
        }
    }
}
