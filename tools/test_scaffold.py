# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
import hashlib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'app/src/main/java/org/beaconcomputing/satellite1'

class TestSatelliteScaffold(unittest.TestCase):
    def text(self, filename):
        return (APP / filename).read_text(encoding='utf-8')
    def test_all_kotlin_sources_sealed(self):
        for item in APP.glob('*.kt'):
            self.assertIn('BEACON COMPUTING INITIATIVE', item.read_text())
            self.assertIn('CJB-DEV-2026', item.read_text())
    def test_explicit_wifi_authorization(self):
        self.assertIn('approvedBssid', self.text('Policy.kt'))
        self.assertIn('approvedSsid', self.text('Policy.kt'))
        self.assertIn('probeApproved = false', self.text('Policy.kt'))
    def test_no_wide_network_scans(self):
        self.assertIn('intArrayOf(22, 80, 443)', self.text('GatewayProbe.kt'))
        self.assertIn('APPROVED_WIFI_GATEWAY_ONLY', self.text('GatewayProbe.kt'))
    def test_low_battery_pause(self):
        t = self.text('MonitorService.kt')
        self.assertIn('level * 100 / scale < 15', t)
        self.assertIn('geo.stop()', t)
    def test_visible_user_started_service(self):
        t = self.text('MonitorService.kt')
        self.assertIn('ServiceCompat.startForeground', t)
        self.assertIn('ACTION_STOP', t)
        self.assertIn('START_NOT_STICKY', t)
    def test_radio_and_geo_permissions(self):
        t = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
        for k in ('ACCESS_FINE_LOCATION','READ_PHONE_STATE','FOREGROUND_SERVICE_LOCATION','INTERNET'):
            self.assertIn(k, t)
        self.assertNotIn('RECEIVE_BOOT_COMPLETED', t)
    def test_jsonl_export_manifest(self):
        t = self.text('EvidenceStore.kt')
        for k in ('EXPORT_MANIFEST','payload_sha256','record_sha256','fileSha256','exportJsonl'):
            self.assertIn(k, t)
        self.assertIn('application/x-ndjson', self.text('MainActivity.kt'))
    def test_encrypted_payloads(self):
        self.assertIn('AES/GCM/NoPadding', self.text('CryptoBox.kt'))
        self.assertIn('AndroidKeyStore', self.text('CryptoBox.kt'))
        self.assertIn('crypto.seal(', self.text('EvidenceStore.kt'))
    def test_https_upload_ack(self):
        t=self.text('UploadWorker.kt')
        for k in ('HttpsURLConnection','batch_sha256','accepted_count','receipt_id','https'):
            self.assertIn(k,t)
        self.assertNotIn('trustAll', t)
    def test_dedicated_cell_observations(self):
        t=self.text('CellTelemetry.kt')
        self.assertIn('CellInfoNr',t)
        self.assertIn('CellInfoLte',t)
        self.assertIn('tower_geolocation_verified',t)
    def test_30_min_schedule(self):
        self.assertIn('PeriodicWorkRequestBuilder<UploadWorker>(30, TimeUnit.MINUTES)', self.text('UploadWorker.kt'))
    def test_branding_palette(self):
        t=self.text('MainActivity.kt')
        for color in ('05080D','650D28','00E5FF','111820','F4F8FB'):
            self.assertIn(color,t)
    def test_document_header(self):
        for item in ROOT.rglob('*.md'):
            self.assertIn('BEACON COMPUTING INITIATIVE',item.read_text())
            self.assertIn('CJB-DEV-2026',item.read_text())

if __name__ == '__main__': unittest.main()
