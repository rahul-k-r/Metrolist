package com.metrolist.android.playback

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.metrolist.shared.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

/** Where phone and watch volume changes go instead of the phone's own stream. */
interface ExternalVolumeRoute {
    /** The routed device's volume (0..1), or null while nothing is routed. */
    val state: Flow<Float?>

    fun setVolume(level: Float)
}

/** Required so the user can grant notification access; Android demands it to read another app's media session. Ignores all notifications. */
class KdeVolumeListenerService : NotificationListenerService()

/**
 * Drives KDE Connect's remote-volume media session while the phone is following a Device Connect device.
 * Active only when the setting is on, notification access is granted, and KDE Connect has a remote-volume session.
 */
internal class KdeConnectVolumeRoute(
    private val context: Context,
    private val following: Flow<Boolean>,
) : ExternalVolumeRoute {
    @Volatile private var controller: MediaController? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<Float?> =
        combine(following, enabled().flatMapLatest { if (it) kdeVolume() else flowOf(null) }) { isFollowing, volume ->
            volume.takeIf { isFollowing }
        }.distinctUntilChanged()

    override fun setVolume(level: Float) {
        val target = controller ?: return
        val max = target.playbackInfo?.maxVolume ?: return
        runCatching { target.setVolumeTo((level.coerceIn(0f, 1f) * max).roundToInt(), 0) }
    }

    // The setting has no change stream and permission is granted in system settings, so poll cheaply.
    private fun enabled(): Flow<Boolean> =
        flow {
            while (true) {
                emit(Settings.shared.forwardVolumeToKdeConnect && NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
                delay(3_000)
            }
        }.distinctUntilChanged()

    private fun kdeVolume(): Flow<Float?> =
        callbackFlow {
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(context, KdeVolumeListenerService::class.java)
            val callback =
                object : MediaController.Callback() {
                    override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) {
                        trySend(info.level())
                    }
                }

            fun refresh(sessions: List<MediaController>?) {
                controller?.unregisterCallback(callback)
                controller =
                    sessions?.firstOrNull {
                        it.packageName == KDE_CONNECT_PACKAGE && it.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
                    }
                controller?.registerCallback(callback)
                trySend(controller?.playbackInfo?.level())
            }

            val listener = MediaSessionManager.OnActiveSessionsChangedListener { refresh(it) }
            try {
                manager.addOnActiveSessionsChangedListener(listener, component)
                refresh(manager.getActiveSessions(component))
            } catch (_: SecurityException) {
                trySend(null)
            }
            awaitClose {
                runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
                controller?.unregisterCallback(callback)
                controller = null
            }
        }.distinctUntilChanged()

    private fun MediaController.PlaybackInfo.level(): Float? = maxVolume.takeIf { it > 0 }?.let { currentVolume.toFloat() / it }

    private companion object {
        const val KDE_CONNECT_PACKAGE = "org.kde.kdeconnect_tp"
    }
}
