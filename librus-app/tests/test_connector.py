"""Account-free fixtures for the boundaries that protect a local sync."""

import base64
import json
from datetime import date
from types import SimpleNamespace
from urllib.parse import parse_qs, urlsplit

import pytest
import requests
from requests.adapters import BaseAdapter

from szkolny_panel import connector as c


class FixtureAdapter(BaseAdapter):
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def send(self, request, **kwargs):
        self.calls.append((request, kwargs))
        fixture = self.responses.pop(0)
        if isinstance(fixture, Exception):
            raise fixture
        status, body, headers = fixture
        response = requests.Response()
        response.status_code = status
        response.url = request.url
        response.request = request
        response.headers.update(headers)
        response._content = body.encode("utf-8")
        response.encoding = "utf-8"
        return response

    def close(self):
        pass


def json_response(value):
    return 200, json.dumps(value), {"Content-Type": "application/json"}


def message(index):
    return {
        "messageId": f"uuid-{index}", "topic": f"Temat {index}",
        "senderName": "Anna Kowalska", "sendDate": "2026-10-07 08:15:00",
        "readDate": None, "content": "THIS MUST NEVER BE DECODED ON SYNC",
        "isAnyFileAttached": False,
    }


def prepared_connector(responses):
    obj = c.LibrusConnector("test-login", "not-a-real-password")
    obj._client = object()
    session = c.GuardedSession()
    adapter = FixtureAdapter(responses)
    session.mount("https://", adapter)
    obj._messages_session = session
    obj._messages_ready = True
    return obj, adapter


def test_message_sync_pages_to_200_without_reading_any_body():
    responses = [json_response({"data": [message(i) for i in range(offset, offset + 50)],
                               "total": 250}) for offset in range(0, 200, 50)]
    obj, adapter = prepared_connector(responses)
    items = obj.fetch("messages")
    assert len(items) == 200
    assert len(adapter.calls) == 4
    for page, (request, kwargs) in enumerate(adapter.calls, start=1):
        assert request.method == "GET"
        assert urlsplit(request.url).path == "/api/inbox/messages"
        assert parse_qs(urlsplit(request.url).query) == {"page": [str(page)], "limit": ["50"]}
        assert kwargs["timeout"] == (5, 20)
        assert kwargs["verify"] is True
        assert request.headers["Accept-Encoding"] == "gzip, deflate"
    assert items[0]["id"] == "messages:new:uuid-0"
    assert items[0]["when"] == "2026-10-07T08:15:00"
    assert items[0]["unread"] is True
    assert "MUST NEVER" not in items[0]["details"]


def test_explicit_read_decodes_and_strips_active_html():
    html = '<p>Wycieczka &amp; zgody</p><script>sendSecret()</script><style>x{}</style><img src="https://tracker.invalid/p"><p>Proszę odpisać.</p>'
    obj, adapter = prepared_connector([json_response({"data": {
        "topic": "Wycieczka &amp; zgody", "Message": base64.b64encode(html.encode()).decode(),
    }})])
    detail = obj.read_message("messages:new:uuid-1")
    assert detail == {"title": "Wycieczka & zgody", "text": "Wycieczka & zgody\nProszę odpisać."}
    assert len(adapter.calls) == 1
    assert urlsplit(adapter.calls[0][0].url).path == "/api/inbox/messages/uuid-1"


@pytest.mark.parametrize("fixture, login, rate", [
    ((200, '<html><form><input type="password"></form></html>', {"Content-Type": "text/html"}), True, False),
    ((200, '<html><input autocomplete="one-time-code"></html>', {"Content-Type": "text/html"}), True, False),
    ((401, "secret body", {}), True, False),
    ((403, "secret body", {}), True, False),
    ((429, "secret body", {}), False, True),
    ((503, "secret body", {}), False, False),
    (requests.Timeout("secret password in a hypothetical exception"), False, False),
])
def test_transport_reports_sanitized_auth_rate_and_timeout_errors(fixture, login, rate):
    obj, _adapter = prepared_connector([fixture])
    with pytest.raises(c.ConnectorError) as error:
        obj.fetch("messages")
    assert error.value.requires_login is login
    assert error.value.rate_limited is rate
    assert "secret" not in str(error.value)
    assert "https://" not in str(error.value)


@pytest.mark.parametrize("target", [
    "http://api.librus.pl/data", "https://api.librus.pl:444/data",
    "https://user:password@api.librus.pl/data", "https://api.librus.pl.evil.test/data",
    "https://127.0.0.1/data", "https://[::1]/data", "https://192.168.0.10/data",
    "https://other.librus.pl/data",
])
def test_url_and_redirect_policy_blocks_before_second_request(target):
    session = c.GuardedSession()
    adapter = FixtureAdapter([(302, "", {"Location": target})])
    session.mount("https://", adapter)
    with pytest.raises(c.ConnectorError):
        session.get("https://api.librus.pl/start")
    assert len(adapter.calls) == 1
    assert "password" not in str(target) or "password" not in adapter.calls[0][0].url


def test_forbidden_initial_host_never_reaches_transport():
    session = c.GuardedSession()
    adapter = FixtureAdapter([])
    session.mount("https://", adapter)
    with pytest.raises(c.ConnectorError):
        session.get("https://example.invalid")
    assert adapter.calls == []


@pytest.mark.parametrize("target, reason, host", [
    ("https://other.librus.pl/MultiDomainLogon?token=fixture-private-token", "serwer nie jest na liście", "other.librus.pl"),
    ("http://api.librus.pl/MultiDomainLogon?token=fixture-private-token", "wymagane HTTPS", "api.librus.pl"),
    ("https://wiadomosci.librus.pl:444/?token=fixture-private-token", "niedozwolony port", "wiadomosci.librus.pl"),
    ("https://user:fixture-private-token@wiadomosci.librus.pl/", "dane logowania w adresie", "wiadomosci.librus.pl"),
    ("https://fixture-private-token.invalid/path", "serwer nie jest na liście", ""),
    ("https://wiadomosci.librus.pl:bad/?token=fixture-private-token", "nieprawidłowy adres", ""),
])
def test_blocked_message_redirect_explains_reason_without_session_data(target, reason, host):
    session = c.GuardedSession()
    session.stage = "messages"
    adapter = FixtureAdapter([(302, "", {"Location": target})])
    session.mount("https://", adapter)
    with pytest.raises(c.ConnectorError) as caught:
        session.get(c.SECTION_URLS["messages"])
    diagnostic = str(caught.value)
    assert reason in diagnostic
    if host:
        assert host in diagnostic
    assert "fixture-private-token" not in diagnostic
    assert "MultiDomainLogon" not in diagnostic
    assert "https://" not in diagnostic
    assert "http://" not in diagnostic
    assert caught.value.stage == "messages"
    assert len(adapter.calls) == 1


@pytest.mark.parametrize("payload", [
    {}, {"data": None, "total": 0}, {"data": [], "total": 10},
    {"data": [{}], "total": 1}, {"data": [], "total": "0"},
])
def test_missing_message_fields_are_an_error_not_an_empty_success(payload):
    obj, _adapter = prepared_connector([json_response(payload)])
    with pytest.raises(c.ConnectorError):
        obj.fetch("messages")


def test_non_login_html_instead_of_json_is_reported_as_parse_failure():
    obj, _adapter = prepared_connector([(200, "<html>Changed API</html>", {"Content-Type": "text/html"})])
    with pytest.raises(c.ConnectorError) as error:
        obj.fetch("messages")
    assert not error.value.requires_login


def test_missing_and_malformed_message_bodies_fail_explicitly():
    for body in ({"data": {"topic": "T"}}, {"data": {"topic": "T", "Message": "!!!!"}}):
        obj, _adapter = prepared_connector([json_response(body)])
        with pytest.raises(c.ConnectorError):
            obj.read_message("messages:new:uuid-1")


def test_academic_mapping_keeps_identity_when_a_grade_changes(monkeypatch):
    grade = SimpleNamespace(date="07.10.2026", grade="4+", desc="Kartkówka", href="/oceny/123",
                            semester=1, teacher="Nauczyciel", category="Kartkówka", weight=2, counts=True)
    monkeypatch.setattr(c, "get_grades", lambda client, sort_by: ([{"Matematyka": [grade]}, {}], {}, [{}, {}]))
    obj, _adapter = prepared_connector([])
    first = obj.fetch("grades")[0]
    grade.grade = "5-"
    second = obj.fetch("grades")[0]
    assert first["id"] == second["id"]
    assert first["title"] == "Matematyka: 4+"
    assert second["title"] == "Matematyka: 5-"
    assert first["when"] == "2026-10-07"
    assert "4.5" not in first["details"]
    assert first["url"] == c.SECTION_URLS["grades"]


def test_schedule_spans_year_boundary_and_timetable_keeps_cancellations(monkeypatch):
    class FixedDate(date):
        @classmethod
        def today(cls):
            return cls(2026, 12, 30)

    monkeypatch.setattr(c, "date", FixedDate)
    months = []

    def schedule(client, month, year):
        months.append((month, year))
        return {5: [SimpleNamespace(title="Sprawdzian", subject="Biologia", data={"Opis": "Rozdział 2"},
                                   href=f"event/{month}", number=2, hour="unknown")]}

    weeks = []

    def timetable(client, monday):
        weeks.append(monday.date())
        return [[SimpleNamespace(subject="", info={"Lekcja odwołana": ""}, date=str(monday.date()),
                                 date_from="08:00", date_to="08:45", number=1, teacher_and_classroom="")]]

    monkeypatch.setattr(c, "get_schedule", schedule)
    monkeypatch.setattr(c, "get_timetable", timetable)
    obj, _adapter = prepared_connector([])
    schedule_items = obj.fetch("schedule")
    assert months == [("12", "2026"), ("1", "2027")]
    assert [x["when"] for x in schedule_items] == ["2026-12-05", "2027-01-05"]
    lesson_items = obj.fetch("timetable")
    assert weeks == [date(2026, 12, 28), date(2027, 1, 4)]
    assert len(lesson_items) == 2
    assert "Lekcja odwołana" in lesson_items[0]["details"]
    assert lesson_items[0]["when"] == "2026-12-28T08:00:00"


def test_message_bootstrap_reuses_existing_oauth_without_login(monkeypatch):
    obj, _adapter = prepared_connector([])
    obj._messages_ready = False
    session = obj._messages_session
    session.cookies.set("oauth_token", "fixture", domain="synergia.librus.pl")
    calls = []

    def get(url):
        calls.append(url)
        session.cookies.set("message_cookie", "fixture", domain="wiadomosci.librus.pl")
        return requests.Response()

    monkeypatch.setattr(session, "get", get)
    obj._bootstrap_messages()
    obj._bootstrap_messages()
    assert calls == [c.SECTION_URLS["messages"]]
