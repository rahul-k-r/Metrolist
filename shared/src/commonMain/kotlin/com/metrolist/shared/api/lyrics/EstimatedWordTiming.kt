package com.metrolist.shared.api.lyrics

import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.WordTimestamp

private const val MIN_SPAN_MS = 1_000L
private const val MAX_MS_PER_CHAR = 120L

/**
 * Gives a line that only carries line-level timing synthetic word timings, so the word sweep can
 * animate it. Each word gets a share of the line's sung span proportional to its length. The span
 * ends at [endMs] (usually the next line's start) but is capped so an instrumental gap after the
 * line doesn't stretch the sweep. Lines with real word timing are returned untouched.
 */
fun LyricsEntry.withEstimatedWords(endMs: Long?): LyricsEntry {
    if (words.orEmpty().any { !it.isBackground && !it.isLineTimed && it.endTime > it.startTime }) return this
    val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return this
    val end = endMs ?: endTime ?: return this
    val available = end - time
    if (available <= 0) return this
    val chars = tokens.sumOf { it.length }
    val span = minOf(available, maxOf(MIN_SPAN_MS, chars * MAX_MS_PER_CHAR))
    var cursor = time.toDouble()
    val estimated =
        tokens.mapIndexed { index, token ->
            val start = cursor
            cursor += span.toDouble() * token.length / chars
            WordTimestamp(
                text = token,
                startTime = start / 1000.0,
                endTime = cursor / 1000.0,
                hasTrailingSpace = index < tokens.lastIndex,
            )
        }
    return copy(words = estimated + words.orEmpty().filter { it.isBackground })
}
