package pl.librushome.android;

import android.app.Notification;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.app.Activity;
import android.view.KeyEvent;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Black-box notification taps: synthetic reminders only, no school connection. */
public class NotificationNavigationTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private Intent demo() { return new Intent(context(),MainActivity.class).putExtra("demo",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK); }
    private MainActivity launchDemo() {
        return (MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(demo());
    }
    private void home() {
        android.app.UiAutomation ui=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ui.injectInputEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME),true);
        ui.injectInputEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_HOME),true);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void finishActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            java.util.Set<Activity> activities=new java.util.HashSet<>();
            for(Stage stage:new Stage[]{Stage.RESUMED,Stage.PAUSED,Stage.STARTED,Stage.STOPPED,Stage.CREATED})
                activities.addAll(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage));
            for(Activity activity:activities)if(activity instanceof MainActivity)activity.finish();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private Button button(View view,String prefix) {
        if(view instanceof Button && ((Button)view).getText().toString().startsWith(prefix))return (Button)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Button b=button(((ViewGroup)view).getChildAt(i),prefix);if(b!=null)return b;}
        return null;
    }
    private boolean activeText(String value) {
        android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
        return root!=null&&!root.findAccessibilityNodeInfosByText(value).isEmpty();
    }
    private void waitForText(String text) throws Exception {
        long deadline=System.currentTimeMillis()+10000;
        while(System.currentTimeMillis()<deadline){if(activeText(text))return;Thread.sleep(50);}
        fail("Missing active-window text: "+text);
    }
    private Notification notification(String tag) throws Exception {
        long deadline=System.currentTimeMillis()+3000;
        while(System.currentTimeMillis()<deadline){
            for(android.service.notification.StatusBarNotification n:NotificationHub.manager(context()).getActiveNotifications())if(tag.equals(n.getTag()))return n.getNotification();
            Thread.sleep(50);
        }
        throw new AssertionError("Notification not published: "+tag);
    }
    @Test public void tappingReminderReloadsFiredStatusFromStoppedActivity() throws Exception {
        ReminderStore store=new ReminderStore(context());long now=System.currentTimeMillis();
        JSONObject item=store.save(null,"messages","synthetic-tap-status-source","demo",true,"Synthetic source","SYNTHETIC_STALE_TAP_NOTE",now+60000,false,now);
        String id=item.getString("id");
        MainActivity activity=launchDemo();
        try {
            waitForText("Nieprzeczytane wiadomości");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{Button b=button(activity.getWindow().getDecorView(),"Przypomnienia");assertNotNull(b);b.performClick();});
            waitForText("SYNTHETIC_STALE_TAP_NOTE");waitForText("Zaplanowane");
            home();
            assertNotNull(store.claim(id,now+60000));store.delivered(id,now+60000,"sent");
            assertEquals("sent",NotificationHub.reminder(context(),store.get(id)));
            notification(id).contentIntent.send();
            waitForText("Twoje przypomnienie");waitForText("Przekazane do Androida");
            assertFalse("Displayed a stale pending status",activeText("Zaplanowane"));
        } finally {finishActivities();store.delete(id);ReminderAlarms.cancel(context(),id);}
    }
    @Test public void tappingChangesReturnsToOverviewFromAnotherPanel() throws Exception {
        MainActivity activity=launchDemo();
        try {
            waitForText("Nieprzeczytane wiadomości");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{Button b=button(activity.getWindow().getDecorView(),"Ustawienia");assertNotNull(b);b.performClick();});
            waitForText("Powiadomienia i alarmy");
            assertEquals("sent",NotificationHub.changes(context(),new JSONObject().put("grades",1)));
            notification("changes").contentIntent.send();
            waitForText("Nieprzeczytane wiadomości");
        } finally {finishActivities();NotificationHub.manager(context()).cancel("changes",1);}
    }
}
