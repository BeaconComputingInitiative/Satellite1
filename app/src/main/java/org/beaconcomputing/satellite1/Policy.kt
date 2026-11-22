// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.content.Context
import android.net.wifi.WifiInfo

/** Explicit choices survive restarts. No device recon before a user-approved network is verified. */
class Policy(context: Context) {
    private val p = context.getSharedPreferences("satellite1-policy", Context.MODE_PRIVATE)
    var locationEnabled: Boolean
        get() = p.getBoolean("location", false)
        set(value) { p.edit().putBoolean("location", value).apply() }
    var cellularEnabled: Boolean
        get() = p.getBoolean("cellular", false)
        set(value) { p.edit().putBoolean("cellular", value).apply() }
    var probeApproved: Boolean
        get() = p.getBoolean("probe", false)
        set(value) { p.edit().putBoolean("probe", value).apply() }
    var autoUpload: Boolean
        get() = p.getBoolean("auto_upload", false)
        set(value) { p.edit().putBoolean("auto_upload", value).apply() }
    var monitoringEnabled: Boolean
        get() = p.getBoolean("monitoring", false)
        set(value) { p.edit().putBoolean("monitoring", value).apply() }
    var endpoint: String
        get() = p.getString("endpoint", "") ?: ""
        set(value) { p.edit().putString("endpoint", value.trim()).apply() }
    var approvedSsid: String
        get() = p.getString("approved_ssid", "") ?: ""
        private set(value) { p.edit().putString("approved_ssid", value).apply() }
    var approvedBssid: String
        get() = p.getString("approved_bssid", "") ?: ""
        private set(value) { p.edit().putString("approved_bssid", value).apply() }

    fun approve(info: WifiInfo): Boolean {
        val ssid = info.ssid.removeSurrounding("\"")
        val bssid = info.bssid ?: ""
        if (ssid.isBlank() || ssid == WifiManagerUnknown || bssid.isBlank() ||
            bssid == "02:00:00:00:00:00") return false
        approvedSsid = ssid
        approvedBssid = bssid.lowercase()
        probeApproved = false // Each new network requires separate active-probe authorization.
        return true
    }
    fun isApproved(info: WifiInfo?): Boolean {
        if (info == null) return false
        val ssid = info.ssid.removeSurrounding("\"")
        return approvedSsid.isNotBlank() && approvedBssid.isNotBlank() &&
            ssid == approvedSsid && info.bssid?.lowercase() == approvedBssid
    }
    fun revoke() {
        approvedSsid = ""
        approvedBssid = ""
        probeApproved = false
    }
    companion object { private const val WifiManagerUnknown = "<unknown ssid>" }
}
