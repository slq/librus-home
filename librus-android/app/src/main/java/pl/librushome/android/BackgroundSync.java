package pl.librushome.android;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.TimeUnit;

/** Android owns the cadence; this is not a permanent foreground service. */
public final class BackgroundSync {
    static final int JOB_ID = 1701;
    static final long INTERVAL_MS = TimeUnit.MINUTES.toMillis(15);
    static final long FLEX_MS = TimeUnit.MINUTES.toMillis(5);
    public static boolean enabled(Context c) { return c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).getBoolean("background", false); }
    public static void enabled(Context c, boolean value) {
        c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).edit().putBoolean("background", value).apply();
        configure(c, value);
    }
    public static void configure(Context c, boolean enabled) {
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        if (!enabled) { jobs.cancel(JOB_ID); return; }
        JobInfo existing = jobs.getPendingJob(JOB_ID);
        if (existing != null && existing.isPeriodic() && existing.getIntervalMillis() == INTERVAL_MS && existing.getFlexMillis() == FLEX_MS
                && existing.isPersisted() && existing.getNetworkType() == JobInfo.NETWORK_TYPE_ANY
                && existing.getService().equals(new ComponentName(c, SyncJobService.class))) return;
        // Replace an older cadence under the same ID, without resetting matching
        // jobs on every focus or successful read (which would postpone them).
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(c, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(INTERVAL_MS, FLEX_MS)
                .setPersisted(true).build();
        if (jobs.schedule(info) != JobScheduler.RESULT_SUCCESS) {
            c.getSharedPreferences("notification_options", Context.MODE_PRIVATE).edit().putBoolean("background", false).apply();
            throw new IllegalStateException("Android did not accept the background job");
        }
    }
}
