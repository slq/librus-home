package pl.librushome.android;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.json.JSONObject;

/** A local day view of cached entries. It never fetches or changes school data. */
public final class HomeDayData {
    private HomeDayData() { }
    static LocalTime time(String value) {
        try { return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime(); }
        catch(Exception ignored) { }
        try { return LocalDateTime.parse(value.replace(' ','T')).toLocalTime(); }
        catch(Exception ignored) { return null; }
    }
    public static String timeLabel(String value) {
        LocalTime time=time(value); return time==null?"Cały dzień / bez podanej godziny":time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
    public static List<JSONObject> onDay(List<JSONObject> source,LocalDate day,boolean pendingReminders) {
        List<JSONObject> result=new ArrayList<>();
        for(JSONObject item:source) {
            if(!day.equals(DisplayData.day(item.optString("when"))))continue;
            if(pendingReminders && !item.optString("details").equals("Zaplanowane"))continue;
            result.add(item);
        }
        result.sort(Comparator.comparing((JSONObject item)->time(item.optString("when")),Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(item->item.optString("title")));
        return result;
    }
    public static int unknownDates(List<JSONObject> source) {
        int count=0;for(JSONObject item:source)if(DisplayData.day(item.optString("when"))==null)count++;return count;
    }
    public static boolean outsideTimetableRange(LocalDate day,String fetched) {
        LocalDate read=DisplayData.day(fetched);if(read==null)return false;
        LocalDate monday=read.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return day.isBefore(monday)||day.isAfter(monday.plusDays(13));
    }
}
