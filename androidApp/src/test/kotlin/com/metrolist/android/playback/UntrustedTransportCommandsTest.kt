package com.metrolist.android.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UntrustedTransportCommandsTest {
    @Test
    fun unverifiedControllerCanControlTransportAndReadQueue() {
        val commands = untrustedTransportPlayerCommands()
        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SHUFFLE_MODE,
        ).forEach { assertTrue("missing $it", commands.contains(it)) }
    }

    @Test
    fun unverifiedControllerCannotReplaceTheQueue() {
        val commands = untrustedTransportPlayerCommands()
        listOf(
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
        ).forEach { assertFalse("unexpected $it", commands.contains(it)) }
    }
}
