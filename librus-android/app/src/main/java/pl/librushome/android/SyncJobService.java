package pl.librushome.android;
import android.app.job.*;

public final class SyncJobService extends JobService {
    private JobParameters active;
    @Override public boolean onStartJob(JobParameters params) {
        active = params;
        MobileRepository.get(this).backgroundRefresh(() -> {
            if (active == params) { active = null; jobFinished(params, false); }
            // JobService queues jobFinished on its main handler. Cancel after
            // that acknowledgment, otherwise Android may requeue the periodic
            // job after a cancellation performed during account recovery.
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (!BackgroundSync.enabled(this)) BackgroundSync.configure(this, false);
            });
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { if (active == params) active = null; return false; }
}
