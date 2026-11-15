package com.metrolist.shared.ui.components.lyrics

import com.metrolist.shared.api.lyrics.LyricsUtils
import com.metrolist.shared.model.LyricsEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real word-synced (enhanced LRC) lyrics that misbehaved in the player; see sweater-weather.lrc. */
class SweaterWeatherLyricsTest {
    private val raw =
        checkNotNull(javaClass.getResource("/lyrics/sweater-weather.lrc")) { "fixture missing" }.readText()
    private val tokenCounts = raw.lines().filter { it.isNotBlank() }.map { Regex("""<\d+:\d{2}[.:]\d+>""").findAll(it).count() }
    private val lines: List<LyricsEntry> = LyricsUtils.parseLyrics(raw)

    private fun ms(seconds: Double) = Math.round(seconds * 1000.0)

    @Test
    fun parserKeepsEveryLineAndWord() {
        assertEquals(tokenCounts.size, lines.size)
        lines.forEachIndexed { i, line ->
            assertEquals(tokenCounts[i], line.words!!.size, "word count of line $i '${line.text}'")
            assertEquals(line.words!!.first().startTime, line.time / 1000.0, 0.0005, "line $i first word start")
        }
        assertEquals(lines.map { it.time }, lines.map { it.time }.sorted())
    }

    @Test
    fun wordsInsideALineNeverOverlapOrRunBackwards() {
        lines.forEachIndexed { i, line ->
            val words = line.words!!
            words.forEachIndexed { w, word ->
                assertTrue(word.endTime >= word.startTime, "line $i word $w '${word.text}' ends before it starts")
                if (w + 1 < words.size) {
                    assertTrue(word.endTime <= words[w + 1].startTime + 1e-9, "line $i word $w overlaps the next word")
                }
            }
        }
    }

    @Test
    fun noWordRunsFarPastItsLinesSuccessor() {
        // A word's end is the next line's start, so a trailing word must never extend into a later line.
        lines.forEachIndexed { i, line ->
            val next = lines.getOrNull(i + 1) ?: return@forEachIndexed
            val last = line.words!!.last()
            if (ms(last.startTime) < next.time) {
                assertTrue(ms(last.endTime) <= next.time, "line $i last word '${last.text}' ends after line ${i + 1} starts")
            }
        }
    }

    @Test
    fun theOverlappingLinesAreBothResolvedAtTheirOwnTimes() {
        for ((first, second) in listOf(40 to 41, 49 to 50).filter { it.second < lines.size }) {
            val a = lines[first]
            val b = lines[second]
            if (b.time < ms(a.words!!.last().endTime)) {
                // Overlap exists in the source; the engine must keep line `first` active until its own end.
                val engine = engine(true)
                val t = b.time + 50
                assertTrue(first in engine.update(t, true, true, seekGeneration = 0).activeLineIndices, "line $first at $t")
            }
        }
    }

    @Test
    fun forwardPlaybackSelectsOneAdvancingPrimaryLine() {
        for (byRealTiming in listOf(false, true)) {
            val engine = engine(byRealTiming)
            var lastPrimary = -1
            var lastList = -1
            val end = lines.last().endTime!! + 3000
            var t = 0L
            val problems = mutableListOf<String>()
            while (t <= end) {
                val s = engine.update(t, true, true, seekGeneration = 0)
                if (s.activeLineIndices.isNotEmpty() && s.primaryLineIndex !in s.activeLineIndices) {
                    problems += "t=$t primary ${s.primaryLineIndex} not in ${s.activeLineIndices}"
                }
                if (s.primaryLineIndex < lastPrimary) problems += "t=$t primary went back $lastPrimary -> ${s.primaryLineIndex}"
                if (s.playbackListIndex < lastList) problems += "t=$t scroll target went back $lastList -> ${s.playbackListIndex}"
                if (s.activeLineIndices.size > 3) problems += "t=$t ${s.activeLineIndices.size} active lines ${s.activeLineIndices}"
                lastPrimary = s.primaryLineIndex
                lastList = s.playbackListIndex
                t += 40
            }
            assertTrue(problems.isEmpty(), "byRealTiming=$byRealTiming:\n" + problems.take(30).joinToString("\n"))
        }
    }

    @Test
    fun exactlyTheLinesCoveringTheClockAreActiveInTheMiddleOfEveryLine() {
        val engine = engine(true)
        lines.forEachIndexed { i, line ->
            val words = line.words!!
            val mid = ms((words.first().startTime + words.first().endTime) / 2)
            val snapshot = engine.update(mid, true, true, seekGeneration = i)
            assertTrue(i in snapshot.activeLineIndices, "line $i '${line.text}' not active at its first word ($mid)")
        }
    }

    @Test
    fun aFinishedLineDoesNotStayLitWhileTheNextOverlappingLineSings() {
        // Lines 38/39 and 48/49 overlap by <1s in the source. The earlier line used to stay active (and
        // keep the scroll anchor) for the whole length of the later one, ~7s.
        val engine = engine(false)
        var t = 0L
        val checks = mapOf(125_000L to 38, 132_000L to 40, 207_000L to 48)
        var failure: String? = null
        while (t <= 207_000L && failure == null) {
            val s = engine.update(t, true, true, seekGeneration = 0)
            if (t == 125_000L && (s.activeLineIndices != listOf(39) || s.playbackListIndex != 39)) {
                failure = "t=$t active=${s.activeLineIndices} list=${s.playbackListIndex}, expected only line 39"
            }
            if (t == 207_000L && (s.activeLineIndices != listOf(49) || s.playbackListIndex != 49)) {
                failure = "t=$t active=${s.activeLineIndices} list=${s.playbackListIndex}, expected only line 49"
            }
            t += 40
        }
        assertEquals(null, failure)
        assertTrue(checks.isNotEmpty())
    }

    @Test
    fun noActiveLineOutlivesItsOwnEndByMoreThanTheHold() {
        val engine = engine(false)
        val ends = lines.map { l -> ms(l.words!!.maxOf { it.endTime }) }
        val problems = mutableListOf<String>()
        var t = 0L
        while (t <= lines.last().endTime!! + 3000) {
            for (i in engine.update(t, true, true, seekGeneration = 0).activeLineIndices) {
                if (t > ends[i] + 1500) problems += "t=$t line $i ended ${t - ends[i]}ms ago"
            }
            t += 40
        }
        assertTrue(problems.isEmpty(), problems.take(10).joinToString("\n"))
    }

    private fun engine(byRealTiming: Boolean): LyricsSyncEngine {
        val items = buildLyricsListItems(lines, activeByRealTiming = byRealTiming)
        val timeline = buildLyricsTimelineItems(items, lines)
        val index = buildLyricsItemIndex(items, lines.size)
        return LyricsSyncEngine(timeline, index.firstLineListIndex, activeByRealTiming = byRealTiming)
    }
}
