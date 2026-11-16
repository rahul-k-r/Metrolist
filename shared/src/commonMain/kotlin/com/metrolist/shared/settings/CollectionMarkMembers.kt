package com.metrolist.shared.settings

import com.metrolist.shared.model.SongItem
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * The song ids inside marked albums and playlists. A mark on a collection matches its songs, but the ids are only known
 * once the collection has been loaded, so they are remembered here (per device, never synced) and refreshed whenever the
 * collection is loaded again.
 */
open class CollectionMembersStore(
    private val read: () -> String,
    private val write: (String) -> Unit,
) {
    private class Parsed(
        val source: String,
        val members: Map<String, Set<String>>,
    )

    @Volatile
    private var cached: Parsed? = null
    private val lock = SynchronizedObject()
    private val _version = MutableStateFlow(0)

    /** Bumps when any collection's songs change, so statistics and filters can refresh. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun parsed(): Parsed =
        synchronized(lock) {
            val source = read()
            cached?.takeIf { it.source == source }?.let { return@synchronized it }
            val map =
                try {
                    json.decodeFromString(serializer, source.ifBlank { "{}" }).mapValues { it.value.toSet() }
                } catch (_: Exception) {
                    emptyMap()
                }
            Parsed(source, map).also { cached = it }
        }

    fun key(
        kind: MarkKind,
        id: String,
    ) = "${kind.name}:$id"

    fun has(
        kind: MarkKind,
        id: String,
    ): Boolean = key(kind, id) in parsed().members

    fun contains(
        keys: Collection<String>,
        songId: String,
    ): Boolean {
        val members = parsed().members
        return keys.any { key -> members[key]?.contains(songId) == true }
    }

    fun songIds(keys: Collection<String>): Set<String> {
        val members = parsed().members
        return keys.flatMapTo(mutableSetOf()) { members[it].orEmpty() }
    }

    /** An empty list is ignored: a failed or partial load must never forget what was known. */
    fun set(
        kind: MarkKind,
        id: String,
        songIds: Collection<String>,
    ) {
        if (songIds.isEmpty()) return
        synchronized(lock) {
            val current = parsed().members
            val key = key(kind, id)
            val next = songIds.toSet()
            if (current[key] == next) return@synchronized
            save(current + (key to next))
        }
    }

    /** Forgets every collection not in [live]. */
    fun retain(live: Set<String>) {
        synchronized(lock) {
            val current = parsed().members
            if (current.keys.all { it in live }) return@synchronized
            save(current.filterKeys { it in live })
        }
    }

    suspend fun refresh(
        kind: MarkKind,
        id: String,
        load: suspend () -> List<SongItem>,
    ) {
        val songs =
            try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        set(kind, id, songs.map { it.id })
    }

    private fun save(members: Map<String, Set<String>>) {
        write(json.encodeToString(serializer, members.mapValues { it.value.toList() }))
        _version.value++
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    }
}

object CollectionMarkMembers : CollectionMembersStore(
    read = { Settings.shared.collectionMarkMembersJson },
    write = { Settings.shared.collectionMarkMembersJson = it },
) {
    // Outlives the menu that started a load: the menu closes the moment an action is chosen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastRefreshMs = mutableMapOf<String, Long>()
    private val refreshLock = SynchronizedObject()

    fun launchRefresh(
        kind: MarkKind,
        id: String,
        load: suspend () -> List<SongItem>,
    ) {
        scope.launch { refresh(kind, id, load) }
    }

    /** For a menu being opened on an already-marked collection: reloads at most once per [STALE_AFTER_MS]. */
    fun launchRefreshIfStale(
        kind: MarkKind,
        id: String,
        load: suspend () -> List<SongItem>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val key = key(kind, id)
        val due =
            synchronized(refreshLock) {
                val last = lastRefreshMs[key]
                (last == null || now - last > STALE_AFTER_MS).also { if (it) lastRefreshMs[key] = now }
            }
        if (due) launchRefresh(kind, id, load)
    }

    private const val STALE_AFTER_MS = 10L * 60 * 1000

    /**
     * Loads every marked album and playlist once, e.g. at startup, so marks that arrived from another device (or lists that
     * changed since) cover their current songs. [load] returns null for a collection this device cannot load.
     */
    suspend fun refreshMarked(load: suspend (MarkedItem) -> List<SongItem>?) {
        val marked =
            (IncognitoMarks.items() + SkipMarks.items())
                .filter { it.kind == MarkKind.ALBUM || it.kind == MarkKind.PLAYLIST }
                .distinctBy { key(it.kind, it.id) }
        retain(marked.mapTo(mutableSetOf()) { key(it.kind, it.id) })
        marked.forEach { item -> refresh(item.kind, item.id) { load(item).orEmpty() } }
    }
}
