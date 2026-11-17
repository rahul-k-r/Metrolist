/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.metrolist.desktop.state.playback.ListenTogetherState
import com.metrolist.desktop.state.playback.LyricsState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.desktop.ui.DesktopDensityProvider
import com.metrolist.desktop.window.WindowVisibility
import com.metrolist.desktop.window.setWindowHiddenFromCapture
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.floating_lyrics
import com.metrolist.shared.generated.resources.floating_lyrics_hide_from_capture
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Always-on-top lyrics window. It lives outside the main window, so it keeps working while the app sits in the tray.
 * A native title bar is used on purpose: it gives drag, resize and close for free on every platform.
 */
@Composable
fun FloatingLyricsWindow() {
    if (!PlayerState.showFloatingLyrics) return

    val windowState =
        rememberWindowState(
            position = WindowPosition.Aligned(Alignment.BottomEnd),
            size = DpSize(460.dp, 420.dp),
        )
    val hideFromCapture = Settings.shared.floatingLyricsHideFromCapture

    Window(
        onCloseRequest = { PlayerState.showFloatingLyrics = false },
        title = stringResource(Res.string.floating_lyrics),
        state = windowState,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(window, hideFromCapture) { setWindowHiddenFromCapture(window, hideFromCapture) }
        val track = PlayerState.currentTrack
        LaunchedEffect(track?.id, Settings.shared.lyricsProviderOrder, Settings.shared.lyricsEnabledProviders) {
            if (track != null) LyricsState.fetchLyrics(track)
        }
        DesktopDensityProvider {
            WindowVisibility(window) {
                val colors = MaterialTheme.colorScheme
                Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                            DesktopLyricsContent(textColor = colors.onSurface)
                        }
                        if (!ListenTogetherState.isListenTogetherGuest()) {
                            FloatingLyricsControls()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                stringResource(Res.string.floating_lyrics_hide_from_capture),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Switch(
                                checked = hideFromCapture,
                                onCheckedChange = { Settings.shared.floatingLyricsHideFromCapture = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingLyricsControls() {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        MiniplayerProgress(
            colorScheme = colors,
            enabled = true,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { PlayerState.skipPrevious() }, enabled = PlayerState.canSkipPrevious) {
                Icon(MetrolistIcons.Rounded.SkipPrevious, stringResource(Res.string.previous), modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { PlayerState.togglePlay() }, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = if (PlayerState.isPlaying) MetrolistIcons.Rounded.Pause else MetrolistIcons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (PlayerState.isPlaying) Res.string.pause else Res.string.play),
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { PlayerState.skipNext() }) {
                Icon(MetrolistIcons.Rounded.SkipNext, stringResource(Res.string.next), modifier = Modifier.size(28.dp))
            }
        }
    }
}
