// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/** Adapts encrypted per-subscription evidence to conservative rules; no RF authentication claimed. */
object RogueCellClassifier {
    private fun cellFor(state: JSONObject?): RogueRadioCell? {
        val serving = state?.optJSONArray("registered_cells") ?: return null
        if (serving.length() != 1) return null // NSA / multi-cell reports require independent adjudication.
        val cell = serving.optJSONObject(0) ?: return null
        val key = state.optJSONArray("identities")?.optString(0) ?: return null
        if (key.isBlank()) return null
        val plmn = cell.optString("mcc") + cell.optString("mnc")
        return RogueRadioCell(
            identity = key,
            radio = cell.optString("radio"),
            plmn = plmn.takeIf { it.matches(Regex("[0-9]{5,6}")) },
            signalDbm = cell.optInt("signal_dbm", Int.MAX_VALUE).takeIf { it in -140..-35 },
            ageMs = cell.optLong("measurement_age_ms", -1).takeIf { it >= 0 }
        )
    }
    private fun elapsed(previous: JSONObject?, current: JSONObject): Long? = try {
        val p = Instant.parse(previous?.getString("observed_utc"))
        val c = Instant.parse(current.getString("observed_utc"))
        Duration.between(p, c).toMillis().takeIf { it > 0 }
    } catch (_: Exception) { null }

    private fun stationary(previous: JSONObject?, current: JSONObject): Boolean? {
        val a = previous?.optJSONObject("phone_location_at_observation") ?: return null
        val b = current.optJSONObject("phone_location_at_observation") ?: return null
        val accuracyA = a.optDouble("accuracy_m", Double.NaN)
        val accuracyB = b.optDouble("accuracy_m", Double.NaN)
        if (!accuracyA.isFinite() || !accuracyB.isFinite() || accuracyA !in 0.0..75.0 || accuracyB !in 0.0..75.0) return null
        val latA = a.optDouble("latitude", Double.NaN)
        val lonA = a.optDouble("longitude", Double.NaN)
        val latB = b.optDouble("latitude", Double.NaN)
        val lonB = b.optDouble("longitude", Double.NaN)
        if (listOf(latA,lonA,latB,lonB).any { !it.isFinite() } ||
            latA !in -90.0..90.0 || latB !in -90.0..90.0 ||
            lonA !in -180.0..180.0 || lonB !in -180.0..180.0) return null
        val distance = FloatArray(1)
        Location.distanceBetween(latA, lonA, latB, lonB, distance)
        // A stationary flag indicates only that two recorded phone fixes were nearby.
        return distance[0] <= 60f
    }
    fun evaluate(previous: JSONObject?, current: JSONObject): JSONObject {
        val context = RogueRadioContext(
            homePlmn = current.optString("home_plmn").takeIf { it.isNotBlank() },
            roaming = if (current.has("network_roaming") && !current.isNull("network_roaming"))
                current.optBoolean("network_roaming") else null,
            stationary = stationary(previous, current),
            previousNeighborCount = previous?.optInt("visible_neighbor_count", -1)?.takeIf { it >= 0 },
            currentNeighborCount = current.optInt("visible_neighbor_count", -1).takeIf { it >= 0 },
            elapsedMs = elapsed(previous, current)
        )
        val result = RogueCellRules.assess(cellFor(previous), cellFor(current), context)
        return JSONObject()
            .put("classification", result.classification)
            .put("risk_score", result.score)
            .put("reason_codes", JSONArray(result.reasons))
            .put("detector_version", "bci-roguecell-heuristic-v1")
            .put("evidence_basis", "android_public_telephony_api_unverified")
            .put("subscription_id", current.optInt("subscription_id", -1))
            .put("observation_utc", current.optString("observed_utc"))
            .put("independent_rf_confirmation", false)
            .put("rogue_cell_confirmed", false)
            .put("physical_tower_identity_verified", false)
            .put("stationary_location_supported", context.stationary ?: JSONObject.NULL)
            .put("explanation", "Heuristic screening only. Roaming, MVNOs, network maintenance, dual connectivity, and stale reports can mimic anomalies.")
    }
}
