package pl.librushome.android;

import android.app.Notification;
import android.content.Context;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Separate instrumentation run: real repository/Keystore/notifications, fake school responses. */
public class BackgroundNotificationTest {
    private final String fixture="""
        import mobile_bridge as m
        from datetime import date
        _original_service = m._service
        class SyntheticConnector:
            revision = 0
            fail_attendance = False
            expire_next_fetch = False
            reject_login = False
            instances = 0
            def __init__(self, *args): SyntheticConnector.instances += 1
            def connect(self):
                if SyntheticConnector.reject_login:
                    raise m.ConnectorError('Synthetic rejected login', requires_login=True)
            def close(self): pass
            def fetch(self, kind):
                if kind == 'grades' and SyntheticConnector.expire_next_fetch:
                    SyntheticConnector.expire_next_fetch = False
                    raise m.ConnectorError('Synthetic expired session', requires_login=True)
                if kind == 'attendance' and SyntheticConnector.fail_attendance:
                    raise m.ConnectorError('Synthetic section failure')
                return [{'id':kind+':synthetic-'+str(i),'kind':kind,'title':'PRIVATE_TITLE',
                         'subtitle':'PRIVATE_SUBTITLE','details':'PRIVATE_DESCRIPTION',
                         'when':date.today().isoformat(),'unread':False,'url':''}
                        for i in range(SyntheticConnector.revision+1)]
        m._service = m.MobileService(SyntheticConnector)
        """;
    private void allowNext(Context c)throws Exception {
        java.lang.reflect.Field elapsed=SyncPolicy.class.getDeclaredField("lastElapsed");elapsed.setAccessible(true);elapsed.setLong(null,-1);
        assertTrue(c.getSharedPreferences("sync_clock",Context.MODE_PRIVATE).edit().clear().commit());
    }
    private void sync(Context c,MobileRepository repo)throws Exception {
        allowNext(c);CountDownLatch completed=new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->repo.backgroundRefresh(completed::countDown));
        assertTrue("Background sync timed out",completed.await(20,TimeUnit.SECONDS));
    }
    private Notification notification(Context c)throws Exception {
        for(android.service.notification.StatusBarNotification n:NotificationHub.manager(c).getActiveNotifications())if("changes".equals(n.getTag()))return n.getNotification();
        return null;
    }
    private void quiet(Context c)throws Exception {Thread.sleep(150);assertNull(notification(c));}
    @Test public void closedApplicationNotifiesAllSectionsAndForegroundAndRepeatedSyncStayQuiet()throws Exception {
        assertTrue("Use a blank emulator",Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();SecureStore store=new SecureStore(c);
        String original=store.read();assertTrue("Never run with a school account",original.isEmpty()||new JSONObject(original).isNull("credentials"));
        if(!Python.isStarted())Python.start(new AndroidPlatform(c));Python py=Python.getInstance();
        PyObject scope=py.getModule("builtins").callAttr("dict");
        py.getModule("builtins").callAttr("exec",fixture,scope);
        PyObject service=py.getModule("mobile_bridge").callAttr("get_service");
        service.callAttr("connect","synthetic-notification-profile","test-only-password",true,false);
        store.write(service.callAttr("export_json").toString()); // Persisted fixture before repository's cold initialization.
        MobileRepository repo=MobileRepository.get(c);
        boolean data=NotificationHub.dataEnabled(c),night=BackgroundSync.nightEnabled(c);
        BackgroundSync.nightEnabled(c,false);
        try {
            NotificationHub.manager(c).cancel("changes",1);NotificationHub.dataEnabled(c,true);BackgroundSync.enabled(c,true);
            sync(c,repo);quiet(c); // First baseline remains quiet.
            py.getModule("builtins").callAttr("exec","SyntheticConnector.revision = 1",scope);
            sync(c,repo);
            long deadline=System.currentTimeMillis()+3000;Notification value=notification(c);
            while(value==null&&System.currentTimeMillis()<deadline){Thread.sleep(30);value=notification(c);}
            assertNotNull("No background notification",value);
            String text=value.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            for(String label:new String[]{"Oceny: 1","Wiadomości: 1","Ogłoszenia: 1","Terminarz: 1","Frekwencja: 1","Plan lekcji: 1","Zadania domowe: 1"})assertTrue(label,text.contains(label));
            assertFalse(value.extras.toString().contains("PRIVATE_"));assertNotNull(value.contentIntent);
            NotificationHub.manager(c).cancel("changes",1);sync(c,repo);quiet(c); // Same data, no repeated banner.
            repo.focused(true);py.getModule("builtins").callAttr("exec","SyntheticConnector.revision = 2",scope);
            sync(c,repo);quiet(c); // Foreground reads are visible in app, not a system alert.
            repo.focused(false);sync(c,repo);quiet(c); // Returning to background does not replay foreground changes.
            py.getModule("builtins").callAttr("exec","SyntheticConnector.revision = 3; SyntheticConnector.fail_attendance = True",scope);
            sync(c,repo);
            deadline=System.currentTimeMillis()+3000;value=notification(c);
            while(value==null&&System.currentTimeMillis()<deadline){Thread.sleep(30);value=notification(c);}
            assertNotNull(value);text=value.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            assertTrue(text.contains("Oceny: 1"));assertFalse(text.contains("Frekwencja:"));
            NotificationHub.manager(c).cancel("changes",1);
            py.getModule("builtins").callAttr("exec","SyntheticConnector.expire_next_fetch = True",scope);
            sync(c,repo);quiet(c);
            JSONObject expired=new JSONObject(service.callAttr("state_json").toString());
            assertTrue(expired.optBoolean("remembered"));assertTrue(expired.optBoolean("needs_login"));
            assertFalse(expired.optBoolean("auto_login_blocked"));
            assertTrue("Old-session expiry must keep the background job",BackgroundSync.enabled(c));
            int created=py.getModule("builtins").callAttr("eval","SyntheticConnector.instances",scope).toInt();
            CountDownLatch deferred=new CountDownLatch(1);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->repo.backgroundRefresh(deferred::countDown));
            assertTrue(deferred.await(5,TimeUnit.SECONDS));
            assertEquals("Recovery must obey the shared cooldown",created,py.getModule("builtins").callAttr("eval","SyntheticConnector.instances",scope).toInt());
            sync(c,repo);quiet(c);
            assertTrue(new JSONObject(service.callAttr("state_json").toString()).optBoolean("connected"));
            assertEquals(created+1,py.getModule("builtins").callAttr("eval","SyntheticConnector.instances",scope).toInt());
            assertTrue(BackgroundSync.enabled(c));
            py.getModule("builtins").callAttr("exec","SyntheticConnector.expire_next_fetch = True; SyntheticConnector.reject_login = True",scope);
            sync(c,repo);assertTrue(BackgroundSync.enabled(c));
            sync(c,repo);
            assertTrue(new JSONObject(service.callAttr("state_json").toString()).optBoolean("auto_login_blocked"));
            assertFalse("Rejected saved login must stop automatic attempts",BackgroundSync.enabled(c));
        } finally {
            BackgroundSync.enabled(c,false);BackgroundSync.nightEnabled(c,night);NotificationHub.dataEnabled(c,data);NotificationHub.manager(c).cancel("changes",1);
            if(original.isEmpty())store.clear();else store.write(original);
            py.getModule("builtins").callAttr("exec","m._service = _original_service",scope);
            allowNext(c);
        }
    }
}
