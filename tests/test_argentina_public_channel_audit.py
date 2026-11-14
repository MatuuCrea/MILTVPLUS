import json
import unittest
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
AUDIT_PATH = ROOT / "data" / "catalog" / "argentina-public-channel-audit.json"


class ArgentinaPublicChannelAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audit = json.loads(AUDIT_PATH.read_text(encoding="utf-8"))

    def test_audit_is_discovery_only_and_never_publishes_automatically(self):
        policy = self.audit["publication_policy"]
        self.assertFalse(policy["publish_to_supabase"])
        self.assertEqual(policy["default_status"], "pending_owner_and_stream_verification")
        self.assertTrue(policy["requires_rights_review_for_embedded_or_redistributed_playback"])

    def test_officially_identified_public_signals_have_https_official_pages(self):
        signals = self.audit["officially_identified_public_broadcasters"]
        self.assertGreaterEqual(len(signals), 6)
        for signal in signals:
            with self.subTest(signal=signal["slug"]):
                parsed = urlparse(signal["official_channel_url"])
                self.assertEqual(parsed.scheme, "https")
                self.assertTrue(parsed.hostname)
                self.assertEqual(signal["rights_status"], "no_miltvplus_redistribution_permission_recorded")
                self.assertNotIn("stream_url", signal)

    def test_caisau_public_signals_are_identified_by_primary_government_source(self):
        signals = {item["slug"]: item for item in self.audit["officially_identified_public_broadcasters"]}
        for slug in ("encuentro", "pakapaka", "deportv"):
            with self.subTest(slug=slug):
                item = signals[slug]
                self.assertEqual(item["operator"], "Contenidos Artísticos e Informativos S.A.U. (CAISAU)")
                self.assertIn("argentina.gob.ar", item["official_owner_directory"])

    def test_provincial_candidates_are_not_marked_as_verified_public_channels(self):
        provinces = self.audit["provincial_and_local_discovery_candidates"]
        self.assertEqual(len(provinces), 20)
        for province in provinces:
            with self.subTest(province=province["province"]):
                self.assertEqual(province["status"], "secondary_directory_lead_only")
                self.assertNotIn("official_watch_url", province)
                self.assertNotIn("stream_url", province)

    def test_terrestrial_free_to_air_candidates_are_not_misclassified_as_public(self):
        candidates = {item["name"]: item for item in self.audit["national_tda_signal_candidates"]}
        for name in ("Canal 26", "Crónica TV", "C5N", "LN+"):
            with self.subTest(name=name):
                self.assertEqual(candidates[name]["status"], "not_classified_as_argentine_public")


if __name__ == "__main__":
    unittest.main()
