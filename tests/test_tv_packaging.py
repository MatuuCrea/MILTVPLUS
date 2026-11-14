import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class SmartTvPackagingTests(unittest.TestCase):
    def test_packaging_script_builds_vendor_development_packages(self):
        script = (ROOT / "scripts/package-smart-tvs.sh").read_text(encoding="utf-8")
        for value in ("TIZEN_CERT_PROFILE", "tizen build-web", "tizen package -t wgt -s",
                      "ares-package --no-minify", "*.wgt", "*.ipk", "SHA256SUMS"):
            self.assertIn(value, script)
        self.assertNotIn("ares-package --sign", script)
        self.assertNotIn("WEBOS_KEY_PASSPHRASE", script)

    def test_signing_example_contains_only_non_secret_profile_settings(self):
        sample = (ROOT / "scripts/tv-signing.env.example").read_text(encoding="utf-8")
        self.assertIn("TIZEN_CERT_PROFILE=", sample)
        self.assertNotIn("PRIVATE_KEY", sample)
        self.assertNotIn("PASSPHRASE", sample)

    def test_sports_catalog_is_separate_and_adult_catalog_stays_empty(self):
        manager = (ROOT / "platforms/shared/media-manager.js").read_text(encoding="utf-8")
        self.assertIn('deportes: { api: "deportes"', manager)
        self.assertIn('if (sectionName === ADULT_SECTION) return { title: "Contenido para Adultos", items: [] }', manager)
        self.assertIn("this.adultCatalogEnabled = false", manager)

    def test_music_experience_keeps_immersive_mode_and_region_scoping(self):
        music = (ROOT / "platforms/shared/music-experience.js").read_text(encoding="utf-8")
        self.assertIn('option value="immersive"', music)
        self.assertIn("regionCode()", music)
        self.assertIn("/api/v2/catalog/musica/", music)
        self.assertIn("/api/v2/resolve/musica/", music)
