package com.metrolist.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendThrottleTest {
    @Test
    fun aFastBurstSendsOncePerInterval() {
        var now = 0L
        val throttle = SendThrottle(80L) { now }
        var sent = 0
        repeat(50) {
            if (throttle.tryAcquire()) sent++
            now += 4L
        }
        // 200 ms of dragging at 4 ms per value: one send at t=0, then every 80 ms.
        assertEquals(3, sent)
    }

    @Test
    fun firstCallAlwaysGoesThroughAndTheNextWaits() {
        var now = 1_000L
        val throttle = SendThrottle(80L) { now }
        assertTrue(throttle.tryAcquire())
        assertFalse(throttle.tryAcquire())
        now += 80L
        assertTrue(throttle.tryAcquire())
    }
}
