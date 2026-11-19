/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.materialkolor.ktx.isLight
import com.metrolist.desktop.state.app.AppState
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.state.content.ArtistState
import com.metrolist.desktop.state.content.PlaylistAlbumState
import com.metrolist.desktop.state.playback.DesktopPlaybackController
import com.metrolist.desktop.state.playback.ListenTogetherState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.desktop.ui.DesktopDensityProvider
import com.metrolist.desktop.ui.theme.MetrolistTheme
import com.metrolist.desktop.utils.ComposeTrayManager
import com.metrolist.desktop.utils.WindowDragHandle
import com.metrolist.desktop.window.WindowVisibility
import com.metrolist.desktop.window.WindowsMiniPlayerWindowFrame
import com.metrolist.desktop.window.setWindowClickThrough
import com.metrolist.desktop.window.setWindowHiddenFromCapture
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cd_playback_position
import com.metrolist.shared.generated.resources.close_miniplayer
import com.metrolist.shared.generated.resources.enter_focus_mode
import com.metrolist.shared.generated.resources.exit_focus_mode
import com.metrolist.shared.generated.resources.floating_lyrics
import com.metrolist.shared.generated.resources.floating_lyrics_hide_from_capture
import com.metrolist.shared.generated.resources.floating_lyrics_translucent
import com.metrolist.shared.generated.resources.hide_queue
import com.metrolist.shared.generated.resources.loading
import com.metrolist.shared.generated.resources.miniplayer
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.no_track_playing
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.generated.resources.show_queue
import com.metrolist.shared.model.isLive
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.settings.PlayerBackgroundStyle
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.ArtistSubtitle
import com.metrolist.shared.ui.components.MetadataLink
import com.metrolist.shared.ui.components.PlayerLikeShareButtons
import com.metrolist.shared.ui.components.playbackMarquee
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.playback.LocalPlaybackController
import com.metrolist.shared.ui.utils.ArtworkBlurStyle
import com.metrolist.shared.utils.OsUtil
import com.metrolist.shared.utils.ShareUrls
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import java.awt.Cursor
import java.awt.Toolkit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

internal val MiniplayerWidth = 420.dp
internal val MiniplayerCompactHeight = 192.dp
internal val MiniplayerFocusSize = 300.dp
internal val MiniplayerMinQueueHeight = 360.dp
internal val MiniplayerMaxQueueHeight = 1000.dp
private val MiniplayerDefaultQueueHeight = 560.dp
private val VerticalResizeHandleHeight = 12.dp

/** The window only grows vertically: the width is fixed and the height stays within what the queue list can use. */
internal fun clampMiniplayerHeight(height: Dp): Dp = height.coerceIn(MiniplayerMinQueueHeight, MiniplayerMaxQueueHeight)

/** Moves a window of [height] up just enough to fit above [bottom]; never above [top]. */
internal fun keepVerticallyOnScreen(
    y: Int,
    height: Int,
    top: Int,
    bottom: Int,
): Int = y.coerceAtMost(bottom - height).coerceAtLeast(top)

/**
 * Composed at application level (see Main.kt), so it opens, closes and updates while the main window is hidden in the
 * tray. Borderless, so the bottom edge carries its own vertical-only resize handle while the queue is open.
 */
@Composable
fun MiniplayerWindow() {
    if (!PlayerState.showMiniplayer) return

    val isFocusMode = PlayerState.miniplayerFocusMode
    var showQueue by remember { mutableStateOf(false) }
    var queueHeight by remember { mutableStateOf(MiniplayerDefaultQueueHeight) }
    val queueOpen = showQueue && !isFocusMode
    val windowSize =
        when {
            isFocusMode -> DpSize(MiniplayerFocusSize, MiniplayerFocusSize)
            queueOpen -> DpSize(MiniplayerWidth, queueHeight)
            else -> DpSize(MiniplayerWidth, MiniplayerCompactHeight)
        }
    val windowState =
        remember {
            WindowState(
                position = WindowPosition.Aligned(Alignment.BottomEnd),
                size = windowSize,
            )
        }

    val isNightMode = Settings.shared.isPureBlackTheme()
    val clickThrough = PlayerState.floatingLyricsClickThrough
    val hideFromCapture = Settings.shared.miniplayerHideFromCapture
    // Per-pixel transparency is unreliable on Linux compositors (it can consume the render loop without drawing).
    val transparentWindow = !OsUtil.isLinux
    val translucent = transparentWindow && Settings.shared.miniplayerTranslucent && !isFocusMode

    Window(
        onCloseRequest = { PlayerState.showMiniplayer = false },
        title = stringResource(Res.string.miniplayer),
        resizable = false,
        visible = true,
        undecorated = true,
        transparent = transparentWindow,
        state = windowState,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(window, hideFromCapture) { setWindowHiddenFromCapture(window, hideFromCapture) }
        LaunchedEffect(window, clickThrough) { setWindowClickThrough(window, clickThrough) }
        val windowDensity = LocalDensity.current.density
        DesktopDensityProvider {
            val scale = LocalDensity.current.density / windowDensity
            LaunchedEffect(windowSize, scale) {
                val target = DpSize(windowSize.width * scale, windowSize.height * scale)
                windowState.size = target
                // The window grows downward; pull it up so the queue and its resize handle stay on screen.
                val config = window.graphicsConfiguration
                if (config != null) {
                    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
                    val bounds = config.bounds
                    val y =
                        keepVerticallyOnScreen(
                            y = window.y,
                            height = target.height.value.roundToInt(),
                            top = bounds.y + insets.top,
                            bottom = bounds.y + bounds.height - insets.bottom,
                        )
                    if (y != window.y) windowState.position = WindowPosition(window.x.dp, y.dp)
                }
            }
            // The main window's own copy stops running while it is hidden in the tray, which froze the accent colours.
            UpdateArtworkTheme()
            // This window is composed outside the main one, so the menus inside it need their own controller.
            val playbackController = remember { DesktopPlaybackController(AppState.scope) }
            MetrolistTheme {
                CompositionLocalProvider(LocalPlaybackController provides playbackController) {
                    WindowVisibility(window) {
                        val colorScheme = MaterialTheme.colorScheme
                        // The drag area moves the window even for gestures a child consumed, so the queue and its resize
                        // handle must sit outside it.
                        val dragArea: @Composable (@Composable () -> Unit) -> Unit = { inner ->
                            if (OsUtil.isWindows) {
                                WindowDraggableArea { inner() }
                            } else {
                                WindowDragHandle(isMaximized = false, onRestoreFromMaximized = {}) { inner() }
                            }
                        }
                        val content: @Composable () -> Unit = {
                            if (isFocusMode) {
                                dragArea { FocusModeMiniplayer(colorScheme, isNightMode, transparentWindow) }
                            } else {
                                StandardMiniplayer(
                                    colorScheme = colorScheme,
                                    isNightMode = isNightMode,
                                    transparentWindow = transparentWindow,
                                    translucent = translucent,
                                    clickThrough = clickThrough,
                                    hideFromCapture = hideFromCapture,
                                    queueOpen = queueOpen,
                                    onToggleQueue = { showQueue = !showQueue },
                                    onResize = { dy -> queueHeight = clampMiniplayerHeight(queueHeight + dy / scale) },
                                    dragArea = dragArea,
                                )
                            }
                        }
                        if (OsUtil.isWindows) {
                            val areWeInDarkMode = remember { mutableStateOf(false) }
                            WindowsMiniPlayerWindowFrame(
                                onCloseRequest = { PlayerState.showMiniplayer = false },
                                content = {
                                    areWeInDarkMode.value = !MaterialTheme.colorScheme.background.isLight()
                                    content()
                                },
                                state = windowState,
                                darkTheme = areWeInDarkMode.value,
                            )
                        } else {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniplayerHeader(
    colorScheme: ColorScheme,
    translucent: Boolean,
    hideFromCapture: Boolean,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
    visible: Boolean,
) {
    val tint = colorScheme.onSurfaceVariant
    // Stays laid out and clickable; it is only invisible while the pointer is away from the window.
    val alpha by animateFloatAsState(if (visible) 1f else 0f)
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        HeaderIcon(
            icon = if (hideFromCapture) MetrolistIcons.Filled.VisibilityOff else MetrolistIcons.Filled.Visibility,
            description = stringResource(Res.string.floating_lyrics_hide_from_capture),
            tint = tint,
            active = hideFromCapture,
            onClick = { Settings.shared.miniplayerHideFromCapture = !hideFromCapture },
        )
        if (!OsUtil.isLinux) {
            HeaderIcon(
                icon = MetrolistIcons.Outlined.Palette,
                description = stringResource(Res.string.floating_lyrics_translucent),
                tint = tint,
                active = translucent,
                onClick = { Settings.shared.miniplayerTranslucent = !translucent },
            )
        }
        HeaderIcon(
            icon = MetrolistIcons.Outlined.Image,
            description = stringResource(Res.string.enter_focus_mode),
            tint = tint,
            active = false,
            onClick = { Settings.shared.miniplayerFocusMode = true },
        )
        HeaderIcon(
            icon = MetrolistIcons.Outlined.Lyrics,
            description = stringResource(Res.string.floating_lyrics),
            tint = tint,
            active = PlayerState.showFloatingLyrics,
            onClick = { PlayerState.showFloatingLyrics = !PlayerState.showFloatingLyrics },
        )
        HeaderIcon(
            icon = MetrolistIcons.AutoMirrored.Outlined.QueueMusic,
            description = stringResource(if (queueOpen) Res.string.hide_queue else Res.string.show_queue),
            tint = tint,
            active = queueOpen,
            onClick = onToggleQueue,
        )
        HeaderIcon(
            icon = MetrolistIcons.Outlined.Close,
            description = stringResource(Res.string.close_miniplayer),
            tint = tint,
            active = false,
            onClick = { PlayerState.showMiniplayer = false },
        )
    }
}

/** A bottom-edge handle that changes only the height. */
@Composable
private fun VerticalResizeHandle(
    onResize: (dy: Dp) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentOnResize by rememberUpdatedState(onResize)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(VerticalResizeHandleHeight)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
                .pointerInput(density) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        currentOnResize(with(density) { drag.y.toDp() })
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun StandardMiniplayer(
    colorScheme: ColorScheme,
    isNightMode: Boolean,
    transparentWindow: Boolean,
    translucent: Boolean,
    clickThrough: Boolean,
    hideFromCapture: Boolean,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
    onResize: (Dp) -> Unit,
    dragArea: @Composable (@Composable () -> Unit) -> Unit,
) {
    val isListenTogetherGuest = ListenTogetherState.isListenTogetherGuest()
    val baseColor = if (isNightMode) Color.Black else colorScheme.surfaceContainerHigh
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    // The variant colour washes out over a light desktop once the card is see-through.
    val subtitleColor = if (translucent) colorScheme.onSurface else colorScheme.onSurfaceVariant
    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .hoverable(hoverSource),
        shape = RoundedCornerShape(if (transparentWindow) 16.dp else 0.dp),
        color = if (translucent) baseColor.copy(alpha = FLOATING_LYRICS_TRANSLUCENT_BACKGROUND_ALPHA) else baseColor,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f)),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!OsUtil.isLinux && !translucent) {
                if (Settings.shared.playerBackgroundStyle == PlayerBackgroundStyle.BLURRED_ARTWORK) {
                    AsyncImage(
                        url = PlayerState.currentArtworkUrl ?: "",
                        artworkPreview = PlayerState.artworkPreview,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(if (OsUtil.isWindows) 6.dp else 16.dp)),
                        contentScale = ContentScale.Crop,
                        blur =
                            ArtworkBlurStyle(
                                radius = 60.dp,
                                backgroundColor = if (isNightMode) Color.Black else colorScheme.surfaceContainerHigh,
                            ),
                    )
                }
                if (!PlayerState.miniplayerFocusMode) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(if (OsUtil.isWindows) 6.dp else 16.dp))
                                .background(
                                    if (isNightMode || !MaterialTheme.colorScheme.background.isLight()) {
                                        colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        colorScheme.primary.copy(alpha = 0.25f)
                                    },
                                ),
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (translucent) FLOATING_LYRICS_TRANSLUCENT_TEXT_ALPHA else 1f }
                        .padding(start = 16.dp, end = 16.dp),
            ) {
                dragArea {
                    Column {
                        // While clicks pass through the buttons could not be used anyway.
                        if (!clickThrough) {
                            MiniplayerHeader(colorScheme, translucent, hideFromCapture, queueOpen, onToggleQueue, visible = hovered)
                        } else {
                            Spacer(Modifier.height(36.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                url = PlayerState.currentArtworkUrl ?: "",
                                artworkPreview = PlayerState.artworkPreview,
                                modifier = Modifier.songThumbSize(PlayerState.currentTrack, 80.dp).clip(RoundedCornerShape(4.dp)),
                                shape = RoundedCornerShape(4.dp),
                                contentScale = if (Settings.shared.cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                            )

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = PlayerState.currentTrack?.title ?: stringResource(Res.string.no_track_playing),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .playbackMarquee(
                                                iterations = 1,
                                                initialDelayMillis = 10000,
                                                velocity = 30.dp,
                                            ),
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val artists = PlayerState.currentTrack?.artists ?: emptyList()
                                    ArtistSubtitle(
                                        artists = artists,
                                        onArtistClick = { artistId ->
                                            ArtistState.fetchArtistData(artistId)
                                            ComposeTrayManager.showWindow()
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = subtitleColor,
                                        modifier =
                                            Modifier
                                                .weight(1f, fill = false)
                                                .playbackMarquee(
                                                    iterations = 1,
                                                    initialDelayMillis = 10000,
                                                    velocity = 30.dp,
                                                ),
                                    )
                                    val album = PlayerState.currentTrack?.album
                                    if (album != null && album.name.isNotBlank()) {
                                        Text(
                                            " • ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = subtitleColor,
                                        )
                                        MetadataLink(
                                            text = album.name,
                                            id = album.id,
                                            onClick = { albumId ->
                                                PlaylistAlbumState.fetchAlbumData(albumId)
                                                ComposeTrayManager.showWindow()
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = subtitleColor,
                                            modifier =
                                                Modifier
                                                    .weight(1f, fill = false)
                                                    .playbackMarquee(
                                                        iterations = 1,
                                                        initialDelayMillis = 10000,
                                                        velocity = 30.dp,
                                                    ),
                                        )
                                    }
                                }

                                if (!isListenTogetherGuest) {
                                    Spacer(Modifier.height(8.dp))

                                    PlaybackControlsWithActions(colorScheme)
                                }
                            }
                        }

                        MiniplayerProgress(
                            colorScheme = colorScheme,
                            enabled = !isListenTogetherGuest,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                        )
                    }
                }

                if (queueOpen) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
                        DesktopQueueContent(
                            onDismiss = onToggleQueue,
                            colorScheme = colorScheme,
                            containerAlpha = if (translucent) FLOATING_LYRICS_TRANSLUCENT_BACKGROUND_ALPHA else 1f,
                            compact = true,
                        )
                    }
                    // Keep the handle's height while click-through so the queue does not sit on the window edge.
                    if (clickThrough) {
                        Spacer(Modifier.height(VerticalResizeHandleHeight))
                    } else {
                        VerticalResizeHandle(onResize = onResize, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusModeMiniplayer(
    colorScheme: ColorScheme,
    isNightMode: Boolean,
    transparentWindow: Boolean,
) {
    val isListenTogetherGuest = ListenTogetherState.isListenTogetherGuest()
    val interactionSource = remember { MutableInteractionSource() }
    // Hover only: a click leaves the surface focused, which used to pin the controls open.
    val showControls by interactionSource.collectIsHoveredAsState()
    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .hoverable(interactionSource),
        shape = RoundedCornerShape(if (transparentWindow) 12.dp else 0.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                url = PlayerState.currentArtworkUrl ?: "",
                artworkPreview = PlayerState.artworkPreview,
                modifier =
                    Modifier.fillMaxSize().then(
                        if (!OsUtil.isLinux && showControls) Modifier.blur(16.dp) else Modifier,
                    ),
                shape = RoundedCornerShape(0.dp),
                contentScale = ContentScale.Crop,
            )

            if (showControls) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (isNightMode || !MaterialTheme.colorScheme.background.isLight()) {
                                    colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    colorScheme.primary.copy(alpha = 0.25f)
                                },
                            ),
                )
            }

            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { Settings.shared.miniplayerFocusMode = !Settings.shared.miniplayerFocusMode },
                            modifier = Modifier.size(40.dp),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                MetrolistIcons.Outlined.FullscreenExit,
                                stringResource(Res.string.exit_focus_mode),
                                modifier = Modifier.size(18.dp),
                                tint = Color.White,
                            )
                        }
                        IconButton(
                            onClick = { PlayerState.showMiniplayer = false },
                            modifier = Modifier.size(40.dp),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                MetrolistIcons.Outlined.Close,
                                stringResource(Res.string.close_miniplayer),
                                modifier = Modifier.size(18.dp),
                                tint = Color.White,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = PlayerState.currentTrack?.title ?: stringResource(Res.string.no_track_playing),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .playbackMarquee(
                                        iterations = 1,
                                        initialDelayMillis = 10000,
                                        velocity = 30.dp,
                                    ),
                        )
                        ArtistSubtitle(
                            artists = PlayerState.currentTrack?.artists.orEmpty(),
                            onArtistClick = { artistId ->
                                ArtistState.fetchArtistData(artistId)
                                ComposeTrayManager.showWindow()
                            },
                            style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                            color = Color.White.copy(alpha = 0.7f),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .playbackMarquee(
                                        iterations = 1,
                                        initialDelayMillis = 10000,
                                        velocity = 30.dp,
                                    ),
                        )

                        if (!isListenTogetherGuest) {
                            Spacer(Modifier.height(24.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                IconButton(
                                    onClick = { PlayerState.skipPrevious() },
                                    enabled = PlayerState.canSkipPrevious,
                                    modifier = Modifier.size(40.dp),
                                    colors =
                                        IconButtonDefaults.iconButtonColors(
                                            containerColor = colorScheme.secondaryContainer,
                                            contentColor = colorScheme.onSecondaryContainer,
                                        ),
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        MetrolistIcons.Rounded.SkipPrevious,
                                        stringResource(Res.string.previous),
                                        modifier = Modifier.size(28.dp),
                                        tint = colorScheme.onSecondaryContainer,
                                    )
                                }

                                Surface(
                                    onClick = { PlayerState.togglePlay() },
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (PlayerState.isPlaybackLoading) {
                                            val loadingText = stringResource(Res.string.loading)
                                            LoadingIndicator(
                                                modifier = Modifier.size(28.dp).semantics { contentDescription = loadingText },
                                                color = Color.White,
                                            )
                                        } else {
                                            val playPauseIcon =
                                                if (PlayerState.isPlaying) {
                                                    MetrolistIcons.Rounded.Pause
                                                } else {
                                                    MetrolistIcons.Rounded.PlayArrow
                                                }
                                            val playPauseDesc =
                                                if (PlayerState.isPlaying) {
                                                    stringResource(Res.string.pause)
                                                } else {
                                                    stringResource(Res.string.play)
                                                }
                                            Icon(
                                                imageVector = playPauseIcon,
                                                contentDescription = playPauseDesc,
                                                modifier = Modifier.size(32.dp),
                                                tint = Color.White,
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { PlayerState.skipNext() },
                                    modifier = Modifier.size(40.dp),
                                    colors =
                                        IconButtonDefaults.iconButtonColors(
                                            containerColor = colorScheme.secondaryContainer,
                                            contentColor = colorScheme.onSecondaryContainer,
                                        ),
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        MetrolistIcons.Rounded.SkipNext,
                                        stringResource(Res.string.next),
                                        modifier = Modifier.size(28.dp),
                                        tint = colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    MiniplayerProgress(
                        colorScheme = colorScheme,
                        enabled = !isListenTogetherGuest,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .height(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(colorScheme: ColorScheme) {
    val backInteractionSource = remember { MutableInteractionSource() }
    val nextInteractionSource = remember { MutableInteractionSource() }
    val playPauseInteractionSource = remember { MutableInteractionSource() }

    val isBackPressed by backInteractionSource.collectIsPressedAsState()
    val isNextPressed by nextInteractionSource.collectIsPressedAsState()
    val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()

    val playPauseWeight by animateFloatAsState(
        targetValue =
            if (isPlayPausePressed) {
                1.8f
            } else if (isBackPressed || isNextPressed) {
                1.1f
            } else {
                1.3f
            },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
    )
    val backButtonWeight by animateFloatAsState(
        targetValue =
            if (isBackPressed) {
                0.65f
            } else if (isPlayPausePressed) {
                0.35f
            } else {
                0.45f
            },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
    )
    val nextButtonWeight by animateFloatAsState(
        targetValue =
            if (isNextPressed) {
                0.65f
            } else if (isPlayPausePressed) {
                0.35f
            } else {
                0.45f
            },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
    )

    Row(
        modifier = Modifier.width(180.dp).height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = { PlayerState.skipPrevious() },
            enabled = PlayerState.canSkipPrevious,
            shapes = IconButtonDefaults.shapes(shape = RoundedCornerShape(50)),
            interactionSource = backInteractionSource,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor =
                        colorScheme.surfaceContainerHighest.copy(
                            alpha = 1.0f,
                        ),
                    contentColor = colorScheme.onSurface,
                ),
            modifier = Modifier.fillMaxHeight().weight(backButtonWeight),
        ) { Icon(MetrolistIcons.Rounded.SkipPrevious, stringResource(Res.string.previous), modifier = Modifier.size(20.dp)) }

        Spacer(Modifier.width(8.dp))

        val playDesc = stringResource(Res.string.play)
        val pauseDesc = stringResource(Res.string.pause)
        val loadingDesc = stringResource(Res.string.loading)

        MetrolistPlayPauseButton(
            isPlaying = PlayerState.isPlaying,
            isLoading = PlayerState.isPlaybackLoading,
            onClick = { PlayerState.togglePlay() },
            colorScheme = colorScheme,
            isWide = false,
            interactionSource = playPauseInteractionSource,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(playPauseWeight)
                    .semantics {
                        contentDescription =
                            if (PlayerState.isPlaybackLoading) {
                                loadingDesc
                            } else if (PlayerState.isPlaying) {
                                pauseDesc
                            } else {
                                playDesc
                            }
                    },
        )

        Spacer(Modifier.width(8.dp))

        FilledIconButton(
            onClick = { PlayerState.skipNext() },
            shapes = IconButtonDefaults.shapes(shape = RoundedCornerShape(50)),
            interactionSource = nextInteractionSource,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor =
                        colorScheme.surfaceContainerHighest.copy(
                            alpha = 1.0f,
                        ),
                    contentColor = colorScheme.onSurface,
                ),
            modifier = Modifier.fillMaxHeight().weight(nextButtonWeight),
        ) { Icon(MetrolistIcons.Rounded.SkipNext, stringResource(Res.string.next), modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun PlaybackControlsWithActions(colorScheme: ColorScheme) {
    val track = PlayerState.currentTrack
    val shareTrack = { track?.let { AppState.shareLink(ShareUrls.song(it.id), it.title) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlaybackControls(colorScheme)
        Spacer(Modifier.width(8.dp))
        PlayerLikeShareButtons(
            isLiked = track?.let { PlayerState.isLiked(it.id) } == true,
            onLikeClick = { track?.let { PlayerState.toggleLike(it) } },
            onShareClick = { shareTrack() },
            onAltShareClick = { shareTrack() },
            groupHeight = 40.dp,
            iconSize = 18.dp,
        )
    }
}

@Composable
internal fun MiniplayerProgress(
    colorScheme: ColorScheme,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isLive = playerIsLiveStream()
    val canSeek = enabled && !isLive
    val playerDuration by PlayerState.playbackDuration.collectAsState()
    var sliderWidthPx by remember { mutableIntStateOf(0) }
    val smoothPosition = rememberPlayerProgressPosition(playerDuration, sliderWidthPx, isLive)
    // Read the initial slider value without subscribing this row to position samples.
    val sampledProgress = if (playerDuration > 0L) PlayerState.playbackPosition.value.toFloat() / playerDuration else 0f
    var isDragging by remember { mutableStateOf(false) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var dragStartPosMs by remember { mutableLongStateOf(-1L) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    val playbackPositionDescription = stringResource(Res.string.cd_playback_position)

    LaunchedEffect(pendingSeekTargetMs) {
        if (pendingSeekTargetMs != null) {
            delay(1200.milliseconds)
            pendingSeekTargetMs = null
        }
    }

    LaunchedEffect(isDragging, pendingSeekTargetMs, dragStartPosMs) {
        if (isDragging) return@LaunchedEffect
        val targetMs = pendingSeekTargetMs ?: return@LaunchedEffect
        PlayerState.playbackPosition.collect { sample ->
            if (sample != dragStartPosMs && abs(sample - targetMs) < 2000L) {
                pendingSeekTargetMs = null
            }
        }
    }
    if (isLive) {
        LiveProgressBar(
            modifier = modifier,
            color = colorScheme.primary,
        )
    } else {
        PlayerSlider(
            value = if (isDragging || pendingSeekTargetMs != null) sliderValue else sampledProgress.coerceIn(0f, 1f),
            valueProvider = {
                when {
                    isDragging || pendingSeekTargetMs != null -> sliderValue
                    playerDuration > 0L -> (smoothPosition.longValue.toFloat() / playerDuration).coerceIn(0f, 1f)
                    else -> 0f
                }
            },
            onValueChange = {
                if (canSeek) {
                    if (!isDragging) {
                        isDragging = true
                        dragStartPosMs = PlayerState.playbackPosition.value
                    }
                    pendingSeekTargetMs = null
                    sliderValue = it
                }
            },
            onValueChangeFinished = {
                if (canSeek) {
                    val targetMs = (sliderValue * playerDuration).toLong()
                    PlayerState.seekTo(sliderValue)
                    isDragging = false
                    pendingSeekTargetMs = targetMs
                }
            },
            style = Settings.shared.sliderStyle,
            isPlaying = PlayerState.isPlaying,
            color = colorScheme.primary,
            bufferedValue = PlayerState.bufferedProgress,
            enabled = canSeek,
            modifier =
                modifier
                    .onSizeChanged { sliderWidthPx = it.width }
                    .semantics { contentDescription = playbackPositionDescription },
        )
    }
}
