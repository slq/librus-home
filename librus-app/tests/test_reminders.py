"""Personal alerts: timing, persistence, privacy, demo and UI behavior."""

import copy
from datetime import datetime, timedelta
import threading
import time

import pytest

from szkolny_panel.core import profile_id
from szkolny_panel.platform_windows import SecureStorage, StorageError
from szkolny_panel.reminders import due_datetime, restore_reminders
from test_core import connect, make_controller
from test_calendar import schedule_app


def create_reminder(controller, kind="announcements", *, show_text=True, offset=60):
    item = controller._tracker.sections[kind][0]
    due = datetime.now().astimezone() + timedelta(seconds=offset)
    assert controller.schedule_reminder(kind, item["id"], "Moja ważna sprawa", due.isoformat(), show_text)
    assert controller.wait_idle()
    return controller._reminders[-1]


def events(controller, kind):
    found = []
    while not controller.events.empty():
        name, payload = controller.events.get_nowait()
        if name == kind:
            found.append(payload)
    return found


@pytest.mark.parametrize("kind", ["announcements", "schedule", "messages"])
def test_reminder_runs_once_at_due_time_even_offline_during_sync(kind):
    controller, notices = make_controller()
    try:
        client = connect(controller)
        reminder = create_reminder(controller, kind)
        due = due_datetime(reminder["due_at"]).timestamp()
        calls = copy.deepcopy(client.calls)
        controller._check_reminders(due - 1)
        assert not notices
        controller._mode = "offline"
        controller._busy = True
        controller._notifications = False  # This switch concerns new school data.
        controller._check_reminders(due)
        controller._check_reminders(due + 60)
        assert len(notices) == 1
        assert notices[0] == ("Szkolny Panel — przypomnienie", "Moja ważna sprawa")
        assert client.calls == calls
        assert reminder["status"] == "fired"
        assert reminder["system_sent"]
        assert len(events(controller, "reminder")) == 1
        assert controller._storage.load()["reminders"][0]["status"] == "fired"
    finally:
        controller._busy = False
        controller.stop()


def test_restore_runs_overdue_reminder_then_restart_does_not_repeat():
    store = SecureStorage()
    first, _ = make_controller(store)
    connect(first)
    row = create_reminder(first)
    saved = store.load()
    saved["reminders"][0]["due_at"] = (datetime.now().astimezone() - timedelta(minutes=10)).isoformat()
    store.save(saved)
    first.stop()
    second, notices = make_controller(store)
    try:
        second.restore()
        assert second.wait_idle()
        assert second._mode == "offline"
        second._check_reminders()
        assert len(notices) == 1
        assert second._reminders[0]["id"] == row["id"]
    finally:
        second.stop()
    third, notices = make_controller(store)
    try:
        third.restore()
        assert third.wait_idle()
        third._check_reminders()
        assert not notices
    finally:
        third.stop()


def test_edit_delete_and_repeat_a_completed_reminder():
    controller, notices = make_controller()
    try:
        connect(controller)
        row = create_reminder(controller)
        due = datetime.now().astimezone() + timedelta(hours=2)
        assert controller.update_reminder(row["id"], "Inna treść", due.isoformat(), False)
        assert controller.wait_idle()
        controller._check_reminders(time.time() + 100)
        assert not notices
        controller._check_reminders(due.timestamp() + 1)
        assert len(notices) == 1
        assert "Inna treść" not in repr(notices)
        assert row["status"] == "fired"
        later = due + timedelta(hours=1)
        controller.update_reminder(row["id"], "Ponów", later.isoformat())
        assert controller.wait_idle()
        assert row["status"] == "pending"
        controller.delete_reminder(row["id"])
        assert controller.wait_idle()
        controller._check_reminders(later.timestamp() + 1)
        assert len(notices) == 1
        assert not controller._reminders
        assert not controller._storage.load()["reminders"]
    finally:
        controller.stop()


@pytest.mark.parametrize("throws", [False, True])
def test_failed_windows_submission_still_shows_one_in_app_alert(throws):
    controller, _ = make_controller()
    try:
        connect(controller)
        row = create_reminder(controller, show_text=False)

        def unavailable(*args):
            if throws:
                raise RuntimeError("fixture-private-error")
            return False

        controller._notifier = unavailable
        controller._check_reminders(due_datetime(row["due_at"]).timestamp())
        controller._check_reminders(time.time() + 600)
        assert row["status"] == "fired"
        assert not row["system_sent"]
        assert len(events(controller, "reminder")) == 1
        assert "fixture-private" not in repr(events(controller, "notice"))
    finally:
        controller.stop()


def test_demo_and_account_changes_keep_reminders_separate_and_forget_clears_them():
    controller, notices = make_controller()
    try:
        connect(controller)
        original = create_reminder(controller)
        saved = copy.deepcopy(controller._storage.load())
        controller.demo()
        assert controller.wait_idle()
        assert not controller._reminders
        temporary = create_reminder(controller, "messages")
        controller._check_reminders(due_datetime(temporary["due_at"]).timestamp())
        assert controller._storage.load() == saved
        connect(controller)
        assert controller._reminders[0]["id"] == original["id"]
        controller.connect("different-fixture-account", "not-a-real-password")
        assert controller.wait_idle()
        assert controller._account == profile_id("different-fixture-account")
        assert not controller._reminders
        create_reminder(controller)
        controller.forget()
        assert controller.wait_idle()
        assert not controller._reminders
    finally:
        controller.stop()


def test_reminder_survives_its_source_leaving_the_visible_window():
    controller, notices = make_controller()
    try:
        connect(controller)
        row = create_reminder(controller, "schedule")
        controller._tracker.apply("schedule", [])
        controller._check_reminders(due_datetime(row["due_at"]).timestamp())
        assert len(notices) == 1
        assert events(controller, "reminder")[0]["source_title"]
    finally:
        controller.stop()


def test_reminders_are_not_discarded_when_a_new_school_year_resets_the_snapshot():
    store = SecureStorage()
    first, _ = make_controller(store)
    connect(first)
    row = create_reminder(first)
    saved = store.load()
    saved["snapshot"]["year"] = "1900/1901"
    store.save(saved)
    first.stop()
    restored, _ = make_controller(store)
    try:
        restored.restore()
        assert restored.wait_idle()
        assert not restored._tracker.initialized
        assert restored._reminders[0]["id"] == row["id"]
    finally:
        restored.stop()


def test_running_timer_delivers_due_alert_without_a_network_refresh():
    controller, notices = make_controller()
    received = threading.Event()
    try:
        client = connect(controller)
        row = create_reminder(controller)
        row["due_at"] = (datetime.now().astimezone() - timedelta(seconds=1)).isoformat()
        before = copy.deepcopy(client.calls)

        def delivered(title, text):
            notices.append((title, text))
            received.set()
            return True

        controller._notifier = delivered
        ticker = threading.Thread(target=controller._tick)
        ticker.start()
        assert received.wait(3)
        controller.stop()
        ticker.join(2)
        assert not ticker.is_alive()
        assert len(notices) == 1
        assert client.calls == before
    finally:
        controller.stop()


def test_reminder_mutations_roll_back_on_storage_failure():
    class BrokenStorage(SecureStorage):
        fail = False

        def save(self, data):
            if self.fail:
                raise StorageError("fixture error")
            super().save(data)

    store = BrokenStorage()
    controller, _ = make_controller(store)
    try:
        connect(controller)
        row = create_reminder(controller)
        before = copy.deepcopy(controller._reminders)
        store.fail = True
        due = (datetime.now().astimezone() + timedelta(hours=1)).isoformat()
        controller.schedule_reminder("messages", controller._tracker.sections["messages"][0]["id"], "Nowe", due)
        assert controller.wait_idle()
        assert controller._reminders == before
        controller.update_reminder(row["id"], "Zmiana", due)
        assert controller.wait_idle()
        assert controller._reminders == before
        controller.delete_reminder(row["id"])
        assert controller.wait_idle()
        assert controller._reminders == before
        assert "storage" in controller._errors
    finally:
        controller.stop()


def test_invalid_and_past_reminders_are_rejected_without_mutation():
    controller, _ = make_controller()
    try:
        connect(controller)
        item = controller._tracker.sections["messages"][0]
        future = (datetime.now().astimezone() + timedelta(hours=1)).isoformat()
        for title, due in (("", future), ("x" * 201, future), ("Tytuł", "nieznana"),
                           ("Tytuł", (datetime.now().astimezone() - timedelta(hours=1)).isoformat())):
            assert not controller.schedule_reminder("messages", item["id"], title, due)
        assert not controller.schedule_reminder("grades", "missing", "Tytuł", future)
        assert not controller.schedule_reminder("messages", "missing", "Tytuł", future)
        assert not controller._reminders
        assert restore_reminders([]) == []
        with pytest.raises(ValueError):
            restore_reminders([{"status": "invalid"}])
    finally:
        controller.stop()


def test_simultaneous_timer_checks_do_not_fire_twice():
    controller, notices = make_controller()
    started = threading.Event()
    finish = threading.Event()
    try:
        connect(controller)
        row = create_reminder(controller)

        def delayed(title, text):
            started.set()
            assert finish.wait(2)
            notices.append((title, text))
            return True

        controller._notifier = delayed
        due = due_datetime(row["due_at"]).timestamp()
        worker = threading.Thread(target=controller._check_reminders, args=(due,))
        worker.start()
        assert started.wait(2)
        controller._check_reminders(due)
        finish.set()
        worker.join(2)
        assert not worker.is_alive()
        assert len(notices) == 1
    finally:
        finish.set()
        controller.stop()


def test_editor_save_manage_and_open_source_in_real_tk(schedule_app, monkeypatch):
    app, _ = schedule_app
    controller = app.controller
    controller.demo()
    assert controller.wait_idle()
    app._render_state(controller._state())
    for kind in ("schedule", "announcements", "messages"):
        app.select_page(kind)
        view = app.views[kind]
        row = view.tree.get_children()[0]
        view.tree.selection_set(row)
        view._select()
        view.reminder_button.invoke()
        dialog = app.reminder_dialog
        assert dialog is not None
        dialog.form_vars["date"].set("nieprawidłowa")
        dialog.save_button.invoke()
        assert dialog.error_label["text"]
        target = datetime.now() + timedelta(hours=1)
        dialog.form_vars["date"].set(target.strftime("%d.%m.%Y"))
        dialog.form_vars["time"].set(target.strftime("%H:%M"))
        dialog.form_vars["title"].set("Własna treść " + kind)
        dialog.save_button.invoke()
        assert controller.wait_idle()
        assert app.reminder_dialog is None
        app._render_state(controller._state())
    app.select_page("reminders")
    view = app.views["reminders"]
    assert len(view.tree.get_children()) == 3
    first = view.tree.get_children()[0]
    view.tree.selection_set(first)
    view._select()
    view.edit_button.invoke()
    dialog = app.reminder_dialog
    dialog.form_vars["title"].set("Zmieniona treść")
    dialog.form_vars["show_text"].set(False)
    dialog.save_button.invoke()
    assert controller.wait_idle()
    app._render_state(controller._state())
    assert view.selected_item["title"] == "Zmieniona treść"
    reminder = view.selected_item["reminder"]
    view.open_button.invoke()
    assert app.current_page == reminder["kind"]
    assert app.views[app.current_page].selected_item["id"] == reminder["item_id"]
    app.select_page("reminders")
    view.delete_button.invoke()
    assert controller.wait_idle()
    app._render_state(controller._state())
    assert len(view.tree.get_children()) == 2
    shown = []
    monkeypatch.setattr(app, "show_text", lambda title, text: shown.append((title, text)))
    due = max(due_datetime(r["due_at"]).timestamp() for r in controller._reminders)
    controller._check_reminders(due + 1)
    app._drain_events()
    assert len(shown) == 2
    assert all("Własna treść" in text for _, text in shown)
    assert all(r["status"] == "fired" for r in controller._reminders)
    app.root.geometry("1080x720+20000+20000")
    app.root.deiconify()
    app.root.update()
    footer = app.sidebar_mode.master
    assert footer.winfo_height() >= footer.winfo_reqheight()
