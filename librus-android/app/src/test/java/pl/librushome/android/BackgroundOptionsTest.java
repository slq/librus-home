package pl.librushome.android;
import java.time.LocalTime;
import org.junit.Test;
import static org.junit.Assert.*;
public class BackgroundOptionsTest {
    @Test public void nightPauseIncludesTwentyAndExcludesSevenAcrossMidnight() {
        for(String time:new String[]{"20:00","23:59:59","00:00","06:59:59"})assertTrue(time,BackgroundSync.nightTime(LocalTime.parse(time)));
        for(String time:new String[]{"07:00","12:00","19:59:59"})assertFalse(time,BackgroundSync.nightTime(LocalTime.parse(time)));
    }
    @Test public void supportedIntervalsCannotFallBelowAndroidsMinimum() {
        assertEquals(15,BackgroundSync.DEFAULT_MINUTES);
        for(int value:new int[]{15,30,60})assertTrue(BackgroundSync.validInterval(value));
        for(int value:new int[]{-1,0,5,14,16,90})assertFalse(BackgroundSync.validInterval(value));
    }
}
