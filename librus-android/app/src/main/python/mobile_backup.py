"""Portable school copy: allowlisted data only, never credentials, sessions or I/O."""
from __future__ import annotations
import copy
import json
import math
import re
from librus_shared.data import normalize_items
from mobile_announcements import AnnouncementArchives
from mobile_changes import ChangeJournal, KINDS

FIELDS = {"version", "profile", "snapshot", "announcement_archives", "change_journal", "retry_after"}
ITEM_FIELDS = {"id", "kind", "title", "subtitle", "when", "details", "url", "unread", "changed"}

def exact(value, fields):
    if not isinstance(value, dict) or set(value) - fields:
        raise ValueError("Invalid backup object")

def rows(kind, items):
    if not isinstance(items, list) or len(items) > 50000:
        raise ValueError("Invalid backup rows")
    ids = set()
    for item in items:
        exact(item, ITEM_FIELDS)
        for field in ("id", "title", "subtitle", "when", "details", "url"):
            if not isinstance(item.get(field, ""), str) or len(item.get(field, "")) > 1000000:
                raise ValueError("Invalid backup text")
        if not item.get("id") or len(item["id"]) > 512 or not item.get("title") or item["id"] in ids:
            raise ValueError("Invalid backup identity")
        ids.add(item["id"])
        for field in ("unread", "changed"):
            if field in item and not isinstance(item[field], bool):
                raise ValueError("Invalid backup flag")
        if item.get("kind", kind) != kind:
            raise ValueError("Invalid backup kind")
    return normalize_items(kind, items)

def validate(raw):
    value = json.loads(raw)
    exact(value, FIELDS)
    if type(value.get("version")) is not int or value["version"] != 1:
        raise ValueError("Invalid backup version")
    profile = value.get("profile")
    if not isinstance(profile, str) or (profile and not re.fullmatch(r"[a-f0-9]{64}", profile)):
        raise ValueError("Invalid backup profile")
    snapshot = value.get("snapshot")
    exact(snapshot, {"year", "sections", "updated_at", "known", "initialized", "coverage"})
    year = snapshot.get("year")
    if not isinstance(year, str) or not re.fullmatch(r"[0-9]{4}/[0-9]{4}", year) or int(year[5:]) != int(year[:4]) + 1:
        raise ValueError("Invalid school year")
    sections, updated, known, coverage = (snapshot.get(k) for k in ("sections", "updated_at", "known", "coverage"))
    for group in (sections, updated, known, coverage):
        exact(group, set(KINDS))
    if set(sections) != set(KINDS) or set(known) != set(KINDS):
        raise ValueError("Incomplete backup sections")
    snapshot = copy.deepcopy(snapshot)
    for kind in KINDS:
        snapshot["sections"][kind] = rows(kind, sections[kind])
        ledger = known[kind]
        if not isinstance(ledger, dict) or len(ledger) > 50000 or any(not isinstance(k, str) or not k or len(k)>512 or not isinstance(v,str) or not re.fullmatch(r"[a-f0-9]{64}",v) for k,v in ledger.items()):
            raise ValueError("Invalid backup ledger")
    if any(not isinstance(v,str) or len(v)>100 for v in updated.values()):
        raise ValueError("Invalid backup timestamp")
    initialized = snapshot.get("initialized")
    if not isinstance(initialized, list) or len(set(initialized)) != len(initialized) or any(k not in KINDS for k in initialized):
        raise ValueError("Invalid backup baseline")
    for days in coverage.values():
        if not isinstance(days,list) or len(days)>100 or any(not isinstance(d,str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}",d) for d in days):
            raise ValueError("Invalid backup coverage")
    archives = value.get("announcement_archives")
    exact(archives, {"version", "profiles"})
    if archives.get("version") != 1 or not isinstance(archives.get("profiles"),dict) or len(archives["profiles"])>1000:
        raise ValueError("Invalid backup archives")
    for account, record in archives["profiles"].items():
        if not re.fullmatch(r"[a-f0-9]{64}",account):
            raise ValueError("Invalid archive profile")
        exact(record,{"items","listed_ids","updated_at"})
        rows("announcements", record.get("items"))
    journal = value.get("change_journal")
    exact(journal, {"version","year","sequence","last_visit","since","current","pending","current_limited","pending_limited"})
    if journal.get("year") != year:
        raise ValueError("Invalid journal year")
    for name in ("current", "pending"):
        if not isinstance(journal.get(name),list):
            raise ValueError("Invalid backup changes")
        for row in journal[name]:
            exact(row,{"kind","event","item","detected_at","sequence"})
            exact(row.get("item"), ITEM_FIELDS)
            rows(row.get("kind"), [row["item"]])
    ChangeJournal(year,journal)
    retry=value.get("retry_after",0)
    if isinstance(retry,bool) or not isinstance(retry,(int,float)) or not math.isfinite(retry) or not 0<=retry<=32503680000:
        raise ValueError("Invalid backup cooldown")
    return dict(version=1,profile=profile,snapshot=snapshot,
                announcement_archives=AnnouncementArchives.from_state(value).export(),
                change_journal=copy.deepcopy(journal),retry_after=retry)

def export(raw):
    saved=json.loads(raw)
    # Projection deliberately excludes credentials and automatic-login state.
    return json.dumps(validate(json.dumps({k:v for k,v in saved.items() if k in FIELDS})),ensure_ascii=False)
