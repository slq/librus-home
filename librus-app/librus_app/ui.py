"""Polish, local-first desktop UI for LibrusApp.

Only the main Tk thread touches widgets. Network and persistence operations
belong to the controller; the UI receives their results through its queue.

For visual checks use ``create_app(controller, restore=False)``. The returned
object exposes ``root`` and ``select_page(name)`` and can be driven in Xvfb.
"""

from __future__ import annotations

import queue
import tkinter as tk
from datetime import datetime
from tkinter import messagebox, ttk
from typing import Any

from . import __version__


BG = "#f4f6fa"
WHITE = "#ffffff"
INK = "#183047"
MUTED = "#64748b"
TEAL = "#087e8b"
TEAL_DARK = "#086774"
TEAL_PALE = "#e6f4f4"
LINE = "#dce4ec"
SIDEBAR = "#172d40"
SIDEBAR_MUTED = "#afbdcb"
WARNING_BG = "#fff4dc"
WARNING_FG = "#855d13"
ERROR_BG = "#fff0ed"
ERROR_FG = "#994835"

PAGES = {
    "overview": ("Przegląd", "To, co warto sprawdzić w pierwszej kolejności."),
    "grades": ("Oceny", "Ostatnio pobrane oceny i komentarze nauczycieli."),
    "messages": ("Wiadomości", "Do 200 najnowszych wiadomości. Treść pobierzesz osobnym przyciskiem."),
    "announcements": ("Ogłoszenia", "Ogłoszenia szkoły z autorem, datą i pełną treścią."),
    "schedule": ("Terminarz", "Wydarzenia z bieżącego i kolejnego miesiąca."),
    "attendance": ("Frekwencja", "Obecności, spóźnienia i nieobecności z dziennika."),
    "timetable": ("Plan lekcji", "Bieżący i kolejny tydzień. Sprawdź czas odczytu poniżej."),
    "reminders": ("Przypomnienia", "Własne alerty dla ogłoszeń, terminarza i wiadomości."),
    "settings": ("Ustawienia", "Konto, odświeżanie danych i powiadomienia na komputerze."),
}

SECTION_TITLES = {key: value[0] for key, value in PAGES.items() if key not in {"overview", "settings", "reminders"}}


def _parse_datetime(value: Any) -> datetime | None:
    text = str(value or "").strip()
    if len(text) < 10 or text[4:5] != "-" or text[7:8] != "-":
        return None
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00"))
    except (TypeError, ValueError):
        return None


def _when(value: Any, *, compact: bool = False) -> str:
    text = str(value or "").strip()
    parsed = _parse_datetime(text)
    if parsed is None:
        return text or "—"
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone()
    date_format = "%d.%m" if compact else "%d.%m.%Y"
    date_text = parsed.strftime(date_format)
    if "T" in text or len(text) > 10:
        if parsed.hour or parsed.minute:
            return f"{date_text} · {parsed:%H:%M}"
    return date_text


def _timestamp(value: Any) -> float | None:
    parsed = _parse_datetime(value)
    if parsed is None:
        return None
    try:
        return parsed.timestamp()
    except (OverflowError, OSError, ValueError):
        return None


def _ordered_items(items: list[dict], section: str) -> list[dict]:
    """Sort dated entries while retaining useful source order for text dates."""
    dated: list[tuple[float, dict]] = []
    undated: list[dict] = []
    for item in items:
        stamp = _timestamp(item.get("when"))
        if stamp is None:
            undated.append(item)
        else:
            dated.append((stamp, item))
    dated.sort(key=lambda entry: entry[0], reverse=section not in {"schedule", "timetable"})
    return [item for _, item in dated] + undated


def _plain_text(parent: tk.Misc, *, height: int = 5, bg: str = WHITE) -> tk.Text:
    return tk.Text(
        parent,
        height=height,
        wrap="word",
        font=("Segoe UI", 10),
        bg=bg,
        fg=INK,
        relief="flat",
        borderwidth=0,
        highlightthickness=0,
        padx=0,
        pady=2,
        spacing1=2,
        spacing3=5,
        cursor="arrow",
        state="disabled",
    )


def _set_text(widget: tk.Text, value: str) -> None:
    widget.configure(state="normal")
    widget.delete("1.0", "end")
    widget.insert("1.0", value)
    widget.configure(state="disabled")


class SectionView:
    """Reusable searchable list with a read-only, plain-text detail panel."""

    def __init__(self, app: "SchoolPanelApp", section: str, *, pane_orientation: str = "vertical") -> None:
        self.app = app
        self.section = section
        self.frame = tk.Frame(app.content, bg=BG)
        self.items_by_row: dict[str, dict] = {}
        self.selected_item: dict | None = None
        self.sort_column: str | None = None
        self.sort_reverse = False

        toolbar = tk.Frame(self.frame, bg=BG)
        self.toolbar = toolbar
        toolbar.pack(fill="x", pady=(0, 12))
        tk.Label(toolbar, text="Szukaj", bg=BG, fg=MUTED, font=("Segoe UI", 10)).pack(side="left", padx=(0, 10))
        self.search_var = tk.StringVar()
        self.search_entry = ttk.Entry(toolbar, textvariable=self.search_var, width=35, style="Panel.TEntry")
        self.search_entry.pack(side="left", fill="x", expand=True)
        self.search_var.trace_add("write", lambda *_: self.render())
        ttk.Button(toolbar, text="Wyczyść", command=lambda: self.search_var.set(""), style="Quiet.TButton").pack(side="left", padx=(8, 0))
        self.reminder_button: ttk.Button | None = None
        if section in {"announcements", "schedule", "messages"}:
            self.reminder_button = ttk.Button(toolbar, text="Przypomnij mi…", command=self.remind_selected,
                                              style="Quiet.TButton", state="disabled")
            self.reminder_button.pack(side="left", padx=(8, 0))
        self.count_label = tk.Label(toolbar, text="", bg=BG, fg=MUTED, font=("Segoe UI", 9))
        self.count_label.pack(side="right", padx=(16, 0))

        self.source_label = tk.Label(self.frame, text="", bg=BG, fg=MUTED, font=("Segoe UI", 9), anchor="w")
        self.source_label.pack(fill="x", pady=(0, 8))
        self.error_label = tk.Label(
            self.frame, text="", bg=ERROR_BG, fg=ERROR_FG, font=("Segoe UI", 10),
            anchor="w", justify="left", padx=14, pady=10, wraplength=820,
        )

        self.read_button: ttk.Button | None = None
        if section == "messages":
            message_actions = tk.Frame(self.frame, bg=BG)
            message_actions.pack(fill="x", pady=(0, 10))
            self.read_button = ttk.Button(
                message_actions,
                text="Pobierz treść (oznaczy jako przeczytaną)",
                command=self.read_selected,
                style="Accent.TButton",
                state="disabled",
            )
            self.read_button.pack(anchor="w")

        self.panes = ttk.Panedwindow(self.frame, orient=pane_orientation)
        self.panes.pack(fill="both", expand=True)
        list_shell = tk.Frame(self.panes, bg=WHITE, highlightbackground=LINE, highlightthickness=1)
        self.panes.add(list_shell, weight=3)
        table_frame = tk.Frame(list_shell, bg=WHITE)
        table_frame.pack(fill="both", expand=True)
        self.tree = ttk.Treeview(
            table_frame,
            columns=("title", "subtitle", "when"),
            show="tree headings",
            selectmode="browse",
            style="Panel.Treeview",
            height=11,
        )
        self.tree.column("#0", width=32, minwidth=32, stretch=False)
        self.tree.heading("#0", text="")
        self.tree.column("title", width=375, minwidth=200, stretch=True)
        self.tree.column("subtitle", width=250, minwidth=150, stretch=True)
        self.tree.column("when", width=155, minwidth=130, stretch=False)
        subtitle_heading = "Autor" if section == "announcements" else "Nadawca" if section == "messages" else "Informacje"
        title_heading = "Tytuł" if section == "announcements" else "Temat" if section == "messages" else "Pozycja"
        for key, title in (("title", title_heading), ("subtitle", subtitle_heading), ("when", "Data")):
            self.tree.heading(key, text=title, anchor="w", command=lambda c=key: self.sort(c))
        scrollbar = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")
        self.tree.pack(side="left", fill="both", expand=True)
        self.tree.tag_configure("unread", font=("Segoe UI", 10, "bold"))
        self.tree.tag_configure("changed", background="#fff8e3")
        self.tree.tag_configure("stripe", background="#f8fafc")
        self.tree.bind("<<TreeviewSelect>>", self._select)
        self.tree.bind("<Double-1>", self._double_click)
        self.tree.bind("<Return>", self._show_selected_dialog)
        self.empty_label = tk.Label(
            list_shell, text="", bg=WHITE, fg=MUTED,
            font=("Segoe UI", 10), wraplength=700, justify="center", pady=22,
        )
        self.legend = tk.Label(
            list_shell,
            text=("● Nieprzeczytana wiadomość     " if section == "messages" else "")
                 + "+ Nowa lub zmieniona pozycja     Kliknij, aby zobaczyć szczegóły.",
            bg=WHITE, fg=MUTED, font=("Segoe UI", 9), anchor="w", padx=12, pady=10,
        )
        self.legend.pack(side="bottom", fill="x")

        detail_shell = tk.Frame(self.panes, bg=WHITE, highlightbackground=LINE, highlightthickness=1)
        self.panes.add(detail_shell, weight=2)
        detail_inner = tk.Frame(detail_shell, bg=WHITE, padx=20, pady=16)
        self.detail_inner = detail_inner
        detail_inner.pack(fill="both", expand=True)
        top = tk.Frame(detail_inner, bg=WHITE)
        top.pack(fill="x")
        top.columnconfigure(0, weight=1)
        self.detail_title = tk.Label(top, text="Szczegóły pozycji", bg=WHITE, fg=INK, font=("Segoe UI", 12, "bold"), anchor="w")
        self.detail_title.grid(row=0, column=0, sticky="ew")
        self.open_button = ttk.Button(top, text="Otwórz w Librusie", command=self.open_selected, style="Quiet.TButton", state="disabled")
        self.open_button.grid(row=0, column=1, padx=(12, 0), sticky="e")
        self.detail_meta = tk.Label(detail_inner, text="", bg=WHITE, fg=MUTED, font=("Segoe UI", 9), anchor="w", wraplength=800, justify="left")
        self.detail_meta.pack(fill="x", pady=(4, 8))
        text_row = tk.Frame(detail_inner, bg=WHITE)
        text_row.pack(fill="both", expand=True)
        self.detail_text = _plain_text(text_row, height=5)
        text_scroll = ttk.Scrollbar(text_row, orient="vertical", command=self.detail_text.yview)
        self.detail_text.configure(yscrollcommand=text_scroll.set)
        text_scroll.pack(side="right", fill="y")
        self.detail_text.pack(side="left", fill="both", expand=True)
        self._render_detail(None)

    def sort(self, column: str) -> None:
        self.sort_reverse = not self.sort_reverse if self.sort_column == column else False
        self.sort_column = column
        self.render()

    def filtered_items(self) -> list[dict]:
        items = list(self.app.state.get("sections", {}).get(self.section) or [])
        query = self.search_var.get().casefold().strip()
        items = _ordered_items(items, self.section)
        if query:
            items = [item for item in items if query in " ".join(str(item.get(key, "")) for key in ("title", "subtitle", "when", "details", "kind")).casefold()]
        return items

    def render(self) -> None:
        state = self.app.state
        items = self.filtered_items()
        query = self.search_var.get().casefold().strip()
        current_id = str(self.selected_item.get("id", "")) if self.selected_item else None
        if self.sort_column:
            if self.sort_column == "when":
                items.sort(key=lambda item: (_timestamp(item.get("when")) or 0.0, str(item.get("when", ""))), reverse=self.sort_reverse)
            else:
                items.sort(key=lambda item: str(item.get(self.sort_column or "", "")).casefold(), reverse=self.sort_reverse)
        old_yview = self.tree.yview()
        self.tree.delete(*self.tree.get_children())
        self.items_by_row.clear()
        selected_row = None
        for index, item in enumerate(items):
            row = f"item_{index}"
            self.items_by_row[row] = item
            unread = bool(item.get("unread"))
            changed = bool(item.get("changed"))
            tags = []
            if index % 2 and not changed:
                tags.append("stripe")
            if changed:
                tags.append("changed")
            if unread:
                tags.append("unread")
            self.tree.insert(
                "", "end", iid=row,
                text="●" if unread else "+" if changed else "",
                values=(str(item.get("title") or "Bez tytułu"), str(item.get("subtitle") or "—"), _when(item.get("when"))),
                tags=tuple(tags),
            )
            if current_id and str(item.get("id", "")) == current_id:
                selected_row = row
        if selected_row:
            self.tree.selection_set(selected_row)
            self.tree.focus(selected_row)
            self._render_detail(self.items_by_row[selected_row])
        else:
            self._render_detail(None)
        if old_yview:
            self.tree.yview_moveto(old_yview[0])
        self.count_label.configure(text=f"Pozycji: {len(items)}")
        stamp = state.get("updated_at", {}).get(self.section)
        self.source_label.configure(text=f"Odczyt sekcji: {_when(stamp)}" if stamp else "Ta sekcja nie została jeszcze pobrana.")
        error = state.get("errors", {}).get(self.section)
        if error:
            self.error_label.configure(text=f"Nie udało się odświeżyć tej sekcji. {error}")
            self.error_label.pack(fill="x", pady=(0, 10), before=self.panes)
        else:
            self.error_label.pack_forget()
        if not items:
            if query:
                empty = "Brak wyników. Spróbuj krótszego hasła lub wyczyść wyszukiwanie."
            else:
                empty = self.app.empty_text(self.section)
            self.empty_label.configure(text=empty)
            self.empty_label.place(relx=0.5, rely=0.44, anchor="center", relwidth=0.92)
        else:
            self.empty_label.place_forget()

    def _select(self, _event: Any = None) -> None:
        selection = self.tree.selection()
        item = self.items_by_row.get(selection[0]) if selection else None
        self._render_detail(item)

    def _render_detail(self, item: dict | None) -> None:
        self.selected_item = item
        if self.reminder_button:
            self.reminder_button.configure(state="normal" if item and not self.app.state.get("busy") else "disabled")
        if item is None:
            self.detail_title.configure(text="Szczegóły pozycji")
            self.detail_meta.configure(text="")
            _set_text(self.detail_text, "Wybierz pozycję z listy. Tutaj pojawią się jej szczegóły.")
            self.open_button.configure(state="disabled")
            if self.read_button:
                self.read_button.configure(state="disabled")
            return
        title = str(item.get("title") or "Bez tytułu")
        self.detail_title.configure(text=title if len(title) <= 78 else title[:75] + "…")
        metadata = [str(item.get("subtitle") or ""), _when(item.get("when"))]
        if item.get("unread"):
            metadata.append("Nieprzeczytana")
        self.detail_meta.configure(text="  ·  ".join(part for part in metadata if part and part != "—"))
        details = str(item.get("details") or "")
        if self.section == "messages":
            note = "Treść wiadomości pobierzesz przyciskiem „Pobierz treść” nad listą. Pobranie może oznaczyć wiadomość jako przeczytaną w Librusie."
            details = f"{details}\n\n{note}" if details else note
        elif not details:
            details = "Dziennik nie udostępnił dodatkowego opisu tej pozycji."
        _set_text(self.detail_text, details)
        self.open_button.configure(state="normal")
        if self.read_button:
            self.read_button.configure(state="disabled" if self.app.state.get("busy") else "normal")

    def _double_click(self, event: Any) -> None:
        if self.tree.identify_region(event.x, event.y) not in {"tree", "cell"}:
            return
        self._show_selected_dialog()

    def _show_selected_dialog(self, _event: Any = None) -> None:
        if not self.selected_item:
            return
        item = self.selected_item
        text = "\n".join(part for part in (str(item.get("subtitle") or ""), _when(item.get("when")), "", str(item.get("details") or "Brak dodatkowego opisu.")) if part is not None)
        if self.section == "messages":
            text += "\n\nTo podgląd metadanych. Treść pobierzesz osobnym przyciskiem w widoku Wiadomości."
        self.app.show_text(str(item.get("title") or "Szczegóły pozycji"), text)

    def open_selected(self) -> None:
        if self.selected_item:
            self.app.call("open_in_librus", self.selected_item.get("id"))

    def read_selected(self) -> None:
        if self.selected_item:
            self.app.call("read_message", self.selected_item.get("id"))

    def remind_selected(self) -> None:
        if self.selected_item:
            self.app.show_reminder_dialog(item=self.selected_item)


class SchoolPanelApp:
    """Single-window Tk application; ``root`` is exposed for visual QA."""

    def __init__(self, controller: Any, *, restore: bool = True) -> None:
        self.controller = controller
        self.root = tk.Tk()
        self.root.title(f"LibrusApp {__version__}")
        self.root.geometry("1240x850")
        self.root.minsize(1080, 720)
        self.root.configure(bg=BG)
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.closed = False
        self.current_page = "overview"
        self.state: dict = {
            "mode": "idle", "busy": False, "status": "Połącz konto, aby pobrać dane.",
            "last_sync": "", "next_sync": "", "interval": 15, "notifications": True,
            "sections": {}, "errors": {}, "updated_at": {},
        }
        self.views: dict[str, SectionView] = {}
        self.page_frames: dict[str, tk.Frame] = {}
        self.scroll_canvases: dict[str, tk.Canvas] = {}
        self.nav_buttons: dict[str, tk.Button] = {}
        self.login_dialog: tk.Toplevel | None = None
        self.reminder_dialog: tk.Toplevel | None = None
        self.notice_job: str | None = None
        self._prefs_dirty = False
        self._applying_preferences = False
        self._setup_styles()
        self._build_shell()
        self._build_overview()
        self._build_settings()
        self.select_page("overview")
        self._render_state(self.state)
        self.root.bind("<Control-r>", lambda _event: self.call("refresh"))
        self.root.bind("<Control-l>", lambda _event: self.show_login())
        self.root.bind("<Control-f>", self.focus_search)
        self.root.bind_all("<MouseWheel>", self._page_wheel, add="+")
        self.root.bind_all("<Button-4>", self._page_wheel, add="+")
        self.root.bind_all("<Button-5>", self._page_wheel, add="+")
        self.root.after(50, self._drain_events)
        self.root.after(80, lambda: self.call("emit_state"))
        if restore:
            self.root.after(150, lambda: self.call("restore"))

    def _setup_styles(self) -> None:
        self.root.option_add("*Font", ("Segoe UI", 10))
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure("TButton", font=("Segoe UI", 10), padding=(14, 9), borderwidth=0)
        style.configure("Accent.TButton", background=TEAL, foreground=WHITE, padding=(16, 10), font=("Segoe UI", 10, "bold"))
        style.map("Accent.TButton", background=[("disabled", "#b8ced2"), ("active", TEAL_DARK)], foreground=[("disabled", "#f7fafa")])
        style.configure("Quiet.TButton", background=WHITE, foreground=INK, borderwidth=1, bordercolor=LINE, lightcolor=WHITE, darkcolor=LINE, padding=(12, 8))
        style.map("Quiet.TButton", background=[("active", "#eaf0f5"), ("disabled", "#f0f2f5")], foreground=[("disabled", "#9aa6b2")])
        style.configure("Calendar.TButton", background=WHITE, foreground=INK, padding=(8, 4))
        style.map("Calendar.TButton", background=[("active", TEAL_PALE)], foreground=[("disabled", "#9aa6b2")])
        style.configure("Danger.TButton", background=WHITE, foreground=ERROR_FG, borderwidth=1, bordercolor="#edcec7", padding=(14, 9))
        style.map("Danger.TButton", background=[("active", ERROR_BG)])
        style.configure("Panel.TEntry", fieldbackground=WHITE, bordercolor=LINE, lightcolor=LINE, darkcolor=LINE, padding=8)
        style.map("Panel.TEntry", bordercolor=[("focus", TEAL)])
        style.configure("TCombobox", fieldbackground=WHITE, background=WHITE, bordercolor=LINE, padding=7)
        style.configure("Panel.TCheckbutton", background=WHITE, foreground=INK, padding=(0, 5))
        style.map("Panel.TCheckbutton", background=[("active", WHITE)])
        style.configure("Panel.Treeview", background=WHITE, fieldbackground=WHITE, foreground=INK, rowheight=40, font=("Segoe UI", 10), borderwidth=0)
        style.map("Panel.Treeview", background=[("selected", TEAL_PALE)], foreground=[("selected", INK)])
        style.configure("Panel.Treeview.Heading", background="#edf2f7", foreground=MUTED, font=("Segoe UI", 9, "bold"), padding=(10, 11), relief="flat")
        style.map("Panel.Treeview.Heading", background=[("active", "#e0e9f1")])
        style.configure("TScrollbar", background="#d2dce5", troughcolor="#f4f7fa", borderwidth=0, arrowsize=12)
        style.configure("TPanedwindow", background=BG)

    def _build_shell(self) -> None:
        sidebar = tk.Frame(self.root, bg=SIDEBAR, width=215)
        sidebar.pack(side="left", fill="y")
        sidebar.pack_propagate(False)
        brand = tk.Frame(sidebar, bg=SIDEBAR)
        brand.pack(fill="x", padx=22, pady=(31, 34))
        tk.Label(brand, text="LA", bg=TEAL, fg=WHITE, font=("Segoe UI", 13, "bold"), padx=10, pady=6).pack(anchor="w")
        tk.Label(brand, text="LibrusApp", bg=SIDEBAR, fg=WHITE, font=("Segoe UI", 17, "bold")).pack(anchor="w", pady=(12, 1))
        tk.Label(brand, text="Dziennik pod ręką", bg=SIDEBAR, fg=SIDEBAR_MUTED, font=("Segoe UI", 9)).pack(anchor="w")
        for key, (title, _) in PAGES.items():
            if key == "settings":
                tk.Frame(sidebar, bg="#314457", height=1).pack(fill="x", padx=20, pady=(20, 12))
            button = tk.Button(
                sidebar, text=title, command=lambda page=key: self.select_page(page),
                bg=SIDEBAR, fg=SIDEBAR_MUTED, activebackground="#25495d", activeforeground=WHITE,
                relief="flat", borderwidth=0, highlightthickness=0,
                anchor="w", padx=18, pady=5, font=("Segoe UI", 11), cursor="hand2",
            )
            button.pack(fill="x", padx=12, pady=2)
            self.nav_buttons[key] = button
        footer = tk.Frame(sidebar, bg=SIDEBAR)
        footer.pack(side="bottom", fill="x", padx=22, pady=24)
        self.sidebar_mode = tk.Label(footer, text="Konto niepołączone", bg=SIDEBAR, fg="#d7e5ef", font=("Segoe UI", 9, "bold"), anchor="w")
        self.sidebar_mode.pack(fill="x")
        tk.Label(footer, text="Dane na tym komputerze", bg=SIDEBAR, fg=SIDEBAR_MUTED, font=("Segoe UI", 9), anchor="w").pack(fill="x", pady=(6, 12))
        tk.Button(
            footer, text="Otwórz Librusa  ↗", command=lambda: self.call("open_in_librus"),
            bg=SIDEBAR, fg="#87d8df", activebackground=SIDEBAR, activeforeground=WHITE,
            font=("Segoe UI", 10), anchor="w", relief="flat", borderwidth=0, padx=0, cursor="hand2",
        ).pack(fill="x")

        main = tk.Frame(self.root, bg=BG)
        main.pack(side="left", fill="both", expand=True)
        header = tk.Frame(main, bg=WHITE, padx=28, pady=20)
        header.pack(fill="x")
        heading = tk.Frame(header, bg=WHITE)
        heading.pack(fill="x")
        title_box = tk.Frame(heading, bg=WHITE)
        title_box.pack(side="left", fill="x", expand=True)
        self.page_title = tk.Label(title_box, text="Przegląd", bg=WHITE, fg=INK, font=("Segoe UI", 23, "bold"), anchor="w")
        self.page_title.pack(fill="x")
        self.page_subtitle = tk.Label(title_box, text="", bg=WHITE, fg=MUTED, font=("Segoe UI", 10), anchor="w")
        self.page_subtitle.pack(fill="x", pady=(3, 0))
        self.login_button = ttk.Button(heading, text="Połącz konto", command=self.show_login, style="Quiet.TButton")
        self.login_button.pack(side="left", padx=(16, 8))
        self.refresh_button = ttk.Button(heading, text="Odśwież", command=lambda: self.call("refresh"), style="Accent.TButton")
        self.refresh_button.pack(side="left")
        sync_row = tk.Frame(header, bg=WHITE)
        sync_row.pack(fill="x", pady=(15, 0))
        self.status_dot = tk.Label(sync_row, text="●", bg=WHITE, fg=MUTED, font=("Segoe UI", 9))
        self.status_dot.pack(side="left", padx=(0, 6))
        self.status_label = tk.Label(sync_row, text="", bg=WHITE, fg=MUTED, font=("Segoe UI", 9), anchor="w")
        self.status_label.pack(side="left", fill="x", expand=True)
        self.sync_label = tk.Label(sync_row, text="", bg=WHITE, fg=MUTED, font=("Segoe UI", 9), anchor="e", justify="right")
        self.sync_label.pack(side="right", padx=(15, 0))
        tk.Frame(main, bg=LINE, height=1).pack(fill="x")

        self.banner = tk.Frame(main, bg=WARNING_BG, padx=28, pady=10)
        self.banner_text = tk.Label(self.banner, text="", bg=WARNING_BG, fg=WARNING_FG, font=("Segoe UI", 10), anchor="w")
        self.banner_text.pack(side="left", fill="x", expand=True)
        self.demo_change_button = tk.Button(
            self.banner, text="Zasymuluj nową ocenę", command=lambda: self.call("inject_demo_change"),
            bg=WARNING_BG, fg=WARNING_FG, activebackground="#f3e6bd", activeforeground=WARNING_FG,
            relief="flat", borderwidth=0, font=("Segoe UI", 9, "bold"), cursor="hand2",
        )

        self.notice_label = tk.Label(main, text="", bg=TEAL_PALE, fg=TEAL_DARK, font=("Segoe UI", 10), padx=28, pady=10, anchor="w", justify="left", wraplength=900)
        self.global_error_label = tk.Label(main, text="", bg=ERROR_BG, fg=ERROR_FG, font=("Segoe UI", 10), padx=28, pady=12, anchor="w", justify="left", wraplength=850)
        self.content = tk.Frame(main, bg=BG, padx=28, pady=23)
        self.content.pack(fill="both", expand=True)
        self.bottom_label = tk.Label(
            main, text="Powiadomienia działają, gdy aplikacja jest uruchomiona. Minimalizacja pozostawia je aktywne; zamknięcie je zatrzymuje.",
            bg=BG, fg=MUTED, font=("Segoe UI", 9), anchor="w", padx=28, pady=12,
        )
        self.bottom_label.pack(side="bottom", fill="x")

    def _build_overview(self) -> None:
        frame = self._scrollable_page("overview")
        self.welcome = tk.Frame(frame, bg=WHITE, highlightbackground=LINE, highlightthickness=1, padx=24, pady=21)
        tk.Label(self.welcome, text="Wszystko w jednym miejscu", bg=WHITE, fg=INK, font=("Segoe UI", 17, "bold"), anchor="w").pack(fill="x")
        tk.Label(
            self.welcome,
            text="Połącz szkolne konto Synergia, aby zobaczyć wiadomości, oceny i najbliższe wydarzenia.\nMożesz też najpierw sprawdzić interfejs na danych demonstracyjnych.",
            bg=WHITE, fg=MUTED, font=("Segoe UI", 10), anchor="w", justify="left",
        ).pack(fill="x", pady=(8, 16))
        actions = tk.Frame(self.welcome, bg=WHITE)
        actions.pack(fill="x")
        ttk.Button(actions, text="Połącz konto Synergia", command=self.show_login, style="Accent.TButton").pack(side="left")
        ttk.Button(actions, text="Zobacz demo", command=lambda: self.call("demo"), style="Quiet.TButton").pack(side="left", padx=(10, 0))

        self.summary_grid = tk.Frame(frame, bg=BG)
        self.summary_grid.pack(fill="x")
        self.metric_values: dict[str, tk.Label] = {}
        self.metric_notes: dict[str, tk.Label] = {}
        for column, (key, title) in enumerate((("messages", "NIEPRZECZYTANE"), ("grades", "POBRANE OCENY"), ("schedule", "NADCHODZĄCE WYDARZENIA"))):
            self.summary_grid.columnconfigure(column, weight=1, uniform="metric")
            card = tk.Frame(self.summary_grid, bg=WHITE, highlightbackground=LINE, highlightthickness=1, padx=18, pady=17)
            card.grid(row=0, column=column, sticky="nsew", padx=(0 if column == 0 else 7, 0 if column == 2 else 7))
            tk.Label(card, text=title, bg=WHITE, fg=MUTED, font=("Segoe UI", 8, "bold"), anchor="w").pack(fill="x")
            value = tk.Label(card, text="—", bg=WHITE, fg=TEAL_DARK if key == "messages" else INK, font=("Segoe UI", 30, "bold"), anchor="w")
            value.pack(fill="x", pady=(6, 0))
            note = tk.Label(card, text="Oczekiwanie na pierwszy odczyt", bg=WHITE, fg=MUTED, font=("Segoe UI", 9), anchor="w", wraplength=235, justify="left")
            note.pack(fill="x", pady=(3, 0))
            self.metric_values[key] = value
            self.metric_notes[key] = note

        self.overview_errors = tk.Label(frame, text="", bg=ERROR_BG, fg=ERROR_FG, font=("Segoe UI", 10), padx=15, pady=12, anchor="w", justify="left", wraplength=850)
        self.preview_grid = tk.Frame(frame, bg=BG)
        self.preview_grid.pack(fill="both", expand=True, pady=(18, 0))
        self.preview_grid.columnconfigure(0, weight=1, uniform="preview")
        self.preview_grid.columnconfigure(1, weight=1, uniform="preview")
        self.preview_grid.rowconfigure(0, weight=1)
        self.preview_lists: dict[str, tk.Frame] = {}
        for column, (key, title) in enumerate((("grades", "Ostatnie oceny"), ("schedule", "Najbliższe wydarzenia"))):
            card = tk.Frame(self.preview_grid, bg=WHITE, highlightbackground=LINE, highlightthickness=1, padx=20, pady=18)
            card.grid(row=0, column=column, sticky="nsew", padx=(0 if column == 0 else 8, 8 if column == 0 else 0))
            heading = tk.Frame(card, bg=WHITE)
            heading.pack(fill="x", pady=(0, 14))
            tk.Label(heading, text=title, bg=WHITE, fg=INK, font=("Segoe UI", 13, "bold"), anchor="w").pack(side="left", fill="x", expand=True)
            tk.Button(
                heading, text="Zobacz", command=lambda section=key: self.select_page(section),
                bg=WHITE, fg=TEAL, activebackground=WHITE, activeforeground=TEAL_DARK,
                relief="flat", borderwidth=0, padx=0, font=("Segoe UI", 9, "bold"), cursor="hand2",
            ).pack(side="right")
            body = tk.Frame(card, bg=WHITE)
            body.pack(fill="both", expand=True)
            self.preview_lists[key] = body

    def _settings_card(self, parent: tk.Misc, title: str, *, pady: tuple[int, int] = (0, 14)) -> tk.Frame:
        card = tk.Frame(parent, bg=WHITE, highlightbackground=LINE, highlightthickness=1, padx=22, pady=18)
        card.pack(fill="x", pady=pady)
        tk.Label(card, text=title, bg=WHITE, fg=INK, font=("Segoe UI", 13, "bold"), anchor="w").pack(fill="x", pady=(0, 10))
        return card

    def _scrollable_page(self, name: str) -> tk.Frame:
        outer = tk.Frame(self.content, bg=BG)
        self.page_frames[name] = outer
        canvas = tk.Canvas(outer, bg=BG, highlightthickness=0, borderwidth=0)
        scrollbar = ttk.Scrollbar(outer, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y", padx=(7, 0))
        canvas.pack(side="left", fill="both", expand=True)
        body = tk.Frame(canvas, bg=BG)
        window_id = canvas.create_window((0, 0), window=body, anchor="nw")

        def resize(_event: Any = None) -> None:
            canvas.itemconfigure(window_id, width=max(1, canvas.winfo_width()))
            canvas.configure(scrollregion=canvas.bbox("all"))

        canvas.bind("<Configure>", resize)
        body.bind("<Configure>", resize)
        self.scroll_canvases[name] = canvas
        return body

    def _page_wheel(self, event: Any) -> None:
        canvas = self.scroll_canvases.get(self.current_page)
        if canvas is None or event.widget.winfo_toplevel() is not self.root:
            return
        if getattr(event, "num", None) == 4:
            units = -3
        elif getattr(event, "num", None) == 5:
            units = 3
        else:
            delta = getattr(event, "delta", 0)
            units = -int(delta / 120) if abs(delta) >= 120 else (-1 if delta > 0 else 1)
        canvas.yview_scroll(units, "units")

    def _build_settings(self) -> None:
        frame = self._scrollable_page("settings")
        account = self._settings_card(frame, "Konto Synergia")
        self.account_status = tk.Label(account, text="", bg=WHITE, fg=MUTED, font=("Segoe UI", 10), anchor="w")
        self.account_status.pack(fill="x", pady=(0, 10))
        row = tk.Frame(account, bg=WHITE)
        row.pack(fill="x")
        ttk.Button(row, text="Połącz / zmień konto", command=self.show_login, style="Quiet.TButton").pack(side="left")
        ttk.Button(row, text="Włącz demo", command=lambda: self.call("demo"), style="Quiet.TButton").pack(side="left", padx=(10, 0))

        automatic = self._settings_card(frame, "Odświeżanie i powiadomienia")
        interval_row = tk.Frame(automatic, bg=WHITE)
        interval_row.pack(fill="x", pady=(0, 7))
        tk.Label(interval_row, text="Sprawdzaj nowe dane co", bg=WHITE, fg=INK, font=("Segoe UI", 10)).pack(side="left", padx=(0, 10))
        self.interval_var = tk.StringVar(value="15")
        self.interval_combo = ttk.Combobox(interval_row, textvariable=self.interval_var, values=("10", "15", "30", "60"), state="readonly", width=6)
        self.interval_combo.pack(side="left")
        tk.Label(interval_row, text="minut", bg=WHITE, fg=INK, font=("Segoe UI", 10)).pack(side="left", padx=(8, 0))
        self.notifications_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(automatic, text="Pokazuj powiadomienia o nowych danych", variable=self.notifications_var, style="Panel.TCheckbutton").pack(anchor="w", pady=(0, 5))
        tk.Label(
            automatic,
            text="Pozostaw aplikację uruchomioną lub zminimalizowaną. Zamknięcie okna zatrzymuje odświeżanie i powiadomienia.",
            bg=WHITE, fg=MUTED, font=("Segoe UI", 9), anchor="w", justify="left", wraplength=820,
        ).pack(fill="x", pady=(0, 13))
        preference_actions = tk.Frame(automatic, bg=WHITE)
        preference_actions.pack(fill="x")
        ttk.Button(preference_actions, text="Zapisz ustawienia", command=self.save_preferences, style="Accent.TButton").pack(side="left")
        ttk.Button(preference_actions, text="Test powiadomienia", command=lambda: self.call("test_notification"), style="Quiet.TButton").pack(side="left", padx=(10, 0))
        self.interval_var.trace_add("write", self._preferences_changed)
        self.notifications_var.trace_add("write", self._preferences_changed)

        storage = self._settings_card(frame, "Dane na komputerze", pady=(0, 0))
        tk.Label(
            storage,
            text="Pobrane dane pozwalają przeglądać dziennik również bez połączenia.\nMożesz usunąć lokalną kopię danych i zapamiętane konto.",
            bg=WHITE, fg=MUTED, font=("Segoe UI", 10), anchor="w", justify="left",
        ).pack(fill="x", pady=(0, 13))
        ttk.Button(storage, text="Usuń lokalne dane i konto", command=self.forget, style="Danger.TButton").pack(anchor="w")

    def _preferences_changed(self, *_args: Any) -> None:
        if not self._applying_preferences:
            self._prefs_dirty = True

    def save_preferences(self) -> None:
        try:
            interval = int(self.interval_var.get())
        except ValueError:
            self.show_notice("Wybierz częstotliwość: 10, 15, 30 lub 60 minut.")
            return
        if interval not in {10, 15, 30, 60}:
            self.show_notice("Wybierz częstotliwość: 10, 15, 30 lub 60 minut.")
            return
        self._prefs_dirty = False
        self.call("set_preferences", interval, self.notifications_var.get())

    def forget(self) -> None:
        confirmed = messagebox.askyesno(
            "Usunąć lokalne dane i konto?",
            "Usunięte zostaną pobrane dane dziennika oraz zapamiętane dane logowania na tym komputerze.\n\nDane w Librusie pozostaną bez zmian. Kontynuować?",
            icon="warning", parent=self.root,
        )
        if confirmed:
            self.call("forget")

    def select_page(self, name: str) -> None:
        if name not in PAGES:
            return
        for frame in self.page_frames.values():
            frame.pack_forget()
        for view in self.views.values():
            view.frame.pack_forget()
        self.current_page = name
        title, subtitle = PAGES[name]
        self.page_title.configure(text=title)
        self.page_subtitle.configure(text=subtitle)
        for key, button in self.nav_buttons.items():
            active = key == name
            button.configure(bg="#25495d" if active else SIDEBAR, fg=WHITE if active else SIDEBAR_MUTED, font=("Segoe UI", 11, "bold" if active else "normal"))
        if name in SECTION_TITLES or name == "reminders":
            if name not in self.views:
                if name == "reminders":
                    from .reminder_view import RemindersView

                    self.views[name] = RemindersView(self)
                elif name == "schedule":
                    from .calendar_view import ScheduleView

                    self.views[name] = ScheduleView(self)
                else:
                    self.views[name] = SectionView(self, name)
            view = self.views[name]
            view.frame.pack(fill="both", expand=True)
            view.render()
        else:
            self.page_frames[name].pack(fill="both", expand=True)
            if name == "overview":
                self._render_overview()

    def focus_search(self, _event: Any = None) -> str:
        view = self.views.get(self.current_page)
        if view:
            view.search_entry.focus_set()
            view.search_entry.selection_range(0, "end")
        return "break"

    def empty_text(self, section: str) -> str:
        mode = self.state.get("mode", "idle")
        updated = self.state.get("updated_at", {}).get(section)
        errors = self.state.get("errors", {})
        if mode == "idle" and not updated:
            return "Połącz konto lub włącz demo, aby zobaczyć dane."
        if not updated:
            if section in errors:
                return "Nie udało się jeszcze pobrać danych tej sekcji. Szczegóły błędu znajdziesz powyżej."
            return "Ta sekcja nie została jeszcze pobrana."
        return "Brak pozycji w pobranych danych tej sekcji."

    def _upcoming(self) -> list[dict]:
        items = _ordered_items(list(self.state.get("sections", {}).get("schedule") or []), "schedule")
        today = datetime.now().date()
        upcoming = []
        for item in items:
            parsed = _parse_datetime(item.get("when"))
            if parsed is None:
                upcoming.append(item)
                continue
            if parsed.tzinfo is not None:
                parsed = parsed.astimezone()
            if parsed.date() >= today:
                upcoming.append(item)
        return upcoming

    def _render_overview(self) -> None:
        if not hasattr(self, "welcome"):
            return
        state = self.state
        sections = state.get("sections", {})
        updated = state.get("updated_at", {})
        if state.get("mode") == "idle" and not any(sections.values()):
            self.welcome.pack(fill="x", pady=(0, 18), before=self.summary_grid)
        else:
            self.welcome.pack_forget()
        upcoming = self._upcoming()
        metrics = {
            "messages": sum(bool(item.get("unread")) for item in sections.get("messages", [])),
            "grades": len(sections.get("grades", [])),
            "schedule": len(upcoming),
        }
        for key, metric in metrics.items():
            known = bool(updated.get(key)) or bool(sections.get(key))
            self.metric_values[key].configure(text=str(metric) if known else "—")
            note = {
                "messages": "W pobranej liście wiadomości",
                "grades": "W ostatnio pobranych danych",
                "schedule": "Dzisiaj i w kolejnych dniach",
            }[key] if known else "Oczekiwanie na pierwszy odczyt"
            self.metric_notes[key].configure(text=note)
        errors = state.get("errors", {})
        if errors:
            names = [SECTION_TITLES.get(key, {"connection": "Połączenie", "storage": "Zapis danych"}.get(key, key)) for key in errors]
            self.overview_errors.configure(text="Sprawdź komunikaty: " + ", ".join(names) + ". Sekcje mogą pokazywać poprzedni odczyt.")
            self.overview_errors.pack(fill="x", pady=(14, 0), before=self.preview_grid)
        else:
            self.overview_errors.pack_forget()
        for key, body in self.preview_lists.items():
            for child in body.winfo_children():
                child.destroy()
            items = upcoming if key == "schedule" else _ordered_items(list(sections.get(key) or []), key)
            if not items:
                text = "Brak nadchodzących wydarzeń w pobranych danych." if key == "schedule" and updated.get(key) else self.empty_text(key)
                tk.Label(body, text=text, bg=WHITE, fg=MUTED, font=("Segoe UI", 10), wraplength=335, justify="left", anchor="nw", pady=16).pack(fill="x")
                continue
            for index, item in enumerate(items[:4]):
                if index:
                    tk.Frame(body, bg=LINE, height=1).pack(fill="x", pady=11)
                row = tk.Frame(body, bg=WHITE)
                row.pack(fill="x")
                heading = tk.Frame(row, bg=WHITE)
                heading.pack(fill="x")
                title = str(item.get("title") or "Bez tytułu")
                tk.Label(heading, text=title, bg=WHITE, fg=INK, font=("Segoe UI", 10, "bold"), anchor="w", justify="left", wraplength=315).pack(fill="x")
                meta = " · ".join(str(part) for part in (item.get("subtitle"), _when(item.get("when"), compact=True)) if part and part != "—")
                tk.Label(row, text=meta, bg=WHITE, fg=MUTED, font=("Segoe UI", 9), anchor="w", justify="left", wraplength=315).pack(fill="x", pady=(4, 0))
                for widget in (row, *row.winfo_children(), *heading.winfo_children()):
                    widget.bind("<Button-1>", lambda _event, section=key: self.select_page(section))
                    widget.configure(cursor="hand2")

    def _render_state(self, payload: dict) -> None:
        self.state = {**self.state, **payload}
        state = self.state
        mode = state.get("mode", "idle")
        busy = bool(state.get("busy"))
        status = str(state.get("status") or "")
        if len(status) > 70:
            status = status[:67] + "…"
        self.status_label.configure(text=status)
        self.status_dot.configure(fg=WARNING_FG if busy or mode == "offline" else TEAL if mode in {"live", "demo"} else MUTED)
        self.refresh_button.configure(text="Pobieranie…" if busy else "Odśwież", state="disabled" if busy or mode == "idle" else "normal")
        self.login_button.configure(text="Zmień konto" if mode in {"live", "offline"} else "Połącz konto", state="disabled" if busy else "normal")
        last_sync = state.get("last_sync")
        sync_text = f"Ostatni odczyt: {_when(last_sync)}" if last_sync else "Brak odczytu"
        if state.get("next_sync") and mode == "live":
            sync_text += f"\nNastępny: {_when(state['next_sync'])}"
        self.sync_label.configure(text=sync_text)
        modes = {"idle": "Konto niepołączone", "demo": "Dane demonstracyjne", "live": "Konto połączone", "offline": "Dane z lokalnej kopii"}
        self.sidebar_mode.configure(text=modes.get(mode, "LibrusApp"))
        self.account_status.configure(text=modes.get(mode, "") + (". Dane demonstracyjne nie pochodzą z konta w szkole." if mode == "demo" else "."))
        global_errors = [str(state.get("errors", {}).get(key, "")) for key in ("connection", "storage")]
        if any(global_errors):
            self.global_error_label.configure(text="\n".join(message for message in global_errors if message))
            self.global_error_label.pack(fill="x", before=self.content)
        else:
            self.global_error_label.pack_forget()
        if mode == "demo":
            self.banner.configure(bg=WARNING_BG)
            self.banner_text.configure(text="Dane demonstracyjne — przykładowe wpisy, bez połączenia z Librusem.", bg=WARNING_BG, fg=WARNING_FG)
            self.demo_change_button.pack(side="right", padx=(15, 0))
            self.demo_change_button.configure(state="disabled" if busy else "normal")
            self.banner.pack(fill="x", before=self.content)
        elif mode == "offline":
            self.banner.configure(bg=WARNING_BG)
            self.banner_text.configure(text="Wyświetlasz zapisaną kopię. Sprawdź ostatni odczyt, aby ocenić aktualność danych.", bg=WARNING_BG, fg=WARNING_FG)
            self.demo_change_button.pack_forget()
            self.banner.pack(fill="x", before=self.content)
        else:
            self.banner.pack_forget()
        unread = sum(bool(item.get("unread")) for item in state.get("sections", {}).get("messages", []))
        self.nav_buttons["messages"].configure(text=f"Wiadomości   {unread}" if unread else "Wiadomości")
        pending = sum(r["status"] == "pending" for r in state.get("reminders", []))
        self.nav_buttons["reminders"].configure(text=f"Przypomnienia   {pending}" if pending else "Przypomnienia")
        if not self._prefs_dirty:
            self._applying_preferences = True
            self.interval_var.set(str(state.get("interval", 15)))
            self.notifications_var.set(bool(state.get("notifications", True)))
            self._applying_preferences = False
        self._render_overview()
        for view in self.views.values():
            view.render()

    def show_login(self) -> None:
        if self.login_dialog and self.login_dialog.winfo_exists():
            self.login_dialog.lift()
            self.login_dialog.focus_force()
            return
        dialog = tk.Toplevel(self.root)
        self.login_dialog = dialog
        dialog.title("Połącz konto Synergia")
        dialog.configure(bg=WHITE)
        dialog.resizable(False, False)
        dialog.transient(self.root)
        box = tk.Frame(dialog, bg=WHITE, padx=30, pady=26)
        box.pack(fill="both", expand=True)
        tk.Label(box, text="Połącz konto Synergia", bg=WHITE, fg=INK, font=("Segoe UI", 19, "bold"), anchor="w").pack(fill="x")
        tk.Label(
            box,
            text="Użyj loginu Synergia otrzymanego ze szkoły.\nE-mail do Konta LIBRUS jest innym sposobem logowania.",
            bg=WHITE, fg=MUTED, font=("Segoe UI", 10), justify="left", anchor="w",
        ).pack(fill="x", pady=(10, 20))
        tk.Label(box, text="Login Synergia", bg=WHITE, fg=INK, font=("Segoe UI", 10, "bold"), anchor="w").pack(fill="x", pady=(0, 5))
        login_var = tk.StringVar()
        login_entry = ttk.Entry(box, textvariable=login_var, width=49, style="Panel.TEntry")
        login_entry.pack(fill="x", pady=(0, 15))
        tk.Label(box, text="Hasło", bg=WHITE, fg=INK, font=("Segoe UI", 10, "bold"), anchor="w").pack(fill="x", pady=(0, 5))
        password_var = tk.StringVar()
        password_entry = ttk.Entry(box, textvariable=password_var, show="•", width=49, style="Panel.TEntry")
        password_entry.pack(fill="x", pady=(0, 13))
        remember_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(box, text="Zapamiętaj konto na tym komputerze", variable=remember_var, style="Panel.TCheckbutton").pack(anchor="w")
        tk.Label(box, text="Zaznacz tylko na swoim komputerze.", bg=WHITE, fg=MUTED, font=("Segoe UI", 9), anchor="w").pack(fill="x", pady=(1, 15))
        error_label = tk.Label(box, text="", bg=WHITE, fg=ERROR_FG, font=("Segoe UI", 9), anchor="w")
        error_label.pack(fill="x")
        buttons = tk.Frame(box, bg=WHITE)
        buttons.pack(fill="x", pady=(10, 0))

        def cancel() -> None:
            password_var.set("")
            dialog.destroy()
            self.login_dialog = None

        def submit(_event: Any = None) -> None:
            login = login_var.get().strip()
            password = password_var.get()
            if not login or not password:
                error_label.configure(text="Wpisz login i hasło.")
                (login_entry if not login else password_entry).focus_set()
                return
            remember = bool(remember_var.get())
            password_var.set("")
            dialog.destroy()
            self.login_dialog = None
            self.call("connect", login, password, remember=remember)
            password = ""

        ttk.Button(buttons, text="Anuluj", command=cancel, style="Quiet.TButton").pack(side="left")
        ttk.Button(buttons, text="Połącz i pobierz dane", command=submit, style="Accent.TButton").pack(side="right")
        dialog.protocol("WM_DELETE_WINDOW", cancel)
        dialog.bind("<Escape>", lambda _event: cancel())
        dialog.bind("<Return>", submit)
        dialog.update_idletasks()
        self._center_dialog(dialog)
        dialog.grab_set()
        login_entry.focus_set()

    def show_text(self, title: str, text: str) -> None:
        dialog = tk.Toplevel(self.root)
        dialog.title(title)
        dialog.geometry("760x560")
        dialog.minsize(560, 350)
        dialog.configure(bg=WHITE)
        dialog.transient(self.root)
        body = tk.Frame(dialog, bg=WHITE, padx=25, pady=22)
        body.pack(fill="both", expand=True)
        heading = tk.Label(body, text=title, bg=WHITE, fg=INK, font=("Segoe UI", 16, "bold"), anchor="w", wraplength=685, justify="left")
        heading.pack(fill="x", pady=(0, 15))
        row = tk.Frame(body, bg=WHITE)
        row.pack(fill="both", expand=True)
        content = _plain_text(row, height=14)
        scrollbar = ttk.Scrollbar(row, orient="vertical", command=content.yview)
        content.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")
        content.pack(side="left", fill="both", expand=True)
        _set_text(content, str(text))
        ttk.Button(body, text="Zamknij", command=dialog.destroy, style="Quiet.TButton").pack(anchor="e", pady=(18, 0))
        dialog.bind("<Escape>", lambda _event: dialog.destroy())
        dialog.update_idletasks()
        self._center_dialog(dialog)
        dialog.focus_set()

    def show_reminder_dialog(self, *, item: dict | None = None, reminder: dict | None = None) -> None:
        from .reminder_view import show_reminder_dialog

        show_reminder_dialog(self, item=item, reminder=reminder)

    def _center_dialog(self, dialog: tk.Toplevel) -> None:
        width = dialog.winfo_width() or dialog.winfo_reqwidth()
        height = dialog.winfo_height() or dialog.winfo_reqheight()
        x = max(0, self.root.winfo_rootx() + (self.root.winfo_width() - width) // 2)
        y = max(0, self.root.winfo_rooty() + (self.root.winfo_height() - height) // 2)
        dialog.geometry(f"+{x}+{y}")

    def show_notice(self, text: str) -> None:
        if self.closed:
            return
        if self.notice_job:
            self.root.after_cancel(self.notice_job)
        self.notice_label.configure(text=str(text))
        self.notice_label.pack(fill="x", before=self.content)
        self.notice_job = self.root.after(10000, self._clear_notice)

    def _clear_notice(self) -> None:
        self.notice_label.pack_forget()
        self.notice_job = None

    def call(self, method: str, *args: Any, **kwargs: Any) -> Any:
        if self.closed:
            return None
        try:
            return getattr(self.controller, method)(*args, **kwargs)
        except Exception:
            # Do not display arbitrary exception strings: some libraries include
            # request details in them. The controller emits safe user messages.
            self.show_notice("Nie udało się wykonać tej czynności. Spróbuj ponownie; jeśli problem wraca, uruchom aplikację ponownie.")
            return None

    def _drain_events(self) -> None:
        if self.closed:
            return
        for _ in range(100):
            try:
                event_type, payload = self.controller.events.get_nowait()
            except queue.Empty:
                break
            if event_type == "state" and isinstance(payload, dict):
                self._render_state(payload)
            elif event_type == "notice":
                self.show_notice(str(payload))
            elif event_type == "message" and isinstance(payload, dict):
                self.show_text(str(payload.get("title") or "Wiadomość"), str(payload.get("text") or "Brak treści."))
            elif event_type == "reminder" and isinstance(payload, dict):
                from .data import LABELS

                details = (str(payload.get("title") or "Przypomnienie") + "\n\n"
                           + LABELS.get(payload.get("kind"), "Wpis") + ": " + str(payload.get("source_title") or "")
                           + "\nTermin: " + _when(payload.get("due_at")))
                self.show_text("Twoje przypomnienie", details)
        self.root.after(150, self._drain_events)

    def close(self) -> None:
        if self.closed:
            return
        self.closed = True
        try:
            self.controller.stop()
        finally:
            self.root.destroy()

    def run(self) -> None:
        self.root.mainloop()


def create_app(controller: Any, *, restore: bool = True) -> SchoolPanelApp:
    """Build the UI without entering mainloop (also useful for screenshots)."""
    return SchoolPanelApp(controller, restore=restore)


def run(controller: Any) -> None:
    """Start the normal desktop application and restore its saved account."""
    create_app(controller).run()
