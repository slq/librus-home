"""Bounded, persisted school-entry changes between app visits. No I/O or network."""
from __future__ import annotations
import copy
import math
from datetime import date
from librus_shared.data import coverage_for

KINDS = ("grades", "messages", "announcements", "schedule", "attendance", "timetable", "homework")
EVENTS = ("new", "updated", "removed", "restored")
MAX_ROWS = 300

class ChangeJournal:
    def __init__(self, year, saved=None):
        self.year = year
        self.active = False
        self.sequence = 0
        self.last_visit = None
        self.since = None
        self.current = {}
        self.pending = {}
        self.current_limited = self.pending_limited = False
        if saved is not None:
            self._restore(saved)

    def _restore(self, saved):
        if not isinstance(saved, dict) or saved.get("version") != 1:
            raise ValueError("Invalid changes journal")
        if saved.get("year") != self.year:
            return
        for name in ("last_visit", "since"):
            value = saved.get(name)
            if value is not None and (isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value) or value < 0):
                raise ValueError("Invalid visit date")
        seq = saved.get("sequence", 0)
        if isinstance(seq, bool) or not isinstance(seq, int) or seq < 0:
            raise ValueError("Invalid journal sequence")
        for name in ("current", "pending"):
            rows = saved.get(name, [])
            if not isinstance(rows, list) or len(rows) > MAX_ROWS:
                raise ValueError("Invalid changes list")
            result = {}
            for row in rows:
                if not isinstance(row, dict) or row.get("kind") not in KINDS or row.get("event") not in EVENTS:
                    raise ValueError("Invalid change event")
                item = row.get("item")
                if not isinstance(item, dict) or not all(isinstance(item.get(k), str) for k in ("id", "title", "subtitle", "when", "details")) or not item["id"] or not item["title"]:
                    raise ValueError("Invalid change metadata")
                number, at = row.get("sequence"), row.get("detected_at")
                if isinstance(number, bool) or not isinstance(number, int) or not 0 < number <= seq or isinstance(at, bool) or not isinstance(at, (int, float)) or not math.isfinite(at) or at < 0:
                    raise ValueError("Invalid change date")
                key = (row["kind"], item["id"])
                if key in result:
                    raise ValueError("Duplicate change")
                result[key] = copy.deepcopy(row)
            setattr(self, name, result)
        for name in ("current_limited", "pending_limited"):
            value = saved.get(name, False)
            if not isinstance(value, bool):
                raise ValueError("Invalid journal limit flag")
            setattr(self, name, value)
        self.sequence = seq
        self.last_visit, self.since = saved.get("last_visit"), saved.get("since")

    def begin_visit(self, now):
        self.since = self.last_visit
        self.last_visit = now
        self.current = copy.deepcopy(self.pending)
        self.current_limited = self.pending_limited
        self.pending = {}
        self.pending_limited = False
        self.active = True

    def end_visit(self):
        self.active = False

    def _put(self, target, row):
        key = (row["kind"], row["item"]["id"])
        old = target.get(key)
        row = copy.deepcopy(row)
        if old:
            if old["event"] == "new" and row["event"] == "updated":
                row["event"] = "new"
            elif old["event"] == "removed" and row["event"] == "new":
                row["event"] = "restored"
        target[key] = row
        if len(target) > MAX_ROWS:
            oldest = min(target, key=lambda k: target[k]["sequence"])
            del target[oldest]
            if target is self.pending:
                self.pending_limited = True
            else:
                self.current_limited = True

    def record(self, kind, item, event, now):
        self.sequence += 1
        metadata = {k: str(item.get(k) or "") for k in ("id", "title", "subtitle", "when", "details")}
        metadata["details"] = metadata["details"][:4000]
        metadata["kind"] = kind
        row = {"kind": kind, "event": event, "item": metadata,
               "detected_at": now, "sequence": self.sequence}
        self._put(self.pending, row)
        if self.active:
            self._put(self.current, row)

    def apply(self, tracker, kind, rows, now, today=None):
        today = today or date.today()
        baseline = kind not in tracker.initialized
        old_known = dict(tracker.known[kind])
        old_items = tracker.sections[kind]
        previous_coverage = set(tracker.coverage.get(kind, set()))
        count = tracker.apply(kind, rows, today=today)
        if baseline:
            return count
        for item in tracker.sections[kind]:
            if item["changed"]:
                self.record(kind, item, "updated" if item["id"] in old_known else "new", now)
        current_coverage = coverage_for(kind, today)
        if current_coverage is not None:
            ids = {item["id"] for item in tracker.sections[kind]}
            for item in old_items:
                day = item["when"][:10]
                if item["id"] not in ids and day >= today.isoformat() and day in current_coverage and day in previous_coverage:
                    self.record(kind, item, "removed", now)
        return count

    def state(self):
        return {"since": self.since, "last_visit": self.last_visit,
                "items": copy.deepcopy(sorted(self.current.values(), key=lambda x: x["sequence"], reverse=True)),
                "limited": self.current_limited}

    def export(self):
        return {"version": 1, "year": self.year, "sequence": self.sequence,
                "last_visit": self.last_visit, "since": self.since,
                "current": copy.deepcopy(list(self.current.values())),
                "pending": copy.deepcopy(list(self.pending.values())),
                "current_limited": self.current_limited, "pending_limited": self.pending_limited}
