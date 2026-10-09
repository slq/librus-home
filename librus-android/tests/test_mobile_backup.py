"""Portable backups use invented data and explicitly prohibit school network requests."""
import copy
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"app/src/main/python"))
from mobile_bridge import MobileService
from mobile_backup import validate
from librus_shared.data import demo_sections

class Fake:
    def __init__(self,*args):self.closed=False
    def connect(self):pass
    def close(self):self.closed=True
    def fetch(self,kind):return copy.deepcopy(demo_sections(include_homework=True)[kind])

class BackupTests(unittest.TestCase):
    def setUp(self):
        blocker=patch("requests.sessions.Session.send",side_effect=AssertionError("Network forbidden"));blocker.start();self.addCleanup(blocker.stop)
        self.svc=MobileService(Fake);self.svc.connect("synthetic-backup","SYNTHETIC_PASSWORD_NEVER_EXPORT",True)
    def backup(self):return json.loads(self.svc.portable_json())
    def test_portable_copy_has_no_credentials_and_survives_new_service_and_login(self):
        raw=self.svc.portable_json();self.assertNotIn("SYNTHETIC_PASSWORD_NEVER_EXPORT",raw);self.assertNotIn("credentials",raw)
        restored=MobileService(Fake);restored.import_portable_json(raw)
        self.assertIsNone(restored.client);self.assertIsNone(restored.credentials);self.assertEqual("offline",restored.mode)
        self.assertEqual(self.svc.announcement_archives.export(),restored.announcement_archives.export())
        restored.connect("synthetic-backup","test-only",True,False)
        self.assertEqual(self.svc.tracker.sections,restored.tracker.sections)
    def test_import_closes_old_session_and_clears_demo_saved_credentials(self):
        data=self.svc.portable_json();client=self.svc.client;self.svc.import_portable_json(data)
        self.assertTrue(client.closed);self.assertFalse(self.svc.state()["remembered"]);self.assertIsNone(self.svc.client)
        self.svc.connect("synthetic-backup","test-only",True,False);self.svc.demo();self.assertIsNotNone(self.svc.live_saved)
        self.svc.import_portable_json(data);self.assertIsNone(self.svc.live_saved);self.assertIsNone(self.svc.credentials)
    def test_bad_nested_data_cannot_change_current_session(self):
        original=self.svc.export_json();client=self.svc.client
        for mutation in (lambda b:b.update(credentials={"login":"secret","password":"secret"}),
                         lambda b:b["snapshot"]["sections"]["grades"][0].update(password="secret"),
                         lambda b:b["snapshot"]["sections"].update(homework=[{"id":"x","title":12}]),
                         lambda b:b["snapshot"].update(initialized="grades"),
                         lambda b:b["announcement_archives"]["profiles"][b["profile"]].update(items="bad"),
                         lambda b:b.update(retry_after=float("nan"))):
            data=self.backup();mutation(data)
            with self.assertRaises((ValueError,TypeError)):self.svc.import_portable_json(json.dumps(data))
            self.assertEqual(original,self.svc.export_json());self.assertIs(client,self.svc.client)
    def test_archived_announcements_from_previous_accounts_are_retained(self):
        self.svc.connect("synthetic-other","test-only",True,False);data=self.backup()
        self.assertGreaterEqual(len(data["announcement_archives"]["profiles"]),1)
        self.assertEqual(data["announcement_archives"],validate(json.dumps(data))["announcement_archives"])
    def test_unknown_version_incomplete_snapshot_and_duplicate_ids_are_rejected(self):
        for mutation in (lambda b:b.update(version=2),lambda b:b["snapshot"]["sections"].pop("homework"),lambda b:b["snapshot"]["sections"]["grades"].append(copy.deepcopy(b["snapshot"]["sections"]["grades"][0]))):
            data=self.backup();mutation(data)
            with self.assertRaises(ValueError):validate(json.dumps(data))
    def test_nested_change_flags_cannot_smuggle_objects_into_backup(self):
        self.svc.journal.record("grades",self.svc.tracker.sections["grades"][0],"new",self.svc.clock())
        self.svc.journal.begin_visit(self.svc.clock())
        data=self.backup();data["change_journal"]["current"][0]["item"]["unread"]={"password":"test-only"}
        with self.assertRaises(ValueError):validate(json.dumps(data))
    def test_demo_is_not_exported(self):
        self.svc.demo()
        with self.assertRaises(ValueError):self.svc.portable_json()

if __name__=="__main__":unittest.main()
