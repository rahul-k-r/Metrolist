package com.metrolist.android.outputswitcher

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.media.MediaRoute2Info
import android.media.SuggestedDeviceInfo
import android.os.Build
import android.util.Log
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteDescriptor
import androidx.mediarouter.media.MediaRouteProvider
import androidx.mediarouter.media.MediaRouteProviderDescriptor
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import androidx.mediarouter.media.RouteListingPreference
import com.metrolist.android.deviceconnect.AndroidDeviceConnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

internal const val SPIKE_TAG = "OutputSwitcherSpike"

internal fun spikeLog(
    msg: String,
    t: Throwable? = null,
) {
    // Timber may not be planted yet when the spike starts; mirror to logcat directly.
    Log.i(SPIKE_TAG, msg, t)
    Timber.tag(SPIKE_TAG).i(t, msg)
}

/**
 * SPIKE: publishes connected Device Connect peers (a) as routes of an in-process androidx
 * MediaRouteProvider, (b) as RouteListingPreference items (API 34+, GO_TO_APP selection), and
 * (c) as SuggestedDeviceInfo (API 36.1+), to observe what the system Output Switcher does with them.
 */
object OutputSwitcherSpike {
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        spikeLog("start sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL}")

        val router = MediaRouter.getInstance(app)
        runCatching {
            router.routerParams =
                MediaRouterParams
                    .Builder()
                    .setOutputSwitcherEnabled(true)
                    .build()
        }.onFailure { spikeLog("setRouterParams failed", it) }

        val provider = SpikeProvider(app)
        router.addProvider(provider)
        val selector =
            MediaRouteSelector
                .Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .build()
        // A registered callback with discovery is needed for the router to register with the system.
        router.addCallback(selector, SpikeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)

        scope.launch {
            AndroidDeviceConnect.manager.state
                .map { it.peers.toSwitcherTargets() }
                .distinctUntilChanged()
                .collect { targets ->
                    spikeLog("targets changed: ${targets.map { "${it.name}[${it.routeId}]" }}")
                    provider.publish(targets)
                    publishListingPreference(app, router, targets)
                    // Suggestions are not published: the docs describe no way to receive a tap on one, so the chip did nothing.
                }
        }
    }

    private fun publishListingPreference(
        app: Context,
        router: MediaRouter,
        targets: List<SwitcherTarget>,
    ) {
        if (Build.VERSION.SDK_INT < 34) {
            spikeLog("RouteListingPreference skipped: API ${Build.VERSION.SDK_INT} < 34")
            return
        }
        runCatching {
            val items =
                targets.map {
                    RouteListingPreference.Item
                        .Builder(it.routeId)
                        .setSelectionBehavior(RouteListingPreference.Item.SELECTION_BEHAVIOR_GO_TO_APP)
                        .build()
                }
            val pref =
                RouteListingPreference
                    .Builder()
                    .setItems(items)
                    .setLinkedItemComponentName(ComponentName(app, TransferMediaActivity::class.java))
                    .setSystemOrderingEnabled(false)
                    .build()
            router.setRouteListingPreference(pref)
            spikeLog("RouteListingPreference set with ${items.size} items -> ${TransferMediaActivity::class.java.simpleName}")
        }.onFailure { spikeLog("RouteListingPreference failed", it) }
    }

    @SuppressLint("NewApi")
    private fun publishSuggestions(
        app: Context,
        targets: List<SwitcherTarget>,
    ) {
        if (Build.VERSION.SDK_INT < 36) {
            spikeLog("setDeviceSuggestions skipped: API ${Build.VERSION.SDK_INT} < 36")
            return
        }
        runCatching {
            val suggestions =
                targets.map {
                    SuggestedDeviceInfo
                        .Builder(it.name, it.routeId, MediaRoute2Info.TYPE_REMOTE_COMPUTER)
                        .build()
                }
            android.media.MediaRouter2
                .getInstance(app)
                .setDeviceSuggestions(suggestions)
            spikeLog("setDeviceSuggestions called with ${suggestions.size} suggestions")
        }.onFailure { spikeLog("setDeviceSuggestions failed (expected on < 36.1)", it) }
    }

    private object SpikeCallback : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) =
            spikeLog("router onRouteAdded ${route.id} '${route.name}' provider=${route.provider.packageName}")

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) =
            spikeLog("router onRouteRemoved ${route.id}")

        override fun onRouteSelected(router: MediaRouter, selectedRoute: MediaRouter.RouteInfo, reason: Int) =
            spikeLog("router onRouteSelected ${selectedRoute.id} reason=$reason")

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) =
            spikeLog("router onRouteUnselected ${route.id} reason=$reason")
    }
}

private class SpikeProvider(
    context: Context,
) : MediaRouteProvider(context) {
    fun publish(targets: List<SwitcherTarget>) {
        val routes =
            targets.map {
                MediaRouteDescriptor
                    .Builder(it.routeId, it.name)
                    .setDescription("Metrolist Device Connect")
                    .addControlFilter(IntentFilter(MediaControlIntent.CATEGORY_LIVE_AUDIO))
                    .setDeviceType(MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER)
                    .setEnabled(true)
                    .build()
            }
        descriptor = MediaRouteProviderDescriptor.Builder().addRoutes(routes).build()
        spikeLog("provider published ${routes.size} routes")
    }

    override fun onCreateRouteController(routeId: String): RouteController? {
        spikeLog("provider onCreateRouteController $routeId")
        return object : RouteController() {
            override fun onSelect() {
                spikeLog("RouteController.onSelect $routeId")
                routeIdToDeviceId(routeId)?.let { AndroidDeviceConnect.manager.remote.transferTo(it) }
            }

            override fun onUnselect(reason: Int) = spikeLog("RouteController.onUnselect $routeId reason=$reason")
        }
    }
}
