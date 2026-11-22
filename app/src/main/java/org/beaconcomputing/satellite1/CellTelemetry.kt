// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telephony.*
import org.json.JSONArray
import org.json.JSONObject

/** Phone-observed cells, not authenticated tower coordinates or a complete RF scan. */
class CellTelemetry(private val context: Context) {
    fun sample(): JSONObject? = sampleForSubscription(SubscriptionManager.getDefaultDataSubscriptionId())

    fun sampleForSubscription(subscriptionId: Int): JSONObject? {
        if (!permitted()) return null
        val base = context.getSystemService(TelephonyManager::class.java) ?: return null
        if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) return null
        val tm = base.createForSubscriptionId(subscriptionId)
        @Suppress("DEPRECATION")
        val cells = try { tm.allCellInfo ?: emptyList() } catch (_: SecurityException) { return null }
        return fromCells(cells, "cached_poll", subscriptionId)
    }

    fun permitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Use the supplied radio callback list; do not trigger a fresh modem scan. */
    fun fromCells(cells: List<CellInfo>, source: String, subscriptionId: Int): JSONObject {
        val arr = JSONArray()
        for (cell in cells.take(40)) {
            val obj = JSONObject()
                .put("registered", cell.isRegistered)
                .put("radio", when (cell) {
                    is CellInfoLte -> "LTE"
                    is CellInfoNr -> "NR"
                    is CellInfoGsm -> "GSM"
                    is CellInfoWcdma -> "WCDMA"
                    is CellInfoTdscdma -> "TDSCDMA"
                    else -> "OTHER"
                })
            @Suppress("DEPRECATION")
            val measuredAtMs = if (Build.VERSION.SDK_INT >= 30) cell.timestampMillis
                else cell.timeStamp / 1_000_000L
            val ageMs = SystemClock.elapsedRealtime() - measuredAtMs
            if (ageMs >= 0) obj.put("measurement_age_ms", ageMs)
            when (cell) {
                is CellInfoLte -> {
                    val id = cell.cellIdentity
                    obj.putSafe("mcc", id.mccString).putSafe("mnc", id.mncString)
                        .putSafeInt("ci", id.ci).putSafeInt("tac", id.tac)
                        .putSafeInt("pci", id.pci).putSafeInt("earfcn", id.earfcn)
                        .putSafeInt("rsrp_dbm", cell.cellSignalStrength.rsrp)
                        .putSafeInt("rsrq_db", cell.cellSignalStrength.rsrq)
                        .putSafeInt("signal_dbm", cell.cellSignalStrength.dbm)
                }
                is CellInfoNr -> {
                    val id = cell.cellIdentity as CellIdentityNr
                    obj.putSafe("mcc", id.mccString).putSafe("mnc", id.mncString)
                        .putSafeLong("nci", id.nci).putSafeInt("tac", id.tac)
                        .putSafeInt("pci", id.pci).putSafeInt("nrarfcn", id.nrarfcn)
                        .putSafeInt("signal_dbm", cell.cellSignalStrength.dbm)
                }
                is CellInfoGsm -> {
                    val id = cell.cellIdentity
                    obj.putSafe("mcc", id.mccString).putSafe("mnc", id.mncString)
                        .putSafeInt("lac", id.lac).putSafeInt("cid", id.cid)
                        .putSafeInt("arfcn", id.arfcn)
                        .putSafeInt("signal_dbm", cell.cellSignalStrength.dbm)
                }
                is CellInfoWcdma -> {
                    val id = cell.cellIdentity
                    obj.putSafe("mcc", id.mccString).putSafe("mnc", id.mncString)
                        .putSafeInt("lac", id.lac).putSafeInt("cid", id.cid)
                        .putSafeInt("psc", id.psc).putSafeInt("uarfcn", id.uarfcn)
                        .putSafeInt("signal_dbm", cell.cellSignalStrength.dbm)
                }
                is CellInfoTdscdma -> {
                    val id = cell.cellIdentity
                    obj.putSafe("mcc", id.mccString).putSafe("mnc", id.mncString)
                        .putSafeInt("lac", id.lac).putSafeInt("cid", id.cid)
                        .putSafeInt("signal_dbm", cell.cellSignalStrength.dbm)
                }
            }
            arr.put(obj)
        }
        val defaultSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        val base = context.getSystemService(TelephonyManager::class.java)
        val scoped = try { base?.createForSubscriptionId(subscriptionId) } catch (_: Exception) { null }
        val homePlmn = try { scoped?.simOperator.orEmpty() } catch (_: SecurityException) { "" }
        val isRoaming: Boolean? = try { scoped?.isNetworkRoaming } catch (_: SecurityException) { null }
        val freshNeighborCount = (0 until arr.length()).count { index ->
            val item = arr.optJSONObject(index)
            item != null && !item.optBoolean("registered", false) &&
                item.optLong("measurement_age_ms", Long.MAX_VALUE) in 0..120_000L
        }
        return JSONObject()
            .put("source", "android.telephony.$source")
            .put("default_data_subscription_id", defaultSubId)
            .put("observation_subscription_id", subscriptionId)
            .put("home_plmn", homePlmn.takeIf { it.matches(Regex("[0-9]{5,6}")) } ?: JSONObject.NULL)
            .put("network_roaming", isRoaming ?: JSONObject.NULL)
            .put("visible_neighbor_count", freshNeighborCount)
            .put("fresh_scan_guaranteed", false)
            .put("tower_geolocation_verified", false)
            .put("cell_count", arr.length())
            .put("cells", arr)
    }
    private fun JSONObject.putSafe(k: String, v: String?): JSONObject {
        if (!v.isNullOrBlank()) put(k, v)
        return this
    }
    private fun JSONObject.putSafeInt(k: String, v: Int): JSONObject {
        if (v != Int.MAX_VALUE && v != Int.MIN_VALUE && v >= -160) put(k, v)
        return this
    }
    private fun JSONObject.putSafeLong(k: String, v: Long): JSONObject {
        if (v != Long.MAX_VALUE && v != Long.MIN_VALUE) put(k, v)
        return this
    }
}
