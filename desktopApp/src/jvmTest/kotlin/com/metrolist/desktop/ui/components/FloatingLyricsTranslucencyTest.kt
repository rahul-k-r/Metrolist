package com.metrolist.desktop.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingLyricsTranslucencyTest {
    @Test
    fun translucentModeIs80PercentTransparentBackgroundAnd25PercentTransparentText() {
        assertEquals(0.2f, FLOATING_LYRICS_TRANSLUCENT_BACKGROUND_ALPHA)
        assertEquals(0.75f, FLOATING_LYRICS_TRANSLUCENT_TEXT_ALPHA)
    }
}
