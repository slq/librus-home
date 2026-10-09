"""Only fictional data; no school requests. Per-visit feed and persistence regressions."""
import copy
import json
import sys
import unittest
from pathlib import Path
from datetime import date
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app/src/main/python"))
from mobile_changes import ChangeJournal, MAX_ROWS
from mobile_bridge import MobileService, KINDS
from librus_shared.data import SnapshotTracker
from librus_shared.connector import ConnectorError
from test_mobile_bridge import FakeConnector

class ChangeTests(unittest.TestCase):
    def setUp(self):
        FakeConnector.created = []
        FakeConnector.fail_login = None
        self.now = 2000000000
        self.service = MobileService(FakeConnector, lambda: self.now)
        block = patch("requests.sessions.Session.send", side_effect=AssertionError("Network forbidden"))
        block.start(); self.addCleanup(block.stop)
        self.service.connect("synthetic-feed", "test-only")
        self.client = FakeConnector.created[-1]

    def add(self, kind, ident=None):
        item = copy.deepcopy(self.client.rows[kind][0])
        item.update(id=ident or "new:" + kind, title="SYNTHETIC_NEW_" + kind, when=date.today().isoformat())
        self.client.rows[kind].append(item)
        return item

    def test_initial_baselines_quiet_and_background_changes_survive_repeated_reads_and_restart(self):
        self.assertFalse(self.service.journal.pending)
        self.service.begin_visit(); self.service.end_visit()
        for kind in KINDS: self.add(kind)
        self.service.refresh(); self.service.refresh()
        self.assertEqual(7, len(self.service.journal.pending))
        self.assertFalse(self.service.state()["change_feed"]["items"])
        saved = self.service.export_json()
        restored = MobileService(FakeConnector, lambda: self.now + 10)
        restored.restore_json(saved); restored.begin_visit()
        feed = restored.state()["change_feed"]
        self.assertEqual(set(KINDS), {e["kind"] for e in feed["items"]})
        self.assertEqual({"new"}, {e["event"] for e in feed["items"]})
        self.assertEqual(self.now, feed["since"])
        self.assertIsNone(restored.client)
        restored.end_visit(); restored.begin_visit()
        self.assertFalse(restored.state()["change_feed"]["items"])

    def test_active_reads_accumulate_coalesce_and_are_available_at_the_next_entry(self):
        self.service.begin_visit()
        item = self.add("grades")
        self.service.refresh()
        self.now += 5; item["title"] = "SYNTHETIC_EDITED"
        self.service.refresh(); self.service.refresh()
        feed = self.service.state()["change_feed"]
        self.assertEqual(1, len(feed["items"]))
        self.assertEqual("new", feed["items"][0]["event"])
        self.assertEqual("SYNTHETIC_EDITED", feed["items"][0]["item"]["title"])
        feed["items"][0]["item"]["title"] = "VIEW_ONLY_MUTATION"
        self.assertNotIn("VIEW_ONLY", self.service.state_json())
        self.service.end_visit(); self.service.begin_visit()
        self.assertEqual(1, len(self.service.state()["change_feed"]["items"]))
        self.service.end_visit(); self.service.begin_visit()
        self.assertFalse(self.service.state()["change_feed"]["items"])

    def test_existing_edits_and_partial_errors_keep_previously_collected_changes(self):
        self.service.begin_visit(); self.service.end_visit()
        self.client.rows["attendance"][0]["title"] = "SYNTHETIC_ATTENDANCE_UPDATE"
        self.service.refresh()
        self.client.errors["attendance"] = ConnectorError("Synthetic unavailable")
        self.add("messages"); self.service.refresh()
        self.service.begin_visit()
        feed = self.service.state()["change_feed"]["items"]
        self.assertEqual({"attendance", "messages"}, {e["kind"] for e in feed})
        self.assertEqual("updated", next(e for e in feed if e["kind"] == "attendance")["event"])
        self.assertEqual("new", next(e for e in feed if e["kind"] == "messages")["event"])

    def test_first_success_after_a_section_error_is_baseline_not_all_new(self):
        self.service.tracker.initialized.remove("homework")
        self.service.tracker.known["homework"] = {}
        self.service.tracker.sections["homework"] = []
        self.service.begin_visit(); self.service.refresh()
        self.assertFalse(self.service.state()["change_feed"]["items"])

    def test_removals_restorations_and_sliding_ranges_do_not_invent_changes(self):
        t = SnapshotTracker(extra_kinds=("homework",)); j = ChangeJournal(t.year)
        item = dict(id="event", title="SYNTHETIC_EVENT", when="2026-10-09")
        j.apply(t, "schedule", [item], 1, date(2026,10,8)); j.begin_visit(2)
        self.assertEqual(1, j.apply(t, "schedule", [], 3, date(2026,10,8)))
        self.assertEqual("removed", j.state()["items"][0]["event"])
        j.apply(t, "schedule", [item], 4, date(2026,10,8))
        self.assertEqual("restored", j.state()["items"][0]["event"])
        t = SnapshotTracker(extra_kinds=("homework",)); j = ChangeJournal(t.year)
        old = dict(id="old", title="SYNTHETIC_OLD", when="2026-10-31")
        newly_covered = dict(id="window", title="SYNTHETIC_WINDOW", when="2026-12-02")
        j.apply(t, "schedule", [old], 1, date(2026,10,31)); j.begin_visit(2)
        self.assertEqual(0, j.apply(t,"schedule",[newly_covered],3,date(2026,11,1)))
        self.assertFalse(j.state()["items"])

    def test_reading_bodies_unread_flags_and_local_ui_state_do_not_create_feed_events(self):
        self.service.begin_visit(); self.service.end_visit()
        self.service.read_message(self.client.rows["messages"][0]["id"])
        self.service.read_homework(self.client.rows["homework"][0]["id"])
        self.service.refresh()
        self.assertFalse(self.service.journal.pending)
        self.assertNotIn("SYNTHETIC_FULL_BODY_NOT_FOR_DISK", self.service.export_json())
        self.assertNotIn("SYNTHETIC_HOMEWORK_BODY_NOT_FOR_DISK", self.service.export_json())

    def test_migration_account_demo_and_year_isolation_and_forget(self):
        old = json.loads(self.service.export_json()); old.pop("change_journal")
        upgraded = MobileService(FakeConnector); upgraded.restore_json(json.dumps(old))
        self.assertFalse(upgraded.journal.pending)
        self.add("grades"); self.service.refresh(); original_ids = {e["item"]["id"] for e in self.service.journal.pending.values()}
        self.service.demo(); self.assertEqual(7,len(self.service.state()["change_feed"]["items"]))
        self.assertEqual("",self.service.export_json())
        self.service.connect("synthetic-feed","test-only",fetch=False)
        self.assertEqual(original_ids,{e["item"]["id"] for e in self.service.journal.pending.values()})
        self.service.connect("different-synthetic-profile","test-only")
        self.assertFalse(self.service.journal.pending)
        self.add("grades"); self.service.refresh(); saved=json.loads(self.service.export_json())
        saved["change_journal"]["year"]="1900/1901"
        upgraded.restore_json(json.dumps(saved));self.assertFalse(upgraded.journal.pending)
        self.service.forget();self.assertFalse(self.service.journal.pending);self.assertIsNone(self.service.journal.last_visit)

    def test_login_after_forget_inside_the_open_app_keeps_live_changes_visible(self):
        self.service.begin_visit()
        self.service.forget()
        self.assertIsNone(self.service.journal.last_visit)
        self.service.connect("synthetic-feed", "test-only")
        self.client = FakeConnector.created[-1]
        self.add("grades"); self.service.refresh()
        self.assertEqual(1, len(self.service.state()["change_feed"]["items"]))

    def test_invalid_journal_restore_is_atomic_and_size_limit_is_explicit(self):
        before=self.service.export_json();saved=json.loads(before)
        saved["change_journal"]["sequence"]=-1
        with self.assertRaises(ValueError):self.service.restore_json(json.dumps(saved))
        self.assertEqual(before,self.service.export_json())
        j=ChangeJournal(self.service.tracker.year);j.begin_visit(1)
        for i in range(MAX_ROWS+10):j.record("grades",dict(id=str(i),title="SYNTHETIC",details="x"*5000),"new",i+2)
        self.assertEqual(MAX_ROWS,len(j.state()["items"]));self.assertTrue(j.state()["limited"])
        self.assertEqual(4000,len(j.state()["items"][0]["item"]["details"]))
        restored=ChangeJournal(j.year,j.export());restored.begin_visit(500)
        self.assertEqual(MAX_ROWS,len(restored.state()["items"]));self.assertTrue(restored.state()["limited"])
