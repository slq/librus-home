"""Offline behavioral checks: persistence, failures, and user-visible effects."""

import copy
import json
import time
from datetime import date

import pytest

from librus_app.connector import ConnectorError
from librus_app.core import Controller
from librus_app.data import SnapshotTracker, demo_sections
from librus_app.platform_windows import SecureStorage


class FakeConnector:
    instances = []

    def __init__(self, login, password):
        self.data = demo_sections()
        self.errors = {}
        self.calls = []
        self.closed = False
        self.__class__.instances.append(self)

    def connect(self):
        self.calls.append("connect")

    def fetch(self, kind):
        self.calls.append(kind)
        if kind in self.errors:
            raise self.errors[kind]
        return copy.deepcopy(self.data[kind])

    def read_message(self, item_id):
        self.calls.append("body:" + item_id)
        return {"title": "Przykład", "text": "Testowa treść"}

    def close(self):
        self.closed = True


def make_controller(storage=None):
    notices = []
    c = Controller(storage=storage, connector_factory=FakeConnector,
                   notifier=lambda *args: notices.append(args) or True, start_ticker=False)
    return c, notices


def connect(c, remember=False):
    c.connect("test-parent", "synthetic-test-secret", remember)
    assert c.wait_idle(5)
    assert c._mode == "live"
    return c._client


def refresh(c):
    c._cooldown_until = 0
    c.refresh()
    assert c.wait_idle(5)


def test_initial_sync_is_quiet_and_password_is_opt_in():
    store = SecureStorage()
    c, notices = make_controller(store)
    try:
        client = connect(c)
        assert not notices
        assert store.load()["credentials"] is None
        assert "synthetic-test-secret" not in json.dumps(store.load())
        assert not any(x.startswith("body:") for x in client.calls)
        refresh(c)
        assert not notices
    finally:
        c.stop()


def test_remember_account_and_restore_baseline_without_spam():
    store = SecureStorage()
    c, notices = make_controller(store)
    connect(c, remember=True)
    assert store.load()["credentials"]["password"] == "synthetic-test-secret"
    c.stop()
    c2, restored_notices = make_controller(store)
    try:
        c2.restore()
        assert c2.wait_idle(5)
        assert c2._mode == "live"
        assert not restored_notices
    finally:
        c2.stop()


def test_partial_failure_preserves_old_rows_and_section_timestamp():
    c, notices = make_controller()
    try:
        client = connect(c)
        original = copy.deepcopy(c._tracker.sections["grades"])
        fetched_at = c._tracker.updated_at["grades"]
        client.errors["grades"] = ConnectorError("Zmieniony format ocen.")
        client.data["messages"][0]["title"] = "Zmieniony temat"
        refresh(c)
        assert c._tracker.sections["grades"] == original
        assert c._tracker.updated_at["grades"] == fetched_at
        assert "grades" in c._errors
        assert c._mode == "live"
        assert len(notices) == 1
        assert "Zmieniony temat" not in repr(notices)  # toast is generic
    finally:
        c.stop()


def test_auth_failure_pauses_sync_and_keeps_cache():
    c, notices = make_controller()
    try:
        client = connect(c)
        before = copy.deepcopy(c._tracker.sections)
        client.errors["grades"] = ConnectorError("Sesja wygasła.", requires_login=True)
        refresh(c)
        assert c._mode == "offline"
        assert c._client is None
        assert c._next_sync is None
        assert c._tracker.sections == before
        assert client.closed
        assert not notices
    finally:
        c.stop()


def test_message_auth_failure_does_not_retry_or_break_other_sections():
    c, notices = make_controller()
    try:
        client = connect(c)
        client.errors["messages"] = ConnectorError("Skrzynka wymaga logowania.", requires_login=True)
        refresh(c)
        count = client.calls.count("messages")
        assert c._client is client
        refresh(c)
        assert client.calls.count("messages") == count
        assert c._mode == "live"
        assert "messages" in c._errors
    finally:
        c.stop()


def test_demo_never_overwrites_live_credentials_or_cache():
    store = SecureStorage()
    c, notices = make_controller(store)
    try:
        connect(c, remember=True)
        saved = copy.deepcopy(store.load())
        c.demo()
        assert c.wait_idle()
        c.inject_demo_change()
        assert c.wait_idle()
        c.set_preferences(30, True)
        assert store.load() == saved
        assert c._mode == "demo"
        assert len(notices) == 1
    finally:
        c.stop()


def test_429_stops_further_calls_and_cannot_be_bypassed_by_connect_or_read():
    c, notices = make_controller()
    try:
        client = connect(c)
        client.errors["schedule"] = ConnectorError("Poczekaj.", rate_limited=True)
        refresh(c)
        assert c._rate_limited_until >= time.time() + 3590
        calls = list(client.calls)
        instances = len(FakeConnector.instances)
        c.refresh()
        c.connect("test-parent", "synthetic-test-secret")
        c.read_message(c._tracker.sections["messages"][0]["id"])
        assert client.calls == calls
        assert len(FakeConnector.instances) == instances
        assert c._next_sync >= c._rate_limited_until
    finally:
        c.stop()


def test_message_detail_is_explicit_and_not_persisted():
    c, notices = make_controller()
    try:
        client = connect(c)
        message_id = c._tracker.sections["messages"][0]["id"]
        c.read_message(message_id)
        assert c.wait_idle()
        assert client.calls[-1] == "body:" + message_id
        assert "Testowa treść" not in json.dumps(c._storage.load(), ensure_ascii=False)
        assert not next(x for x in c._tracker.sections["messages"] if x["id"] == message_id)["unread"]
    finally:
        c.stop()


def test_forget_clears_only_local_state_and_stops_sync():
    c, notices = make_controller()
    try:
        client = connect(c, remember=True)
        c.forget()
        assert c.wait_idle()
        assert c._storage.load() == {}
        assert c._next_sync is None
        assert c._mode == "idle"
        assert c._remembered is None
        assert client.closed
    finally:
        c.stop()


def test_failed_disk_removal_still_clears_memory_credentials():
    from librus_app.platform_windows import StorageError

    class LockedStorage(SecureStorage):
        def clear(self):
            raise StorageError("Plik jest zablokowany.")

    c, _ = make_controller(LockedStorage())
    try:
        connect(c, remember=True)
        c.forget()
        assert c.wait_idle()
        assert c._remembered is None
        assert c._client is None
        assert c._account == ""
        assert c._next_sync is None
        assert "storage" in c._errors
        c.set_preferences(30, True)
        assert c._storage.load()["credentials"] is None
    finally:
        c.stop()


def test_unchecking_remember_removes_old_password_even_if_login_fails():
    c, _ = make_controller()
    try:
        connect(c, remember=True)
        before = c._tracker.export()
        last_sync = c._last_sync

        class FailedLogin(FakeConnector):
            def connect(self):
                raise ConnectorError("Błąd logowania.", requires_login=True)

        c._connector_factory = FailedLogin
        c.connect("test-parent", "another-fake-password", False)
        assert c.wait_idle()
        assert c._storage.load()["credentials"] is None
        assert "connection" in c._errors
        assert c._state()["mode"] == "offline"
        assert c._tracker.export() == before
        assert c._last_sync == last_sync
        assert c._client is None
        assert c._next_sync is None
    finally:
        c.stop()


@pytest.mark.parametrize("error", [
    ConnectorError("Błąd logowania.", requires_login=True),
    ValueError("Unexpected authentication response"),
])
def test_failed_first_login_does_not_report_a_saved_copy(error):
    class FailedLogin(FakeConnector):
        def connect(self):
            raise error

    c, _ = make_controller()
    c._connector_factory = FailedLogin
    try:
        c.connect("test-parent", "synthetic-test-secret")
        assert c.wait_idle()
        state = c._state()
        assert state["mode"] == "idle"
        assert state["last_sync"] == "—"
        assert "connection" in state["errors"]
        assert not c._tracker.initialized
        assert c._client is None
        assert c._next_sync is None
        assert FailedLogin.instances[-1].closed
    finally:
        c.stop()


@pytest.mark.parametrize("error", [
    ConnectorError("Sesja wygasła.", requires_login=True),
    ConnectorError("Poczekaj.", rate_limited=True),
    ConnectorError("Nie można pobrać danych."),
])
def test_failed_first_sync_does_not_report_a_saved_copy(error):
    class UnavailableData(FakeConnector):
        def fetch(self, kind):
            raise error

    c, _ = make_controller()
    c._connector_factory = UnavailableData
    try:
        c.connect("test-parent", "synthetic-test-secret")
        assert c.wait_idle()
        state = c._state()
        if error.requires_login:
            assert state["mode"] == "idle"
            assert c._client is None
        else:
            assert state["mode"] == "live"
            assert c._client is not None
        assert state["last_sync"] == "—"
        assert state["errors"]
        assert "kopię" not in state["status"]
        assert "zachowano" not in state["status"]
        assert not c._tracker.initialized
    finally:
        c.stop()


def test_snapshot_deduplicates_restart_and_ignores_server_read_state():
    tracker = SnapshotTracker()
    items = demo_sections()["messages"]
    assert tracker.apply("messages", items) == 0
    items[0]["unread"] = not items[0]["unread"]
    assert tracker.apply("messages", items) == 0
    items[0]["title"] = "Poprawiony temat"
    assert tracker.apply("messages", items) == 1
    restored = SnapshotTracker(tracker.export())
    assert restored.apply("messages", items) == 0
    removed = items.pop()
    assert restored.apply("messages", items) == 0
    assert restored.apply("messages", items + [removed]) == 0


def test_new_timetable_horizon_is_quiet_but_added_or_cancelled_lesson_is_a_change():
    today = date(2026, 10, 7)
    tracker = SnapshotTracker()
    items = demo_sections(today)["timetable"]
    assert tracker.apply("timetable", items, today=today) == 0
    extra = copy.deepcopy(items[0])
    extra.update(id="new-lesson", when="2026-10-08 12:00", title="Zastępstwo")
    assert tracker.apply("timetable", items + [extra], today=today) == 1
    assert tracker.apply("timetable", items, today=today) == 1
    assert tracker.apply("timetable", items, today=today) == 0
    future = copy.deepcopy(extra)
    future.update(id="new-horizon", when="2026-10-19 12:00")
    assert tracker.apply("timetable", [future], today=date(2026, 10, 14)) == 0


def test_malformed_section_does_not_destroy_previous_snapshot():
    tracker = SnapshotTracker()
    tracker.apply("grades", demo_sections()["grades"])
    before = tracker.export()
    try:
        tracker.apply("grades", [{"title": "Brak id"}])
    except ValueError:
        pass
    else:
        raise AssertionError("Should reject malformed rows")
    assert tracker.export() == before
