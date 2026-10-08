package pl.librushome.android;
import android.app.*;
import android.content.*;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real notification action through the receiver, on a blank emulator only. */
public class ReminderConvenienceTest {
    @Test public void notificationActionSnoozesWithoutOpeningActivityAndOnlyOnce() throws Exception {
        assertTrue("Use a blank emulator",Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String raw=new SecureStore(c).read();
        assertTrue("Use an emulator without remembered credentials",raw.isEmpty()||new JSONObject(raw).isNull("credentials"));
        ReminderStore store=new ReminderStore(c);long now=System.currentTimeMillis();
        JSONObject item=store.save(null,"homework","synthetic-source","synthetic-profile",true,"PRIVATE_TITLE","PRIVATE_NOTE",now+1000,false,now,"2026-10-09");
        String id=item.getString("id");
        try {
            JSONObject fired=store.claim(id,now+1000);assertNotNull(fired);
            assertEquals("sent",NotificationHub.reminder(c,fired));
            Notification notification=null;long deadline=System.currentTimeMillis()+3000;
            while(notification==null&&System.currentTimeMillis()<deadline){
                for(android.service.notification.StatusBarNotification n:NotificationHub.manager(c).getActiveNotifications())if(id.equals(n.getTag()))notification=n.getNotification();
                if(notification==null)Thread.sleep(30);
            }
            assertNotNull(notification);assertEquals(1,notification.actions.length);
            assertEquals("Przypomnij za 30 minut",notification.actions[0].title.toString());
            assertFalse(notification.extras.toString().contains("PRIVATE_NOTE"));
            if(Build.VERSION.SDK_INT>=31)assertTrue(notification.actions[0].actionIntent.isImmutable());
            long before=System.currentTimeMillis();notification.actions[0].actionIntent.send();
            deadline=System.currentTimeMillis()+5000;JSONObject later=store.get(id);
            while(!later.optString("status").equals("pending")&&System.currentTimeMillis()<deadline){Thread.sleep(30);later=store.get(id);}
            assertEquals("pending",later.getString("status"));assertTrue(later.getLong("due")>=before+1800000);
            assertTrue(later.getLong("due")<=System.currentTimeMillis()+1800000);
            assertEquals("2026-10-09",later.getString("source_when"));assertFalse(later.getBoolean("show_text"));
            long due=later.getLong("due");notification.actions[0].actionIntent.send();Thread.sleep(200);
            assertEquals(due,store.get(id).getLong("due"));
            for(android.service.notification.StatusBarNotification n:NotificationHub.manager(c).getActiveNotifications())assertFalse(id.equals(n.getTag()));
        } finally {store.delete(id);ReminderAlarms.cancel(c,id);}
    }
}
