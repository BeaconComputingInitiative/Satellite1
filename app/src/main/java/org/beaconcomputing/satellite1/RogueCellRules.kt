// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

/** Screening rule, NOT a validated detector of cell-site simulators or attacker identity. */
data class RogueRadioCell(
    val identity: String,
    val radio: String,
    val plmn: String?,
    val signalDbm: Int?,
    val ageMs: Long?
)

data class RogueRadioContext(
    val homePlmn: String?,
    val roaming: Boolean?,
    val stationary: Boolean?,
    val previousNeighborCount: Int?,
    val currentNeighborCount: Int?,
    val elapsedMs: Long?
)

data class RogueAssessment(val classification: String, val score: Int, val reasons: List<String>)

/** Signals are inherently ambiguous; no non-root Android result is labeled confirmed. */
object RogueCellRules {
    const val MAX_MEASUREMENT_AGE_MS = 120_000L
    const val MAX_OBSERVATION_GAP_MS = 600_000L

    /** Assess IF AND ONLY IF both observations are fresh, unambiguous and ordered. */
    fun assess(previous: RogueRadioCell?, current: RogueRadioCell?, context: RogueRadioContext): RogueAssessment {
        fun unknown() = RogueAssessment("INSUFFICIENT_EVIDENCE", 0, listOf("AMBIGUOUS_OR_STALE_CELL_DATA"))
        if (previous == null || current == null || previous.identity.isBlank() || current.identity.isBlank()) return unknown()
        if (previous.ageMs == null || current.ageMs == null || previous.ageMs !in 0..MAX_MEASUREMENT_AGE_MS ||
            current.ageMs !in 0..MAX_MEASUREMENT_AGE_MS ||
            context.elapsedMs == null || context.elapsedMs !in 1..MAX_OBSERVATION_GAP_MS) return unknown()
        if (previous.identity == current.identity) return RogueAssessment("NO_ANOMALY_OBSERVED", 0, emptyList())
        val reasons = mutableListOf<String>()
        var score = 0
        val downgradedTo2g = previous.radio in setOf("LTE", "NR") && current.radio == "GSM"
        if (downgradedTo2g) { reasons.add("LTE_NR_TO_2G_DOWNGRADE"); score += 2 }
        val simPlmn = context.homePlmn
        val plmnMismatch = context.roaming == false &&
            simPlmn != null && simPlmn.matches(Regex("[0-9]{5,6}")) &&
            current.plmn != null && current.plmn.matches(Regex("[0-9]{5,6}")) &&
            current.plmn != simPlmn
        if (plmnMismatch) { reasons.add("SIM_HOME_PLMN_DISAGREEMENT"); score += 1 }
        val surge = context.stationary == true && previous.signalDbm != null && current.signalDbm != null &&
            previous.signalDbm in -140..-35 && current.signalDbm in -140..-35 &&
            current.signalDbm - previous.signalDbm >= 25
        if (surge) { reasons.add("STATIONARY_SIGNAL_SURGE"); score += 1 }
        val neighborDrop = context.stationary == true &&
            (context.previousNeighborCount ?: -1) >= 3 && context.currentNeighborCount == 0
        if (neighborDrop) { reasons.add("NEIGHBOR_VISIBILITY_DROP"); score += 1 }
        // All four weak/medium indications must coincide before issuing a *suspected* label.
        // This cannot establish that a radio cell is illegitimate, or who operates it.
        val classification = when {
            downgradedTo2g && plmnMismatch && surge && neighborDrop && score >= 5 -> "SUSPECTED_ROGUE_CELL"
            score > 0 -> "CELLULAR_ANOMALY"
            else -> "NO_ANOMALY_OBSERVED"
        }
        return RogueAssessment(classification, score, reasons.toList())
    }
}
