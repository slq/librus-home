"""Android service: all calls are serialized by the Java repository worker.

No disk, UI, telemetry or notifications in this module. Automatic connection
uses opt-in saved credentials at most once per scheduled attempt; a rejected
login or rejected newly authenticated session stops further automatic attempts.
Java provides encrypted Android Keystore storage and lifecycle callbacks.
"""
from __future__ import annotations
import copy
import hashlib
import json
import time
from librus_shared.connector import LibrusConnector, ConnectorError
from librus_shared.data import KINDS as DESKTOP_KINDS, LABELS, SnapshotTracker, demo_sections

KINDS = (*DESKTOP_KINDS, "homework")


class MobileService:
    def __init__(self, factory=LibrusConnector, clock=time.time):
        self.factory, self.clock = factory, clock
        self.client = None
        self.tracker = SnapshotTracker(extra_kinds=("homework",))
        self.profile = ""
        self.credentials = None
        self.mode = "idle"
        self.errors = {}
        self.status = "Połącz konto Synergia lub obejrzyj demo."
        self.retry_after = 0.0
        self.blocked = set()
        self.progress = None
        self.last_changes = {}
        self.auto_login_blocked = False
        self.live_saved = None

    def state(self):
        return {"mode": self.mode, "status": self.status,
                "sections": copy.deepcopy(self.tracker.sections),
                "updated_at": dict(self.tracker.updated_at), "errors": dict(self.errors),
                "connected": self.client is not None, "remembered": bool(self.credentials),
                "needs_login": self.client is None and self.mode != "demo",
                "retry_after": self.retry_after, "changes": dict(self.last_changes), "profile": self.profile, "auto_login_blocked": self.auto_login_blocked}

    def state_json(self):
        return json.dumps(self.state(), ensure_ascii=False)

    def set_progress(self, progress):
        self.progress = progress

    def publish(self):
        if self.progress is not None:
            self.progress.update(self.state_json())

    def export_json(self):
        if self.mode == "demo":
            return ""
        return json.dumps({"version": 1, "profile": self.profile,
                           "credentials": self.credentials, "snapshot": self.tracker.export(),
                           "retry_after": self.retry_after, "auto_login_blocked": self.auto_login_blocked}, ensure_ascii=False)

    def restore_json(self, raw):
        value = json.loads(raw) if raw else {}
        if not isinstance(value, dict) or (value and value.get("version") != 1):
            raise ValueError("Invalid saved state")
        tracker = SnapshotTracker(value.get("snapshot"), extra_kinds=("homework",))
        profile = value.get("profile", "")
        credentials = value.get("credentials")
        if not isinstance(profile, str):
            raise ValueError("Invalid profile")
        if credentials is not None:
            if (not isinstance(credentials, dict)
                or not all(isinstance(credentials.get(k), str) and credentials[k] for k in ("login", "password"))
                or self.profile_id(credentials["login"]) != profile):
                raise ValueError("Invalid credentials")
        blocked = value.get("auto_login_blocked", False)
        if not isinstance(blocked, bool):
            raise ValueError("Invalid automatic login state")
        retry = value.get("retry_after", 0)
        if not isinstance(retry, (int, float)) or not 0 <= retry < float("inf"):
            raise ValueError("Invalid retry date")
        self.tracker, self.profile, self.credentials = tracker, profile, credentials
        self.auto_login_blocked = blocked
        self.retry_after = max(self.retry_after, retry)
        self.mode = "offline" if tracker.initialized else "idle"
        self.status = "Zapisana kopia. Połącz konto, aby pobrać aktualne dane." if tracker.initialized else "Połącz konto Synergia lub obejrzyj demo."
        return self.state_json()

    @staticmethod
    def profile_id(login):
        return hashlib.sha256(login.strip().encode()).hexdigest()

    def close_client(self):
        if self.client is not None:
            self.client.close()
        self.client = None

    def revoke_remembered(self):
        if self.mode == "demo":
            # Revoke the original account's saved password too, before login.
            if self.live_saved:
                self.live_saved["credentials"] = None
        self.credentials = None
        return self.state_json()

    def _allowed(self):
        if self.clock() < self.retry_after:
            self.status = "Librus ograniczył zapytania. Spróbuj po upływie wskazanej przerwy."
            return False
        return True

    def connect(self, login, password, remember=False, fetch=True):
        self.last_changes = {}
        self.auto_login_blocked = False
        login = login.strip()
        if not login or not password:
            self.errors["connection"] = "Wpisz login Synergia i hasło."
            return self.state_json()
        if not self._allowed():
            return self.state_json()
        previous = self.live_saved if self.mode == "demo" else json.loads(self.export_json())
        self.close_client()
        self.mode = "offline" if self.tracker.initialized and self.mode != "demo" else "idle"
        self.errors.clear()
        if not remember:
            self.credentials = None
        candidate = self.factory(login, password)
        try:
            candidate.connect()
        except ConnectorError as exc:
            candidate.close()
            self.errors["connection"] = str(exc)
            self.status = "Nie udało się połączyć konta."
            if exc.rate_limited:
                self.retry_after = self.clock() + 3600
            # Never retain a demo snapshot as a real account's offline data.
            if previous:
                self.restore_json(json.dumps(previous))
                self.status = "Nie udało się połączyć konta."
            return self.state_json()
        except Exception:
            candidate.close()
            self.errors["connection"] = "Nieoczekiwana odpowiedź logowania. Sprawdź konto w oficjalnym Librusie."
            self.status = "Nie udało się połączyć konta."
            if previous:
                self.restore_json(json.dumps(previous))
                self.status = "Nie udało się połączyć konta."
            return self.state_json()
        target_profile = self.profile_id(login)
        self.tracker = SnapshotTracker(previous.get("snapshot"), extra_kinds=("homework",)) if previous and previous.get("profile") == target_profile else SnapshotTracker(extra_kinds=("homework",))
        self.profile = target_profile
        self.credentials = {"login": login, "password": password} if remember else None
        self.client, self.mode = candidate, "live"
        self.blocked.clear()
        self.live_saved = None
        if not fetch:
            self.status = "Konto połączone. Kolejny odczyt danych po zakończeniu przerwy 5 minut."
            return self.state_json()
        return self.refresh(after_login=True)

    def open(self):
        self.last_changes = {}
        if self.mode == "demo":
            return self.state_json()
        if self.client:
            return self.refresh()
        if self.auto_login_blocked:
            self.status = "Automatyczne logowanie wstrzymane. Połącz konto ponownie przyciskiem Konto."
            return self.state_json()
        if self.credentials:
            credentials = dict(self.credentials)
            result = self.connect(credentials["login"], credentials["password"], True)
            if self.client is None and self.clock() >= self.retry_after:
                self.auto_login_blocked = True
                self.status = "Automatyczne logowanie wstrzymane. Połącz konto ponownie przyciskiem Konto."
                return self.state_json()
            return result
        return self.state_json()

    def refresh(self, *, after_login=False):
        self.last_changes = {}
        if self.mode == "demo":
            return self.state_json()
        if not self._allowed():
            return self.state_json()
        if not self.client:
            self.status = "Połącz konto, aby odświeżyć dane."
            return self.state_json()
        successes, changes = 0, {}
        for kind in ("grades", "schedule", "attendance", "timetable", "announcements", "homework", "messages"):
            if kind in self.blocked:
                continue
            self.status = "Pobieram: " + LABELS[kind].lower() + "…"
            self.publish()
            try:
                rows = self.client.fetch(kind)
                changes[kind] = self.tracker.apply(kind, rows)
                self.errors.pop(kind, None)
                successes += 1
            except ConnectorError as exc:
                self.errors[kind] = str(exc)
                if exc.rate_limited:
                    self.retry_after = self.clock() + 3600
                    break
                if exc.requires_login:
                    if kind in {"messages", "homework"}:
                        # A module may be unavailable for this account while the
                        # core school session and other sections still work.
                        self.blocked.add(kind)
                    else:
                        self.close_client()
                        # An existing session may expire while credentials remain valid.
                        # Retry only on the next scheduled attempt (Java enforces 5 min).
                        # A failed fresh session stops recovery to avoid a login loop.
                        self.auto_login_blocked = after_login or not bool(self.credentials)
                        break
            except Exception:
                self.errors[kind] = "Nie rozpoznano danych sekcji. Zachowano poprzedni odczyt."
            self.publish()
        self.last_changes = changes
        self.mode = "live" if self.client and successes else "offline" if self.tracker.initialized else "idle"
        if self.retry_after > self.clock():
            self.status = "Librus ograniczył zapytania. Pobieranie wstrzymane na co najmniej godzinę."
        elif not self.client:
            self.status = ("Sesja wygasła. Zapamiętane konto zostanie połączone przy następnej próbie pobrania; pokazuję ostatnią kopię."
                           if self.credentials and not self.auto_login_blocked else
                           "Sesja wygasła. Połącz konto ponownie; zachowano dostępne dane.")
        elif successes == len(KINDS):
            self.status = "Dane aktualne. " + (f"Zmiany: {sum(changes.values())}." if sum(changes.values()) else "Brak nowych zmian.")
        elif successes:
            self.status = f"Odświeżono {successes}/{len(KINDS)} sekcji. Sprawdź komunikaty pozostałych."
        else:
            self.status = "Nie pobrano nowych danych. " + ("Pokazuję ostatnią kopię." if self.tracker.initialized else "Sprawdź błędy sekcji.")
        return self.state_json()

    def read_message(self, item_id):
        item = next((row for row in self.tracker.sections["messages"] if row["id"] == item_id), None)
        if item is None:
            return json.dumps({"error": "Wiadomość nie znajduje się w pobranej liście."})
        if self.mode == "demo":
            return json.dumps({"title": item["title"], "text": "Przykładowa treść wiadomości. Demo nie łączy się z kontem szkoły."})
        if not self._allowed():
            return json.dumps({"error": self.status})
        if not self.client:
            return json.dumps({"error": "Połącz konto, aby pobrać treść."})
        try:
            body = self.client.read_message(item_id)
            item["unread"] = False
            self.errors.pop("messages", None)
            return json.dumps(body, ensure_ascii=False)
        except ConnectorError as exc:
            self.errors["messages"] = str(exc)
            if exc.rate_limited:
                self.retry_after = self.clock() + 3600
            return json.dumps({"error": str(exc)})
        except Exception:
            return json.dumps({"error": "Nie udało się pobrać treści wiadomości."})

    def read_homework(self, item_id):
        item = next((row for row in self.tracker.sections["homework"] if row["id"] == item_id), None)
        if item is None:
            return json.dumps({"error": "Zadanie nie znajduje się w pobranej liście."})
        if self.mode == "demo":
            return json.dumps({"id": item_id, "text": "Przykładowa treść zadania: rozwiąż ćwiczenia i przygotuj odpowiedź na kolejną lekcję. Demo nie łączy się z Librusem."}, ensure_ascii=False)
        if not self._allowed():
            return json.dumps({"error": self.status})
        if not self.client:
            return json.dumps({"error": "Połącz konto, aby pobrać treść zadania."})
        try:
            body = self.client.read_homework(item_id)
            self.errors.pop("homework", None)
            return json.dumps({"id": item_id, "text": body["text"]}, ensure_ascii=False)
        except ConnectorError as exc:
            self.errors["homework"] = str(exc)
            if exc.rate_limited:
                self.retry_after = self.clock() + 3600
            if exc.requires_login:
                self.blocked.add("homework")
            return json.dumps({"error": str(exc)})
        except Exception:
            return json.dumps({"error": "Nie udało się pobrać treści zadania."})

    def demo(self):
        if self.mode != "demo":
            self.live_saved = json.loads(self.export_json())
        self.close_client()
        self.credentials = None
        self.tracker = SnapshotTracker(extra_kinds=("homework",))
        for kind, rows in demo_sections(include_homework=True).items():
            self.tracker.apply(kind, rows)
        self.mode, self.errors = "demo", {}
        self.status = "Dane demonstracyjne — bez połączenia z Librusem."
        return self.state_json()

    def forget(self):
        self.close_client()
        self.tracker = SnapshotTracker(extra_kinds=("homework",))
        self.profile, self.credentials, self.live_saved = "", None, None
        self.auto_login_blocked = False
        self.mode, self.errors, self.blocked = "idle", {}, set()
        self.status = "Usunięto lokalne dane i konto."
        return self.state_json()


_service = MobileService()
def get_service():
    return _service
