package pl.librushome.android;
import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CalendarExportTimesTest {
    @Test public void dateOnlyUsesUtcWholeDayAcrossDst() {
        var range=CalendarExportTimes.event("2026-10-25","",ZoneId.of("Europe/Warsaw"));
        assertTrue(range.allDay());assertEquals("UTC",range.zone());assertEquals(86400000,range.end()-range.start());assertEquals(Instant.parse("2026-10-25T00:00:00Z").toEpochMilli(),range.start());
    }
    @Test public void explicitSchoolHourIsLocalButUnknownHoursRemainAllDay() {
        var zone=ZoneId.of("Europe/Warsaw");var timed=CalendarExportTimes.event("2026-10-09","Opis\nGodzina: 8:30",zone);
        assertFalse(timed.allDay());assertEquals(Instant.parse("2026-10-09T06:30:00Z").toEpochMilli(),timed.start());assertEquals(3600000,timed.end()-timed.start());assertTrue(CalendarExportTimes.event("2026-10-09","Godzina: unknown",zone).allDay());
    }
    @Test public void offsetsAndReminderDurationsPreserveTheInstant() {
        var zone=ZoneId.of("America/New_York");long due=Instant.parse("2026-10-09T08:00:00Z").toEpochMilli();
        assertEquals(due,CalendarExportTimes.event("2026-10-09T10:00:00+02:00","",zone).start());assertEquals(900000,CalendarExportTimes.reminder(due,zone).end()-due);
    }
    @Test public void invalidDateOrNonexistentLocalHourIsRejected() {
        for(String date:new String[]{"bad","2026-02-30","2027-03-28T02:30:00"})try{CalendarExportTimes.event(date,"",ZoneId.of("Europe/Warsaw"));fail(date);}catch(java.time.DateTimeException|IllegalArgumentException expected){}
    }
    @Test public void stableIdentityDoesNotContainTitlesAndSeparatesProfilesAndKinds() {
        String key=CalendarExportTimes.key("profile-a","homework","17");assertEquals(key,CalendarExportTimes.key("profile-a","homework","17"));assertNotEquals(key,CalendarExportTimes.key("profile-b","homework","17"));assertNotEquals(key,CalendarExportTimes.key("profile-a","schedule","17"));assertFalse(key.contains("profile-a"));
    }
}