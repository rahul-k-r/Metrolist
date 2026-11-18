package com.metrolist.desktop.state.playback

/**
 * Whether the queue entry at [index] may be removed. The queue lock deliberately plays no part: as on Android it
 * stops swiping and reordering, while an explicit menu action can still remove a track.
 */
internal fun canRemoveQueueEntry(
    index: Int,
    currentIndex: Int,
    isGuest: Boolean,
): Boolean = !isGuest && index > currentIndex
