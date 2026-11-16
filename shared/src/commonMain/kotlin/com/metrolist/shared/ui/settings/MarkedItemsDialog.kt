@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.album
import com.metrolist.shared.generated.resources.artist
import com.metrolist.shared.generated.resources.close
import com.metrolist.shared.generated.resources.marked_items
import com.metrolist.shared.generated.resources.marked_items_desc
import com.metrolist.shared.generated.resources.marked_items_empty
import com.metrolist.shared.generated.resources.marked_items_incognito
import com.metrolist.shared.generated.resources.marked_items_remove
import com.metrolist.shared.generated.resources.marked_items_skip
import com.metrolist.shared.generated.resources.playlist
import com.metrolist.shared.generated.resources.song
import com.metrolist.shared.settings.IncognitoMarks
import com.metrolist.shared.settings.MarkKind
import com.metrolist.shared.settings.MarkRegistry
import com.metrolist.shared.settings.MarkedItem
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.SkipMarks
import com.metrolist.shared.ui.components.AlertDialog
import com.metrolist.shared.ui.components.PopupVisibility
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

/** Settings row that opens [MarkedItemsDialog]; shown on every platform wherever the marks' switches live. */
@Composable
fun MarkedItemsSettingRow() {
    var open by remember { mutableStateOf(false) }
    val count =
        remember(Settings.shared.incognitoMarksJson, Settings.shared.skipMarksJson) { IncognitoMarks.items().size + SkipMarks.items().size }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.marked_items), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.marked_items_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PopupVisibility(visible = open) { MarkedItemsDialog(onDismiss = { open = false }) }
}

/** Everything marked incognito or skipped, whatever its level (song, artist, album, playlist), each removable. */
@Composable
fun MarkedItemsDialog(onDismiss: () -> Unit) {
    // Keyed on the stored JSON, which also changes when another device's marks are merged in.
    val incognito = remember(Settings.shared.incognitoMarksJson) { IncognitoMarks.items() }
    val skipped = remember(Settings.shared.skipMarksJson) { SkipMarks.items() }

    val incognitoTitle = stringResource(Res.string.marked_items_incognito)
    val skipTitle = stringResource(Res.string.marked_items_skip)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.marked_items)) },
        text = {
            if (incognito.isEmpty() && skipped.isEmpty()) {
                Text(stringResource(Res.string.marked_items_empty))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    markSection(incognitoTitle, incognito, IncognitoMarks)
                    markSection(skipTitle, skipped, SkipMarks)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.close)) }
        },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.markSection(
    title: String,
    marks: List<MarkedItem>,
    registry: MarkRegistry,
) {
    if (marks.isEmpty()) return
    item(key = "header:$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
    items(marks.sortedWith(compareBy({ it.kind }, { it.title.lowercase() })), key = { "$title:${it.kind}:${it.id}" }) { mark ->
        MarkedItemRow(mark) { registry.unmark(mark.kind, mark.id) }
    }
}

@Composable
private fun MarkedItemRow(
    mark: MarkedItem,
    onRemove: () -> Unit,
) {
    val name = mark.title.ifBlank { mark.id }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            when (mark.kind) {
                MarkKind.SONG -> MetrolistIcons.Outlined.MusicNote
                MarkKind.ARTIST -> MetrolistIcons.Outlined.Person
                MarkKind.ALBUM -> MetrolistIcons.Outlined.Album
                MarkKind.PLAYLIST -> MetrolistIcons.Outlined.LibraryMusic
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    when (mark.kind) {
                        MarkKind.SONG -> Res.string.song
                        MarkKind.ARTIST -> Res.string.artist
                        MarkKind.ALBUM -> Res.string.album
                        MarkKind.PLAYLIST -> Res.string.playlist
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(MetrolistIcons.Outlined.Close, contentDescription = stringResource(Res.string.marked_items_remove, name))
        }
    }
}
