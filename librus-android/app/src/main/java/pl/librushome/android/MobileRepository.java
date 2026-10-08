package pl.librushome.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-scoped session; only the worker touches Python, credentials or disk. */
public final class MobileRepository {
    public interface Listener {
        void onState(JSONObject state);
        void onMessage(JSONObject content);
        void onHomework(JSONObject content);
    }
    private interface Operation { void run() throws Exception; }
    @android.annotation.SuppressLint("StaticFieldLeak") // Only an application context; never an Activity.
    private static MobileRepository instance;
    public static synchronized MobileRepository get(Context context) {
        if (instance == null) instance = new MobileRepository(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final SecureStore store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile boolean busy;
    private volatile boolean initialized;
    private volatile boolean focused;
    public void focused(boolean value) { focused = value; }
    private JSONObject state = new JSONObject();
    private PyObject service;
    private volatile String storageError = "";
    private volatile String operationError = "";
    private boolean writeEnabled = true;

    private MobileRepository(Context context) {
        this.context = context;
        store = new SecureStore(context);
    }

    public void observe(Listener value) {
        listener = value;
        if (value != null) dispatch();
    }

    private void initialize() {
        if (initialized) return;
        if (!Python.isStarted()) Python.start(new AndroidPlatform(context));
        service = Python.getInstance().getModule("mobile_bridge").callAttr("get_service");
        service.callAttr("set_progress", this);
        try { service.callAttr("restore_json", store.read()); }
        catch (Exception error) {
            storageError = "Nie można odczytać lokalnego zapisu. Zachowano plik. Usuń lokalne dane lub połącz konto ponownie.";
            writeEnabled = false;
        }
        JSONObject recovered;
        try { recovered = new JSONObject(service.callAttr("state_json").toString()); }
        catch (Exception ignored) { recovered = new JSONObject(); }
        if (BackgroundSync.enabled(context)) {
            if (recovered.optBoolean("remembered") && !recovered.optBoolean("auto_login_blocked")) BackgroundSync.configure(context, true);
            else BackgroundSync.enabled(context, false);
        }
        initialized = true;
    }

    private void submit(Operation operation) { submit(operation, null); }
    private synchronized void submit(Operation operation, Runnable completion) {
        if (busy) { if (completion != null) main.post(completion); return; }
        busy = true;
        operationError = "";
        dispatch();
        worker.execute(() -> {
            try { initialize(); operation.run(); }
            catch (Exception error) {
                // Never log Python/request exceptions: they may include account data.
                operationError = "Nie udało się zakończyć operacji. Spróbuj ponownie lub uruchom aplikację ponownie.";
            } finally {
                try { if (service != null) update(service.callAttr("state_json").toString()); }
                catch (Exception ignored) { }
                main.post(() -> { busy = false; dispatch(); if (completion != null) completion.run(); });
            }
        });
    }

    private boolean persist() {
        if (!writeEnabled) return false;
        String json = service.callAttr("export_json").toString();
        if (json.isEmpty()) return false; // Demo must never replace a real account.
        try { store.write(json); storageError = ""; return true; }
        catch (Exception error) { storageError = "Nie zapisano danych. Bieżący widok pozostaje dostępny; po zamknięciu zmiany mogą zniknąć."; return false; }
    }

    public void open() {
        if (!focused && BackgroundSync.nightPaused(context)) return;
        if (busy || (initialized && SyncPolicy.remaining(context) > 0)) return;
        submit(() -> {
            if (!focused && BackgroundSync.nightPaused(context)) return;
            JSONObject before = new JSONObject(service.callAttr("state_json").toString());
            if (before.optString("mode").equals("demo") || (!before.optBoolean("connected") && !before.optBoolean("remembered"))) return;
            if (!SyncPolicy.begin(context)) return;
            service.callAttr("open"); finishSync();
        });
    }

    public void refresh() {
        if (busy) return;
        if (SyncPolicy.remaining(context) > 0) {
            operationError = "Dane pobierane są nie częściej niż co 5 minut. Poczekaj do końca przerwy.";
            dispatch(); return;
        }
        open();
    }

    public void connect(String login, String password, boolean remember) {
        GoogleCalendarSync.demo(false);
        submit(() -> {
            if (!remember) {
                BackgroundSync.enabled(context, false);
                // Revoke the persisted password before trying any new credentials.
                service.callAttr("revoke_remembered");
                try {
                    String old = store.read();
                    if (!old.isEmpty()) {
                        JSONObject saved = new JSONObject(old);
                        saved.put("credentials", JSONObject.NULL);
                        store.write(saved.toString());
                    }
                } catch (Exception error) {
                    storageError = "Nie udało się usunąć poprzednio zapamiętanego hasła. Usuń lokalne dane w Ustawieniach.";
                    return;
                }
            }
            boolean mayFetch = SyncPolicy.begin(context);
            service.callAttr("connect", login, password, remember, mayFetch);
            // An explicit successful login permits replacing an unreadable old state.
            if (service.callAttr("state").get("connected").toBoolean()) writeEnabled = true;
            finishSync();
        });
    }

    private void finishSync() throws Exception {
        JSONObject current = new JSONObject(service.callAttr("state_json").toString());
        boolean saved = persist();
        if (saved && !current.optString("mode").equals("demo")) GoogleCalendarSync.syncStored(context);
        if (saved && !focused && !current.optString("mode").equals("demo") && NotificationHub.dataEnabled(context)) {
            String outcome = NotificationHub.changes(context, current.optJSONObject("changes") == null ? new JSONObject() : current.getJSONObject("changes"));
            if (outcome.equals("blocked")) operationError = "Nowe dane zapisano, ale Android blokuje powiadomienia. Sprawdź ustawienia powiadomień.";
        }
        if (BackgroundSync.enabled(context)) {
            if (!current.optBoolean("remembered") || current.optString("mode").equals("demo")) BackgroundSync.enabled(context, false);
            else BackgroundSync.configure(context, true);
        }
    }

    public void backgroundRefresh(Runnable completion) {
        if (!BackgroundSync.enabled(context)) {
            BackgroundSync.configure(context, false); main.post(completion); return;
        }
        // Skip before Python startup, Keystore reads and login. Recheck after
        // queueing so a job crossing 20:00 cannot begin fetching at night.
        if (BackgroundSync.nightPaused(context)) { main.post(completion); return; }
        if (busy) { main.post(completion); return; }
        submit(() -> {
            if (BackgroundSync.nightPaused(context)) return;
            JSONObject before = new JSONObject(service.callAttr("state_json").toString());
            if (!before.optBoolean("remembered") || before.optString("mode").equals("demo")) {
                BackgroundSync.enabled(context, false); return;
            }
            if (!SyncPolicy.begin(context)) return;
            service.callAttr("open"); finishSync();
            JSONObject after = new JSONObject(service.callAttr("state_json").toString());
            // Expiry of an old session permits one recovery at the next scheduled attempt.
            // Only a rejected login/fresh session disables background reads.
            if (after.optBoolean("needs_login") && after.optBoolean("auto_login_blocked")
                    && after.optDouble("retry_after") * 1000 <= System.currentTimeMillis()) {
                BackgroundSync.enabled(context, false);
                operationError = "Odczyt w tle zatrzymany: zaloguj się ponownie i włącz odświeżanie w Ustawieniach.";
            }
        }, completion);
    }

    public void demo() { GoogleCalendarSync.demo(true); submit(() -> { BackgroundSync.enabled(context, false); service.callAttr("demo"); }); }

    public void readMessage(String itemId) {
        submit(() -> {
            JSONObject content = new JSONObject(service.callAttr("read_message", itemId).toString());
            persist();
            main.post(() -> { if (listener != null) listener.onMessage(content); });
        });
    }

    public void readHomework(String itemId) {
        submit(() -> {
            JSONObject content = new JSONObject(service.callAttr("read_homework", itemId).toString());
            persist(); // Preserve a possible HTTP 429 cooldown or expired-session flag.
            main.post(() -> { if (listener != null) listener.onHomework(content); });
        });
    }

    public void forget() {
        GoogleCalendarSync.disable(context);
        submit(() -> {
            BackgroundSync.enabled(context, false);
            service.callAttr("forget");
            try { ReminderAlarms.clear(context); store.clear(); storageError = ""; writeEnabled = true; }
            catch (Exception error) { storageError = "Wyczyszczono pamięć, ale nie usunięto pliku danych. Spróbuj ponownie przed zamknięciem aplikacji."; writeEnabled = false; }
        });
    }

    /** Called by the Python bridge on the worker for partial section progress. */
    public void update(String json) {
        main.post(() -> {
            try { state = new JSONObject(json); }
            catch (Exception ignored) { }
            dispatch();
        });
    }

    private void dispatch() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::dispatch); return; }
        try {
            JSONObject display = new JSONObject(state.toString());
            display.put("busy", busy).put("ready", initialized).put("storage_error", storageError);
            display.put("operation_error", operationError);
            if (listener != null) listener.onState(display);
        } catch (Exception ignored) { }
    }
}
