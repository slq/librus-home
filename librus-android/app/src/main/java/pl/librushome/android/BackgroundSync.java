package pl.librushome.android;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.TimeUnit;

/** Android owns the cadence; this is not a permanent foreground service. */
public final class BackgroundSync {
    static final int JOB_ID = 1701;
    static final int DEFAULT_MINUTES = 15;
    static final long FLEX_MS = TimeUnit.MINUTES.toMillis(5);
    public static boolean enabled(Context c) { return c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).getBoolean("background", false); }
    public static void enabled(Context c, boolean value) {
        c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).edit().putBoolean("background", value).remove("resume_background_after_login").apply();
        configure(c, value);
    }
    public static int intervalMinutes(Context c) {
        int saved=c.getSharedPreferences("notification_options",Context.MODE_PRIVATE).getInt("background_minutes",DEFAULT_MINUTES);
        return validInterval(saved)?saved:DEFAULT_MINUTES;
    }
    static boolean validInterval(int value) { return value==15||value==30||value==60; }
    public static void intervalMinutes(Context c,int value) {
        if(!validInterval(value))throw new IllegalArgumentException("Wybierz 15, 30 lub 60 minut.");
        c.getSharedPreferences("notification_options",Context.MODE_PRIVATE).edit().putInt("background_minutes",value).apply();
        configure(c,enabled(c));
    }
    public static boolean nightEnabled(Context c) {
        return c.getSharedPreferences("notification_options",Context.MODE_PRIVATE).getBoolean("night_pause",true);
    }
    public static void nightEnabled(Context c,boolean value) {
        c.getSharedPreferences("notification_options",Context.MODE_PRIVATE).edit().putBoolean("night_pause",value).apply();
    }
    static boolean nightTime(java.time.LocalTime time) {
        return !time.isBefore(java.time.LocalTime.of(20,0))||time.isBefore(java.time.LocalTime.of(7,0));
    }
    static java.util.function.Supplier<java.time.LocalTime> localTime = () -> java.time.LocalTime.now(java.time.ZoneId.systemDefault());
    public static boolean nightPaused(Context c) {
        return nightEnabled(c)&&nightTime(localTime.get());
    }
    public static void configure(Context c, boolean enabled) {
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        if (!enabled) { jobs.cancel(JOB_ID); return; }
        long interval=TimeUnit.MINUTES.toMillis(intervalMinutes(c));
        JobInfo existing = jobs.getPendingJob(JOB_ID);
        if (existing != null && existing.isPeriodic() && existing.getIntervalMillis() == interval && existing.getFlexMillis() == FLEX_MS
                && existing.isPersisted() && existing.getNetworkType() == JobInfo.NETWORK_TYPE_ANY
                && existing.getService().equals(new ComponentName(c, SyncJobService.class))) return;
        // Replace an older cadence under the same ID, without resetting matching
        // jobs on every focus or successful read (which would postpone them).
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(c, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(interval, FLEX_MS)
                .setPersisted(true).build();
        if (jobs.schedule(info) != JobScheduler.RESULT_SUCCESS) {
            c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).edit().putBoolean("background", false).apply();
            throw new IllegalStateException("Android did not accept the background job");
        }
    }
}
