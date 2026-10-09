package pl.librushome.android;

import android.content.*;
import android.os.Build;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import org.json.*;
import org.junit.Test;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Two explicit runs with force-stop between: fictional account, NO password or school request. */
public class AnnouncementArchiveRestartTest {
    private void barrier()throws Exception {
        var field=MobileRepository.class.getDeclaredField("worker");field.setAccessible(true);
        ((ExecutorService)field.get(MobileRepository.get(HomeworkCompletionTest.context()))).submit(()->{}).get(15,TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void close(String expected)throws Exception {
        long until=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<until){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if(root!=null&&!root.findAccessibilityNodeInfosByText(expected).isEmpty())for(var node:root.findAccessibilityNodeInfosByText("Zamknij"))if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
            Thread.sleep(30);
        }fail("Missing archived detail: "+expected);
    }
    @Test public void seed()throws Exception {
        HomeworkCompletionTest.blank();if(!Python.isStarted())Python.start(new AndroidPlatform(HomeworkCompletionTest.context()));
        PyObject scope=Python.getInstance().getModule("builtins").callAttr("dict");
        String fixture="""
            import json
            from mobile_bridge import MobileService
            class Fake:
                revision = 0
                def __init__(self,*args): pass
                def connect(self): pass
                def close(self): pass
                def fetch(self,kind):
                    if kind != 'announcements' or Fake.revision == 2: return []
                    first=dict(id='synthetic:a',title='SYNTHETIC_NOTICE_A',subtitle='SYNTHETIC_AUTHOR',when='2000-10-10',details='SYNTHETIC_CONTENT_V1' if Fake.revision==0 else 'SYNTHETIC_CONTENT_V2')
                    second=dict(id='synthetic:b',title='SYNTHETIC_NOTICE_B',details='SYNTHETIC_OLDER_CONTENT',when='2000-10-09')
                    return [first,second] if Fake.revision==0 else [first]
            svc=MobileService(Fake)
            svc.connect('synthetic-announcement-archive','test-only')
            Fake.revision=1;svc.refresh()
            Fake.revision=2;svc.refresh();svc.refresh()
            saved=json.loads(svc.export_json())
            saved['snapshot']['year']='2000/2001'
            saved['change_journal']['year']='2000/2001'
            saved=json.dumps(saved)
            """;
        Python.getInstance().getModule("builtins").callAttr("exec",fixture,scope);
        String raw=scope.callAttr("get","saved").toString();JSONObject saved=new JSONObject(raw);assertTrue(saved.isNull("credentials"));
        new SecureStore(HomeworkCompletionTest.context()).write(raw);
        byte[] disk=java.nio.file.Files.readAllBytes(new java.io.File(HomeworkCompletionTest.context().getNoBackupFilesDir(),"state.aes").toPath());
        assertFalse(new String(disk,java.nio.charset.StandardCharsets.ISO_8859_1).contains("SYNTHETIC_CONTENT_V2"));
    }
    @Test public void verify()throws Exception {
        assertTrue(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));SecureStore store=new SecureStore(HomeworkCompletionTest.context());JSONObject saved=new JSONObject(store.read());assertTrue(saved.isNull("credentials"));
        if(!Python.isStarted())Python.start(new AndroidPlatform(HomeworkCompletionTest.context()));
        String profile=Python.getInstance().getModule("mobile_bridge").get("MobileService").callAttr("profile_id","synthetic-announcement-archive").toString();
        assertEquals("Wrong fixture profile",profile,saved.optString("profile"));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(HomeworkCompletionTest.context(),MainActivity.class).putExtra("reminder_id","synthetic-missing"))) {
            long until=System.currentTimeMillis()+15000;boolean[] ready={false};
            while(System.currentTimeMillis()<until){scenario.onActivity(a->{JSONObject sections=ChangesFeedUiTest.state(a).optJSONObject("sections");JSONArray rows=sections==null?null:sections.optJSONArray("announcements");ready[0]=rows!=null&&rows.length()==2;});if(ready[0])break;Thread.sleep(30);}assertTrue("Missing restored archive",ready[0]);barrier();
            scenario.onActivity(a->{JSONObject state=ChangesFeedUiTest.state(a);assertEquals(HomeworkCompletionTest.year(),state.optString("year"));assertEquals("offline",state.optString("mode"));assertFalse(state.optBoolean("connected"));JSONArray rows=state.optJSONObject("sections").optJSONArray("announcements");assertTrue(rows.optJSONObject(0).optBoolean("archived"));assertEquals("SYNTHETIC_CONTENT_V2",rows.optJSONObject(0).optString("details"));assertEquals(0,state.optJSONObject("sections").optJSONArray("grades").length());NavigationTestSupport.open(a,"Ogłoszenia");assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Lokalne archiwum"));assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"SYNTHETIC_NOTICE_B"));ChangesFeedUiTest.described(a.getWindow().getDecorView(),"SYNTHETIC_NOTICE_A. Otwórz szczegóły").performClick();});
            close("SYNTHETIC_CONTENT_V2");scenario.recreate();barrier();
            scenario.onActivity(a->{assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"SYNTHETIC_NOTICE_A"));NavigationTestSupport.open(a,"Szukaj");EditText query=(EditText)ChangesFeedUiTest.described(a.getWindow().getDecorView(),"Wspólne wyszukiwanie");query.setText("SYNTHETIC_CONTENT_V2");assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Wyników: 1"));assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"SYNTHETIC_NOTICE_A"));query.setText("SYNTHETIC_CONTENT_V1");assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Wyników: 0"));});
        }finally{barrier();store.clear();}
    }
}
