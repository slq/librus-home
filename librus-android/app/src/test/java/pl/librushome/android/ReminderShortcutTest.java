package pl.librushome.android;
import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReminderShortcutTest {
    private final ZoneId zone=ZoneId.of("Europe/Warsaw");
    private final long now=Instant.parse("2026-10-07T10:00:00Z").toEpochMilli();
    @Test public void dateOnlyDeadlineUsesPreviousEveningWithoutInventingAnHour() {
        assertEquals(Instant.parse("2026-10-08T16:00:00Z").toEpochMilli(),ReminderTimes.beforeEvent("2026-10-09",true,zone,now));
        assertFalse(ReminderTimes.hasTime("2026-10-09"));
        try {ReminderTimes.beforeEvent("2026-10-09",false,zone,now);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void timedEventAndOffsetUseThePhonesLocalTime() {
        assertEquals(Instant.parse("2026-10-09T05:00:00Z").toEpochMilli(),ReminderTimes.beforeEvent("2026-10-09T08:00:00",false,zone,now));
        assertEquals(Instant.parse("2026-10-08T16:00:00Z").toEpochMilli(),ReminderTimes.beforeEvent("2026-10-09T00:30:00+00:00",true,zone,now));
        assertTrue(ReminderTimes.hasTime("2026-10-09 08:00"));
    }
    @Test public void pastInvalidDatesAndDstGapsCannotBeScheduledByShortcut() {
        for(String value:new String[]{"2026-10-07","2026-02-30","2027-03-28T02:30:00","unknown"}) {
            try {ReminderTimes.beforeEvent(value,true,zone,now);fail(value);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void oneHourBeforeAutumnOverlapUsesElapsedTime() {
        assertEquals(Instant.parse("2026-10-24T23:30:00Z").toEpochMilli(),ReminderTimes.beforeEvent("2026-10-25T02:30:00",false,zone,now));
    }
}
