package pl.librushome.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native phone UI. No account secrets are stored in Activity saved state. */
public final class MainActivity extends Activity implements MobileRepository.Listener {
    private static final int BG = 0xfff4f6fa, INK = 0xff183047, MUTED = 0xff64748b;
    private static final int TEAL = 0xff087e8b, WHITE = Color.WHITE, DARK = 0xff172d40;
    private static final String[] KEYS = {"overview", "grades", "messages", "announcements", "schedule", "attendance", "timetable", "settings"};
    private static final String[] TITLES = {"Przegląd", "Oceny", "Wiadomości", "Ogłoszenia", "Terminarz", "Frekwencja", "Plan lekcji", "Ustawienia"};
    private MobileRepository repository;
    private JSONObject state = new JSONObject();
    private LinearLayout shell, list, filters;
    private TextView status, heading;
    private Button refresh, account;
    private ProgressBar progress;
    private ScrollView scroll;
    private EditText search;
    private final Map<String, Button> tabs = new LinkedHashMap<>();
    private final Map<String, String> queries = new HashMap<>();
    private String page = "overview";
    private int sort = 0;
    private boolean calendar = true, loginPrompted, updatingSearch;
    private YearMonth month = YearMonth.now();
    private LocalDate selectedDay;
    private Dialog loginDialog;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (saved != null) {
            page = saved.getString("page", "overview");
            calendar = saved.getBoolean("calendar", true);
            loginPrompted = saved.getBoolean("loginPrompted", false);
            try { month = YearMonth.parse(saved.getString("month", month.toString())); }
            catch (Exception ignored) { }
            String day = saved.getString("day");
            if (day != null) try { selectedDay = LocalDate.parse(day); } catch (Exception ignored) { }
            Bundle savedQueries = saved.getBundle("queries");
            if (savedQueries != null) for (String key : savedQueries.keySet()) queries.put(key, savedQueries.getString(key, ""));
        }
        repository = MobileRepository.get(this);
        buildShell();
        select(page);
        // Synthetic startup mode is useful for repeatable device smoke tests.
        if (saved == null && getIntent().getBooleanExtra("demo", false)) {
            loginPrompted = true;
            repository.demo();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        repository.observe(this);
        repository.open();
    }
    @Override protected void onStop() { repository.observe(null); super.onStop(); }
    @Override protected void onDestroy() {
        if (loginDialog != null) loginDialog.dismiss();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("page", page);
        out.putString("month", month.toString());
        if (selectedDay != null) out.putString("day", selectedDay.toString());
        out.putBoolean("calendar", calendar);
        out.putBoolean("loginPrompted", loginPrompted);
        Bundle values = new Bundle();
        for (Map.Entry<String, String> entry : queries.entrySet()) values.putString(entry.getKey(), entry.getValue());
        out.putBundle("queries", values);
        super.onSaveInstanceState(out);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, dp(3), 0, dp(3));
        return v;
    }
    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(14)); return drawable;
    }
    private Button button(String label, Runnable action, boolean accent) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13);
        b.setTextColor(accent ? WHITE : TEAL); b.setBackground(background(accent ? TEAL : WHITE));
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void weighted(LinearLayout parent, View child) { parent.addView(child, new LinearLayout.LayoutParams(0, -2, 1)); }
    private void spaced(LinearLayout parent, View child) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); parent.addView(child, p);
    }
    private LinearLayout card() {
        LinearLayout v = column(); v.setPadding(dp(16), dp(12), dp(16), dp(12)); v.setBackground(background(WHITE)); spaced(list, v); return v;
    }

    private void buildShell() {
        shell = column(); shell.setBackgroundColor(BG);
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout top = column(); top.setPadding(dp(18), dp(12), dp(18), dp(10)); top.setBackgroundColor(DARK);
        LinearLayout titleRow = row();
        weighted(titleRow, text("LibrusApp", 24, WHITE, true));
        account = button("Konto", this::showLogin, false); titleRow.addView(account);
        top.addView(titleRow);
        status = text("Przygotowuję aplikację…", 12, 0xffb7cedf, false); top.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true); top.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        shell.addView(top);

        HorizontalScrollView nav = new HorizontalScrollView(this); nav.setHorizontalScrollBarEnabled(true);
        LinearLayout tabRow = row(); tabRow.setPadding(dp(12), dp(10), dp(12), dp(10));
        for (int index = 0; index < KEYS.length; index++) {
            final String key = KEYS[index];
            Button tab = button(TITLES[index], () -> select(key), false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48)); p.rightMargin = dp(7);
            tabRow.addView(tab, p); tabs.put(key, tab);
        }
        nav.addView(tabRow); shell.addView(nav);
        LinearLayout section = row(); section.setPadding(dp(18), 0, dp(18), dp(8));
        heading = text("Przegląd", 23, INK, true); weighted(section, heading);
        refresh = button("Odśwież", repository::refresh, true); section.addView(refresh); shell.addView(section);
        filters = column(); filters.setPadding(dp(18), 0, dp(18), dp(8));
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(15); search.setHint("Szukaj w tej sekcji");
        search.setContentDescription("Wyszukiwanie w bieżącej sekcji");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (!updatingSearch) { queries.put(page, s.toString()); renderList(); }
            }
            public void afterTextChanged(Editable e) { }
        });
        filters.addView(search);
        LinearLayout filterActions = row();
        Spinner sortBy = new Spinner(this);
        ArrayAdapter<String> choices = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Domyślna kolejność", "Najnowsze", "Najstarsze", "Tytuł A–Z"});
        sortBy.setAdapter(choices);
        sortBy.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int index, long id) { sort = index; renderList(); }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        weighted(filterActions, sortBy);
        filterActions.addView(button("Wyczyść", () -> search.setText(""), false)); filters.addView(filterActions);
        shell.addView(filters);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        list = column(); list.setPadding(dp(18), dp(4), dp(18), dp(24)); scroll.addView(list);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
    }

    private String title(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return TITLES[i];
        return key;
    }
    private JSONObject object(JSONObject source, String key) { JSONObject v = source.optJSONObject(key); return v == null ? new JSONObject() : v; }
    private List<JSONObject> items(String key) {
        JSONArray rows = object(state, "sections").optJSONArray(key);
        List<JSONObject> result = new ArrayList<>();
        if (rows != null) for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) result.add(rows.optJSONObject(i));
        return result;
    }

    private void select(String key) {
        if (!tabs.containsKey(key)) key = "overview";
        page = key;
        heading.setText(title(page));
        for (Map.Entry<String, Button> tab : tabs.entrySet()) {
            boolean selected = tab.getKey().equals(page);
            tab.getValue().setBackground(background(selected ? TEAL : WHITE)); tab.getValue().setTextColor(selected ? WHITE : TEAL);
        }
        filters.setVisibility(page.equals("overview") || page.equals("settings") ? View.GONE : View.VISIBLE);
        updatingSearch = true; search.setText(queries.getOrDefault(page, "")); updatingSearch = false;
        renderList(); scroll.scrollTo(0, 0);
    }

    @Override public void onState(JSONObject value) {
        state = value;
        boolean busy = state.optBoolean("busy");
        String mode = state.optString("mode", "idle");
        status.setText((mode.equals("demo") ? "DEMO · " : mode.equals("offline") ? "KOPIA LOKALNA · " : "") + state.optString("status", "Przygotowuję aplikację…"));
        refresh.setEnabled(!busy && !mode.equals("idle")); account.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (mode.equals("demo")) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        int y = scroll.getScrollY(); renderList(); scroll.post(() -> scroll.scrollTo(0, y));
        if (state.optBoolean("ready") && state.optBoolean("needs_login") && !busy && !loginPrompted) {
            loginPrompted = true; showLogin();
        }
    }

    private void notice(String value) {
        if (value == null || value.trim().isEmpty()) return;
        TextView warning = text(value, 14, 0xff994835, false); warning.setPadding(dp(14), dp(12), dp(14), dp(12));
        warning.setBackground(background(0xfffff0ed)); spaced(list, warning);
    }
    private void renderList() {
        if (list == null) return;
        list.removeAllViews();
        notice(state.optString("storage_error")); notice(state.optString("operation_error"));
        notice(object(state, "errors").optString("connection"));
        double retry = state.optDouble("retry_after", 0);
        if (retry * 1000 > System.currentTimeMillis()) {
            notice("Pobieranie wstrzymane do " + java.time.Instant.ofEpochSecond((long) retry).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
        }
        if (page.equals("overview")) { overview(); return; }
        if (page.equals("settings")) { settings(); return; }
        String fetched = object(state, "updated_at").optString(page);
        list.addView(text(fetched.trim().isEmpty() ? "Sekcja jeszcze niepobrana" : "Ostatni odczyt: " + DisplayData.date(fetched), 12, MUTED, false));
        notice(object(state, "errors").optString(page));
        String query = queries.getOrDefault(page, "").trim().toLowerCase(Locale.ROOT);
        List<JSONObject> all = items(page);
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject item : all) {
            String haystack = (item.optString("title") + " " + item.optString("subtitle") + " " + item.optString("when") + " " + item.optString("details")).toLowerCase(Locale.ROOT);
            if (haystack.contains(query)) filtered.add(item);
        }
        if (page.equals("schedule")) {
            LinearLayout modes = row();
            weighted(modes, button("Kalendarz + lista", () -> { calendar = true; selectedDay = null; renderList(); }, calendar));
            weighted(modes, button("Lista", () -> { calendar = false; selectedDay = null; renderList(); }, !calendar)); spaced(list, modes);
            if (calendar) {
                calendar(filtered);
                filtered.removeIf(item -> {
                    LocalDate day = DisplayData.day(item.optString("when"));
                    return selectedDay != null ? !selectedDay.equals(day) : day != null && !YearMonth.from(day).equals(month);
                });
            }
        }
        Comparator<JSONObject> byDate = Comparator.comparing(item -> item.optString("when"));
        boolean ascending = page.equals("schedule") || page.equals("timetable");
        filtered.sort(sort == 3 ? Comparator.comparing(item -> item.optString("title").toLowerCase(Locale.ROOT)) :
                sort == 1 ? byDate.reversed() : sort == 2 ? byDate : ascending ? byDate : byDate.reversed());
        list.addView(text("Pozycji: " + filtered.size(), 12, MUTED, false));
        if (filtered.isEmpty()) {
            String empty = !query.isEmpty() ? "Brak wyników. Zmień lub wyczyść wyszukiwanie." :
                    fetched.trim().isEmpty() ? "Połącz konto lub włącz demo. Jeśli odczyt się nie udał, sprawdź błąd powyżej." :
                    "Brak pozycji w pobranych danych dla tego widoku.";
            card().addView(text(empty, 15, MUTED, false));
        }
        for (JSONObject item : filtered) entry(item, page);
    }

    private void entry(JSONObject item, String kind) {
        LinearLayout c = card();
        String marker = item.optBoolean("unread") ? "● Nieprzeczytana" : item.optBoolean("changed") ? "+ Nowa lub zmieniona" : "";
        if (!marker.isEmpty()) c.addView(text(marker, 12, TEAL, true));
        c.addView(text(item.optString("title", "Bez tytułu"), 16, INK, true));
        c.addView(text(item.optString("subtitle"), 13, MUTED, false));
        c.addView(text(DisplayData.date(item.optString("when")), 12, MUTED, false));
        c.setOnClickListener(v -> details(item, kind));
        c.setContentDescription(item.optString("title") + ". Otwórz szczegóły");
        c.setFocusable(true);
    }

    private void overview() {
        if (!state.optBoolean("ready")) { card().addView(text("Przygotowuję połączenie i lokalny zapis…", 16, MUTED, false)); return; }
        if (!state.optBoolean("connected") && !state.optString("mode").equals("demo")) {
            LinearLayout welcome = card(); welcome.addView(text("Dziennik pod ręką", 22, INK, true));
            welcome.addView(text("Połącz szkolne konto Synergia lub poznaj aplikację na danych przykładowych.", 15, MUTED, false));
            welcome.addView(button("Połącz konto Synergia", this::showLogin, true));
            welcome.addView(button("Zobacz demo", repository::demo, false));
        }
        long unread = items("messages").stream().filter(item -> item.optBoolean("unread")).count();
        List<JSONObject> upcoming = new ArrayList<>();
        for (JSONObject item : items("schedule")) {
            LocalDate day = DisplayData.day(item.optString("when"));
            if (day == null || !day.isBefore(LocalDate.now())) upcoming.add(item);
        }
        metric("Nieprzeczytane wiadomości", "messages", unread, "W maksymalnie 200 pobranych nagłówkach");
        metric("Pobrane oceny", "grades", items("grades").size(), "W danych udostępnionych przez szkołę");
        metric("Nadchodzące wydarzenia", "schedule", upcoming.size(), "Dzisiaj i w kolejnych dniach");
        JSONObject errors = object(state, "errors");
        for (String key : KEYS) if (errors.has(key)) notice(title(key) + ": " + errors.optString(key));
        list.addView(text("Ostatnie oceny", 19, INK, true));
        List<JSONObject> grades = items("grades"); grades.sort(Comparator.comparing((JSONObject v) -> v.optString("when")).reversed());
        for (JSONObject item : grades.subList(0, Math.min(4, grades.size()))) entry(item, "grades");
        list.addView(text("Najbliższe wydarzenia", 19, INK, true));
        upcoming.sort(Comparator.comparing(v -> v.optString("when")));
        for (JSONObject item : upcoming.subList(0, Math.min(4, upcoming.size()))) entry(item, "schedule");
    }
    private void metric(String label, String key, long count, String note) {
        LinearLayout c = card(); c.addView(text(label, 14, MUTED, false));
        c.addView(text(object(state, "updated_at").has(key) ? Long.toString(count) : "—", 30, TEAL, true));
        c.addView(text(note, 12, MUTED, false)); c.setOnClickListener(v -> select(key));
    }

    private void calendar(List<JSONObject> rows) {
        LinearLayout c = card(), navigation = row();
        navigation.addView(button("‹", () -> { month = month.minusMonths(1); selectedDay = null; renderList(); }, false));
        TextView monthName = text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("pl"))), 17, INK, true);
        monthName.setGravity(Gravity.CENTER); weighted(navigation, monthName);
        navigation.addView(button("›", () -> { month = month.plusMonths(1); selectedDay = null; renderList(); }, false)); c.addView(navigation);
        LinearLayout shortcuts = row();
        weighted(shortcuts, button("Dzisiaj", () -> { month = YearMonth.now(); selectedDay = LocalDate.now(); renderList(); }, false));
        weighted(shortcuts, button("Cały miesiąc", () -> { selectedDay = null; renderList(); }, false)); c.addView(shortcuts);
        LinearLayout weekdays = row(); for (String label : new String[]{"Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"}) {
            TextView v = text(label, 11, MUTED, true); v.setGravity(Gravity.CENTER); weighted(weekdays, v);
        } c.addView(weekdays);
        LocalDate start = month.atDay(1).minusDays(month.atDay(1).getDayOfWeek().getValue() - 1);
        int cells = ((month.lengthOfMonth() + month.atDay(1).getDayOfWeek().getValue() - 2) / 7 + 1) * 7;
        for (int index = 0; index < cells; index += 7) {
            LinearLayout week = row();
            for (int col = 0; col < 7; col++) {
                LocalDate day = start.plusDays(index + col);
                long count = rows.stream().filter(v -> day.equals(DisplayData.day(v.optString("when")))).count();
                String value = Integer.toString(day.getDayOfMonth()) + (count > 0 ? "\n" + count + " wpis." : "");
                Button b = button(value, () -> { month = YearMonth.from(day); selectedDay = day.equals(selectedDay) ? null : day; renderList(); }, day.equals(selectedDay));
                b.setPadding(0, dp(3), 0, dp(3)); b.setTextSize(10); b.setMinWidth(0); b.setMinimumWidth(0);
                if (!YearMonth.from(day).equals(month)) b.setAlpha(0.45f);
                if (day.equals(LocalDate.now())) b.setTypeface(null, Typeface.BOLD);
                b.setContentDescription(DisplayData.date(day.toString()) + ", wydarzeń: " + count);
                weighted(week, b);
            } c.addView(week);
        }
        LocalDate fetched = DisplayData.day(object(state, "updated_at").optString("schedule"));
        if (fetched != null && !state.optString("mode").equals("demo")) {
            YearMonth covered = YearMonth.from(fetched);
            if (!month.equals(covered) && !month.equals(covered.plusMonths(1))) c.addView(text("Miesiąc poza zakresem ostatniego odczytu. Dane mogą być niepełne.", 12, 0xff994835, false));
        }
        c.addView(text(selectedDay == null ? "Wybierz dzień, aby zawęzić listę." : "Wybrano " + DisplayData.date(selectedDay.toString()), 12, MUTED, false));
    }

    private void settings() {
        LinearLayout c = card(); c.addView(text("Konto i dane", 19, INK, true));
        c.addView(text(state.optBoolean("remembered") ? "Konto zapamiętane na tym telefonie." : "Hasło nie jest zapamiętane.", 15, MUTED, false));
        c.addView(button("Połącz / zmień konto", this::showLogin, true));
        c.addView(button("Włącz demo", repository::demo, false));
        c.addView(button("Usuń lokalne dane i konto", () -> new AlertDialog.Builder(this)
                .setTitle("Usunąć lokalne dane?").setMessage("Usunięte zostaną kopia dziennika i zapamiętane konto na telefonie. Dane w Librusie pozostaną bez zmian.")
                .setNegativeButton("Anuluj", null).setPositiveButton("Usuń", (d, w) -> repository.forget()).show(), false));
        LinearLayout info = card(); info.addView(text("Odświeżanie", 19, INK, true));
        info.addView(text("Najnowsze dane pobierane są po otwarciu lub powrocie do aplikacji przy aktywnej sesji albo zapamiętanym koncie. Kolejna próba najwcześniej po minucie. Bez zapamiętanego hasła po zakończeniu procesu zaloguj się ponownie.", 15, MUTED, false));
        info.addView(text("Ta pierwsza wersja nie odświeża w tle i nie wysyła systemowych powiadomień ani własnych alarmów. Dane i opcjonalne hasło są szyfrowane kluczem Android Keystore; kopie zapasowe systemu są wyłączone.", 14, MUTED, false));
        LinearLayout about = card(); about.addView(text("LibrusApp Android 0.1.0", 19, INK, true));
        about.addView(text("Nieoficjalny klient Synergii. Kod: AGPL-3.0-or-later. Integracja wykorzystuje librus-apix i adapter projektu desktopowego.", 14, MUTED, false));
        about.addView(button("Otwórz oficjalnego Librusa", () -> openOfficial("overview"), false));
        about.addView(button("Licencje i źródła", () -> {
            try (java.io.InputStream input = getAssets().open("THIRD_PARTY_NOTICES.md")) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                showText("Źródła i licencje", output.toString("UTF-8"));
            } catch (Exception error) { showText("Źródła i licencje", "Informacje znajdują się w źródłach projektu librus-android."); }
        }, false));
    }

    private void details(JSONObject item, String kind) {
        Dialog dialog = new Dialog(this);
        LinearLayout body = column(); body.setPadding(dp(18), dp(16), dp(18), dp(16)); body.setBackgroundColor(WHITE);
        body.addView(text(item.optString("title", "Szczegóły"), 21, INK, true));
        if (item.has("subtitle") || item.has("when"))
            body.addView(text(item.optString("subtitle") + " · " + DisplayData.date(item.optString("when")), 13, MUTED, false));
        if (kind.equals("messages")) {
            body.addView(text("Pobranie treści może oznaczyć wiadomość jako przeczytaną w Librusie.", 13, MUTED, false));
            Button read = button("Pobierz treść", () -> { dialog.dismiss(); repository.readMessage(item.optString("id")); }, true);
            read.setEnabled(!state.optBoolean("busy")); body.addView(read);
        }
        ScrollView detailScroll = new ScrollView(this);
        TextView content = text(item.optString("details", "Brak dodatkowego opisu."), 16, INK, false); content.setTextIsSelectable(true);
        detailScroll.addView(content); body.addView(detailScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = row();
        weighted(actions, button("Otwórz w Librusie", () -> openOfficial(kind), false));
        weighted(actions, button("Zamknij", dialog::dismiss, false)); body.addView(actions);
        dialog.setContentView(body); dialog.show();
        dialog.getWindow().setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.85));
        if (!state.optString("mode").equals("demo")) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void showLogin() {
        if (state.optBoolean("busy") || (loginDialog != null && loginDialog.isShowing())) return;
        LinearLayout body = column(); body.setPadding(dp(22), dp(12), dp(22), 0);
        body.addView(text("Użyj loginu Synergii otrzymanego ze szkoły, nie adresu e-mail Konta LIBRUS.", 14, MUTED, false));
        EditText login = new EditText(this); login.setHint("Login Synergia"); login.setSingleLine(true); login.setSaveEnabled(false);
        login.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); body.addView(login);
        EditText password = new EditText(this); password.setHint("Hasło"); password.setSingleLine(true); password.setSaveEnabled(false);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); body.addView(password);
        CheckBox remember = new CheckBox(this); remember.setText("Zapamiętaj konto na tym telefonie"); remember.setChecked(false); body.addView(remember);
        body.addView(text("Zapis konta i kopii jest szyfrowany. Zaznacz tylko na własnym telefonie.", 12, MUTED, false));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Połącz konto Synergia").setView(body)
                .setNegativeButton("Anuluj", null).setNeutralButton("Demo", (d, w) -> repository.demo()).setPositiveButton("Połącz", null).create();
        loginDialog = dialog; dialog.setOnDismissListener(d -> { password.setText(""); loginDialog = null; });
        dialog.show(); dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String user = login.getText().toString().trim(), secret = password.getText().toString();
            if (user.isEmpty()) { login.setError("Wpisz login"); return; }
            if (secret.isEmpty()) { password.setError("Wpisz hasło"); return; }
            boolean save = remember.isChecked(); password.setText(""); dialog.dismiss();
            repository.connect(user, secret, save);
        });
    }

    @Override public void onMessage(JSONObject content) {
        if (content.has("error")) new AlertDialog.Builder(this).setTitle("Nie pobrano treści").setMessage(content.optString("error")).setPositiveButton("OK", null).show();
        else showText(content.optString("title", "Wiadomość"), content.optString("text", "Brak treści."));
    }
    private void showText(String title, String content) {
        JSONObject item = new JSONObject();
        try { item.put("title", title).put("details", content); } catch (Exception ignored) { }
        details(item, "overview");
    }
    private void openOfficial(String kind) {
        String path = switch (kind) {
            case "grades" -> "przegladaj_oceny/uczen";
            case "messages" -> "wiadomosci3";
            case "announcements" -> "ogloszenia";
            case "schedule" -> "terminarz/";
            case "attendance" -> "przegladaj_nb/uczen";
            case "timetable" -> "przegladaj_plan_lekcji";
            default -> "";
        };
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://synergia.librus.pl/" + path))); }
        catch (Exception error) { Toast.makeText(this, "Brak przeglądarki do otwarcia Librusa.", Toast.LENGTH_LONG).show(); }
    }
}
