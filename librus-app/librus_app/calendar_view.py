"""Monthly calendar and searchable list for the existing schedule snapshot."""

from __future__ import annotations

import calendar
import tkinter as tk
from datetime import date
from tkinter import ttk

from .ui import BG, INK, LINE, MUTED, TEAL, TEAL_PALE, WHITE, SectionView, _parse_datetime, _when


MONTHS = ("", "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
          "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień")
WEEKDAYS = ("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Nd")


def shift_month(month: date, offset: int) -> date:
    year, index = divmod(month.year * 12 + month.month - 1 + offset, 12)
    return date(year, index + 1, 1)


def item_day(item: dict) -> date | None:
    parsed = _parse_datetime(item.get("when"))
    if parsed is None:
        return None
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone()
    return parsed.date()


class ScheduleView(SectionView):
    def __init__(self, app) -> None:
        self.month = date.today().replace(day=1)
        self.selected_day: date | None = None
        self.show_calendar = True
        super().__init__(app, "schedule", pane_orientation="horizontal")
        self.detail_inner.configure(padx=12, pady=6)
        self.detail_meta.pack_configure(pady=(2, 4))

        modes = tk.Frame(self.frame, bg=BG)
        modes.pack(fill="x", pady=(0, 8), before=self.source_label)
        self.calendar_button = ttk.Button(modes, text="Kalendarz + lista", command=lambda: self.set_calendar(True))
        self.calendar_button.pack(side="left")
        self.list_button = ttk.Button(modes, text="Lista", command=lambda: self.set_calendar(False))
        self.list_button.pack(side="left", padx=(8, 0))
        self.source_label.pack_forget()
        self.source_label.pack(in_=modes, side="left", padx=(12, 0), fill="x", expand=True)
        self.details_button = ttk.Button(modes, text="Szczegóły wydarzenia", command=self._show_selected_dialog,
                                         style="Quiet.TButton", state="disabled")
        self.details_button.pack(side="right")

        self.calendar_frame = tk.Frame(self.frame, bg=WHITE, highlightbackground=LINE, highlightthickness=1,
                                       padx=12, pady=8)
        header = tk.Frame(self.calendar_frame, bg=WHITE)
        header.pack(fill="x", pady=(0, 6))
        ttk.Button(header, text="‹", width=3, style="Calendar.TButton", command=lambda: self.move_month(-1)).pack(side="left")
        self.month_title = tk.Label(header, bg=WHITE, fg=INK, font=("Segoe UI", 12, "bold"))
        self.month_title.pack(side="left", padx=12)
        ttk.Button(header, text="›", width=3, style="Calendar.TButton", command=lambda: self.move_month(1)).pack(side="left")
        ttk.Button(header, text="Dzisiaj", command=self.go_today, style="Calendar.TButton").pack(side="right")
        self.all_days_button = ttk.Button(header, text="Cały miesiąc", command=self.clear_day, style="Calendar.TButton")
        self.all_days_button.pack(side="right", padx=(0, 8))

        self.days_frame = tk.Frame(self.calendar_frame, bg=LINE)
        self.days_frame.pack(fill="x")
        self.calendar_note = tk.Label(self.calendar_frame, bg=WHITE, fg=MUTED, font=("Segoe UI", 9),
                                      anchor="w", justify="left", wraplength=760)
        self.calendar_note.pack(fill="x", pady=(6, 0))
        self.day_buttons: dict[date, tk.Button] = {}
        self.day_items: dict[date, list[dict]] = {}
        self.set_calendar(True)

    def set_calendar(self, visible: bool) -> None:
        self.show_calendar = visible
        self.selected_day = None
        self.calendar_button.configure(style="Accent.TButton" if visible else "Quiet.TButton")
        self.list_button.configure(style="Quiet.TButton" if visible else "Accent.TButton")
        if visible:
            self.calendar_frame.pack(fill="x", pady=(0, 10), before=self.panes)
        else:
            self.calendar_frame.pack_forget()
        # Display details beside the list to leave both usable below the calendar.
        self.tree.column("title", width=210, minwidth=120)
        self.tree.column("subtitle", width=120, minwidth=70)
        self.tree.column("when", width=115, minwidth=100)
        self.legend.configure(text="+ Nowa lub zmieniona pozycja · Dwuklik otwiera szczegóły." if visible else
                              "+ Nowa lub zmieniona pozycja     Kliknij, aby zobaczyć szczegóły.")
        self.render()

    def move_month(self, offset: int) -> None:
        try:
            self.month = shift_month(self.month, offset)
        except ValueError:
            return
        self.selected_day = None
        self.render()

    def go_today(self) -> None:
        self.month = date.today().replace(day=1)
        self.selected_day = date.today()
        self.render()

    def clear_day(self) -> None:
        self.selected_day = None
        self.render()

    def select_day(self, day: date) -> None:
        self.month = day.replace(day=1)
        self.selected_day = None if self.selected_day == day else day
        self.render()

    def filtered_items(self) -> list[dict]:
        items = super().filtered_items()
        if not self.show_calendar:
            return items
        if self.selected_day:
            return [item for item in items if item_day(item) == self.selected_day]
        # Undated entries remain accessible on the list, even though they
        # cannot be placed on the calendar.
        return [item for item in items if item_day(item) is None or
                item_day(item).replace(day=1) == self.month]

    def render(self) -> None:
        super().render()
        if not hasattr(self, "calendar_frame"):
            return
        if self.show_calendar:
            self.render_calendar()
            if not self.items_by_row and self.app.state.get("updated_at", {}).get("schedule"):
                self.empty_label.configure(text="Brak wydarzeń pasujących do wybranego dnia lub miesiąca i wyszukiwania.")

    def _render_detail(self, item: dict | None) -> None:
        super()._render_detail(item)
        if hasattr(self, "details_button"):
            self.details_button.configure(state="normal" if item else "disabled")

    def render_calendar(self) -> None:
        for child in self.days_frame.winfo_children():
            child.destroy()
        self.day_buttons.clear()
        self.day_items = {}
        for item in super().filtered_items():
            day = item_day(item)
            if day:
                self.day_items.setdefault(day, []).append(item)
        self.month_title.configure(text=f"{MONTHS[self.month.month]} {self.month.year}")
        self.all_days_button.configure(state="normal" if self.selected_day else "disabled")
        for column, weekday in enumerate(WEEKDAYS):
            self.days_frame.columnconfigure(column, weight=1, uniform="day")
            tk.Label(self.days_frame, text=weekday, bg=WHITE, fg=MUTED, font=("Segoe UI", 9, "bold"),
                     pady=3).grid(row=0, column=column, sticky="ew", padx=1, pady=1)
        today = date.today()
        for row, week in enumerate(calendar.Calendar(firstweekday=0).monthdatescalendar(self.month.year, self.month.month), 1):
            for column, day in enumerate(week):
                events = self.day_items.get(day, [])
                selected = day == self.selected_day
                current_month = day.month == self.month.month
                bg = TEAL if selected else TEAL_PALE if events and current_month else WHITE if current_month else BG
                fg = WHITE if selected else INK if current_month else MUTED
                text = str(day.day) + (f"  · {len(events)}" if events else "")
                if day == today:
                    text += " ★"
                if any(item.get("changed") for item in events):
                    text += " +"
                button = tk.Button(self.days_frame, text=text, command=lambda d=day: self.select_day(d),
                                   bg=bg, fg=fg, activebackground=TEAL_PALE, activeforeground=INK,
                                   font=("Segoe UI", 9, "bold" if events or day == today else "normal"),
                                   relief="flat", borderwidth=0, padx=2, pady=1, cursor="hand2",
                                   highlightthickness=1, highlightbackground=TEAL if day == today else bg,
                                   highlightcolor=TEAL)
                button.grid(row=row, column=column, sticky="ew", padx=1, pady=1)
                self.day_buttons[day] = button
        fetched = _parse_datetime(self.app.state.get("updated_at", {}).get("schedule"))
        if fetched is None:
            note = self.app.empty_text("schedule")
        else:
            fetched_month = fetched.astimezone().date().replace(day=1) if fetched.tzinfo else fetched.date().replace(day=1)
            if self.app.state.get("mode") != "demo" and self.month not in {fetched_month, shift_month(fetched_month, 1)}:
                note = "Ten miesiąc jest poza zakresem ostatniego odczytu. Dane mogą być niepełne."
            elif self.selected_day:
                note = f"Lista dla {_when(self.selected_day.isoformat())}. Kliknij dzień ponownie lub wybierz „Cały miesiąc”."
            else:
                note = "Kliknij dzień, aby zawęzić listę. Liczba obok daty: wydarzenia · ★ dzisiaj · + zmiany."
        self.calendar_note.configure(text=note)
