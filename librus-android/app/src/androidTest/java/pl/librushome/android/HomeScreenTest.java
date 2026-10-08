package pl.librushome.android;

import android.content.*;
import android.graphics.Rect;
import android.os.Build;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

/** Synthetic-only day view and real navigation regression tests. */
public class HomeScreenTest {
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private View find(View root,String value) {
        if(root instanceof TextView&&((TextView)root).getText().toString().equals(value))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View child=find(((ViewGroup)root).getChildAt(i),value);if(child!=null)return child;}
        return null;
    }
    private void blank()throws Exception {
        assertTrue(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        String raw=new SecureStore(context()).read();assertTrue("Use a blank emulator",raw.isEmpty()||new JSONObject(raw).isNull("credentials"));
    }
    private void ready(ActivityScenario<MainActivity> scenario)throws Exception {
        long until=System.currentTimeMillis()+20000;boolean[] found={false};
        while(System.currentTimeMillis()<until) {
            scenario.onActivity(a->found[0]=find(a.getWindow().getDecorView(),"Dzisiaj")!=null);
            if(found[0])return;Thread.sleep(40);
        }
        fail("Missing day overview");
    }
    private JSONObject item(String title,String when)throws Exception {return new JSONObject().put("title",title).put("when",when);}
    @Test public void datesRespectLocalZoneSortHoursAndKeepSourcesAndPendingStatus()throws Exception {
        TimeZone before=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"));
        try {
            LocalDate day=LocalDate.of(2026,10,9);
            JSONObject allDay=item("All day",day.toString()),late=item("Late",day+" 10:00"),early=item("Early",day+"T08:00:00"),offset=item("Offset","2026-10-08T22:30:00Z"),invalid=item("Unknown","");
            List<JSONObject> source=Arrays.asList(late,early,allDay,offset,invalid,item("Other",day.plusDays(1).toString()));
            assertEquals(Arrays.asList(allDay,offset,early,late),HomeDayData.onDay(source,day,false));
            assertEquals(late,source.get(0));assertFalse(allDay.has("calendar_kind"));assertEquals(1,HomeDayData.unknownDates(source));
            assertEquals("00:30",HomeDayData.timeLabel(offset.getString("when")));assertTrue(HomeDayData.timeLabel(day.toString()).contains("bez podanej godziny"));
            JSONObject pending=item("Pending",day.toString()).put("details","Zaplanowane"),fired=item("Fired",day.toString()).put("details","Wykonane");
            assertEquals(Arrays.asList(pending),HomeDayData.onDay(Arrays.asList(fired,pending),day,true));
            assertFalse(HomeDayData.outsideTimetableRange(LocalDate.of(2026,10,18),"2026-10-08"));assertTrue(HomeDayData.outsideTimetableRange(LocalDate.of(2026,10,19),"2026-10-08"));
        }finally{TimeZone.setDefault(before);}
    }
    @Test public void todayTomorrowRemindersDetailsAndSelectionSurviveRecreation()throws Exception {
        blank();ReminderStore store=new ReminderStore(context());long now=System.currentTimeMillis();LocalDate tomorrow=LocalDate.now().plusDays(1);
        JSONObject own=store.save(null,"messages","synthetic-home-source","demo",true,"SYNTHETIC_SOURCE","SYNTHETIC_HOME_NOTE",ReminderStore.localTime(tomorrow,LocalTime.of(18,0),ZoneId.systemDefault()),false,now);
        JSONObject foreign=store.save(null,"messages","synthetic-foreign-source","other",false,"SYNTHETIC_FOREIGN","FOREIGN_NOTE",own.getLong("due"),false,now);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context(),MainActivity.class).putExtra("demo",true))) {
            ready(scenario);
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"Lekcje · 4"));assertNotNull(find(root,"Matematyka"));assertNull(find(root,"Historia"));NavigationTestSupport.button(root,"Jutro").performClick();});
            // Reminder loading is asynchronous and must finish before asserting the new local day.
            long until=System.currentTimeMillis()+5000;boolean[] found={false};
            while(System.currentTimeMillis()<until){scenario.onActivity(a->found[0]=find(a.getWindow().getDecorView(),"SYNTHETIC_HOME_NOTE")!=null);if(found[0])break;Thread.sleep(30);}assertTrue(found[0]);
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"Lekcje · 1"));assertNotNull(find(root,"Historia"));assertNotNull(find(root,"Ćwiczenia z ułamków"));assertNotNull(find(root,"Matematyka · sprawdzian"));assertNull(find(root,"FOREIGN_NOTE"));});
            scenario.recreate();ready(scenario);
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"Historia"));assertNotNull(find(root,"Ćwiczenia z ułamków"));((View)find(root,"Ćwiczenia z ułamków").getParent()).performClick();});
            long dialogDeadline=System.currentTimeMillis()+5000;boolean detail=false;
            while(System.currentTimeMillis()<dialogDeadline){android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText("Pobierz treść zadania").isEmpty()){detail=true;for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Zamknij"))node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);break;}Thread.sleep(30);}assertTrue("Home homework entry must open its original detail dialog",detail);
            scenario.onActivity(a->{NavigationTestSupport.open(a,"Wiadomości");assertTrue(NavigationTestSupport.button(a.getWindow().getDecorView(),"Wiadomości").isSelected());});
            scenario.recreate();scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertTrue(NavigationTestSupport.button(root,"Wiadomości").isSelected());assertNotNull(find(root,"Zebranie rodziców w przyszłym tygodniu"));});
        }finally{for(JSONObject r:Arrays.asList(own,foreign)){store.delete(r.getString("id"));ReminderAlarms.cancel(context(),r.getString("id"));}}
    }
    @Test public void bottomNavigationStaysVisibleAndEverySecondaryPanelIsReachable()throws Exception {
        blank();try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context(),MainActivity.class).putExtra("demo",true))) {
            ready(scenario);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final LinearLayout[] footer={null};final int[] originalY={0};
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();footer[0]=(LinearLayout)NavigationTestSupport.button(root,"Start").getParent();assertEquals(5,footer[0].getChildCount());int[] pos=new int[2];footer[0].getLocationOnScreen(pos);originalY[0]=pos[1];NavigationTestSupport.open(a,"Ustawienia");View last=find(a.getWindow().getDecorView(),"LibrusApp Android 0.7.0");assertNotNull(last);last.requestRectangleOnScreen(new Rect(0,0,last.getWidth(),last.getHeight()),true);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{int[] pos=new int[2];footer[0].getLocationOnScreen(pos);assertEquals(originalY[0],pos[1]);for(int i=0;i<5;i++){View b=footer[0].getChildAt(i);Rect r=new Rect();assertTrue(b.getGlobalVisibleRect(r));assertTrue(r.height()>0);}assertTrue(NavigationTestSupport.button(a.getWindow().getDecorView(),"Więcej").isSelected());});
            for(String panel:new String[]{"Oceny","Ogłoszenia","Terminarz","Frekwencja","Plan lekcji","Przypomnienia","Ustawienia"})scenario.onActivity(a->{NavigationTestSupport.open(a,panel);assertNotNull(find(a.getWindow().getDecorView(),panel));assertTrue(NavigationTestSupport.button(a.getWindow().getDecorView(),"Więcej").isSelected());});
            scenario.recreate();scenario.onActivity(a->{assertNotNull(find(a.getWindow().getDecorView(),"Konto i dane"));assertTrue(NavigationTestSupport.button(a.getWindow().getDecorView(),"Więcej").isSelected());});
        }
    }
    @Test public void offlineCacheDistinguishesUnreadSectionsAndFailedRefreshWithoutHidingEntries()throws Exception {
        blank();try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context(),MainActivity.class).putExtra("demo",true))) {
            ready(scenario);JSONObject lesson=item("SYNTHETIC_CACHED_LESSON",LocalDate.now()+" 08:00");
            JSONObject cache=new JSONObject().put("ready",true).put("mode","offline").put("status","SYNTHETIC_CACHE").put("sections",new JSONObject().put("timetable",new JSONArray().put(lesson)))
                    .put("updated_at",new JSONObject().put("timetable",LocalDate.now().minusDays(20).toString())).put("errors",new JSONObject().put("timetable","SYNTHETIC_READ_ERROR"));
            scenario.onActivity(a->{a.onState(cache);View root=a.getWindow().getDecorView();assertNotNull(find(root,"SYNTHETIC_CACHED_LESSON"));assertNotNull(find(root,"Sekcja jeszcze niepobrana."));assertNotNull(find(root,"Ten dzień jest poza zakresem ostatniego odczytu planu. Odśwież dane."));assertNotNull(find(root,"Ostatni odczyt nie udał się: SYNTHETIC_READ_ERROR"));assertNull(find(root,"Brak zadań z terminem na ten dzień w pobranych danych."));assertNull(find(root,"Dziennik pod ręką"));});
        }
    }
}
