package com.metrolist.desktop.utils

import com.metrolist.shared.db.MusicDatabase
import com.metrolist.shared.db.entities.toSongItem
import com.metrolist.shared.model.SongItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.context.GlobalContext

/** Most recently played distinct songs, newest first, for the tray menu. */
internal object TrayRecentSongs {
    private val database: MusicDatabase by lazy { GlobalContext.get().get() }

    fun flow(limit: Int): Flow<List<SongItem>> =
        database.dao().events().map { events ->
            recentDistinct(events.asSequence().map { it.song.toSongItem() }, { it.id }, limit)
        }
}

internal fun <T> recentDistinct(
    newestFirst: Sequence<T>,
    id: (T) -> String,
    limit: Int,
): List<T> {
    val seen = HashSet<String>()
    return newestFirst.filter { seen.add(id(it)) }.take(limit).toList()
}
