"""Validation of personal reminders; independent of school-year snapshots."""

from datetime import datetime
from uuid import uuid4


REMINDER_KINDS = frozenset({"announcements", "schedule", "messages"})


def due_datetime(value: str) -> datetime:
    if not isinstance(value, str):
        raise ValueError("Podaj poprawną datę i godzinę przypomnienia.")
    try:
        parsed = datetime.fromisoformat(value)
        result = parsed.astimezone()
        # Reject a local wall time in the gap when clocks move forward.
        if parsed.tzinfo is None and result.replace(tzinfo=None) != parsed:
            raise ValueError
        return result
    except (ValueError, TypeError, OverflowError, OSError):
        raise ValueError("Podaj poprawną datę i godzinę przypomnienia.") from None


def reminder_input(title: str, due_at: str, show_text: bool, *, now: float) -> dict:
    if not isinstance(title, str) or not title.strip() or len(title.strip()) > 200:
        raise ValueError("Wpisz treść przypomnienia (od 1 do 200 znaków).")
    due = due_datetime(due_at)
    if due.timestamp() <= now:
        raise ValueError("Termin przypomnienia musi być w przyszłości.")
    if not isinstance(show_text, bool):
        raise ValueError("Nieprawidłowe ustawienie treści powiadomienia.")
    return {"title": title.strip(), "due_at": due.isoformat(timespec="seconds"),
            "show_text": show_text, "status": "pending", "fired_at": "", "system_sent": False}


def new_reminder(item: dict, values: dict) -> dict:
    if item.get("kind") not in REMINDER_KINDS or not item.get("id"):
        raise ValueError("Przypomnienia można ustawić dla ogłoszeń, terminarza i wiadomości.")
    return {"id": uuid4().hex, "kind": item["kind"], "item_id": item["id"],
            "source_title": str(item.get("title") or "Bez tytułu")[:300], **values}


def restore_reminders(value) -> list[dict]:
    if not isinstance(value, list):
        raise ValueError("Nieprawidłowa lista przypomnień.")
    result = []
    seen = set()
    for row in value:
        if (not isinstance(row, dict) or row.get("kind") not in REMINDER_KINDS
                or row.get("status") not in {"pending", "fired"}
                or any(not isinstance(row.get(key), str) or not row[key].strip()
                       for key in ("id", "item_id", "source_title", "title", "due_at"))
                or len(row["title"]) > 200 or len(row["source_title"]) > 300
                or row["id"] in seen or not isinstance(row.get("show_text"), bool)
                or not isinstance(row.get("system_sent", False), bool)):
            raise ValueError("Nieprawidłowy zapis przypomnienia.")
        seen.add(row["id"])
        fired_at = row.get("fired_at", "")
        if not isinstance(fired_at, str) or (row["status"] == "fired" and not fired_at):
            raise ValueError("Nieprawidłowy stan przypomnienia.")
        if fired_at:
            due_datetime(fired_at)
        result.append({key: row[key] for key in ("id", "kind", "item_id", "source_title", "title", "show_text", "status")}
                      | {"due_at": due_datetime(row["due_at"]).isoformat(timespec="seconds"),
                         "fired_at": fired_at, "system_sent": row.get("system_sent", False)})
    return result
