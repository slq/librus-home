package pl.librushome.android;

import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Explicit two-phase tests on a blank emulator: real reboot or disabled notifications. */
public class ReminderRecoveryTest {
    private static final String SOURCE="synthetic-recovery-only", NOTE="SYNTHETIC_REBOOT_REMINDER";
    @Test public void recoveryKeepsPendingFiredDeletedAndBlockedStates() throws Exception {
        String phase=InstrumentationRegistry.getArguments().getString("recovery_phase","");
        Assume.assumeTrue(phase.equals("seed")||phase.equals("verify")||phase.equals("seed-blocked")||phase.equals("verify-blocked"));
        assertTrue("Only on a blank emulator",Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ReminderStore store=new ReminderStore(c);
        android.content.SharedPreferences fixture=c.getSharedPreferences("recovery_test_fixture",Context.MODE_PRIVATE);
        if(phase.startsWith("seed")) {
            JSONArray before=store.list();
            for(int i=0;i<before.length();i++) {
                JSONObject row=before.getJSONObject(i);
                if(row.optBoolean("demo")&&row.optString("source_id").equals(SOURCE)) {
                    store.delete(row.getString("id"));ReminderAlarms.cancel(c,row.getString("id"));
                }
            }
            boolean blocked=phase.equals("seed-blocked");
            if(blocked)assertFalse("Disable notifications before seed-blocked",NotificationHub.allowed(c,NotificationHub.REMINDERS));
            else assertTrue("Enable notifications before seed",NotificationHub.allowed(c,NotificationHub.REMINDERS));
            long now=System.currentTimeMillis(), due=now+12000;
            JSONObject pending=store.save(null,"schedule",SOURCE,"demo",true,"Synthetic event",NOTE,due,false,now);
            ReminderAlarms.schedule(c,pending);
            JSONObject fired=store.save(null,"messages",SOURCE,"demo",true,"Synthetic past",NOTE+"_FIRED",now+1000,false,now);
            store.claim(fired.getString("id"),now+1000);store.delivered(fired.getString("id"),now+1000,"sent");
            JSONObject deleted=store.save(null,"announcements",SOURCE,"demo",true,"Synthetic canceled",NOTE+"_DELETED",due,false,now);
            ReminderAlarms.schedule(c,deleted);store.delete(deleted.getString("id"));ReminderAlarms.cancel(c,deleted.getString("id"));
            assertTrue(fixture.edit().clear().putString("pending",pending.getString("id"))
                    .putString("fired",fired.getString("id")).putLong("fired_at",now+1000)
                    .putString("deleted",deleted.getString("id")).putLong("due",due)
                    .putInt("boot",Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1)).commit());
            return;
        }
        String pendingId=fixture.getString("pending",""),firedId=fixture.getString("fired",""),deletedId=fixture.getString("deleted","");
        assertFalse("Run seed first",pendingId.isEmpty());
        try {
            boolean blocked=phase.equals("verify-blocked");
            if(!blocked)assertTrue("Reboot the emulator between seed and verify",
                    Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1)>fixture.getInt("boot",Integer.MAX_VALUE));
            JSONObject delivered=store.get(pendingId);assertNotNull(delivered);
            assertEquals("fired",delivered.getString("status"));
            assertEquals(blocked?"blocked":"sent",delivered.getString("delivery"));
            assertTrue(delivered.getLong("fired_at")>=fixture.getLong("due",Long.MAX_VALUE));
            JSONObject unchanged=store.get(firedId);assertNotNull(unchanged);
            assertEquals("fired",unchanged.getString("status"));assertEquals(fixture.getLong("fired_at",0),unchanged.getLong("fired_at"));
            assertNull(store.get(deletedId));
            assertNull(store.claim(pendingId,System.currentTimeMillis()));
            assertNull(store.claim(firedId,System.currentTimeMillis()));
            // Dismiss only this fixture; rehydration must not repost fired/deleted alerts.
            for(String id:new String[]{pendingId,firedId,deletedId})NotificationHub.manager(c).cancel(id,1);
            ReminderAlarms.restore(c);
            for(android.service.notification.StatusBarNotification n:NotificationHub.manager(c).getActiveNotifications()) {
                assertFalse(n.getTag().equals(firedId)||n.getTag().equals(deletedId)||n.getTag().equals(pendingId));
            }
        } finally {
            for(String id:new String[]{pendingId,firedId,deletedId}){store.delete(id);ReminderAlarms.cancel(c,id);}
            fixture.edit().clear().commit();
        }
    }
}
