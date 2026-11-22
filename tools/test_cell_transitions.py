# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/org/beaconcomputing/satellite1'
class TestCellTransitions(unittest.TestCase):
    def read(self, name): return (SRC/name).read_text()
    def test_runtime_callbacks(self):
        text=self.read('MonitorService.kt')
        self.assertIn('TelephonyCallback.CellInfoListener', text)
        self.assertIn('PhoneStateListener.LISTEN_CELL_INFO', text)
        self.assertIn('unregisterTelephonyCallback', text)
        self.assertIn('unregisterCellListeners()', text)
    def test_change_is_distinct_from_periodic_observation(self):
        service=self.read('MonitorService.kt')
        self.assertIn('store.trackCellSample(',service)
        self.assertIn('store.append("CELL_RADIO_OBSERVATION"',service)
        rules=self.read('CellChangeRules.kt')
        self.assertIn('previousKeys == currentKeys', rules)
        self.assertIn('CELL_SERVING_CHANGED', rules)
        self.assertIn('CELL_REGISTERED_SET_CHANGED', rules)
        self.assertIn('CELL_BASELINE_ESTABLISHED', rules)
        self.assertIn('subscriptionChanged', rules)
        self.assertIn('if (currentKeys.isEmpty()) return null', rules)
    def test_registered_cell_only(self):
        code=self.read('CellChangeTracker.kt')
        self.assertIn('if (!cell.optBoolean("registered", false)) continue',code)
        self.assertIn('physical_tower_change_verified',code)
        self.assertIn('exact_handoff_time_verified',code)
    def test_encrypted_persistent_baseline_and_additive_migration(self):
        code=self.read('EvidenceStore.kt')
        self.assertIn('null, 3)',code)
        self.assertIn('if (oldVersion == 1)',code)
        self.assertIn('CREATE TABLE cell_state',code)
        self.assertIn('db.beginTransaction()',code)
        self.assertIn('crypto.seal(state.toString())',code)
        self.assertNotIn('DROP TABLE events',code)
    def test_cell_dashboard(self):
        code=self.read('MainActivity.kt')
        self.assertIn('CELLULAR INTELLIGENCE  //  LIVE',code)
        self.assertIn('REFRESH CELL EVENTS',code)
        self.assertIn('cellChangeCount',code)
    def test_version_advances_for_same_key_upgrade(self):
        gradle=(ROOT/'app/build.gradle.kts').read_text()
        self.assertIn('versionCode = 6',gradle)
        self.assertIn('versionName = "0.1.5-orbital-boot"',gradle)
        sign=(ROOT/'tools/bci-release-sign.sh').read_text()
        self.assertIn('SATELLITE1-v0.1.5-signed.apk',sign)
    def test_continuous_collection_has_explicit_gates(self):
        svc=self.read('MonitorService.kt')
        for token in ('policy.monitoringEnabled', 'policy.cellularEnabled',
                      'batteryLow()', 'CellTelemetry(this).permitted()', 'START_NOT_STICKY'):
            self.assertIn(token,svc)
if __name__=='__main__': unittest.main()
