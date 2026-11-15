package com.metrolist.shared.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSyncTest {
    @BeforeTest
    fun setUp() {
        Settings.replaceSharedForTest(Settings(MapSettings()))
    }

    @AfterTest
    fun tearDown() {
        Settings.replaceSharedForTest(null)
    }

    @Test
    fun endpointsAndDeviceStateNeverLeaveThisDevice() {
        val settings = Settings.shared
        settings.lyricsAiBaseUrl = "https://example.invalid/v1"
        settings.listenTogetherServerUrl = "wss://example.invalid/ws"
        settings.deviceConnectDeviceName = "Desk"
        settings.playPauseFadeEnabled = false

        val keys = settings.exportSyncSettings().entries.map { it.key }.toSet()

        assertFalse("lyricsAiBaseUrl" in keys)
        assertFalse("listenTogetherServerUrl" in keys)
        assertFalse("deviceConnectDeviceName" in keys)
        assertTrue("playPauseFadeEnabled" in keys)
    }

    @Test
    fun importIgnoresExcludedAndUnknownKeys() {
        val settings = Settings.shared
        settings.lyricsAiBaseUrl = "https://mine.invalid/v1"
        settings.playPauseFadeEnabled = true

        val snapshot =
            PortableSettings(
                entries =
                    listOf(
                        PortableSetting("lyricsAiBaseUrl", SettingType.STRING, "https://evil.invalid/v1"),
                        PortableSetting("notARealSetting", SettingType.STRING, "x"),
                        PortableSetting("playPauseFadeEnabled", SettingType.BOOLEAN, "false"),
                    ),
            )

        assertEquals(1, settings.syncDifferenceCount(snapshot))
        settings.importSyncSettings(snapshot)

        assertEquals("https://mine.invalid/v1", settings.lyricsAiBaseUrl)
        assertFalse(settings.playPauseFadeEnabled)
    }
}
