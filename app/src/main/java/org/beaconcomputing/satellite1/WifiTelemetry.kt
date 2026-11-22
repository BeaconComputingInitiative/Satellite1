// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address

/** Wi-Fi discovery limited to a positively approved SSID+BSSID; never trusts SSID alone. */
class WifiTelemetry(private val context: Context) {
    data class Snapshot(val network: Network, val wifi: WifiInfo, val json: JSONObject, val gateway: String?)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    @Suppress("DEPRECATION")
    fun approvedSnapshot(policy: Policy): Snapshot? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val wifi = wifiManager.connectionInfo ?: return null
        if (!policy.isApproved(wifi)) return null
        val wifiNetwork = connectivity.allNetworks.firstOrNull {
            val caps = connectivity.getNetworkCapabilities(it)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return@firstOrNull false
            // API 31+: when Android supplies network-specific WifiInfo, verify that
            // the actively routed Wi-Fi network is the approved AP, not stale cache.
            if (Build.VERSION.SDK_INT >= 31) {
                val networkInfo = caps.transportInfo as? WifiInfo ?: return@firstOrNull false
                if (!policy.isApproved(networkInfo)) return@firstOrNull false
            }
            true
        } ?: return null
        val links = connectivity.getLinkProperties(wifiNetwork) ?: return null
        val gateway = links.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val facts = JSONObject()
            .put("ssid", wifi.ssid.removeSurrounding("\""))
            .put("bssid", wifi.bssid)
            .put("signal_rssi_dbm", wifi.rssi)
            .put("frequency_mhz", wifi.frequency)
            .put("gateway_ipv4", gateway ?: JSONObject.NULL)
            .put("addresses", JSONArray(links.linkAddresses.map { it.address.hostAddress + "/" + it.prefixLength }))
            .put("dns", JSONArray(links.dnsServers.map { it.hostAddress }))
            .put("active_wifi_verified", true)
        return Snapshot(wifiNetwork, wifi, facts, gateway)
    }
    @Suppress("DEPRECATION")
    fun currentWifiForApproval(): WifiInfo? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val w = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return w?.connectionInfo
    }
}
