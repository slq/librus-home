package pl.librushome.android;
import android.content.Context;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import org.json.JSONObject;

/** Two explicit emulator-only phases; target process ends between phases. */
public class AlarmProcessTest {
    @Test public void persistedAlarmSurvivesProcessExitAndOpensItsReminder() throws Exception {
        String phase=InstrumentationRegistry.getArguments().getString("alarm_phase","");
        Assume.assumeTrue(phase.equals("seed")||phase.equals("verify")||phase.equals("verify-delivery"));
        assertTrue("Use a blank emulator, never a phone",Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences own=target.getSharedPreferences("alarm_process_fixture",Context.MODE_PRIVATE);
        ReminderStore store=new ReminderStore(target);
        if(phase.equals("seed")) {
            org.json.JSONArray prior=store.list();
            for(int i=0;i<prior.length();i++) {
                JSONObject previous=prior.getJSONObject(i);
                if(previous.optBoolean("demo") && previous.optString("source_id").equals("synthetic-process-source") && previous.optString("note").equals("SYNTHETIC_AFTER_PROCESS_EXIT")) {
                    store.delete(previous.getString("id"));ReminderAlarms.cancel(target,previous.getString("id"));
                }
            }
            long now=System.currentTimeMillis();
            JSONObject item=store.save(null,"messages","synthetic-process-source","demo",true,"Synthetic source","SYNTHETIC_AFTER_PROCESS_EXIT",now+25000,false,now);
            assertTrue(own.edit().putString("id",item.getString("id")).commit());
            ReminderAlarms.schedule(target,item);
            assertNotNull(store.get(item.getString("id")));return;
        }
        String id=own.getString("id","");assertFalse("Run seed first",id.isEmpty());
        try {
            JSONObject delivered=store.get(id);assertNotNull(delivered);
            assertEquals("fired",delivered.getString("status"));assertEquals("sent",delivered.getString("delivery"));
            if(phase.equals("verify-delivery"))return; // Verify while screen remains locked; no Activity is started.
            // Verify the persisted receiver result above, then recreate its same payload
            // to test the tap route even if the original notification was dismissed.
            assertEquals("sent",NotificationHub.reminder(target,delivered));
            android.service.notification.StatusBarNotification found=null;
            long publishedDeadline=System.currentTimeMillis()+3000;
            while(found==null && System.currentTimeMillis()<publishedDeadline) {
                for(android.service.notification.StatusBarNotification n:NotificationHub.manager(target).getActiveNotifications())if(id.equals(n.getTag()))found=n;
                if(found==null)Thread.sleep(50);
            }
            assertNotNull("Notification not published",found);
            assertFalse(found.getNotification().extras.toString().contains("SYNTHETIC_AFTER_PROCESS_EXIT"));
            found.getNotification().contentIntent.send();
            long deadline=System.currentTimeMillis()+15000;boolean opened=false;
            while(System.currentTimeMillis()<deadline&&!opened) {
                android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if(root!=null)opened=!root.findAccessibilityNodeInfosByText("SYNTHETIC_AFTER_PROCESS_EXIT").isEmpty();
                if(!opened)Thread.sleep(100);
            }
            assertTrue("Notification did not open its saved reminder",opened);
        } finally { store.delete(id);ReminderAlarms.cancel(target,id);own.edit().clear().commit(); }
    }
}
