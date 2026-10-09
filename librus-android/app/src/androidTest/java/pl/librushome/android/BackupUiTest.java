package pl.librushome.android;
import android.app.*;
import android.content.*;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Real settings controls, SAF intents, preview, cancellation and confirmed replacement. */
public class BackupUiTest {
    private static BackupUi ui(MainActivity a){try{Field f=MainActivity.class.getDeclaredField("backupUi");f.setAccessible(true);return (BackupUi)f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private static AlertDialog dialog(MainActivity a){try{Field f=BackupUi.class.getDeclaredField("confirmation");f.setAccessible(true);return (AlertDialog)f.get(ui(a));}catch(Exception e){throw new AssertionError(e);}}
    private static void ready(ActivityScenario<MainActivity> scenario)throws Exception {
        long until=System.currentTimeMillis()+20000;boolean[] ready={false};while(System.currentTimeMillis()<until){scenario.onActivity(a->{var b=NavigationTestSupport.button(a.getWindow().getDecorView(),"Eksportuj kopię JSON");ready[0]=b!=null&&b.isEnabled();});if(ready[0])return;Thread.sleep(40);}fail("Backup settings not ready");
    }
    private static void preview(ActivityScenario<MainActivity> scenario)throws Exception {
        long until=System.currentTimeMillis()+10000;boolean[] ready={false};while(System.currentTimeMillis()<until){scenario.onActivity(a->ready[0]=dialog(a)!=null&&dialog(a).isShowing());if(ready[0])return;Thread.sleep(40);}fail("Import preview not shown");
    }
    private static Instrumentation.ActivityMonitor picker(String action,File file)throws Exception {
        IntentFilter filter=new IntentFilter(action);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("*/*");
        return InstrumentationRegistry.getInstrumentation().addMonitor(filter,new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file))),true);
    }
    @Test public void settingsExportPreviewCancelAndImportWorkThroughDocumentPickerResults()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();JSONObject before=BackupStore.before(c);File output=new File(c.getCacheDir(),"synthetic-backup-ui.json");
        try {
            BackupTestSupport.seed();JSONObject stored=new JSONObject(new SecureStore(c).read());stored.put("credentials",JSONObject.NULL);new SecureStore(c).write(stored.toString());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(c,MainActivity.class).setData(Uri.parse("librusapp://updates")))){
                ready(scenario);var monitor=picker(Intent.ACTION_CREATE_DOCUMENT,output);
                try{scenario.onActivity(a->assertTrue(NavigationTestSupport.button(a.getWindow().getDecorView(),"Eksportuj kopię JSON").performClick()));ready(scenario);assertTrue(monitor.getHits()>0);}finally{InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);}
                assertTrue(output.isFile());BackupDocument doc;try(InputStream in=new FileInputStream(output)){doc=BackupDocument.read(in);}assertFalse(new String(doc.bytes(),java.nio.charset.StandardCharsets.UTF_8).contains("credentials"));
                String snapshot=new SecureStore(c).read();monitor=picker(Intent.ACTION_OPEN_DOCUMENT,output);
                try{scenario.onActivity(a->NavigationTestSupport.button(a.getWindow().getDecorView(),"Importuj kopię JSON").performClick());preview(scenario);assertTrue(monitor.getHits()>0);scenario.onActivity(a->dialog(a).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());assertEquals(snapshot,new SecureStore(c).read());}
                finally{InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);}
                ready(scenario);monitor=picker(Intent.ACTION_OPEN_DOCUMENT,output);
                try{scenario.onActivity(a->NavigationTestSupport.button(a.getWindow().getDecorView(),"Importuj kopię JSON").performClick());preview(scenario);scenario.onActivity(a->dialog(a).getButton(AlertDialog.BUTTON_POSITIVE).performClick());ready(scenario);
                    assertTrue(new JSONObject(new SecureStore(c).read()).isNull("credentials"));assertFalse(GoogleCalendarSync.enabled(c));assertEquals(60,BackgroundSync.intervalMinutes(c));
                    scenario.onActivity(a->{assertFalse(ui(a).importing);assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Kopia danych i ustawień"));});
                }finally{InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);}
            }
        }finally{BackupTestSupport.clean(before);output.delete();}
    }
}
