package pl.librushome.android;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;

/** Notification payloads never contain credentials or school content by default. */
public final class NotificationHub {
    static final String DATA = "school_changes", REMINDERS = "personal_reminders";
    private NotificationHub() { }
    static NotificationManager manager(Context c) { return c.getSystemService(NotificationManager.class); }
    public static void channels(Context c) {
        NotificationChannel data = new NotificationChannel(DATA, "Zmiany w dzienniku", NotificationManager.IMPORTANCE_DEFAULT);
        data.setDescription("Zbiorcze liczby zmian bez danych ucznia i treści wpisów.");
        NotificationChannel alerts = new NotificationChannel(REMINDERS, "Twoje przypomnienia", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Przypomnienia ustawione dla wiadomości, ogłoszeń, terminarza i zadań domowych.");
        alerts.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager(c).createNotificationChannels(java.util.Arrays.asList(data, alerts));
    }
    public static boolean allowed(Context c, String channel) {
        channels(c);
        NotificationChannel value = manager(c).getNotificationChannel(channel);
        return manager(c).areNotificationsEnabled() && value != null && value.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    private static String post(Context c, String channel, String tag, String title, String text, Intent open) {
        return post(c, channel, tag, title, text, open, null);
    }
    private static String post(Context c, String channel, String tag, String title, String text, Intent open, Notification.Action action) {
        if (!allowed(c, channel)) return "blocked";
        PendingIntent tap = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification publicCopy = new Notification.Builder(c, channel).setSmallIcon(pl.librushome.android.R.drawable.ic_notification)
                .setContentTitle("LibrusApp").setContentText("Otwórz aplikację, aby zobaczyć szczegóły.").setVisibility(Notification.VISIBILITY_PUBLIC).build();
        Notification.Builder builder = new Notification.Builder(c, channel).setSmallIcon(pl.librushome.android.R.drawable.ic_notification)
                .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(tap).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicCopy).setCategory(channel.equals(REMINDERS) ? Notification.CATEGORY_REMINDER : Notification.CATEGORY_STATUS);
        if (action != null) builder.addAction(action);
        try { manager(c).notify(tag, 1, builder.build()); return "sent"; }
        catch (SecurityException e) { return "blocked"; }
    }
    private static Intent open(Context c, String path) {
        return new Intent(c, MainActivity.class).setData(android.net.Uri.parse("librusapp://" + path))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    public static String reminder(Context c, JSONObject item) {
        Intent intent = open(c, "reminder/" + item.optString("id")).putExtra("reminder_id", item.optString("id"));
        String text = item.optBoolean("show_text") ? item.optString("note") : "Masz zaplanowane przypomnienie. Otwórz LibrusApp.";
        Notification.Action action = null;
        if (item.optString("status").equals("fired") && item.has("fired_at")) {
            Intent snooze = new Intent(c, ReminderReceiver.class).setAction("pl.librushome.android.SNOOZE")
                    .setData(android.net.Uri.parse("librusapp://snooze/" + item.optString("id") + "/" + item.optLong("fired_at") + "/" + item.optLong("due")))
                    .putExtra("id", item.optString("id")).putExtra("fired_at", item.optLong("fired_at")).putExtra("due", item.optLong("due"));
            PendingIntent later = PendingIntent.getBroadcast(c, 0, snooze, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            action = new Notification.Action.Builder(null, "Przypomnij za 30 minut", later).build();
        }
        return post(c, REMINDERS, item.optString("id"), "LibrusApp · Przypomnienie", text, intent, action);
    }
    public static String changes(Context c, JSONObject changes) {
        String[] keys = {"grades", "messages", "announcements", "schedule", "attendance", "timetable", "homework"};
        String[] labels = {"Oceny", "Wiadomości", "Ogłoszenia", "Terminarz", "Frekwencja", "Plan lekcji", "Zadania domowe"};
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.length; i++) if (changes.optInt(keys[i]) > 0) {
            if (text.length() > 0) text.append(" · ");
            text.append(labels[i]).append(": ").append(changes.optInt(keys[i]));
        }
        if (text.length() == 0) return "quiet";
        return post(c, DATA, "changes", "LibrusApp · Nowe dane", text + ". Otwórz aplikację.", open(c, "changes"));
    }
    public static String test(Context c) {
        return post(c, REMINDERS, "test", "LibrusApp · Test", "Powiadomienie testowe. Dane konta nie są ujawniane.", open(c, "test"));
    }
    public static void settings(Activity activity) {
        activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
    }
    public static boolean dataEnabled(Context c) { return c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).getBoolean("data", false); }
    public static void dataEnabled(Context c, boolean enabled) { c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).edit().putBoolean("data", enabled).apply(); }
    static void error(Context c, String value) { c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).edit().putString("alarm_error", value).apply(); }
    public static String error(Context c) { return c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).getString("alarm_error", ""); }
}
