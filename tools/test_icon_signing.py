# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"

class TestIconAndSigning(unittest.TestCase):
    def test_launcher_resources_exist(self):
        manifest=(ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android:icon="@mipmap/ic_launcher"',manifest)
        self.assertIn('android:roundIcon="@mipmap/ic_launcher_round"',manifest)
        xml=ET.parse(RES / "mipmap-anydpi-v26/ic_launcher.xml")
        self.assertEqual(xml.getroot().tag,"adaptive-icon")
        self.assertTrue((RES / "drawable-nodpi/ic_satellite1_launcher_foreground.png").is_file())
    def test_all_density_bitmaps(self):
        for density,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
            for name in ('ic_launcher.png','ic_launcher_round.png'):
                data=(RES / f'mipmap-{density}' / name).read_bytes()
                self.assertEqual(data[:8],b'\x89PNG\r\n\x1a\n')
                self.assertEqual(struct.unpack('>II',data[16:24]),(px,px))
    def test_foreground_dimensions(self):
        data=(RES / 'drawable-nodpi/ic_satellite1_launcher_foreground.png').read_bytes()
        self.assertEqual(data[:8],b'\x89PNG\r\n\x1a\n')
        self.assertEqual(struct.unpack('>II',data[16:24]),(432,432))
    def test_private_keystore_not_bundled(self):
        self.assertFalse(list(ROOT.rglob('*.jks')))
        self.assertFalse(list(ROOT.rglob('*.p12')))
    def test_release_signer_checks(self):
        t=(ROOT / 'tools/bci-release-sign.sh').read_text()
        for token in ('sha256sum --check --strict SHA256SUMS', 'assembleRelease', 'zipalign',
                      'apksigner', 'verify --verbose --print-certs', 'KEY_CREATED',
                      'SIGNED_APK_SHA256'):
            self.assertIn(token,t)

if __name__=='__main__': unittest.main()
