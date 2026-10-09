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
        self.rows = demo_sections(include_homework=True)
        self.errors = {}
        self.calls = []
        self.closed = False
        self.body_reads = 0
        self.homework_reads = 0
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

    def read_homework(self, item_id):
        self.homework_reads += 1
        if "homework_body" in self.errors:
            raise self.errors["homework_body"]
        return {"text": "SYNTHETIC_HOMEWORK_BODY_NOT_FOR_DISK"}


class MobileTests(unittest.TestCase):
    def test_state_exposes_snapshot_year_for_account_scoped_local_statuses(self):
        self.service.demo()
        self.assertEqual(self.service.tracker.year, self.service.state()["year"])
        self.assertEqual(self.service.tracker.year, json.loads(self.service.state_json())["year"])

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
        self.assertEqual(len(client.calls), 7)
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
        self.assertEqual(len(client.calls), 6)
        self.assertFalse(client.closed)

    def test_main_session_expiry_preserves_cache_and_requires_login(self):
        client = self.connect()
        client.errors["grades"] = ConnectorError("Expired session", requires_login=True)
        self.service.refresh()
        self.assertTrue(client.closed)
        self.assertEqual(self.service.mode, "offline")
        self.assertTrue(self.service.state()["needs_login"])
        self.assertTrue(self.service.tracker.sections["grades"])

    def test_remembered_expiry_can_reconnect_on_next_attempt_without_replaying_changes(self):
        client = self.connect(remember=True)
        snapshot = copy.deepcopy(self.service.tracker.sections)
        client.errors["grades"] = ConnectorError("Synthetic expired session", requires_login=True)
        self.service.open()
        self.assertTrue(self.service.state()["needs_login"])
        self.assertTrue(self.service.state()["remembered"])
        self.assertFalse(self.service.auto_login_blocked)
        self.assertEqual(self.service.tracker.sections, snapshot)
        self.assertEqual(len(FakeConnector.created), 1)
        self.service.open()
        self.assertTrue(self.service.state()["connected"])
        self.assertEqual(len(FakeConnector.created), 2)
        self.assertEqual(sum(self.service.last_changes.values()), 0)

    def test_newly_authenticated_session_failure_stops_reconnection_loop(self):
        client = self.connect(remember=True)
        client.errors["grades"] = ConnectorError("Synthetic expired session", requires_login=True)
        self.service.open()
        def rejected_session(login, password):
            candidate = FakeConnector(login, password)
            candidate.errors["grades"] = ConnectorError("Synthetic denied fresh session", requires_login=True)
            return candidate
        self.service.factory = rejected_session
        self.service.open()
        self.assertEqual(len(FakeConnector.created), 2)
        self.assertTrue(self.service.auto_login_blocked)
        for _ in range(3):
            self.service.open()
        restored = MobileService(factory=rejected_session, clock=lambda: self.now)
        restored.restore_json(self.service.export_json())
        restored.open()
        self.assertEqual(len(FakeConnector.created), 2)

    def test_pending_session_recovery_obeys_rate_limit(self):
        client = self.connect(remember=True)
        client.errors["grades"] = ConnectorError("Synthetic expired session", requires_login=True)
        self.service.open()
        self.service.retry_after = self.now + 3600
        self.service.open()
        self.assertEqual(len(FakeConnector.created), 1)
        self.assertFalse(self.service.auto_login_blocked)
        self.now += 3600
        self.service.open()
        self.assertTrue(self.service.state()["connected"])
        self.assertEqual(len(FakeConnector.created), 2)

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

    def test_change_counts_are_not_reused_after_rate_limit_or_reopening(self):
        client = self.connect(remember=True)
        client.rows["messages"][0]["title"] += " synthetic change"
        self.service.refresh()
        self.assertEqual(self.service.last_changes["messages"], 1)
        self.service.retry_after = self.now + 3600
        self.service.open()
        self.assertEqual(self.service.last_changes, {})
        self.service.retry_after = 0
        self.service.open()
        self.assertEqual(sum(self.service.last_changes.values()), 0)

    def test_explicit_login_can_wait_without_fetching_data_during_cooldown(self):
        self.service.connect("synthetic-parent", "test-only-password", True, False)
        self.assertEqual(FakeConnector.created[-1].calls, [])
        self.assertTrue(self.service.state()["connected"])
        self.assertEqual(self.service.last_changes, {})
        self.service.open()
        self.assertEqual(len(FakeConnector.created[-1].calls), 7)
        self.assertEqual(sum(self.service.last_changes.values()), 0)

    def test_failed_automatic_login_is_not_repeated_by_focus_or_after_restart(self):
        client = self.connect(remember=True)
        client.close()
        self.service.client = None
        FakeConnector.fail_login = ConnectorError("Synthetic denial", requires_login=True)
        self.service.open()
        attempts = len(FakeConnector.created)
        self.assertTrue(self.service.auto_login_blocked)
        self.service.open()
        self.assertEqual(len(FakeConnector.created), attempts)
        restored = MobileService(factory=FakeConnector, clock=lambda: self.now)
        restored.restore_json(self.service.export_json())
        restored.open()
        self.assertEqual(len(FakeConnector.created), attempts)
        FakeConnector.fail_login = None
        restored.connect("synthetic-parent", "test-only-password", True)
        self.assertFalse(restored.auto_login_blocked)
        self.assertTrue(restored.state()["connected"])

    def test_homework_sync_is_quiet_then_detects_edit_and_keeps_previous_data_on_error(self):
        client = self.connect()
        self.assertIn("homework", client.calls)
        self.assertEqual(client.homework_reads, 0)
        self.assertEqual(self.service.last_changes["homework"], 0)
        client.rows["homework"][0]["when"] = "2033-08-01T09:00:00"
        client.rows["homework"][0]["title"] += " edited"
        self.service.refresh()
        self.assertEqual(self.service.last_changes["homework"], 1)
        before = copy.deepcopy(self.service.tracker.sections["homework"])
        timestamp = self.service.tracker.updated_at["homework"]
        client.errors["homework"] = ConnectorError("Synthetic unavailable module")
        self.service.refresh()
        self.assertEqual(self.service.tracker.sections["homework"], before)
        self.assertEqual(self.service.tracker.updated_at["homework"], timestamp)
        self.assertIn("homework", self.service.errors)
        self.assertTrue(self.service.state()["connected"])
        self.assertIn("6/7", self.service.status)

    def test_unavailable_homework_module_does_not_break_other_sections_or_repeat_access_attempt(self):
        client = self.connect()
        client.errors["homework"] = ConnectorError("Synthetic module denied", requires_login=True)
        self.service.refresh()
        self.assertFalse(client.closed)
        self.assertIn("homework", self.service.blocked)
        client.calls.clear()
        self.service.refresh()
        self.assertNotIn("homework", client.calls)
        self.assertEqual(len(client.calls), 6)
        self.assertIn("messages", client.calls)
        self.service.connect("synthetic-parent", "test-only-password", False)
        self.assertNotIn("homework", self.service.blocked)
        self.assertIn("homework", FakeConnector.created[-1].calls)

    def test_homework_body_is_explicit_and_not_persisted_or_a_change(self):
        client = self.connect()
        item = self.service.tracker.sections["homework"][0]
        body = json.loads(self.service.read_homework(item["id"]))
        self.assertEqual(body["id"], item["id"])
        self.assertEqual(client.homework_reads, 1)
        self.assertNotIn(body["text"], self.service.export_json())
        self.service.refresh()
        self.assertEqual(self.service.last_changes["homework"], 0)
        self.assertIn("error", json.loads(self.service.read_homework("homework:unknown")))
        self.assertEqual(client.homework_reads, 1)

    def test_old_six_section_snapshot_restores_and_first_homework_fetch_is_quiet(self):
        client = self.connect(remember=True)
        saved = json.loads(self.service.export_json())
        snapshot = saved["snapshot"]
        for field in ("sections", "known", "updated_at", "coverage"):
            snapshot[field].pop("homework", None)
        snapshot["initialized"].remove("homework")
        restored = MobileService(factory=FakeConnector, clock=lambda: self.now)
        restored.restore_json(json.dumps(saved))
        self.assertEqual(restored.tracker.sections["homework"], [])
        self.assertEqual(restored.tracker.sections["grades"], self.service.tracker.sections["grades"])
        restored.open()
        self.assertTrue(restored.tracker.sections["homework"])
        self.assertEqual(restored.last_changes["homework"], 0)
        # Desktop keeps its six sections and does not silently start polling homework.
        from librus_shared.data import KINDS, SnapshotTracker
        self.assertEqual(len(KINDS), 6)
        self.assertNotIn("homework", SnapshotTracker().sections)

    def test_homework_detail_rate_limit_and_session_expiry_preserve_list(self):
        client = self.connect(remember=True)
        item_id = self.service.tracker.sections["homework"][0]["id"]
        before = copy.deepcopy(self.service.tracker.sections["homework"])
        client.errors["homework_body"] = ConnectorError("Synthetic rate limit", rate_limited=True)
        self.assertIn("error", json.loads(self.service.read_homework(item_id)))
        self.assertEqual(self.service.retry_after, self.now + 3600)
        self.service.read_homework(item_id)
        self.assertEqual(client.homework_reads, 1)
        restored = MobileService(factory=FakeConnector, clock=lambda: self.now)
        restored.restore_json(self.service.export_json())
        self.assertEqual(restored.retry_after, self.now + 3600)
        self.service.retry_after = 0
        client.errors["homework_body"] = ConnectorError("Synthetic expired session", requires_login=True)
        self.service.read_homework(item_id)
        self.assertFalse(client.closed)
        self.assertIn("homework", self.service.blocked)
        self.assertEqual(self.service.mode, "live")
        self.assertEqual(self.service.tracker.sections["homework"], before)

    def test_homework_offline_details_and_demo_do_not_contact_school(self):
        self.connect()
        saved = self.service.export_json()
        restored = MobileService(factory=FakeConnector)
        restored.restore_json(saved)
        item_id = restored.tracker.sections["homework"][0]["id"]
        self.assertIn("error", json.loads(restored.read_homework(item_id)))
        self.assertEqual(len(FakeConnector.created), 1)
        restored.demo()
        demo_id = restored.tracker.sections["homework"][0]["id"]
        self.assertIn("Przykładowa treść zadania", json.loads(restored.read_homework(demo_id))["text"])
        self.assertEqual(len(FakeConnector.created), 1)
        self.assertEqual(restored.export_json(), "")


if __name__ == "__main__":
    unittest.main()
