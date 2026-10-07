package pl.librushome.android;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Date-only school events are not converted into midnight appointments. */
public final class DisplayData {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("pl"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm", Locale.forLanguageTag("pl"));
    private DisplayData() { }
    public static LocalDate day(String value) {
        try { return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate(); }
        catch (Exception ignored) { }
        try { return LocalDate.parse(value.substring(0, 10)); }
        catch (Exception ignored) { return null; }
    }
    public static String date(String value) {
        if (value == null || value.trim().isEmpty()) return "Brak daty";
        try { return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).format(TIME); }
        catch (Exception ignored) { }
        try { return LocalDateTime.parse(value.replace(' ', 'T')).format(TIME); }
        catch (Exception ignored) { }
        LocalDate day = day(value);
        return day == null ? value : day.format(DATE);
    }
}
