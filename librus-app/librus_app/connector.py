"""Read-only Librus adapter for a local desktop application.

Academic data uses librus-apix 1.5.3. The OAuth login, new message-list mapping
and MultiDomainLogon bootstrap follow the publicly documented approach in
emsi/librus_pyapi 0.2.0 (AGPL-3.0). No live-account compatibility is assumed.
Fetching a message body is deliberately separate from synchronization.
"""

from __future__ import annotations

import base64
import binascii
import hashlib
import json
import re
from collections.abc import Mapping
from datetime import date, datetime, timedelta
from html.parser import HTMLParser
from urllib.parse import quote, urljoin, urlsplit

import requests
from librus_apix.announcements import get_announcements
from librus_apix.attendance import get_attendance
from librus_apix.client import Client, Token
from librus_apix.exceptions import AuthorizationError, TokenError, TokenKeyError
from librus_apix.grades import get_grades
from librus_apix.homework import get_homework, homework_detail
from librus_apix.schedule import get_schedule
from librus_apix.timetable import get_timetable


ALLOWED_HOSTS = frozenset({
    "api.librus.pl", "synergia.librus.pl", "wiadomosci.librus.pl",
    "portal.librus.pl", "konto.librus.pl",
})
SECTION_URLS = {
    "grades": "https://synergia.librus.pl/przegladaj_oceny/uczen",
    "messages": "https://synergia.librus.pl/wiadomosci3",
    "announcements": "https://synergia.librus.pl/ogloszenia",
    "schedule": "https://synergia.librus.pl/terminarz/",
    "attendance": "https://synergia.librus.pl/przegladaj_nb/uczen",
    "timetable": "https://synergia.librus.pl/przegladaj_plan_lekcji",
    "homework": "https://synergia.librus.pl/moje_zadania",
}
MESSAGE_API = "https://wiadomosci.librus.pl/api/inbox/messages"
LOGIN_ENTRY_URL = "https://synergia.librus.pl/loguj/portalRodzina"
AUTHORIZATION_URL = "https://api.librus.pl/OAuth/Authorization?client_id=46"
OAUTH_RETURN_BASE = AUTHORIZATION_URL + "&response_type=code&scope=mydata"
MAX_AUTH_REDIRECTS = 10
_REDIRECT_STATUSES = {301, 302, 303, 307, 308}
_STAGE_LABELS = {
    "data": "pobieranie danych",
    "login_start": "inicjalizacja logowania",
    "login_form": "otwarcie formularza logowania",
    "login_submit": "sprawdzenie loginu i hasła",
    "login_finish": "zakończenie logowania",
    "login_session": "utworzenie sesji Synergii",
    "messages": "otwarcie skrzynki wiadomości",
}


class ConnectorError(Exception):
    def __init__(self, message: str, *, requires_login: bool = False,
                 rate_limited: bool = False, stage: str = "",
                 http_status: int | None = None):
        # Diagnostics contain only fixed labels and the numeric HTTP status.
        # Never include a response body, request URL, credentials or cookies.
        self.stage = stage if stage in _STAGE_LABELS else ""
        self.http_status = http_status
        diagnostic = []
        if isinstance(http_status, int) and 100 <= http_status <= 599:
            diagnostic.append(f"HTTP {http_status}")
        if self.stage:
            diagnostic.append("etap: " + _STAGE_LABELS[self.stage])
        suffix = " (" + "; ".join(diagnostic) + ")." if diagnostic else ""
        super().__init__(message.rstrip(".") + suffix if suffix else message)
        self.requires_login = requires_login
        self.rate_limited = rate_limited


class _PlainHTML(HTMLParser):
    _hidden = {"script", "style", "noscript", "svg", "iframe", "object", "template"}
    _blocks = {"p", "div", "br", "li", "tr", "h1", "h2", "h3", "h4", "hr"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.hidden_depth = 0

    def handle_starttag(self, tag, attrs):
        if tag in self._hidden:
            self.hidden_depth += 1
        if not self.hidden_depth and tag in self._blocks:
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in self._hidden and self.hidden_depth:
            self.hidden_depth -= 1
        if not self.hidden_depth and tag in self._blocks:
            self.parts.append("\n")

    def handle_data(self, data):
        if not self.hidden_depth:
            self.parts.append(data)


def plain_text(value) -> str:
    """Turn server HTML into inert text, without loading links or images."""
    if value is None:
        return ""
    parser = _PlainHTML()
    parser.feed(str(value))
    parser.close()
    lines = [re.sub(r"[\t\r \xa0]+", " ", s).strip()
             for s in "".join(parser.parts).split("\n")]
    return "\n".join(s for s in lines if s)


class _LoginSignals(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.password = False
        self.otp = False
        self.denied_heading = False
        self._heading = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "input":
            self.password |= str(attrs.get("type", "")).lower() == "password"
            name = str(attrs.get("name", "")).lower()
            self.otp |= attrs.get("autocomplete") == "one-time-code" or name in {
                "otp", "otp_code", "totp", "totp_code", "verification_code", "2fa_code",
            }
        if tag in {"h1", "h2", "h3"}:
            self._heading = True

    def handle_endtag(self, tag):
        if tag in {"h1", "h2", "h3"}:
            self._heading = False

    def handle_data(self, data):
        if self._heading and "brak dostępu" in data.casefold():
            self.denied_heading = True


def _validate_url(url: str) -> None:
    host = ""
    try:
        parts = urlsplit(url)
        port = parts.port
        host = parts.hostname or ""
        if parts.scheme != "https":
            reason = "wymagane HTTPS"
        elif host not in ALLOWED_HOSTS:
            reason = "serwer nie jest na liście"
        elif port not in (None, 443):
            reason = "niedozwolony port"
        elif parts.username is not None or parts.password is not None:
            reason = "dane logowania w adresie"
        else:
            return
    except (ValueError, TypeError):
        reason = "nieprawidłowy adres"
    # Show only a syntactically valid Librus hostname, never a full URL,
    # path, query, fragment, userinfo, or arbitrary external server name.
    diagnostic = "Powód: " + reason
    if len(host) <= 253 and re.fullmatch(r"(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*librus\.pl", host):
        diagnostic += "; serwer: " + host
    raise ConnectorError("Zablokowano przekierowanie poza dozwolone serwisy Librusa. " + diagnostic + ".")


class GuardedSession(requests.Session):
    """Apply timeouts and the same host policy to original and redirect requests."""

    def __init__(self, *, allow_login_pages: bool = False):
        super().__init__()
        self.allow_login_pages = allow_login_pages
        self.stage = "data"
        self.timeout = (5, 20)
        self.max_redirects = 10
        # Avoid implicit .netrc credentials and environment-provided proxies.
        self.trust_env = False

    def failure(self, message: str, *, requires_login: bool = False,
                rate_limited: bool = False, http_status: int | None = None):
        return ConnectorError(message, requires_login=requires_login,
                              rate_limited=rate_limited, stage=self.stage,
                              http_status=http_status)

    def get_redirect_target(self, response):
        target = super().get_redirect_target(response)
        if (not target or self.stage != "messages" or response.request is None
                or response.request.method != "GET"):
            return target
        try:
            _validate_url(response.url)
            destination = urlsplit(urljoin(response.url, target))
            if (destination.scheme == "http" and destination.hostname == "wiadomosci.librus.pl"
                    and destination.port in (None, 80)
                    and destination.username is None and destination.password is None):
                # Synergia's mailbox bootstrap may advertise HTTP. Upgrade the
                # redirect before Requests builds it so Secure cookies are also
                # selected for HTTPS. Never send the advertised HTTP request.
                return destination._replace(scheme="https", netloc="wiadomosci.librus.pl").geturl()
        except (ConnectorError, ValueError, TypeError):
            pass  # send() still rejects the original invalid destination.
        return target

    def send(self, request, **kwargs):
        try:
            _validate_url(request.url)
        except ConnectorError as exc:
            raise self.failure(str(exc)) from None
        kwargs["timeout"] = kwargs.get("timeout") or self.timeout
        kwargs["verify"] = True
        request.headers["Accept-Encoding"] = "gzip, deflate"
        try:
            response = super().send(request, **kwargs)
        except requests.Timeout:
            raise self.failure("Librus nie odpowiedział w wyznaczonym czasie. Spróbuj ponownie później.") from None
        except requests.TooManyRedirects:
            raise self.failure("Nie udało się zakończyć przekierowania logowania.", requires_login=True) from None
        except requests.RequestException:
            raise self.failure("Nie udało się połączyć z Librusem. Sprawdź połączenie z internetem.") from None

        if response.status_code == 429:
            raise self.failure("Librus ograniczył liczbę zapytań. Odczekaj przed kolejną synchronizacją.", rate_limited=True, http_status=response.status_code)
        if response.status_code in (401, 403):
            raise self.failure("Librus odrzucił żądanie. Sprawdź możliwość logowania na oficjalnej stronie.", requires_login=True, http_status=response.status_code)
        if response.status_code >= 500:
            raise self.failure("Librus jest chwilowo niedostępny lub prowadzi prace serwisowe.", http_status=response.status_code)
        if response.status_code >= 400:
            raise self.failure("Librus nie udostępnił tej sekcji danych.", http_status=response.status_code)

        content_type = response.headers.get("Content-Type", "").lower()
        if "html" in content_type or response.content.lstrip().startswith(b"<"):
            signals = _LoginSignals()
            signals.feed(response.text)
            if signals.otp:
                raise self.failure("Konto wymaga kodu 2FA. Ta wersja panelu nie obsługuje takiego logowania; użyj oficjalnego Librusa.", requires_login=True, http_status=response.status_code)
            if not self.allow_login_pages and (signals.password or signals.denied_heading):
                raise self.failure("Sesja Librusa wygasła lub konto wymaga ponownego logowania.", requires_login=True, http_status=response.status_code)
        return response


def _login_target(session: GuardedSession, base: str, target) -> str:
    if not isinstance(target, str) or not target.strip() or len(target) > 8192:
        raise session.failure("Librus nie podał poprawnego przekierowania logowania.", requires_login=True)
    try:
        destination = urljoin(base, target)
        _validate_url(destination)
    except ConnectorError as exc:
        raise session.failure(str(exc), requires_login=True) from None
    except ValueError:
        raise session.failure("Zablokowano przekierowanie poza dozwolone serwisy Librusa.", requires_login=True) from None
    return destination


def _follow_login_redirects(session: GuardedSession, url: str):
    """Follow GET redirects explicitly; preserve cookies from every response."""
    for _ in range(MAX_AUTH_REDIRECTS):
        response = session.get(url, allow_redirects=False)
        if response.status_code not in _REDIRECT_STATUSES:
            if not 200 <= response.status_code < 300:
                raise session.failure("Librus zwrócił nieoczekiwaną odpowiedź logowania.", requires_login=True, http_status=response.status_code)
            return response
        url = _login_target(session, response.url, response.headers.get("Location"))
    raise session.failure("Librus przekroczył limit przekierowań logowania. Spróbuj ponownie później.", requires_login=True)


def _synergia_cookie(session: GuardedSession, name: str) -> str:
    # apix needs session-wide SID cookies. Ignore same-named cookies for the
    # API, other domains, expired values and narrower paths.
    for domain in ("synergia.librus.pl", "librus.pl"):
        values = {
            cookie.value for cookie in session.cookies
            if cookie.name == name and cookie.domain.lstrip(".") == domain
            and cookie.path in ("", "/") and cookie.value and not cookie.is_expired()
        }
        if len(values) == 1:
            return values.pop()
        if values:
            return ""
    return ""


def _authenticate(session: GuardedSession, login: str, password: str) -> Token:
    """Synergia OAuth bootstrap, without apix's anonymous API-root probe.

    The credentials are posted exactly once to the fixed authorization URL.
    Later navigation follows only validated GET targets returned by Librus.
    """
    session.stage = "login_start"
    response = session.get(LOGIN_ENTRY_URL, allow_redirects=False)
    if response.status_code not in _REDIRECT_STATUSES:
        raise session.failure("Nie udało się otworzyć logowania Synergii. Sprawdź logowanie na oficjalnej stronie.", requires_login=True, http_status=response.status_code)
    authorization_form = _login_target(session, response.url, response.headers.get("Location"))

    session.stage = "login_form"
    _follow_login_redirects(session, authorization_form)

    session.stage = "login_submit"
    response = session.post(
        AUTHORIZATION_URL,
        data={"action": "login", "login": login, "pass": password},
        headers={
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "Accept-Language": "pl-PL,pl;q=0.9,en;q=0.5",
            "Origin": "https://api.librus.pl",
            "Referer": AUTHORIZATION_URL,
            "X-Requested-With": "XMLHttpRequest",
        },
        allow_redirects=False,
    )
    if not 200 <= response.status_code < 300:
        raise session.failure("Librus zwrócił nieoczekiwane przekierowanie po wysłaniu danych logowania.", requires_login=True, http_status=response.status_code)
    try:
        payload = response.json()
    except ValueError:
        raise session.failure("Librus zwrócił nierozpoznany format odpowiedzi logowania.", requires_login=True, http_status=response.status_code) from None
    if not isinstance(payload, dict):
        raise session.failure("Librus zwrócił nierozpoznany format odpowiedzi logowania.", requires_login=True, http_status=response.status_code)
    status = payload.get("status")
    if status is False or (isinstance(status, str) and status.casefold() in {"error", "failed", "failure"}):
        raise session.failure("Librus nie zaakceptował logowania. Sprawdź dane konta Synergia i komunikaty przy logowaniu na oficjalnej stronie.", requires_login=True, http_status=response.status_code)
    if status not in (None, "ok", "success", True):
        raise session.failure("Librus zwrócił nierozpoznany wynik logowania.", requires_login=True, http_status=response.status_code)

    session.stage = "login_finish"
    destination = _login_target(session, OAUTH_RETURN_BASE, payload.get("goTo"))
    session.allow_login_pages = False
    _follow_login_redirects(session, destination)

    session.stage = "login_session"
    dzienniksid = _synergia_cookie(session, "DZIENNIKSID")
    sdzienniksid = _synergia_cookie(session, "SDZIENNIKSID")
    if not dzienniksid or not sdzienniksid:
        raise session.failure("Logowanie nie utworzyło pełnej sesji Synergii. Sprawdź, czy na oficjalnej stronie nie ma dodatkowego potwierdzenia.", requires_login=True)
    return Token(dzienniks=dzienniksid, sdzienniks=sdzienniksid)


def _parse_error() -> ConnectorError:
    return ConnectorError("Nie udało się odczytać danych tej sekcji. Librus mógł zmienić ich format.")


def _iso(value, *, optional: bool = False) -> str:
    value = plain_text(value)
    if not value:
        if optional:
            return ""
        raise _parse_error()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        try:
            return date.fromisoformat(value).isoformat()
        except ValueError:
            raise _parse_error() from None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).isoformat(timespec="seconds")
    except ValueError:
        for fmt in ("%d.%m.%Y", "%d-%m-%Y", "%d.%m.%Y %H:%M", "%d.%m.%Y %H:%M:%S"):
            try:
                parsed = datetime.strptime(value, fmt)
                return parsed.date().isoformat() if "%H" not in fmt else parsed.isoformat(timespec="seconds")
            except ValueError:
                continue
    raise _parse_error()


def _homework_date(value) -> str:
    # apix combines the date and the adjacent weekday/time column. Keep valid
    # times, but remove only recognised weekday labels from date-only values.
    value = re.sub(r"\s+-+$", "", plain_text(value))
    try:
        return _iso(value)
    except ConnectorError:
        match = re.fullmatch(r"(.+?)\s+\(?([a-ząćęłńóśźż.]+)\)?", value, re.IGNORECASE)
        weekdays = {"poniedziałek", "wtorek", "środa", "sroda", "czwartek",
                    "piątek", "piatek", "sobota", "niedziela",
                    "pon", "pn", "wt", "śr", "sr", "czw", "pt", "sob", "nd", "niedz"}
        if match and match[2].casefold().rstrip(".") in weekdays:
            return _iso(match[1])
        raise


def _identifier(kind: str, *identity) -> str:
    raw = json.dumps(identity, ensure_ascii=False, sort_keys=True, default=str)
    return kind + ":" + hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


def _item(kind, item_id, title, subtitle="", when="", details="", unread=False):
    return {
        "id": item_id, "kind": kind, "title": plain_text(title),
        "subtitle": plain_text(subtitle), "when": when,
        "details": plain_text(details), "url": SECTION_URLS[kind],
        "unread": bool(unread),
    }


def _description(values: Mapping) -> str:
    parts = []
    labels = {"teacher_swap": "Nauczyciel", "subject_swap": "Przedmiot",
              "classroom_swap": "Sala", "date_added": "Dodano"}
    for key, value in values.items():
        if isinstance(value, Mapping):
            value = _description(value)
        value = plain_text(value)
        if value and value != "unknown":
            parts.append(f"{labels.get(key, key)}: {value}")
        elif key and not value:
            parts.append(plain_text(key))
    return "\n".join(parts)


def _decode_body(value) -> str:
    if not isinstance(value, str):
        raise _parse_error()
    try:
        encoded = re.sub(r"\s+", "", value)
        decoded = base64.b64decode(encoded, validate=True).decode("utf-8")
    except (ValueError, binascii.Error, UnicodeError):
        raise _parse_error() from None
    return plain_text(decoded)


class LibrusConnector:
    def __init__(self, login: str, password: str):
        self.login = login
        self._password = password
        self._client: Client | None = None
        self._session: GuardedSession | None = None
        self._messages_session: GuardedSession | None = None
        self._messages_ready = False

    def connect(self) -> None:
        """Authenticate once. No hidden retries or background login loops."""
        if self._client is not None:
            return
        if not self.login or not self._password:
            raise ConnectorError("Podaj login i hasło konta Synergia otrzymanego ze szkoły.", requires_login=True)
        session = GuardedSession(allow_login_pages=True)
        client = Client(token=Token(), extra_cookies=requests.cookies.RequestsCookieJar())
        client._session.close()
        client._session = session
        try:
            client.token = _authenticate(session, self.login, self._password)
            # Copy immediately: apix replaces its request cookie jar on reads.
            messages = GuardedSession()
            messages.cookies.update(session.cookies.copy())
            session.allow_login_pages = False
            session.stage = "data"
        except ConnectorError:
            session.cookies.clear()
            session.close()
            raise
        except (AuthorizationError, TokenError, TokenKeyError):
            session.cookies.clear()
            session.close()
            raise session.failure("Nie udało się zalogować. Sprawdź dane konta Synergia i wymagane potwierdzenia w Librusie.", requires_login=True) from None
        except Exception:
            session.cookies.clear()
            session.close()
            raise session.failure("Librus zwrócił nierozpoznaną odpowiedź logowania. Sprawdź konto na oficjalnej stronie.", requires_login=True) from None
        self._client = client
        self._session = session
        self._messages_session = messages

    def _require_connected(self):
        if self._client is None:
            raise ConnectorError("Najpierw połącz konto Synergia.", requires_login=True)

    def fetch(self, kind: str) -> list[dict]:
        self._require_connected()
        methods = {"grades": self._grades, "messages": self._messages,
                   "announcements": self._announcements,
                   "schedule": self._schedule, "attendance": self._attendance,
                   "timetable": self._timetable, "homework": self._homework}
        if kind not in methods:
            raise ConnectorError("Nieobsługiwana sekcja danych.")
        try:
            return methods[kind]()
        except ConnectorError:
            raise
        except (AuthorizationError, TokenError, TokenKeyError):
            raise ConnectorError("Sesja Librusa wygasła. Połącz konto ponownie.", requires_login=True) from None
        except Exception:
            raise _parse_error() from None

    def _grades(self):
        numeric, _averages, descriptive = get_grades(self._client, "all")
        result = []
        for flavor, semesters in (("numeric", numeric), ("descriptive", descriptive)):
            if not isinstance(semesters, list):
                raise _parse_error()
            for semester in semesters:
                if not isinstance(semester, Mapping):
                    raise _parse_error()
                for subject, entries in semester.items():
                    if not isinstance(entries, list):
                        raise _parse_error()
                    for grade in entries:
                        when = _iso(grade.date)
                        value = plain_text(grade.grade)
                        desc = plain_text(grade.desc)
                        identity = (flavor, grade.href) if grade.href else (
                            flavor, subject, grade.semester, when, grade.teacher, desc, value,
                        )
                        category = getattr(grade, "category", "")
                        details = _description({"Przedmiot": subject, "Ocena": value,
                            "Opis": desc, "Kategoria": category, "Nauczyciel": grade.teacher,
                            "Semestr": grade.semester})
                        if flavor == "numeric":
                            details += f"\nWaga: {grade.weight}\nWliczana do średniej: {'tak' if grade.counts else 'nie'}"
                        result.append(_item("grades", _identifier("grades", *identity),
                            f"{subject}: {value}" if value else f"{subject} — ocena opisowa",
                            category or grade.teacher, when, details))
        return result

    def _announcements(self):
        announcements = get_announcements(self._client)
        if not isinstance(announcements, list):
            raise _parse_error()
        result = []
        for announcement in announcements:
            title = plain_text(announcement.title).strip()
            author = plain_text(announcement.author).strip()
            when = _iso(announcement.date)
            if not title:
                raise _parse_error()
            # apix exposes no server ID. Keep content out of the identity so
            # an edited body is detected as a change to the same announcement.
            result.append(_item("announcements", _identifier("announcements", title, author, when),
                                title, author, when, announcement.description))
        return result

    def _attendance(self):
        semesters = get_attendance(self._client, "all")
        if not isinstance(semesters, list):
            raise _parse_error()
        result = []
        for entries in semesters:
            if not isinstance(entries, list):
                raise _parse_error()
            for entry in entries:
                when = _iso(entry.date)
                identity = (entry.href,) if entry.href else (when, entry.period, entry.subject)
                details = _description({"Rodzaj": entry.type, "Symbol": entry.symbol,
                    "Przedmiot": entry.subject, "Lekcja": entry.period,
                    "Nauczyciel": entry.teacher, "Temat": entry.topic})
                result.append(_item("attendance", _identifier("attendance", *identity),
                    entry.type or entry.symbol, f"{entry.subject} · lekcja {entry.period}",
                    when, details))
        return result

    def _schedule(self):
        today = date.today()
        current = today.replace(day=1)
        following = (current + timedelta(days=32)).replace(day=1)
        result = []
        for month in (current, following):
            events = get_schedule(self._client, str(month.month), str(month.year))
            if not isinstance(events, Mapping):
                raise _parse_error()
            for day, entries in events.items():
                if not isinstance(entries, list):
                    raise _parse_error()
                when = date(month.year, month.month, int(day)).isoformat()
                for event in entries:
                    if not isinstance(event.data, Mapping):
                        raise _parse_error()
                    identity = (event.href,) if event.href else (
                        when, event.subject, event.title, event.number, event.hour,
                    )
                    details = _description(event.data)
                    if event.hour and event.hour != "unknown":
                        details += f"\nGodzina: {event.hour}"
                    result.append(_item("schedule", _identifier("schedule", *identity),
                        event.title or event.subject, event.subject, when, details))
        return result

    def _homework(self):
        # The filter is supplied to Librus for the current school year. Do not
        # fetch every assignment's detail page during periodic synchronization.
        today = date.today()
        year = today.year if today.month >= 9 else today.year - 1
        rows = get_homework(self._client, date(year, 9, 1).isoformat(), date(year + 1, 8, 31).isoformat())
        if not isinstance(rows, list):
            raise _parse_error()
        result = []
        for row in rows:
            raw_id = str(row.href)
            if not re.fullmatch(r"[0-9]{1,20}", raw_id):
                raise _parse_error()
            # apix names the first table column 'lesson' (school subject),
            # and the third 'subject' (assignment topic).
            subject, topic, teacher = plain_text(row.lesson), plain_text(row.subject), plain_text(row.teacher)
            if not topic:
                raise _parse_error()
            due = _homework_date(row.completion_date)
            assigned = _homework_date(row.task_date)
            details = _description({"Przedmiot": subject, "Temat": topic, "Nauczyciel": teacher,
                                    "Kategoria": row.category, "Dodano": assigned, "Termin wykonania": due})
            details += "\nPełna treść dostępna po wybraniu Pobierz treść zadania. Załączniki i wysyłanie rozwiązania dostępne w oficjalnym Librusie."
            item = _item("homework", "homework:" + raw_id, topic, " · ".join(filter(None, (subject, teacher))), due, details)
            item["url"] = SECTION_URLS["homework"] + "/podglad/" + raw_id
            result.append(item)
        return result

    def read_homework(self, item_id: str) -> dict:
        self._require_connected()
        if not isinstance(item_id, str) or not re.fullmatch(r"homework:[0-9]{1,20}", item_id):
            raise ConnectorError("Nieprawidłowy identyfikator zadania domowego.")
        try:
            values = homework_detail(self._client, item_id.split(":", 1)[1])
            if not isinstance(values, Mapping) or not values:
                raise _parse_error()
            text = _description(values)
            if not text:
                raise _parse_error()
            return {"text": text + "\n\nZałączniki i wysyłanie rozwiązania dostępne w oficjalnym Librusie."}
        except ConnectorError:
            raise
        except (AuthorizationError, TokenError, TokenKeyError):
            raise ConnectorError("Sesja Librusa wygasła. Połącz konto ponownie.", requires_login=True) from None
        except Exception:
            raise _parse_error() from None

    def _timetable(self):
        today = date.today()
        monday = today - timedelta(days=today.weekday())
        result = []
        for start in (monday, monday + timedelta(days=7)):
            week = get_timetable(self._client, datetime.combine(start, datetime.min.time()))
            if not isinstance(week, list):
                raise _parse_error()
            for day in week:
                if not isinstance(day, list):
                    raise _parse_error()
                for lesson in day:
                    if not isinstance(lesson.info, Mapping):
                        raise _parse_error()
                    if not lesson.subject and not lesson.info:
                        continue
                    when = _iso(lesson.date)
                    if lesson.date_from:
                        when = _iso(f"{when}T{lesson.date_from}")
                    details = _description({"Nauczyciel i sala": lesson.teacher_and_classroom,
                        "Początek": lesson.date_from, "Koniec": lesson.date_to,
                        **lesson.info})
                    result.append(_item("timetable", _identifier("timetable", lesson.date, lesson.number),
                        lesson.subject or "Zmiana w planie", f"Lekcja {lesson.number} · {lesson.teacher_and_classroom}",
                        when, details))
        return result

    def _bootstrap_messages(self):
        if self._messages_ready:
            return
        session = self._messages_session
        if session is None:
            raise ConnectorError("Najpierw połącz konto Synergia.", requires_login=True)
        session.stage = "messages"
        oauth = bool(_synergia_cookie(session, "oauth_token"))
        if not oauth:
            # Refresh uses existing cookies; it does not submit login credentials.
            session.get("https://synergia.librus.pl/refreshToken")
            oauth = bool(_synergia_cookie(session, "oauth_token"))
        if not oauth:
            raise session.failure("Sesja nie udostępniła dostępu do nowej skrzynki. Połącz konto ponownie.", requires_login=True)
        session.get(SECTION_URLS["messages"])
        if not any(c.domain.lstrip(".") == "wiadomosci.librus.pl" for c in session.cookies):
            raise session.failure("Nowa skrzynka wiadomości nie jest dostępna dla tej sesji. Pozostałe sekcje można nadal odczytać.")
        self._messages_ready = True
        session.stage = "data"

    @staticmethod
    def _json(response):
        try:
            data = response.json()
        except (ValueError, requests.JSONDecodeError):
            raise _parse_error() from None
        if not isinstance(data, Mapping):
            raise _parse_error()
        return data

    def _messages(self):
        self._bootstrap_messages()
        result = {}
        for page in range(1, 5):
            payload = self._json(self._messages_session.get(MESSAGE_API, params={"page": page, "limit": 50}))
            if (not isinstance(payload.get("data"), list)
                    or type(payload.get("total")) is not int or payload["total"] < 0):
                raise _parse_error()
            entries, total = payload["data"], payload["total"]
            if len(entries) > 50 or (not entries and total > (page - 1) * 50):
                raise _parse_error()
            for message in entries:
                required = {"messageId", "topic", "sendDate", "readDate"}
                if not isinstance(message, Mapping) or not required.issubset(message):
                    raise _parse_error()
                raw_id = message["messageId"]
                if not isinstance(raw_id, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,199}", raw_id):
                    raise _parse_error()
                if not isinstance(message["topic"], str):
                    raise _parse_error()
                sender = message.get("senderName") or " ".join(filter(None, [
                    message.get("senderFirstName"), message.get("senderLastName")]))
                if not sender:
                    raise _parse_error()
                when = _iso(message["sendDate"], optional=True)
                details = _description({"Nadawca": sender, "Data": when or "Brak daty wysłania"})
                if message.get("isAnyFileAttached"):
                    details += "\nWiadomość zawiera załączniki; otwórz je w oficjalnym Librusie."
                # No detail endpoint and no body/snippet decoding during sync.
                details += "\nTreść dostępna po otwarciu wiadomości."
                item_id = "messages:new:" + raw_id
                result[item_id] = _item("messages", item_id, message["topic"] or "(bez tematu)",
                    sender, when, details, message["readDate"] in (None, ""))
            if len(entries) < 50 or page * 50 >= total:
                break
        return list(result.values())

    def read_message(self, item_id: str) -> dict:
        self._require_connected()
        prefix = "messages:new:"
        if not item_id.startswith(prefix):
            raise ConnectorError("Ta wiadomość nie pochodzi z obsługiwanej skrzynki.")
        raw_id = item_id[len(prefix):]
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,199}", raw_id):
            raise ConnectorError("Nieprawidłowy identyfikator wiadomości.")
        try:
            self._bootstrap_messages()
            response = self._messages_session.get(MESSAGE_API + "/" + quote(raw_id, safe=""))
            payload = self._json(response)
            message = payload.get("data")
            if not isinstance(message, Mapping) or "Message" not in message or not isinstance(message.get("topic"), str):
                raise _parse_error()
            return {"title": plain_text(message["topic"]), "text": _decode_body(message["Message"])}
        except ConnectorError:
            raise
        except Exception:
            raise _parse_error() from None

    def close(self) -> None:
        for session in (self._session, self._messages_session):
            if session is not None:
                session.cookies.clear()
                session.close()
        self._client = None
        self._session = None
        self._messages_session = None
        self._messages_ready = False
        self._password = ""
