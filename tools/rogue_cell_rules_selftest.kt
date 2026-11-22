// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
// Standalone JVM rule tests; no Android device, RF measurements, or network access.
import org.beaconcomputing.satellite1.*

private fun cell(id: String, radio: String, plmn: String = "310260", dbm: Int = -90, age: Long = 1_000L) =
    RogueRadioCell(id, radio, plmn, dbm, age)
private fun context(
    home: String? = "310260", roaming: Boolean? = false, stationary: Boolean? = true,
    before: Int? = 0, after: Int? = 0, elapsed: Long? = 30_000L
) = RogueRadioContext(home, roaming, stationary, before, after, elapsed)

fun main() {
    var assertions = 0
    fun verify(name: String, valid: Boolean) {
        check(valid) { "FAILED: $name" }
        assertions++
    }
    val lte = cell("lte1", "LTE", dbm = -90)
    verify("null previous is unknown", RogueCellRules.assess(null, lte, context()).classification == "INSUFFICIENT_EVIDENCE")
    verify("same identity no anomaly", RogueCellRules.assess(lte, lte, context()).classification == "NO_ANOMALY_OBSERVED")
    verify("normal LTE handoff", RogueCellRules.assess(lte, cell("lte2", "LTE", dbm = -86), context()).classification == "NO_ANOMALY_OBSERVED")
    verify("signal alone not rogue", RogueCellRules.assess(lte, cell("lte2", "LTE", dbm = -60), context()).classification == "CELLULAR_ANOMALY")
    verify("downgrade alone not rogue", RogueCellRules.assess(lte, cell("g1", "GSM", dbm = -89), context(after=3)).classification == "CELLULAR_ANOMALY")
    verify("all indicators suspected", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55), context(before=5,after=0)).classification == "SUSPECTED_ROGUE_CELL")
    verify("roaming prevents false suspicion", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55), context(roaming=true,before=5,after=0)).classification != "SUSPECTED_ROGUE_CELL")
    verify("no location prevents false suspicion", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55), context(stationary=null,before=5,after=0)).classification != "SUSPECTED_ROGUE_CELL")
    verify("no neighbor corroboration prevents false suspicion", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55), context(before=5,after=2)).classification != "SUSPECTED_ROGUE_CELL")
    verify("stale current ignored", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55, 300_000), context()).classification == "INSUFFICIENT_EVIDENCE")
    verify("stale prior ignored", RogueCellRules.assess(cell("lte1", "LTE", age=300_000), cell("g1", "GSM", "311480", -55), context()).classification == "INSUFFICIENT_EVIDENCE")
    verify("gap too long ignored", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55), context(elapsed=650_000)).classification == "INSUFFICIENT_EVIDENCE")
    verify("negative age ignored", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55, -1), context()).classification == "INSUFFICIENT_EVIDENCE")
    verify("operator mismatch alone not rogue", RogueCellRules.assess(lte, cell("lte2", "LTE", "311480", -90), context(before=0,after=0)).classification == "CELLULAR_ANOMALY")
    verify("no home plmn prevents false suspicion", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55), context(home=null,before=5,after=0)).classification != "SUSPECTED_ROGUE_CELL")
    verify("missing prior identity ignored", RogueCellRules.assess(cell("", "LTE"), cell("g1", "GSM", "311480", -55), context()).classification == "INSUFFICIENT_EVIDENCE")
    verify("no confirmed outcome", RogueCellRules.assess(lte, cell("g1", "GSM", "311480", -55), context()).classification != "CONFIRMED_ROGUE_CELL")
    println("BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026")
    println("ROGUE-CELL HEURISTIC RULES: $assertions/$assertions PASS; real-world accuracy NOT VALIDATED")
}
