"""Account-free checks for announcements, persistence and change notifications."""

import copy
from types import SimpleNamespace

import pytest
from librus_apix.client import Client, Token

from szkolny_panel import connector as c
from szkolny_panel.data import KINDS, SnapshotTracker, demo_sections
from test_connector import prepared_connector
from test_core import connect, make_controller, refresh


def announcement(**overrides):
    values = dict(title="Dzień otwarty", author="Dyrekcja", date="07.10.2026",
                  description="<p>Zapraszamy rodziców.</p>")
    values.update(overrides)
    return SimpleNamespace(**values)


def test_announcements_use_existing_session_and_parse_school_html():
    html = '''<table class="decorated big center printable margin-top">
      <thead><tr><td>Dzień otwarty</td></tr></thead><tbody>
      <tr class="line0"><td>Dyrekcja</td></tr>
      <tr class="line1"><td>07.10.2026</td></tr>
      <tr class="line0"><td><p>Zapraszamy rodziców.</p></td></tr>
      </tbody></table>'''
    obj, adapter = prepared_connector([(200, html, {"Content-Type": "text/html"})])
    client = Client(token=Token(dzienniks="fixture-d", sdzienniks="fixture-s"))
    client._session.close()
    client._session = obj._messages_session
    obj._client = client
    try:
        items = obj.fetch("announcements")
        assert len(items) == 1
        assert items[0]["title"] == "Dzień otwarty"
        assert items[0]["subtitle"] == "Dyrekcja"
        assert items[0]["when"] == "2026-10-07"
        assert items[0]["details"] == "Zapraszamy rodziców."
        assert items[0]["url"] == c.SECTION_URLS["announcements"]
        assert not items[0]["unread"]
        assert len(adapter.calls) == 1
        request, kwargs = adapter.calls[0]
        assert request.method == "GET"
        assert request.url == "https://synergia.librus.pl/ogloszenia"
        assert kwargs["timeout"] == (5, 20)
        assert kwargs["verify"] is True
    finally:
        obj.close()


def test_announcements_strip_active_html_and_keep_identity_on_body_edit(monkeypatch):
    row = announcement(description='<p>Nowa treść &amp; szczegóły</p><script>secret()</script><img src="https://tracker.invalid">')
    monkeypatch.setattr(c, "get_announcements", lambda client: [row])
    obj, adapter = prepared_connector([])
    first = obj.fetch("announcements")[0]
    row.description = "Zmieniona treść"
    second = obj.fetch("announcements")[0]
    assert first["id"] == second["id"]
    assert first["details"] == "Nowa treść & szczegóły"
    assert second["details"] == "Zmieniona treść"
    assert not adapter.calls


@pytest.mark.parametrize("payload", [None, {}, [announcement(title="")],
                                         [announcement(date="nieznana")], [object()]])
def test_malformed_announcements_fail_instead_of_reporting_no_data(monkeypatch, payload):
    monkeypatch.setattr(c, "get_announcements", lambda client: payload)
    obj, _ = prepared_connector([])
    with pytest.raises(c.ConnectorError, match="format"):
        obj.fetch("announcements")


@pytest.mark.parametrize("html, succeeds", [
    ('<div class="container border-red resizeable center"><div><p>Brak ogłoszeń</p></div></div>', True),
    ("<html>Nieznany format strony</html>", False),
])
def test_empty_announcements_are_distinct_from_parser_failure(html, succeeds):
    obj, _ = prepared_connector([(200, html, {"Content-Type": "text/html"})])
    client = Client(token=Token(dzienniks="fixture-d", sdzienniks="fixture-s"))
    client._session.close()
    client._session = obj._messages_session
    obj._client = client
    try:
        if succeeds:
            assert obj.fetch("announcements") == []
        else:
            with pytest.raises(c.ConnectorError):
                obj.fetch("announcements")
    finally:
        obj.close()


def test_sync_notifies_only_new_and_changed_announcements_and_preserves_cache():
    controller, notices = make_controller()
    try:
        client = connect(controller)
        assert "announcements" in client.calls
        assert not notices
        client.data["announcements"][0]["details"] = "Poprawiona treść ogłoszenia"
        refresh(controller)
        assert len(notices) == 1
        assert "ogłoszenia: 1" in notices[0][1]
        assert "Poprawiona treść" not in repr(notices)
        refresh(controller)
        assert len(notices) == 1
        extra = copy.deepcopy(client.data["announcements"][0])
        extra.update(id="new-announcement", title="Nowe ogłoszenie")
        client.data["announcements"].append(extra)
        refresh(controller)
        assert len(notices) == 2
        previous = copy.deepcopy(controller._tracker.sections["announcements"])
        fetched_at = controller._tracker.updated_at["announcements"]
        client.errors["announcements"] = c.ConnectorError("Nieznany format ogłoszeń.")
        refresh(controller)
        assert controller._mode == "live"
        assert "announcements" in controller._errors
        assert controller._tracker.sections["announcements"] == previous
        assert controller._tracker.updated_at["announcements"] == fetched_at
        assert len(notices) == 2
        restored = SnapshotTracker(controller._storage.load()["snapshot"])
        assert restored.apply("announcements", client.data["announcements"]) == 0
    finally:
        controller.stop()


def test_older_snapshot_establishes_quiet_announcement_baseline():
    tracker = SnapshotTracker()
    for kind, rows in demo_sections().items():
        tracker.apply(kind, rows)
    saved = tracker.export()
    for key in ("sections", "known", "updated_at"):
        saved[key].pop("announcements")
    saved["initialized"].remove("announcements")
    restored = SnapshotTracker(saved)
    assert restored.sections["announcements"] == []
    assert restored.sections["grades"] == tracker.sections["grades"]
    rows = demo_sections()["announcements"]
    assert restored.apply("announcements", rows) == 0
    assert restored.apply("announcements", []) == 0
    assert restored.apply("announcements", rows) == 0
    assert set(restored.sections) == set(KINDS)


def test_open_announcement_uses_fixed_official_section(monkeypatch):
    urls = []
    monkeypatch.setattr("szkolny_panel.core.webbrowser.open", urls.append)
    controller, _ = make_controller()
    try:
        connect(controller)
        row = controller._tracker.sections["announcements"][0]
        row["url"] = "https://untrusted.invalid"
        controller.open_in_librus(row["id"])
        assert urls == ["https://synergia.librus.pl/ogloszenia"]
    finally:
        controller.stop()
