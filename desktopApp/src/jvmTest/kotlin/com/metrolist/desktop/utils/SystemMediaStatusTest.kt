package com.metrolist.desktop.utils

import dev.nucleusframework.media.control.MediaPlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemMediaStatusTest {
    @Test
    fun playingLocallyIsReportedAsPlaying() {
        assertEquals(MediaPlaybackStatus.PLAYING, systemMediaStatus(hasTrack = true, isPlaying = true, followingRemote = false))
    }

    @Test
    fun mirroringAnotherDeviceNeverReportsPlaying() {
        assertEquals(MediaPlaybackStatus.PAUSED, systemMediaStatus(hasTrack = true, isPlaying = true, followingRemote = true))
        assertEquals(MediaPlaybackStatus.PAUSED, systemMediaStatus(hasTrack = true, isPlaying = false, followingRemote = true))
    }

    @Test
    fun noTrackIsStopped() {
        assertEquals(MediaPlaybackStatus.STOPPED, systemMediaStatus(hasTrack = false, isPlaying = true, followingRemote = true))
        assertEquals(MediaPlaybackStatus.STOPPED, systemMediaStatus(hasTrack = false, isPlaying = false, followingRemote = false))
    }
}
