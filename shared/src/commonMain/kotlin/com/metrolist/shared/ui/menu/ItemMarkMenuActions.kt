package com.metrolist.shared.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.incognito_mark_album
import com.metrolist.shared.generated.resources.incognito_mark_artist
import com.metrolist.shared.generated.resources.incognito_mark_playlist
import com.metrolist.shared.generated.resources.incognito_mark_song
import com.metrolist.shared.generated.resources.incognito_unmark_album
import com.metrolist.shared.generated.resources.incognito_unmark_artist
import com.metrolist.shared.generated.resources.incognito_unmark_playlist
import com.metrolist.shared.generated.resources.incognito_unmark_song
import com.metrolist.shared.generated.resources.skip_mark_album
import com.metrolist.shared.generated.resources.skip_mark_playlist
import com.metrolist.shared.generated.resources.skip_mark_song
import com.metrolist.shared.generated.resources.skip_unmark_album
import com.metrolist.shared.generated.resources.skip_unmark_playlist
import com.metrolist.shared.generated.resources.skip_unmark_song
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.settings.CollectionMarkMembers
import com.metrolist.shared.settings.IncognitoMarks
import com.metrolist.shared.settings.MarkKind
import com.metrolist.shared.settings.MarkedItem
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.SkipMarks
import com.metrolist.shared.ui.components.PlayerMenuAction
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Mark toggles for an album or playlist menu. The mark covers every song inside, so the songs are loaded (by
 * [loadSongs]) when the mark is set and again when the menu opens on an already-marked collection.
 */
@Composable
fun collectionMarkMenuActions(
    kind: MarkKind,
    id: String,
    title: String,
    thumbnail: String?,
    loadSongs: suspend () -> List<SongItem>,
    onDismiss: () -> Unit,
): List<PlayerMenuAction> {
    require(kind == MarkKind.ALBUM || kind == MarkKind.PLAYLIST)
    val settings = Settings.shared
    val isAlbum = kind == MarkKind.ALBUM
    val markLabel = stringResource(if (isAlbum) Res.string.incognito_mark_album else Res.string.incognito_mark_playlist)
    val unmarkLabel = stringResource(if (isAlbum) Res.string.incognito_unmark_album else Res.string.incognito_unmark_playlist)
    val skipLabel = stringResource(if (isAlbum) Res.string.skip_mark_album else Res.string.skip_mark_playlist)
    val unskipLabel = stringResource(if (isAlbum) Res.string.skip_unmark_album else Res.string.skip_unmark_playlist)
    // Built-in lists (liked, history, downloads...) are generated per device, so a mark on one cannot be loaded back.
    if (id.isBlank() || id.startsWith("auto:")) return emptyList()
    val item = MarkedItem(kind, id, title, thumbnail)
    val incognito = settings.incognitoModeEnabled && IncognitoMarks.isMarked(kind, id)
    val skipped = settings.skipMarkingEnabled && SkipMarks.isMarked(kind, id)
    LaunchedEffect(id, incognito, skipped) {
        if (incognito || skipped) CollectionMarkMembers.launchRefreshIfStale(kind, id, loadSongs)
    }

    return buildList {
        if (settings.incognitoModeEnabled) {
            add(
                action(
                    MetrolistIcons.Filled.VisibilityOff,
                    if (incognito) unmarkLabel else markLabel,
                    onDismiss,
                    {
                        IncognitoMarks.toggle(item)
                        if (!incognito) CollectionMarkMembers.launchRefresh(kind, id, loadSongs)
                    },
                    selected = incognito,
                ),
            )
        }
        if (settings.skipMarkingEnabled) {
            add(
                action(
                    MetrolistIcons.Rounded.SkipNext,
                    if (skipped) unskipLabel else skipLabel,
                    onDismiss,
                    {
                        SkipMarks.toggle(item)
                        if (!skipped) CollectionMarkMembers.launchRefresh(kind, id, loadSongs)
                    },
                    selected = skipped,
                ),
            )
        }
    }
}

/** Incognito toggle for an artist's own menu (artists have no skip mark). */
@Composable
fun artistMarkMenuActions(
    artist: ArtistItem,
    onDismiss: () -> Unit,
): List<PlayerMenuAction> {
    if (!Settings.shared.incognitoModeEnabled || artist.id.isBlank()) return emptyList()
    val mark = stringResource(Res.string.incognito_mark_artist, artist.title)
    val unmark = stringResource(Res.string.incognito_unmark_artist, artist.title)
    val marked = IncognitoMarks.isMarked(MarkKind.ARTIST, artist.id)
    return listOf(
        action(
            MetrolistIcons.Filled.VisibilityOff,
            if (marked) unmark else mark,
            onDismiss,
            { IncognitoMarks.toggle(MarkedItem(MarkKind.ARTIST, artist.id, artist.title, artist.thumbnail)) },
            selected = marked,
        ),
    )
}

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
