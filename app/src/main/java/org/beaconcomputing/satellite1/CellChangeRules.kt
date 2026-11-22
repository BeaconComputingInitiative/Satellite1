// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

/** One subscription at a time. RF signal changes are never cell handoffs. */
object CellChangeRules {
    /** With multiple simultaneously registered cells, report the *set*, not a proven handoff. */
    fun classify(previousKeys: Set<String>?, currentKeys: Set<String>): String? {
        if (currentKeys.isEmpty()) return null
        if (previousKeys == null || previousKeys.isEmpty()) return "CELL_BASELINE_ESTABLISHED"
        if (previousKeys == currentKeys) return null
        if (previousKeys.size == 1 && currentKeys.size == 1) return "CELL_SERVING_CHANGED"
        return "CELL_REGISTERED_SET_CHANGED"
    }
    /** A SIM/default-data change is a separate event from a cell identity change. */
    fun subscriptionChanged(previous: Int?, current: Int?): Boolean =
        previous != null && current != null && previous >= 0 && current >= 0 && previous != current
}
