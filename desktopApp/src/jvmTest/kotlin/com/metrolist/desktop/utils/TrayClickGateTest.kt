package com.metrolist.desktop.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class TrayClickGateTest {
    private class FakeScheduler {
        var task: (() -> Unit)? = null

        fun schedule(
            @Suppress("UNUSED_PARAMETER") delayMs: Long,
            run: () -> Unit,
        ): () -> Boolean {
            task = run
            return {
                val had = task === run
                if (had) task = null
                had
            }
        }

        fun fire() {
            val t = task
            task = null
            t?.invoke()
        }
    }

    private fun gate(
        scheduler: FakeScheduler,
        events: MutableList<String>,
    ) = TrayClickGate({ 400L }, scheduler::schedule, { events += "single" }, { events += "double" })

    @Test
    fun singleClickRunsAfterTheIntervalOnly() {
        val s = FakeScheduler()
        val events = mutableListOf<String>()
        val g = gate(s, events)
        g.click()
        assertEquals(emptyList(), events)
        s.fire()
        assertEquals(listOf("single"), events)
    }

    @Test
    fun secondClickInsideTheIntervalIsADoubleClickAndSuppressesTheSingle() {
        val s = FakeScheduler()
        val events = mutableListOf<String>()
        val g = gate(s, events)
        g.click()
        g.click()
        s.fire()
        assertEquals(listOf("double"), events)
    }

    @Test
    fun clicksAfterTheSingleHasRunStartAFreshSequence() {
        val s = FakeScheduler()
        val events = mutableListOf<String>()
        val g = gate(s, events)
        g.click()
        s.fire()
        g.click()
        s.fire()
        assertEquals(listOf("single", "single"), events)
    }
}
