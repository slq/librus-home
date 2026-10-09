package pl.librushome.android;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.*;
import org.json.*;
import org.junit.Test;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Import resets authentication; only a successful explicitly remembered login restarts school jobs. */
public class BackupRepositoryTest {
    @Test public void backgroundReadResumesAfterRememberedLoginToImportedAccount()throws Exception {
        BackupTestSupport.blank();var c=BackupTestSupport.context();JSONObject before=BackupStore.before(c);MobileRepository repo=MobileRepository.get(c);
        PyObject service=null,oldFactory=null;
        try {
            BackupDocument doc=BackupTestSupport.seed();assertTrue("Desired background option missing from copy",doc.root.getJSONObject("settings").getJSONObject("notifications").getBoolean("background"));service=Python.getInstance().getModule("mobile_bridge").callAttr("get_service");oldFactory=service.get("factory");
            Python.getInstance().getModule("builtins").callAttr("setattr",service,"factory",BackupTestSupport.fakeFactory);
            CountDownLatch imported=new CountDownLatch(1);String[] problem={null};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->repo.importBackup(doc,(d,e)->{problem[0]=e;imported.countDown();}));assertTrue(imported.await(15,TimeUnit.SECONDS));assertEquals("",problem[0]);assertFalse(BackgroundSync.enabled(c));assertTrue("Deferred option missing after import",c.getSharedPreferences("notification_options",0).getBoolean("resume_background_after_login",false));
            java.util.concurrent.atomic.AtomicReference<JSONObject> last=new java.util.concurrent.atomic.AtomicReference<>();
            CountDownLatch connected=new CountDownLatch(1);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{repo.observe(new MobileRepository.Listener(){
                public void onState(JSONObject state){last.set(state);if(state.optBoolean("connected")&&!state.optBoolean("busy"))connected.countDown();}public void onMessage(JSONObject content){}public void onHomework(JSONObject content){}
            });repo.connect("synthetic-backup-test","test-only",true);});assertTrue(connected.await(15,TimeUnit.SECONDS));assertTrue("Synthetic login failed",last.get().optString("operation_error").isEmpty());
            assertTrue("Background sync did not resume: deferred="+c.getSharedPreferences("notification_options",0).getBoolean("resume_background_after_login",false)+", remembered="+last.get().optBoolean("remembered")+", operationFailed="+!last.get().optString("operation_error").isEmpty()+", storageFailed="+!last.get().optString("storage_error").isEmpty(),BackgroundSync.enabled(c));assertEquals(60,BackgroundSync.intervalMinutes(c));assertFalse("Deferred flag was not cleared",c.getSharedPreferences("notification_options",0).contains("resume_background_after_login"));assertFalse(GoogleCalendarSync.enabled(c));
            assertEquals("synthetic-backup-test",new JSONObject(new SecureStore(c).read()).getJSONObject("credentials").getString("login"));
            assertTrue(new JSONObject(new SecureStore(c).read()).getJSONObject("announcement_archives").getJSONObject("profiles").has(doc.root.getJSONObject("school").getString("profile")));
        }finally {
            CountDownLatch forgotten=new CountDownLatch(1);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{repo.observe(new MobileRepository.Listener(){public void onState(JSONObject state){if(!state.optBoolean("connected")&&!state.optBoolean("busy"))forgotten.countDown();}public void onMessage(JSONObject content){}public void onHomework(JSONObject content){}});repo.forget();});assertTrue(forgotten.await(15,TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->repo.observe(null));
            if(service!=null&&oldFactory!=null)Python.getInstance().getModule("builtins").callAttr("setattr",service,"factory",oldFactory);
            BackupTestSupport.clean(before);
        }
    }
}
