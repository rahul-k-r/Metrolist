package com.metrolist.shared.playback.ffmpeg

import java.util.concurrent.CountDownLatch
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * One unit of ffmpeg-touching work running on a pooled thread.
 *
 * The bundled ffmpeg natives register a thread-exit hook that frees memory it did not allocate, so any thread that
 * has used ffmpeg and then ends aborts the process with heap corruption (0xc0000374). Workers therefore come from a
 * pool whose threads never time out: idle ones are reused instead of exiting.
 */
internal class FfmpegWorker {
    private val done = CountDownLatch(1)
    private var thread: Thread? = null

    val isAlive: Boolean get() = done.count > 0

    @Synchronized
    fun interrupt() {
        thread?.interrupt()
    }

    fun join() = done.await()

    /** Waits up to [millis]; returns without throwing when the worker is still running. */
    fun join(millis: Long) {
        done.await(millis, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun isCurrent(): Boolean = thread === Thread.currentThread()

    internal fun runOn(
        worker: Thread,
        name: String,
        priority: Int,
        body: () -> Unit,
    ) {
        val previousName = worker.name
        val previousPriority = worker.priority
        worker.name = name
        worker.priority = priority
        synchronized(this) { thread = worker }
        try {
            body()
        } finally {
            synchronized(this) { thread = null }
            // A stale interrupt must not leak into the next task that reuses this thread.
            Thread.interrupted()
            worker.name = previousName
            worker.priority = previousPriority
            done.countDown()
        }
    }
}

private val ffmpegWorkerPool =
    ThreadPoolExecutor(0, Int.MAX_VALUE, Long.MAX_VALUE, TimeUnit.NANOSECONDS, SynchronousQueue()) { task ->
        Thread(task, "Ffmpeg-Worker").apply { isDaemon = true }
    }

internal fun startFfmpegWorker(
    name: String,
    priority: Int = Thread.NORM_PRIORITY,
    body: () -> Unit,
): FfmpegWorker {
    val worker = FfmpegWorker()
    ffmpegWorkerPool.execute { worker.runOn(Thread.currentThread(), name, priority, body) }
    return worker
}
