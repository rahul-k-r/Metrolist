package com.metrolist.android.outputswitcher

import android.app.Activity
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.metrolist.android.deviceconnect.AndroidDeviceConnect

/** Target of the system's android.media.action.TRANSFER_MEDIA intent (GO_TO_APP selection). */
class TransferMediaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val routeId = intent?.getStringExtra("android.media.extra.ROUTE_ID")
        spikeLog("TransferMediaActivity action=${intent?.action} routeId=$routeId")
        val deviceId = routeIdToDeviceId(routeId)
        if (deviceId == null) {
            spikeLog("unknown routeId, ignoring")
        } else {
            spikeLog("transferring playback to deviceId=$deviceId")
            AndroidDeviceConnect.manager.remote.transferTo(deviceId)
        }
        finish()
    }
}

/** Debug-only auto-start: no edits to production code needed. */
class OutputSwitcherSpikeInitializer : android.content.ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        // Post so Application.onCreate (Timber planting etc.) has finished.
        Handler(Looper.getMainLooper()).post { runCatching { OutputSwitcherSpike.start(ctx) }.onFailure { spikeLog("start failed", it) } }
        return true
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0

    override fun update(uri: Uri, v: android.content.ContentValues?, s: String?, a: Array<String>?): Int = 0
}
