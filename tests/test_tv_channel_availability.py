import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "check_tv_channel_availability.py"
SPEC = importlib.util.spec_from_file_location("tv_monitor", SCRIPT)
MONITOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MONITOR)


class AvailabilityClassificationTests(unittest.TestCase):
    def test_success_is_online(self):
        self.assertEqual(MONITOR.classify(200)[0], "online")
        self.assertEqual(MONITOR.classify(302)[0], "online")

    def test_access_limits_are_blocked_not_offline(self):
        self.assertEqual(MONITOR.classify(403)[0], "blocked")
        self.assertEqual(MONITOR.classify(429)[0], "blocked")

    def test_server_error_is_offline(self):
        self.assertEqual(MONITOR.classify(503)[0], "offline")

    def test_unknown_status_needs_review(self):
        self.assertEqual(MONITOR.classify(404)[0], "pending_review")

    def test_insecure_stream_urls_are_blocked_without_network_access(self):
        result = MONITOR.check_url("http://example.com/live.m3u8", stream_type="hls")
        self.assertEqual(result["status"], "blocked")
        self.assertIsNone(result["http_status"])

    def test_credentials_in_stream_url_are_blocked(self):
        result = MONITOR.check_url("https://user:password@example.com/live.m3u8", stream_type="hls")
        self.assertEqual(result["status"], "blocked")


if __name__ == "__main__":
    unittest.main()
