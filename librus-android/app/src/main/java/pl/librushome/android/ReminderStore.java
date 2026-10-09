package pl.librushome.android;

import android.content.Context;
import org.json.*;
import java.time.*;
import java.util.UUID;

/** Independent encrypted storage, serialized across UI, alarms and boot recovery. */
public final class ReminderStore {
    static final Object LOCK = new Object();
    private final SecureStore secure;
    public ReminderStore(Context c) { this(c, "reminders.aes", "LibrusApp.reminders.v1"); }
    ReminderStore(Context c, String file, String alias) { secure = new SecureStore(c, file, alias); }
    public JSONArray list() throws Exception { synchronized (LOCK) {
        String raw = secure.read();
        if (raw.isEmpty()) return new JSONArray();
        JSONObject saved = new JSONObject(raw);
        if (saved.optInt("version") != 1 || !(saved.opt("items") instanceof JSONArray)) throw new IllegalStateException("Invalid reminder store");
        return saved.getJSONArray("items");
    } }
    private void write(JSONArray rows) throws Exception { secure.write(new JSONObject().put("version", 1).put("items", rows).toString()); }
    public static long localTime(LocalDate date, LocalTime time, ZoneId zone) {
        LocalDateTime local = LocalDateTime.of(date, time);
        java.util.List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) throw new IllegalArgumentException("Ta godzina nie istnieje przy zmianie czasu. Wybierz inną.");
        // In the autumn overlap choose the earlier occurrence and display the offset in the UI.
        return local.toInstant(offsets.get(0)).toEpochMilli();
    }
    public JSONObject save(String id, String kind, String source, String profile, boolean demo, String title,
                           String note, long due, boolean showText, long now) throws Exception {
        return save(id, kind, source, profile, demo, title, note, due, showText, now, "");
    }
    public JSONObject save(String id, String kind, String source, String profile, boolean demo, String title,
                           String note, long due, boolean showText, long now, String sourceWhen) throws Exception { synchronized (LOCK) {
        note = note.trim();
        if (note.isEmpty() || note.codePointCount(0, note.length()) > 200) throw new IllegalArgumentException("Wpisz treść od 1 do 200 znaków.");
        if (due <= now) throw new IllegalArgumentException("Wybierz termin w przyszłości.");
        if (!java.util.Arrays.asList("messages", "announcements", "schedule", "homework").contains(kind) || source.isEmpty()) throw new IllegalArgumentException("Wybierz wiadomość, ogłoszenie, wydarzenie lub zadanie domowe.");
        JSONArray rows = list();
        int found = -1;
        if (id != null) for (int i = 0; i < rows.length(); i++) if (rows.getJSONObject(i).getString("id").equals(id)) found = i;
        if (id != null && found < 0) throw new IllegalArgumentException("Przypomnienie nie istnieje.");
        if (found < 0 && rows.length() >= 1000) throw new IllegalArgumentException("Usuń stare przypomnienia przed dodaniem kolejnych.");
        JSONObject item = new JSONObject().put("id", id == null ? UUID.randomUUID().toString() : id)
                .put("kind", kind).put("source_id", source).put("profile", profile).put("demo", demo)
                .put("title", title).put("note", note).put("due", due).put("show_text", showText)
                .put("status", "pending").put("delivery", "");
        if (sourceWhen.isEmpty() && found >= 0) sourceWhen = rows.getJSONObject(found).optString("source_when");
        if (!sourceWhen.isEmpty()) item.put("source_when", sourceWhen);
        if (found < 0) rows.put(item); else rows.put(found, item);
        write(rows); // Failure leaves the previous file intact, and caller does not schedule.
        return item;
    } }
    public JSONObject get(String id) throws Exception { synchronized (LOCK) {
        JSONArray rows = list();
        for (int i = 0; i < rows.length(); i++) if (rows.getJSONObject(i).optString("id").equals(id)) return rows.getJSONObject(i);
        return null;
    } }
    public JSONObject claim(String id, long now) throws Exception { synchronized (LOCK) {
        JSONArray rows = list();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.getJSONObject(i);
            if (item.optString("id").equals(id) && item.optString("status").equals("pending") && item.optLong("due") <= now) {
                item.put("status", "fired").put("fired_at", now).put("delivery", "attempting");
                write(rows); // Persist the claim before posting: concurrent receivers cannot duplicate it.
                return item;
            }
        }
        return null;
    } }
    /** Only the exact fired notification may reschedule this reminder, once. */
    public JSONObject snooze(String id, long firedAt, long previousDue, long now) throws Exception { synchronized (LOCK) {
        JSONArray rows = list();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.getJSONObject(i);
            if (item.optString("id").equals(id) && item.optString("status").equals("fired")
                    && item.optLong("fired_at", -1) == firedAt && item.optLong("due", -1) == previousDue) {
                item.put("due", Math.addExact(now, 30 * 60 * 1000L)).put("status", "pending").put("delivery", "");
                item.remove("fired_at"); write(rows); return item;
            }
        }
        return null;
    } }
    public void delivered(String id, long firedAt, String outcome) throws Exception { synchronized (LOCK) {
        JSONArray rows = list();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.getJSONObject(i);
            if (item.optString("id").equals(id) && item.optString("status").equals("fired") && item.optLong("fired_at") == firedAt) {
                item.put("delivery", outcome); write(rows); return;
            }
        }
    } }
    public void delete(String id) throws Exception { synchronized (LOCK) {
        JSONArray before = list(), after = new JSONArray();
        for (int i = 0; i < before.length(); i++) if (!before.getJSONObject(i).optString("id").equals(id)) after.put(before.getJSONObject(i));
        write(after);
    } }
    void replace(JSONArray rows) throws Exception { synchronized (LOCK) { write(rows); } }
    public void clear() throws Exception { synchronized (LOCK) { secure.clear(); } }
}
