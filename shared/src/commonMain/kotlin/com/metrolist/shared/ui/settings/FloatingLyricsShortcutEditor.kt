package com.metrolist.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.floating_lyrics_shortcut_invalid
import com.metrolist.shared.generated.resources.floating_lyrics_shortcut_press
import com.metrolist.shared.generated.resources.reset
import com.metrolist.shared.settings.FloatingLyricsShortcut
import org.jetbrains.compose.resources.stringResource

private val shortcutKeys: Map<Key, String> =
    buildMap {
        listOf(
            Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
            Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
        ).forEachIndexed { index, key -> put(key, ('A' + index).toString()) }
        listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
            .forEachIndexed { index, key -> put(key, index.toString()) }
        listOf(Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12)
            .forEachIndexed { index, key -> put(key, "F${index + 1}") }
    }

/**
 * A settings row laid out like the built-in ones (title, description, current value on the right). Clicking it records
 * the next valid combination; Esc or clicking away cancels. The settings screen draws custom rows bare, so the row's
 * text is drawn here.
 */
@Composable
internal fun FloatingLyricsShortcutEditor(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var recording by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val shown = FloatingLyricsShortcut.parse(value)?.format() ?: FloatingLyricsShortcut.DEFAULT_TEXT
    LaunchedEffect(recording) { if (recording) focus.requestFocus() }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .focusRequester(focus)
            .onFocusChanged { if (!it.isFocused) recording = false }
            .onPreviewKeyEvent { event ->
                if (!recording) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                if (event.key == Key.Escape) {
                    recording = false
                    return@onPreviewKeyEvent true
                }
                // Modifier-only presses are not an answer yet.
                val name = shortcutKeys[event.key] ?: return@onPreviewKeyEvent true
                val candidate =
                    FloatingLyricsShortcut(
                        ctrl = event.isCtrlPressed,
                        alt = event.isAltPressed,
                        shift = event.isShiftPressed,
                        meta = event.isMetaPressed,
                        key = name,
                    )
                if (candidate.isValid) {
                    onValueChange(candidate.format())
                    recording = false
                } else {
                    rejected = true
                }
                true
            }.clickable {
                rejected = false
                recording = true
            }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Text(
                if (recording) stringResource(Res.string.floating_lyrics_shortcut_press) else shown,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (recording && rejected) {
            Text(
                text = stringResource(Res.string.floating_lyrics_shortcut_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!recording && shown != FloatingLyricsShortcut.DEFAULT_TEXT) {
            TextButton(onClick = { onValueChange(FloatingLyricsShortcut.DEFAULT_TEXT) }) {
                Text(stringResource(Res.string.reset))
            }
        }
    }
}
