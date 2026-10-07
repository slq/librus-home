package pl.librushome.android;
import android.content.Context;
import android.os.SystemClock;

/** One cooldown for foreground, focus, manual refresh, background and process restarts. */
public final class SyncPolicy {
    public static final long INTERVAL_MS = 5 * 60 * 1000;
    private static long lastElapsed = -1;
    static long remaining(long now, long last) { return last <= 0 ? 0 : Math.max(0, Math.min(INTERVAL_MS, INTERVAL_MS - (now-last))); }
    public static synchronized long remaining(Context c) {
        if (lastElapsed >= 0) return Math.max(0, INTERVAL_MS-(SystemClock.elapsedRealtime()-lastElapsed));
        android.content.SharedPreferences prefs=c.getSharedPreferences("sync_clock", Context.MODE_PRIVATE);
        long now=System.currentTimeMillis(), last=prefs.getLong("last_attempt",0);
        if(last>now) { prefs.edit().putLong("last_attempt",now).apply();lastElapsed=SystemClock.elapsedRealtime();return INTERVAL_MS; }
        return remaining(now,last);
    }
    public static synchronized boolean begin(Context c) {
        if (remaining(c)>0) return false;
        record(c); return true;
    }
    static synchronized void record(Context c) {
        lastElapsed=SystemClock.elapsedRealtime();
        c.getSharedPreferences("sync_clock", Context.MODE_PRIVATE).edit().putLong("last_attempt",System.currentTimeMillis()).apply();
    }
}
