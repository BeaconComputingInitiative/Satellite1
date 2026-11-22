# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Regression: details() must reference in-scope current state (not derive() snapshot).
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
TRACKER = ROOT / 'app/src/main/java/org/beaconcomputing/satellite1/CellChangeTracker.kt'

class CellTrackerScopeRegression(unittest.TestCase):
    def test_details_state_is_current_not_snapshot(self):
        source = TRACKER.read_text()
        self.assertIn('fun details(kind: String, previous: JSONObject?, current: JSONObject, location: JSONObject?): JSONObject', source)
        details = source.split('    fun details(', 1)[1]
        for field in ('home_plmn', 'network_roaming', 'visible_neighbor_count'):
            self.assertIn(f'.put("{field}", current.opt', details)
        self.assertNotIn('snapshot.', details)
    def test_derive_still_uses_snapshot(self):
        derive = TRACKER.read_text().split('    fun derive(', 1)[1].split('    private fun keys(', 1)[0]
        self.assertIn('snapshot.opt("home_plmn")', derive)
        self.assertIn('snapshot.opt("network_roaming")', derive)
        self.assertIn('snapshot.optInt("visible_neighbor_count", -1)', derive)

if __name__ == '__main__':
    unittest.main()
