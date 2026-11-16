package com.metrolist.desktop.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.WindowState
import com.metrolist.desktop.state.app.DesktopNavigation
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.state.content.LibraryContentState
import com.metrolist.shared.generated.resources.add_to_queue
import com.metrolist.shared.generated.resources.play_next
import com.metrolist.shared.generated.resources.playlists
import com.metrolist.shared.generated.resources.tray_recently_played
import kotlinx.coroutines.flow.flowOf
import com.metrolist.desktop.state.playback.ListenTogetherState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.tray_by
import com.metrolist.shared.generated.resources.tray_hide_window
import com.metrolist.shared.generated.resources.tray_next_track
import com.metrolist.shared.generated.resources.tray_pause
import com.metrolist.shared.generated.resources.tray_play
import com.metrolist.shared.generated.resources.tray_previous_track
import com.metrolist.shared.generated.resources.tray_quit_metrolist
import com.metrolist.shared.generated.resources.tray_settings
import com.metrolist.shared.generated.resources.tray_show_miniplayer
import com.metrolist.shared.generated.resources.tray_show_window
import com.metrolist.shared.generated.resources.tray_tooltip_paused
import com.metrolist.shared.generated.resources.tray_tooltip_playing
import com.metrolist.shared.generated.resources.tray_unknown
import com.metrolist.shared.generated.resources.tray_unknown_title
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.displayName
import com.metrolist.shared.ui.navigation.Screen
import com.metrolist.shared.utils.OsUtil
import dev.nucleusframework.composenativetray.lib.mac.MacOSMenuBarThemeDetector
import dev.nucleusframework.composenativetray.tray.api.Tray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

private const val TRAY_RECENT_LIMIT = 8
private const val TRAY_PLAYLIST_LIMIT = 15

object ComposeTrayManager {
    private val clickScheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "tray-click").apply { isDaemon = true } }
    private var windowState: WindowState? = null
    private var mainWindow: Window? = null
    private var onQuit: (() -> Unit)? = null
    private var isWindowVisible = mutableStateOf(true)
    internal val windowVisible: Boolean get() = isWindowVisible.value
    private val trayEnabled = mutableStateOf(true)
    private var trayDisposed = CompletableDeferred(Unit)

    private var isDarkTheme: Boolean = false

    private var useThemeColors: Boolean = false
    private var themeBgColor: Color? = null
    private var themeFgColor: Color? = null
    private var themeBorderColor: Color? = null

    fun initialize(
        mainWindowState: WindowState,
        mainWindow: Window,
        onQuit: () -> Unit,
    ) {
        windowState = mainWindowState
        this.mainWindow = mainWindow
        this.onQuit = onQuit
        trayDisposed = CompletableDeferred()
        trayEnabled.value = true
    }

    fun setThemeColors(
        bgArgb: Int,
        fgArgb: Int,
        borderArgb: Int,
        dark: Boolean,
    ) {
        try {
            useThemeColors = true
            themeBgColor = Color(bgArgb)
            themeFgColor = Color(fgArgb)
            themeBorderColor = Color(borderArgb)
            isDarkTheme = dark
        } catch (_: Exception) {
        }
    }

    fun toggleWindow() {
        if (isWindowVisible.value) {
            hideWindow()
        } else {
            showWindow()
        }
    }

    fun showWindow() {
        EventQueue.invokeLater {
            // Disposal can precede tray cleanup; a disposed Compose window cannot be shown again.
            val window = mainWindow?.takeIf { it.isDisplayable } ?: return@invokeLater
            windowState?.let { state ->
                state.isMinimized = false
                isWindowVisible.value = true
            }
            if (window is Frame && (window.extendedState and Frame.ICONIFIED) != 0) {
                window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
            }
            window.isVisible = true
            if (OsUtil.isMac) {
                runCatching {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().requestForeground(true)
                    }
                }
            }
            window.toFront()
            window.requestFocus()
        }
    }

    fun hideWindow() {
        EventQueue.invokeLater {
            val window = mainWindow?.takeIf { it.isDisplayable } ?: return@invokeLater
            isWindowVisible.value = false
            window.isVisible = false
        }
    }

    fun setWindowVisible(visible: Boolean) {
        isWindowVisible.value = visible
    }

    @Composable
    private fun rememberMacOsMenuBarIsDark(): Boolean {
        var isDark by remember {
            mutableStateOf(runCatching { MacOSMenuBarThemeDetector.isDark() }.getOrDefault(true))
        }
        DisposableEffect(Unit) {
            val listener =
                java.util.function.Consumer<Boolean> { dark ->
                    isDark = dark
                }
            runCatching { MacOSMenuBarThemeDetector.registerListener(listener) }
            onDispose {
                runCatching { MacOSMenuBarThemeDetector.removeListener(listener) }
            }
        }
        return isDark
    }

    @Composable
    fun TrayIcon() {
        if (!trayEnabled.value) return
        val mediaControls by PlayerState.mediaControls.collectAsState()
        val isPlaying = PlayerState.isPlaying
        val currentTrack = PlayerState.currentTrack
        val isListenTogetherGuest = ListenTogetherState.isListenTogetherGuest()

        val isMacDarkMenu = if (OsUtil.isMac) rememberMacOsMenuBarIsDark() else true

        @Suppress("DEPRECATION")
        val icon = painterResource(if (isMacDarkMenu) "logo.png" else "logo_dark.png")

        val strUnknownTitle = stringResource(Res.string.tray_unknown_title)
        val strUnknown = stringResource(Res.string.tray_unknown)
        val strPlay = stringResource(Res.string.tray_play)
        val strPause = stringResource(Res.string.tray_pause)
        val strNextTrack = stringResource(Res.string.tray_next_track)
        val strPreviousTrack = stringResource(Res.string.tray_previous_track)
        val strHideWindow = stringResource(Res.string.tray_hide_window)
        val strShowWindow = stringResource(Res.string.tray_show_window)
        val strShowMiniplayer = stringResource(Res.string.tray_show_miniplayer)
        val strSettings = stringResource(Res.string.tray_settings)
        val strQuitMetrolist = stringResource(Res.string.tray_quit_metrolist)
        val strTrayByTemplate = stringResource(Res.string.tray_by)
        val strRecentlyPlayed = stringResource(Res.string.tray_recently_played)
        val strPlaylists = stringResource(Res.string.playlists)
        val strPlayNext = stringResource(Res.string.play_next)
        val strAddToQueue = stringResource(Res.string.add_to_queue)
        val recentFlow = remember { runCatching { TrayRecentSongs.flow(TRAY_RECENT_LIMIT) }.getOrElse { flowOf(emptyList()) } }
        val recentSongs by recentFlow.collectAsState(emptyList())
        val libraryPlaylists = LibraryContentState.userPlaylists
        var playlistMode by remember { mutableStateOf(Settings.shared.desktopTrayPlaylistMode) }

        val playingText =
            currentTrack?.let { track ->
                val artistStr = track.artists.joinToString { it.displayName }.takeIf { it.isNotBlank() } ?: strUnknown
                stringResource(Res.string.tray_tooltip_playing, track.title.takeIf { it.isNotBlank() } ?: strUnknownTitle, artistStr)
            }
        val pausedText =
            currentTrack?.let { track ->
                val artistStr = track.artists.joinToString { it.displayName }.takeIf { it.isNotBlank() } ?: strUnknown
                stringResource(Res.string.tray_tooltip_paused, track.title.takeIf { it.isNotBlank() } ?: strUnknownTitle, artistStr)
            }
        val tooltip = (if (isPlaying) playingText else pausedText) ?: "Metrolist"

        // Click to play/pause: held back one double-click interval so a double click can open the app instead.
        val singleClick by rememberUpdatedState {
            EventQueue.invokeLater {
                if (!isListenTogetherGuest && PlayerState.currentTrack != null) {
                    val willPlay = !PlayerState.isPlaying
                    PlayerState.togglePlay()
                    TrayToast.show((if (willPlay) playingText else pausedText).orEmpty())
                }
            }
        }
        val clickGate =
            remember {
                TrayClickGate(
                    intervalMs = {
                        ((Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int) ?: 500)
                            .toLong()
                            .coerceIn(250L, 500L)
                    },
                    schedule = { delayMs, task ->
                        val future = clickScheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS)
                        val cancel: () -> Boolean = { future.cancel(false) }
                        cancel
                    },
                    onSingle = { singleClick() },
                    onDouble = { showWindow() },
                )
            }

        Tray(
            icon = icon,
            tooltip = tooltip,
            primaryAction = {
                if (Settings.shared.desktopTrayClickPlayPause) {
                    clickGate.click()
                } else if (Settings.shared.desktopTrayToggleOnClick) {
                    if (isWindowVisible.value) {
                        hideWindow()
                    } else {
                        showWindow()
                    }
                }
            },
            menuContent = {
                currentTrack?.let { track ->
                    val titleLabel = track.title.takeIf { it.isNotBlank() } ?: strUnknownTitle
                    val artistStr = track.artists.joinToString { it.displayName }.takeIf { it.isNotBlank() } ?: strUnknown
                    val artistLabel = String.format(strTrayByTemplate, artistStr)
                    Item(label = titleLabel) { showWindow() }
                    val artistId = track.artists.firstOrNull { !it.id.isNullOrBlank() }?.id
                    Item(label = artistLabel, isEnabled = artistId != null) {
                        artistId?.let {
                            showWindow()
                            EventQueue.invokeLater { DesktopNavigation.navigate(Screen.Artist(it)) }
                        }
                    }
                    Divider()
                }

                if (!isListenTogetherGuest) {
                    Item(
                        label = if (isPlaying) strPause else strPlay,
                        isEnabled = mediaControls.playPause,
                    ) {
                        PlayerState.togglePlay()
                    }

                    if (currentTrack != null) {
                        Item(label = strNextTrack, isEnabled = mediaControls.next) {
                            PlayerState.skipNext()
                        }

                        Item(label = strPreviousTrack, isEnabled = mediaControls.previous) {
                            PlayerState.skipPrevious()
                        }
                    }

                    Divider()
                }

                if (!isListenTogetherGuest && recentSongs.isNotEmpty()) {
                    SubMenu(label = strRecentlyPlayed) {
                        recentSongs.forEach { song ->
                            SubMenu(label = song.title.takeIf { it.isNotBlank() } ?: strUnknownTitle) {
                                Item(label = strPlay) { PlayerState.playTrack(song, recentSongs) }
                                Item(label = strPlayNext) { PlayerState.playNext(song) }
                                Item(label = strAddToQueue) { PlayerState.addToQueue(song) }
                            }
                        }
                    }
                }
                if (libraryPlaylists.isNotEmpty()) {
                    SubMenu(label = strPlaylists) {
                        // The ticked mode decides what choosing a playlist below does.
                        listOf(
                            TrayPlaylistPlayer.MODE_PLAY to strPlay,
                            TrayPlaylistPlayer.MODE_PLAY_NEXT to strPlayNext,
                            TrayPlaylistPlayer.MODE_ADD_TO_QUEUE to strAddToQueue,
                        ).forEach { (mode, label) ->
                            Item(label = if (playlistMode == mode) "✓ $label" else label) {
                                Settings.shared.desktopTrayPlaylistMode = mode
                                playlistMode = mode
                            }
                        }
                        Divider()
                        libraryPlaylists.take(TRAY_PLAYLIST_LIMIT).forEach { playlist ->
                            Item(
                                label = playlist.name.takeIf { it.isNotBlank() } ?: strUnknownTitle,
                                isEnabled = !isListenTogetherGuest,
                            ) { TrayPlaylistPlayer.run(playlist, playlistMode) }
                        }
                    }
                }
                if (recentSongs.isNotEmpty() || libraryPlaylists.isNotEmpty()) Divider()

                Item(
                    label = if (isWindowVisible.value) strHideWindow else strShowWindow,
                ) {
                    toggleWindow()
                }

                Item(label = strShowMiniplayer) {
                    PlayerState.showMiniplayer = true
                }

                Divider()

                Item(label = strSettings) {
                    UiState.showSettings = true
                    if (!isWindowVisible.value) {
                        showWindow()
                    }
                }

                Divider()

                Item(label = strQuitMetrolist) {
                    onQuit?.invoke()
                }
            },
        )
        DisposableEffect(Unit) {
            onDispose { trayDisposed.complete(Unit) }
        }
    }

    suspend fun cleanup() {
        withContext(Dispatchers.Main) { trayEnabled.value = false }
        withTimeoutOrNull(1000.milliseconds) { trayDisposed.await() }
        delay(50.milliseconds)
        windowState = null
        mainWindow = null
        onQuit = null
    }
}
