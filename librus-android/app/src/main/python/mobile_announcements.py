"""Account-scoped announcement archives. No expiry, network, files or fuzzy identity matching."""
from __future__ import annotations
import copy
from librus_shared.data import normalize_items, fingerprint


class AnnouncementArchives:
    def __init__(self, saved=None):
        self.profiles = {}
        if saved is not None:
            if not isinstance(saved, dict) or saved.get("version") != 1 or not isinstance(saved.get("profiles"), dict):
                raise ValueError("Invalid announcement archives")
            for profile, record in saved["profiles"].items():
                if not isinstance(profile, str) or not profile:
                    raise ValueError("Invalid announcement profile")
                if not isinstance(record, dict) or not isinstance(record.get("items"), list):
                    raise ValueError("Invalid announcement record")
                originals = record["items"]
                for item in originals:
                    if (not isinstance(item, dict) or not isinstance(item.get("id"), str) or not item["id"]
                        or not isinstance(item.get("title"), str) or not item["title"]
                        or any(not isinstance(item.get(k, ""), str) for k in ("subtitle", "when", "details", "url"))):
                        raise ValueError("Invalid saved announcement")
                cleaned = normalize_items("announcements", originals)
                if len(cleaned) != len(originals):
                    raise ValueError("Duplicate archived announcement")
                listed = record.get("listed_ids")
                ids = {item["id"] for item in cleaned}
                if (not isinstance(listed, list) or any(not isinstance(k, str) or k not in ids for k in listed)
                    or len(set(listed)) != len(listed) or not isinstance(record.get("updated_at"), str)):
                    raise ValueError("Invalid announcement read metadata")
                self.profiles[profile] = dict(items=cleaned, listed_ids=list(listed), updated_at=record["updated_at"])

    @classmethod
    def from_state(cls, value):
        if "announcement_archives" in value and not isinstance(value["announcement_archives"], dict):
            raise ValueError("Invalid announcement archives")
        archive = cls(value.get("announcement_archives"))
        profile, snapshot = value.get("profile", ""), value.get("snapshot")
        if profile and profile not in archive.profiles and snapshot:
            # Read the original snapshot even when SnapshotTracker has discarded an old school year.
            if not isinstance(snapshot, dict) or not isinstance(snapshot.get("sections", {}), dict):
                raise ValueError("Invalid saved announcement snapshot")
            rows = snapshot.get("sections", {}).get("announcements", [])
            cleaned = normalize_items("announcements", rows)
            if cleaned or "announcements" in snapshot.get("initialized", []):
                updated = snapshot.get("updated_at", {})
                if not isinstance(updated, dict):
                    raise ValueError("Invalid announcement read metadata")
                archive.profiles[profile] = dict(items=cleaned, listed_ids=[item["id"] for item in cleaned],
                                                updated_at=str(updated.get("announcements", "")))
        return archive

    def prepare(self, profile, incoming):
        if not isinstance(profile, str) or not profile:
            raise ValueError("Missing announcement profile")
        cleaned = normalize_items("announcements", incoming)  # Validate the whole response before any mutation.
        previous = self.profiles.get(profile, {}).get("items", [])
        merged = {item["id"]: item for item in previous}
        merged.update({item["id"]: item for item in cleaned})
        return dict(items=normalize_items("announcements", list(merged.values())),
                    listed_ids=[item["id"] for item in cleaned], updated_at="")

    def commit(self, profile, record, tracker):
        self.profiles[profile] = copy.deepcopy(record)
        # The shared tracker bounds its ledger; the archive must remember every retained ID.
        tracker.known["announcements"] = {item["id"]: fingerprint(item) for item in record["items"]}

    def restore_into(self, profile, tracker):
        record = self.profiles.get(profile)
        if record is None:
            return
        tracker.sections["announcements"] = copy.deepcopy(record["items"])
        tracker.known["announcements"] = {item["id"]: fingerprint(item) for item in record["items"]}
        tracker.initialized.add("announcements")
        tracker.updated_at["announcements"] = record["updated_at"]

    def decorate(self, profile, rows):
        record = self.profiles.get(profile)
        if record is not None:
            listed = set(record["listed_ids"])
            for item in rows:
                item["archived"] = item["id"] not in listed

    def export(self):
        return copy.deepcopy(dict(version=1, profiles=self.profiles))
