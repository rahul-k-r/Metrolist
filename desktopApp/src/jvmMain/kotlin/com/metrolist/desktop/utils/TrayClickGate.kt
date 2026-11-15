package com.metrolist.desktop.utils

/**
 * The tray library reports only single clicks, so a double click is two clicks inside [intervalMs]. The first
 * click is held back for that interval; a second one cancels it and runs [onDouble] instead.
 *
 * [schedule] runs a task after a delay and returns a canceller. The gate never trusts the canceller's result:
 * a task that already started still checks, under the lock, that it is the click still pending.
 */
internal class TrayClickGate(
    private val intervalMs: () -> Long,
    private val schedule: (delayMs: Long, task: () -> Unit) -> () -> Boolean,
    private val onSingle: () -> Unit,
    private val onDouble: () -> Unit,
) {
    private class Pending {
        var cancel: () -> Boolean = { false }
    }

    private var pending: Pending? = null

    @Synchronized
    fun click() {
        val previous = pending
        if (previous != null) {
            pending = null
            previous.cancel()
            onDouble()
            return
        }
        val click = Pending()
        pending = click
        click.cancel = schedule(intervalMs()) { runSingle(click) }
    }

    private fun runSingle(click: Pending) {
        synchronized(this) {
            if (pending !== click) return
            pending = null
        }
        onSingle()
    }
}
