"""Synthetic mobile sessions: never authenticates or uses live account data."""
import copy
import json
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app/src/main/python"))
from mobile_bridge import MobileService
from librus_shared.connector import ConnectorError
from librus_shared.data import demo_sections


class FakeConnector:
    created = []
    fail_login = None

    def __init__(self, login, password):
        self.rows = demo_sections()
        self.errors = {}
        self.calls = []
        self.closed = False
        self.body_reads = 0
        self.created.append(self)

    def connect(self):
        if self.fail_login:
            raise self.fail_login

    def fetch(self, kind):
        self.calls.append(kind)
        if kind in self.errors:
            raise self.errors[kind]
        return copy.deepcopy(self.rows[kind])

    def close(self):
        self.closed = True

    def read_message(self, item_id):
        self.body_reads += 1
        if "body" in self.errors:
            raise self.errors["body"]
        return {"title": "Synthetic message", "text": "SYNTHETIC_FULL_BODY_NOT_FOR_DISK"}


class MobileTests(unittest.TestCase):
    def setUp(self):
        FakeConnector.created = []
        FakeConnector.fail_login = None
        self.now = 2_000_000_000
        self.service = MobileService(factory=FakeConnector, clock=lambda: self.now)
        # A regression accidentally invoking the real HTTP adapter must fail.
        blocker = patch("requests.sessions.Session.send", side_effect=AssertionError("Network forbidden in tests"))
        blocker.start()
        self.addCleanup(blocker.stop)

    def connect(self, remember=False):
        self.service.connect("synthetic-parent", "test-only-password", remember)
        return FakeConnector.created[-1]

    def test_first_sync_is_quiet_and_password_is_opt_in(self):
        client = self.connect()
        self.assertEqual(len(client.calls), 6)
        self.assertEqual(sum(self.service.last_changes.values()), 0)
        exported = json.loads(self.service.export_json())
        self.assertIsNone(exported["credentials"])
        self.assertNotIn("test-only-password", self.service.state_json())
        self.assertFalse(self.service.state()["needs_login"])

    def test_restore_without_password_is_offline_and_does_not_connect(self):
        self.connect()
        restored = MobileService(factory=FakeConnector)
        restored.restore_json(self.service.export_json())
        restored.open()
        self.assertEqual(len(FakeConnector.created), 1)
        self.assertEqual(restored.mode, "offline")
        self.assertTrue(restored.state()["needs_login"])
        self.assertTrue(restored.tracker.sections["grades"])

    def test_remembered_open_connects_and_preserves_baseline(self):
        self.connect(True)
        saved = self.service.export_json()
        restored = MobileService(factory=FakeConnector)
        restored.restore_json(saved)
        restored.open()
        self.assertEqual(restored.mode, "live")
        self.assertEqual(sum(restored.last_changes.values()), 0)
        self.assertTrue(restored.state()["remembered"])

    def test_partial_error_keeps_section_and_its_timestamp(self):
        client = self.connect()
        before = copy.deepcopy(self.service.tracker.sections["grades"])
        timestamp = self.service.tracker.updated_at["grades"]
        client.errors["grades"] = ConnectorError("Synthetic section failure")
        client.rows["announcements"][0]["details"] += " changed"
        self.service.refresh()
        self.assertEqual(self.service.tracker.sections["grades"], before)
        self.assertEqual(self.service.tracker.updated_at["grades"], timestamp)
        self.assertEqual(self.service.last_changes["announcements"], 1)
        self.assertIn("grades", self.service.errors)

    def test_rate_limit_blocks_refresh_login_and_body_and_survives_restore(self):
        client = self.connect(True)
        client.calls.clear()
        client.errors["grades"] = ConnectorError("Rate limit", rate_limited=True)
        self.service.refresh()
        self.assertEqual(client.calls, ["grades"])
        self.service.refresh()
        self.service.connect("other-synthetic-parent", "test-only", False)
        message_id = self.service.tracker.sections["messages"][0]["id"]
        self.assertIn("error", json.loads(self.service.read_message(message_id)))
        self.assertEqual(client.body_reads, 0)
        self.assertEqual(len(FakeConnector.created), 1)
        restored = MobileService(factory=FakeConnector, clock=lambda: self.now)
        restored.restore_json(self.service.export_json())
        restored.open()
        self.assertEqual(len(FakeConnector.created), 1)

    def test_message_auth_failure_does_not_break_other_sections_or_loop(self):
        client = self.connect()
        client.errors["messages"] = ConnectorError("Expired inbox", requires_login=True)
        self.service.refresh()
        client.calls.clear()
        self.service.refresh()
        self.assertNotIn("messages", client.calls)
        self.assertEqual(len(client.calls), 5)
        self.assertFalse(client.closed)

    def test_main_session_expiry_preserves_cache_and_requires_login(self):
        client = self.connect()
        client.errors["grades"] = ConnectorError("Expired session", requires_login=True)
        self.service.refresh()
        self.assertTrue(client.closed)
        self.assertEqual(self.service.mode, "offline")
        self.assertTrue(self.service.state()["needs_login"])
        self.assertTrue(self.service.tracker.sections["grades"])

    def test_body_is_explicit_and_never_exported(self):
        client = self.connect()
        self.assertEqual(client.body_reads, 0)
        item = self.service.tracker.sections["messages"][0]
        self.assertTrue(item["unread"])
        result = json.loads(self.service.read_message(item["id"]))
        self.assertIn("SYNTHETIC_FULL_BODY", result["text"])
        self.assertFalse(item["unread"])
        self.assertNotIn(result["text"], self.service.export_json())
        self.service.refresh()
        self.assertEqual(sum(self.service.last_changes.values()), 0)

    def test_demo_does_not_export_or_mix_with_real_account(self):
        self.connect(True)
        profile = self.service.profile
        self.service.demo()
        self.assertEqual(self.service.export_json(), "")
        self.service.connect("synthetic-parent", "test-only-password", True)
        self.assertEqual(self.service.profile, profile)
        self.assertEqual(sum(self.service.last_changes.values()), 0)
        self.service.connect("other-synthetic-parent", "test-only", False)
        self.assertNotEqual(self.service.profile, profile)
        self.assertEqual(sum(self.service.last_changes.values()), 0)

    def test_revocation_survives_failed_login(self):
        self.connect(True)
        self.service.revoke_remembered()
        FakeConnector.fail_login = ConnectorError("Synthetic denial")
        self.service.connect("synthetic-parent", "test-only-wrong", False)
        self.assertIsNone(json.loads(self.service.export_json())["credentials"])
        self.assertIn("connection", self.service.errors)

    def test_failed_first_login_does_not_claim_an_offline_copy(self):
        FakeConnector.fail_login = ConnectorError("Synthetic denial")
        self.connect()
        self.assertEqual(self.service.mode, "idle")
        self.assertFalse(self.service.tracker.initialized)
        self.assertIn("connection", self.service.errors)

    def test_forget_clears_profile_cache_and_credentials(self):
        client = self.connect(True)
        self.service.forget()
        self.assertTrue(client.closed)
        self.assertFalse(self.service.tracker.initialized)
        self.assertFalse(self.service.profile)
        self.assertIsNone(self.service.credentials)
        self.assertEqual(self.service.mode, "idle")

    def test_invalid_restore_does_not_replace_valid_state(self):
        self.connect()
        before = self.service.export_json()
        for invalid in ('[]', '{"version": 99}', '{"version":1,"profile":12}',
                        '{"version":1,"credentials":{"login":"synthetic","password":"test-only"}}'):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    self.service.restore_json(invalid)
                self.assertEqual(self.service.export_json(), before)


if __name__ == "__main__":
    unittest.main()
