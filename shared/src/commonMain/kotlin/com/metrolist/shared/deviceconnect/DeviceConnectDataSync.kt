package com.metrolist.shared.deviceconnect

import androidx.compose.runtime.snapshotFlow
import com.metrolist.shared.settings.LyricsSyncItem
import com.metrolist.shared.settings.MarkRegistry
import com.metrolist.shared.settings.MarkedItem
import com.metrolist.shared.settings.PortableSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

object DcDataKinds {
    const val MARKS = "marks"
    const val LYRICS = "lyrics"
    const val SETTINGS_REQUEST = "settings_request"
    const val SETTINGS_SNAPSHOT = "settings_snapshot"
    const val SETTINGS_PUSH = "settings_push"
}

/** App data carried between paired devices on top of the playback channel. */
@Serializable
data class DcDataMessage(
    val kind: String,
    val incognito: List<MarkedItem> = emptyList(),
    val skip: List<MarkedItem> = emptyList(),
    val settings: PortableSettings? = null,
    val lyrics: List<LyricsSyncItem> = emptyList(),
)

/** Source and sink for lyrics sync, so the sync logic stays independent of the database. */
interface LyricsSyncBridge {
    /** Last-change time per song that has something to sync. */
    fun stamps(): Map<String, Long>

    suspend fun export(ids: Collection<String>): List<LyricsSyncItem>

    /** Applies the items newer than local state; returns how many songs changed. */
    suspend fun apply(items: List<LyricsSyncItem>): Int
}

/**
 * Plain JSON per lyrics message. A frame is capped at [DEVICE_CONNECT_MAX_FRAME_BYTES] after AES-GCM and base64
 * (about 4/3 larger), so 40 KB leaves headroom for the envelope.
 */
internal const val LYRICS_MESSAGE_BUDGET_BYTES = 40_000

/** Packs [items] in order into messages within the budget; one song too large for any frame is skipped. */
internal fun chunkLyricsItems(items: List<LyricsSyncItem>): List<List<LyricsSyncItem>> {
    val overhead = DcJson.encodeToString(DcDataMessage(DcDataKinds.LYRICS)).encodeToByteArray().size
    val chunks = mutableListOf<List<LyricsSyncItem>>()
    var current = mutableListOf<LyricsSyncItem>()
    var size = overhead
    for (item in items) {
        val itemSize = DcJson.encodeToString(item).encodeToByteArray().size + 1
        if (overhead + itemSize > LYRICS_MESSAGE_BUDGET_BYTES) continue
        if (size + itemSize > LYRICS_MESSAGE_BUDGET_BYTES) {
            chunks += current
            current = mutableListOf()
            size = overhead
        }
        current += item
        size += itemSize
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
}

/** Settings another device wants to apply here; nothing is applied until the user accepts. */
data class IncomingSettingsPush(
    val fromDeviceId: String,
    val settings: PortableSettings,
    val changedCount: Int,
)

sealed interface DataSyncStatus {
    data class Applied(
        val deviceId: String,
        val changedCount: Int,
    ) : DataSyncStatus

    data class Sent(
        val deviceId: String,
    ) : DataSyncStatus

    data class Failed(
        val deviceId: String,
    ) : DataSyncStatus
}

/** Moves bytes to a paired device; false when it is gone or does not support data sync. */
fun interface DataSyncTransport {
    suspend fun send(
        deviceId: String,
        bytes: ByteArray,
    ): Boolean
}

/**
 * Keeps incognito and skip marks converged across connected devices, and offers one-shot settings push and pull.
 * Marks flow automatically; settings only move when the user asks, and a push waits for the receiver to accept.
 */
class DeviceConnectDataSync(
    private val scope: CoroutineScope,
    private val transport: DataSyncTransport,
    private val incognitoMarks: MarkRegistry,
    private val skipMarks: MarkRegistry,
    private val exportSettings: () -> PortableSettings,
    private val importSettings: (PortableSettings) -> Unit,
    private val settingsDifferences: (PortableSettings) -> Int,
    private val marksVersion: () -> Pair<String, String>,
    /** A peer can request our settings without a prompt here, so this is the place to refuse (e.g. incognito). */
    private val allowSettingsRequest: () -> Boolean = { true },
    private val pullTimeoutMs: Long = 30_000L,
    /** Resolved on use, because the lyrics database may not exist yet when Device Connect is built. */
    private val lyrics: () -> LyricsSyncBridge? = { null },
    private val lyricsVersion: () -> String = { "" },
) {
    private var lyricsSent: Map<String, Long> = emptyMap()

    private val peers = MutableStateFlow<Set<String>>(emptySet())
    private val pendingPulls = MutableStateFlow<Set<String>>(emptySet())
    private val _incoming = MutableStateFlow<IncomingSettingsPush?>(null)
    private val _status = MutableStateFlow<DataSyncStatus?>(null)

    val connectedPeers: StateFlow<Set<String>> = peers.asStateFlow()
    val incomingPush: StateFlow<IncomingSettingsPush?> = _incoming.asStateFlow()
    val status: StateFlow<DataSyncStatus?> = _status.asStateFlow()

    /** Wires the live peer set and incoming messages; call once. */
    fun start(
        peerIds: StateFlow<Set<String>>,
        messages: SharedFlow<Pair<String, ByteArray>>,
    ) {
        scope.launch {
            var known = emptySet<String>()
            peerIds.collect { current ->
                val added = current - known
                known = current
                peers.value = current
                pendingPulls.update { it intersect current }
                if (_incoming.value?.fromDeviceId !in current) _incoming.value = null
                added.forEach {
                    sendMarks(it)
                    sendLyrics(it, lyrics()?.stamps()?.keys.orEmpty())
                }
            }
        }
        scope.launch {
            lyricsSent = lyrics()?.stamps().orEmpty()
            snapshotFlow(lyricsVersion).distinctUntilChanged().drop(1).collect {
                val current = lyrics()?.stamps() ?: return@collect
                val changed = current.filter { (id, stamp) -> lyricsSent[id] != stamp }.keys
                lyricsSent = current
                if (changed.isNotEmpty()) peers.value.forEach { sendLyrics(it, changed) }
            }
        }
        scope.launch { messages.collect { (from, bytes) -> onMessage(from, bytes) } }
        scope.launch {
            snapshotFlow(marksVersion).distinctUntilChanged().drop(1).collect {
                peers.value.forEach { sendMarks(it) }
            }
        }
    }

    suspend fun onMessage(
        from: String,
        bytes: ByteArray,
    ) {
        val message =
            try {
                DcJson.decodeFromString<DcDataMessage>(bytes.decodeToString())
            } catch (_: Exception) {
                return
            }
        if (from !in peers.value) return
        when (message.kind) {
            DcDataKinds.MARKS -> {
                incognitoMarks.mergeFrom(message.incognito)
                skipMarks.mergeFrom(message.skip)
            }

            DcDataKinds.LYRICS -> {
                lyrics()?.let { bridge ->
                    bridge.apply(message.lyrics)
                    // Applied stamps are not local changes; counting them as sent stops them echoing back to peers.
                    val ids = message.lyrics.mapTo(HashSet()) { it.songId }
                    lyricsSent = lyricsSent + bridge.stamps().filterKeys { it in ids }
                }
            }

            DcDataKinds.SETTINGS_REQUEST -> {
                if (!allowSettingsRequest()) return
                send(from, DcDataMessage(DcDataKinds.SETTINGS_SNAPSHOT, settings = exportSettings()))
            }

            DcDataKinds.SETTINGS_SNAPSHOT -> {
                // Only a snapshot we asked for is applied; an unsolicited one is dropped.
                val snapshot = message.settings ?: return
                if (from !in pendingPulls.value) return
                pendingPulls.update { it - from }
                apply(from, snapshot)
            }

            DcDataKinds.SETTINGS_PUSH -> {
                val snapshot = message.settings ?: return
                _incoming.value = IncomingSettingsPush(from, snapshot, settingsDifferences(snapshot))
            }
        }
    }

    fun pullSettings(deviceId: String) {
        scope.launch {
            pendingPulls.update { it + deviceId }
            if (!send(deviceId, DcDataMessage(DcDataKinds.SETTINGS_REQUEST))) {
                pendingPulls.update { it - deviceId }
                _status.value = DataSyncStatus.Failed(deviceId)
                return@launch
            }
            // A snapshot arriving long after the request is no longer the one the user asked for.
            delay(pullTimeoutMs)
            pendingPulls.update { it - deviceId }
        }
    }

    fun pushSettings(deviceId: String) {
        scope.launch {
            val sent = send(deviceId, DcDataMessage(DcDataKinds.SETTINGS_PUSH, settings = exportSettings()))
            _status.value = if (sent) DataSyncStatus.Sent(deviceId) else DataSyncStatus.Failed(deviceId)
        }
    }

    fun acceptIncoming() {
        val incoming = _incoming.value ?: return
        _incoming.value = null
        apply(incoming.fromDeviceId, incoming.settings)
    }

    fun rejectIncoming() {
        _incoming.value = null
    }

    fun clearStatus() {
        _status.value = null
    }

    private fun apply(
        from: String,
        snapshot: PortableSettings,
    ) {
        val changed = settingsDifferences(snapshot)
        _status.value =
            try {
                importSettings(snapshot)
                DataSyncStatus.Applied(from, changed)
            } catch (_: Exception) {
                DataSyncStatus.Failed(from)
            }
    }

    private suspend fun sendMarks(deviceId: String) {
        val incognito = incognitoMarks.entries()
        val skip = skipMarks.entries()
        if (incognito.isEmpty() && skip.isEmpty()) return
        send(deviceId, DcDataMessage(DcDataKinds.MARKS, incognito = incognito, skip = skip))
    }

    private suspend fun sendLyrics(
        deviceId: String,
        ids: Collection<String>,
    ) {
        if (ids.isEmpty()) return
        val bridge = lyrics() ?: return
        chunkLyricsItems(bridge.export(ids)).forEach {
            send(deviceId, DcDataMessage(DcDataKinds.LYRICS, lyrics = it))
        }
    }

    private suspend fun send(
        deviceId: String,
        message: DcDataMessage,
    ): Boolean = transport.send(deviceId, DcJson.encodeToString(message).encodeToByteArray())
}
