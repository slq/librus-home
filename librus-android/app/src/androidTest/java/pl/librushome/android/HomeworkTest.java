package pl.librushome.android;

import android.app.Notification;
import android.content.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Demo-only end-to-end homework panel, explicit body and reminder/source navigation. */
public class HomeworkTest {
    private static final String NOTE="SYNTHETIC_HOMEWORK_UI_REMINDER";
    private android.app.Instrumentation instrumentation(){return InstrumentationRegistry.getInstrumentation();}
    private android.content.Context context(){return instrumentation().getTargetContext();}
    private View find(View view,String value,boolean button){
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(value) && (!button || view instanceof Button))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=find(((ViewGroup)view).getChildAt(i),value,button);if(result!=null)return result;}
        return null;
    }
    private AccessibilityNodeInfo root(){return instrumentation().getUiAutomation().getRootInActiveWindow();}
    private void waitText(String value)throws Exception{
        long deadline=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<deadline){AccessibilityNodeInfo root=root();if(root!=null&&!root.findAccessibilityNodeInfosByText(value).isEmpty())return;Thread.sleep(50);}
        fail("Missing active text: "+value);
    }
    private void click(String value)throws Exception{
        waitText(value);
        for(AccessibilityNodeInfo node:root().findAccessibilityNodeInfosByText(value)){
            while(node!=null){if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;node=node.getParent();}
        }
        fail("No clickable text: "+value);
    }
    private void enterNote(AccessibilityNodeInfo node){
        if(node.isEditable()){
            android.os.Bundle args=new android.os.Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,NOTE);
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));return;
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){if(child.isEditable()){enterNote(child);return;}enterNote(child);}}
    }
    private void cleanup()throws Exception{
        ReminderStore store=new ReminderStore(context());JSONArray rows=store.list();
        for(int i=0;i<rows.length();i++){JSONObject item=rows.getJSONObject(i);if(item.optBoolean("demo")&&item.optString("note").equals(NOTE)){store.delete(item.getString("id"));ReminderAlarms.cancel(context(),item.getString("id"));}}
    }
    @Test public void homeworkPanelReadsBodySavesReminderAndReturnsToSource()throws Exception{
        cleanup();
        Intent intent=new Intent(context(),MainActivity.class).putExtra("demo",true);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(intent)){
            waitText("DEMO ·");
            waitText("Nieprzeczytane wiadomości");
            scenario.onActivity(a->{View tab=find(a.getWindow().getDecorView(),"Zadania domowe",true);assertNotNull(tab);tab.performClick();});
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(find(root,"Ćwiczenia z ułamków",false));assertNull(find(root,"Powtórka słownictwa",false));View all=find(root,"Wszystkie",true);assertNotNull(all);all.performClick();assertNotNull(find(root,"Powtórka słownictwa",false));assertNotNull(find(root,"Termin minął",false));});
            scenario.onActivity(a->{View label=find(a.getWindow().getDecorView(),"Ćwiczenia z ułamków",false);assertNotNull(label);assertTrue(((View)label.getParent()).performClick());});
            click("Pobierz treść zadania");waitText("Przykładowa treść zadania");
            click("Przypomnij mi…");waitText("Nowe przypomnienie");enterNote(root());click("Zapisz");
            long deadline=System.currentTimeMillis()+5000;JSONObject reminder=null;
            while(System.currentTimeMillis()<deadline&&reminder==null){JSONArray rows=new ReminderStore(context()).list();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("note").equals(NOTE))reminder=rows.getJSONObject(i);if(reminder==null)Thread.sleep(50);}
            assertNotNull("Homework reminder not saved",reminder);assertEquals("homework",reminder.getString("kind"));assertEquals("demo:homework:h1",reminder.getString("source_id"));
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();Button reminders=null;for(Button b:buttons(root))if(b.getText().toString().startsWith("Przypomnienia"))reminders=b;assertNotNull(reminders);reminders.performClick();});
            click(NOTE);waitText("Twoje przypomnienie");click("Pokaż wpis");waitText("Ćwiczenia z ułamków");waitText("Pobierz treść zadania");click("Zamknij");
        }finally{cleanup();}
    }
    private java.util.List<Button> buttons(View root){java.util.List<Button> result=new java.util.ArrayList<>();if(root instanceof Button)result.add((Button)root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)result.addAll(buttons(((ViewGroup)root).getChildAt(i)));return result;}
    @Test public void homeworkChangeNotificationContainsCountOnly()throws Exception{
        try{
            assertEquals("sent",NotificationHub.changes(context(),new JSONObject().put("homework",2).put("title","SYNTHETIC_PRIVATE_TASK")));
            long deadline=System.currentTimeMillis()+3000;Notification found=null;
            while(System.currentTimeMillis()<deadline&&found==null){for(android.service.notification.StatusBarNotification n:NotificationHub.manager(context()).getActiveNotifications())if(n.getTag().equals("changes"))found=n.getNotification();if(found==null)Thread.sleep(50);}
            assertNotNull(found);assertEquals("Zadania domowe: 2. Otwórz aplikację.",found.extras.getCharSequence(Notification.EXTRA_TEXT).toString());assertFalse(found.extras.toString().contains("SYNTHETIC_PRIVATE_TASK"));
        }finally{NotificationHub.manager(context()).cancel("changes",1);}
    }
}