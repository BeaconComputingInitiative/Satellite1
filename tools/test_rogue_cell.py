# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Qualify static integration IF AND ONLY IF test suite is complete.
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
K=ROOT/'app/src/main/java/org/beaconcomputing/satellite1'
class RogueCellIntegrationTests(unittest.TestCase):
    def test_rules_are_conservative(self):
        s=(K/'RogueCellRules.kt').read_text()
        for t in ('INSUFFICIENT_EVIDENCE', 'NO_ANOMALY_OBSERVED', 'CELLULAR_ANOMALY',
                  'SUSPECTED_ROGUE_CELL', 'LTE_NR_TO_2G_DOWNGRADE',
                  'SIM_HOME_PLMN_DISAGREEMENT', 'STATIONARY_SIGNAL_SURGE',
                  'NEIGHBOR_VISIBILITY_DROP', 'MAX_MEASUREMENT_AGE_MS'):
            self.assertIn(t,s)
        self.assertNotIn('CONFIRMED_ROGUE_CELL', s)
    def test_event_is_encrypted_and_atomic(self):
        s=(K/'EvidenceStore.kt').read_text()
        self.assertIn('RogueCellClassifier.evaluate(previous, state)',s)
        self.assertIn('insertEvent(db, "CELL_RISK_ASSESSMENT", risk)',s)
        self.assertIn('if (kind == "CELL_SERVING_CHANGED")',s)
        self.assertIn('db.beginTransaction()',s)
    def test_dashboard_does_not_claim_proof(self):
        s=(K/'MainActivity.kt').read_text()
        self.assertIn('ROGUE-CELL ANOMALY SCREENING',s)
        self.assertIn('Suspected does NOT mean a fake tower is verified',s)
        self.assertIn('riskAssessments = it.recentRiskAssessments(5)',s)
    def test_input_quality_and_privacy(self):
        s=(K/'RogueCellClassifier.kt').read_text()
        self.assertIn('if (serving.length() != 1) return null',s)
        self.assertIn('independent_rf_confirmation',s)
        self.assertIn('rogue_cell_confirmed',s)
        self.assertIn('phone_location_at_observation',s)
        tele=(K/'CellTelemetry.kt').read_text()
        self.assertIn('home_plmn',tele)
        self.assertIn('network_roaming',tele)
        self.assertNotIn('getSubscriberId(',tele)
        self.assertNotIn('getDeviceId(',tele)
    def test_no_autocontainment_or_unqualified_training(self):
        s=(K/'RogueCellClassifier.kt').read_text()+(K/'RogueCellRules.kt').read_text()
        self.assertNotIn('forceStopPackage',s)
        self.assertNotIn('approved_for_training',s)
        self.assertTrue((ROOT/'orbit11-intake/orbit11_receiver.py').exists())
if __name__=='__main__':unittest.main()
