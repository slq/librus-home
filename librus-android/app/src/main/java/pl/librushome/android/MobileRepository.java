package pl.librushome.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
    private JSONObject state = new JSONObject();
    private PyObject service;
    private volatile String storageError = "";
    private volatile String operationError = "";
    private boolean writeEnabled = true;
    private volatile long lastOpen = -60000;

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
        initialized = true;
    }

    private synchronized void submit(Operation operation) {
        if (busy) return;
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
                main.post(() -> { busy = false; dispatch(); });
            }
        });
    }

    private void persist() {
        if (!writeEnabled) return;
        String json = service.callAttr("export_json").toString();
        if (json.isEmpty()) return; // Demo must never replace a real account.
        try { store.write(json); storageError = ""; }
        catch (Exception error) { storageError = "Nie zapisano danych. Bieżący widok pozostaje dostępny; po zamknięciu zmiany mogą zniknąć."; }
    }

    public void open() {
        if (busy || (initialized && SystemClock.elapsedRealtime() - lastOpen < 60000)) return;
        lastOpen = SystemClock.elapsedRealtime();
        submit(() -> { service.callAttr("open"); persist(); });
    }

    public void refresh() {
        if (busy) return;
        if (SystemClock.elapsedRealtime() - lastOpen < 60000) {
            operationError = "Poczekaj minutę od ostatniego odczytu, aby ograniczyć liczbę zapytań.";
            dispatch();
            return;
        }
        lastOpen = SystemClock.elapsedRealtime();
        submit(() -> { service.callAttr("refresh"); persist(); });
    }

    public void connect(String login, String password, boolean remember) {
        submit(() -> {
            if (!remember) {
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
            service.callAttr("connect", login, password, remember);
            lastOpen = SystemClock.elapsedRealtime();
            // An explicit successful login permits replacing an unreadable old state.
            if (service.callAttr("state").get("connected").toBoolean()) writeEnabled = true;
            persist();
        });
    }

    public void demo() { submit(() -> service.callAttr("demo")); }

    public void readMessage(String itemId) {
        submit(() -> {
            JSONObject content = new JSONObject(service.callAttr("read_message", itemId).toString());
            persist();
            main.post(() -> { if (listener != null) listener.onMessage(content); });
        });
    }

    public void forget() {
        submit(() -> {
            service.callAttr("forget");
            try { store.clear(); storageError = ""; writeEnabled = true; }
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
