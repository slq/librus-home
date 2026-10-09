package pl.librushome.android;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import org.json.*;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

/** Seed opens the Android confirmation; host confirms on the BLANK emulator, then Verify runs after upgrade. */
public class UpdateInstallIntegrationTest {
    @Test public void seed()throws Exception{
        HomeworkCompletionTest.blank();UpdateManager m=UpdateManager.get(UpdateTestSupport.context());m.automatic(false);assertTrue(UpdateTestSupport.context().getPackageManager().canRequestPackageInstalls());
        if(!Python.isStarted())Python.start(new AndroidPlatform(UpdateTestSupport.context()));PyObject scope=Python.getInstance().getModule("builtins").callAttr("dict");
        Python.getInstance().getModule("builtins").callAttr("exec","""
            from mobile_bridge import MobileService
            class Fake:
                def __init__(self,*args): pass
                def connect(self): pass
                def close(self): pass
                def fetch(self,kind): return [dict(id='synthetic:upgrade',title='SYNTHETIC_UPGRADE_ARCHIVE',details='SYNTHETIC_PRIVATE_ARCHIVE')] if kind=='announcements' else []
            svc=MobileService(Fake)
            svc.connect('synthetic-update-install','test-only')
            saved=svc.export_json()
            """,scope);
        String saved=scope.callAttr("get","saved").toString(),profile=new JSONObject(saved).getString("profile");new SecureStore(UpdateTestSupport.context()).write(saved);
        long now=System.currentTimeMillis();JSONObject reminder=new ReminderStore(UpdateTestSupport.context()).save(null,"announcements","synthetic:upgrade",profile,false,"SYNTHETIC_UPGRADE_ARCHIVE","SYNTHETIC_UPGRADE_REMINDER",now+86400000,false,now);
        String key=HomeworkStatusStore.key(profile,false,HomeworkCompletionTest.year(),"synthetic-homework");new HomeworkStatusStore(UpdateTestSupport.context()).set(key,true,HomeworkStatusStore.epoch());
        UpdateTestSupport.context().getSharedPreferences("update_install_test",0).edit().putString("profile",profile).putString("reminder",reminder.getString("id")).putString("homework",key).commit();
        File file=UpdateTestSupport.asset("update.apk");UpdateManager.transport=UpdateTestSupport.fake(UpdateTestSupport.release(file),file);m.check(true,null);UpdateTestSupport.idle(m);m.download();UpdateTestSupport.idle(m);assertTrue(m.ready);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(UpdateTestSupport.context(),MainActivity.class).putExtra("reminder_id","synthetic-missing"))){
            scenario.onActivity(a->{NavigationTestSupport.open(a,"Ustawienia");NavigationTestSupport.button(a.getWindow().getDecorView(),"Zainstaluj aktualizację").performClick();});UpdateTestSupport.idle(m);
            long until=System.currentTimeMillis()+10000;boolean confirmation=false;while(System.currentTimeMillis()<until){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText("Do you want to update").isEmpty()){confirmation=true;break;}Thread.sleep(30);}assertTrue("Missing Android update confirmation",confirmation);
        }
    }
    @Test public void verify()throws Exception{
        assertTrue(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));assertEquals(19,UpdateManager.installedCode(UpdateTestSupport.context()));assertEquals(0,UpdateTestSupport.context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE);
        var prefs=UpdateTestSupport.context().getSharedPreferences("update_install_test",0);String profile=prefs.getString("profile",""),reminderId=prefs.getString("reminder",""),key=prefs.getString("homework","");assertFalse(profile.isEmpty());
        SecureStore school=new SecureStore(UpdateTestSupport.context());JSONObject saved=new JSONObject(school.read());assertEquals(profile,saved.getString("profile"));assertTrue(saved.isNull("credentials"));assertTrue(saved.getJSONObject("announcement_archives").getJSONObject("profiles").getJSONObject(profile).getJSONArray("items").toString().contains("SYNTHETIC_PRIVATE_ARCHIVE"));
        ReminderStore reminders=new ReminderStore(UpdateTestSupport.context());assertEquals("SYNTHETIC_UPGRADE_REMINDER",reminders.get(reminderId).getString("note"));assertTrue(new HomeworkStatusStore(UpdateTestSupport.context()).completed().contains(key));
        try{if(!Python.isStarted())Python.start(new AndroidPlatform(UpdateTestSupport.context()));PyObject restored=Python.getInstance().getModule("mobile_bridge").get("MobileService").call();restored.callAttr("restore_json",saved.toString());assertTrue(restored.callAttr("state_json").toString().contains("SYNTHETIC_UPGRADE_ARCHIVE"));}
        finally{school.clear();reminders.delete(reminderId);ReminderAlarms.cancel(UpdateTestSupport.context(),reminderId);new HomeworkStatusStore(UpdateTestSupport.context()).set(key,false,HomeworkStatusStore.epoch());prefs.edit().clear().commit();UpdateManager.get(UpdateTestSupport.context()).prefs().edit().clear().commit();}
    }
}
