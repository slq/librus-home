package pl.librushome.android;
import android.content.Context;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.Python;
import java.time.LocalTime;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Run separately on a blank emulator: real repository's night preflight before cold startup. */
public class BackgroundNightTest {
    @Test public void nightJobSkipsPythonAndLoginAndResumesAtSeven()throws Exception {
        assertTrue(Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String raw=new SecureStore(c).read();
        assertTrue("Use a blank emulator",raw.isEmpty()||(new JSONObject(raw).isNull("credentials")&&new JSONObject(raw).optString("profile").isEmpty()));
        assertFalse("Run this test in a separate instrumentation process",Python.isStarted());
        boolean night=BackgroundSync.nightEnabled(c);java.util.function.Supplier<LocalTime> clock=BackgroundSync.localTime;
        long last=c.getSharedPreferences("sync_clock",Context.MODE_PRIVATE).getLong("last_attempt",0);
        try {
            BackgroundSync.nightEnabled(c,true);BackgroundSync.enabled(c,true);BackgroundSync.localTime=()->LocalTime.of(20,0);
            CountDownLatch completed=new CountDownLatch(1);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->MobileRepository.get(c).backgroundRefresh(completed::countDown));
            assertTrue(completed.await(5,TimeUnit.SECONDS));assertFalse("Night must not start Python",Python.isStarted());
            assertEquals(last,c.getSharedPreferences("sync_clock",Context.MODE_PRIVATE).getLong("last_attempt",0));
            assertTrue("Night pause must preserve the enabled schedule",BackgroundSync.enabled(c));
            BackgroundSync.localTime=()->LocalTime.of(7,0);CountDownLatch morning=new CountDownLatch(1);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->MobileRepository.get(c).backgroundRefresh(morning::countDown));
            assertTrue(morning.await(20,TimeUnit.SECONDS));assertTrue(Python.isStarted());
            assertFalse("Morning detects no account and safely stops the fixture",BackgroundSync.enabled(c));
            assertEquals(raw,new SecureStore(c).read());
        } finally {BackgroundSync.localTime=clock;BackgroundSync.nightEnabled(c,night);BackgroundSync.enabled(c,false);}
    }
}
