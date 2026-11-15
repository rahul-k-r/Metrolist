package com.metrolist.shared.deviceconnect

import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.MusicQueueSnapshot
import kotlinx.serialization.Serializable

@Serializable
enum class DcRepeatMode { OFF, ALL, ONE }

@Serializable
enum class DcPlaybackAction {
    PLAY,
    PAUSE,
    SEEK,
    NEXT,
    PREVIOUS,
    SELECT,
    REPLACE_QUEUE,
    ADD,
    PLAY_NEXT,
    REMOVE,
    MOVE,
    REORDER,
    CLEAR,
    SHUFFLE,
    REPEAT,
    SPEED,
    PITCH,
    VOLUME,
    RESTORE_QUEUE,
    REPLACE_CURRENT,

    /** Switch the target between audio and video. [DcPlaybackCommand.enabled] is the wanted mode, null toggles. */
    VIDEO_MODE,
}

@Serializable
data class DcPlaybackCommand(
    val action: DcPlaybackAction,
    val songs: List<SongItem> = emptyList(),
    val index: Int = -1,
    val toIndex: Int = -1,
    val positionMs: Long = 0L,
    val enabled: Boolean? = null,
    val preserveHistory: Boolean = true,
    val repeatMode: DcRepeatMode? = null,
    val speed: Float = 1f,
    val pitch: Float? = null,
    val title: String? = null,
    val finite: Boolean = true,
    val indices: Set<Int> = emptySet(),
    val queue: MusicQueueSnapshot? = null,
    val expectedTrackRevision: Long? = null,
    val order: List<Int> = emptyList(),
    val volume: Float? = null,
    val preserveCurrentTrack: Boolean = false,
)

@Serializable
data class DcScrobbleSession(
    val songId: String,
    val startedAtTimestamp: Long,
    val accumulatedPlayedMs: Long,
    val completed: Boolean,
    val pending: Boolean,
)

@Serializable
data class DcLocalPlaybackState(
    val queue: MusicQueueSnapshot = MusicQueueSnapshot(),
    val title: String? = null,
    val repeatMode: DcRepeatMode = DcRepeatMode.OFF,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val pitch: Float? = null,
    val ready: Boolean = false,
    val finite: Boolean = true,
    val scrobbleSession: DcScrobbleSession? = null,
    val volume: Float = 1f,
)

/** The target device's playback at one instant; [playback] position is valid at [anchorTimeMs] on the target's clock. */
@Serializable
data class DcPlaybackSnapshot(
    val sessionId: String,
    val revision: Long,
    val queueRevision: Long,
    val trackRevision: Long,
    val playback: DcLocalPlaybackState,
    val anchorTimeMs: Long = 0L,
    val queueStateId: String = "",
)

@Serializable
enum class DcSyncError {
    CONFLICT,
    UNSHAREABLE_QUEUE,
    NOT_ALLOWED,
    STALE_QUEUE,
    UNAVAILABLE,
    TOO_LARGE,
    INVALID_MESSAGE,
}

data class DcSyncState(
    val sessionId: String? = null,
    val targetId: String? = null,
    val controllerIds: Set<String> = emptySet(),
    val playbackAvailable: Boolean = false,
    val error: DcSyncError? = null,
)

interface DeviceConnectPlaybackPort {
    val canStartDefaultPlayback: Boolean get() = true

    suspend fun snapshot(): DcLocalPlaybackState

    suspend fun apply(
        snapshot: DcPlaybackSnapshot,
        positionMs: Long,
        shouldCommit: () -> Boolean,
    )

    suspend fun setControllerPlayback(playback: DcLocalPlaybackState?)

    suspend fun setPlaying(playing: Boolean)

    suspend fun seekTo(positionMs: Long)

    suspend fun setSpeed(speed: Float)

    suspend fun setVolume(volume: Float)

    suspend fun setAudioOutputEnabled(enabled: Boolean)

    /** A controller asked this target to show video ([enabled] true), audio only (false), or to flip (null). */
    suspend fun setVideoMode(enabled: Boolean?) = Unit

    suspend fun extendQueue(shouldCommit: () -> Boolean): DcLocalPlaybackState? = null

    suspend fun acknowledge(snapshot: DcPlaybackSnapshot) = Unit

    /**
     * A session was just opened around playback this device started on its own. Ports that report per-session
     * state (desired playing, readiness) must seed it from [snapshot], or the first snapshot() after the session
     * opens reports a paused, unready player and the target announces itself as paused.
     */
    suspend fun adoptLocalSession(snapshot: DcPlaybackSnapshot) = Unit

    fun setExternalReportingEnabled(
        enabled: Boolean,
        session: DcScrobbleSession?,
    ) = Unit
}

@Serializable
enum class DcSyncMessageType {
    ATTACH,

    TRANSFER,

    PULL,
    STATE,
    UPDATE,
    COMMAND,

    RELEASE,
    ERROR,
}

@Serializable
data class DcSyncMessage(
    val type: DcSyncMessageType,
    val sessionId: String? = null,
    val snapshot: DcPlaybackSnapshot? = null,
    val command: DcPlaybackCommand? = null,
    val revision: Long = 0L,
    val queueRevision: Long = 0L,
    val trackRevision: Long = 0L,
    val index: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val pitch: Float? = null,
    val repeatMode: DcRepeatMode = DcRepeatMode.OFF,
    val seek: Boolean = false,
    val ready: Boolean = true,
    val scrobbleSession: DcScrobbleSession? = null,
    val volume: Float = 1f,
    val auto: Boolean = false,
    val pause: Boolean = false,
    val error: DcSyncError? = null,
    val queueStateId: String = "",
)
