"""Schedule navigation and real Tk interactions on synthetic data only."""

from datetime import date, datetime

import pytest
import tkinter as tk

from librus_app.calendar_view import item_day, shift_month
from librus_app.core import Controller
from librus_app.platform_windows import SecureStorage
from librus_app.ui import create_app


def event(ident, day, title="Sprawdzian", details="Przykładowa treść"):
    return {"id": ident, "title": title, "subtitle": "Matematyka", "when": day.isoformat(),
            "details": details, "kind": "schedule", "changed": False}


def test_month_navigation_handles_year_boundary_and_leap_year():
    assert shift_month(date(2026, 12, 31), 1) == date(2027, 1, 1)
    assert shift_month(date(2027, 1, 31), -1) == date(2026, 12, 1)
    assert shift_month(date(2028, 1, 31), 1) == date(2028, 2, 1)
    assert item_day({"when": "2028-02-29T08:00:00"}) == date(2028, 2, 29)
    assert item_day({"when": "nieznana data"}) is None


@pytest.fixture
def schedule_app(monkeypatch, tk_root):
    # Keep the test window off screen, including while checking actual layout.
    def hidden_root():
        root = tk.Toplevel(tk_root)
        root.withdraw()
        root.geometry("1080x720+20000+20000")
        root.overrideredirect(True)
        return root

    monkeypatch.setattr(tk, "Tk", hidden_root)
    storage = SecureStorage()
    storage._persistent = False
    controller = Controller(storage=storage, notifier=lambda *args: True, start_ticker=False)
    try:
        app = create_app(controller, restore=False)
    except tk.TclError as error:
        controller.stop()
        if "display" in str(error).lower():
            pytest.skip("Tk display is unavailable")
        raise
    try:
        month = date.today().replace(day=1)
        rows = [event("first", month, "Sprawdzian", "Ułamki"),
                event("second", month, "Zebranie", "Spotkanie rodziców"),
                event("third", month.replace(day=2), "Wycieczka"),
                event("next", shift_month(month, 1), "Kolejny miesiąc")]
        app._render_state({"mode": "demo", "sections": {"schedule": rows},
                           "updated_at": {"schedule": datetime.now().isoformat()}})
        app.select_page("schedule")
        yield app, rows
    finally:
        for pending in app.root.tk.call("after", "info"):
            app.root.after_cancel(pending)
        app.close()


def test_calendar_day_selection_and_list_switch_keep_all_events(schedule_app):
    app, rows = schedule_app
    view = app.views["schedule"]
    assert len(view.tree.get_children()) == 3
    assert len(view.day_items[view.month]) == 2
    view.day_buttons[view.month].invoke()
    assert len(view.tree.get_children()) == 2
    first = view.tree.get_children()[0]
    view.tree.selection_set(first)
    view._select()
    assert view.selected_item["id"] == "first"
    assert "Ułamki" in view.detail_text.get("1.0", "end")
    assert str(view.details_button["state"]) == "normal"
    view.day_buttons[view.month].invoke()
    assert view.selected_day is None
    assert len(view.tree.get_children()) == 3
    view.list_button.invoke()
    assert len(view.tree.get_children()) == 4
    assert not view.calendar_frame.winfo_manager()
    view.calendar_button.invoke()
    assert len(view.tree.get_children()) == 3


def test_calendar_search_and_refresh_update_counts_and_details(schedule_app):
    app, rows = schedule_app
    view = app.views["schedule"]
    view.search_var.set("ułamki")
    assert len(view.tree.get_children()) == 1
    assert len(view.day_items[view.month]) == 1
    view.day_buttons[view.month].invoke()
    first = view.tree.get_children()[0]
    view.tree.selection_set(first)
    view._select()
    rows[0]["details"] = "Ułamki — zmieniony zakres"
    rows[0]["changed"] = True
    app._render_state({"sections": {"schedule": rows}})
    assert view.selected_item["id"] == "first"
    assert "zmieniony zakres" in view.detail_text.get("1.0", "end")
    assert "+" in view.day_buttons[view.month]["text"]
    view.search_var.set("brak takiej treści")
    assert not view.tree.get_children()
    assert not view.day_items
    view.search_var.set("")
    assert len(view.tree.get_children()) == 2


def test_calendar_month_navigation_and_uncovered_month_notice(schedule_app):
    app, rows = schedule_app
    view = app.views["schedule"]
    current_month = view.month
    view.select_day(current_month)
    view.move_month(1)
    assert view.month == shift_month(current_month, 1)
    assert view.selected_day is None
    assert len(view.tree.get_children()) == 1
    app.state["mode"] = "live"
    view.move_month(1)
    assert not view.tree.get_children()
    assert "poza zakresem" in view.calendar_note["text"]
    view.go_today()
    assert view.month == current_month
    assert view.selected_day == date.today()


def test_calendar_leap_day_and_empty_data_are_visible(schedule_app):
    app, rows = schedule_app
    view = app.views["schedule"]
    leap_day = date(2028, 2, 29)
    app._render_state({"sections": {"schedule": [event("leap", leap_day)]}})
    view.select_day(leap_day)
    assert leap_day in view.day_buttons
    assert len(view.tree.get_children()) == 1
    app._render_state({"sections": {"schedule": []}, "errors": {"schedule": "Błąd odczytu"}})
    assert not view.tree.get_children()
    assert view.error_label.winfo_manager() == "pack"
    app._render_state({"updated_at": {}, "errors": {}, "mode": "idle"})
    assert "Połącz konto" in view.calendar_note["text"]


@pytest.mark.parametrize("size", ["1080x720", "1240x850"])
@pytest.mark.parametrize("six_weeks", [False, True])
def test_calendar_and_list_fit_window(schedule_app, size, six_weeks):
    app, rows = schedule_app
    view = app.views["schedule"]
    if six_weeks:
        view.month = date(2026, 8, 1)
        view.render()
    app.root.geometry(size + "+20000+20000")
    app.root.deiconify()
    app.root.update()
    assert view.calendar_frame.winfo_ismapped()
    assert view.tree.winfo_ismapped()
    assert view.calendar_frame.winfo_height() >= view.calendar_frame.winfo_reqheight()
    assert view.tree.winfo_height() >= 110
    assert view.detail_text.winfo_height() >= 40
    assert view.panes.winfo_y() >= view.calendar_frame.winfo_y() + view.calendar_frame.winfo_height()
