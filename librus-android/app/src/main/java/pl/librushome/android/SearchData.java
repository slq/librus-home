package pl.librushome.android;

import java.util.*;
import org.json.*;

/** Searches only the current local view. No network, index files, or model mutations. */
public final class SearchData {
    private static final List<String> SOURCES = Collections.unmodifiableList(Arrays.asList(
            "grades", "messages", "announcements", "schedule", "homework", "attendance", "timetable", "reminders"));
    private SearchData() { }
    public static List<String> sources() { return SOURCES; }
    public static final class Result {
        public final String kind;
        public final JSONObject item;
        private final int rank;
        Result(String kind, JSONObject item, int rank) { this.kind=kind; this.item=item; this.rank=rank; }
    }
    public static List<Result> find(JSONObject sections, List<JSONObject> reminders, String query, String source, int sort) {
        List<Result> result = new ArrayList<>();
        String[] words = SearchText.words(query);
        if (words.length == 0) return result;
        for (String kind : SOURCES) {
            if (!source.equals("all") && !source.equals(kind)) continue;
            List<JSONObject> rows = new ArrayList<>();
            if (kind.equals("reminders")) rows.addAll(reminders);
            else {
                JSONArray data = sections.optJSONArray(kind);
                if (data != null) for (int i=0;i<data.length();i++) if (data.optJSONObject(i)!=null) rows.add(data.optJSONObject(i));
            }
            for (JSONObject item : rows) {
                String title=item.optString("title"), subtitle=item.optString("subtitle"), when=item.optString("when");
                String text=title+" "+subtitle+" "+when+" "+DisplayData.date(when)+" "+item.optString("details");
                if (SearchText.matches(words, text)) result.add(new Result(kind,item,
                        SearchText.matches(words,title)?0:SearchText.matches(words,subtitle)?1:2));
            }
        }
        Comparator<Result> date = Comparator.comparing(r -> r.item.optString("when"));
        Comparator<Result> title = Comparator.comparing(r -> SearchText.normalize(r.item.optString("title")));
        Comparator<Result> order = sort==1?date.reversed():sort==2?date:sort==3?title:
                Comparator.comparingInt((Result r)->r.rank).thenComparing(date.reversed());
        result.sort(order.thenComparing(title).thenComparing(r->r.kind).thenComparing(r->r.item.optString("id")));
        return result;
    }
}
