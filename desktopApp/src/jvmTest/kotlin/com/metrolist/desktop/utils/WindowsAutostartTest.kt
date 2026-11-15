package com.metrolist.desktop.utils

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsAutostartTest {
    @Test
    fun installedExeIsRegisteredQuoted() {
        val command = autostartAddCommand("C:\\Program Files\\Metrolist\\Metrolist.exe")!!
        assertContains(command, "\"C:\\Program Files\\Metrolist\\Metrolist.exe\"")
        assertEquals("reg", command.first())
    }

    @Test
    fun jvmLaunchIsNeverRegistered() {
        assertNull(autostartAddCommand("C:\\jdk\\bin\\java.exe"))
        assertNull(autostartAddCommand("C:\\jdk\\bin\\javaw.exe"))
        assertNull(autostartAddCommand(null))
        assertNull(autostartAddCommand(" "))
    }

    @Test
    fun removeTargetsTheSameValue() {
        val add = autostartAddCommand("C:\\Metrolist.exe")!!
        val remove = autostartRemoveCommand()
        assertEquals(add[add.indexOf("/v") + 1], remove[remove.indexOf("/v") + 1])
    }
}
