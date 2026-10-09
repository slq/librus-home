package pl.librushome.android;
import android.app.job.*;

public final class UpdateJobService extends JobService {
    private JobParameters active;
    @Override public boolean onStartJob(JobParameters p){active=p;UpdateManager.get(this).check(false,()->{if(active==p){active=null;jobFinished(p,false);}});return true;}
    @Override public boolean onStopJob(JobParameters p){if(active==p)active=null;return false;}
}
