"""Fictional announcements only; no school requests. Durable upsert, migration and account isolation."""
import copy
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"app/src/main/python"))
from mobile_bridge import MobileService
from mobile_announcements import AnnouncementArchives
from librus_shared.connector import ConnectorError
from test_mobile_bridge import FakeConnector


class AnnouncementArchiveTests(unittest.TestCase):
    def setUp(self):
        FakeConnector.created=[];FakeConnector.fail_login=None
        block=patch("requests.sessions.Session.send",side_effect=AssertionError("Network forbidden"))
        block.start();self.addCleanup(block.stop)
        self.svc=MobileService(FakeConnector,lambda:2000000000)
        self.svc.connect("synthetic-archive-parent","test-only")
        self.client=FakeConnector.created[-1]
    def rows(self):return self.svc.state()["sections"]["announcements"]
    def test_absent_and_empty_lists_keep_every_downloaded_announcement_without_notifications(self):
        old=copy.deepcopy(self.rows());self.svc.begin_visit()
        self.client.rows["announcements"]=old[:1];self.svc.refresh()
        self.assertEqual(len(old),len(self.rows()));self.assertEqual(1,sum(x["archived"] for x in self.rows()))
        self.assertEqual(0,self.svc.last_changes["announcements"])
        self.client.rows["announcements"]=[];self.svc.refresh();self.svc.refresh()
        self.assertEqual({x["id"] for x in old},{x["id"] for x in self.rows()})
        self.assertTrue(all(x["archived"] for x in self.rows()))
        self.assertFalse(self.svc.state()["change_feed"]["items"])
        restored=MobileService(FakeConnector);restored.restore_json(self.svc.export_json())
        self.assertEqual(self.rows(),restored.state()["sections"]["announcements"])
        self.assertEqual("offline",restored.mode)
    def test_edit_replaces_same_id_and_reappearance_does_not_create_duplicate_changes(self):
        first=self.rows()[0];ident=first["id"];old_body=first["details"]
        self.svc.begin_visit();changed=copy.deepcopy(first);changed["details"]="SYNTHETIC_LATEST_CONTENT";changed["title"]="SYNTHETIC_UPDATED_TITLE"
        # A truly stable ID also handles title edits; the current HTML adapter's identity limitation is documented.
        self.client.rows["announcements"]=[changed];self.svc.refresh()
        current=[x for x in self.rows() if x["id"]==ident]
        self.assertEqual(1,len(current));self.assertEqual(changed["details"],current[0]["details"])
        self.assertEqual(1,self.svc.last_changes["announcements"])
        event=self.svc.state()["change_feed"]["items"][0]
        self.assertEqual("updated",event["event"]);self.assertEqual(changed["details"],event["item"]["details"])
        self.assertNotIn(old_body,self.svc.export_json())
        self.client.rows["announcements"]=[];self.svc.refresh()
        self.client.rows["announcements"]=[changed];self.svc.refresh()
        self.assertEqual(0,self.svc.last_changes["announcements"])
        self.assertFalse(next(x for x in self.rows() if x["id"]==ident)["archived"])
    def test_first_empty_baseline_then_new_announcements_still_notifies_once(self):
        empty=MobileService(FakeConnector);empty.connect("empty-synthetic","test-only",fetch=False)
        client=FakeConnector.created[-1];client.rows["announcements"]=[];empty.refresh()
        self.assertEqual(0,empty.last_changes["announcements"])
        empty.begin_visit();new=dict(id="synthetic:new",title="SYNTHETIC_NEW",details="new body")
        client.rows["announcements"]=[new];empty.refresh()
        self.assertEqual(1,empty.last_changes["announcements"])
        client.rows["announcements"]=[];empty.refresh();client.rows["announcements"]=[new];empty.refresh()
        self.assertEqual(0,empty.last_changes["announcements"])
        self.assertEqual(1,len(empty.state()["sections"]["announcements"]))
    def test_legacy_copy_migrates_even_after_year_rollover_without_restoring_old_grades(self):
        saved=json.loads(self.svc.export_json());saved.pop("announcement_archives")
        saved["snapshot"]["year"]="2000/2001";saved["change_journal"]["year"]="2000/2001"
        restored=MobileService(FakeConnector);restored.restore_json(json.dumps(saved))
        self.assertEqual(2,len(restored.state()["sections"]["announcements"]))
        self.assertEqual([],restored.state()["sections"]["grades"])
        self.assertEqual([],restored.state()["change_feed"]["items"])
        self.assertEqual(saved["snapshot"]["updated_at"]["announcements"],restored.state()["updated_at"]["announcements"])
        next_saved=json.loads(restored.export_json());next_saved["snapshot"]["year"]="2001/2002"
        next_service=MobileService(FakeConnector);next_service.restore_json(json.dumps(next_saved))
        self.assertEqual(restored.state()["sections"]["announcements"],next_service.state()["sections"]["announcements"])
    def test_account_switch_return_demo_failure_and_forget_are_isolated(self):
        a=self.svc.profile;self.client.rows["announcements"]=[];self.svc.refresh();a_rows=self.rows()
        self.svc.connect("other-synthetic-archive","test-only",fetch=False)
        b=self.svc.profile;self.assertNotEqual(a,b);self.assertEqual([],self.rows())
        other=FakeConnector.created[-1];other.rows["announcements"]=[dict(id="same-id",title="SYNTHETIC_OTHER_ACCOUNT")];self.svc.refresh()
        self.assertEqual("SYNTHETIC_OTHER_ACCOUNT",self.rows()[0]["title"])
        self.svc.connect("synthetic-archive-parent","test-only",fetch=False)
        self.assertEqual(a_rows,self.rows());self.assertNotIn("SYNTHETIC_OTHER_ACCOUNT",self.svc.state_json())
        self.assertEqual({a,b},set(json.loads(self.svc.export_json())["announcement_archives"]["profiles"]))
        self.svc.demo();self.assertEqual("",self.svc.export_json());self.assertFalse(any(x.get("archived") for x in self.rows()))
        self.svc.connect("synthetic-archive-parent","test-only",fetch=False);self.assertEqual(a_rows,self.rows())
        self.svc.demo();FakeConnector.fail_login=ConnectorError("Synthetic rejected login")
        self.svc.connect("other-synthetic-archive","test-only");self.assertEqual(a_rows,self.rows())
        self.svc.forget();self.assertEqual({},json.loads(self.svc.export_json())["announcement_archives"]["profiles"])
        self.assertEqual([],self.rows())
    def test_malformed_response_and_invalid_restore_do_not_overwrite_previous_archive(self):
        previous=copy.deepcopy(self.rows());archive=self.svc.announcement_archives.export();updated=self.svc.state()["updated_at"]["announcements"]
        self.client.rows["announcements"]=[dict(id="x",title="one"),dict(id="x",title="conflicting")];self.svc.refresh()
        self.assertEqual(previous,self.rows());self.assertEqual(archive,self.svc.announcement_archives.export())
        self.assertEqual(updated,self.svc.state()["updated_at"]["announcements"]);self.assertIn("announcements",self.svc.errors)
        self.client.errors["announcements"]=ConnectorError("Synthetic section failure");self.svc.refresh();self.assertEqual(previous,self.rows())
        saved=json.loads(self.svc.export_json());key=self.svc.profile
        corruptions=[None,[],dict(version=2,profiles={}),dict(version=1,profiles={key:dict(items=[],listed_ids=["missing"],updated_at="")}),dict(version=1,profiles={key:dict(items=[None],listed_ids=[],updated_at="")})]
        for bad in corruptions:
            invalid=copy.deepcopy(saved);invalid["announcement_archives"]=bad
            before=self.svc.state_json()
            with self.assertRaises(ValueError):self.svc.restore_json(json.dumps(invalid))
            self.assertEqual(before,self.svc.state_json());self.assertEqual(archive,self.svc.announcement_archives.export())
    def test_archive_does_not_trim_old_records_or_full_descriptions_to_journal_limit(self):
        rows=[dict(id=str(i),title="SYNTHETIC_ARCHIVED_"+str(i),details="FULL_DESCRIPTION_"+"x"*5000) for i in range(501)]
        self.client.rows["announcements"]=rows;self.svc.refresh();count=len(self.rows())
        self.client.rows["announcements"]=[];self.svc.refresh()
        self.assertEqual(503,count);self.assertEqual(count,len(self.rows()))
        restored=MobileService(FakeConnector);restored.restore_json(self.svc.export_json())
        archived=next(x for x in restored.state()["sections"]["announcements"] if x["id"]=="0")
        self.assertEqual(rows[0]["details"],archived["details"])
        state=self.svc.state();state["sections"]["announcements"][0]["title"]="view-only"
        exported=self.svc.announcement_archives.export();exported["profiles"].clear()
        self.assertEqual(count,len(self.rows()));self.assertNotIn("view-only",self.svc.state_json())

if __name__=="__main__":unittest.main()
