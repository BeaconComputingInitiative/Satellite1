# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/'app/src/main/java/org/beaconcomputing/satellite1'
class TestMonitorRecovery(unittest.TestCase):
    def test_dashboard_tracks_live_service(self):
        ui=(APP/'MainActivity.kt').read_text()
        self.assertIn('mutableStateOf(MonitorRuntimeState.active)', ui)
        self.assertIn('monitoring = MonitorRuntimeState.active',ui)
        self.assertIn('LIVE SERVICE:', ui)
        self.assertNotIn('mutableStateOf(policy.monitoringEnabled)',ui)
    def test_service_start_is_explicit_and_guarded(self):
        service=(APP/'MonitorService.kt').read_text()
        for phrase in ('MONITOR_ACTIVE:', 'MONITOR_HOLD:', 'MONITOR_STOPPED:',
                       'ServiceCompat.startForeground', 'MonitorRuntimeState.active = false',
                       'START_NOT_STICKY', 'tickScheduled'):
            self.assertIn(phrase,service)
    def test_service_does_not_restart_automatically(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertNotIn('RECEIVE_BOOT_COMPLETED',manifest)
        self.assertIn('android:foregroundServiceType="location"',manifest)
    def test_preserves_data_and_signing_identity(self):
        sign=(ROOT/'tools/bci-release-sign.sh').read_text()
        self.assertIn('satellite1-release.p12',sign)
        self.assertIn('SATELLITE1-v0.1.5-signed.apk',sign)
        self.assertNotIn('rm -rf',sign)
        db=(APP/'EvidenceStore.kt').read_text()
        self.assertNotIn('DROP TABLE events',db)
if __name__=='__main__': unittest.main()
