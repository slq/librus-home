package pl.librushome.android;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public class BackgroundOptionsUiTest {
    private View find(View root,String text) {
        if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View value=find(((ViewGroup)root).getChildAt(i),text);if(value!=null)return value;}
        return null;
    }
    private Spinner spinner(View root) {
        if(root instanceof Spinner&&root.getContentDescription()!=null&&"Interwał pobierania w tle".contentEquals(root.getContentDescription()))return (Spinner)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){Spinner value=spinner(((ViewGroup)root).getChildAt(i));if(value!=null)return value;}
        return null;
    }
    @Test public void intervalAndNightSettingsSaveAndSurviveActivityRecreation()throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        int before=BackgroundSync.intervalMinutes(c);boolean night=BackgroundSync.nightEnabled(c);
        BackgroundSync.intervalMinutes(c,15);BackgroundSync.nightEnabled(c,true);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(c,MainActivity.class).putExtra("demo",true))) {
            long deadline=System.currentTimeMillis()+15000;
            while(System.currentTimeMillis()<deadline) {
                android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if(root!=null&&!root.findAccessibilityNodeInfosByText("DEMO ·").isEmpty())break;
                Thread.sleep(30);
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{NavigationTestSupport.open(a,"Ustawienia");});
            scenario.onActivity(a->{Spinner options=spinner(a.getWindow().getDecorView());assertNotNull(options);assertEquals("15 minut",options.getSelectedItem());options.setSelection(2);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(60,BackgroundSync.intervalMinutes(c));
            scenario.onActivity(a->{Switch option=(Switch)find(a.getWindow().getDecorView(),"Przerwa nocna 20:00–07:00");assertTrue(option.isChecked());option.performClick();});
            assertFalse(BackgroundSync.nightEnabled(c));assertFalse(BackgroundSync.enabled(c));
            scenario.recreate();InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{assertEquals("60 minut",spinner(a.getWindow().getDecorView()).getSelectedItem());assertFalse(((Switch)find(a.getWindow().getDecorView(),"Przerwa nocna 20:00–07:00")).isChecked());});
        } finally {BackgroundSync.enabled(c,false);BackgroundSync.intervalMinutes(c,before);BackgroundSync.nightEnabled(c,night);}
    }
}
