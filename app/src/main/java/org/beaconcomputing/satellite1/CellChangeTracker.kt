// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Per-subscription cellular baseline. Reject stale/unusable identities; never infer site geography. */
object CellChangeTracker {
    const val MAX_TRANSITION_AGE_MS = 120_000L
    private fun nonnegative(value: JSONObject, key: String): Long? {
        if (!value.has(key) || value.isNull(key)) return null
        return value.optLong(key, -1L).takeIf { it >= 0L }
    }
    private fun signature(cell: JSONObject): String? {
        val radio = cell.optString("radio")
        val mcc = cell.optString("mcc").takeIf { it.matches(Regex("[0-9]{3}")) } ?: return null
        val mnc = cell.optString("mnc").takeIf { it.matches(Regex("[0-9]{2,3}")) } ?: return null
        val area = nonnegative(cell, if (radio == "LTE" || radio == "NR") "tac" else "lac") ?: return null
        val global = when (radio) {
            "LTE" -> nonnegative(cell, "ci")
            "NR" -> nonnegative(cell, "nci")
            "GSM", "WCDMA", "TDSCDMA" -> nonnegative(cell, "cid")
            else -> null
        } ?: return null
        return listOf(radio, mcc, mnc, area.toString(), global.toString()).joinToString("|")
    }
    fun derive(snapshot: JSONObject, source: String): JSONObject? {
        val subId = snapshot.optInt("observation_subscription_id", -1)
        if (subId < 0) return null  // Unknown SIM: do not mix baselines across subscriptions.
        val cells = snapshot.optJSONArray("cells") ?: return null
        val byKey = sortedMapOf<String, JSONObject>()
        for (i in 0 until cells.length()) {
            val cell = cells.optJSONObject(i) ?: continue
            if (!cell.optBoolean("registered", false)) continue
            val age = nonnegative(cell, "measurement_age_ms") ?: continue
            if (age > MAX_TRANSITION_AGE_MS) continue // cached modem data is not proof of current attachment
            val key = signature(cell) ?: continue
            byKey[key] = JSONObject(cell.toString())
        }
        if (byKey.isEmpty()) return null // Never erase a baseline when only stale cells were returned.
        val keys = JSONArray()
        val serving = JSONArray()
        for ((key, item) in byKey) { keys.put(key); serving.put(item) }
        return JSONObject()
            .put("identities", keys)
            .put("registered_cells", serving)
            .put("subscription_id", subId)
            .put("observed_utc", Instant.now().toString())
            .put("observation_source", source)
            .put("measurement_freshness_limit_ms", MAX_TRANSITION_AGE_MS)
            .put("home_plmn", snapshot.opt("home_plmn") ?: JSONObject.NULL)
            .put("network_roaming", snapshot.opt("network_roaming") ?: JSONObject.NULL)
            .put("visible_neighbor_count", snapshot.optInt("visible_neighbor_count", -1))
            .put("physical_tower_change_verified", false)
    }
    private fun keys(state: JSONObject?): Set<String>? {
        val arr = state?.optJSONArray("identities") ?: return null
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.toSet()
    }
    fun classify(previous: JSONObject?, current: JSONObject): String? =
        CellChangeRules.classify(keys(previous), keys(current) ?: emptySet())

    fun details(kind: String, previous: JSONObject?, current: JSONObject, location: JSONObject?): JSONObject {
        val currentCells = current.getJSONArray("registered_cells")
        val priorCells = previous?.optJSONArray("registered_cells") ?: JSONArray()
        val facts = JSONObject()
            .put("change_type", when (kind) {
                "CELL_BASELINE_ESTABLISHED" -> "initial_per_subscription_baseline"
                "CELL_SERVING_CHANGED" -> "single_registered_cell_identity_replaced"
                else -> "registered_cell_set_changed_not_proven_handoff"
            })
            .put("subscription_id", current.getInt("subscription_id"))
            .put("previous_registered_count", priorCells.length())
            .put("current_registered_count", currentCells.length())
            .put("previous", priorCells)
            .put("current", currentCells)
            .put("previous_observed_utc", previous?.optString("observed_utc") ?: JSONObject.NULL)
            .put("observed_utc", current.getString("observed_utc"))
            .put("observation_source", current.getString("observation_source"))
            .put("measurement_freshness_limit_ms", MAX_TRANSITION_AGE_MS)
            .put("home_plmn", current.opt("home_plmn") ?: JSONObject.NULL)
            .put("network_roaming", current.opt("network_roaming") ?: JSONObject.NULL)
            .put("visible_neighbor_count", current.optInt("visible_neighbor_count", -1))
            .put("physical_tower_change_verified", false)
            .put("exact_handoff_time_verified", false)
        if (location != null) facts.put("phone_location_at_observation", location)
        return facts
    }
}
