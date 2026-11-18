package com.metrolist.desktop.state.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueRemovalRulesTest {
    @Test
    fun onlyUpcomingEntriesCanBeRemoved() {
        assertTrue(canRemoveQueueEntry(index = 3, currentIndex = 2, isGuest = false))
        assertFalse(canRemoveQueueEntry(index = 2, currentIndex = 2, isGuest = false))
        assertFalse(canRemoveQueueEntry(index = 1, currentIndex = 2, isGuest = false))
        assertFalse(canRemoveQueueEntry(index = -1, currentIndex = -1, isGuest = false))
    }

    @Test
    fun aListenTogetherGuestCannotRemoveAnything() {
        assertFalse(canRemoveQueueEntry(index = 5, currentIndex = 0, isGuest = true))
    }
}
