"""Snapshots and change detection. No network or credentials in this module."""

from __future__ import annotations

import copy
import hashlib
import json
from datetime import date, datetime, timedelta
from typing import Any


KINDS = ("grades", "messages", "announcements", "schedule", "attendance", "timetable")
LABELS = {
    "grades": "Oceny", "messages": "Wiadomości", "announcements": "Ogłoszenia", "schedule": "Terminarz",
    "attendance": "Frekwencja", "timetable": "Plan lekcji", "homework": "Zadania domowe",
}
SCOPE_HINTS = {
    "grades": "Oceny udostępnione przez szkołę w bieżącym roku szkolnym.",
    "messages": "Do 200 najnowszych wiadomości. Licznik dotyczy pobranej listy.",
    "announcements": "Ogłoszenia udostępnione przez szkołę w Librusie, wraz z treścią.",
    "schedule": "Bieżący i następny miesiąc.",
    "attendance": "Wpisy frekwencji udostępnione przez szkołę; to nie procent obecności.",
    "timetable": "Bieżący i następny tydzień.",
    "homework": "Zadania udostępnione przez Librusa dla bieżącego roku szkolnego.",
}


def stamp() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def school_year(today: date | None = None) -> str:
    today = today or date.today()
    start = today.year if today.month >= 9 else today.year - 1
    return f"{start}/{start + 1}"


def coverage_for(kind: str, today: date | None = None) -> set[str] | None:
    """Days covered by our sliding-window fetches, including empty days."""
    today = today or date.today()
    if kind == "timetable":
        start = today - timedelta(days=today.weekday())
        return {(start + timedelta(days=i)).isoformat() for i in range(14)}
    if kind == "schedule":
        start = today.replace(day=1)
        next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
        end = (next_month.replace(day=28) + timedelta(days=4)).replace(day=1)
        return {(start + timedelta(days=i)).isoformat() for i in range((end - start).days)}
    return None


def fingerprint(item: dict[str, Any]) -> str:
    """Server read-state and UI flags are deliberately not notification events."""
    values = {k: item.get(k, "") for k in ("title", "subtitle", "when", "details")}
    value = json.dumps(values, ensure_ascii=False, sort_keys=True).encode("utf-8")
    return hashlib.sha256(value).hexdigest()


def normalize_items(kind: str, items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if kind not in (*KINDS, "homework") or not isinstance(items, list):
        raise ValueError("Nieprawidłowy format listy danych.")
    result: dict[str, dict[str, Any]] = {}
    for original in items:
        if not isinstance(original, dict) or not original.get("id") or not original.get("title"):
            raise ValueError("Wpis nie zawiera identyfikatora lub tytułu.")
        item = {key: str(original.get(key) or "") for key in
                ("id", "title", "subtitle", "when", "details", "url")}
        item.update(kind=kind, unread=bool(original.get("unread", False)), changed=False)
        if item["id"] in result and fingerprint(result[item["id"]]) != fingerprint(item):
            raise ValueError("Odczyt zawiera sprzeczne wpisy o tym samym identyfikatorze.")
        result[item["id"]] = item
    return sorted(result.values(), key=lambda x: (x["when"], x["title"], x["id"]),
                  reverse=kind not in {"schedule", "timetable", "homework"})


class SnapshotTracker:
    """Keep each section independent: a failed section never erases its data.

    A ledger outlives the visible window, so temporarily disappearing rows do not
    trigger duplicate notifications when they return. New horizon days establish
    a quiet baseline. Future timetable/schedule removals within known coverage
    are also detected. Absence from a paginated mailbox is not a deletion.
    """

    def __init__(self, saved: dict[str, Any] | None = None, *, extra_kinds: tuple[str, ...] = ()):
        if any(k != "homework" for k in extra_kinds):
            raise ValueError("Nieobsługiwana dodatkowa sekcja.")
        self.kinds = tuple(dict.fromkeys((*KINDS, *extra_kinds)))
        self.sections: dict[str, list[dict[str, Any]]] = {k: [] for k in self.kinds}
        self.updated_at: dict[str, str] = {}
        self.known: dict[str, dict[str, str]] = {k: {} for k in self.kinds}
        self.coverage: dict[str, set[str]] = {}
        self.initialized: set[str] = set()
        self.year = school_year()
        if saved:
            self._restore(saved)

    def _restore(self, saved: dict[str, Any]) -> None:
        if not isinstance(saved, dict):
            raise ValueError("Nieprawidłowy format zapisanej kopii.")
        if saved.get("year") != self.year:
            return  # new school year: fresh baseline instead of old grades
        sections = saved.get("sections", {})
        updated = saved.get("updated_at", {})
        known = saved.get("known", {})
        if not all(isinstance(v, dict) for v in (sections, updated, known)):
            raise ValueError("Nieprawidłowy format zapisanej kopii.")
        for kind in self.kinds:
            if kind in sections:
                self.sections[kind] = normalize_items(kind, sections[kind])
            if kind in updated:
                self.updated_at[kind] = str(updated[kind])
            if kind in known:
                if not isinstance(known[kind], dict):
                    raise ValueError("Nieprawidłowy format historii zmian.")
                self.known[kind] = {str(k): str(v) for k, v in known[kind].items()}
        self.initialized = set(saved.get("initialized", [])).intersection(self.kinds)
        self.coverage = {k: set(v) for k, v in saved.get("coverage", {}).items()
                         if k in self.kinds and isinstance(v, list)}

    def apply(self, kind: str, items: list[dict[str, Any]], fetched_at: str | None = None,
              today: date | None = None) -> int:
        cleaned = normalize_items(kind, items)  # validate before any mutation
        today = today or date.today()
        current_coverage = coverage_for(kind, today)
        previous_coverage = self.coverage.get(kind, set())
        old_items = {i["id"]: i for i in self.sections[kind]}
        old_ledger = self.known[kind]
        baseline = kind not in self.initialized
        changes = 0
        for item in cleaned:
            old = old_ledger.get(item["id"])
            different = old != fingerprint(item)
            covered_before = current_coverage is None or item["when"][:10] in previous_coverage
            relevant = kind not in {"schedule", "timetable"} or item["when"][:10] >= today.isoformat()
            item["changed"] = bool(not baseline and different and covered_before and relevant)
            changes += int(item["changed"])
        if not baseline and current_coverage is not None:
            incoming_ids = {i["id"] for i in cleaned}
            for item_id, item in old_items.items():
                day = item["when"][:10]
                if (item_id not in incoming_ids and day >= today.isoformat()
                        and day in current_coverage and day in previous_coverage):
                    changes += 1
                    # A later restoration is a real change, not a duplicate.
                    old_ledger.pop(item_id, None)
        for item in cleaned:
            old_ledger[item["id"]] = fingerprint(item)
        # Bound disk usage while retaining much more than the visible mailbox.
        if len(old_ledger) > 20000:
            self.known[kind] = dict(list(old_ledger.items())[-20000:])
        self.sections[kind] = cleaned
        self.updated_at[kind] = fetched_at or stamp()
        self.initialized.add(kind)
        if current_coverage is not None:
            self.coverage[kind] = current_coverage
        return changes

    def export(self) -> dict[str, Any]:
        return copy.deepcopy({
            "year": self.year,
            "sections": self.sections,
            "updated_at": self.updated_at,
            "known": self.known,
            "initialized": sorted(self.initialized),
            "coverage": {k: sorted(v) for k, v in self.coverage.items()},
        })


def demo_sections(today: date | None = None, *, include_homework: bool = False) -> dict[str, list[dict[str, Any]]]:
    """Invented examples only. No child name, school, or real account."""
    today = today or date.today()
    d = lambda offset: (today + timedelta(days=offset)).isoformat()

    def item(kind, ident, title, subtitle, when, details="", unread=False):
        return {"id": f"demo:{kind}:{ident}", "kind": kind, "title": title,
                "subtitle": subtitle, "when": when, "details": details,
                "unread": unread, "url": ""}

    sections = {
        "grades": [
            item("grades", "g1", "Matematyka · 5", "Sprawdzian: ułamki", d(0),
                 "Ocena: 5\nPrzedmiot: matematyka\nKategoria: sprawdzian\nWaga: 2\n\nDane demonstracyjne."),
            item("grades", "g2", "Język angielski · 4+", "Odpowiedź ustna", d(-1),
                 "Ocena: 4+\nKategoria: odpowiedź ustna\n\nDane demonstracyjne."),
            item("grades", "g3", "Historia · 5", "Praca na lekcji", d(-3)),
            item("grades", "g4", "Język polski · 4", "Wypracowanie", d(-5)),
        ],
        "messages": [
            item("messages", "m1", "Zebranie rodziców w przyszłym tygodniu", "Wychowawca — przykład", d(0) + " 09:20",
                 "Podgląd: Zapraszam na spotkanie rodziców. Szczegóły i godzina w treści wiadomości.\n\nDane demonstracyjne.", True),
            item("messages", "m2", "Wyjście klasy do muzeum", "Sekretariat — przykład", d(-1) + " 14:10",
                 "Podgląd: Informacja organizacyjna o planowanym wyjściu klasy.\n\nDane demonstracyjne.", True),
            item("messages", "m3", "Materiały do powtórzenia", "Nauczyciel — przykład", d(-3) + " 16:45", "Dane demonstracyjne."),
        ],
        "announcements": [
            item("announcements", "n1", "Dzień otwarty szkoły", "Dyrekcja — przykład", d(0),
                 "Zapraszamy rodziców na dzień otwarty szkoły. Szczegółowy program zostanie podany przez wychowawcę.\n\nDane demonstracyjne."),
            item("announcements", "n2", "Zmiana godzin pracy biblioteki", "Biblioteka — przykład", d(-2),
                 "W tym tygodniu biblioteka szkolna jest czynna od 8:00 do 14:00.\n\nDane demonstracyjne."),
        ],
        "schedule": [
            item("schedule", "s1", "Matematyka · sprawdzian", "Ułamki zwykłe i dziesiętne", d(1) + " 08:00", "Powtórka działu o ułamkach.\n\nDane demonstracyjne."),
            item("schedule", "s2", "Język angielski · kartkówka", "Słownictwo z rozdziału 2", d(3) + " 09:50"),
            item("schedule", "s3", "Zebranie rodziców", "Spotkanie z wychowawcą", d(6) + " 17:00"),
        ],
        "attendance": [
            item("attendance", "a1", "Spóźnienie", "Język polski · lekcja 1", d(-2), "Przykładowy wpis frekwencji."),
            item("attendance", "a2", "Nieobecność usprawiedliwiona", "Matematyka · lekcja 2", d(-7)),
        ],
        "timetable": [
            item("timetable", "t1", "Matematyka", "08:00–08:45 · sala 12", d(0) + " 08:00", "Dane demonstracyjne."),
            item("timetable", "t2", "Język polski", "08:55–09:40 · sala 6", d(0) + " 08:55"),
            item("timetable", "t3", "Język angielski", "09:50–10:35 · sala 18", d(0) + " 09:50"),
            item("timetable", "t4", "Biologia", "10:50–11:35 · sala 21", d(0) + " 10:50"),
            item("timetable", "t5", "Historia", "08:00–08:45 · sala 7", d(1) + " 08:00"),
        ],
    }
    if include_homework:
        sections["homework"] = [
            item("homework", "h1", "Ćwiczenia z ułamków", "Matematyka · Nauczyciel — przykład", d(1) + "T08:00:00",
                 "Przedmiot: Matematyka\nKategoria: ćwiczenia\nDodano: " + d(-1) + "\nTermin wykonania: " + d(1) + " 08:00\nPełną treść pobierzesz osobnym przyciskiem.\nDane demonstracyjne."),
            item("homework", "h2", "Opis ulubionej książki", "Język polski · Nauczyciel — przykład", d(3),
                 "Kategoria: wypracowanie\nTermin wykonania: " + d(3) + "\nDane demonstracyjne."),
            item("homework", "h3", "Powtórka słownictwa", "Język angielski · Nauczyciel — przykład", d(-1),
                 "Termin wykonania: " + d(-1) + "\nDane demonstracyjne."),
        ]
    return sections
