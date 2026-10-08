package pl.librushome.android;

import java.time.Instant;
import java.util.*;
import org.json.*;

/** A combined view only: never mutates or persists school entries or reminders. */
public final class CalendarData {
    private CalendarData() { }
    public static String label(String kind) {
        return kind.equals("schedule")?"Terminarz":kind.equals("homework")?"Zadania domowe":"Przypomnienia";
    }
    public static int color(String kind) {
        return kind.equals("schedule")?0xff087e8b:kind.equals("homework")?0xff915b13:0xff7352a3;
    }
    public static List<JSONObject> combine(List<JSONObject> events,List<JSONObject> tasks,JSONArray reminders,String profile,boolean demo) {
        List<JSONObject> result=new ArrayList<>();
        List<List<JSONObject>> sources=Arrays.asList(events,tasks);
        for(int index=0;index<sources.size();index++)for(JSONObject item:sources.get(index)) {
            try {result.add(new JSONObject(item.toString()).put("calendar_kind",index==0?"schedule":"homework"));}
            catch(JSONException invalid){throw new IllegalArgumentException("Invalid calendar entry",invalid);}
        }
        for(int i=0;i<reminders.length();i++) {
            JSONObject item=reminders.optJSONObject(i);
            if(item==null||item.optBoolean("demo")!=demo||!item.optString("profile").equals(profile)||!item.has("due"))continue;
            try {
                String status=item.optString("status").equals("pending")?"Zaplanowane":"Wykonane";
                result.add(new JSONObject().put("id","reminder:"+item.optString("id")).put("reminder_id",item.optString("id"))
                        .put("calendar_kind","reminders").put("title",item.optString("note"))
                        .put("subtitle",status+" · "+item.optString("title")).put("details",status)
                        .put("when",Instant.ofEpochMilli(item.optLong("due")).toString()));
            }catch(JSONException invalid){throw new IllegalArgumentException("Invalid reminder entry",invalid);}
        }
        return result;
    }
}
