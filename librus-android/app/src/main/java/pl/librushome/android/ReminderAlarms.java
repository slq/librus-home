package pl.librushome.android;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.*;
import java.util.concurrent.*;

/** Separate executor: reminders must not wait for slow school requests. */
public final class ReminderAlarms {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    public static void execute(Runnable task) { WORK.execute(task); }
    public static boolean exact(Context c) { return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms(); }
    public static void requestExact(Activity a) {
        if (Build.VERSION.SDK_INT >= 31) a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + a.getPackageName())));
    }
    private static PendingIntent pending(Context c, String id) {
        Intent intent = new Intent(c, ReminderReceiver.class).setAction("pl.librushome.android.REMIND")
                .setData(Uri.parse("librusapp://alarm/" + id)).putExtra("id", id);
        return PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(Context c, JSONObject item) {
        if (!item.optString("status").equals("pending")) return;
        NotificationHub.manager(c).cancel(item.optString("id"), 1); // Editing a fired reminder removes its old banner.
        long at = Math.max(System.currentTimeMillis() + 1000, item.optLong("due"));
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent value = pending(c, item.optString("id"));
        try {
            if (exact(c)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, value);
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, value);
        } catch (SecurityException revoked) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, value); }
    }
    public static void cancel(Context c, String id) {
        PendingIntent value = pending(c, id); c.getSystemService(AlarmManager.class).cancel(value); value.cancel();
        NotificationHub.manager(c).cancel(id, 1);
    }
    public static void restore(Context c) throws Exception {
        synchronized (ReminderStore.LOCK) {
            JSONArray rows = new ReminderStore(c).list();
            for (int i = 0; i < rows.length(); i++) schedule(c, rows.getJSONObject(i));
        }
    }
    public static void fire(Context c, String id) throws Exception {
        synchronized (ReminderStore.LOCK) {
            ReminderStore store = new ReminderStore(c);
            JSONObject item = store.claim(id, System.currentTimeMillis());
            if (item == null) { JSONObject future = store.get(id); if (future != null) schedule(c, future); return; }
            String outcome;
            try { outcome = NotificationHub.reminder(c, item); } catch (Exception e) { outcome = "failed"; }
            store.delivered(id, item.optLong("fired_at"), outcome);
        }
    }
    public static void clear(Context c) throws Exception {
        synchronized (ReminderStore.LOCK) {
            ReminderStore store = new ReminderStore(c);
            JSONArray rows;
            try { rows = store.list(); } catch (Exception unreadable) { rows = new JSONArray(); }
            store.clear();
            for (int i = 0; i < rows.length(); i++) cancel(c, rows.getJSONObject(i).optString("id"));
            NotificationHub.manager(c).cancelAll(); NotificationHub.error(c, "");
        }
    }
}
