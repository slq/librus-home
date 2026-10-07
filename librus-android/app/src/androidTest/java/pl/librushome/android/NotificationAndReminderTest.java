package pl.librushome.android;
import android.app.Notification;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import org.json.*;
import java.nio.file.Files;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic storage, isolated keys, and generic test notifications only. */
public class NotificationAndReminderTest {
    private Context context;
    private ReminderStore store;
    @Before public void setup() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        store=new ReminderStore(context,"test-only-reminders.aes","LibrusApp.test-only.reminders");store.clear();
    }
    @After public void cleanup() throws Exception { store.clear();NotificationHub.manager(context).cancel("test-reminder-privacy",1); }
    private JSONObject create() throws Exception {
        return store.save(null,"messages","synthetic-source","synthetic-profile",true,"SYNTHETIC_SCHOOL_TITLE","SYNTHETIC_PRIVATE_NOTE",2000,false,1000);
    }
    @Test public void encryptedRoundtripRejectsInvalidEditWithoutChangingThePreviousReminder() throws Exception {
        JSONObject saved=create();
        File file=new File(context.getNoBackupFilesDir(),"test-only-reminders.aes");byte[] before=Files.readAllBytes(file.toPath());
        assertFalse(new String(before,java.nio.charset.StandardCharsets.ISO_8859_1).contains("SYNTHETIC_PRIVATE_NOTE"));
        try { store.save(saved.getString("id"),"messages","synthetic-source","synthetic-profile",true,"title","",3000,false,1000);fail("Empty note accepted"); }catch(IllegalArgumentException expected) { }
        assertArrayEquals(before,Files.readAllBytes(file.toPath()));
        assertEquals("SYNTHETIC_PRIVATE_NOTE",new ReminderStore(context,"test-only-reminders.aes","LibrusApp.test-only.reminders").get(saved.getString("id")).getString("note"));
        try { store.save(null,"messages","synthetic-source","synthetic-profile",true,"title","note",1000,false,1000);fail("Past due accepted"); }catch(IllegalArgumentException expected) { }
        assertEquals(1,store.list().length());
    }
    @Test public void concurrentDeliveryClaimsOnlyOnceAndSurvivesRestart() throws Exception {
        String id=create().getString("id");AtomicInteger claimed=new AtomicInteger();CountDownLatch start=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Void> delivery=()->{start.await();if(store.claim(id,2000)!=null)claimed.incrementAndGet();return null;};
            Future<Void> a=pool.submit(delivery),b=pool.submit(delivery);start.countDown();a.get();b.get();
            assertEquals(1,claimed.get());
            ReminderStore reopened=new ReminderStore(context,"test-only-reminders.aes","LibrusApp.test-only.reminders");
            assertNull(reopened.claim(id,3000));reopened.delivered(id,2000,"blocked");
            assertEquals("blocked",reopened.get(id).getString("delivery"));
            JSONObject edited=reopened.save(id,"messages","synthetic-source","synthetic-profile",true,"title","new note",5000,false,3000);
            assertEquals("pending",edited.getString("status"));assertFalse(edited.has("fired_at"));assertNotNull(reopened.claim(id,5000));
        } finally { pool.shutdownNow(); }
    }
    @Test public void defaultReminderNotificationDoesNotDiscloseSourceOrText() throws Exception {
        JSONObject item=create().put("id","test-reminder-privacy");
        assertEquals("sent",NotificationHub.reminder(context,item));
        android.service.notification.StatusBarNotification found=null;
        for(android.service.notification.StatusBarNotification n:NotificationHub.manager(context).getActiveNotifications())if("test-reminder-privacy".equals(n.getTag()))found=n;
        assertNotNull(found);Notification notification=found.getNotification();
        assertFalse(notification.extras.toString().contains("SYNTHETIC_PRIVATE_NOTE"));assertFalse(notification.extras.toString().contains("SYNTHETIC_SCHOOL_TITLE"));
        assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility);assertNotNull(notification.contentIntent);
        assertFalse(notification.publicVersion.extras.toString().contains("SYNTHETIC_PRIVATE_NOTE"));
        NotificationHub.reminder(context,item.put("show_text",true));
        boolean updated=false;
        long deadline=System.currentTimeMillis()+3000;
        while(System.currentTimeMillis()<deadline && !updated) {
            for(android.service.notification.StatusBarNotification n:NotificationHub.manager(context).getActiveNotifications())
                if("test-reminder-privacy".equals(n.getTag()) && "SYNTHETIC_PRIVATE_NOTE".contentEquals(n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT))) updated=true;
            if(!updated)Thread.sleep(50);
        }
        assertTrue("Android did not update the notification",updated);
    }
    @Test public void backgroundRefreshCannotContactSchoolFromDemo() throws Exception {
        android.content.Intent intent=new android.content.Intent(context,MainActivity.class).putExtra("demo",true);
        try(androidx.test.core.app.ActivityScenario<MainActivity> scenario=androidx.test.core.app.ActivityScenario.launch(intent)) {
            long deadline=System.currentTimeMillis()+30000;
            while(System.currentTimeMillis()<deadline) {
                if(com.chaquo.python.Python.isStarted()) {
                    String state=com.chaquo.python.Python.getInstance().getModule("mobile_bridge").callAttr("get_service").callAttr("state_json").toString();
                    if(new JSONObject(state).optString("mode").equals("demo"))break;
                }
                Thread.sleep(50);
            }
            BackgroundSync.enabled(context,true);
            assertNotNull(context.getSystemService(android.app.job.JobScheduler.class).getPendingJob(BackgroundSync.JOB_ID));
            CountDownLatch finished=new CountDownLatch(1);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->MobileRepository.get(context).backgroundRefresh(finished::countDown));
            assertTrue("Background request did not finish",finished.await(10,TimeUnit.SECONDS));
            assertFalse(BackgroundSync.enabled(context));
            assertNull(context.getSystemService(android.app.job.JobScheduler.class).getPendingJob(BackgroundSync.JOB_ID));
        } finally { BackgroundSync.enabled(context,false); }
    }

}
