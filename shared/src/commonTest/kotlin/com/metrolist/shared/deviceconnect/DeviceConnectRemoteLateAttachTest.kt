package com.metrolist.shared.deviceconnect

import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.MusicQueueSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceConnectRemoteLateAttachTest {
    @Test
    fun controllerAttachedWhileTargetLoadsSeesPlayingOnceTargetStarts() =
        runTest {
            val sent = mutableListOf<DcSyncMessage>()
            val port = RecordingPort().apply { state = loading() }
            val remote = target(sent)
            try {
                remote.attach(port)
                remote.onPeerConnected("controller", supportsPlaybackClock = true)
                remote.onMessage("controller", DcSyncMessage(DcSyncMessageType.ATTACH))
                runCurrent()
                assertEquals(false, sent.last { it.type == DcSyncMessageType.STATE }.snapshot?.playback?.playing)
                sent.clear()

                port.state = port.state.copy(playing = true, ready = true, positionMs = 5_000L)
                remote.localStateChanged()
                advanceTimeBy(1_001L)
                runCurrent()

                val update = sent.last { it.type == DcSyncMessageType.UPDATE }
                assertEquals(true, update.playing)
                assertTrue(update.positionMs >= 5_000L)
            } finally {
                remote.close()
            }
        }

    @Test
    fun playFromControllerNeverSeeksAnAlreadyPlayingTargetBackwards() =
        runTest {
            val sent = mutableListOf<DcSyncMessage>()
            val port = RecordingPort().apply { state = loading() }
            val remote = target(sent)
            try {
                remote.attach(port)
                remote.onPeerConnected("controller", supportsPlaybackClock = true)
                remote.onMessage("controller", DcSyncMessage(DcSyncMessageType.ATTACH))
                runCurrent()
                val session = remote.state.value.sessionId

                // Target starts playing without any observation reaching the remote.
                port.state = port.state.copy(playing = true, ready = true, positionMs = 5_000L)
                advanceTimeBy(2_000L)
                port.state = port.state.copy(positionMs = 7_000L)
                port.applied.clear()

                remote.onMessage(
                    "controller",
                    DcSyncMessage(
                        DcSyncMessageType.COMMAND,
                        sessionId = session,
                        command = DcPlaybackCommand(DcPlaybackAction.PLAY),
                        queueRevision = 1L,
                    ),
                )
                runCurrent()

                assertTrue(port.applied.all { it >= 7_000L }, "target was re-applied at ${port.applied}")
                assertTrue(port.state.positionMs >= 7_000L)
            } finally {
                remote.close()
            }
        }

    private fun kotlinx.coroutines.test.TestScope.target(sent: MutableList<DcSyncMessage>) =
        DeviceConnectRemote(this, { "target" }, { _, message ->
            sent += message
            true
        }, monotonicClockMs = { testScheduler.currentTime + 1L })

    private fun loading(): DcLocalPlaybackState {
        val songs = listOf("first", "second").map { SongItem(it, it, null, emptyList(), null, duration = 300) }
        return DcLocalPlaybackState(
            queue = MusicQueueSnapshot(songs, songs, 0, originalIndices = listOf(0, 1)),
            positionMs = 0L,
            durationMs = 0L,
            playing = false,
            ready = false,
        )
    }

    private class RecordingPort : DeviceConnectPlaybackPort {
        var state = DcLocalPlaybackState()
        val applied = mutableListOf<Long>()

        override suspend fun snapshot(): DcLocalPlaybackState = state

        override suspend fun apply(
            snapshot: DcPlaybackSnapshot,
            positionMs: Long,
            shouldCommit: () -> Boolean,
        ) {
            applied += positionMs
            if (shouldCommit()) state = snapshot.playback.copy(positionMs = positionMs, volume = state.volume)
        }

        override suspend fun seekTo(positionMs: Long) {
            applied += positionMs
            state = state.copy(positionMs = positionMs)
        }

        override suspend fun setControllerPlayback(playback: DcLocalPlaybackState?) = Unit

        override suspend fun setPlaying(playing: Boolean) {
            state = state.copy(playing = playing)
        }

        override suspend fun setSpeed(speed: Float) {
            state = state.copy(speed = speed)
        }

        override suspend fun setVolume(volume: Float) {
            state = state.copy(volume = volume)
        }

        override suspend fun setAudioOutputEnabled(enabled: Boolean) = Unit
    }
}
