package com.metrolist.shared.playback.ffmpeg

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FfmpegWorkersTest {
    /** Threads that touched ffmpeg must never exit, so consecutive workers have to land on the same live threads. */
    @Test
    fun threadsSurviveWorkersAndAreReused() {
        val threads = mutableSetOf<Thread>()
        repeat(20) {
            startFfmpegWorker("test") { synchronized(threads) { threads += Thread.currentThread() } }.join()
        }
        assertTrue(threads.size < 20, "threads were not reused: ${threads.size}")
        assertTrue(threads.all { it.isAlive }, "a pooled thread exited")
    }

    @Test
    fun nameAndPriorityApplyOnlyWhileRunning() {
        var seen = "" to 0
        startFfmpegWorker("AudioTest-Thread", Thread.NORM_PRIORITY + 2) {
            seen = Thread.currentThread().name to Thread.currentThread().priority
        }.join()
        assertEquals("AudioTest-Thread" to Thread.NORM_PRIORITY + 2, seen)
        var after = "" to 0
        startFfmpegWorker("other") { after = Thread.currentThread().name to Thread.currentThread().priority }.join()
        assertEquals("other" to Thread.NORM_PRIORITY, after)
    }

    @Test
    fun interruptStopsTheWorkerAndJoinWaitsForIt() {
        val started = CountDownLatch(1)
        var interrupted = false
        val worker =
            startFfmpegWorker("test") {
                started.countDown()
                try {
                    Thread.sleep(30_000)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(worker.isAlive)
        worker.interrupt()
        worker.join()
        assertTrue(interrupted)
        assertFalse(worker.isAlive)
    }

    @Test
    fun staleInterruptDoesNotReachTheNextWorkerOnTheSameThread() {
        val first = startFfmpegWorker("test") {}
        first.join()
        first.interrupt()
        var sawInterrupt = true
        startFfmpegWorker("test") { sawInterrupt = Thread.currentThread().isInterrupted }.join()
        assertEquals(false, sawInterrupt)
    }

    @Test
    fun boundedJoinReturnsWhileWorkerStillRuns() {
        val release = CountDownLatch(1)
        val worker = startFfmpegWorker("test") { release.await() }
        worker.join(50)
        assertTrue(worker.isAlive)
        release.countDown()
        worker.join()
        assertFalse(worker.isAlive)
    }
}
