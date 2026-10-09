package pl.librushome.android;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Only demo/synthetic data: preview, filters, rotation, details and removed metadata. */
public class ChangesFeedUiTest {
    static View described(View root,String value){
        if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=described(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;
    }
    static JSONObject state(MainActivity a){try{var f=MainActivity.class.getDeclaredField("state");f.setAccessible(true);return (JSONObject)f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private void close(String text)throws Exception {
        long until=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<until){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty()){for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("Zamknij"))if(n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;}Thread.sleep(30);}fail("Missing details: "+text);
    }
    @Test public void demoFeedFiltersSevenKindsKeepsVisitOnRotationAndOpensSourceDetails()throws Exception {
        HomeworkCompletionTest.blank();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(HomeworkCompletionTest.context(),MainActivity.class).putExtra("demo",true))) {
            HomeworkCompletionTest.ready(scenario);final double[] visit={0};
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(HomeworkCompletionTest.find(root,"Od ostatniego wejścia · 7"));JSONObject feed=state(a).optJSONObject("change_feed");visit[0]=feed.optDouble("last_visit");assertEquals(7,feed.optJSONArray("items").length());((Spinner)described(root,"Źródło zmian na ekranie Start")).setSelection(4);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{View row=described(a.getWindow().getDecorView(),"Zmiana: Matematyka · sprawdzian. Otwórz szczegóły");assertNotNull(row);row.performClick();});close("Przypomnij mi…");
            scenario.recreate();HomeworkCompletionTest.ready(scenario);
            scenario.onActivity(a->{assertEquals(visit[0],state(a).optJSONObject("change_feed").optDouble("last_visit"),0);assertEquals(4,((Spinner)described(a.getWindow().getDecorView(),"Źródło zmian na ekranie Start")).getSelectedItemPosition());});
        }
    }
    @Test public void removedEntryOutsideDayPlanOpensSnapshotWithoutMutatingJournal()throws Exception {
        HomeworkCompletionTest.blank();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(HomeworkCompletionTest.context(),MainActivity.class).putExtra("demo",true))) {
            HomeworkCompletionTest.ready(scenario);
            JSONObject item=new JSONObject().put("id","synthetic-removed").put("title","SYNTHETIC_REMOVED_EVENT").put("subtitle","SYNTHETIC_SUBTITLE").put("when","2026-12-01").put("details","SYNTHETIC_OLD_DETAILS");
            JSONObject event=new JSONObject().put("kind","schedule").put("event","removed").put("item",item).put("sequence",1).put("detected_at",System.currentTimeMillis()/1000.0);
            JSONObject supplied=new JSONObject().put("ready",true).put("mode","demo").put("year",HomeworkCompletionTest.year()).put("change_feed",new JSONObject().put("items",new JSONArray().put(event)));
            scenario.onActivity(a->{a.onState(supplied);View row=described(a.getWindow().getDecorView(),"Zmiana: SYNTHETIC_REMOVED_EVENT. Otwórz szczegóły");assertNotNull(row);row.performClick();});close("Wpis został usunięty");assertEquals("SYNTHETIC_OLD_DETAILS",item.getString("details"));
        }
    }
}
