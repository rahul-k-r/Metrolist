package com.metrolist.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayPauseFadeRampTest {
    @Test
    fun startsAtFullGainAndIsDone() {
        val ramp = PlayPauseFadeRamp()
        assertEquals(1f, ramp.gainAt(0))
        assertTrue(ramp.isDone(0))
    }

    @Test
    fun fadeOutRunsFromFullToSilentAndFallsMonotonically() {
        val ramp = PlayPauseFadeRamp()
        ramp.fadeTo(0f, nowMs = 0, durationMs = 300)
        assertEquals(1f, ramp.gainAt(0), 1e-4f)
        assertEquals(0f, ramp.gainAt(300), 1e-4f)
        var previous = 1f
        for (t in 0..300 step 10) {
            val gain = ramp.gainAt(t.toLong())
            assertTrue(gain <= previous + 1e-6f, "gain rose at $t")
            previous = gain
        }
    }

    @Test
    fun fadeInRisesMonotonicallyToFull() {
        val ramp = PlayPauseFadeRamp()
        ramp.reset(0f)
        ramp.fadeTo(1f, nowMs = 0, durationMs = 300)
        assertEquals(0f, ramp.gainAt(0), 1e-4f)
        assertEquals(1f, ramp.gainAt(300), 1e-4f)
        var previous = 0f
        for (t in 0..300 step 10) {
            val gain = ramp.gainAt(t.toLong())
            assertTrue(gain >= previous - 1e-6f, "gain fell at $t")
            previous = gain
        }
    }

    @Test
    fun pausingThenResumingMidFadeContinuesFromTheCurrentGain() {
        val ramp = PlayPauseFadeRamp()
        ramp.fadeTo(0f, nowMs = 0, durationMs = 300)
        val mid = ramp.gainAt(100)
        ramp.fadeTo(1f, nowMs = 100, durationMs = 300)
        assertEquals(mid, ramp.gainAt(100), 1e-4f)
        assertEquals(1f, ramp.gainAt(400), 1e-4f)
    }

    @Test
    fun noSingleStepIsLargerThanAFractionOfFullScale() {
        val ramp = PlayPauseFadeRamp()
        ramp.fadeTo(0f, nowMs = 0, durationMs = 300)
        var previous = 1f
        var worst = 0f
        for (t in 0..300 step 8) {
            val gain = ramp.gainAt(t.toLong())
            worst = maxOf(worst, previous - gain)
            previous = gain
        }
        assertTrue(worst < 0.25f, "largest step was $worst")
    }
}
