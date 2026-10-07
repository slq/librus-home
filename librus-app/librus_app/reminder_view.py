"""Personal reminder editor and a list of pending and completed alerts."""

from datetime import datetime, timedelta
import tkinter as tk
from tkinter import ttk

from .data import LABELS
from .reminders import due_datetime
from .ui import INK, MUTED, WHITE, ERROR_FG, SectionView, _when


class RemindersView(SectionView):
    def __init__(self, app):
        super().__init__(app, "reminders")
        self.tree.heading("title", text="Treść przypomnienia")
        self.tree.heading("subtitle", text="Sekcja / status")
        self.tree.heading("when", text="Termin")
        self.legend.configure(text="Wybierz przypomnienie, aby je zmienić, usunąć lub wrócić do wpisu.")
        self.open_button.configure(text="Pokaż wpis", command=self.open_source)
        self.edit_button = ttk.Button(self.toolbar, text="Zmień…", command=self.edit_selected,
                                      style="Quiet.TButton", state="disabled")
        self.edit_button.pack(side="left", padx=(8, 0))
        self.delete_button = ttk.Button(self.toolbar, text="Usuń", command=self.delete_selected,
                                        style="Quiet.TButton", state="disabled")
        self.delete_button.pack(side="left", padx=(8, 0))

    def filtered_items(self) -> list[dict]:
        rows = []
        query = self.search_var.get().casefold().strip()
        reminders = sorted(self.app.state.get("reminders", []),
                           key=lambda r: (r["status"] != "pending", due_datetime(r["due_at"]).timestamp()))
        for reminder in reminders:
            status = "Zaplanowane" if reminder["status"] == "pending" else "Wykonane" if reminder.get("system_sent") else "Wykonane w aplikacji"
            source = LABELS[reminder["kind"]] + ": " + reminder["source_title"]
            details = (reminder["title"] + "\n\n" + source + "\nTermin: " + _when(reminder["due_at"])
                       + "\nStatus: " + status + "\nTreść w Windows: " + ("tak" if reminder["show_text"] else "ukryta"))
            if reminder.get("fired_at"):
                details += "\nUruchomiono: " + _when(reminder["fired_at"])
            row = {"id": reminder["id"], "title": reminder["title"], "subtitle": LABELS[reminder["kind"]] + " · " + status,
                   "when": reminder["due_at"], "details": details, "reminder": reminder}
            if not query or query in " ".join((details, row["subtitle"])).casefold():
                rows.append(row)
        return rows

    def render(self):
        super().render()
        count = sum(r["status"] == "pending" for r in self.app.state.get("reminders", []))
        self.source_label.configure(text=f"Zaplanowane: {count}. Alerty działają, gdy aplikacja jest uruchomiona, także bez internetu.")
        if not self.items_by_row:
            self.empty_label.configure(text="Brak pasujących przypomnień." if self.search_var.get().strip() else
                                       "Wybierz ogłoszenie, wydarzenie lub wiadomość i kliknij „Przypomnij mi…”.")

    def _render_detail(self, item):
        super()._render_detail(item)
        for name in ("edit_button", "delete_button"):
            if hasattr(self, name):
                getattr(self, name).configure(state="normal" if item and not self.app.state.get("busy") else "disabled")

    def edit_selected(self):
        if self.selected_item:
            self.app.show_reminder_dialog(reminder=self.selected_item["reminder"])

    def delete_selected(self):
        if self.selected_item:
            self.app.call("delete_reminder", self.selected_item["id"])

    def open_source(self):
        if not self.selected_item:
            return
        reminder = self.selected_item["reminder"]
        self.app.select_page(reminder["kind"])
        view = self.app.views[reminder["kind"]]
        view.search_var.set("")
        if reminder["kind"] == "schedule":
            view.set_calendar(False)
        view.render()
        row = next((key for key, item in view.items_by_row.items() if item["id"] == reminder["item_id"]), None)
        if row:
            view.tree.selection_set(row)
            view.tree.see(row)
            view._select()
        else:
            self.app.show_notice("Wpis nie znajduje się już w pobranej liście. Przypomnienie zachowuje jego tytuł.")


def show_reminder_dialog(app, *, item=None, reminder=None):
    if app.reminder_dialog and app.reminder_dialog.winfo_exists():
        app.reminder_dialog.lift()
        return
    if item is None and reminder is None:
        return
    kind = reminder["kind"] if reminder else item["kind"]
    source_title = (reminder["source_title"] if reminder else item["title"])[:300]
    due = due_datetime(reminder["due_at"]) if reminder else datetime.now().astimezone() + timedelta(hours=1)
    dialog = tk.Toplevel(app.root)
    app.reminder_dialog = dialog
    dialog.title("Zmień przypomnienie" if reminder else "Przypomnij mi")
    dialog.configure(bg=WHITE)
    dialog.resizable(False, False)
    dialog.transient(app.root)
    box = tk.Frame(dialog, bg=WHITE, padx=25, pady=22)
    box.pack(fill="both", expand=True)
    tk.Label(box, text=dialog.title(), bg=WHITE, fg=INK, font=("Segoe UI", 18, "bold")).pack(anchor="w")
    tk.Label(box, text=LABELS[kind] + ": " + source_title, bg=WHITE, fg=MUTED, font=("Segoe UI", 10),
             wraplength=510, justify="left", anchor="w").pack(fill="x", pady=(8, 18))
    title_var = tk.StringVar(value=reminder["title"] if reminder else source_title[:200])
    date_var = tk.StringVar(value=due.strftime("%d.%m.%Y"))
    time_var = tk.StringVar(value=due.strftime("%H:%M"))
    show_var = tk.BooleanVar(value=reminder["show_text"] if reminder else True)
    tk.Label(box, text="Treść przypomnienia", bg=WHITE, fg=INK).pack(anchor="w", pady=(0, 4))
    title_entry = ttk.Entry(box, textvariable=title_var, width=60, style="Panel.TEntry")
    title_entry.pack(fill="x", pady=(0, 14))
    when = tk.Frame(box, bg=WHITE)
    when.pack(fill="x")
    for label, variable, width in (("Data (dd.mm.rrrr)", date_var, 14), ("Godzina (gg:mm)", time_var, 9)):
        column = tk.Frame(when, bg=WHITE)
        column.pack(side="left", padx=(0, 14))
        tk.Label(column, text=label, bg=WHITE, fg=INK).pack(anchor="w", pady=(0, 4))
        ttk.Entry(column, textvariable=variable, width=width, style="Panel.TEntry").pack()

    def set_time(value):
        date_var.set(value.strftime("%d.%m.%Y"))
        time_var.set(value.strftime("%H:%M"))

    presets = tk.Frame(box, bg=WHITE)
    presets.pack(fill="x", pady=(10, 12))
    for label, minutes in (("Za 15 minut", 15), ("Za godzinę", 60)):
        ttk.Button(presets, text=label, style="Quiet.TButton",
                   command=lambda m=minutes: set_time(datetime.now() + timedelta(minutes=m))).pack(side="left", padx=(0, 8))
    ttk.Button(presets, text="Jutro 09:00", style="Quiet.TButton",
               command=lambda: set_time((datetime.now() + timedelta(days=1)).replace(hour=9, minute=0))).pack(side="left")
    ttk.Checkbutton(box, text="Pokaż treść w powiadomieniu Windows", variable=show_var, style="Panel.TCheckbutton").pack(anchor="w")
    tk.Label(box, text="Godzina lokalna tego komputera. Pozostaw aplikację uruchomioną lub zminimalizowaną.\n"
                       "Po zamknięciu zaległe przypomnienie pojawi się przy następnym uruchomieniu."
                       + ("\nW demo przypomnienia są tymczasowe." if app.state.get("mode") == "demo" else ""),
             bg=WHITE, fg=MUTED, font=("Segoe UI", 9), wraplength=510, justify="left").pack(fill="x", pady=(5, 10))
    error_label = tk.Label(box, bg=WHITE, fg=ERROR_FG, anchor="w", wraplength=510, justify="left")
    error_label.pack(fill="x")

    def close():
        dialog.destroy()
        app.reminder_dialog = None

    def submit():
        try:
            parsed = datetime.strptime(date_var.get().strip() + " " + time_var.get().strip(), "%d.%m.%Y %H:%M")
            due_at = due_datetime(parsed.isoformat()).isoformat(timespec="seconds")
            if not title_var.get().strip() or len(title_var.get().strip()) > 200:
                raise ValueError("Wpisz treść przypomnienia (od 1 do 200 znaków).")
            if due_datetime(due_at).timestamp() <= datetime.now().timestamp():
                raise ValueError("Termin przypomnienia musi być w przyszłości.")
        except ValueError as exc:
            error_label.configure(text=str(exc) if "przypomnienia" in str(exc) else "Podaj datę dd.mm.rrrr i godzinę gg:mm.")
            return
        if reminder:
            launched = app.call("update_reminder", reminder["id"], title_var.get(), due_at, show_var.get())
        else:
            launched = app.call("schedule_reminder", kind, item["id"], title_var.get(), due_at, show_var.get())
        if launched:
            close()
        else:
            error_label.configure(text="Nie rozpoczęto zapisu. Poczekaj na zakończenie odświeżania i spróbuj ponownie.")

    actions = tk.Frame(box, bg=WHITE)
    actions.pack(fill="x", pady=(12, 0))
    ttk.Button(actions, text="Anuluj", command=close, style="Quiet.TButton").pack(side="left")
    save_button = ttk.Button(actions, text="Zapisz przypomnienie", command=submit, style="Accent.TButton")
    save_button.pack(side="right")
    dialog.form_vars = {"title": title_var, "date": date_var, "time": time_var, "show_text": show_var}
    dialog.save_button = save_button
    dialog.error_label = error_label
    dialog.protocol("WM_DELETE_WINDOW", close)
    dialog.bind("<Escape>", lambda _event: close())
    dialog.update_idletasks()
    app._center_dialog(dialog)
    dialog.grab_set()
    title_entry.focus_set()
