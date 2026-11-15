package com.metrolist.desktop.state.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class PlayerPresentationState {
    var isExpanded by mutableStateOf(false)
        private set

    /** Expanded player shown inside the main pane, keeping the sidebar and queue visible. */
    var isPaneExpanded by mutableStateOf(false)
        private set
    var isFullscreen by mutableStateOf(false)
    var showVideoFullscreen by mutableStateOf(false)

    fun enterExpanded() {
        isPaneExpanded = false
        isExpanded = true
    }

    fun exitExpanded() {
        isExpanded = false
    }

    fun enterPaneExpanded() {
        isExpanded = false
        isPaneExpanded = true
    }

    fun exitPaneExpanded() {
        isPaneExpanded = false
    }

    fun toggleFullscreenVideo() {
        enterExpanded()
        showVideoFullscreen = !showVideoFullscreen
    }

    fun closePlayer() {
        showVideoFullscreen = false
        exitPaneExpanded()
        exitExpanded()
    }
}
