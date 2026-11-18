package com.metrolist.shared.api.lyrics

import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.WordTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EstimatedWordTimingTest {
    @Test
    fun wordsShareTheSpanByLengthAndTileItWithoutGaps() {
        val words = LyricsEntry(1_000, "aa bbbb").withEstimatedWords(endMs = 1_900).words!!
        assertEquals(listOf("aa", "bbbb"), words.map { it.text })
        assertEquals(1.0, words[0].startTime, 1e-9)
        assertEquals(words[0].endTime, words[1].startTime, 1e-9)
        assertEquals(1.9, words[1].endTime, 1e-9)
        assertTrue(words[0].endTime - words[0].startTime < words[1].endTime - words[1].startTime)
        assertTrue(words[0].hasTrailingSpace && !words[1].hasTrailingSpace)
    }

    @Test
    fun longGapsAfterALineDoNotStretchTheSweep() {
        val words = LyricsEntry(0, "hi there").withEstimatedWords(endMs = 60_000).words!!
        assertEquals(1.0, words.last().endTime, 1e-9)
    }

    @Test
    fun realWordTimingAndEmptyLinesAreLeftAlone() {
        val timed = LyricsEntry(0, "a b", words = listOf(WordTimestamp("a", 0.0, 0.5), WordTimestamp("b", 0.5, 1.0)))
        assertSame(timed, timed.withEstimatedWords(2_000))
        val blank = LyricsEntry(0, "  ")
        assertSame(blank, blank.withEstimatedWords(2_000))
        val noEnd = LyricsEntry(0, "a b")
        assertSame(noEnd, noEnd.withEstimatedWords(null))
    }

    @Test
    fun theToggleIsOfferedUnlessALineHasRealWordTiming() {
        val plain = LyricsEntry(1_000, "la la")
        val backgroundOnly = LyricsEntry(2_000, "la", words = listOf(WordTimestamp("ooh", 2.0, 2.4, isBackground = true)))
        val zeroLength = LyricsEntry(3_000, "la", words = listOf(WordTimestamp("la", 3.0, 3.0)))
        val real = LyricsEntry(4_000, "la", words = listOf(WordTimestamp("la", 4.0, 4.5)))
        assertTrue(listOf(plain, backgroundOnly, zeroLength).canEstimateWordSweep())
        assertFalse(listOf(plain, real).canEstimateWordSweep())
        assertFalse(listOf(LyricsEntry(0, "unsynced")).canEstimateWordSweep())
        assertFalse(emptyList<LyricsEntry>().canEstimateWordSweep())
    }

    @Test
    fun listEstimationEndsEachLineAtTheNextAndLeavesOtherListsAlone() {
        val list = listOf(LyricsEntry(1_000, "aa bb"), LyricsEntry(3_000, "cc dd"), LyricsEntry(6_000, "ee ff"))
        val estimated = list.withEstimatedWordTiming()
        // A short line sweeps for its minimum span rather than stretching to the next line.
        assertEquals(2.0, estimated[0].words!!.last().endTime, 1e-9)
        assertTrue(estimated[1].words!!.last().endTime <= 6.0)
        assertSame(list[2], estimated[2], "the last line has no known end and stays as it was")

        val real = listOf(list[0], LyricsEntry(3_000, "x", words = listOf(WordTimestamp("x", 3.0, 3.5))))
        assertSame(real, real.withEstimatedWordTiming())
        val unsynced = listOf(LyricsEntry(0, "a"), LyricsEntry(0, "b"))
        assertSame(unsynced, unsynced.withEstimatedWordTiming())
    }

    @Test
    fun combiningMarkScriptsSplitOnWhitespaceAndKeepEachWordIntact() {
        val line = "ನಾನು ನಿನ್ನ ಪ್ರೀತಿ"
        val words = LyricsEntry(0, line).withEstimatedWords(endMs = 4_000).words!!
        assertEquals(listOf("ನಾನು", "ನಿನ್ನ", "ಪ್ರೀತಿ"), words.map { it.text })
        assertEquals(line, words.joinToString("") { it.text + if (it.hasTrailingSpace) " " else "" })
    }

    @Test
    fun lineTimedWordsAreReplacedButBackgroundWordsKept() {
        val bg = WordTimestamp("ooh", 0.2, 0.4, isBackground = true)
        val entry =
            LyricsEntry(
                0,
                "la la",
                words = listOf(WordTimestamp("la la", 0.0, 1.0, isLineTimed = true), bg),
            )
        val words = entry.withEstimatedWords(1_000).words!!
        assertEquals(listOf("la", "la", "ooh"), words.map { it.text })
        assertEquals(bg, words.last())
    }
}
