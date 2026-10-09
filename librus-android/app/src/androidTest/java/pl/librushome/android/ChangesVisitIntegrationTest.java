package pl.librushome.android;
import android.content.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.lifecycle.Lifecycle;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;

/** Two runs with force-stop between them; fake cached account has NO password and never contacts school. */
public class ChangesVisitIntegrationTest {
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private String profile()throws Exception{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest("synthetic-feed-visit".getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(byte b:digest)result.append(String.format(java.util.Locale.ROOT,"%02x",b));return result.toString();}
    private void barrier()throws Exception {
        var f=MobileRepository.class.getDeclaredField("worker");f.setAccessible(true);ExecutorService worker=(ExecutorService)f.get(MobileRepository.get(context()));worker.submit(()->{}).get(15,TimeUnit.SECONDS);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void waitFeed(ActivityScenario<MainActivity> scenario,int count)throws Exception {
        long until=System.currentTimeMillis()+20000;boolean[] found={false};
        while(System.currentTimeMillis()<until){scenario.onActivity(a->{JSONObject feed=ChangesFeedUiTest.state(a).optJSONObject("change_feed");JSONArray items=feed==null?null:feed.optJSONArray("items");found[0]=items!=null&&items.length()==count;});if(found[0]){barrier();return;}Thread.sleep(30);}fail("Missing cached change feed: "+count);
    }
    @Test public void seed()throws Exception {
        HomeworkCompletionTest.blank();if(!Python.isStarted())Python.start(new AndroidPlatform(context()));
        PyObject scope=Python.getInstance().getModule("builtins").callAttr("dict");
        String fixture="""
            import json, time
            from datetime import date
            from mobile_bridge import MobileService
            class Fake:
                revision = 0
                def __init__(self,*args): pass
                def connect(self): pass
                def close(self): pass
                def fetch(self,kind):
                    rows=[dict(id=kind+':old',title='SYNTHETIC_OLD_'+kind,when=date.today().isoformat())]
                    if Fake.revision: rows.append(dict(id=kind+':new',title='SYNTHETIC_NEW_'+kind,subtitle='SYNTHETIC_SUBTITLE',details='SYNTHETIC_DETAILS',when=date.today().isoformat()))
                    return rows
            svc=MobileService(Fake)
            svc.connect('synthetic-feed-visit','test-only')
            svc.begin_visit();svc.end_visit()
            Fake.revision=1
            svc.refresh();svc.refresh()
            saved=svc.export_json()
            """;
        Python.getInstance().getModule("builtins").callAttr("exec",fixture,scope);
        JSONObject saved=new JSONObject(scope.callAttr("get","saved").toString());assertTrue(saved.isNull("credentials"));assertEquals(7,saved.getJSONObject("change_journal").getJSONArray("pending").length());new SecureStore(context()).write(saved.toString());
    }
    @Test public void verify()throws Exception {
        assertTrue(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));SecureStore store=new SecureStore(context());JSONObject saved=new JSONObject(store.read());assertTrue("Wrong fixture profile",profile().equals(saved.optString("profile")));assertTrue(saved.isNull("credentials"));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context(),MainActivity.class).putExtra("reminder_id","synthetic-missing"))) {
            waitFeed(scenario,7);final double[] visit={0};
            scenario.onActivity(a->{NavigationTestSupport.open(a,"Przegląd");JSONObject feed=ChangesFeedUiTest.state(a).optJSONObject("change_feed");visit[0]=feed.optDouble("last_visit");Set<String> kinds=new HashSet<>();JSONArray items=feed.optJSONArray("items");for(int i=0;i<items.length();i++)kinds.add(items.optJSONObject(i).optString("kind"));assertEquals(7,kinds.size());((Spinner)ChangesFeedUiTest.described(a.getWindow().getDecorView(),"Źródło zmian na ekranie Start")).setSelection(4);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();scenario.recreate();barrier();
            scenario.onActivity(a->{assertEquals(visit[0],ChangesFeedUiTest.state(a).optJSONObject("change_feed").optDouble("last_visit"),0);assertEquals(7,ChangesFeedUiTest.state(a).optJSONObject("change_feed").optJSONArray("items").length());assertEquals(4,((Spinner)ChangesFeedUiTest.described(a.getWindow().getDecorView(),"Źródło zmian na ekranie Start")).getSelectedItemPosition());});
            scenario.moveToState(Lifecycle.State.CREATED);barrier();
            String add="""
                import mobile_bridge, time
                from datetime import date
                svc=mobile_bridge.get_service()
                svc.journal.apply(svc.tracker,'grades',svc.tracker.sections['grades']+[dict(id='after-home',title='SYNTHETIC_AFTER_HOME',when=date.today().isoformat())],time.time())
                """;
            Python.getInstance().getModule("builtins").callAttr("exec",add,Python.getInstance().getModule("builtins").callAttr("dict"));
            scenario.moveToState(Lifecycle.State.RESUMED);waitFeed(scenario,1);
            scenario.onActivity(a->{JSONObject feed=ChangesFeedUiTest.state(a).optJSONObject("change_feed");assertEquals(visit[0],feed.optDouble("since"),0);assertEquals("SYNTHETIC_AFTER_HOME",feed.optJSONArray("items").optJSONObject(0).optJSONObject("item").optString("title"));assertEquals(0,((Spinner)ChangesFeedUiTest.described(a.getWindow().getDecorView(),"Źródło zmian na ekranie Start")).getSelectedItemPosition());});
        }finally{barrier();store.clear();}
    }
}
