package pl.librushome.android;

import android.content.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

/** Combined cached view, original panels, source filters and private reminder profile isolation. */
public class CalendarTest {
    private static final String NOTE="SYNTHETIC_CALENDAR_NOTE";
    private View find(View root,String text,boolean button) {
        if(root instanceof TextView&&((TextView)root).getText().toString().equals(text)&&(!button||root instanceof Button))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),text,button);if(v!=null)return v;}return null;
    }
    private View described(View root,String label) {
        if(label.equals(root.getContentDescription()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=described(((ViewGroup)root).getChildAt(i),label);if(v!=null)return v;}return null;
    }
    private void waitText(String text)throws Exception {
        long until=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<until){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty())return;Thread.sleep(30);}fail("Missing text: "+text);
    }
    private void closeDialog()throws Exception {
        AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Zamknij"))if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
        fail("Missing Close action");
    }
    @Test public void aggregateKeepsDateOnlyDeadlinesAndIsolatesProfileAndDemoWithoutMutatingSources()throws Exception {
        JSONObject event=new JSONObject().put("id","s1").put("title","event").put("when","2026-10-09T08:00:00");
        JSONObject task=new JSONObject().put("id","h1").put("title","task").put("when","2026-10-09");
        long due=Instant.parse("2026-10-09T23:30:00Z").toEpochMilli();
        JSONObject own=new JSONObject().put("id","r1").put("profile","mine").put("demo",false).put("note","own note").put("title","source").put("due",due).put("status","pending");
        JSONArray reminders=new JSONArray().put(own).put(new JSONObject(own.toString()).put("id","foreign").put("profile","other")).put(new JSONObject(own.toString()).put("id","demo").put("demo",true));
        List<JSONObject> rows=CalendarData.combine(Arrays.asList(event),Arrays.asList(task),reminders,"mine",false);
        assertEquals(3,rows.size());assertEquals("schedule",rows.get(0).getString("calendar_kind"));assertEquals("homework",rows.get(1).getString("calendar_kind"));
        assertEquals("2026-10-09",rows.get(1).getString("when"));assertFalse(event.has("calendar_kind"));assertFalse(task.has("calendar_kind"));
        assertEquals("r1",rows.get(2).getString("reminder_id"));assertEquals(Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate(),DisplayData.day(rows.get(2).getString("when")));
        rows.get(0).put("title","view-only change");assertEquals("event",event.getString("title"));
        assertEquals("demo",CalendarData.combine(Collections.emptyList(),Collections.emptyList(),reminders,"mine",true).get(0).getString("reminder_id"));
        assertEquals(0,CalendarData.combine(Collections.emptyList(),Collections.emptyList(),reminders,"missing",false).size());
    }
    @Test public void separateCalendarCombinesThreeSourcesFiltersDayAndOpensExistingDetails()throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        String raw=new SecureStore(c).read();assertTrue("Use a blank emulator",raw.isEmpty()||(new JSONObject(raw).isNull("credentials")&&new JSONObject(raw).optString("profile").isEmpty()));
        ReminderStore store=new ReminderStore(c);LocalDate day=LocalDate.now().plusDays(3);long now=System.currentTimeMillis();
        JSONObject reminder=store.save(null,"homework","demo:homework:h2","demo",true,"SYNTHETIC_SOURCE",NOTE,ReminderStore.localTime(day,LocalTime.of(18,0),ZoneId.systemDefault()),false,now);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(c,MainActivity.class).putExtra("demo",true))) {
            waitText("DEMO ·");
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"Więcej",true));assertNotNull(find(root,"Zadania",true));find(root,"Kalendarz",true).performClick();});
            waitText("Pokaż w kalendarzu");
            if(!YearMonth.from(day).equals(YearMonth.now()))scenario.onActivity(a->find(a.getWindow().getDecorView(),"›",true).performClick());
            waitText(NOTE);
            String dayDescription=DisplayData.date(day.toString())+", wpisów: 3";
            scenario.onActivity(a->{View cell=described(a.getWindow().getDecorView(),dayDescription);assertNotNull(cell);cell.performClick();});
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"Opis ulubionej książki",false));assertNotNull(find(root,"Język angielski · kartkówka",false));assertNotNull(find(root,NOTE,false));assertNull(find(root,"Ćwiczenia z ułamków",false));});
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();((CheckBox)described(root,"Źródło kalendarza: Zadania domowe")).performClick();});
            scenario.onActivity(a->{assertNull(find(a.getWindow().getDecorView(),"Opis ulubionej książki",false));assertNotNull(find(a.getWindow().getDecorView(),NOTE,false));});
            scenario.recreate();waitText("Pokaż w kalendarzu");waitText(NOTE);
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertFalse(((CheckBox)described(root,"Źródło kalendarza: Zadania domowe")).isChecked());View note=find(root,NOTE,false);assertNotNull(note);((View)note.getParent()).performClick();});
            waitText("Twoje przypomnienie");closeDialog();
            scenario.onActivity(a->{View label=find(a.getWindow().getDecorView(),"Język angielski · kartkówka",false);((View)label.getParent()).performClick();});
            waitText("Przypomnij mi…");closeDialog();
            scenario.onActivity(a->{NavigationTestSupport.open(a,"Zadania domowe");assertNotNull(find(a.getWindow().getDecorView(),"Ćwiczenia z ułamków",false));});
            scenario.onActivity(a->{NavigationTestSupport.open(a,"Terminarz");assertNotNull(find(a.getWindow().getDecorView(),"Kalendarz + lista",true));});
        } finally {store.delete(reminder.getString("id"));ReminderAlarms.cancel(c,reminder.getString("id"));}
    }
}
