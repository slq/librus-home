package pl.librushome.android;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import static org.junit.Assert.*;

/** Run Seed, force-stop the blank emulator app, then Verify in a new instrumentation process. */
public class HomeworkCompletionRestartTest {
    @Test public void seed()throws Exception {
        HomeworkCompletionTest.blank();HomeworkStatusStore store=new HomeworkStatusStore(HomeworkCompletionTest.context());
        store.set(HomeworkCompletionTest.demoKey(),true,HomeworkStatusStore.epoch());assertTrue(store.completed().contains(HomeworkCompletionTest.demoKey()));
    }
    @Test public void verify()throws Exception {
        HomeworkCompletionTest.blank();HomeworkStatusStore store=new HomeworkStatusStore(HomeworkCompletionTest.context());assertTrue(store.completed().contains(HomeworkCompletionTest.demoKey()));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(HomeworkCompletionTest.context(),MainActivity.class).putExtra("demo",true))) {
            HomeworkCompletionTest.ready(scenario);scenario.onActivity(a->NavigationTestSupport.open(a,"Zadania domowe"));
            HomeworkCompletionTest.waitAbsent(scenario);scenario.onActivity(a->NavigationTestSupport.button(a.getWindow().getDecorView(),"Zrobione").performClick());HomeworkCompletionTest.waitCheckbox(scenario,true);
        }finally{store.set(HomeworkCompletionTest.demoKey(),false,HomeworkStatusStore.epoch());}
    }
}
