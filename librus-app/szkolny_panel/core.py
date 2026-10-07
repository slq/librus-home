"""Application coordinator. Only the worker communicates with Librus."""

from __future__ import annotations

import copy
import hashlib
import queue
import threading
import time
import webbrowser
from datetime import datetime
from typing import Any, Callable

from .data import KINDS, LABELS, SCOPE_HINTS, SnapshotTracker, demo_sections, stamp
from .platform_windows import SecureStorage, StorageError, notify
from .reminders import REMINDER_KINDS, due_datetime, new_reminder, reminder_input, restore_reminders


def profile_id(login: str) -> str:
    return hashlib.sha256(login.strip().encode("utf-8")).hexdigest()


def display_time(seconds: float | None) -> str:
    return datetime.fromtimestamp(seconds).astimezone().strftime("%H:%M") if seconds else "—"


class Controller:
    def __init__(self, *, demo_on_start: bool = False, storage=None,
                 connector_factory: Callable | None = None, notifier: Callable | None = None,
                 start_ticker: bool = True):
        self.events: queue.Queue = queue.Queue()
        self._lock = threading.RLock()
        self._stop_event = threading.Event()
        self._storage = storage if storage is not None else SecureStorage()
        self._connector_factory = connector_factory
        self._notifier = notifier or notify
        self._demo_on_start = demo_on_start
        self._start_ticker = start_ticker
        self._restored = False
        self._worker: threading.Thread | None = None
        self._ticker: threading.Thread | None = None
        self._client = None
        self._tracker = SnapshotTracker()
        self._account = ""
        self._remembered: dict | None = None
        self._blocked_sections: set[str] = set()
        self._mode = "idle"
        self._busy = False
        self._status = "Połącz konto Synergia lub zobacz dane demonstracyjne."
        self._last_sync = "—"
        self._next_sync: float | None = None
        self._cooldown_until = 0.0
        self._rate_limited_until = 0.0
        self._interval = 15
        self._notifications = True
        self._errors: dict[str, str] = {}
        self._failure_count = 0
        self._demo_data: dict = {}
        self._demo_counter = 0
        self._reminders: list[dict] = []
        self._reminders_inflight: set[str] = set()

    def _state(self) -> dict[str, Any]:
        return {
            "mode": self._mode, "busy": self._busy, "status": self._status,
            "last_sync": self._last_sync, "next_sync": display_time(self._next_sync),
            "interval": self._interval, "notifications": self._notifications,
            "sections": copy.deepcopy(self._tracker.sections),
            "updated_at": dict(self._tracker.updated_at), "errors": dict(self._errors),
            "section_notes": SCOPE_HINTS, "persistent": self._storage.persistent,
            "reminders": copy.deepcopy(self._reminders),
        }

    def emit_state(self) -> None:
        with self._lock:
            self.events.put(("state", self._state()))

    def _notice(self, message: str) -> None:
        self.events.put(("notice", message))

    def _launch(self, operation: Callable, status: str) -> bool:
        with self._lock:
            if self._stop_event.is_set():
                return False
            if self._busy:
                self._notice("Poczekaj na zakończenie bieżącego odświeżania.")
                return False
            self._busy = True
            self._status = status
            self.emit_state()

            def worker():
                try:
                    operation()
                except Exception:
                    # Never display raw HTTP exceptions, cookie values or server bodies.
                    with self._lock:
                        self._status = "Nie udało się zakończyć operacji. Poprzednie dane zachowano."
                    self._notice("Wystąpił błąd aplikacji. Spróbuj ponownie; jeśli się powtórzy, przekaż opis kroku, na którym wystąpił.")
                finally:
                    with self._lock:
                        self._busy = False
                        self.emit_state()
                    if self._stop_event.is_set() and self._client is not None:
                        self._client.close()

            self._worker = threading.Thread(target=worker, name="LibrusSync", daemon=True)
            self._worker.start()
            return True

    def restore(self) -> None:
        with self._lock:
            if self._restored:
                return
            self._restored = True
            if self._start_ticker:
                self._ticker = threading.Thread(target=self._tick, name="PanelTimer", daemon=True)
                self._ticker.start()
        if self._demo_on_start:
            self.demo()
        else:
            self._launch(self._restore, "Sprawdzam zapisane ustawienia…")

    def _restore(self) -> None:
        try:
            saved = self._storage.load()
            if saved and saved.get("version") != 1:
                raise ValueError("Unsupported state version")
            settings = saved.get("settings", {})
            tracker = SnapshotTracker(saved.get("snapshot"))
            reminders = restore_reminders(saved.get("reminders", []))
            credentials = saved.get("credentials")
            with self._lock:
                self._tracker = tracker
                self._reminders = reminders
                self._account = str(saved.get("profile_id", ""))
                self._interval = int(settings.get("interval", 15))
                if self._interval not in {10, 15, 30, 60}:
                    self._interval = 15
                self._notifications = bool(settings.get("notifications", True))
                self._last_sync = str(saved.get("last_sync", "—"))
                self._mode = "offline" if tracker.initialized else "idle"
                self._status = ("Wyświetlam zapisaną kopię. Połącz konto, aby pobrać aktualne dane."
                                if tracker.initialized else "Połącz konto Synergia lub zobacz dane demonstracyjne.")
                self.emit_state()
            if (isinstance(credentials, dict) and isinstance(credentials.get("login"), str)
                    and isinstance(credentials.get("password"), str)
                    and credentials["login"] and credentials["password"]
                    and profile_id(credentials["login"]) == self._account):
                self._connect(credentials["login"], credentials["password"], True)
        except (StorageError, ValueError, TypeError, KeyError):
            with self._lock:
                self._status = "Nie można odczytać zapisanej kopii. Połącz konto ponownie."
                self._errors["storage"] = "Zapis jest uszkodzony lub należy do innego konta Windows."

    def _save(self) -> bool:
        with self._lock:
            if self._mode == "demo":
                return True
            if self._stop_event.is_set():
                return False
            data = {
                "version": 1, "profile_id": self._account,
                "credentials": copy.deepcopy(self._remembered),
                "settings": {"interval": self._interval, "notifications": self._notifications},
                "snapshot": self._tracker.export(), "last_sync": self._last_sync,
                "reminders": copy.deepcopy(self._reminders),
            }
            try:
                self._storage.save(data)
                self._errors.pop("storage", None)
                return True
            except StorageError:
                self._errors["storage"] = "Nie udało się zapisać danych na tym komputerze. Bieżący widok pozostaje dostępny."
                return False

    def connect(self, login: str, password: str, remember: bool = False) -> None:
        login = login.strip()
        if not login or not password:
            self._notice("Wpisz login Synergia i hasło otrzymane ze szkoły.")
            return
        if time.time() < self._rate_limited_until:
            self._notice("Librus ograniczył zapytania. Kolejna próba po " + display_time(self._rate_limited_until) + ".")
            return
        self._launch(lambda: self._connect(login, password, remember), "Loguję do konta Synergia…")

    def _connect(self, login: str, password: str, remember: bool) -> None:
        from .connector import LibrusConnector, ConnectorError

        if time.time() < self._rate_limited_until:
            self._status = "Połączenia wstrzymano do " + display_time(self._rate_limited_until) + "."
            return
        account = profile_id(login)
        with self._lock:
            if self._client is not None:
                self._client.close()
                self._client = None
            self._next_sync = None
            self._blocked_sections.clear()
            self._errors.clear()
            if account != self._account or self._mode == "demo":
                self._tracker = SnapshotTracker()
                self._reminders = []
                self._last_sync = "—"
                # Recover matching baseline when reconnecting after a demo preview.
                try:
                    saved = self._storage.load()
                    if saved.get("profile_id") == account and saved.get("version") == 1:
                        self._tracker = SnapshotTracker(saved.get("snapshot"))
                        self._reminders = restore_reminders(saved.get("reminders", []))
                        self._last_sync = str(saved.get("last_sync", "—"))
                except (StorageError, ValueError, TypeError):
                    pass
            self._account = account
            self._remembered = None
            self._mode = "offline" if self._tracker.initialized else "idle"
            self._status = "Loguję do konta Synergia…"
            self.emit_state()
        if not remember:
            # Unchecking the option revokes an older saved password even when
            # this new login attempt fails. Keep its last data snapshot intact.
            try:
                previous = self._storage.load()
                if previous.get("credentials") is not None:
                    previous["credentials"] = None
                    self._storage.save(previous)
            except StorageError:
                with self._lock:
                    self._errors["storage"] = "Nie udało się usunąć wcześniej zapamiętanego hasła z pliku. Użyj opcji usunięcia lokalnych danych."
        candidate = (self._connector_factory or LibrusConnector)(login, password)
        try:
            candidate.connect()
        except ConnectorError as exc:
            candidate.close()
            with self._lock:
                self._errors["connection"] = str(exc)
                self._status = "Nie udało się połączyć z kontem. Sprawdź komunikat poniżej."
                if getattr(exc, "rate_limited", False):
                    self._cooldown_until = time.time() + 3600
                    self._rate_limited_until = self._cooldown_until
            return
        except Exception:
            candidate.close()
            with self._lock:
                self._errors["connection"] = "Nieoczekiwana odpowiedź podczas logowania. Sprawdź logowanie w zwykłym Librusie."
                self._status = "Nie udało się połączyć z kontem."
            return
        with self._lock:
            if self._stop_event.is_set():
                candidate.close()
                return
            self._client = candidate
            self._mode = "live"
            self._failure_count = 0
            self._remembered = {"login": login, "password": password} if remember else None
            self._errors.pop("connection", None)
        self._save()
        self._sync()

    def refresh(self) -> None:
        with self._lock:
            if self._mode == "demo":
                self._launch(self._sync_demo, "Odświeżam dane demonstracyjne…")
                return
            if self._client is None:
                self._notice("Połącz konto Synergia, aby odświeżyć dane.")
                return
            if time.time() < self._cooldown_until:
                self._notice("Kolejna próba będzie możliwa o " + display_time(self._cooldown_until) + ".")
                return
        self._launch(self._sync, "Odświeżam dane…")

    def _sync(self) -> None:
        from .connector import ConnectorError

        if time.time() < self._rate_limited_until:
            with self._lock:
                self._next_sync = self._rate_limited_until
                self._status = "Odświeżanie wstrzymano do " + display_time(self._rate_limited_until) + "."
            return
        successes = 0
        changes: dict[str, int] = {}
        pause_login = False
        limited = False
        # Read the new messaging module last; one unavailable module must not
        # prevent the other successful sections from being displayed.
        for kind in ("grades", "schedule", "attendance", "timetable", "announcements", "messages"):
            with self._lock:
                if self._stop_event.is_set():
                    return
                if kind in self._blocked_sections:
                    continue
                self._status = "Pobieram: " + LABELS[kind].lower() + "…"
                self.emit_state()
            try:
                items = self._client.fetch(kind)
                with self._lock:
                    if self._stop_event.is_set():
                        return
                    count = self._tracker.apply(kind, items)
                    changes[kind] = count
                    self._errors.pop(kind, None)
                    successes += 1
            except ConnectorError as exc:
                with self._lock:
                    self._errors[kind] = str(exc)
                if getattr(exc, "rate_limited", False):
                    limited = True
                    break
                if getattr(exc, "requires_login", False):
                    if kind == "messages":
                        self._blocked_sections.add(kind)
                    else:
                        pause_login = True
                        break
            except Exception:
                with self._lock:
                    self._errors[kind] = "Nie udało się rozpoznać danych tej sekcji. Zachowano poprzedni odczyt."
            self.emit_state()

        with self._lock:
            if successes:
                self._last_sync = datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S")
            self._failure_count = self._failure_count + 1 if not successes else 0
            normal_delay = self._interval * 60
            delay = max(normal_delay, 3600) if limited else min(4 * 3600, normal_delay * 2 ** min(self._failure_count, 4))
            self._cooldown_until = time.time() + (delay if limited else 60)
            if limited:
                self._rate_limited_until = self._cooldown_until
            self._next_sync = time.time() + delay
            if pause_login:
                self._mode = "offline" if self._tracker.initialized else "idle"
                self._next_sync = None
                self._client.close()
                self._client = None
                self._status = ("Sesja wygasła. Połącz konto ponownie; zachowano ostatnio pobrane dane."
                                if self._tracker.initialized else
                                "Nie udało się potwierdzić sesji. Połącz konto ponownie.")
            elif limited:
                self._mode = "live" if self._client is not None else "idle"
                if not successes and self._tracker.initialized:
                    self._mode = "offline"
                self._status = "Librus ograniczył liczbę zapytań. Odświeżanie wstrzymano na co najmniej godzinę."
            elif successes == len(KINDS):
                self._mode = "live"
                total = sum(changes.values())
                self._status = (f"Dane odświeżone. Zmiany: {total}." if total
                                else "Dane odświeżone. Brak nowych zmian.")
            elif successes:
                self._mode = "live"
                self._status = f"Odświeżono {successes}/{len(KINDS)} sekcji. Pozostałe pokazują ostatni udany odczyt."
            else:
                self._status = ("Nie pobrano nowych danych. Wyświetlam ostatnią zapisaną kopię."
                                if self._tracker.initialized else
                                "Nie pobrano danych. Sprawdź komunikaty błędów sekcji.")
                self._mode = ("offline" if self._tracker.initialized else
                              "live" if self._client is not None else "idle")
            self._save()
            self.emit_state()
        self._notify_changes(changes)

    def _notify_changes(self, changes: dict[str, int], *, demo: bool = False) -> None:
        if not self._notifications or self._stop_event.is_set() or not sum(changes.values()):
            return
        counts = ", ".join(f"{LABELS[k].lower()}: {v}" for k, v in changes.items() if v)
        title = "Szkolny Panel — test" if demo else "Nowe informacje w dzienniku"
        message = f"Zmiany — {counts}. Otwórz Szkolny Panel, aby zobaczyć szczegóły."
        # A school message or pupil's name never enters the OS notification text.
        shown = self._notifier(title, message)
        if not shown:
            self._notice("Wykryto zmiany (" + counts + "). Windows nie potwierdził wysłania powiadomienia; sprawdź ustawienia systemu.")

    def _tick(self) -> None:
        while not self._stop_event.wait(1):
            self._check_reminders()
            with self._lock:
                due = (self._next_sync is not None and time.time() >= self._next_sync
                       and not self._busy and self._client is not None)
            if due:
                self.refresh()

    def demo(self) -> None:
        def operation():
            with self._lock:
                if self._client is not None:
                    self._client.close()
                    self._client = None
                self._mode = "demo"
                self._next_sync = None
                self._remembered = None
                self._errors.clear()
                self._tracker = SnapshotTracker()
                self._reminders = []
                self._demo_counter = 0
                self._demo_data = demo_sections()
            self._sync_demo()
        self._launch(operation, "Wczytuję dane demonstracyjne…")

    def _sync_demo(self) -> None:
        changes = {}
        with self._lock:
            for kind in KINDS:
                changes[kind] = self._tracker.apply(kind, self._demo_data[kind])
            self._last_sync = datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S")
            self._status = "Dane demonstracyjne — żadne połączenie z kontem Librus nie zostało wykonane."
            self.emit_state()
        self._notify_changes(changes, demo=True)

    def inject_demo_change(self) -> None:
        if self._mode != "demo":
            return
        def operation():
            with self._lock:
                self._demo_counter += 1
                self._demo_data["grades"].append({
                    "id": f"demo:grades:new-{self._demo_counter}", "kind": "grades",
                    "title": "Biologia · 5", "subtitle": "Nowa ocena — przykład",
                    "when": stamp(), "details": "To sztuczny wpis do sprawdzenia wykrywania zmian i powiadomień.",
                    "url": "", "unread": False,
                })
            self._sync_demo()
        self._launch(operation, "Dodaję przykładową ocenę…")

    def set_preferences(self, interval_min: int, notifications: bool) -> None:
        try:
            interval_min = int(interval_min)
        except (TypeError, ValueError):
            return
        if interval_min not in {10, 15, 30, 60}:
            self._notice("Wybierz odświeżanie co 10, 15, 30 lub 60 minut.")
            return
        with self._lock:
            self._interval = interval_min
            self._notifications = bool(notifications)
            if self._next_sync:
                self._next_sync = max(time.time() + interval_min * 60, self._cooldown_until)
            self._save()
            self.emit_state()

    def test_notification(self) -> None:
        def operation():
            shown = self._notifier("Szkolny Panel — test", "Tak będą wyglądały powiadomienia o nowych informacjach w dzienniku.")
            self._notice("Wysłano test do Windows. Jeśli go nie widać, sprawdź tryb Nie przeszkadzać i ustawienia powiadomień."
                         if shown else "Nie udało się wysłać powiadomienia systemowego. Sprawdź ustawienia Windows.")
        self._launch(operation, "Sprawdzam powiadomienia…")

    def schedule_reminder(self, kind: str, item_id: str, title: str, due_at: str,
                          show_text: bool = True) -> bool:
        try:
            values = reminder_input(title, due_at, show_text, now=time.time())
            with self._lock:
                if kind not in REMINDER_KINDS:
                    raise ValueError("Przypomnienia można ustawić dla ogłoszeń, terminarza i wiadomości.")
                item = next((x for x in self._tracker.sections[kind] if x["id"] == item_id), None)
                if item is None:
                    raise ValueError("Nie znaleziono wpisu do przypomnienia.")
                reminder = new_reminder(item, values)
        except ValueError as exc:
            self._notice(str(exc))
            return False

        def operation():
            with self._lock:
                self._reminders.append(reminder)
                if not self._save():
                    self._reminders.remove(reminder)
                    self._notice("Nie zapisano przypomnienia. Sprawdź komunikat zapisu danych.")
                else:
                    self._notice("Zaplanowano przypomnienie." + (" W demo zniknie po jego zakończeniu." if self._mode == "demo" else ""))
        return self._launch(operation, "Zapisuję przypomnienie…")

    def update_reminder(self, reminder_id: str, title: str, due_at: str,
                        show_text: bool = True) -> bool:
        try:
            values = reminder_input(title, due_at, show_text, now=time.time())
        except ValueError as exc:
            self._notice(str(exc))
            return False

        def operation():
            with self._lock:
                row = next((r for r in self._reminders if r["id"] == reminder_id), None)
                if row is None or reminder_id in self._reminders_inflight:
                    self._notice("Przypomnienie zostało usunięte lub jest właśnie wysyłane.")
                    return
                previous = copy.deepcopy(row)
                row.update(values)
                if not self._save():
                    row.clear()
                    row.update(previous)
                    self._notice("Nie zapisano zmiany przypomnienia.")
                else:
                    self._notice("Zmieniono termin i treść przypomnienia.")
        return self._launch(operation, "Zmieniam przypomnienie…")

    def delete_reminder(self, reminder_id: str) -> bool:
        def operation():
            with self._lock:
                row = next((r for r in self._reminders if r["id"] == reminder_id), None)
                if row is None or reminder_id in self._reminders_inflight:
                    self._notice("Przypomnienie zostało usunięte lub jest właśnie wysyłane.")
                    return
                index = self._reminders.index(row)
                self._reminders.remove(row)
                if not self._save():
                    self._reminders.insert(index, row)
                    self._notice("Nie zapisano usunięcia przypomnienia.")
                else:
                    self._notice("Usunięto przypomnienie.")
        return self._launch(operation, "Usuwam przypomnienie…")

    def _check_reminders(self, now: float | None = None) -> None:
        """Independent of network sync and its interval; also works offline."""
        now = time.time() if now is None else now
        with self._lock:
            if self._stop_event.is_set():
                return
            due = [copy.deepcopy(r) for r in self._reminders if r["status"] == "pending"
                   and r["id"] not in self._reminders_inflight and due_datetime(r["due_at"]).timestamp() <= now]
            self._reminders_inflight.update(r["id"] for r in due)
            profile = (self._account, self._mode == "demo")
        for reminder in due:
            try:
                with self._lock:
                    if self._stop_event.is_set() or profile != (self._account, self._mode == "demo"):
                        continue
                text = reminder["title"] if reminder["show_text"] else "Masz zaplanowane przypomnienie. Otwórz Szkolny Panel, aby zobaczyć szczegóły."
                try:
                    sent = bool(self._notifier("Szkolny Panel — przypomnienie", text))
                except Exception:
                    sent = False
                with self._lock:
                    row = next((r for r in self._reminders if r["id"] == reminder["id"]), None)
                    if row is None or profile != (self._account, self._mode == "demo"):
                        continue
                    row.update(status="fired", fired_at=datetime.fromtimestamp(now).astimezone().isoformat(timespec="seconds"),
                               system_sent=sent)
                    if not self._save():
                        self._notice("Przypomnienie wykonane, ale nie zapisano jego stanu. Może pojawić się ponownie po restarcie.")
                    self.events.put(("reminder", copy.deepcopy(row)))
                    if not sent:
                        self._notice("Windows nie przyjął przypomnienia. Jego treść pokaże okno aplikacji.")
                    self.emit_state()
            finally:
                with self._lock:
                    self._reminders_inflight.discard(reminder["id"])

    def read_message(self, item_id: str) -> None:
        with self._lock:
            item = next((x for x in self._tracker.sections["messages"] if x["id"] == item_id), None)
            if not item:
                return
            if self._mode == "demo":
                self.events.put(("message", {"title": item["title"], "text":
                    "To przykładowa treść wiadomości.\n\nW rzeczywistym trybie treść jest pobierana po kliknięciu i może zostać oznaczona jako przeczytana w Librusie.\n\nDane demonstracyjne — bez połączenia ze szkołą."}))
                return
            if self._client is None:
                self._notice("Połącz konto, aby pobrać treść wiadomości.")
                return
            if time.time() < self._rate_limited_until:
                self._notice("Pobieranie wstrzymane do " + display_time(self._rate_limited_until) + ".")
                return

        def operation():
            from .connector import ConnectorError
            try:
                content = self._client.read_message(item_id)
                self.events.put(("message", content))
                # Full body is deliberately not persisted. The list remains a preview.
                with self._lock:
                    item["unread"] = False
                    self._status = "Pobrano treść wiadomości."
                    self._save()
            except ConnectorError as exc:
                with self._lock:
                    self._status = "Nie pobrano treści wiadomości."
                    self._errors["messages"] = str(exc)
                    if getattr(exc, "rate_limited", False):
                        self._cooldown_until = time.time() + 3600
                        self._rate_limited_until = self._cooldown_until
                        self._next_sync = self._cooldown_until
                self._notice(str(exc))
        self._launch(operation, "Pobieram treść wiadomości…")

    def open_in_librus(self, item_id: str | None = None) -> None:
        # Open a fixed official landing page, never arbitrary URLs supplied in messages.
        kind = None
        if item_id:
            with self._lock:
                kind = next((k for k, items in self._tracker.sections.items()
                             if any(x["id"] == item_id for x in items)), None)
        from .connector import SECTION_URLS

        url = SECTION_URLS.get(kind, "https://synergia.librus.pl/")
        webbrowser.open(url)

    def forget(self) -> None:
        def operation():
            with self._lock:
                if self._client is not None:
                    self._client.close()
                self._client = None
                disk_error = False
                try:
                    self._storage.clear()
                except StorageError:
                    disk_error = True
                # Clear RAM even when a locked file cannot be removed.
                self._tracker = SnapshotTracker()
                self._reminders = []
                self._remembered = None
                self._account = ""
                self._next_sync = None
                self._blocked_sections.clear()
                self._cooldown_until = 0
                self._errors.clear()
                self._mode = "idle"
                self._last_sync = "—"
                if disk_error:
                    self._status = "Usunięto konto z pamięci aplikacji; nie udało się usunąć zapisu z dysku."
                    self._errors["storage"] = "Plik z poprzednimi danymi może nadal istnieć. Usuń lokalne dane ponownie przed ponownym uruchomieniem aplikacji."
                else:
                    self._status = "Usunięto lokalne dane i zapamiętane konto."
        self._launch(operation, "Usuwam lokalne dane…")

    def stop(self) -> None:
        self._stop_event.set()
        with self._lock:
            self._next_sync = None
            if not self._busy and self._client is not None:
                self._client.close()
            self._remembered = None

    def wait_idle(self, timeout: float = 5) -> bool:
        """For offline tests and preview capture, never called by the UI loop."""
        worker = self._worker
        if worker:
            worker.join(timeout)
        return not self._busy
