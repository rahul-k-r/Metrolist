package com.metrolist.shared.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncognitoSettingsTest {
    @Test
    fun defaultsAreOffWithMenuEntryAndRestartResetOn() {
        val settings = Settings(MapSettings())
        assertFalse(settings.incognitoModeEnabled)
        assertTrue(settings.incognitoShowInProfileMenu)
        assertTrue(settings.incognitoResetOnRestart)
        assertFalse(settings.incognitoActive)
        assertFalse(settings.isIncognito)
    }

    @Test
    fun activeFlagIsInertWithoutTheMasterSwitch() {
        val settings = Settings(MapSettings())
        settings.incognitoActive = true
        assertFalse(settings.isIncognito)
        assertFalse(settings.listenHistoryPaused)
        assertFalse(settings.searchHistoryPaused)
    }

    @Test
    fun masterSwitchAndActiveTogetherPauseBothHistoriesWithoutTouchingUserPauses() {
        val settings = Settings(MapSettings())
        settings.incognitoModeEnabled = true
        settings.incognitoActive = true
        assertTrue(settings.isIncognito)
        assertTrue(settings.listenHistoryPaused)
        assertTrue(settings.searchHistoryPaused)
        assertFalse(settings.pauseListenHistory)
        assertFalse(settings.pauseSearchHistory)
    }

    @Test
    fun turningTheMasterSwitchOffEndsIncognitoEvenWhenTheFlagIsStillSet() {
        val settings = Settings(MapSettings())
        settings.incognitoModeEnabled = true
        settings.incognitoActive = true
        settings.incognitoModeEnabled = false
        assertFalse(settings.isIncognito)
        assertFalse(settings.listenHistoryPaused)
    }

    @Test
    fun userPauseSettingsStillApplyOutsideIncognito() {
        val settings = Settings(MapSettings())
        settings.pauseListenHistory = true
        settings.pauseSearchHistory = true
        assertTrue(settings.listenHistoryPaused)
        assertTrue(settings.searchHistoryPaused)
        assertFalse(settings.isIncognito)
    }

    @Test
    fun quickToggleNeedsBothTheMasterSwitchAndTheMenuOption() {
        assertFalse(IncognitoPolicy.showQuickToggle(modeEnabled = false, showInProfileMenu = true))
        assertFalse(IncognitoPolicy.showQuickToggle(modeEnabled = true, showInProfileMenu = false))
        assertFalse(IncognitoPolicy.showQuickToggle(modeEnabled = false, showInProfileMenu = false))
        assertTrue(IncognitoPolicy.showQuickToggle(modeEnabled = true, showInProfileMenu = true))
    }

    @Test
    fun activeStateResetsOnStartByDefault() {
        val backing = MapSettings()
        Settings(backing).apply {
            incognitoModeEnabled = true
            incognitoActive = true
        }
        val restarted = Settings(backing)
        assertFalse(restarted.incognitoActive)
        assertTrue(restarted.incognitoModeEnabled)
    }

    @Test
    fun activeStateSurvivesStartWhenResetIsOff() {
        val backing = MapSettings()
        Settings(backing).apply {
            incognitoModeEnabled = true
            incognitoResetOnRestart = false
            incognitoActive = true
        }
        val restarted = Settings(backing)
        assertTrue(restarted.incognitoActive)
        assertTrue(restarted.isIncognito)
    }

    @Test
    fun activeStateIsNotPartOfABackup() {
        val settings = Settings(MapSettings())
        settings.incognitoModeEnabled = true
        settings.incognitoActive = true
        val keys = settings.exportBackupSettings().entries.map { it.key }
        assertTrue(IncognitoPolicy.ENABLED_KEY in keys)
        assertFalse(IncognitoPolicy.ACTIVE_KEY in keys)
        assertEquals(1, keys.count { it == IncognitoPolicy.ENABLED_KEY })
    }
}
