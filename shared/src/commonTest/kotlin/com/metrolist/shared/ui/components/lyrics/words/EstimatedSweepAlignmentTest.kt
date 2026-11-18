package com.metrolist.shared.ui.components.lyrics.words

import com.metrolist.shared.api.lyrics.withEstimatedWords
import com.metrolist.shared.model.LyricsEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EstimatedSweepAlignmentTest {
    private fun check(line: String) {
        val words = LyricsEntry(10_000, line).withEstimatedWords(endMs = 14_000).words!!
        val aligned = alignSyllablesForFormatting(words, line)
        // Each estimated word lands on its own, ordered span of the displayed text.
        aligned.forEach { assertEquals(it.syllable.text, line.substring(it.startOffset, it.endOffset)) }
        assertTrue(aligned.zipWithNext().all { (a, b) -> a.endOffset <= b.startOffset })
        val grouped = reconstructWords(aligned, line)
        assertEquals(words.size, grouped.size, "every estimated word stays a word")
        assertTrue(grouped.zipWithNext().all { (a, b) -> a.wordEndTimeMs <= b.wordStartTimeMs + 1 })
        assertTrue(grouped.first().wordStartTimeMs < grouped.last().wordEndTimeMs)
    }

    @Test
    fun latinLinesAlignAndRegroup() = check("hello brave new world")

    @Test
    fun kannadaLinesAlignAndRegroup() = check("ನಾನು ನಿನ್ನ ಪ್ರೀತಿ ಮಾಡುತ್ತೇನೆ")
}
