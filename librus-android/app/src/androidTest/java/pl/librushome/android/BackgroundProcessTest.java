package pl.librushome.android;

import android.content.*;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

/** Emulator-only phases: start the real JobService with no Activity and no account. */
public class BackgroundProcessTest {
    @Test public void schedulerStartsClosedApplicationAndSafelyStopsWithoutRememberedAccount()throws Exception {
        String phase=InstrumentationRegistry.getArguments().getString("background_phase","");
        Assume.assumeTrue(phase.equals("seed")||phase.equals("verify"));
        assertTrue("Only on a blank emulator",Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        // A blank state prevents a scheduled job from ever authenticating to Librus.
        String raw=new SecureStore(c).read();
        if(!raw.isEmpty()){
            org.json.JSONObject saved=new org.json.JSONObject(raw);
            assertTrue("Use an emulator without a school account",saved.isNull("credentials")&&saved.optString("profile").isEmpty());
        }
        if(phase.equals("seed")){
            BackgroundSync.nightEnabled(c,false);BackgroundSync.enabled(c,true);
            // Instrumentation terminates the target process immediately; flush
            // this fixture's setting before testing a genuine cold launch.
            assertTrue(c.getSharedPreferences("notification_options",Context.MODE_PRIVATE).edit().putBoolean("background",true).putBoolean("night_pause",false).commit());
            android.app.job.JobInfo job=c.getSystemService(android.app.job.JobScheduler.class).getPendingJob(BackgroundSync.JOB_ID);
            assertNotNull(job);assertEquals(900000,job.getIntervalMillis());return;
        }
        try {
            assertFalse("Run the job after the seed instrumentation has ended",BackgroundSync.enabled(c));
            assertNull(c.getSystemService(android.app.job.JobScheduler.class).getPendingJob(BackgroundSync.JOB_ID));
            assertEquals("Background execution must not create account data",raw,new SecureStore(c).read());
        } finally {BackgroundSync.enabled(c,false);BackgroundSync.nightEnabled(c,true);}
    }
}