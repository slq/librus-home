"""Real Requests redirects and cookies; synthetic exchanges, no live account."""

import json

import pytest
import requests

from szkolny_panel import connector as c
from test_connector import message
from test_login import Exchange, OAuthAdapter


@pytest.fixture(autouse=True)
def no_live_http(monkeypatch):
    def forbidden(*args, **kwargs):
        raise AssertionError("Live HTTP is forbidden in bootstrap tests")

    monkeypatch.setattr(requests.adapters.HTTPAdapter, "send", forbidden)


def connector(exchanges):
    obj = c.LibrusConnector("fixture-login", "fixture-password")
    obj._client = object()
    session = c.GuardedSession()
    adapter = OAuthAdapter(exchanges)
    session.mount("https://", adapter)
    session.mount("http://", adapter)
    session.cookies.set("oauth_token", "fixture-oauth", domain="synergia.librus.pl", secure=True)
    obj._messages_session = session
    return obj, adapter


@pytest.mark.parametrize("port", ["", ":80"])
def test_message_bootstrap_upgrades_known_http_redirect_before_preparing_cookies(port):
    suffix = "/MultiDomainLogon?token=fixture%2Btoken%2F%3D&return=%2Finbox"
    secure_target = "https://wiadomosci.librus.pl" + suffix
    obj, adapter = connector([
        Exchange("GET", c.SECTION_URLS["messages"], 302, headers={
            "Location": "http://wiadomosci.librus.pl" + port + suffix,
        }, cookies=("bootstrap=fixture-secure; Domain=.librus.pl; Path=/; Secure; HttpOnly",)),
        Exchange("GET", secure_target, 302, headers={"Location": "/inbox"}, cookies=(
            "message_cookie=fixture-session; Path=/; Secure; HttpOnly",
        )),
        Exchange("GET", "https://wiadomosci.librus.pl/inbox", body="<html>Skrzynka</html>",
                 headers={"Content-Type": "text/html"}),
        Exchange("GET", c.MESSAGE_API + "?page=1&limit=50",
                 body=json.dumps({"data": [message(1)], "total": 1}),
                 headers={"Content-Type": "application/json"}),
    ])
    try:
        items = obj.fetch("messages")
        assert items[0]["id"] == "messages:new:uuid-1"
        assert obj._messages_ready
        assert len(adapter.calls) == 4
        assert not adapter.exchanges
        assert "bootstrap=fixture-secure" in adapter.calls[1][0].headers.get("Cookie", "")
        assert "message_cookie=fixture-session" in adapter.calls[-1][0].headers.get("Cookie", "")
        for request, kwargs in adapter.calls:
            assert request.method == "GET"
            assert request.url.startswith("https://")
            assert kwargs["verify"] is True
            assert "fixture-password" not in (request.body or "")
    finally:
        obj.close()


@pytest.mark.parametrize("target", [
    "http://api.librus.pl/collect", "http://other.librus.pl/collect",
    "http://wiadomosci.librus.pl.evil.invalid/collect",
    "http://wiadomosci.librus.pl:444/collect",
    "http://user:fixture-private@wiadomosci.librus.pl/collect",
])
def test_bootstrap_does_not_relax_other_redirect_restrictions(target):
    obj, adapter = connector([
        Exchange("GET", c.SECTION_URLS["messages"], 302, headers={"Location": target}),
    ])
    try:
        with pytest.raises(c.ConnectorError):
            obj.fetch("messages")
        assert len(adapter.calls) == 1
        assert not obj._messages_ready
    finally:
        obj.close()


def test_initial_http_message_request_remains_blocked():
    obj, adapter = connector([])
    obj._messages_session.stage = "messages"
    try:
        with pytest.raises(c.ConnectorError):
            obj._messages_session.get("http://wiadomosci.librus.pl/inbox")
        assert not adapter.calls
    finally:
        obj.close()


def test_http_mailbox_redirect_outside_bootstrap_remains_blocked():
    obj, adapter = connector([
        Exchange("GET", c.MESSAGE_API, 302,
                 headers={"Location": "http://wiadomosci.librus.pl/inbox"}),
    ])
    try:
        with pytest.raises(c.ConnectorError):
            obj._messages_session.get(c.MESSAGE_API)
        assert len(adapter.calls) == 1
    finally:
        obj.close()


def test_upgraded_bootstrap_redirect_loop_still_has_a_limit():
    target = "https://wiadomosci.librus.pl/MultiDomainLogon?token=fixture-loop"
    obj, adapter = connector([
        Exchange("GET", c.SECTION_URLS["messages"], 302,
                 headers={"Location": target.replace("https://", "http://", 1)}),
        *[Exchange("GET", target, 302,
                   headers={"Location": target.replace("https://", "http://", 1)}) for _ in range(11)],
    ])
    try:
        with pytest.raises(c.ConnectorError) as caught:
            obj.fetch("messages")
        assert caught.value.requires_login
        assert "fixture-loop" not in str(caught.value)
        assert len(adapter.calls) <= 11
        assert all(request.url.startswith("https://") for request, _ in adapter.calls)
        assert not obj._messages_ready
    finally:
        obj.close()


def test_failed_tls_on_upgraded_target_never_falls_back_to_http():
    obj, adapter = connector([
        Exchange("GET", c.SECTION_URLS["messages"], 302,
                 headers={"Location": "http://wiadomosci.librus.pl/MultiDomainLogon"}),
        Exchange("GET", "https://wiadomosci.librus.pl/MultiDomainLogon",
                 error=requests.exceptions.SSLError("fixture-private-certificate-error")),
    ])
    try:
        with pytest.raises(c.ConnectorError) as caught:
            obj.fetch("messages")
        assert "fixture-private" not in str(caught.value)
        assert len(adapter.calls) == 2
        assert all(request.url.startswith("https://") for request, _ in adapter.calls)
        assert not obj._messages_ready
    finally:
        obj.close()


def test_post_redirect_to_http_is_not_upgraded_or_replayed():
    obj, adapter = connector([
        Exchange("POST", c.SECTION_URLS["messages"], 307,
                 headers={"Location": "http://wiadomosci.librus.pl/collect"}),
    ])
    obj._messages_session.stage = "messages"
    try:
        with pytest.raises(c.ConnectorError):
            obj._messages_session.post(c.SECTION_URLS["messages"], data={"test": "fixture"})
        assert len(adapter.calls) == 1
    finally:
        obj.close()
