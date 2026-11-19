package com.metrolist.android.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * Android hands media-button keys to one session per app that has played audio, preferring a session whose state
 * matches the app's audio state. While this device only mirrors another one it plays nothing, so an idle session
 * wins that slot over the mirroring one. The headset's keys then stop at this session, which ignores them, instead of
 * reaching the mirroring session and being forwarded to the real player.
 *
 * It is never added to the media service, so it has no notification or card, and it only exists while following.
 */
@UnstableApi
internal class FollowerMediaButtonGate(
    private val context: Context,
    private val looper: Looper,
) {
    private var session: MediaSession? = null

    val isHeld: Boolean get() = session != null

    fun setHeld(held: Boolean) {
        if (held == isHeld) return
        if (held) {
            session = MediaSession.Builder(context, IdlePlayer(looper)).setId(SESSION_ID).build()
        } else {
            release()
        }
    }

    fun release() {
        session?.release()
        session = null
    }

    private class IdlePlayer(
        looper: Looper,
    ) : SimpleBasePlayer(looper) {
        override fun getState(): State = State.Builder().setAvailableCommands(Player.Commands.EMPTY).build()
    }

    private companion object {
        const val SESSION_ID = "follower_media_button_gate"
    }
}
