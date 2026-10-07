package pl.librushome.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class SyncPolicyTest {
    @Test public void focusAndRestartKeepFiveMinuteBoundary() {
        long last=1_000_000;
        assertEquals(300000,SyncPolicy.remaining(last,last));
        assertEquals(1,SyncPolicy.remaining(last+299999,last));
        assertEquals(0,SyncPolicy.remaining(last+300000,last));
        assertEquals(0,SyncPolicy.remaining(last+600000,last));
    }
    @Test public void firstUseAndClockRollbackHaveBoundedWait() {
        assertEquals(0,SyncPolicy.remaining(1_000_000,0));
        assertEquals(300000,SyncPolicy.remaining(1_000_000,2_000_000));
    }
}
