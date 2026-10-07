"""Offline OAuth regression tests using Requests' real cookie extraction.

All credentials, cookies and response bodies below are synthetic. The default
HTTP transport is blocked, so a missed fixture can never contact Librus.
"""

import io
import json
from dataclasses import dataclass, field
from email.message import Message
from types import SimpleNamespace
from urllib.parse import parse_qs, urlsplit

import pytest
import requests
from requests.adapters import BaseAdapter

from szkolny_panel import connector as c


ENTRY = "https://synergia.librus.pl/loguj/portalRodzina"
AUTH = "https://api.librus.pl/OAuth/Authorization?client_id=46"
FORM = AUTH + "&response_type=code&scope=mydata&state=fixture-state-secret"
GO_TO = "/OAuth/Authorization/2FA?client_id=46&ticket=fixture-go-to-secret"
CONTINUE = "https://api.librus.pl" + GO_TO
CALLBACK = ENTRY + "?code=fixture-return-secret&state=fixture-state-secret"
INDEX = "https://synergia.librus.pl/uczen/index"
LOGIN = "fixture-login-never-sent-to-network"
PASSWORD = "fixture-password-never-sent-to-network"


@dataclass
class Exchange:
    method: str
    url: str
    status: int = 200
    body: str = ""
    headers: dict = field(default_factory=dict)
    cookies: tuple = ()
    error: Exception | None = None


class CookieRaw(io.BytesIO):
    def __init__(self, body, headers):
        super().__init__(body)
        self._original_response = SimpleNamespace(msg=headers)

    def release_conn(self):
        pass


class OAuthAdapter(BaseAdapter):
    """A strict scripted server, including real repeated Set-Cookie headers."""

    def __init__(self, exchanges):
        self.exchanges = list(exchanges)
        self.calls = []

    def send(self, request, **kwargs):
        self.calls.append((request, kwargs))
        assert self.exchanges, "Unexpected request after the scripted login ended"
        expected = self.exchanges.pop(0)
        assert (request.method, request.url) == (expected.method, expected.url)
        if expected.error is not None:
            raise expected.error

        headers = Message()
        for name, value in expected.headers.items():
            headers.add_header(name, value)
        for cookie in expected.cookies:
            headers.add_header("Set-Cookie", cookie)

        body = expected.body.encode("utf-8")
        response = requests.Response()
        response.request = request
        response.url = request.url
        response.status_code = expected.status
        response.encoding = "utf-8"
        response.headers.update(expected.headers)
        if expected.cookies:
            response.headers["Set-Cookie"] = ", ".join(expected.cookies)
        response._content = body
        response._content_consumed = True
        response.raw = CookieRaw(body, headers)
        # HTTPAdapter normally fills response.cookies in build_response;
        # Session.send separately extracts the same headers into its own jar.
        requests.cookies.extract_cookies_to_jar(response.cookies, request, response.raw)
        return response

    def close(self):
        pass


@pytest.fixture(autouse=True)
def no_real_network(monkeypatch):
    def forbidden(*args, **kwargs):
        raise AssertionError("Live HTTP is forbidden in login regression tests")

    monkeypatch.setattr(requests.adapters.HTTPAdapter, "send", forbidden)


def install_transport(monkeypatch, exchanges):
    adapter = OAuthAdapter(exchanges)
    session_class = c.GuardedSession
    sessions = []

    def make_session(*args, **kwargs):
        session = session_class(*args, **kwargs)
        session.mount("https://", adapter)
        session.mount("http://", adapter)
        sessions.append(session)
        return session

    monkeypatch.setattr(c, "GuardedSession", make_session)
    return adapter, sessions


def login_prefix(payload=None):
    if payload is None:
        payload = {"status": "ok", "goTo": GO_TO}
    return [
        Exchange("GET", ENTRY, 302, headers={"Location": FORM}, cookies=(
            "oauth_state=fixture-state-secret; Path=/; Secure; HttpOnly",
        )),
        Exchange("GET", FORM, body='<form><input type="password" name="pass"></form>',
                 headers={"Content-Type": "text/html"}, cookies=(
                     "PHPSESSID=fixture-api-secret; Path=/; Secure; HttpOnly",
                 )),
        Exchange("POST", AUTH, body=json.dumps(payload),
                 headers={"Content-Type": "application/json"}),
    ]


def login_success(*, payload=None, account="first", domain="synergia.librus.pl"):
    domain_attr = f"; Domain={domain}" if domain else ""
    return login_prefix(payload) + [
        # /2FA may simply redirect. Its name alone is not an OTP requirement.
        Exchange("GET", CONTINUE, 302, headers={"Location": CALLBACK}),
        Exchange("GET", CALLBACK, 302, headers={"Location": "/uczen/index"}, cookies=(
            f"DZIENNIKSID=fixture-{account}-d; Path=/; Secure; HttpOnly{domain_attr}",
            f"SDZIENNIKSID=fixture-{account}-s; Path=/; Secure; HttpOnly{domain_attr}",
            f"oauth_token=fixture-{account}-oauth; Path=/; Secure; HttpOnly{domain_attr}",
        )),
        Exchange("GET", INDEX, body="<html><h1>Dziennik</h1></html>",
                 headers={"Content-Type": "text/html"}),
    ]


def close_sessions(sessions):
    for session in sessions:
        session.close()


@pytest.mark.parametrize("payload", [
    {"status": "ok", "goTo": GO_TO},
    {"status": "success", "goTo": GO_TO},
    {"status": True, "goTo": GO_TO},
    {"goTo": GO_TO},
])
def test_login_follows_server_oauth_flow_without_anonymous_api_root(monkeypatch, payload):
    adapter, sessions = install_transport(monkeypatch, login_success(payload=payload))
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        obj.connect()
        assert adapter.exchanges == []
        assert len(adapter.calls) == 6
        assert not any(urlsplit(req.url).hostname == "api.librus.pl"
                       and urlsplit(req.url).path in {"", "/"} for req, _ in adapter.calls)
        posts = [req for req, _ in adapter.calls if req.method == "POST"]
        assert len(posts) == 1
        assert posts[0].url == AUTH
        body = posts[0].body
        if isinstance(body, bytes):
            body = body.decode("utf-8")
        assert parse_qs(body) == {"action": ["login"], "login": [LOGIN], "pass": [PASSWORD]}
        assert "PHPSESSID=fixture-api-secret" in posts[0].headers.get("Cookie", "")
        assert "oauth_state=fixture-state-secret" in adapter.calls[4][0].headers.get("Cookie", "")
        assert "DZIENNIKSID=fixture-first-d" in adapter.calls[5][0].headers.get("Cookie", "")
        assert obj._client.token.API_Key == "fixture-first-d:fixture-first-s"
        assert obj._session.allow_login_pages is False
        assert obj._messages_session.cookies is not obj._session.cookies
        assert any(cookie.name == "oauth_token" and cookie.value == "fixture-first-oauth"
                   for cookie in obj._messages_session.cookies)
        assert any(cookie.name == "PHPSESSID" and cookie.value == "fixture-api-secret"
                   for cookie in obj._messages_session.cookies)
        for _req, kwargs in adapter.calls:
            assert kwargs["verify"] is True
            assert kwargs["timeout"] == (5, 20)
    finally:
        obj.close()
        close_sessions(sessions)


def test_form_redirect_preserves_intermediate_api_cookies(monkeypatch):
    exchanges = login_success()
    form_page = AUTH + "&state=fixture-state-secret&step=form"
    original_form = exchanges[1]
    original_form.url = form_page
    exchanges.insert(1, Exchange("GET", FORM, 302,
                                headers={"Location": form_page}, cookies=(
                                    "authorization_step=fixture-step-secret; Path=/; Secure; HttpOnly",
                                )))
    adapter, sessions = install_transport(monkeypatch, exchanges)
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        obj.connect()
        assert adapter.exchanges == []
        post = next(req for req, _ in adapter.calls if req.method == "POST")
        assert "authorization_step=fixture-step-secret" in post.headers.get("Cookie", "")
        assert "PHPSESSID=fixture-api-secret" in post.headers.get("Cookie", "")
    finally:
        obj.close()
        close_sessions(sessions)


@pytest.mark.parametrize("index, stage", [
    (0, "login_start"), (1, "login_form"),
    (2, "login_submit"), (3, "login_finish"),
])
@pytest.mark.parametrize("status", [401, 403])
def test_real_auth_denials_stop_at_the_responsible_stage(monkeypatch, index, stage, status):
    exchanges = login_success()[:index + 1]
    exchanges[-1].status = status
    exchanges[-1].body = PASSWORD + " fixture-body-secret"
    exchanges[-1].headers = {"Content-Type": "text/plain"}
    adapter, sessions = install_transport(monkeypatch, exchanges)
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError) as error:
            obj.connect()
        assert len(adapter.calls) == index + 1
        assert error.value.requires_login
        assert error.value.http_status == status
        assert error.value.stage == stage
        diagnostic = str(error.value)
        for secret in (LOGIN, PASSWORD, "fixture-body-secret", "fixture-state-secret",
                       "fixture-go-to-secret", "fixture-api-secret"):
            assert secret not in diagnostic
        assert str(status) in diagnostic
        assert obj._client is None
        assert obj._messages_session is None
        assert all(len(session.cookies) == 0 for session in sessions)
    finally:
        obj.close()
        close_sessions(sessions)


def test_json_login_error_does_not_follow_even_a_present_go_to(monkeypatch):
    payload = {"status": "error", "goTo": GO_TO,
               "errors": [{"message": PASSWORD + " fixture-server-error-secret"}]}
    adapter, sessions = install_transport(monkeypatch, login_prefix(payload))
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError) as error:
            obj.connect()
        assert len(adapter.calls) == 3
        assert error.value.requires_login
        assert error.value.stage == "login_submit"
        assert "fixture-server-error-secret" not in str(error.value)
        assert PASSWORD not in str(error.value)
    finally:
        obj.close()
        close_sessions(sessions)


@pytest.mark.parametrize("status", [307, 308])
def test_post_redirect_never_replays_credentials(monkeypatch, status):
    exchanges = login_prefix()
    exchanges[-1].status = status
    exchanges[-1].body = ""
    exchanges[-1].headers = {"Location": "https://konto.librus.pl/another-endpoint"}
    adapter, sessions = install_transport(monkeypatch, exchanges)
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError):
            obj.connect()
        assert len(adapter.calls) == 3
        assert [(req.method, req.url) for req, _ in adapter.calls if req.method == "POST"] == [("POST", AUTH)]
    finally:
        obj.close()
        close_sessions(sessions)


@pytest.mark.parametrize("index, stage", [(1, "login_form"), (3, "login_finish")])
def test_real_otp_form_stops_without_sending_a_code(monkeypatch, index, stage):
    exchanges = login_success()[:index + 1]
    exchanges[-1].status = 200
    exchanges[-1].headers = {"Content-Type": "text/html"}
    exchanges[-1].body = '<form><input autocomplete="one-time-code" name="otp"></form>'
    adapter, sessions = install_transport(monkeypatch, exchanges)
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError) as error:
            obj.connect()
        assert len(adapter.calls) == index + 1
        assert error.value.requires_login
        assert error.value.stage == stage
        assert "2FA" in str(error.value)
    finally:
        obj.close()
        close_sessions(sessions)


@pytest.mark.parametrize("where", ["entry", "go_to"])
@pytest.mark.parametrize("target", [
    "https://api.librus.pl.evil.invalid/collect?secret=fixture-redirect-secret",
    "http://api.librus.pl/collect?secret=fixture-redirect-secret",
    "https://user:fixture-redirect-secret@api.librus.pl/collect",
])
def test_untrusted_login_redirect_is_rejected_before_transport(monkeypatch, where, target):
    if where == "entry":
        exchanges = [Exchange("GET", ENTRY, 302, headers={"Location": target})]
    else:
        exchanges = login_prefix({"status": "ok", "goTo": target})
    adapter, sessions = install_transport(monkeypatch, exchanges)
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError) as error:
            obj.connect()
        assert len(adapter.calls) == (1 if where == "entry" else 3)
        assert all(req.url != target for req, _ in adapter.calls)
        assert "fixture-redirect-secret" not in str(error.value)
    finally:
        obj.close()
        close_sessions(sessions)


@pytest.mark.parametrize("payload", [
    {"status": "ok"}, {"status": "ok", "goTo": None},
    {"status": "ok", "goTo": {}}, {"status": "ok", "goTo": ""},
])
def test_success_without_a_redirect_is_not_authenticated(monkeypatch, payload):
    adapter, sessions = install_transport(monkeypatch, login_prefix(payload))
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError):
            obj.connect()
        assert len(adapter.calls) == 3
        assert obj._client is None
    finally:
        obj.close()
        close_sessions(sessions)


@pytest.mark.parametrize("domain", [None, "synergia.librus.pl", ".synergia.librus.pl", ".librus.pl"])
def test_synergia_session_cookie_domain_variants(monkeypatch, domain):
    adapter, sessions = install_transport(monkeypatch, login_success(domain=domain))
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        obj.connect()
        assert obj._client.token.API_Key == "fixture-first-d:fixture-first-s"
        assert adapter.exchanges == []
    finally:
        obj.close()
        close_sessions(sessions)


@pytest.mark.parametrize("bad_cookies", ["missing", "api_host_only", "wrong_path"])
def test_missing_or_unusable_session_cookies_do_not_complete_login(monkeypatch, bad_cookies):
    exchanges = login_success()
    exchanges[4].cookies = ()
    if bad_cookies == "api_host_only":
        exchanges[3].cookies = (
            "DZIENNIKSID=fixture-wrong-d; Path=/; Secure; HttpOnly",
            "SDZIENNIKSID=fixture-wrong-s; Path=/; Secure; HttpOnly",
        )
    elif bad_cookies == "wrong_path":
        exchanges[4].cookies = (
            "DZIENNIKSID=fixture-wrong-d; Path=/unrelated/; Secure; HttpOnly",
            "SDZIENNIKSID=fixture-wrong-s; Path=/unrelated/; Secure; HttpOnly",
        )
    adapter, sessions = install_transport(monkeypatch, exchanges)
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError) as error:
            obj.connect()
        assert error.value.stage == "login_session"
        assert obj._client is None
        assert len(adapter.calls) == 6
    finally:
        obj.close()
        close_sessions(sessions)


def test_api_cookie_with_same_name_does_not_replace_synergia_cookie(monkeypatch):
    exchanges = login_success()
    exchanges[3].cookies = (
        "DZIENNIKSID=fixture-wrong-api-d; Path=/; Secure; HttpOnly",
        "SDZIENNIKSID=fixture-wrong-api-s; Path=/; Secure; HttpOnly",
    )
    _adapter, sessions = install_transport(monkeypatch, exchanges)
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        obj.connect()
        assert obj._client.token.API_Key == "fixture-first-d:fixture-first-s"
    finally:
        obj.close()
        close_sessions(sessions)


def test_account_sessions_are_distinct_and_message_cookies_survive_apix_reads(monkeypatch):
    exchanges = login_success(account="first") + login_success(account="second")
    exchanges.append(Exchange("GET", INDEX, body="<html>Dziennik</html>",
                              headers={"Content-Type": "text/html"}))
    adapter, sessions = install_transport(monkeypatch, exchanges)
    first = c.LibrusConnector(LOGIN, PASSWORD)
    second = c.LibrusConnector("fixture-second-login", "fixture-second-password")
    try:
        first.connect()
        first.connect()
        second.connect()
        assert len(adapter.calls) == 12
        assert first._client.token is not second._client.token
        assert first._client.cookies is not second._client.cookies
        assert first._session.cookies is not second._session.cookies
        assert "Cookie" not in adapter.calls[6][0].headers
        assert first._client.token.API_Key == "fixture-first-d:fixture-first-s"
        assert second._client.token.API_Key == "fixture-second-d:fixture-second-s"
        before = [(cookie.name, cookie.value, cookie.domain, cookie.path)
                  for cookie in first._messages_session.cookies]
        first._client.get(INDEX)
        after = [(cookie.name, cookie.value, cookie.domain, cookie.path)
                 for cookie in first._messages_session.cookies]
        assert after == before
        assert any(name == "oauth_token" and value == "fixture-first-oauth"
                   for name, value, _domain, _path in after)
        first._session.cookies.clear()
        assert len(first._messages_session.cookies) == len(before)
        assert any(cookie.value == "fixture-second-d" for cookie in second._session.cookies)
        assert adapter.exchanges == []
    finally:
        first.close()
        second.close()
        close_sessions(sessions)
    assert first._password == ""
    assert second._password == ""


def test_redirect_loop_is_bounded_without_reposting_credentials(monkeypatch):
    exchanges = login_prefix()
    exchanges.extend(Exchange("GET", CONTINUE, 302, headers={"Location": CONTINUE})
                     for _ in range(11))
    adapter, sessions = install_transport(monkeypatch, exchanges)
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError) as error:
            obj.connect()
        assert error.value.requires_login
        assert len(adapter.calls) <= 13
        assert len([req for req, _ in adapter.calls if req.method == "POST"]) == 1
        assert obj._client is None
    finally:
        obj.close()
        close_sessions(sessions)


@pytest.mark.parametrize("status, rate", [(429, True), (503, False)])
def test_rate_limit_or_maintenance_stops_before_credentials(monkeypatch, status, rate):
    adapter, sessions = install_transport(monkeypatch, [
        Exchange("GET", ENTRY, status, body="fixture-private-response"),
    ])
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError) as error:
            obj.connect()
        assert error.value.http_status == status
        assert error.value.stage == "login_start"
        assert error.value.rate_limited is rate
        assert len(adapter.calls) == 1
        assert "fixture-private-response" not in str(error.value)
    finally:
        obj.close()
        close_sessions(sessions)


def test_timeout_reports_stage_without_transport_exception_secrets(monkeypatch):
    adapter, sessions = install_transport(monkeypatch, [
        Exchange("GET", ENTRY, error=requests.Timeout(PASSWORD + " fixture-exception-secret")),
    ])
    obj = c.LibrusConnector(LOGIN, PASSWORD)
    try:
        with pytest.raises(c.ConnectorError) as error:
            obj.connect()
        assert error.value.stage == "login_start"
        assert error.value.http_status is None
        assert len(adapter.calls) == 1
        assert PASSWORD not in str(error.value)
        assert "fixture-exception-secret" not in str(error.value)
    finally:
        obj.close()
        close_sessions(sessions)
