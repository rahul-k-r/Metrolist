package com.metrolist.shared.db

import com.metrolist.shared.api.lyrics.LyricsFetcher
import com.metrolist.shared.api.lyrics.LyricsTranslationClient
import com.metrolist.shared.model.LyricsProvider
import com.metrolist.shared.model.LyricsProviderResult
import com.metrolist.shared.model.LyricsTimingKind
import com.metrolist.shared.model.LyricsWithProvider
import com.metrolist.shared.repository.LyricsRepository
import com.metrolist.shared.settings.LyricsSyncItem
import com.metrolist.shared.settings.Settings
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsSyncPersistenceTest {
    @AfterTest
    fun tearDown() = Settings.replaceSharedForTest(null)

    private class Device(
        val settings: Settings = Settings(MapSettings()),
    ) {
        private var tick = System.currentTimeMillis()
        private val path = Files.createTempFile("lyrics-sync", ".db").also { Files.delete(it) }
        val database = getDesktopDatabaseBuilder(path.toFile()).build()
        val repository =
            LyricsRepository(
                object : LyricsFetcher {
                    override suspend fun getLyrics(
                        title: String,
                        artist: String,
                        duration: Long?,
                        album: String?,
                        videoId: String?,
                        provider: LyricsProvider,
                        isVideoContext: Boolean,
                    ) = LyricsWithProvider("", "LrcLib")
                },
                LyricsTranslationClient(HttpClient(MockEngine { error("unused") })),
                database.dao(),
                // Strictly increasing, so two changes in one millisecond still order.
                nowMs = { ++tick },
            )

        /** Runs [block] with this device's settings as the app-wide ones, as the real app has one per process. */
        suspend fun <T> use(block: suspend LyricsRepository.() -> T): T {
            Settings.replaceSharedForTest(settings)
            return repository.block()
        }
    }

    @Test
    fun userLyricsOffsetsAndAProviderPickReachTheOtherDeviceAndANewerStateWins() =
        runTest {
            val phone = Device()
            val laptop = Device()
            phone.use {
                saveCustomLyrics("song", "[00:01.00] mine")
                setLyricsOffset("song", 300)
                setLyricsOffset("song", 120, provider = "LrcLib")
            }
            val items = phone.use { exportSyncItems(listOf("song", "unstamped")) }
            assertEquals(listOf("song"), items.map { it.songId })
            assertEquals("[00:01.00] mine", items.single().userLyrics)
            assertEquals(300, items.single().offsetMs)
            assertEquals(mapOf("LrcLib" to 120), items.single().providerOffsets)

            assertEquals(1, laptop.use { applySyncItems(items) })
            laptop.use {
                assertEquals("[00:01.00] mine", getUserLyrics("song"))
                assertEquals(300, getLyricsOffset("song"))
                assertEquals(120, getLyricsOffset("song", "LrcLib"))
                assertEquals(0, applySyncItems(items), "an item that is not newer changes nothing")
            }

            val pick = LyricsProviderResult(LyricsProvider.LRCLIB, "LrcLib", "[00:02.00] Picked", LyricsTimingKind.UNSYNCED)
            phone.use { selectProviderResult("song", pick) }
            val picked = phone.use { exportSyncItems(listOf("song")) }.single()
            assertEquals("[00:02.00] Picked", picked.providerLyrics)
            assertNull(picked.userLyrics, "choosing a provider sets the user fork aside")
            assertEquals(1, laptop.use { applySyncItems(listOf(picked)) })
            val synced = laptop.database.dao().cachedLyrics("song")
            assertEquals("LrcLib", synced?.provider)
            assertTrue(synced?.isUserSelected == true)
            assertEquals(300, synced?.offsetMs)
        }

    @Test
    fun revertingUserLyricsSyncsAsRemoval() =
        runTest {
            val phone = Device()
            val laptop = Device()
            phone.use { saveCustomLyrics("song", "[00:01.00] mine") }
            laptop.use { applySyncItems(phone.use { exportSyncItems(listOf("song")) }) }
            phone.use { revertUserLyrics("song") }
            val reverted: List<LyricsSyncItem> = phone.use { exportSyncItems(listOf("song")) }
            laptop.use {
                assertEquals(1, applySyncItems(reverted))
                assertNull(getUserLyrics("song"))
            }
        }
}
