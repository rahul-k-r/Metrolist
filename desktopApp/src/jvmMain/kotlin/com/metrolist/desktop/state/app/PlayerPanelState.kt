package com.metrolist.desktop.state.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class PlayerPanelState {
    private var queueVisible by mutableStateOf(false)
    private var lyricsVisible by mutableStateOf(false)
    private var lyricsOpenedLast = false

    var canShowBoth = false
        set(value) {
            field = value
            if (!value && queueVisible && lyricsVisible) {
                if (lyricsOpenedLast) queueVisible = false else lyricsVisible = false
            }
        }

    /** Replaces the open panels; when both are asked for but only one fits, lyrics wins. */
    fun restore(
        queue: Boolean,
        lyrics: Boolean,
    ) {
        queueVisible = false
        lyricsVisible = false
        if (queue) showQueue = true
        if (lyrics) showLyrics = true
    }

    var showQueue: Boolean
        get() = queueVisible
        set(value) {
            queueVisible = value
            if (value) {
                lyricsOpenedLast = false
                if (!canShowBoth) lyricsVisible = false
            }
        }

    var showLyrics: Boolean
        get() = lyricsVisible
        set(value) {
            lyricsVisible = value
            if (value) {
                lyricsOpenedLast = true
                if (!canShowBoth) queueVisible = false
            }
        }
}
