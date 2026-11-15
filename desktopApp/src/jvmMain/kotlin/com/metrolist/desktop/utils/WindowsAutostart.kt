package com.metrolist.desktop.utils

import com.metrolist.shared.utils.OsUtil

/** Registers the installed app under the per-user Windows Run key. Does nothing on other platforms. */
object WindowsAutostart {
    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "Metrolist"

    fun sync(enabled: Boolean) {
        if (!OsUtil.isWindows) return
        val command =
            if (enabled) {
                val exe = ProcessHandle.current().info().command().orElse(null)
                autostartAddCommand(exe) ?: return
            } else {
                autostartRemoveCommand()
            }
        runCatching { ProcessBuilder(command).redirectErrorStream(true).start().also { it.inputStream.readAllBytes() }.waitFor() }
    }
}

/** Null when running from a JVM (development), where the executable is java.exe rather than the app. */
internal fun autostartAddCommand(exePath: String?): List<String>? {
    val exe = exePath?.takeIf { it.isNotBlank() } ?: return null
    val name = exe.substringAfterLast('\\').substringAfterLast('/').lowercase()
    if (name == "java.exe" || name == "javaw.exe") return null
    return listOf(
        "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
        "/v", "Metrolist", "/t", "REG_SZ", "/d", "\"$exe\"", "/f",
    )
}

internal fun autostartRemoveCommand(): List<String> =
    listOf("reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "Metrolist", "/f")
