package pl.librushome.android;
import android.content.*;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import org.json.*;
import static org.junit.Assert.*;

final class BackupTestSupport {
    static PyObject fakeFactory;
    static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    static void blank()throws Exception {
        assertTrue(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));String raw=new SecureStore(context()).read();
        assertTrue("Test requires an emulator with no school account",raw.isEmpty()||(new JSONObject(raw).isNull("credentials")&&new JSONObject(raw).optString("profile").isEmpty()));
        UpdateManager.get(context()).automatic(false);
    }
    static String school()throws Exception {
        if(!Python.isStarted())Python.start(new AndroidPlatform(context()));PyObject scope=Python.getInstance().getModule("builtins").callAttr("dict");
        Python.getInstance().getModule("builtins").callAttr("exec","""
            from mobile_bridge import MobileService
            from librus_shared.data import demo_sections
            class Fake:
                def __init__(self,*args): pass
                def connect(self): pass
                def close(self): pass
                def fetch(self,kind):
                    rows=demo_sections(include_homework=True)[kind]
                    for row in rows: row['id']='synthetic-backup:'+row['id']
                    return rows
            svc=MobileService(Fake)
            svc.connect('synthetic-backup-test','SYNTHETIC_PASSWORD_NEVER_EXPORT',True)
            private=svc.export_json()
            portable=svc.portable_json()
            """,scope);
        fakeFactory=scope.callAttr("get","Fake");
        return scope.callAttr("get","private").toString();
    }
    static String portable(String school)throws Exception{return Python.getInstance().getModule("mobile_backup").callAttr("export",school).toString();}
    static BackupDocument seed()throws Exception {
        Context c=context();String raw=school();new SecureStore(c).write(raw);String profile=new JSONObject(raw).getString("profile");
        long now=System.currentTimeMillis();new ReminderStore(c).save(null,"announcements","synthetic:source",profile,false,"SYNTHETIC_BACKUP_TITLE","SYNTHETIC_BACKUP_NOTE",now+86400000L,false,now);
        new HomeworkStatusStore(c).set(HomeworkStatusStore.key(profile,false,HomeworkCompletionTest.year(),"synthetic-backup:homework"),true,HomeworkStatusStore.epoch());
        c.getSharedPreferences("notification_options",0).edit().putBoolean("data",true).putBoolean("background",true).putInt("background_minutes",60).putBoolean("night_pause",false).commit();
        GoogleCalendarSync.prefs(c).edit().putBoolean("enabled",true).putLong("calendar",123456789).putString("identity","synthetic-calendar-identity").putString("profile",profile).putBoolean("homework",false).putBoolean("alerts",true).commit();
        return BackupStore.capture(c,portable(raw));
    }
    static void clean(JSONObject original)throws Exception {BackupStore.afterSchoolWrite=()->{};BackupStore.journal(context()).write(original.toString());BackupStore.recover(context());}
    static void check(BackupDocument doc)throws Exception {
        Context c=context();JSONObject saved=new JSONObject(new SecureStore(c).read());String profile=doc.root.getJSONObject("school").getString("profile");assertEquals(profile,saved.getString("profile"));assertFalse(saved.has("credentials")&&!saved.isNull("credentials"));
        assertTrue(saved.getJSONObject("announcement_archives").getJSONObject("profiles").getJSONObject(profile).getJSONArray("items").length()>0);
        assertEquals("SYNTHETIC_BACKUP_NOTE",new ReminderStore(c).get(doc.root.getJSONArray("reminders").getJSONObject(0).getString("id")).getString("note"));
        assertTrue(new HomeworkStatusStore(c).completed().contains(HomeworkStatusStore.key(profile,false,HomeworkCompletionTest.year(),"synthetic-backup:homework")));
        assertEquals(60,BackgroundSync.intervalMinutes(c));assertFalse(BackgroundSync.nightEnabled(c));assertTrue(NotificationHub.dataEnabled(c));assertFalse(BackgroundSync.enabled(c));assertTrue(c.getSharedPreferences("notification_options",0).getBoolean("resume_background_after_login",false));
        assertFalse(GoogleCalendarSync.enabled(c));assertEquals(-1,GoogleCalendarSync.prefs(c).getLong("calendar",-1));assertFalse(GoogleCalendarSync.source(c,"homework"));assertTrue(GoogleCalendarSync.alerts(c));assertFalse(UpdateManager.get(c).automatic());
        assertEquals(CalendarExportTimes.key(profile,"announcements","synthetic:source"),CalendarExportTimes.key(saved.getString("profile"),"announcements","synthetic:source"));
    }
    private BackupTestSupport(){}
}
