package pl.librushome.android;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Only run on the blank helper emulator: offline navigation, source details, restoration and pagination. */
public class GlobalSearchUiTest {
    private static EditText query(MainActivity a){return (EditText)ChangesFeedUiTest.described(a.getWindow().getDecorView(),"Wspólne wyszukiwanie");}
    private static Spinner source(MainActivity a){return (Spinner)ChangesFeedUiTest.described(a.getWindow().getDecorView(),"Źródło wspólnego wyszukiwania");}
    private void close(String expected)throws Exception {
        long until=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<until){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if(root!=null&&!root.findAccessibilityNodeInfosByText(expected).isEmpty())for(var node:root.findAccessibilityNodeInfosByText("Zamknij"))if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
            Thread.sleep(30);
        }fail("Missing details: "+expected);
    }
    @Test public void startAndMoreSearchKeepQueryFilterOnRotationAndOpenHomeworkWithoutFetchingBody()throws Exception {
        HomeworkCompletionTest.blank();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(HomeworkCompletionTest.context(),MainActivity.class).putExtra("demo",true))) {
            HomeworkCompletionTest.ready(scenario);
            scenario.onActivity(a->{NavigationTestSupport.button(a.getWindow().getDecorView(),"Szukaj we wszystkich sekcjach").performClick();query(a).setText("ulamkow");});
            scenario.onActivity(a->{View row=ChangesFeedUiTest.described(a.getWindow().getDecorView(),"Ćwiczenia z ułamków. Otwórz szczegóły");assertNotNull(row);row.performClick();});close("Pobierz treść zadania");
            scenario.onActivity(a->{query(a).setText("matematyka");source(a).setSelection(4);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.recreate();
            scenario.onActivity(a->{assertEquals("matematyka",query(a).getText().toString());assertEquals(4,source(a).getSelectedItemPosition());assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Matematyka · sprawdzian"));NavigationTestSupport.open(a,"Wiadomości");
                EditText local=(EditText)ChangesFeedUiTest.described(a.getWindow().getDecorView(),"Wyszukiwanie w bieżącej sekcji");assertEquals("",local.getText().toString());local.setText("local-only");NavigationTestSupport.open(a,"Szukaj");assertEquals("matematyka",query(a).getText().toString());assertEquals(4,source(a).getSelectedItemPosition());
                NavigationTestSupport.button(a.getWindow().getDecorView(),"Wyczyść").performClick();assertEquals("",query(a).getText().toString());assertNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Matematyka · sprawdzian"));});
        }
    }
    @Test public void largeOfflineListIncludesPastEntriesAndUpdatesResultsWithoutDroppingTypedQuery()throws Exception {
        HomeworkCompletionTest.blank();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(HomeworkCompletionTest.context(),MainActivity.class).putExtra("demo",true))) {
            HomeworkCompletionTest.ready(scenario);JSONArray rows=new JSONArray();for(int i=0;i<65;i++)rows.put(new JSONObject().put("id","synthetic-"+i).put("title",String.format(java.util.Locale.ROOT,"SYNTHETIC_RESULT_%02d",i)).put("subtitle","Żółć").put("when","2020-01-01").put("details","Opis: konkurs"));
            JSONObject supplied=new JSONObject().put("ready",true).put("mode","demo").put("year",HomeworkCompletionTest.year()).put("sections",new JSONObject().put("messages",rows));
            scenario.onActivity(a->{a.onState(supplied);NavigationTestSupport.open(a,"Szukaj");query(a).setText("zolc konkurs");assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Wyników: 65"));assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Pokazano 50 z 65"));assertNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"SYNTHETIC_RESULT_64"));NavigationTestSupport.button(a.getWindow().getDecorView(),"Pokaż kolejne wyniki").performClick();assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"SYNTHETIC_RESULT_64"));});
            rows.put(new JSONObject().put("id","synthetic-more").put("title","SYNTHETIC_LATE_UPDATE").put("details","Konkurs Żółć"));
            scenario.onActivity(a->{a.onState(supplied);assertEquals("zolc konkurs",query(a).getText().toString());assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Wyników: 66"));assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"SYNTHETIC_LATE_UPDATE"));query(a).setText("nonexistent");assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Wyników: 0"));});
        }
    }
    @Test public void ownReminderIsSearchableAndOpensOriginalDetailWithoutShowingOtherProfile()throws Exception {
        HomeworkCompletionTest.blank();ReminderStore store=new ReminderStore(HomeworkCompletionTest.context());long now=System.currentTimeMillis();
        JSONObject own=store.save(null,"schedule","synthetic-source","demo",true,"SYNTHETIC_SOURCE","SYNTHETIC_SEARCH_NOTE",now+3600000,false,now);
        JSONObject foreign=store.save(null,"schedule","synthetic-source","OTHER_PROFILE",false,"SYNTHETIC_SOURCE","SYNTHETIC_FOREIGN_NOTE",now+3600000,false,now);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(HomeworkCompletionTest.context(),MainActivity.class).putExtra("demo",true))) {
            HomeworkCompletionTest.ready(scenario);scenario.onActivity(a->{NavigationTestSupport.open(a,"Szukaj");query(a).setText("SYNTHETIC_SEARCH_NOTE");});
            long until=System.currentTimeMillis()+5000;boolean[] found={false};while(System.currentTimeMillis()<until){scenario.onActivity(a->found[0]=HomeworkCompletionTest.find(a.getWindow().getDecorView(),"SYNTHETIC_SEARCH_NOTE")!=null);if(found[0])break;Thread.sleep(30);}assertTrue(found[0]);
            scenario.onActivity(a->{ChangesFeedUiTest.described(a.getWindow().getDecorView(),"SYNTHETIC_SEARCH_NOTE. Otwórz szczegóły").performClick();});close("Twoje przypomnienie");
            scenario.onActivity(a->{query(a).setText("SYNTHETIC_FOREIGN_NOTE");assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Wyników: 0"));});
        }finally{store.delete(own.getString("id"));store.delete(foreign.getString("id"));ReminderAlarms.cancel(HomeworkCompletionTest.context(),own.getString("id"));ReminderAlarms.cancel(HomeworkCompletionTest.context(),foreign.getString("id"));}
    }
}
