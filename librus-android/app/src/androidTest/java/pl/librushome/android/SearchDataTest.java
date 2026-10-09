package pl.librushome.android;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Synthetic data only; cross-source and sorting tests against Android's actual JSON implementation. */
public class SearchDataTest {
    @Test public void sevenSourcesSearchAllFieldsDatesAndWordsWithoutMatchingUnknownMetadata()throws Exception {
        JSONObject sections=new JSONObject();
        for(String kind:SearchData.sources())if(!kind.equals("reminders"))sections.put(kind,new JSONArray().put(
                new JSONObject().put("id","same-id").put("title","SYNTHETIC "+kind).put("subtitle","Matematyka · Żółć")
                .put("details","Opis: konkurs").put("when","2026-10-07")).put("malformed"));
        sections.put("credentials",new JSONArray().put(new JSONObject().put("title","SECRET_MATCH")));
        String before=sections.toString();
        List<SearchData.Result> all=SearchData.find(sections,Collections.emptyList(),"zolc konkurs","all",0);
        assertEquals(7,all.size());Set<String> kinds=new HashSet<>();for(var row:all)kinds.add(row.kind);assertEquals(7,kinds.size());
        assertEquals(1,SearchData.find(sections,Collections.emptyList(),"07.10.2026","messages",0).size());
        assertEquals(7,SearchData.find(sections,Collections.emptyList(),"2026-10-07","all",0).size());
        assertEquals(0,SearchData.find(sections,Collections.emptyList(),"SECRET_MATCH","all",0).size());
        assertEquals(0,SearchData.find(sections,Collections.emptyList()," ","all",0).size());
        assertEquals(0,SearchData.find(sections,Collections.emptyList(),"matematyka missing","all",0).size());
        assertEquals(before,sections.toString());
    }
    @Test public void rankingSortingAndPrivateReminderHistoryKeepSourceIdentity()throws Exception {
        JSONObject title=new JSONObject().put("id","m1").put("title","Konkurs").put("when","2026-10-07");
        JSONObject body=new JSONObject().put("id","m2").put("title","Zebranie").put("details","konkurs").put("when","2026-10-09");
        JSONObject sections=new JSONObject().put("messages",new JSONArray().put(body).put(title));
        JSONObject own=new JSONObject().put("id","r1").put("profile","mine").put("demo",false).put("note","Konkurs · przypomnienie")
                .put("title","SYNTHETIC_SOURCE").put("due",1791540000000L).put("status","fired");
        JSONArray saved=new JSONArray().put(own).put(new JSONObject(own.toString()).put("profile","other").put("note","FOREIGN_NOTE"))
                .put(new JSONObject(own.toString()).put("demo",true).put("note","DEMO_NOTE"));
        var reminders=CalendarData.combine(Collections.emptyList(),Collections.emptyList(),saved,"mine",false);
        assertEquals(3,SearchData.find(sections,reminders,"konkurs","all",0).size());
        assertEquals("m1",SearchData.find(sections,reminders,"konkurs","messages",0).get(0).item.getString("id"));
        assertEquals("m2",SearchData.find(sections,reminders,"konkurs","messages",1).get(0).item.getString("id"));
        assertEquals("m1",SearchData.find(sections,reminders,"konkurs","messages",2).get(0).item.getString("id"));
        assertEquals("m1",SearchData.find(sections,reminders,"konkurs","messages",3).get(0).item.getString("id"));
        assertEquals("r1",SearchData.find(sections,reminders,"przypomnienie","reminders",0).get(0).item.getString("reminder_id"));
        assertEquals(0,SearchData.find(sections,reminders,"FOREIGN_NOTE","all",0).size());
        assertEquals(0,SearchData.find(sections,reminders,"DEMO_NOTE","all",0).size());
    }
}
