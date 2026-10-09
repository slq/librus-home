package pl.librushome.android;
import android.content.*;

/** Explicit alarm delivery and system recovery after reboot, update or clock change. */
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        PendingResult pending = goAsync();
        ReminderAlarms.execute(() -> {
            try {
                BackupStore.recover(app);
                if ("pl.librushome.android.REMIND".equals(intent.getAction())) ReminderAlarms.fire(app, intent.getStringExtra("id"));
                else if ("pl.librushome.android.SNOOZE".equals(intent.getAction()))
                    ReminderAlarms.snooze(app, intent.getStringExtra("id"), intent.getLongExtra("fired_at", -1), intent.getLongExtra("due", -1));
                else { if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))UpdateManager.configure(app); ReminderAlarms.restore(app); GoogleCalendarSync.request(app,null); }
                NotificationHub.error(app, "");
                if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
                    UpdateManager.configure(app);
                    try { BackgroundSync.configure(app, BackgroundSync.enabled(app)); }
                    catch (Exception syncFailure) {
                        NotificationHub.error(app, "Nie udało się odtworzyć pobierania w tle po aktualizacji. Sprawdź ustawienia aplikacji.");
                    }
                }
            } catch (Exception e) {
                NotificationHub.error(app, "Nie udało się obsłużyć przypomnienia. Sprawdź listę przypomnień; zachowano lokalny zapis.");
            } finally {
                try { app.sendBroadcast(new Intent("pl.librushome.android.REMINDERS_CHANGED").setPackage(app.getPackageName()), app.getPackageName() + ".INTERNAL"); }
                finally { pending.finish(); }
            }
        });
    }
}
