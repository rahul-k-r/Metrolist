package com.metrolist.shared.ui.menu

import androidx.compose.runtime.Composable
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.incognito_mark_artist
import com.metrolist.shared.generated.resources.incognito_mark_song
import com.metrolist.shared.generated.resources.incognito_unmark_artist
import com.metrolist.shared.generated.resources.incognito_unmark_song
import com.metrolist.shared.generated.resources.skip_mark_song
import com.metrolist.shared.generated.resources.skip_unmark_song
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.settings.IncognitoMarks
import com.metrolist.shared.settings.MarkKind
import com.metrolist.shared.settings.MarkedItem
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.SkipMarks
import com.metrolist.shared.ui.components.PlayerMenuAction
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

/** Mark toggles for a song's menu. Each group only appears while its own switch in Settings is on. */
@Composable
fun itemMarkMenuActions(
    song: SongItem,
    onDismiss: () -> Unit,
): List<PlayerMenuAction> {
    val settings = Settings.shared
    val markSong = stringResource(Res.string.incognito_mark_song)
    val unmarkSong = stringResource(Res.string.incognito_unmark_song)
    val skip = stringResource(Res.string.skip_mark_song)
    val unskip = stringResource(Res.string.skip_unmark_song)
    val artist = song.artists.firstOrNull { !it.id.isNullOrBlank() }
    val markArtist = artist?.let { stringResource(Res.string.incognito_mark_artist, it.name) }
    val unmarkArtist = artist?.let { stringResource(Res.string.incognito_unmark_artist, it.name) }

    return buildList {
        if (settings.incognitoModeEnabled) {
            val songMark = MarkedItem(MarkKind.SONG, song.id, song.title, song.thumbnail)
            val songMarked = IncognitoMarks.isMarked(MarkKind.SONG, song.id)
            add(
                action(
                    MetrolistIcons.Filled.VisibilityOff,
                    if (songMarked) unmarkSong else markSong,
                    onDismiss,
                    { IncognitoMarks.toggle(songMark) },
                    selected = songMarked,
                ),
            )
            if (artist != null && markArtist != null && unmarkArtist != null) {
                val artistMark = MarkedItem(MarkKind.ARTIST, artist.id.orEmpty(), artist.name, artist.thumbnail)
                val artistMarked = IncognitoMarks.isMarked(MarkKind.ARTIST, artist.id)
                add(
                    action(
                        MetrolistIcons.Filled.VisibilityOff,
                        if (artistMarked) unmarkArtist else markArtist,
                        onDismiss,
                        { IncognitoMarks.toggle(artistMark) },
                        selected = artistMarked,
                    ),
                )
            }
        }
        if (settings.skipMarkingEnabled) {
            val skipMark = MarkedItem(MarkKind.SONG, song.id, song.title, song.thumbnail)
            val skipped = SkipMarks.isMarked(MarkKind.SONG, song.id)
            add(
                action(
                    MetrolistIcons.Rounded.SkipNext,
                    if (skipped) unskip else skip,
                    onDismiss,
                    { SkipMarks.toggle(skipMark) },
                    selected = skipped,
                ),
            )
        }
    }
}
