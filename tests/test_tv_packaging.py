import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class SmartTvPackagingTests(unittest.TestCase):
    def test_packaging_script_requires_vendor_signing_material(self):
        script = (ROOT / "scripts/package-smart-tvs.sh").read_text(encoding="utf-8")
        self.assertIn("TIZEN_CERT_PROFILE", script)
        self.assertIn("WEBOS_PRIVATE_KEY", script)
        self.assertIn("WEBOS_KEY_PASSPHRASE", script)
        self.assertIn("tizen package -t wgt", script)
        self.assertIn("ares-package --sign", script)
        self.assertIn("*.wgt", script)
        self.assertIn("*.ipk", script)
        self.assertIn("SHA256SUMS", script)

    def test_signing_example_contains_placeholders_not_private_material(self):
        sample = (ROOT / "scripts/tv-signing.env.example").read_text(encoding="utf-8")
        self.assertIn("TIZEN_CERT_PROFILE=", sample)
        self.assertIn("WEBOS_PRIVATE_KEY=", sample)
        self.assertIn("WEBOS_KEY_PASSPHRASE=", sample)
        self.assertIn("replace-with-secret", sample)

    def test_sports_catalog_is_separate_and_adult_catalog_stays_empty(self):
        manager = (ROOT / "platforms/shared/media-manager.js").read_text(encoding="utf-8")
        self.assertIn('deportes: { api: "tv_en_vivo"', manager)
        self.assertIn('filter: "sports"', manager)
        self.assertIn('if (sectionName === ADULT_SECTION) return { title: "Contenido para Adultos", items: [] }', manager)
        self.assertIn("this.adultCatalogEnabled = false", manager)

    def test_music_experience_keeps_immersive_mode_and_region_scoping(self):
        music = (ROOT / "platforms/shared/music-experience.js").read_text(encoding="utf-8")
        self.assertIn('option value="immersive"', music)
        self.assertIn("regionCode()", music)
        self.assertIn("/api/v2/catalog/musica/", music)
        self.assertIn("/api/v2/resolve/musica/", music)
