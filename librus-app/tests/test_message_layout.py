"""Message actions remain available when the preview pane is collapsed."""

import pytest

from szkolny_panel.data import demo_sections
from test_calendar import schedule_app


@pytest.mark.parametrize("size", ["1080x720", "1240x850"])
def test_read_message_button_stays_visible_when_preview_is_collapsed(schedule_app, monkeypatch, size):
    app, _ = schedule_app
    app._render_state({"sections": demo_sections(), "mode": "demo"})
    app.select_page("messages")
    view = app.views["messages"]
    app.root.geometry(size + "+20000+20000")
    app.root.deiconify()
    app.root.update()
    assert str(view.read_button["state"]) == "disabled"
    row = view.tree.get_children()[0]
    view.tree.selection_set(row)
    view._select()
    selected_id = view.selected_item["id"]
    for position in (view.panes.winfo_height() // 2, view.panes.winfo_height() - 4):
        view.panes.sashpos(0, position)
        app.root.update()
        button = view.read_button
        assert button.winfo_ismapped()
        assert button.winfo_height() >= button.winfo_reqheight()
        assert button.winfo_rooty() >= view.frame.winfo_rooty()
        assert button.winfo_rooty() + button.winfo_height() <= view.frame.winfo_rooty() + view.frame.winfo_height()
        assert str(button["state"]) == "normal"
    calls = []
    monkeypatch.setattr(app, "call", lambda *args, **kwargs: calls.append(args))
    view.read_button.invoke()
    assert calls == [("read_message", selected_id)]
    app._render_state({"busy": True})
    assert view.read_button.winfo_ismapped()
    assert str(view.read_button["state"]) == "disabled"
