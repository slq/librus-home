package pl.librushome.android;
import android.app.job.*;
import android.os.*;
/** Durable local-provider export after reminder edits; independent of Librus network/night pause. */
public final class CalendarExportJobService extends JobService {
    private JobParameters active;
    @Override public boolean onStartJob(JobParameters params){
        active=params;
        GoogleCalendarSync.execute(()->{
            GoogleCalendarSync.syncStored(getApplicationContext());
            new Handler(Looper.getMainLooper()).post(()->{if(active==params){active=null;jobFinished(params,false);}});
        });return true;
    }
    @Override public boolean onStopJob(JobParameters params){if(active==params)active=null;return GoogleCalendarSync.enabled(this)&&GoogleCalendarSync.permitted(this);}
}