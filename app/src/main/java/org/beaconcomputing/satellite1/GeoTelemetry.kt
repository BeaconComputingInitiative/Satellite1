// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject

/** Foreground-service-only location; never auto-starts from BOOT_COMPLETED. */
class GeoTelemetry(private val context: Context, private val onMeasurement: (JSONObject) -> Unit) {
    private val location = context.getSystemService(LocationManager::class.java)
    private var started = false
    @Volatile private var lastFix: Location? = null

    fun recentFix(maxAgeMs: Long = 120_000L): JSONObject? {
        val value = lastFix ?: return null
        val ageMs = (SystemClock.elapsedRealtimeNanos() - value.elapsedRealtimeNanos) / 1_000_000L
        if (ageMs < 0 || ageMs > maxAgeMs) return null
        return JSONObject()
            .put("latitude", value.latitude)
            .put("longitude", value.longitude)
            .put("accuracy_m", value.accuracy)
            .put("fix_time_ms", value.time)
            .put("fix_age_ms", ageMs)
            .put("provider", value.provider)
    }
    private val listener = object : LocationListener {
        override fun onLocationChanged(value: Location) {
            lastFix = Location(value)
            onMeasurement(JSONObject()
                .put("latitude", value.latitude)
                .put("longitude", value.longitude)
                .put("accuracy_m", value.accuracy)
                .put("provider", value.provider)
                .put("fix_time_ms", value.time)
                .put("altitude_m", if (value.hasAltitude()) value.altitude else JSONObject.NULL)
                .put("bearing_deg", if (value.hasBearing()) value.bearing else JSONObject.NULL))
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    fun start() {
        if (started) return
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            val provider = when {
                location.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                location.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return
            }
            location.requestLocationUpdates(provider, 60_000L, 25f, listener)
            started = true
        } catch (_: SecurityException) { started = false }
    }
    fun stop() {
        lastFix = null
        if (started) try { location.removeUpdates(listener) } catch (_: SecurityException) { }
        started = false
    }
}
