package pl.librushome.android;

import android.app.job.*;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.TimeUnit;

/** Actual Android scheduler: cadence migration, persistence, network and opt-out. */
public class BackgroundSyncTest {
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void assertNoSchoolAccount(Context c) {
        try {
            String raw=new SecureStore(c).read();
            if(!raw.isEmpty()){
                org.json.JSONObject saved=new org.json.JSONObject(raw);
                assertTrue("Use an emulator without a remembered school account",saved.isNull("credentials")&&saved.optString("profile").isEmpty());
            }
        } catch(Exception e){throw new AssertionError("Cannot verify isolated test state");}
    }
    @Test public void migratesOldCadenceToOnePersistedFifteenMinuteJob() {
        Context c=context();assertNoSchoolAccount(c);JobScheduler jobs=c.getSystemService(JobScheduler.class);
        try {
            BackgroundSync.intervalMinutes(c,15);
            BackgroundSync.enabled(c,false);
            JobInfo old=new JobInfo.Builder(BackgroundSync.JOB_ID,new ComponentName(c,SyncJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(TimeUnit.MINUTES.toMillis(30),TimeUnit.MINUTES.toMillis(5)).setPersisted(true).build();
            assertEquals(JobScheduler.RESULT_SUCCESS,jobs.schedule(old));
            assertEquals(TimeUnit.MINUTES.toMillis(30),jobs.getPendingJob(BackgroundSync.JOB_ID).getIntervalMillis());
            BackgroundSync.enabled(c,true);
            JobInfo updated=jobs.getPendingJob(BackgroundSync.JOB_ID);
            assertNotNull(updated);assertTrue(BackgroundSync.enabled(c));assertTrue(updated.isPeriodic());assertTrue(updated.isPersisted());
            assertEquals(TimeUnit.MINUTES.toMillis(15),updated.getIntervalMillis());
            assertEquals(TimeUnit.MINUTES.toMillis(5),updated.getFlexMillis());
            assertEquals(JobInfo.NETWORK_TYPE_ANY,updated.getNetworkType());assertEquals(new ComponentName(c,SyncJobService.class),updated.getService());
            BackgroundSync.configure(c,true);
            long matching=jobs.getAllPendingJobs().stream().filter(job->job.getId()==BackgroundSync.JOB_ID).count();
            assertEquals(1,matching);assertEquals(updated.getIntervalMillis(),jobs.getPendingJob(BackgroundSync.JOB_ID).getIntervalMillis());
        } finally {BackgroundSync.enabled(c,false);}
    }
    @Test public void selectedIntervalsUpdateTheSameJobAndDisabledSettingsNeverScheduleIt() {
        Context c=context();assertNoSchoolAccount(c);JobScheduler jobs=c.getSystemService(JobScheduler.class);
        int original=BackgroundSync.intervalMinutes(c);
        try {
            BackgroundSync.enabled(c,false);BackgroundSync.intervalMinutes(c,30);assertNull(jobs.getPendingJob(BackgroundSync.JOB_ID));
            BackgroundSync.enabled(c,true);
            for(int minutes:new int[]{30,60,15}) {
                BackgroundSync.intervalMinutes(c,minutes);
                assertEquals(minutes,BackgroundSync.intervalMinutes(c));
                JobInfo job=jobs.getPendingJob(BackgroundSync.JOB_ID);assertNotNull(job);
                assertEquals(TimeUnit.MINUTES.toMillis(minutes),job.getIntervalMillis());assertTrue(job.isPersisted());
                assertEquals(1,jobs.getAllPendingJobs().stream().filter(value->value.getId()==BackgroundSync.JOB_ID).count());
            }
            try {BackgroundSync.intervalMinutes(c,5);fail("Invalid interval accepted");}catch(IllegalArgumentException expected){}
            assertEquals(15,BackgroundSync.intervalMinutes(c));
        } finally {BackgroundSync.enabled(c,false);BackgroundSync.intervalMinutes(c,original);}
    }
    @Test public void optingOutCancelsJobAndConfigurationDoesNotEnableItAgain() {
        Context c=context();assertNoSchoolAccount(c);JobScheduler jobs=c.getSystemService(JobScheduler.class);
        try {
            BackgroundSync.enabled(c,true);assertNotNull(jobs.getPendingJob(BackgroundSync.JOB_ID));
            BackgroundSync.enabled(c,false);BackgroundSync.configure(c,BackgroundSync.enabled(c));
            assertFalse(BackgroundSync.enabled(c));assertNull(jobs.getPendingJob(BackgroundSync.JOB_ID));
        } finally {BackgroundSync.enabled(c,false);}
    }
}