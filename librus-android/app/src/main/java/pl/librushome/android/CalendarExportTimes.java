package pl.librushome.android;

import java.time.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Calendar dates independent of the provider; date-only entries use UTC calendar boundaries. */
public final class CalendarExportTimes {
    public record Range(long start,long end,boolean allDay,String zone) { }
    public static Range event(String when,String details,ZoneId zone) {
        if(when==null || when.isBlank())throw new IllegalArgumentException("Brak terminu");
        if(when.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate day=LocalDate.parse(when);
            Matcher hour=Pattern.compile("(?:^|\\n)Godzina: (\\d{1,2}:\\d{2})(?:\\s|$)").matcher(details==null?"":details);
            if(hour.find())return timed(day.atTime(LocalTime.parse(hour.group(1),java.time.format.DateTimeFormatter.ofPattern("H:mm"))),zone,3600000);
            return new Range(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),true,"UTC");
        }
        try {long start=OffsetDateTime.parse(when).toInstant().toEpochMilli();return new Range(start,Math.addExact(start,3600000),false,zone.getId());}
        catch(java.time.format.DateTimeParseException ignored) {return timed(LocalDateTime.parse(when.replace(' ','T')),zone,3600000);}
    }
    private static Range timed(LocalDateTime at,ZoneId zone,long duration) {
        var offsets=zone.getRules().getValidOffsets(at);
        if(offsets.isEmpty())throw new IllegalArgumentException("Nieistniejąca godzina");
        long start=at.toInstant(offsets.get(0)).toEpochMilli();return new Range(start,Math.addExact(start,duration),false,zone.getId());
    }
    public static Range reminder(long due,ZoneId zone) {return new Range(due,Math.addExact(due,900000),false,zone.getId());}
    public static String hash(String value) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static String key(String profile,String kind,String id) {return "librusapp://export/"+hash(profile)+"/"+kind+"/"+hash(id);}
    private CalendarExportTimes() { }
}