package pl.librushome.android;
import android.content.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class UpdateUiTest {
    @Test public void settingsShowsNotesChecksDownloadsAndKeepsControlsAfterRecreation()throws Exception{
        HomeworkCompletionTest.blank();File file=UpdateTestSupport.asset("update.apk");UpdateManager m=UpdateManager.get(UpdateTestSupport.context());UpdateTestSupport.idle(m);m.automatic(false);UpdateTransport original=UpdateManager.transport;UpdateManager.transport=UpdateTestSupport.fake(UpdateTestSupport.release(file),file);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(UpdateTestSupport.context(),MainActivity.class).putExtra("demo",true))){
            HomeworkCompletionTest.ready(scenario);scenario.onActivity(a->{NavigationTestSupport.open(a,"Ustawienia");NavigationTestSupport.button(a.getWindow().getDecorView(),"Sprawdź aktualizacje").performClick();});UpdateTestSupport.idle(m);
            scenario.onActivity(a->{assertNotNull(HomeworkCompletionTest.find(a.getWindow().getDecorView(),"Wersja 0.12.1 · "+Math.round(file.length()/1024.0/1024.0)+" MiB\n\nSYNTHETIC_RELEASE_NOTES"));NavigationTestSupport.button(a.getWindow().getDecorView(),"Pobierz aktualizację").performClick();});UpdateTestSupport.idle(m);assertTrue(m.ready);
            scenario.recreate();scenario.onActivity(a->{assertNotNull(NavigationTestSupport.button(a.getWindow().getDecorView(),"Zainstaluj aktualizację"));assertEquals(android.view.View.VISIBLE,NavigationTestSupport.button(a.getWindow().getDecorView(),"Zainstaluj aktualizację").getVisibility());});
        }finally{m.release=null;m.ready=false;m.apk().delete();m.prefs().edit().clear().apply();UpdateManager.transport=original;}
    }
}
