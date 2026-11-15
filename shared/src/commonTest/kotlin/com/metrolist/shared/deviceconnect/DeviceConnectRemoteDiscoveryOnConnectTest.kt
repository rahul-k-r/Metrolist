package com.metrolist.shared.deviceconnect

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Discovery used to run only from the manager's maintenance loop (every ~5s, and a no-op while the playback port was
 * not yet attached or the peer not yet registered), so a controller that connected to an already-playing device sat
 * with no remote state, and therefore no mini player, until the next tick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceConnectRemoteDiscoveryOnConnectTest {
    @Test
    fun attachingPortAfterPeerConnectedRequestsRemoteState() =
        runTest {
            val sent = mutableListOf<DcSyncMessage>()
            val remote = controller(sent)
            try {
                remote.onPeerConnected("target", supportsPlaybackClock = true)
                runCurrent()
                remote.attach(IdlePort())
                runCurrent()
                assertTrue(sent.any { it.type == DcSyncMessageType.ATTACH && it.auto }, "no discovery sent: $sent")
            } finally {
                remote.close()
            }
        }

    private fun kotlinx.coroutines.test.TestScope.controller(sent: MutableList<DcSyncMessage>) =
        DeviceConnectRemote(this, { "controller" }, { _, message ->
            sent += message
            true
        }, monotonicClockMs = { testScheduler.currentTime + 1L })

    private class IdlePort : DeviceConnectPlaybackPort {
        override suspend fun snapshot(): DcLocalPlaybackState = DcLocalPlaybackState()

        override suspend fun apply(
            snapshot: DcPlaybackSnapshot,
            positionMs: Long,
            shouldCommit: () -> Boolean,
        ) = Unit

        override suspend fun seekTo(positionMs: Long) = Unit

        override suspend fun setControllerPlayback(playback: DcLocalPlaybackState?) = Unit

        override suspend fun setPlaying(playing: Boolean) = Unit

        override suspend fun setSpeed(speed: Float) = Unit

        override suspend fun setVolume(volume: Float) = Unit

        override suspend fun setAudioOutputEnabled(enabled: Boolean) = Unit
    }
}
