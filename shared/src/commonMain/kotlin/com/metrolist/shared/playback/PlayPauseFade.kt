package com.metrolist.shared.playback

import kotlin.math.exp

/**
 * Gain ramp for pausing and resuming. Retargeting continues from the gain reached so far, so a quick
 * pause-then-play reverses smoothly instead of jumping. The curve is constant-dB per unit time, which
 * sounds like an even fade; a linear amplitude ramp is mostly audible at its quiet end.
 */
class PlayPauseFadeRamp {
    private var startGain = 1f
    private var targetGain = 1f
    private var startMs = 0L
    private var durationMs = 0L

    val target: Float get() = targetGain

    fun fadeTo(
        target: Float,
        nowMs: Long,
        durationMs: Long,
    ) {
        startGain = gainAt(nowMs)
        targetGain = target.coerceIn(0f, 1f)
        startMs = nowMs
        this.durationMs = durationMs.coerceAtLeast(1L)
    }

    /** Jumps straight to [gain], for a new track or an immediate pause. */
    fun reset(gain: Float = 1f) {
        startGain = gain
        targetGain = gain
        durationMs = 0L
    }

    fun isDone(nowMs: Long): Boolean = durationMs <= 0L || nowMs - startMs >= durationMs

    fun gainAt(nowMs: Long): Float {
        if (durationMs <= 0L) return targetGain
        val progress = ((nowMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
        return if (targetGain >= startGain) {
            startGain + (targetGain - startGain) * rise(progress)
        } else {
            targetGain + (startGain - targetGain) * rise(1f - progress)
        }
    }

    private fun rise(progress: Float): Float {
        val floor = exp(-FADE_STEEPNESS)
        return ((exp(-FADE_STEEPNESS * (1f - progress)) - floor) / (1f - floor)).coerceIn(0f, 1f)
    }

    private companion object {
        const val FADE_STEEPNESS = 5f
    }
}
