package pl.librushome.android;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Only public APK fixtures and synthetic metadata; no school content or real releases. */
public class UpdateValidationTest {
    @Test public void metadataRejectsWrongAppInvalidTypesSizesHashesAndVersionUrlMismatch()throws Exception{
        File apk=UpdateTestSupport.asset("update.apk");JSONObject valid=new JSONObject(UpdateTestSupport.release(apk).json());
        for(JSONObject invalid:Arrays.asList(new JSONObject(valid.toString()).put("packageName","evil.app"),new JSONObject(valid.toString()).put("versionCode","19"),new JSONObject(valid.toString()).put("size",UpdatePolicy.MAX_APK_BYTES+1),new JSONObject(valid.toString()).put("sha256","wrong"),new JSONObject(valid.toString()).put("versionName","0.12.2"),new JSONObject(valid.toString()).put("notes","x".repeat(16001)))){boolean rejected=false;try{UpdateRelease.parse(invalid.toString());}catch(JSONException expected){rejected=true;}assertTrue(rejected);}
        assertEquals(valid.getLong("versionCode"),UpdateRelease.parse(valid.toString()).code);
    }
    @Test public void actualReleaseWithMatchingSignerIsAcceptedButUnsignedTamperedAndWrongVersionAreRejected()throws Exception{
        File valid=UpdateTestSupport.asset("update.apk"),unsigned=UpdateTestSupport.asset("unsigned.apk");UpdateRelease r=UpdateTestSupport.release(valid);UpdateApk.verify(UpdateTestSupport.context(),valid,r);
        boolean rejected=false;try{UpdateApk.verify(UpdateTestSupport.context(),unsigned,UpdateTestSupport.release(unsigned));}catch(Exception expected){rejected=true;}assertTrue("Unsigned APK must be rejected",rejected);
        rejected=false;try{UpdateApk.verify(UpdateTestSupport.context(),valid,UpdateTestSupport.release(valid,20,"0.12.1"));}catch(Exception expected){rejected=true;}assertTrue("Wrong version must be rejected",rejected);
        try(FileOutputStream append=new FileOutputStream(valid,true)){append.write(1);}rejected=false;try{UpdateApk.verify(UpdateTestSupport.context(),valid,r);}catch(Exception expected){rejected=true;}assertTrue("Modified APK must be rejected",rejected);
    }
    @Test public void dailyChecksSchedulePersistentlyThrottleAndNeverDownloadAutomatically()throws Exception{
        HomeworkCompletionTest.blank();UpdateManager m=UpdateManager.get(UpdateTestSupport.context());UpdateTestSupport.idle(m);UpdateTransport previous=UpdateManager.transport;int[] calls={0};
        UpdateManager.transport=new UpdateTransport(){public String manifest()throws Exception{calls[0]++;throw new NoRelease();}public void download(UpdateRelease r,File f,java.util.function.IntConsumer p,java.util.function.BooleanSupplier c){throw new AssertionError("Automatic check must not download APK");}};
        try{
            m.prefs().edit().remove("checked").apply();m.automatic(true);android.app.job.JobInfo job=UpdateTestSupport.context().getSystemService(android.app.job.JobScheduler.class).getPendingJob(UpdateManager.JOB_ID);assertNotNull(job);assertTrue(job.isPersisted());assertEquals(UpdatePolicy.CHECK_INTERVAL,job.getIntervalMillis());assertEquals(new android.content.ComponentName(UpdateTestSupport.context(),UpdateJobService.class),job.getService());
            m.check(false,null);UpdateTestSupport.idle(m);assertEquals(1,calls[0]);m.check(false,null);UpdateTestSupport.idle(m);assertEquals(1,calls[0]);m.check(true,null);UpdateTestSupport.idle(m);assertEquals(2,calls[0]);m.automatic(false);assertNull(UpdateTestSupport.context().getSystemService(android.app.job.JobScheduler.class).getPendingJob(UpdateManager.JOB_ID));
        }finally{m.automatic(false);UpdateManager.transport=previous;m.prefs().edit().clear().apply();}
    }
    @Test public void failedDownloadLeavesNoInstallablePartialAndDoesNotChangeSchoolState()throws Exception{
        HomeworkCompletionTest.blank();UpdateManager m=UpdateManager.get(UpdateTestSupport.context());UpdateTestSupport.idle(m);m.automatic(false);
        File apk=UpdateTestSupport.asset("update.apk");UpdateRelease good=UpdateTestSupport.release(apk);String school=new SecureStore(UpdateTestSupport.context()).read();UpdateTransport before=UpdateManager.transport;
        try{
            UpdateManager.transport=UpdateTestSupport.fake(good,apk);m.check(true,null);UpdateTestSupport.idle(m);assertNotNull(m.release);
            File broken=new File(UpdateTestSupport.context().getCacheDir(),"broken.apk");try(FileOutputStream out=new FileOutputStream(broken)){out.write(new byte[]{1,2,3});}
            UpdateManager.transport=UpdateTestSupport.fake(good,broken);m.download();UpdateTestSupport.idle(m);assertFalse(m.ready);assertFalse(new File(UpdateTestSupport.context().getCacheDir(),"app-update.part").exists());assertEquals(school,new SecureStore(UpdateTestSupport.context()).read());
        }finally{m.release=null;m.ready=false;m.apk().delete();m.prefs().edit().clear().apply();UpdateManager.transport=before;}
    }
}
