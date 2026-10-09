package pl.librushome.android;

import android.content.*;
import android.os.Build;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;
import java.util.*;

/** Synthetic-only encrypted status, undo and cross-panel regression coverage. */
public class HomeworkCompletionTest {
    static String year(){int y=LocalDate.now().getYear()-(LocalDate.now().getMonthValue()<9?1:0);return y+"/"+(y+1);}
    static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    static void blank()throws Exception {
        assertTrue(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));String raw=new SecureStore(context()).read();
        assertTrue("Never use a school account",raw.isEmpty()||(new JSONObject(raw).isNull("credentials")&&new JSONObject(raw).optString("profile").isEmpty()));
    }
    static String demoKey(){return HomeworkStatusStore.key("demo",true,year(),"demo:homework:h1");}
    static View find(View root,String text) {
        if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View child=find(((ViewGroup)root).getChildAt(i),text);if(child!=null)return child;}return null;
    }
    static CheckBox checkbox(View root,String title) {
        if(root instanceof CheckBox&&("Zrobione: "+title).contentEquals(root.getContentDescription()))return (CheckBox)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){CheckBox child=checkbox(((ViewGroup)root).getChildAt(i),title);if(child!=null)return child;}return null;
    }
    static void ready(ActivityScenario<MainActivity> scenario)throws Exception {
        long until=System.currentTimeMillis()+20000;boolean[] ready={false};
        while(System.currentTimeMillis()<until){scenario.onActivity(a->ready[0]=find(a.getWindow().getDecorView(),"Dzisiaj")!=null);if(ready[0])return;Thread.sleep(30);}fail("Demo not ready");
    }
    static void waitCheckbox(ActivityScenario<MainActivity> scenario,boolean done)throws Exception {
        long until=System.currentTimeMillis()+5000;boolean[] ready={false};
        while(System.currentTimeMillis()<until){scenario.onActivity(a->{CheckBox box=checkbox(a.getWindow().getDecorView(),"Ćwiczenia z ułamków");ready[0]=box!=null&&box.isEnabled()&&box.isChecked()==done;});if(ready[0])return;Thread.sleep(30);}fail("Completion control not ready: "+done);
    }
    static void waitAbsent(ActivityScenario<MainActivity> scenario)throws Exception {
        long until=System.currentTimeMillis()+5000;boolean[] ready={false};
        while(System.currentTimeMillis()<until){scenario.onActivity(a->ready[0]=find(a.getWindow().getDecorView(),"Ćwiczenia z ułamków")==null&&NavigationTestSupport.button(a.getWindow().getDecorView(),"Zrobione").isEnabled());if(ready[0])return;Thread.sleep(30);}fail("Completed task still in To do");
    }
    private HomeworkStatusStore isolated(String name) {return new HomeworkStatusStore(context(),name+".aes","LibrusApp.test."+name);}
    @Test public void encryptedStatusesSurviveNewStoreUndoAndAreIsolatedByAccountDemoYearAndId()throws Exception {
        String file="test-homework-state",alias="LibrusApp.test."+file;HomeworkStatusStore store=isolated(file);store.clear();
        try {
            String key=HomeworkStatusStore.key("SYNTHETIC_PROFILE",false,"2026/2027","homework:123");store.set(key,true,HomeworkStatusStore.epoch());
            assertEquals(Collections.singleton(key),isolated(file).completed());
            for(String other:Arrays.asList(HomeworkStatusStore.key("OTHER_PROFILE",false,"2026/2027","homework:123"),HomeworkStatusStore.key("SYNTHETIC_PROFILE",true,"2026/2027","homework:123"),HomeworkStatusStore.key("SYNTHETIC_PROFILE",false,"2027/2028","homework:123"),HomeworkStatusStore.key("SYNTHETIC_PROFILE",false,"2026/2027","homework:124")))assertFalse(store.completed().contains(other));
            byte[] ciphertext=java.nio.file.Files.readAllBytes(new java.io.File(context().getNoBackupFilesDir(),file+".aes").toPath());assertFalse(new String(ciphertext,java.nio.charset.StandardCharsets.ISO_8859_1).contains("SYNTHETIC_PROFILE"));
            store.set(key,true,HomeworkStatusStore.epoch());assertArrayEquals(ciphertext,java.nio.file.Files.readAllBytes(new java.io.File(context().getNoBackupFilesDir(),file+".aes").toPath()));
            // Neither title nor due date belongs to the key, so school edits retain the user's choice.
            JSONObject edited=new JSONObject().put("id","homework:123").put("title","EDITED_TITLE").put("when","2026-11-10");assertTrue(store.completed().contains(HomeworkStatusStore.key("SYNTHETIC_PROFILE",false,"2026/2027",edited.getString("id"))));
            store.set(key,false,HomeworkStatusStore.epoch());assertTrue(isolated(file).completed().isEmpty());
        }finally{store.clear();}
    }
    @Test public void unreadableStatusesCannotBeOverwrittenAndInvalidIdentityIsRejected()throws Exception {
        String name="test-homework-corrupt",alias="LibrusApp.test."+name;HomeworkStatusStore store=isolated(name);store.clear();
        SecureStore encrypted=new SecureStore(context(),name+".aes",alias);String invalid="{\"version\":1,\"done\":\"bad\"}";
        try {
            encrypted.write(invalid);boolean refused=false;
            try{store.set(HomeworkStatusStore.key("SYNTHETIC_PROFILE",false,year(),"homework:1"),true,HomeworkStatusStore.epoch());}catch(Exception expected){refused=true;}assertTrue(refused);assertEquals(invalid,encrypted.read());
            refused=false;try{HomeworkStatusStore.key("",false,year(),"homework:1");}catch(IllegalArgumentException expected){refused=true;}assertTrue(refused);assertEquals(invalid,encrypted.read());
        }finally{store.clear();}
    }
    @Test public void clearingStatusesAlsoRejectsAWritesQueuedBeforeForget()throws Exception {
        HomeworkStatusStore store=isolated("test-homework-clear");store.clear();long before=HomeworkStatusStore.epoch();String key=HomeworkStatusStore.key("SYNTHETIC_PROFILE",false,year(),"homework:1");
        try {store.set(key,true,before);assertFalse(store.completed().isEmpty());store.clear();boolean refused=false;try{store.set(key,true,before);}catch(IllegalStateException expected){refused=true;}assertTrue(refused);assertTrue(store.completed().isEmpty());}finally{store.clear();}
    }
    @Test public void homeworkCheckboxFiltersRecreateAndDetailUndoWorkOffline()throws Exception {
        blank();HomeworkStatusStore store=new HomeworkStatusStore(context());store.set(demoKey(),false,HomeworkStatusStore.epoch());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context(),MainActivity.class).putExtra("demo",true))) {
            ready(scenario);scenario.onActivity(a->NavigationTestSupport.open(a,"Zadania domowe"));waitCheckbox(scenario,false);
            scenario.onActivity(a->checkbox(a.getWindow().getDecorView(),"Ćwiczenia z ułamków").performClick());waitAbsent(scenario);assertTrue(store.completed().contains(demoKey()));
            scenario.onActivity(a->NavigationTestSupport.button(a.getWindow().getDecorView(),"Zrobione").performClick());waitCheckbox(scenario,true);
            scenario.recreate();waitCheckbox(scenario,true);
            scenario.onActivity(a->{View label=find(a.getWindow().getDecorView(),"Ćwiczenia z ułamków");((View)label.getParent()).performClick();});
            long until=System.currentTimeMillis()+5000;boolean clicked=false;
            while(System.currentTimeMillis()<until){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText("Twój status na tym telefonie").isEmpty()){
                for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Zrobione"))if(node.isCheckable()&&node.isEnabled()){assertTrue(node.isChecked());clicked=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
                if(clicked)break;}Thread.sleep(30);}assertTrue("Cannot undo in details",clicked);
            until=System.currentTimeMillis()+5000;while(store.completed().contains(demoKey())&&System.currentTimeMillis()<until)Thread.sleep(30);assertFalse(store.completed().contains(demoKey()));
            AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Zamknij"))node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            scenario.onActivity(a->NavigationTestSupport.button(a.getWindow().getDecorView(),"Do zrobienia").performClick());waitCheckbox(scenario,false);
        }finally{store.set(demoKey(),false,HomeworkStatusStore.epoch());}
    }
    @Test public void forgettingLocalDataThroughSettingsClearsCompletionAndCachedUi()throws Exception {
        blank();HomeworkStatusStore store=new HomeworkStatusStore(context());store.set(demoKey(),false,HomeworkStatusStore.epoch());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context(),MainActivity.class).putExtra("demo",true))) {
            ready(scenario);scenario.onActivity(a->NavigationTestSupport.open(a,"Zadania domowe"));waitCheckbox(scenario,false);
            scenario.onActivity(a->checkbox(a.getWindow().getDecorView(),"Ćwiczenia z ułamków").performClick());waitAbsent(scenario);assertTrue(store.completed().contains(demoKey()));
            scenario.onActivity(a->{NavigationTestSupport.open(a,"Ustawienia");NavigationTestSupport.button(a.getWindow().getDecorView(),"Usuń lokalne dane i konto").performClick();});
            long until=System.currentTimeMillis()+5000;boolean clicked=false;
            while(System.currentTimeMillis()<until){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Usuń"))if(node.getText()!=null&&"Usuń".equalsIgnoreCase(node.getText().toString())&&node.isClickable())clicked=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);if(clicked)break;Thread.sleep(30);}assertTrue("Missing deletion confirmation",clicked);
            until=System.currentTimeMillis()+5000;while(!store.completed().isEmpty()&&System.currentTimeMillis()<until)Thread.sleep(30);assertTrue(store.completed().isEmpty());
            // Return to demo after the erase: the previous in-memory mark must not reappear.
            boolean[] idle={false};until=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<until){scenario.onActivity(a->idle[0]=NavigationTestSupport.button(a.getWindow().getDecorView(),"Konto").isEnabled());if(idle[0])break;Thread.sleep(30);}assertTrue("Forget did not finish",idle[0]);scenario.onActivity(a->NavigationTestSupport.button(a.getWindow().getDecorView(),"Włącz demo").performClick());
            scenario.onActivity(a->{NavigationTestSupport.open(a,"Zadania domowe");NavigationTestSupport.button(a.getWindow().getDecorView(),"Do zrobienia").performClick();});waitCheckbox(scenario,false);
        }finally{store.set(demoKey(),false,HomeworkStatusStore.epoch());}
    }
    @Test public void completedTaskLeavesTodayTomorrowAndRemainsCheckedInCalendar()throws Exception {
        blank();HomeworkStatusStore store=new HomeworkStatusStore(context());store.set(demoKey(),false,HomeworkStatusStore.epoch());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context(),MainActivity.class).putExtra("demo",true))) {
            ready(scenario);scenario.onActivity(a->NavigationTestSupport.button(a.getWindow().getDecorView(),"Jutro").performClick());waitCheckbox(scenario,false);
            scenario.onActivity(a->checkbox(a.getWindow().getDecorView(),"Ćwiczenia z ułamków").performClick());
            long until=System.currentTimeMillis()+5000;boolean[] removed={false};while(System.currentTimeMillis()<until){scenario.onActivity(a->removed[0]=find(a.getWindow().getDecorView(),"Zrobione na ten dzień: 1 · dostępne w panelu Zadania")!=null&&find(a.getWindow().getDecorView(),"Ćwiczenia z ułamków")==null);if(removed[0])break;Thread.sleep(30);}assertTrue(removed[0]);
            scenario.onActivity(a->NavigationTestSupport.open(a,"Kalendarz"));
            if(java.time.YearMonth.from(LocalDate.now().plusDays(1)).isAfter(java.time.YearMonth.now()))scenario.onActivity(a->NavigationTestSupport.button(a.getWindow().getDecorView(),"›").performClick());
            waitCheckbox(scenario,true);scenario.onActivity(a->checkbox(a.getWindow().getDecorView(),"Ćwiczenia z ułamków").performClick());waitCheckbox(scenario,false);
            scenario.onActivity(a->NavigationTestSupport.open(a,"Przegląd"));waitCheckbox(scenario,false);assertFalse(store.completed().contains(demoKey()));
        }finally{store.set(demoKey(),false,HomeworkStatusStore.epoch());}
    }
}
