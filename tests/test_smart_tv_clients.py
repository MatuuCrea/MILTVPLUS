import json
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]


class SmartTvClientTests(unittest.TestCase):
    def test_tizen_manifest_and_assets_are_present(self):
        root = ET.parse(ROOT / "platforms/tizen/config.xml").getroot()
        ns = {"t": "http://tizen.org/ns/widgets", "w": "http://www.w3.org/ns/widgets"}
        self.assertEqual(root.findtext("w:content", namespaces=ns), "index.html")
        self.assertIsNotNone(root.find("t:profile[@name='tv-samsung']", ns))
        self.assertTrue((ROOT / "platforms/tizen/index.html").is_file())
        self.assertTrue((ROOT / "platforms/tizen/app.js").is_file())
        self.assertTrue((ROOT / "platforms/tizen/icon.png").read_bytes().startswith(bytes.fromhex("89504e470d0a1a0a")))

    def test_webos_manifest_and_assets_are_present(self):
        app = json.loads((ROOT / "platforms/webos/appinfo.json").read_text(encoding="utf-8"))
        self.assertEqual(app["type"], "web")
        self.assertEqual(app["main"], "index.html")
        for name in (app["icon"], app["largeIcon"]):
            self.assertTrue((ROOT / "platforms/webos" / name).is_file())
            self.assertTrue((ROOT / "platforms/webos" / name).read_bytes().startswith(bytes.fromhex("89504e470d0a1a0a")))
        self.assertTrue((ROOT / "platforms/webos/app.js").is_file())

    def test_both_clients_only_query_published_channels(self):
        for path in ("platforms/tizen/app.js", "platforms/webos/app.js"):
            source = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn("/rest/v1/tv_channels?", source)
            self.assertIn("is_published=eq.true", source)
            self.assertIn("sb_publishable_", source)
            self.assertNotIn("service_role", source.lower())

    def test_direct_playback_requires_platform_supported_type_and_https(self):
        tizen = (ROOT / "platforms/tizen/app.js").read_text(encoding="utf-8")
        webos = (ROOT / "platforms/webos/app.js").read_text(encoding="utf-8")
        self.assertIn('c.playback_type==="hls"||c.playback_type==="dash"', tizen)
        self.assertIn('c.playback_type==="hls"', webos)
        self.assertNotIn('c.playback_type==="hls"||c.playback_type==="dash"', webos)
        for source in (tizen, webos):
            self.assertIn('x.protocol==="https:"', source)
            self.assertIn("official", source.lower())

    def test_tv_navigation_and_free_policy_are_visible(self):
        for path in ("platforms/tizen/index.html", "platforms/webos/index.html"):
            source = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn("100 % gratis", source)
            self.assertIn("BACK", source)
        for path in ("platforms/tizen/app.js", "platforms/webos/app.js"):
            source = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn("e.keyCode===13", source)
            self.assertIn("e.keyCode===37", source)
            self.assertIn("e.keyCode===39", source)


    def test_android_tv_uses_television_mode_and_free_branding(self):
        main = (ROOT / "app/src/main/java/com/matuucrea/miltvplus/MainActivity.kt").read_text(encoding="utf-8")
        ui = (ROOT / "app/src/main/java/com/matuucrea/miltvplus/ui/PremiumExperience.kt").read_text(encoding="utf-8")
        self.assertIn("UI_MODE_TYPE_TELEVISION", main)
        self.assertIn("&& !isTelevision", main)
        self.assertIn("MILTVPLUS · 100 % GRATIS", ui)
        self.assertNotIn('Text("MILTVPLUS Premium"', ui)
        self.assertNotIn('Text("Experiencia Premium"', ui)


if __name__ == "__main__":
    unittest.main()
