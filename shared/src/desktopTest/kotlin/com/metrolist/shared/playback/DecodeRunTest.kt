package com.metrolist.shared.playback

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecodeRunTest {
    /** Threads that touched ffmpeg must never exit, so consecutive runs have to land on the same live workers. */
    @Test
    fun workersSurviveRunsAndAreReused() {
        val threads = mutableSetOf<Thread>()
        repeat(20) {
            val run = startDecodeRun { synchronized(threads) { threads += Thread.currentThread() } }
            run.join()
        }
        assertTrue(threads.size <= 4, "expected a bounded pool, saw ${threads.size} threads")
        assertTrue(threads.all { it.isAlive }, "a decode worker exited")
    }

    @Test
    fun interruptStopsTheRunAndJoinWaitsForIt() {
        val started = CountDownLatch(1)
        var interrupted = false
        val run =
            startDecodeRun {
                started.countDown()
                try {
                    Thread.sleep(30_000)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(run.isAlive)
        run.interrupt()
        run.join()
        assertTrue(interrupted)
        assertFalse(run.isAlive)
    }

    @Test
    fun staleInterruptDoesNotReachTheNextRunOnTheSameWorker() {
        val first = startDecodeRun {}
        first.join()
        first.interrupt()
        var sawInterrupt = true
        startDecodeRun { sawInterrupt = Thread.currentThread().isInterrupted }.join()
        assertEquals(false, sawInterrupt)
    }
}
