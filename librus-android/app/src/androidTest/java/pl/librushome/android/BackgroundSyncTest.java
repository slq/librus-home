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
    @Test public void optingOutCancelsJobAndConfigurationDoesNotEnableItAgain() {
        Context c=context();assertNoSchoolAccount(c);JobScheduler jobs=c.getSystemService(JobScheduler.class);
        try {
            BackgroundSync.enabled(c,true);assertNotNull(jobs.getPendingJob(BackgroundSync.JOB_ID));
            BackgroundSync.enabled(c,false);BackgroundSync.configure(c,BackgroundSync.enabled(c));
            assertFalse(BackgroundSync.enabled(c));assertNull(jobs.getPendingJob(BackgroundSync.JOB_ID));
        } finally {BackgroundSync.enabled(c,false);}
    }
}