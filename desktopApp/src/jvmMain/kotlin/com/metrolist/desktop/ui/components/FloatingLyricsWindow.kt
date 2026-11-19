/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class, FlowPreview::class)

package com.metrolist.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.metrolist.desktop.state.playback.ListenTogetherState
import com.metrolist.desktop.state.playback.LyricsState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.desktop.ui.DesktopDensityProvider
import com.metrolist.desktop.ui.theme.MetrolistTheme
import com.metrolist.desktop.window.WindowVisibility
import com.metrolist.desktop.window.registerGlobalHotkey
import com.metrolist.desktop.window.setWindowClickThrough
import com.metrolist.desktop.window.setWindowHiddenFromCapture
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.close
import com.metrolist.shared.generated.resources.floating_lyrics
import com.metrolist.shared.generated.resources.floating_lyrics_hide_from_capture
import com.metrolist.shared.generated.resources.floating_lyrics_translucent
import com.metrolist.shared.generated.resources.loading
import com.metrolist.shared.generated.resources.miniplayer
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.settings.FloatingLyricsShortcut
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.utils.OsUtil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import kotlin.math.roundToInt

internal const val FLOATING_LYRICS_TRANSLUCENT_BACKGROUND_ALPHA = 0.2f
internal const val FLOATING_LYRICS_TRANSLUCENT_TEXT_ALPHA = 0.75f
private val MinWindowSize = DpSize(320.dp, 240.dp)

/**
 * One global click-through toggle shared by the always-on-top windows (lyrics floater and miniplayer). Registered only
 * while one of them is open. If the combination is taken the toggle simply never fires, so a window can never end up
 * unclickable with no way back.
 */
@Composable
fun FloatingWindowsHotkey() {
    if (!PlayerState.showFloatingLyrics && !PlayerState.showMiniplayer) return
    val shortcutText = Settings.shared.floatingLyricsClickThroughShortcut
    DisposableEffect(shortcutText) {
        val shortcut = FloatingLyricsShortcut.parse(shortcutText) ?: FloatingLyricsShortcut.DEFAULT
        val hotkey = registerGlobalHotkey(shortcut) { PlayerState.floatingLyricsClickThrough = !PlayerState.floatingLyricsClickThrough }
        onDispose {
            hotkey?.close()
            PlayerState.floatingLyricsClickThrough = false
        }
    }
}

/**
 * Always-on-top, borderless lyrics window. It lives outside the main window, so it keeps working while the app sits in
 * the tray. Borderless windows get no native resize edges, so a corner grip handles resizing.
 */
@Composable
fun FloatingLyricsWindow() {
    if (!PlayerState.showFloatingLyrics) return

    // Only reused when it still lands on a connected screen.
    val savedBounds =
        remember {
            FloatingLyricsBounds.parse(Settings.shared.floatingLyricsBounds)?.takeIf { bounds ->
                runCatching {
                    bounds.isReachableOn(
                        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds },
                    )
                }.getOrDefault(false)
            }
        }
    val windowState =
        rememberWindowState(
            position = savedBounds?.let { WindowPosition.Absolute(it.x.dp, it.y.dp) } ?: WindowPosition.Aligned(Alignment.BottomEnd),
            size = savedBounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(460.dp, 420.dp),
        )
    // Remember where the window was left; a drag or resize is saved once it settles.
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.position to windowState.size }
            .debounce(500)
            .collect { (position, size) ->
                if (position !is WindowPosition.Absolute) return@collect
                Settings.shared.floatingLyricsBounds =
                    FloatingLyricsBounds(
                        position.x.value.roundToInt(),
                        position.y.value.roundToInt(),
                        size.width.value.roundToInt(),
                        size.height.value.roundToInt(),
                    ).format()
            }
    }
    val clickThrough = PlayerState.floatingLyricsClickThrough
    val hideFromCapture = Settings.shared.floatingLyricsHideFromCapture
    // Per-pixel transparency is unreliable on Linux compositors (see the miniplayer), so translucency is skipped there.
    val canBeTranslucent = !OsUtil.isLinux
    val translucent = canBeTranslucent && Settings.shared.floatingLyricsTranslucent

    Window(
        onCloseRequest = { PlayerState.showFloatingLyrics = false },
        title = stringResource(Res.string.floating_lyrics),
        state = windowState,
        undecorated = true,
        transparent = canBeTranslucent,
        resizable = true,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(window, hideFromCapture) { setWindowHiddenFromCapture(window, hideFromCapture) }
        LaunchedEffect(window, clickThrough) { setWindowClickThrough(window, clickThrough) }
        val track = PlayerState.currentTrack
        LaunchedEffect(track?.id, Settings.shared.lyricsProviderOrder, Settings.shared.lyricsEnabledProviders) {
            if (track != null) LyricsState.fetchLyrics(track)
        }
        DesktopDensityProvider {
            // The main window's own copy stops running while it is hidden in the tray, which froze the accent colours.
            UpdateArtworkTheme()
            MetrolistTheme {
                WindowVisibility(window) {
                    val colors = MaterialTheme.colorScheme
                    val contentColor = colors.onSurface.copy(alpha = if (translucent) FLOATING_LYRICS_TRANSLUCENT_TEXT_ALPHA else 1f)
                    val background = colors.surface.copy(alpha = if (translucent) FLOATING_LYRICS_TRANSLUCENT_BACKGROUND_ALPHA else 1f)
                    val hoverSource = remember { MutableInteractionSource() }
                    val hovered by hoverSource.collectIsHoveredAsState()
                    Surface(
                        modifier = Modifier.fillMaxSize().hoverable(hoverSource),
                        color = background,
                        shape = RoundedCornerShape(if (canBeTranslucent) 12.dp else 0.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Everything above the controls drags the window. The lyrics keep their own drag-to-scroll
                                // (children consume the gesture first), so the padding around them is the grab edge.
                                WindowDraggableArea(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // While clicks pass through, the buttons could not be used, so only the lyrics stay.
                                        if (!clickThrough) {
                                            FloatingLyricsHeader(
                                                contentColor = contentColor,
                                                hideFromCapture = hideFromCapture,
                                                translucent = translucent,
                                                canBeTranslucent = canBeTranslucent,
                                                visible = hovered,
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                                            DesktopLyricsContent(textColor = contentColor)
                                        }
                                    }
                                }
                                if (clickThrough || ListenTogetherState.isListenTogetherGuest()) {
                                    Spacer(Modifier.height(12.dp))
                                } else {
                                    FloatingLyricsControls(contentColor)
                                }
                            }
                            if (!clickThrough) {
                                ResizeGrip(
                                    onResize = { dx, dy ->
                                        windowState.size =
                                            DpSize(
                                                (windowState.size.width + dx).coerceAtLeast(MinWindowSize.width),
                                                (windowState.size.height + dy).coerceAtLeast(MinWindowSize.height),
                                            )
                                    },
                                    color = contentColor,
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingLyricsHeader(
    contentColor: Color,
    hideFromCapture: Boolean,
    translucent: Boolean,
    canBeTranslucent: Boolean,
    visible: Boolean,
) {
    // Stays laid out and clickable; it is only invisible while the pointer is away from the window.
    val alpha by animateFloatAsState(if (visible) 1f else 0f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 4.dp)
                .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        HeaderIcon(
            icon = if (hideFromCapture) MetrolistIcons.Filled.VisibilityOff else MetrolistIcons.Filled.Visibility,
            description = stringResource(Res.string.floating_lyrics_hide_from_capture),
            tint = contentColor,
            active = hideFromCapture,
            onClick = { Settings.shared.floatingLyricsHideFromCapture = !hideFromCapture },
        )
        if (canBeTranslucent) {
            HeaderIcon(
                icon = MetrolistIcons.Outlined.Palette,
                description = stringResource(Res.string.floating_lyrics_translucent),
                tint = contentColor,
                active = translucent,
                onClick = { Settings.shared.floatingLyricsTranslucent = !translucent },
            )
        }
        HeaderIcon(
            icon = MetrolistIcons.Filled.PictureInPicture,
            description = stringResource(Res.string.miniplayer),
            tint = contentColor,
            active = PlayerState.showMiniplayer,
            onClick = { PlayerState.showMiniplayer = !PlayerState.showMiniplayer },
        )
        HeaderIcon(
            icon = MetrolistIcons.Outlined.Close,
            description = stringResource(Res.string.close),
            tint = contentColor,
            active = false,
            onClick = { PlayerState.showFloatingLyrics = false },
        )
    }
}

@Composable
internal fun HeaderIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        delayMillis = 400,
        tooltipPlacement =
            TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                alignment = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 2.dp),
            ),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(18.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else tint,
            )
        }
    }
}

@Composable
private fun FloatingLyricsControls(contentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { PlayerState.skipPrevious() }, enabled = PlayerState.canSkipPrevious, modifier = Modifier.size(36.dp)) {
            Icon(
                MetrolistIcons.Rounded.SkipPrevious,
                stringResource(Res.string.previous),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
        }
        IconButton(onClick = { PlayerState.togglePlay() }, modifier = Modifier.size(40.dp)) {
            if (PlayerState.isPlaybackLoading) {
                val loadingText = stringResource(Res.string.loading)
                LoadingIndicator(
                    modifier = Modifier.size(28.dp).semantics { contentDescription = loadingText },
                    color = contentColor,
                )
            } else {
                Icon(
                    imageVector = if (PlayerState.isPlaying) MetrolistIcons.Rounded.Pause else MetrolistIcons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (PlayerState.isPlaying) Res.string.pause else Res.string.play),
                    modifier = Modifier.size(28.dp),
                    tint = contentColor,
                )
            }
        }
        IconButton(onClick = { PlayerState.skipNext() }, modifier = Modifier.size(36.dp)) {
            Icon(MetrolistIcons.Rounded.SkipNext, stringResource(Res.string.next), modifier = Modifier.size(24.dp), tint = contentColor)
        }
        MiniplayerProgress(
            colorScheme = MaterialTheme.colorScheme,
            enabled = true,
            modifier = Modifier.weight(1f).height(32.dp).padding(start = 8.dp),
        )
    }
}

@Composable
private fun ResizeGrip(
    onResize: (dx: androidx.compose.ui.unit.Dp, dy: androidx.compose.ui.unit.Dp) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentOnResize by rememberUpdatedState(onResize)
    Box(
        modifier =
            modifier
                .size(18.dp)
                .pointerInput(density) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        currentOnResize(with(density) { drag.x.toDp() }, with(density) { drag.y.toDp() })
                    }
                },
    ) {
        Icon(
            imageVector = MetrolistIcons.Outlined.Fullscreen,
            contentDescription = null,
            modifier = Modifier.size(12.dp).align(Alignment.BottomEnd),
            tint = color.copy(alpha = 0.5f),
        )
    }
}
