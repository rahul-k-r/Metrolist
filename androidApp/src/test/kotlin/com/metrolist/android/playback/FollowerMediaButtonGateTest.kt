package com.metrolist.android.playback

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class FollowerMediaButtonGateTest {
    private fun gate() = FollowerMediaButtonGate(RuntimeEnvironment.getApplication(), Looper.getMainLooper())

    @Test
    fun holdsASessionOnlyWhileFollowing() {
        val gate = gate()
        assertFalse(gate.isHeld)
        gate.setHeld(true)
        assertTrue(gate.isHeld)
        gate.setHeld(false)
        assertFalse(gate.isHeld)
    }

    @Test
    fun repeatedHoldsDoNotCreateADuplicateSession() {
        val gate = gate()
        gate.setHeld(true)
        gate.setHeld(true)
        assertTrue(gate.isHeld)
        gate.release()
        assertFalse(gate.isHeld)
        // A second session with the same id would throw if the first had leaked.
        gate.setHeld(true)
        assertTrue(gate.isHeld)
        gate.release()
    }
}
