package pl.librushome.android;

import java.time.*;

/** Event-relative shortcuts preserve date-only deadlines and reject DST gaps. */
public final class ReminderTimes {
    private ReminderTimes() { }
    public static long beforeEvent(String value, boolean previousEvening, ZoneId zone, long now) {
        LocalDate day; ZonedDateTime event = null;
        try {
            try { event = OffsetDateTime.parse(value).atZoneSameInstant(zone); }
            catch (java.time.format.DateTimeParseException ignored) {
                if (value.length() > 10) {
                    LocalDateTime local = LocalDateTime.parse(value.replace(' ', 'T'));
                    event = Instant.ofEpochMilli(ReminderStore.localTime(local.toLocalDate(), local.toLocalTime(), zone)).atZone(zone);
                }
            }
            day = event == null ? LocalDate.parse(value) : event.toLocalDate();
        } catch (Exception invalid) { throw new IllegalArgumentException("Wpis nie ma poprawnego terminu."); }
        long due;
        if (previousEvening) due = ReminderStore.localTime(day.minusDays(1), LocalTime.of(18, 0), zone);
        else {
            if (event == null) throw new IllegalArgumentException("Termin nie zawiera godziny. Wybierz ją samodzielnie.");
            due = event.minusHours(1).toInstant().toEpochMilli();
        }
        if (due <= now) throw new IllegalArgumentException("Ten skrót wskazuje termin, który już minął. Wybierz inną datę.");
        return due;
    }
    public static boolean hasTime(String value) {
        try { OffsetDateTime.parse(value); return true; } catch (Exception ignored) { }
        try { LocalDateTime.parse(value.replace(' ', 'T')); return true; } catch (Exception ignored) { return false; }
    }
}
