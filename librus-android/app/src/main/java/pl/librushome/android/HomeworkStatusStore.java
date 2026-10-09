package pl.librushome.android;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Local completion metadata only. Kept separate from network snapshots and reminder alarms. */
public final class HomeworkStatusStore {
    static final Object LOCK=new Object();
    private static long epoch;
    private final SecureStore secure;
    public HomeworkStatusStore(Context context) { this(context,"homework-status.aes","LibrusApp.homework-status.v1"); }
    HomeworkStatusStore(Context context,String file,String alias) { secure=new SecureStore(context,file,alias); }
    public static long epoch() { synchronized(LOCK){return epoch;} }
    public static String key(String profile,boolean demo,String year,String id) {
        if(profile==null||profile.isEmpty()||id==null||id.isEmpty()||id.length()>512||year==null||!year.matches("[0-9]{4}/[0-9]{4}"))throw new IllegalArgumentException("Missing homework identity");
        try { return new JSONArray().put(profile).put(demo).put(year).put(id).toString(); }
        catch(Exception invalid){throw new IllegalArgumentException(invalid);}
    }
    private JSONObject read()throws Exception {
        String raw=secure.read();if(raw.isEmpty())return new JSONObject();
        JSONObject saved=new JSONObject(raw);
        if(saved.getInt("version")!=1||!(saved.opt("done") instanceof JSONObject))throw new IllegalStateException("Invalid homework statuses");
        JSONObject done=saved.getJSONObject("done");if(done.length()>10000)throw new IllegalStateException("Too many statuses");
        for(Iterator<String> keys=done.keys();keys.hasNext();) {
            String k=keys.next();JSONArray parts=new JSONArray(k);
            if(parts.length()!=4||!(parts.opt(0) instanceof String)||!(parts.opt(1) instanceof Boolean)||!(parts.opt(2) instanceof String)||!(parts.opt(3) instanceof String)
                    ||!key(parts.getString(0),parts.getBoolean(1),parts.getString(2),parts.getString(3)).equals(k)||!(done.opt(k) instanceof Number)||done.getLong(k)<=0)throw new IllegalStateException("Invalid homework status");
        }
        return done;
    }
    public Set<String> completed()throws Exception { synchronized(LOCK){
        Set<String> result=new HashSet<>();JSONObject done=read();for(Iterator<String> keys=done.keys();keys.hasNext();)result.add(keys.next());return Collections.unmodifiableSet(result);
    } }
    public void set(String key,boolean completed,long expectedEpoch)throws Exception { synchronized(LOCK){
        if(expectedEpoch!=epoch)throw new IllegalStateException("Local data were reset");
        JSONArray parts=new JSONArray(key);
        if(parts.length()!=4||!key(parts.getString(0),parts.getBoolean(1),parts.getString(2),parts.getString(3)).equals(key))throw new IllegalArgumentException("Invalid homework identity");
        JSONObject done=read();if(done.has(key)==completed)return;
        if(completed) {if(done.length()>=10000)throw new IllegalStateException("Too many completed tasks");done.put(key,System.currentTimeMillis());}
        else done.remove(key);
        secure.write(new JSONObject().put("version",1).put("done",done).toString());
    } }
    JSONObject backup()throws Exception { synchronized(LOCK){return new JSONObject(read().toString());} }
    void replace(JSONObject done)throws Exception { synchronized(LOCK){epoch++;secure.write(new JSONObject().put("version",1).put("done",done).toString());} }
    public void clear()throws Exception { synchronized(LOCK){epoch++;secure.clear();} }
}
