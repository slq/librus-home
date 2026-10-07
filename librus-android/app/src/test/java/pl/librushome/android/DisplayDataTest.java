package pl.librushome.android;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;

public class DisplayDataTest {
    @Test public void dateOnlyRemainsDateOnly() {
        assertEquals("29.02.2028", DisplayData.date("2028-02-29"));
        assertEquals(LocalDate.of(2028, 2, 29), DisplayData.day("2028-02-29"));
    }
    @Test public void unknownDatesStayVisible() {
        assertEquals("Brak daty", DisplayData.date(""));
        assertEquals("nieznana data", DisplayData.date("nieznana data"));
        assertNull(DisplayData.day("nieznana data"));
    }
    @Test public void localTimetableTimeIsShown() {
        assertEquals("07.10.2026 · 08:55", DisplayData.date("2026-10-07T08:55:00"));
    }
}
