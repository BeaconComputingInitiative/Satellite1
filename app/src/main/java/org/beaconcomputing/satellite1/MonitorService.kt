// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** In-process proof of foreground service status. Never substitute a persisted preference for actual status. */
internal object MonitorRuntimeState {
    @Volatile var active: Boolean = false
    @Volatile var state: String = "STOPPED"
}

/** User-started, visible foreground monitoring. No boot auto-start, hidden execution or broad scans. */
class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private lateinit var policy: Policy
    private lateinit var store: EvidenceStore
    private lateinit var geo: GeoTelemetry
    private lateinit var connectivity: ConnectivityManager
    private var lastCellSampleMs = 0L
    private var lastInventoryMs = 0L
    private var lastNetworkDigest = ""
    private var callbackRegistered = false
    private var tickScheduled = false
    private data class ListenerRegistration(
        val telephony: TelephonyManager,
        val modern: TelephonyCallback?,
        val legacy: PhoneStateListener?
    )
    private val cellListeners = mutableMapOf<Int, ListenerRegistration>()
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { handler.post { scheduleSample() } }
        override fun onLost(network: Network) { handler.post { scheduleSample() } }
    }
    private val tick = object : Runnable {
        override fun run() {
            scheduleSample()
            handler.postDelayed(this, 60_000L)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        policy = Policy(this)
        store = EvidenceStore(this)
        connectivity = getSystemService(ConnectivityManager::class.java)
        geo = GeoTelemetry(this) { facts ->
            if (policy.locationEnabled && policy.monitoringEnabled && !batteryLow()) {
                executor.execute { store.append("GEOLOCATION", facts) }
            }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "SATELLITE1 active monitoring", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            policy.monitoringEnabled = false
            MonitorRuntimeState.active = false
            MonitorRuntimeState.state = "STOP_REQUESTED"
            android.util.Log.i("SATELLITE1", "MONITOR_STOP_REQUESTED")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!policy.monitoringEnabled || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            MonitorRuntimeState.active = false
            MonitorRuntimeState.state = "HOLD_POLICY_OR_LOCATION_PERMISSION"
            android.util.Log.w("SATELLITE1", "MONITOR_HOLD: policy disabled or precise location unavailable")
            stopSelf()
            return START_NOT_STICKY
        }
        val stopIntent = Intent(this, MonitorService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("SATELLITE1 • BCI")
            .setContentText("Visible telemetry collection • tap Stop to end")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop", pending).build())
            .build()
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (error: Exception) {
            MonitorRuntimeState.active = false
            MonitorRuntimeState.state = "HOLD_FOREGROUND_START_${error.javaClass.simpleName}"
            android.util.Log.e("SATELLITE1", "MONITOR_HOLD: foreground start ${error.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!callbackRegistered) {
            try {
                connectivity.registerNetworkCallback(
                    NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                    networkCallback
                )
                callbackRegistered = true
            } catch (error: Exception) {
                // Cellular and geo sampling can continue when Wi-Fi callback registration fails.
                android.util.Log.w("SATELLITE1", "WIFI_CALLBACK_HOLD: ${error.javaClass.simpleName}")
            }
        }
        if (!tickScheduled) {
            handler.post(tick)
            tickScheduled = true
        }
        MonitorRuntimeState.active = true
        MonitorRuntimeState.state = "ACTIVE"
        android.util.Log.i("SATELLITE1", "MONITOR_ACTIVE: foreground service started")
        return START_NOT_STICKY
    }
    @RequiresApi(31)
    private inner class CellInfoChanged31(private val subId: Int) : TelephonyCallback(), TelephonyCallback.CellInfoListener {
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
            enqueueCellObservation(cellInfo, "system_cell_callback", subId)
        }
    }
    @Suppress("DEPRECATION")
    private inner class CellInfoChangedLegacy(private val subId: Int) : PhoneStateListener() {
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            if (cellInfo != null) enqueueCellObservation(cellInfo, "legacy_cell_callback", subId)
        }
    }

    /** Callback events are serialized on the evidence executor, never the main UI thread. */
    private fun enqueueCellObservation(cells: List<CellInfo>, source: String, subId: Int) {
        if (!policy.monitoringEnabled || !policy.cellularEnabled || batteryLow()) return
        val copy = cells.toList()
        try {
            executor.execute {
                try {
                    if (!policy.monitoringEnabled || !policy.cellularEnabled || batteryLow()) return@execute
                    val collector = CellTelemetry(this)
                    if (!collector.permitted()) return@execute
                    val snapshot = collector.fromCells(copy, source, subId)
                    store.trackCellSample(snapshot, source,
                        if (policy.locationEnabled) geo.recentFix() else null)
                } catch (err: Exception) {
                    android.util.Log.w("SATELLITE1", "Cell callback deferred: ${err.javaClass.simpleName}")
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { /* service is stopping */ }
    }

    /** Subscription-specific listeners avoid mixing dual-SIM serving-cell sets. */
    private fun activeSubscriptionIds(): Set<Int> {
        val manager = getSystemService(SubscriptionManager::class.java)
        val ids = try {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                manager?.activeSubscriptionInfoList?.map { it.subscriptionId } ?: emptyList()
            else emptyList()
        } catch (_: SecurityException) { emptyList() }
        val valid = ids.filter { SubscriptionManager.isValidSubscriptionId(it) }.take(4).toSet()
        if (valid.isNotEmpty()) return valid
        val defaultId = SubscriptionManager.getDefaultDataSubscriptionId()
        return if (SubscriptionManager.isValidSubscriptionId(defaultId)) setOf(defaultId) else emptySet()
    }

    private fun ensureCellListeners() {
        if (!policy.monitoringEnabled || !policy.cellularEnabled ||
            !CellTelemetry(this).permitted() || batteryLow()) {
            unregisterCellListeners()
            return
        }
        val desired = activeSubscriptionIds()
        for (sub in cellListeners.keys.toList()) {
            if (sub !in desired) unregisterCellListener(sub)
        }
        val base = getSystemService(TelephonyManager::class.java) ?: return
        for (sub in desired) {
            if (cellListeners.containsKey(sub)) continue
            val scoped = base.createForSubscriptionId(sub)
            try {
                val registration = if (Build.VERSION.SDK_INT >= 31) {
                    val callback = CellInfoChanged31(sub)
                    scoped.registerTelephonyCallback(executor, callback)
                    ListenerRegistration(scoped, callback, null)
                } else {
                    @Suppress("DEPRECATION") val listener = CellInfoChangedLegacy(sub)
                    @Suppress("DEPRECATION") scoped.listen(listener, PhoneStateListener.LISTEN_CELL_INFO)
                    ListenerRegistration(scoped, null, listener)
                }
                cellListeners[sub] = registration
                android.util.Log.i("SATELLITE1", "CELL_LISTENER_ACTIVE: per-subscription callback registered")
            } catch (error: Exception) {
                android.util.Log.w("SATELLITE1", "Cell listener deferred: ${error.javaClass.simpleName}")
            }
        }
    }
    private fun unregisterCellListener(subId: Int) {
        val reg = cellListeners.remove(subId) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                reg.modern?.let { reg.telephony.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION") reg.legacy?.let { reg.telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (_: Exception) { /* revoked permission or disappearing subscription */ }
    }
    private fun unregisterCellListeners() { cellListeners.keys.toList().forEach(::unregisterCellListener) }

    private fun batteryLow(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return !charging && level >= 0 && scale > 0 && level * 100 / scale < 15
    }
    private fun scheduleSample() {
        if (!policy.monitoringEnabled) { stopSelf(); return }
        if (batteryLow()) { geo.stop(); unregisterCellListeners(); return }
        if (policy.locationEnabled) geo.start() else geo.stop()
        ensureCellListeners()
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            try {
                val now = System.currentTimeMillis()
                val snapshot = WifiTelemetry(this).approvedSnapshot(policy)
                if (snapshot != null) {
                    val encoded = snapshot.json.toString()
                    val digest = EvidenceStore.hex(EvidenceStore.sha256(encoded.toByteArray()))
                    if (digest != lastNetworkDigest || now - lastInventoryMs >= 30 * 60_000L) {
                        store.append("APPROVED_WIFI_CONTEXT", snapshot.json)
                        lastNetworkDigest = digest
                    }
                    if (now - lastInventoryMs >= 30 * 60_000L) {
                        lastInventoryMs = now
                        if (policy.probeApproved && snapshot.gateway != null) {
                            store.append("APPROVED_GATEWAY_CONNECTIVITY",
                                GatewayProbe.inspect(snapshot.network, snapshot.gateway))
                        }
                    }
                } else lastNetworkDigest = ""
                if (policy.cellularEnabled && now - lastCellSampleMs >= 5 * 60_000L) {
                    store.trackDefaultSubscription(SubscriptionManager.getDefaultDataSubscriptionId())
                    val collector = CellTelemetry(this)
                    for (sub in activeSubscriptionIds()) {
                        collector.sampleForSubscription(sub)?.let { snapshot ->
                            store.trackCellSample(snapshot, "cached_poll",
                                if (policy.locationEnabled) geo.recentFix() else null)
                            store.append("CELL_RADIO_OBSERVATION", snapshot)
                        }
                    }
                    lastCellSampleMs = now
                }
            } catch (exception: Exception) {
                // No source payloads or credentials in service logs.
                android.util.Log.w("SATELLITE1", "Collector deferred: ${exception.javaClass.simpleName}")
            } finally { busy.set(false) }
        }
    }
    override fun onDestroy() {
        MonitorRuntimeState.active = false
        MonitorRuntimeState.state = "STOPPED"
        android.util.Log.i("SATELLITE1", "MONITOR_STOPPED: service destroyed")
        handler.removeCallbacks(tick)
        geo.stop()
        unregisterCellListeners()
        if (callbackRegistered) connectivity.unregisterNetworkCallback(networkCallback)
        // Close the encrypted evidence store *after* already queued cell events finish.
        try {
            executor.execute { store.close() }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            store.close()
        }
        executor.shutdown()
        super.onDestroy()
    }
    companion object {
        const val ACTION_STOP = "org.beaconcomputing.satellite1.STOP"
        private const val CHANNEL = "satellite1_monitoring"
        private const val NOTIFICATION_ID = 10011
    }
}
