package com.metrolist.desktop.utils

/**
 * The tray library reports only single clicks, so a double click is two clicks inside [intervalMs]. The first
 * click is held back for that interval; a second one cancels it and runs [onDouble] instead.
 *
 * [schedule] runs a task after a delay and returns a canceller that reports whether it stopped the task.
 */
internal class TrayClickGate(
    private val intervalMs: () -> Long,
    private val schedule: (delayMs: Long, task: () -> Unit) -> () -> Boolean,
    private val onSingle: () -> Unit,
    private val onDouble: () -> Unit,
) {
    private var pendingCancel: (() -> Boolean)? = null

    @Synchronized
    fun click() {
        val cancel = pendingCancel
        pendingCancel = null
        if (cancel != null && cancel()) {
            onDouble()
            return
        }
        pendingCancel =
            schedule(intervalMs()) {
                synchronized(this) { pendingCancel = null }
                onSingle()
            }
    }
}
