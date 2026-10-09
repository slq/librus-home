package pl.librushome.android;
import android.content.Context;
import android.app.job.JobScheduler;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class BackupDataTest {
    @Test public void portableCopyRestoresArchivesRemindersTasksAndOptionsWithoutAuthentication()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();JSONObject before=BackupStore.before(c);
        try {BackupDocument doc=BackupTestSupport.seed();String json=new String(doc.bytes(),StandardCharsets.UTF_8);assertFalse(json.contains("SYNTHETIC_PASSWORD_NEVER_EXPORT"));assertFalse(json.contains("credentials"));assertFalse(json.contains("synthetic-calendar-identity"));
            BackupDocument parsed=BackupDocument.read(new ByteArrayInputStream(doc.bytes()));BackupStore.apply(c,parsed);BackupTestSupport.check(parsed);
            assertNull(c.getSystemService(JobScheduler.class).getPendingJob(BackgroundSync.JOB_ID));assertTrue(BackupStore.journal(c).read().isEmpty());
        }finally{BackupTestSupport.clean(before);}
    }
    @Test public void expiredAndPreviouslyFiredRemindersDoNotNotifyAgain()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();JSONObject before=BackupStore.before(c);
        try{BackupDocument doc=BackupTestSupport.seed();JSONObject row=doc.root.getJSONArray("reminders").getJSONObject(0);row.put("due",System.currentTimeMillis()-1000);String id=row.getString("id");BackupStore.apply(c,new BackupDocument(doc.root));
            JSONObject restored=new ReminderStore(c).get(id);assertEquals("fired",restored.getString("status"));assertEquals("import_expired",restored.getString("delivery"));assertNull(new ReminderStore(c).claim(id,System.currentTimeMillis()));
            for(var notification:NotificationHub.manager(c).getActiveNotifications())assertNotEquals(id,notification.getTag());
            BackupStore.apply(c,new BackupDocument(doc.root));assertEquals(1,new ReminderStore(c).list().length());assertNull(new ReminderStore(c).claim(id,System.currentTimeMillis()));
        }finally{BackupTestSupport.clean(before);}
    }
    @Test public void writeFailureRollsBackAllStoresIncludingPrivateLoginAndTypedOptions()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();JSONObject before=BackupStore.before(c);
        try{BackupDocument doc=BackupTestSupport.seed();String privateBefore=new SecureStore(c).read();String reminderBefore=new ReminderStore(c).list().toString();var options=c.getSharedPreferences("notification_options",0).getAll();
            BackupStore.afterSchoolWrite=()->{throw new IllegalStateException("Synthetic disk failure");};boolean refused=false;try{BackupStore.apply(c,doc);}catch(Exception expected){refused=true;}assertTrue(refused);
            assertEquals(privateBefore,new SecureStore(c).read());assertEquals(reminderBefore,new ReminderStore(c).list().toString());assertEquals(options,c.getSharedPreferences("notification_options",0).getAll());assertTrue(BackupStore.journal(c).read().isEmpty());
        }finally{BackupTestSupport.clean(before);}
    }
    @Test public void journalRecoversInterruptedImportBeforeStartupRead()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();JSONObject before=BackupStore.before(c);
        try{BackupDocument doc=BackupTestSupport.seed();JSONObject original=BackupStore.before(c);BackupStore.journal(c).write(original.toString());new SecureStore(c).write(doc.school());new ReminderStore(c).replace(new JSONArray());new HomeworkStatusStore(c).replace(new JSONObject());
            BackupStore.recover(c);assertEquals(original.getString("school"),new SecureStore(c).read());assertEquals(original.getJSONArray("reminders").toString(),new ReminderStore(c).list().toString());assertFalse(new HomeworkStatusStore(c).completed().isEmpty());assertTrue(BackupStore.journal(c).read().isEmpty());
        }finally{BackupTestSupport.clean(before);}
    }
    @Test public void malformedFilesUnknownVersionsForeignSecretsAndUnboundedNestingAreRejected()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();JSONObject before=BackupStore.before(c);
        try{BackupDocument doc=BackupTestSupport.seed();String original=new SecureStore(c).read();
            for(String raw:new String[]{"{}","[]",doc.root.toString()+" trailing",doc.root.toString().replace("\"schema\":1","\"schema\":2"),"[".repeat(40)+"]".repeat(40)}){boolean refused=false;try{BackupDocument.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception expected){refused=true;}assertTrue(refused);assertEquals(original,new SecureStore(c).read());}
            JSONObject foreign=new JSONObject(doc.root.toString());foreign.put("password","SYNTHETIC_SECRET");try{new BackupDocument(foreign);fail("Foreign secret accepted");}catch(IllegalArgumentException expected){}
            JSONObject interval=new JSONObject(doc.root.toString());interval.getJSONObject("settings").getJSONObject("notifications").put("background_minutes",4294967311L);try{new BackupDocument(interval);fail("Overflow accepted");}catch(IllegalArgumentException expected){}
        }finally{BackupTestSupport.clean(before);}
    }
}
