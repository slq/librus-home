package pl.librushome.android;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
public class ReminderTimeTest {
    @Test public void springDstGapCannotSilentlyMoveTheReminder() {
        try { ReminderStore.localTime(LocalDate.of(2027,3,28),LocalTime.of(2,30),ZoneId.of("Europe/Warsaw")); fail("DST gap accepted"); }
        catch(IllegalArgumentException expected) { }
    }
    @Test public void autumnOverlapSelectsTheFirstOccurrenceExplicitly() {
        long at=ReminderStore.localTime(LocalDate.of(2026,10,25),LocalTime.of(2,30),ZoneId.of("Europe/Warsaw"));
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(),at);
    }
}
