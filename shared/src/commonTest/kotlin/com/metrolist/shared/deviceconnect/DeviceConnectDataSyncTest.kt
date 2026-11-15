package com.metrolist.shared.deviceconnect

import com.metrolist.shared.settings.MarkKind
import com.metrolist.shared.settings.MarkRegistry
import com.metrolist.shared.settings.MarkedItem
import com.metrolist.shared.settings.PortableSetting
import com.metrolist.shared.settings.PortableSettings
import com.metrolist.shared.settings.SettingType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceConnectDataSyncTest {
    private class Device(
        scope: TestScope,
        var clock: Long = 1_000L,
        initialSettings: Map<String, String> = emptyMap(),
    ) {
        var incognitoJson = ""
        var skipJson = ""
        val settings = initialSettings.toMutableMap()
        val sent = mutableListOf<Pair<String, DcDataMessage>>()
        var sendResult = true
        val peerIds = MutableStateFlow<Set<String>>(emptySet())
        val messages = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 16)
        val incognito = MarkRegistry({ incognitoJson }, { incognitoJson = it }, markArtists = true, now = { clock })
        val skip = MarkRegistry({ skipJson }, { skipJson = it }, markArtists = false, now = { clock })
        val sync =
            DeviceConnectDataSync(
                scope = scope.backgroundScope,
                transport =
                    DataSyncTransport { to, bytes ->
                        sent += to to DcJson.decodeFromString<DcDataMessage>(bytes.decodeToString())
                        sendResult
                    },
                incognitoMarks = incognito,
                skipMarks = skip,
                exportSettings = { PortableSettings(entries = settings.map { (k, v) -> PortableSetting(k, SettingType.STRING, v) }) },
                importSettings = { snapshot -> snapshot.entries.forEach { settings[it.key] = it.value } },
                settingsDifferences = { snapshot -> snapshot.entries.count { settings[it.key] != it.value } },
                marksVersion = { incognitoJson to skipJson },
            ).also { it.start(peerIds, messages) }

        suspend fun deliver(
            from: String,
            message: DcDataMessage,
        ) = sync.onMessage(from, DcJson.encodeToString(message).encodeToByteArray())
    }

    private fun song(id: String) = MarkedItem(MarkKind.SONG, id, id)

    @Test
    fun newerMarkChangeWinsAndAnUnmarkSurvivesAMergeWithAStaleCopy() {
        val a = listOf(song("x").copy(updatedAt = 10))
        val b = listOf(song("x").copy(updatedAt = 20, removed = true))
        val merged = com.metrolist.shared.settings.mergeMarks(a, b)
        assertEquals(1, merged.size)
        assertTrue(merged.single().removed)
        assertEquals(a, com.metrolist.shared.settings.mergeMarks(a, listOf(song("x").copy(updatedAt = 5, removed = true))))
    }

    @Test
    fun connectingPushesNonEmptyMarksAndTheOtherSideMergesThem() =
        runTest {
            val phone = Device(this)
            val laptop = Device(this)
            phone.incognito.mark(song("a"))
            phone.skip.mark(song("b"))
            phone.peerIds.value = setOf("laptop")
            laptop.peerIds.value = setOf("phone")
            runCurrent()
            val (to, message) = phone.sent.single()
            assertEquals("laptop", to)
            laptop.deliver("phone", message)
            assertTrue(laptop.incognito.isMarked(MarkKind.SONG, "a"))
            assertTrue(laptop.skip.isMarked(MarkKind.SONG, "b"))
        }

    @Test
    fun anUnmarkOnOneDeviceRemovesTheMarkOnTheOther() =
        runTest {
            val phone = Device(this)
            val laptop = Device(this)
            phone.peerIds.value = setOf("laptop")
            laptop.peerIds.value = setOf("phone")
            runCurrent()
            phone.incognito.mark(song("a"))
            laptop.incognito.mergeFrom(phone.incognito.entries())
            laptop.clock = 2_000L
            laptop.incognito.unmark(MarkKind.SONG, "a")
            phone.deliver("laptop", DcDataMessage(DcDataKinds.MARKS, incognito = laptop.incognito.entries()))
            assertFalse(phone.incognito.isMarked(MarkKind.SONG, "a"))
        }

    @Test
    fun emptyMarksAreNotSentOnConnect() =
        runTest {
            val device = Device(this)
            device.peerIds.value = setOf("other")
            runCurrent()
            assertTrue(device.sent.isEmpty())
        }

    @Test
    fun pullAppliesOnlyTheSnapshotWeAskedFor() =
        runTest {
            val requester = Device(this, initialSettings = mapOf("theme" to "light"))
            val owner = Device(this, initialSettings = mapOf("theme" to "dark", "volume" to "7"))
            requester.peerIds.value = setOf("owner")
            owner.peerIds.value = setOf("requester")
            runCurrent()
            requester.sync.pullSettings("owner")
            runCurrent()
            assertEquals(DcDataKinds.SETTINGS_REQUEST, requester.sent.single().second.kind)

            owner.deliver("requester", requester.sent.single().second)
            val snapshot = owner.sent.single().second
            assertEquals(DcDataKinds.SETTINGS_SNAPSHOT, snapshot.kind)

            requester.deliver("owner", snapshot)
            assertEquals("dark", requester.settings["theme"])
            assertEquals("7", requester.settings["volume"])
            assertIs<DataSyncStatus.Applied>(requester.sync.status.value)

            val unsolicited = PortableSettings(entries = listOf(PortableSetting("theme", SettingType.STRING, "neon")))
            requester.deliver("owner", DcDataMessage(DcDataKinds.SETTINGS_SNAPSHOT, settings = unsolicited))
            assertEquals("dark", requester.settings["theme"])
        }

    @Test
    fun aPushWaitsForAcceptanceAndRejectingDropsIt() =
        runTest {
            val receiver = Device(this, initialSettings = mapOf("theme" to "light"))
            receiver.peerIds.value = setOf("sender")
            runCurrent()
            val push =
                DcDataMessage(
                    DcDataKinds.SETTINGS_PUSH,
                    settings = PortableSettings(entries = listOf(PortableSetting("theme", SettingType.STRING, "dark"))),
                )
            receiver.deliver("sender", push)
            assertEquals("light", receiver.settings["theme"])
            val incoming = assertNotNull(receiver.sync.incomingPush.value)
            assertEquals(1, incoming.changedCount)

            receiver.sync.rejectIncoming()
            assertNull(receiver.sync.incomingPush.value)
            assertEquals("light", receiver.settings["theme"])

            receiver.deliver("sender", push)
            receiver.sync.acceptIncoming()
            assertEquals("dark", receiver.settings["theme"])
            assertNull(receiver.sync.incomingPush.value)
        }

    @Test
    fun messagesFromDevicesThatAreNotConnectedAreIgnored() =
        runTest {
            val device = Device(this)
            device.deliver("ghost", DcDataMessage(DcDataKinds.MARKS, incognito = listOf(song("a").copy(updatedAt = 5))))
            assertFalse(device.incognito.isMarked(MarkKind.SONG, "a"))
        }

    @Test
    fun aFailedSendIsReportedForPullAndPush() =
        runTest {
            val device = Device(this)
            device.sendResult = false
            device.peerIds.value = setOf("other")
            runCurrent()
            device.sync.pushSettings("other")
            runCurrent()
            assertIs<DataSyncStatus.Failed>(device.sync.status.value)
            device.sync.clearStatus()
            device.sync.pullSettings("other")
            runCurrent()
            assertIs<DataSyncStatus.Failed>(device.sync.status.value)
        }
}
