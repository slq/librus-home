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
    private static final String[] KEYS = {"overview", "calendar", "grades", "messages", "announcements", "schedule", "homework", "attendance", "timetable", "reminders", "search", "settings"};
    private static final String[] TITLES = {"Przegląd", "Kalendarz", "Oceny", "Wiadomości", "Ogłoszenia", "Terminarz", "Zadania domowe", "Frekwencja", "Plan lekcji", "Przypomnienia", "Szukaj", "Ustawienia"};
    private MobileRepository repository;
    private JSONObject state = new JSONObject();
    private LinearLayout shell, list, filters;
    private TextView status, heading;
    private Button refresh, account;
    private ProgressBar progress;
    private ScrollView scroll;
    private EditText search;
    private Spinner searchSource;
    private String globalSource="all";
    private int searchLimit=50;
    private final Map<String, Button> tabs = new LinkedHashMap<>();
    private final Map<String, String> queries = new HashMap<>();
    private String page = "overview";
    private int sort = 0;
    private boolean calendar = true, homeworkUpcoming = true, loginPrompted, updatingSearch;
    private YearMonth month = YearMonth.now();
    private LocalDate selectedDay;
    private YearMonth unifiedMonth=YearMonth.now();
    private LocalDate unifiedDay;
    private boolean calendarEvents=true,calendarTasks=true,calendarReminders=true;
    private Dialog loginDialog, moreDialog;
    private boolean overviewTomorrow;
    private LocalDate overviewDate;
    private String changesSource="all";
    private boolean changesExpanded;
    private ReminderUi reminderUi;
    private GoogleCalendarUi googleCalendarUi;
    private UpdateUi updateUi;
    private BackupUi backupUi;
    private HomeworkCompletionUi homeworkStatus;
    private int homeworkStatusFilter;
    private String pendingReminder;
    private boolean remindersReady, receiverRegistered;
    private final android.os.Handler foreground = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshTick = new Runnable() {
        public void run() {
            repository.open();
            if (page.equals("overview") && !LocalDate.now().plusDays(overviewTomorrow?1:0).equals(overviewDate)) { int y=scroll.getScrollY(); renderList(); scroll.post(() -> scroll.scrollTo(0,y)); }
            foreground.postDelayed(this, 30000);
        }
    };
    private final android.content.BroadcastReceiver reminderUpdates = new android.content.BroadcastReceiver() {
        public void onReceive(android.content.Context c, Intent intent) { reloadReminders(); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (saved != null) {
            page = saved.getString("page", "overview");
            globalSource=saved.getString("globalSource","all");
            if(!SearchData.sources().contains(globalSource))globalSource="all";
            searchLimit=Math.max(50,Math.min(5000,saved.getInt("searchLimit",50)));
            overviewTomorrow = saved.getBoolean("overviewTomorrow", false);
            changesSource=saved.getString("changesSource","all");changesExpanded=saved.getBoolean("changesExpanded",false);
            calendar = saved.getBoolean("calendar", true);
            homeworkUpcoming = saved.getBoolean("homeworkUpcoming", true);
            homeworkStatusFilter=saved.getInt("homeworkStatusFilter",0);
            if(homeworkStatusFilter<0||homeworkStatusFilter>2)homeworkStatusFilter=0;
            loginPrompted = saved.getBoolean("loginPrompted", false);
            try { month = YearMonth.parse(saved.getString("month", month.toString())); }
            catch (Exception ignored) { }
            String day = saved.getString("day");
            if (day != null) try { selectedDay = LocalDate.parse(day); } catch (Exception ignored) { }
            try {unifiedMonth=YearMonth.parse(saved.getString("unifiedMonth",unifiedMonth.toString()));}catch(Exception ignored){}
            try {String chosen=saved.getString("unifiedDay");if(chosen!=null)unifiedDay=LocalDate.parse(chosen);}catch(Exception ignored){}
            calendarEvents=saved.getBoolean("calendarEvents",true);calendarTasks=saved.getBoolean("calendarTasks",true);calendarReminders=saved.getBoolean("calendarReminders",true);
            Bundle savedQueries = saved.getBundle("queries");
            if (savedQueries != null) for (String key : savedQueries.keySet()) queries.put(key, savedQueries.getString(key, ""));
        }
        repository = MobileRepository.get(this);
        updateUi=new UpdateUi(this);
        backupUi=new BackupUi(this,repository,()->state,()->{loginPrompted=true;reloadReminders();homeworkStatus.reload();renderList();});
        NotificationHub.channels(this);
        reminderUi = new ReminderUi(this, () -> state, () -> {
            updateReminderBadge();
            if (page.equals("reminders")||page.equals("calendar")||page.equals("overview")||page.equals("search")) renderList();
        }, this::showReminderSource);
        googleCalendarUi = new GoogleCalendarUi(this, () -> state, this::renderList);
        homeworkStatus=new HomeworkCompletionUi(this,()->state,()->{
            if(list!=null&&(page.equals("overview")||page.equals("homework")||page.equals("calendar")||page.equals("search"))) {
                int y=scroll.getScrollY();renderList();scroll.post(()->scroll.scrollTo(0,y));
            }
        });
        pendingReminder = saved==null ? getIntent().getStringExtra("reminder_id") : saved.getString("pendingReminder");
        if (saved==null && pendingReminder != null) { page = "reminders"; loginPrompted = true; }
        else if (saved==null && getIntent().getData() != null && "changes".equals(getIntent().getData().getHost())) {
            page = "overview"; loginPrompted = true;
        }
        if (saved==null && "android.provider.calendar.action.HANDLE_CUSTOM_EVENT".equals(getIntent().getAction())) { page="calendar"; loginPrompted=true; }
        if(saved==null&&getIntent().getData()!=null&&"updates".equals(getIntent().getData().getHost())){page="settings";loginPrompted=true;}
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
        updateUi.start();
        repository.focused(true);
        repository.observe(this);
        if(repository.enterApp()){changesSource="all";changesExpanded=false;}
        repository.open();
        foreground.removeCallbacks(refreshTick); foreground.postDelayed(refreshTick, 30000);
        android.content.IntentFilter events = new android.content.IntentFilter("pl.librushome.android.REMINDERS_CHANGED");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(reminderUpdates, events, getPackageName() + ".INTERNAL", null, android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerLegacyReminderUpdates(events);
        receiverRegistered = true; reloadReminders(); homeworkStatus.reload();
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Only API 26–32, protected by our signature permission.
    private void registerLegacyReminderUpdates(android.content.IntentFilter events) {
        registerReceiver(reminderUpdates, events, getPackageName() + ".INTERNAL", null);
    }
    @Override protected void onResume() { super.onResume(); repository.focused(true); if (reminderUi != null) reloadReminders(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (repository != null) { repository.focused(hasFocus); if (hasFocus) repository.open(); } }
    @Override protected void onPause() { repository.focused(false); super.onPause(); }
    @Override protected void onStop() {
        updateUi.stop();
        foreground.removeCallbacks(refreshTick);
        if (receiverRegistered) { unregisterReceiver(reminderUpdates); receiverRegistered = false; }
        repository.focused(false);
        repository.leaveApp(isChangingConfigurations());
        repository.observe(null); super.onStop();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); pendingReminder = intent.getStringExtra("reminder_id");
        if (pendingReminder != null) {
            loginPrompted = true;
            remindersReady = false; // The receiver may have updated the stored status while this Activity was stopped.
            select("reminders"); reloadReminders();
        } else if (intent.getData() != null && "changes".equals(intent.getData().getHost())) {changesSource="all";changesExpanded=false;select("overview");}
        else if(intent.getData()!=null&&"updates".equals(intent.getData().getHost())){loginPrompted=true;select("settings");}
        else if ("android.provider.calendar.action.HANDLE_CUSTOM_EVENT".equals(intent.getAction())) select("calendar");
    }
    private void updateReminderBadge() {
        Button more=tabs.get("more");
        if(more!=null && reminderUi!=null) more.setContentDescription("Więcej. Zaplanowane przypomnienia: "+reminderUi.pendingCount());
    }
    private void reloadReminders() { reminderUi.reload(() -> {
        remindersReady=true;
        if(page.equals("calendar")||page.equals("overview")||page.equals("search")) {
            int y=scroll.getScrollY(); renderList(); scroll.post(() -> scroll.scrollTo(0,y));
        }
        openPendingReminder();
    }); }
    private void openPendingReminder() {
        if (pendingReminder != null && remindersReady && state.optBoolean("ready")) {
            String id = pendingReminder; pendingReminder = null; reminderUi.open(id);
        }
    }
    private void showReminderSource(JSONObject reminder, String kind) {
        queries.put(kind, ""); if (kind.equals("schedule")) { calendar = false; selectedDay = null; }
        if (kind.equals("homework")) {homeworkUpcoming = false;homeworkStatusFilter=2;}
        select(kind);
        for (JSONObject item : items(kind)) if (item.optString("id").equals(reminder.optString("source_id"))) { details(item, kind); return; }
        showText("Wpis niedostępny", "Źródło nie znajduje się w pobranych danych. Przypomnienie zachowano: " + reminder.optString("title"));
    }
    @Override protected void onDestroy() {
        if (loginDialog != null) loginDialog.dismiss();
        if (moreDialog != null) moreDialog.dismiss();
        if(backupUi!=null)backupUi.close();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("page", page);
        out.putString("globalSource",globalSource);out.putInt("searchLimit",searchLimit);
        out.putBoolean("overviewTomorrow", overviewTomorrow);
        out.putString("changesSource",changesSource);out.putBoolean("changesExpanded",changesExpanded);
        out.putString("pendingReminder",pendingReminder);
        out.putString("month", month.toString());
        if (selectedDay != null) out.putString("day", selectedDay.toString());
        out.putBoolean("calendar", calendar);
        out.putBoolean("homeworkUpcoming", homeworkUpcoming);
        out.putInt("homeworkStatusFilter",homeworkStatusFilter);
        out.putBoolean("loginPrompted", loginPrompted);
        out.putString("unifiedMonth",unifiedMonth.toString());if(unifiedDay!=null)out.putString("unifiedDay",unifiedDay.toString());
        out.putBoolean("calendarEvents",calendarEvents);out.putBoolean("calendarTasks",calendarTasks);out.putBoolean("calendarReminders",calendarReminders);
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
    private Button button(String label, Runnable action, boolean accent) { return ButtonStyles.make(this, label, action, accent); }
    private void weighted(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (child instanceof Button) params.setMargins(dp(3), dp(2), dp(3), dp(2));
        parent.addView(child, params);
    }
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

        LinearLayout section = row(); section.setPadding(dp(18), 0, dp(18), dp(8));
        heading = text("Przegląd", 23, INK, true); weighted(section, heading);
        refresh = button("Odśwież", repository::refresh, true); section.addView(refresh); shell.addView(section);
        filters = column(); filters.setPadding(dp(18), 0, dp(18), dp(8));
        search = new EditText(this); search.setSingleLine(true); search.setTextSize(15); search.setHint("Szukaj w tej sekcji");
        search.setContentDescription("Wyszukiwanie w bieżącej sekcji");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (!updatingSearch) { queries.put(page, s.toString()); if(page.equals("search"))searchLimit=50; renderList(); scroll.scrollTo(0,0); }
            }
            public void afterTextChanged(Editable e) { }
        });
        search.setSaveEnabled(false); // Queries are saved explicitly, without Android assigning unstable view IDs.
        filters.addView(search);
        searchSource=new Spinner(this);searchSource.setContentDescription("Źródło wspólnego wyszukiwania");
        List<String> sourceLabels=new ArrayList<>();sourceLabels.add("Wszystkie sekcje");
        for(String kind:SearchData.sources())sourceLabels.add(title(kind));
        searchSource.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sourceLabels));
        searchSource.setMinimumHeight(dp(48));filters.addView(searchSource);
        searchSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View v,int index,long id){
                String source=index==0?"all":SearchData.sources().get(index-1);
                if(page.equals("search")&&!source.equals(globalSource)){globalSource=source;searchLimit=50;renderList();scroll.scrollTo(0,0);}
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });
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
        buildBottomNavigation();
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
        if (!Arrays.asList(KEYS).contains(key)) key = "overview";
        page = key;
        heading.setText(title(page));
        for (Map.Entry<String, Button> tab : tabs.entrySet()) {
            boolean selected = tab.getKey().equals(page) || (tab.getKey().equals("more") && !Arrays.asList("overview","calendar","announcements","messages").contains(page));
            ButtonStyles.style(tab.getValue(), selected);
            tab.getValue().setSelected(selected);
            for(android.graphics.drawable.Drawable icon:tab.getValue().getCompoundDrawables()) if(icon!=null) icon.setTint(selected?WHITE:TEAL);
        }
        filters.setVisibility(page.equals("overview") || page.equals("settings") ? View.GONE : View.VISIBLE);
        search.setHint(page.equals("search")?"Szukaj we wszystkich sekcjach":"Szukaj w tej sekcji");
        search.setContentDescription(page.equals("search")?"Wspólne wyszukiwanie":"Wyszukiwanie w bieżącej sekcji");
        searchSource.setVisibility(page.equals("search")?View.VISIBLE:View.GONE);
        searchSource.setSelection(globalSource.equals("all")?0:SearchData.sources().indexOf(globalSource)+1);
        updatingSearch = true; search.setText(queries.getOrDefault(page, "")); updatingSearch = false;
        renderList(); scroll.scrollTo(0, 0);
    }

    @Override public void onState(JSONObject value) {
        state = value;
        homeworkStatus.contextChanged();
        updateReminderBadge();
        boolean busy = state.optBoolean("busy");
        String mode = state.optString("mode", "idle");
        status.setText((mode.equals("demo") ? "DEMO · " : mode.equals("offline") ? "KOPIA LOKALNA · " : "") + state.optString("status", "Przygotowuję aplikację…"));
        refresh.setEnabled(!busy && !mode.equals("idle")); account.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (mode.equals("demo")) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        int y = scroll.getScrollY(); renderList(); scroll.post(() -> scroll.scrollTo(0, y));
        openPendingReminder();
        if (state.optBoolean("ready") && state.optBoolean("needs_login") && (!state.optBoolean("remembered") || state.optBoolean("auto_login_blocked")) && !busy && !loginPrompted && !backupUi.importing) {
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
        if(page.equals("overview")||page.equals("homework")||page.equals("calendar")||page.equals("search")) {
            notice(homeworkStatus.error());
            if(!homeworkStatus.error().isEmpty())spaced(list,button("Ponów odczyt statusów",homeworkStatus::reload,false));
            if(!homeworkStatus.progress().isEmpty())spaced(list,text(homeworkStatus.progress(),12,MUTED,false));
        }
        double retry = state.optDouble("retry_after", 0);
        if (retry * 1000 > System.currentTimeMillis()) {
            notice("Pobieranie wstrzymane do " + java.time.Instant.ofEpochSecond((long) retry).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
        }
        if (page.equals("search")) { globalSearch(); return; }
        if (page.equals("overview")) { overview(); return; }
        if (page.equals("settings")) { settings(); return; }
        if (page.equals("calendar")) { unifiedCalendar(); return; }
        if (page.equals("reminders")) { reminderUi.render(list, queries.getOrDefault(page, "")); return; }
        String fetched = object(state, "updated_at").optString(page);
        list.addView(text(fetched.trim().isEmpty() ? "Sekcja jeszcze niepobrana" : "Ostatni odczyt: " + DisplayData.date(fetched), 12, MUTED, false));
        notice(object(state, "errors").optString(page));
        if(page.equals("announcements"))spaced(list,text("Zachowujemy wszystkie pobrane ogłoszenia, także z poprzednich lat. Ponowny odczyt tego samego wpisu zastępuje treść najnowszą wersją. Brak wpisu na liście Librusa nie usuwa lokalnej kopii.",13,MUTED,false));
        String query = queries.getOrDefault(page, "").trim().toLowerCase(Locale.ROOT);
        List<JSONObject> all = items(page);
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject item : all) {
            String haystack = (item.optString("title") + " " + item.optString("subtitle") + " " + item.optString("when") + " " + item.optString("details")).toLowerCase(Locale.ROOT);
            if (haystack.contains(query)) filtered.add(item);
        }
        if (page.equals("homework")) {
            spaced(list, text("Zadania udostępnione przez Librusa dla bieżącego roku szkolnego. Data oznacza termin wykonania; aplikacja nie potwierdza oddania pracy.", 13, MUTED, false));
            LinearLayout modes = row();
            weighted(modes, button("Terminy od dziś", () -> { homeworkUpcoming = true; renderList(); }, homeworkUpcoming));
            weighted(modes, button("Wszystkie terminy", () -> { homeworkUpcoming = false; renderList(); }, !homeworkUpcoming)); spaced(list, modes);
            LinearLayout statuses=row();
            String[] labels={"Do zrobienia","Zrobione","Wszystkie"};
            for(int i=0;i<labels.length;i++) {
                int value=i;Button option=button(labels[i],()->{homeworkStatusFilter=value;renderList();},homeworkStatusFilter==i);
                option.setEnabled(homeworkStatus.editable());weighted(statuses,option);
            }
            spaced(list,statuses);
            if(homeworkStatus.ready()) {
                long completed=all.stream().filter(homeworkStatus::done).count();
                spaced(list,text("Na pobranej liście: do zrobienia "+(all.size()-completed)+", zrobione "+completed,12,MUTED,false));
                if(homeworkStatusFilter<2)filtered.removeIf(item->homeworkStatus.done(item)!=(homeworkStatusFilter==1));
            }
            if (homeworkUpcoming) filtered.removeIf(item -> {
                LocalDate day = DisplayData.day(item.optString("when"));
                return day != null && day.isBefore(LocalDate.now());
            });
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
        boolean ascending = page.equals("schedule") || page.equals("timetable") || page.equals("homework");
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

    private void unifiedCalendar() {
        spaced(list,text("Wydarzenia, terminy zadań i własne przypomnienia w jednym miejscu.",14,MUTED,false));
        String[] kinds={"schedule","homework","reminders"};boolean[] enabled={calendarEvents,calendarTasks,calendarReminders};
        LinearLayout sources=card();sources.addView(text("Pokaż w kalendarzu",15,INK,true));
        for(int i=0;i<kinds.length;i++) {
            final String kind=kinds[i];CheckBox option=new CheckBox(this);option.setText(CalendarData.label(kind));option.setTextColor(CalendarData.color(kind));option.setChecked(enabled[i]);
            option.setContentDescription("Źródło kalendarza: "+CalendarData.label(kind));sources.addView(option);
            option.setOnCheckedChangeListener((b,checked)->{if(kind.equals("schedule"))calendarEvents=checked;else if(kind.equals("homework"))calendarTasks=checked;else calendarReminders=checked;renderList();});
        }
        for(String kind:new String[]{"schedule","homework"}) {
            String fetched=object(state,"updated_at").optString(kind);
            spaced(list,text(title(kind)+": "+(fetched.isEmpty()?"sekcja jeszcze niepobrana":"ostatni odczyt "+DisplayData.date(fetched)),12,MUTED,false));
            if(!object(state,"errors").optString(kind).isEmpty())notice(title(kind)+": "+object(state,"errors").optString(kind));
        }
        if(!remindersReady)spaced(list,text("Wczytuję własne przypomnienia…",12,MUTED,false));
        notice(reminderUi.calendarError());
        List<JSONObject> rows=CalendarData.combine(calendarEvents?items("schedule"):Collections.emptyList(),calendarTasks?items("homework"):Collections.emptyList(),new JSONArray(),"",false);
        if(calendarReminders)rows.addAll(reminderUi.calendarItems());
        String query=queries.getOrDefault("calendar","").trim().toLowerCase(Locale.ROOT);
        rows.removeIf(item->!(CalendarData.label(item.optString("calendar_kind"))+" "+item.optString("title")+" "+item.optString("subtitle")+" "+item.optString("details")+" "+DisplayData.date(item.optString("when"))).toLowerCase(Locale.ROOT).contains(query));
        calendar(rows);
        rows.removeIf(item->{LocalDate day=DisplayData.day(item.optString("when"));return unifiedDay!=null?!unifiedDay.equals(day):day==null||!YearMonth.from(day).equals(unifiedMonth);});
        Comparator<JSONObject> byDate=Comparator.comparing(item->DisplayData.day(item.optString("when")));
        byDate=byDate.thenComparing(item->DisplayData.date(item.optString("when")));
        rows.sort(sort==3?Comparator.comparing(item->item.optString("title").toLowerCase(Locale.ROOT)):sort==1?byDate.reversed():byDate);
        spaced(list,text((unifiedDay==null?"Wpisy w miesiącu":"Wpisy na "+DisplayData.date(unifiedDay.toString()))+" · "+rows.size(),16,INK,true));
        if(rows.isEmpty())card().addView(text("Brak wpisów dla wybranego widoku. Sprawdź dzień, źródła i wyszukiwanie.",15,MUTED,false));
        for(JSONObject item:rows)entry(item,item.optString("calendar_kind"));
    }
    private YearMonth shownCalendarMonth(){return page.equals("calendar")?unifiedMonth:month;}
    private LocalDate shownCalendarDay(){return page.equals("calendar")?unifiedDay:selectedDay;}
    private void calendarMonth(YearMonth value){if(page.equals("calendar"))unifiedMonth=value;else month=value;}
    private void calendarDay(LocalDate value){if(page.equals("calendar"))unifiedDay=value;else selectedDay=value;}

    private void entry(JSONObject item, String kind) {
        LinearLayout c = card();
        if(page.equals("calendar"))c.addView(text(CalendarData.label(kind),12,CalendarData.color(kind),true));
        if(page.equals("search"))c.addView(text(title(kind),12,TEAL,true));
        String marker = item.optBoolean("unread") ? "● Nieprzeczytana" : item.optBoolean("changed") ? "+ Nowa lub zmieniona" : "";
        if (!marker.isEmpty()) c.addView(text(marker, 12, TEAL, true));
        if(kind.equals("announcements")&&item.optBoolean("archived"))c.addView(text("Lokalne archiwum",12,MUTED,true));
        TextView itemTitle=text(item.optString("title", "Bez tytułu"),16,INK,true);
        if(kind.equals("homework")&&homeworkStatus.done(item)) {itemTitle.setTextColor(MUTED);itemTitle.setPaintFlags(itemTitle.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);}
        c.addView(itemTitle);
        c.addView(text(item.optString("subtitle"), 13, MUTED, false));
        c.addView(text((kind.equals("homework") ? "Termin: " : "") + DisplayData.date(item.optString("when")), 12, MUTED, false));
        if (kind.equals("homework")) {
            homeworkStatus.bind(c,item);
            LocalDate day = DisplayData.day(item.optString("when"));
            if (day != null && day.isBefore(LocalDate.now())) c.addView(text("Termin minął", 12, MUTED, false));
            else if (LocalDate.now().equals(day)) c.addView(text("Termin dzisiaj", 12, TEAL, true));
            else if (LocalDate.now().plusDays(1).equals(day)) c.addView(text("Termin jutro", 12, TEAL, true));
        }
        c.setOnClickListener(v -> {if(page.equals("search"))hideKeyboard();if(kind.equals("reminders"))reminderUi.open(item.optString("reminder_id"));else details(item,kind);});
        c.setContentDescription(item.optString("title") + ". Otwórz szczegóły");
        c.setFocusable(true);
    }

    private void buildBottomNavigation() {
        LinearLayout nav=row(); nav.setBackgroundColor(WHITE); nav.setPadding(dp(6),dp(5),dp(6),dp(5));
        nav.setContentDescription("Dolna nawigacja");
        String[] keys={"overview","calendar","announcements","messages","more"};
        String[] labels={"Start","Kalendarz","Ogłoszenia","Wiadomości","Więcej"};
        int[] icons={R.drawable.nav_home,R.drawable.nav_calendar,R.drawable.nav_announcements,R.drawable.nav_messages,R.drawable.nav_more};
        for(int i=0;i<keys.length;i++) {
            String key=keys[i]; Button tab=button(labels[i],()->{if(key.equals("more"))showMore();else select(key);},false);
            tab.setTextSize(11); tab.setMaxLines(1);
            tab.setAutoSizeTextTypeUniformWithConfiguration(dp(9),Math.max(dp(9),Math.round(11*getResources().getDisplayMetrics().scaledDensity)),1,android.util.TypedValue.COMPLEX_UNIT_PX);
            tab.setPadding(dp(1),dp(7),dp(1),dp(7)); tab.setMinHeight(dp(64)); tab.setMinimumHeight(dp(64));
            android.graphics.drawable.Drawable icon=getDrawable(icons[i]).mutate(); icon.setBounds(0,0,dp(22),dp(22));
            tab.setCompoundDrawables(null,icon,null,null); tab.setCompoundDrawablePadding(dp(4));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1); params.setMargins(dp(1),dp(2),dp(1),dp(2));
            nav.addView(tab,params); tabs.put(key,tab);
        }
        shell.addView(nav); updateReminderBadge();
    }
    private void showMore() {
        if(moreDialog!=null && moreDialog.isShowing())return;
        Dialog dialog=new Dialog(this); moreDialog=dialog;
        LinearLayout body=column(); body.setPadding(dp(18),dp(16),dp(18),dp(16)); body.setBackgroundColor(BG);
        body.addView(text("Więcej",22,INK,true));
        ScrollView choices=new ScrollView(this); LinearLayout panels=column(); choices.addView(panels);
        for(String key:new String[]{"search","grades","homework","schedule","attendance","timetable","reminders","settings"}) {
            String label=title(key);
            if(key.equals("reminders") && reminderUi.pendingCount()>0)label+=" · "+reminderUi.pendingCount();
            spaced(panels,button(label,()->{dialog.dismiss();select(key);},page.equals(key)));
        }
        body.addView(choices,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(button("Zamknij",dialog::dismiss,false));
        dialog.setContentView(body); dialog.setOnDismissListener(d->{if(moreDialog==dialog)moreDialog=null;}); dialog.show();
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.75));
        if(!state.optString("mode").equals("demo"))dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(search.getWindowToken(),0);
        search.clearFocus();
    }
    private void globalSearch() {
        spaced(list,text("Szukaj w pobranych tytułach, opisach, nadawcach, przedmiotach i datach. Pełną treść wiadomości i zadań pobierzesz osobno w szczegółach.",13,MUTED,false));
        JSONObject updated=object(state,"updated_at"), errors=object(state,"errors");
        List<String> missing=new ArrayList<>();
        for(String kind:SearchData.sources()) {
            if(!globalSource.equals("all")&&!globalSource.equals(kind))continue;
            if(kind.equals("reminders"))continue;
            if(updated.optString(kind).isEmpty())missing.add(title(kind));
            if(!errors.optString(kind).isEmpty())notice(title(kind)+": "+errors.optString(kind));
        }
        if(!missing.isEmpty())spaced(list,text("Sekcje jeszcze niepobrane: "+String.join(", ",missing),12,MUTED,false));
        if(globalSource.equals("all")||globalSource.equals("reminders")) {
            if(!remindersReady)spaced(list,text("Wczytuję własne przypomnienia…",12,MUTED,false));
            notice(reminderUi.calendarError());
        }
        String query=queries.getOrDefault("search","");
        if(SearchText.words(query).length==0) {card().addView(text("Wpisz temat, nazwisko, przedmiot lub datę. Wyniki pojawią się tutaj ze wszystkich sekcji, także dla wykonanych zadań i przypomnień.",15,MUTED,false));return;}
        List<SearchData.Result> results=SearchData.find(object(state,"sections"),reminderUi.calendarItems(),query,globalSource,sort);
        spaced(list,text("Wyników: "+results.size(),16,INK,true));
        if(results.isEmpty()) {card().addView(text("Brak wyników w pobranych danych. Zmień słowa lub wybierz inne źródło.",15,MUTED,false));return;}
        int shown=Math.min(searchLimit,results.size());
        for(SearchData.Result result:results.subList(0,shown))entry(result.item,result.kind);
        if(shown<results.size()) {
            spaced(list,text("Pokazano "+shown+" z "+results.size(),12,MUTED,false));
            spaced(list,button("Pokaż kolejne wyniki",()->{int y=scroll.getScrollY();searchLimit+=50;renderList();scroll.post(()->scroll.scrollTo(0,y));},false));
        }
    }
    private void overview() {
        if (!state.optBoolean("ready")) { card().addView(text("Przygotowuję połączenie i lokalny zapis…", 16, MUTED, false)); return; }
        JSONObject updated=object(state,"updated_at");
        if (!state.optBoolean("connected") && !state.optString("mode").equals("demo") && updated.length()==0) {
            LinearLayout welcome = card(); welcome.addView(text("Dziennik pod ręką", 22, INK, true));
            welcome.addView(text("Połącz szkolne konto Synergia lub poznaj aplikację na danych przykładowych.", 15, MUTED, false));
            welcome.addView(button("Połącz konto Synergia", this::showLogin, true));
            welcome.addView(button("Zobacz demo", repository::demo, false));
        }
        spaced(list,button("Szukaj we wszystkich sekcjach",()->select("search"),false));
        changeFeed();
        LocalDate day=LocalDate.now().plusDays(overviewTomorrow?1:0); overviewDate=day;
        LinearLayout dayCard=card(), selector=row();
        weighted(selector,button("Dzisiaj",()->{overviewTomorrow=false;renderList();},!overviewTomorrow));
        weighted(selector,button("Jutro",()->{overviewTomorrow=true;renderList();},overviewTomorrow)); dayCard.addView(selector);
        dayCard.addView(text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy",Locale.forLanguageTag("pl"))),18,INK,true));
        LinearLayout shortcuts=row();
        long unread=items("messages").stream().filter(item->item.optBoolean("unread")).count();
        weighted(shortcuts,button("Nieprzeczytane wiadomości · "+(updated.has("messages")?unread:"—"),()->select("messages"),false));
        weighted(shortcuts,button("Oceny · "+(updated.has("grades")?items("grades").size():"—"),()->select("grades"),false));
        spaced(list,shortcuts);
        overviewGroup("Lekcje","timetable",items("timetable"),day,"Brak lekcji na ten dzień w pobranym planie.");
        overviewGroup("Zadania do zrobienia","homework",items("homework"),day,"Brak zadań do zrobienia z terminem na ten dzień w pobranych danych.");
        overviewGroup("Wydarzenia","schedule",items("schedule"),day,"Brak wydarzeń na ten dzień w pobranym terminarzu.");
        overviewGroup("Twoje przypomnienia","reminders",reminderUi.calendarItems(),day,"Brak zaplanowanych przypomnień na ten dzień.");
        for(String kind:new String[]{"grades","messages","announcements","attendance"}) {
            String error=object(state,"errors").optString(kind); if(!error.isEmpty())notice(title(kind)+": "+error);
        }
        List<JSONObject> grades=items("grades"); grades.sort(Comparator.comparing((JSONObject v)->v.optString("when")).reversed());
        if(!grades.isEmpty()) {
            list.addView(text("Ostatnie oceny",19,INK,true));
            for(JSONObject item:grades.subList(0,Math.min(3,grades.size())))entry(item,"grades");
        }
    }
    private void changeFeed() {
        JSONObject feed=object(state,"change_feed");JSONArray items=feed.optJSONArray("items");
        int total=items==null?0:items.length();LinearLayout c=card();
        c.addView(text("Od ostatniego wejścia · "+total,20,INK,true));
        double since=feed.optDouble("since",0);
        c.addView(text(since>0?"Od wejścia "+DisplayData.date(java.time.Instant.ofEpochMilli((long)(since*1000)).toString()):"Nowe i zmienione wpisy wykryte od uruchomienia tej funkcji.",12,MUTED,false));
        if(total==0) {c.addView(text("Brak nowych zmian. Pierwszy odczyt sekcji tworzy punkt odniesienia; kolejne pokażą tu nowości.",14,MUTED,false));return;}
        String[] kinds={"all","grades","messages","announcements","schedule","attendance","timetable","homework"};
        String[] labels={"Wszystkie źródła","Oceny","Wiadomości","Ogłoszenia","Terminarz","Frekwencja","Plan lekcji","Zadania domowe"};
        Spinner source=new Spinner(this);source.setContentDescription("Źródło zmian na ekranie Start");source.setMinimumHeight(dp(48));
        ArrayAdapter<String> options=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);source.setAdapter(options);
        int selected=Arrays.asList(kinds).indexOf(changesSource);if(selected<0){changesSource="all";selected=0;}source.setSelection(selected);c.addView(source);
        source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View v,int index,long id){if(source.isAttachedToWindow()&&!kinds[index].equals(changesSource)){changesSource=kinds[index];changesExpanded=false;renderList();scroll.scrollTo(0,0);}}
        });
        List<JSONObject> rows=new ArrayList<>();for(int i=0;i<total;i++){JSONObject row=items.optJSONObject(i);if(row!=null&&(changesSource.equals("all")||changesSource.equals(row.optString("kind"))))rows.add(row);}
        if(rows.isEmpty())c.addView(text("Brak zmian z tego źródła.",14,MUTED,false));
        for(JSONObject event:rows.subList(0,Math.min(changesExpanded?rows.size():3,rows.size()))) {
            JSONObject item=object(event,"item");String kind=event.optString("kind"),type=switch(event.optString("event")){case "new"->"Nowy wpis";case "removed"->"Usunięty wpis";case "restored"->"Przywrócony wpis";default->"Zaktualizowany wpis";};
            LinearLayout row=column();row.setPadding(0,dp(10),0,dp(10));row.setMinimumHeight(dp(64));
            row.addView(text(title(kind)+" · "+type,12,TEAL,true));row.addView(text(item.optString("title"),16,INK,true));
            String subtitle=item.optString("subtitle");if(!subtitle.isEmpty())row.addView(text(subtitle,13,MUTED,false));
            row.addView(text("Data wpisu: "+DisplayData.date(item.optString("when")),12,MUTED,false));
            row.setFocusable(true);row.setContentDescription("Zmiana: "+item.optString("title")+". Otwórz szczegóły");row.setOnClickListener(v->openChange(event));c.addView(row);
            View divider=new View(this);divider.setBackgroundColor(0xffe7edf1);c.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        if(rows.size()>3)c.addView(button(changesExpanded?"Zwiń zmiany":"Pokaż wszystkie zmiany · "+rows.size(),()->{changesExpanded=!changesExpanded;renderList();},false));
        if(feed.optBoolean("limited"))c.addView(text("Lista obejmuje ostatnie 300 zmienionych wpisów. Pełne dane znajdziesz w panelach.",12,MUTED,false));
    }
    private void openChange(JSONObject event) {
        String kind=event.optString("kind");JSONObject saved=object(event,"item");
        if(!event.optString("event").equals("removed"))for(JSONObject current:items(kind))if(current.optString("id").equals(saved.optString("id"))){details(current,kind);return;}
        try {
            JSONObject copy=new JSONObject(saved.toString());
            copy.put("details",(event.optString("event").equals("removed")?"Wpis został usunięty z pobranych danych.":"Wpis nie znajduje się już w bieżącej pobranej liście.")+"\nPoniżej zachowana kopia metadanych (do 4000 znaków opisu).\n\n"+copy.optString("details"));
            details(copy,kind);
        }catch(Exception invalid){showText("Zmiana","Szczegóły wpisu są niedostępne.");}
    }

    private void overviewGroup(String label,String kind,List<JSONObject> source,LocalDate day,String empty) {
        List<JSONObject> entries=HomeDayData.onDay(source,day,kind.equals("reminders"));
        int completed=kind.equals("homework")?(int)entries.stream().filter(homeworkStatus::done).count():0;
        if(kind.equals("homework"))entries.removeIf(homeworkStatus::done);
        LinearLayout c=card(), header=row();
        boolean loaded=kind.equals("reminders")?remindersReady:object(state,"updated_at").has(kind)&&(!kind.equals("homework")||homeworkStatus.ready());
        weighted(header,text(label+" · "+(loaded?entries.size():"—"),18,INK,true));
        header.addView(button("Wszystkie",()->{if(kind.equals("homework")){homeworkStatusFilter=2;homeworkUpcoming=false;}select(kind);},false)); c.addView(header);
        if(kind.equals("homework")&&completed>0)c.addView(text("Zrobione na ten dzień: "+completed+" · dostępne w panelu Zadania",12,TEAL,false));
        String fetched=object(state,"updated_at").optString(kind),error=kind.equals("reminders")?reminderUi.calendarError():object(state,"errors").optString(kind);
        if(!error.isEmpty())c.addView(text("Ostatni odczyt nie udał się: "+error,13,0xff994835,false));
        if(kind.equals("reminders")) { if(!remindersReady)c.addView(text("Wczytuję przypomnienia…",13,MUTED,false)); }
        else c.addView(text(fetched.isEmpty()?"Sekcja jeszcze niepobrana.":"Ostatni odczyt: "+DisplayData.date(fetched)+(state.optString("mode").equals("offline")?" · kopia lokalna":""),12,MUTED,false));
        if(entries.isEmpty() && (kind.equals("reminders")?remindersReady && error.isEmpty():!fetched.isEmpty())) c.addView(text(empty,14,MUTED,false));
        if(HomeDayData.unknownDates(source)>0)c.addView(text("Wpisy bez rozpoznanej daty znajdziesz w pełnym panelu.",12,MUTED,false));
        if(kind.equals("timetable") && HomeDayData.outsideTimetableRange(day,fetched) && !state.optString("mode").equals("demo")) c.addView(text("Ten dzień jest poza zakresem ostatniego odczytu planu. Odśwież dane.",12,0xff994835,false));
        for(JSONObject item:entries) {
            LinearLayout row=column(); row.setPadding(0,dp(10),0,dp(10)); row.setMinimumHeight(dp(56));
            row.addView(text(HomeDayData.timeLabel(item.optString("when")),12,TEAL,true));
            row.addView(text(item.optString("title","Bez tytułu"),16,INK,true));
            String subtitle=item.optString("subtitle"); if(!subtitle.isEmpty())row.addView(text(subtitle,13,MUTED,false));
            row.setOnClickListener(v->{if(kind.equals("reminders"))reminderUi.open(item.optString("reminder_id"));else details(item,kind);});
            row.setFocusable(true); row.setContentDescription(item.optString("title")+". Otwórz szczegóły"); c.addView(row);
            if(kind.equals("homework"))homeworkStatus.bind(c,item);
            View line=new View(this); line.setBackgroundColor(0xffe7edf1); c.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        }
    }

    private void calendar(List<JSONObject> rows) {
        YearMonth shown=shownCalendarMonth();LocalDate chosen=shownCalendarDay();
        LinearLayout c = card(), navigation = row();
        navigation.addView(button("‹", () -> { calendarMonth(shown.minusMonths(1)); calendarDay(null); renderList(); }, false));
        TextView monthName = text(shown.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("pl"))), 17, INK, true);
        monthName.setGravity(Gravity.CENTER); weighted(navigation, monthName);
        navigation.addView(button("›", () -> { calendarMonth(shown.plusMonths(1)); calendarDay(null); renderList(); }, false)); c.addView(navigation);
        LinearLayout shortcuts = row();
        weighted(shortcuts, button("Dzisiaj", () -> { calendarMonth(YearMonth.now()); calendarDay(LocalDate.now()); renderList(); }, false));
        weighted(shortcuts, button("Cały miesiąc", () -> { calendarDay(null); renderList(); }, false)); c.addView(shortcuts);
        LinearLayout weekdays = row(); for (String label : new String[]{"Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"}) {
            TextView v = text(label, 11, MUTED, true); v.setGravity(Gravity.CENTER); weighted(weekdays, v);
        } c.addView(weekdays);
        LocalDate start = shown.atDay(1).minusDays(shown.atDay(1).getDayOfWeek().getValue() - 1);
        int cells = ((shown.lengthOfMonth() + shown.atDay(1).getDayOfWeek().getValue() - 2) / 7 + 1) * 7;
        for (int index = 0; index < cells; index += 7) {
            LinearLayout week = row();
            for (int col = 0; col < 7; col++) {
                LocalDate day = start.plusDays(index + col);
                long count = rows.stream().filter(v -> day.equals(DisplayData.day(v.optString("when")))).count();
                String value = Integer.toString(day.getDayOfMonth()) + (count > 0 ? "\n" + count + " wpis." : "");
                Button b = button(value, () -> { calendarMonth(YearMonth.from(day)); calendarDay(day.equals(chosen)?null:day); renderList(); }, day.equals(chosen));
                b.setPadding(0, dp(3), 0, dp(3)); b.setTextSize(10); b.setMinWidth(0); b.setMinimumWidth(0);
                if (!YearMonth.from(day).equals(shown)) b.setAlpha(0.45f);
                if (day.equals(LocalDate.now())) b.setTypeface(null, Typeface.BOLD);
                b.setContentDescription(DisplayData.date(day.toString()) + (page.equals("calendar")?", wpisów: ":", wydarzeń: ") + count);
                weighted(week, b);
            } c.addView(week);
        }
        LocalDate fetched = DisplayData.day(object(state, "updated_at").optString("schedule"));
        if (fetched != null && !state.optString("mode").equals("demo") && (!page.equals("calendar")||calendarEvents)) {
            YearMonth covered = YearMonth.from(fetched);
            if (!shown.equals(covered) && !shown.equals(covered.plusMonths(1))) c.addView(text("Terminarz: miesiąc poza zakresem ostatniego odczytu. Wydarzenia mogą być niepełne.", 12, 0xff994835, false));
        }
        c.addView(text(chosen == null ? "Wybierz dzień, aby zawęzić listę." : "Wybrano " + DisplayData.date(chosen.toString()), 12, MUTED, false));
    }

    private void settings() {
        LinearLayout c = card(); c.addView(text("Konto i dane", 19, INK, true));
        c.addView(text(state.optBoolean("remembered") ? "Konto zapamiętane na tym telefonie." : "Hasło nie jest zapamiętane.", 15, MUTED, false));
        c.addView(button("Połącz / zmień konto", this::showLogin, true));
        c.addView(button("Włącz demo", repository::demo, false));
        c.addView(button("Usuń lokalne dane i konto", () -> new AlertDialog.Builder(this)
                .setTitle("Usunąć lokalne dane?").setMessage("Usunięte zostaną kopia dziennika, zapamiętane konto, statusy zadań i wszystkie własne przypomnienia na telefonie. Dane w Librusie pozostaną bez zmian.")
                .setNegativeButton("Anuluj", null).setPositiveButton("Usuń", (d, w) -> { repository.forget(); foreground.postDelayed(()->{reloadReminders();homeworkStatus.reload();}, 1000); }).show(), false));
        backupUi.render(card());
        LinearLayout info = card(); info.addView(text("Odświeżanie", 19, INK, true));
        info.addView(text("Najnowsze dane pobierane są po otwarciu lub powrocie do aplikacji przy aktywnej sesji albo zapamiętanym koncie. Kolejna próba najwcześniej po 5 minutach, także po ponownym uzyskaniu focusu. Otwarta aplikacja sprawdza dane co około 5 minut. Bez zapamiętanego hasła po zakończeniu procesu zaloguj się ponownie.", 15, MUTED, false));
        info.addView(text("Własne przypomnienia działają offline i po zamknięciu aplikacji. Dane, przypomnienia i opcjonalne hasło są szyfrowane kluczem Android Keystore; kopie zapasowe systemu są wyłączone.", 14, MUTED, false));
        updateUi.render(card());
        notificationSettings();
        googleCalendarUi.render(card());
        LinearLayout about = card(); about.addView(text("LibrusApp Android "+UpdateManager.installedName(this), 19, INK, true));
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

    private void notificationSettings() {
        LinearLayout c = card(); c.addView(text("Powiadomienia i alarmy", 19, INK, true));
        Switch data = new Switch(this); data.setText("Powiadomienia o nowych danych w tle"); data.setChecked(NotificationHub.dataEnabled(this));
        data.setPadding(0, dp(10), 0, dp(10)); c.addView(data);
        data.setOnCheckedChangeListener((b, value) -> { NotificationHub.dataEnabled(this, value); if (value) requestNotificationPermission(false); });
        Switch background = new Switch(this); background.setText("Pobieraj dane w tle"); background.setChecked(BackgroundSync.enabled(this));
        background.setPadding(0, dp(10), 0, dp(10)); c.addView(background);
        background.setOnCheckedChangeListener((b, value) -> {
            if (value && (!state.optBoolean("remembered") || state.optString("mode").equals("demo"))) {
                background.setChecked(false); Toast.makeText(this, "Połącz konto z opcją zapamiętania. Demo nie pobiera danych w tle.", Toast.LENGTH_LONG).show(); return;
            }
            try { BackgroundSync.enabled(this, value); }
            catch (Exception e) { background.setChecked(false); Toast.makeText(this, "Android nie przyjął harmonogramu odświeżania.", Toast.LENGTH_LONG).show(); }
        });
        if(getSharedPreferences("notification_options",0).getBoolean("resume_background_after_login",false))c.addView(text("Po imporcie: pobieranie w tle wróci po zalogowaniu z opcją zapamiętania konta.",13,MUTED,false));
        c.addView(text("Interwał pobierania w tle",14,INK,true));
        Spinner interval=new Spinner(this);
        String[] intervals={"15 minut","30 minut","60 minut"};int[] minutes={15,30,60};
        ArrayAdapter<String> intervalAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,intervals);
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);interval.setAdapter(intervalAdapter);
        interval.setContentDescription("Interwał pobierania w tle");interval.setMinimumHeight(dp(48));
        interval.setSelection(BackgroundSync.intervalMinutes(this)==60?2:BackgroundSync.intervalMinutes(this)==30?1:0);spaced(c,interval);
        Switch night=new Switch(this);night.setText("Przerwa nocna 20:00–07:00");night.setChecked(BackgroundSync.nightEnabled(this));
        night.setPadding(0,dp(10),0,dp(10));c.addView(night);
        TextView backgroundInfo=text("",13,MUTED,false);c.addView(backgroundInfo);
        Runnable updateBackgroundInfo=()->backgroundInfo.setText("W tle: około co "+BackgroundSync.intervalMinutes(this)+" minut, tylko z internetem. "
                +(BackgroundSync.nightEnabled(this)?"Przerwa 20:00–07:00 według czasu telefonu. ":"Przerwa nocna wyłączona. ")
                +"Otwarcie aplikacji pozwala pobrać dane także w nocy; własne przypomnienia nadal działają. Android może opóźniać odczyty. Alerty o zmianach wszystkich sekcji pojawiają się poza focusem; pierwszy odczyt i brak zmian są ciche.");
        updateBackgroundInfo.run();
        interval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                if(minutes[position]==BackgroundSync.intervalMinutes(MainActivity.this))return;
                try {BackgroundSync.intervalMinutes(MainActivity.this,minutes[position]);}
                catch(Exception e){background.setChecked(false);Toast.makeText(MainActivity.this,"Zapisano interwał, ale Android nie przyjął harmonogramu. Włącz pobieranie w tle ponownie.",Toast.LENGTH_LONG).show();}
                updateBackgroundInfo.run();
            }
        });
        night.setOnCheckedChangeListener((b,value)->{BackgroundSync.nightEnabled(this,value);updateBackgroundInfo.run();});
        c.addView(text(NotificationHub.allowed(this, NotificationHub.REMINDERS) ? "Powiadomienia przypomnień dostępne w Androidzie." : "Powiadomienia przypomnień zablokowane w Androidzie.", 13, MUTED, false));
        spaced(c, button("Test powiadomienia", () -> requestNotificationPermission(true), true));
        spaced(c, button("Ustawienia powiadomień Androida", () -> NotificationHub.settings(this), false));
        c.addView(text(ReminderAlarms.exact(this) ? "Dokładne alarmy: dostępne." : "Dokładne alarmy: brak zgody — możliwe opóźnienie.", 13, MUTED, false));
        spaced(c, button("Zgoda na dokładne alarmy", () -> { if (ReminderAlarms.exact(this)) Toast.makeText(this, "Dokładne alarmy są już dostępne.", Toast.LENGTH_SHORT).show(); else ReminderAlarms.requestExact(this); }, false));
        c.addView(text("Tryb Nie przeszkadzać i oszczędzanie baterii mogą wyciszać lub opóźniać alerty. Po wymuszonym zatrzymaniu otwórz aplikację ponownie. Przypomnienia mają oddzielny kanał i nie zależą od powiadomień o nowych danych.", 13, MUTED, false));
        notice(NotificationHub.error(this));
    }
    private void requestNotificationPermission(boolean test) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, test ? 411 : 410); return;
        }
        if (test) testNotification();
    }
    private void testNotification() {
        String result = NotificationHub.test(this);
        Toast.makeText(this, result.equals("sent") ? "Przekazano test do Androida. Widoczność zależy od ustawień systemu." : "Android blokuje powiadomienia. Sprawdź ustawienia aplikacji i kanału przypomnień.", Toast.LENGTH_LONG).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(backupUi!=null)backupUi.result(request,result,data);}
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == GoogleCalendarUi.PERMISSION) { googleCalendarUi.permissionResult(); renderList(); return; }
        if (request == 411 && grants.length > 0 && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) testNotification();
        if (request == 410 || request == 411) { renderList(); if (grants.length == 0 || grants[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "Bez zgody Android nie pokaże powiadomień. Przypomnienia pozostają na liście.", Toast.LENGTH_LONG).show(); }
    }

    private void details(JSONObject item, String kind) {
        Dialog dialog = new Dialog(this);
        LinearLayout body = column(); body.setPadding(dp(18), dp(16), dp(18), dp(16)); body.setBackgroundColor(WHITE);
        body.addView(text(item.optString("title", "Szczegóły"), 21, INK, true));
        if (item.has("subtitle") || item.has("when"))
            body.addView(text(item.optString("subtitle") + " · " + DisplayData.date(item.optString("when")), 13, MUTED, false));
        if(kind.equals("announcements")&&item.optBoolean("archived"))body.addView(text("Zapisana kopia — ogłoszenia nie ma na ostatniej pobranej liście Librusa.",13,MUTED,false));
        if (kind.equals("messages")) {
            body.addView(text("Pobranie treści może oznaczyć wiadomość jako przeczytaną w Librusie.", 13, MUTED, false));
            Button read = button("Pobierz treść", () -> { dialog.dismiss(); repository.readMessage(item.optString("id")); }, true);
            read.setEnabled(!state.optBoolean("busy")); body.addView(read);
        }
        if(kind.equals("homework")) {
            homeworkStatus.bind(body,item);
            body.addView(text("Twój status na tym telefonie. Oddawanie pracy odbywa się w Librusie.",12,MUTED,false));
        }
        if (kind.equals("homework") && !item.optBoolean("body_loaded")) {
            Button read = button("Pobierz treść zadania", () -> { dialog.dismiss(); repository.readHomework(item.optString("id")); }, true);
            read.setEnabled(!state.optBoolean("busy")); body.addView(read);
        }
        if (java.util.Arrays.asList("messages", "announcements", "schedule", "homework").contains(kind)) {
            Button remind = button("Przypomnij mi…", () -> { dialog.dismiss(); reminderUi.add(item, kind); }, false);
            LinearLayout.LayoutParams remindParams = new LinearLayout.LayoutParams(-1, -2); remindParams.topMargin = dp(8); remindParams.bottomMargin = dp(8); body.addView(remind, remindParams);
        }
        ScrollView detailScroll = new ScrollView(this);
        TextView content = text(item.optString("details", "Brak dodatkowego opisu."), 16, INK, false); content.setTextIsSelectable(true);
        detailScroll.addView(content); body.addView(detailScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = row();
        weighted(actions, button("Otwórz w Librusie", () -> { if (kind.equals("homework")) openHomework(item.optString("id")); else openOfficial(kind); }, false));
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
    @Override public void onHomework(JSONObject content) {
        if (content.has("error")) { showText("Nie pobrano treści zadania", content.optString("error")); return; }
        for (JSONObject item : items("homework")) if (item.optString("id").equals(content.optString("id"))) {
            try {
                JSONObject detail = new JSONObject(item.toString());
                detail.put("details", item.optString("details") + "\n\n" + content.optString("text")).put("body_loaded", true);
                details(detail, "homework");
            } catch (Exception ignored) { showText("Zadanie domowe", content.optString("text")); }
            return;
        }
        showText("Zadanie niedostępne", "Zadanie nie znajduje się już w pobranej liście.");
    }
    private void openHomework(String id) {
        if (!id.matches("homework:[0-9]{1,20}")) { openOfficial("homework"); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://synergia.librus.pl/moje_zadania/podglad/" + id.substring("homework:".length())))); }
        catch (Exception error) { Toast.makeText(this, "Brak przeglądarki do otwarcia Librusa.", Toast.LENGTH_LONG).show(); }
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
            case "homework" -> "moje_zadania";
            case "attendance" -> "przegladaj_nb/uczen";
            case "timetable" -> "przegladaj_plan_lekcji";
            default -> "";
        };
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://synergia.librus.pl/" + path))); }
        catch (Exception error) { Toast.makeText(this, "Brak przeglądarki do otwarcia Librusa.", Toast.LENGTH_LONG).show(); }
    }
}
