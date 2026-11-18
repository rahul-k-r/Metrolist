package com.metrolist.shared.api.lyrics

import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.WordTimestamp

private const val MIN_SPAN_MS = 1_000L
private const val MAX_MS_PER_CHAR = 120L

/** Real per-word timing: background vocals, line-timed stand-ins and zero-length words don't count. */
fun LyricsEntry.hasWordTiming(): Boolean = words.orEmpty().any { !it.isBackground && !it.isLineTimed && it.endTime > it.startTime }

/** The sweep toggle is offered for synced lyrics where no line has real word timing. */
fun List<LyricsEntry>.canEstimateWordSweep(): Boolean = any { it.time > 0L } && none { it.hasWordTiming() }

/**
 * Estimates words for every line of a line-synced list, each ending where the next line starts. Done once at the
 * source, so the renderer, the word-sync decision and the position clock all see the same words. Lists that
 * already carry real word timing, or aren't synced, come back as the same instance.
 */
fun List<LyricsEntry>.withEstimatedWordTiming(): List<LyricsEntry> =
    if (!canEstimateWordSweep()) this else mapIndexed { index, entry -> entry.withEstimatedWords(getOrNull(index + 1)?.time) }

/**
 * Gives a line that only carries line-level timing synthetic word timings, so the word sweep can
 * animate it. Each word gets a share of the line's sung span proportional to its length. The span
 * ends at [endMs] (usually the next line's start) but is capped so an instrumental gap after the
 * line doesn't stretch the sweep. Lines with real word timing are returned untouched.
 */
fun LyricsEntry.withEstimatedWords(endMs: Long?): LyricsEntry {
    if (hasWordTiming()) return this
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
