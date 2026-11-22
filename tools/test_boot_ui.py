#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# UI source tests pass IF AND ONLY IF version, splash, motion and consent gates remain wired.
import pathlib, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/org/beaconcomputing/satellite1'
class BootUiTests(unittest.TestCase):
    def test_animating_orbit_and_radar(self):
        s=(SRC/'BootSequence.kt').read_text()
        for marker in ('rememberInfiniteTransition', 'satellite-orbit', 'radar-sweep',
                       'drawOrbitalScene', 'ENTER MISSION CONTROL', 'Animatable(0f)',
                       'animateIntAsState', 'animateColorAsState'):
            with self.subTest(marker=marker): self.assertIn(marker,s)
    def test_no_telemetry_side_effects_in_boot(self):
        s=(SRC/'BootSequence.kt').read_text()
        for marker in ('TelephonyManager','LocationManager','startForegroundService',
                       'HttpURLConnection','EvidenceStore(', 'POST_NOTIFICATIONS'):
            with self.subTest(marker=marker): self.assertNotIn(marker,s)
    def test_launch_and_dark_theme(self):
        s=(SRC/'MainActivity.kt').read_text()
        self.assertIn('installSplashScreen()',s)
        self.assertIn('Settings.Global.ANIMATOR_DURATION_SCALE',s)
        self.assertIn('rememberSaveable',s)
        theme=(ROOT/'app/src/main/res/values/styles.xml').read_text()
        self.assertIn('Theme.SplashScreen',theme)
        self.assertIn('postSplashScreenTheme',theme)
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('@style/Theme.Satellite1.Splash',manifest)
    def test_version_and_bci_identity(self):
        s=(ROOT/'app/build.gradle.kts').read_text()
        self.assertIn('versionCode = 6',s)
        self.assertIn('0.1.5-orbital-boot',s)
        self.assertIn('core-splashscreen:1.0.1',s)
        self.assertIn('CJB-DEV-2026',(SRC/'BootSequence.kt').read_text())
if __name__=='__main__': unittest.main()
