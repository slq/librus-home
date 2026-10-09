package pl.librushome.android;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Versioned, bounded portable JSON. Only user data/options are allowed, never authentication. */
final class BackupDocument {
    static final int MAX_BYTES = 32 * 1024 * 1024;
    final JSONObject root;
    BackupDocument(JSONObject value) throws Exception {
        root = new JSONObject(value.toString());
        keys(root,"format","schema","created_at","app_version","school","reminders","homework","settings");
        if(!"LibrusApp-backup".equals(root.getString("format")) || number(root,"schema")!=1) throw new IllegalArgumentException("Unsupported backup");
        number(root,"created_at"); string(root,"app_version",100);
        JSONObject school=root.getJSONObject("school"); // Python validates all nested school fields before import.
        keys(school,"version","profile","snapshot","change_journal","announcement_archives","retry_after");
        if(school.has("credentials"))throw new IllegalArgumentException("Authentication is not portable");
        JSONArray reminders=root.getJSONArray("reminders"); if(reminders.length()>1000)throw new IllegalArgumentException("Too many reminders");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<reminders.length();i++) {
            JSONObject item=reminders.getJSONObject(i);
            keys(item,"id","kind","source_id","profile","demo","title","note","due","show_text","status","delivery","fired_at","source_when");
            String id=string(item,"id",128); if(!UUID.fromString(id).toString().equals(id)||!ids.add(id))throw new IllegalArgumentException("Duplicate reminder");
            if(!Arrays.asList("messages","announcements","schedule","homework").contains(string(item,"kind",30))
                ||string(item,"source_id",512).isEmpty()||!string(item,"profile",64).matches("[a-f0-9]{64}")||bool(item,"demo"))throw new IllegalArgumentException("Invalid source");
            string(item,"title",1000000);String note=string(item,"note",800);if(note.trim().isEmpty()||note.codePointCount(0,note.length())>200)throw new IllegalArgumentException("Invalid reminder note");
            if(number(item,"due")==0)throw new IllegalArgumentException("Invalid due date");bool(item,"show_text");
            if(!Arrays.asList("pending","fired").contains(string(item,"status",20)))throw new IllegalArgumentException("Invalid reminder status");
            string(item,"delivery",100);if(item.has("fired_at"))number(item,"fired_at");if(item.has("source_when"))string(item,"source_when",100);
        }
        JSONObject homework=root.getJSONObject("homework");if(homework.length()>10000)throw new IllegalArgumentException("Too many completed tasks");
        for(Iterator<String> it=homework.keys();it.hasNext();) {
            String key=it.next();JSONArray parts=new JSONArray(key);
            if(parts.length()!=4||!(parts.opt(0) instanceof String)||!(parts.opt(1) instanceof Boolean)||!(parts.opt(2) instanceof String)||!(parts.opt(3) instanceof String)
                ||parts.getBoolean(1)||!parts.getString(0).matches("[a-f0-9]{64}")||!HomeworkStatusStore.key(parts.getString(0),false,parts.getString(2),parts.getString(3)).equals(key)||number(homework,key)==0)throw new IllegalArgumentException("Invalid task identity");
        }
        JSONObject settings=root.getJSONObject("settings");keys(settings,"notifications","calendar","updates");
        JSONObject n=settings.getJSONObject("notifications");keys(n,"data","background","background_minutes","night_pause");bool(n,"data");bool(n,"background");bool(n,"night_pause");if(number(n,"background_minutes")>60||!BackgroundSync.validInterval((int)number(n,"background_minutes")))throw new IllegalArgumentException("Invalid interval");
        JSONObject c=settings.getJSONObject("calendar");keys(c,"enabled","schedule","homework","reminders","alerts");for(String k:new String[]{"enabled","schedule","homework","reminders","alerts"})bool(c,k);
        JSONObject u=settings.getJSONObject("updates");keys(u,"automatic");bool(u,"automatic");
    }
    static void keys(JSONObject value,String... names)throws Exception {
        Set<String> allowed=new HashSet<>(Arrays.asList(names));for(Iterator<String> it=value.keys();it.hasNext();)if(!allowed.contains(it.next()))throw new IllegalArgumentException("Unknown backup field");
    }
    static String string(JSONObject o,String key,int max)throws Exception {Object v=o.get(key);if(!(v instanceof String)||((String)v).length()>max)throw new IllegalArgumentException("Invalid text");return (String)v;}
    static boolean bool(JSONObject o,String key)throws Exception {Object v=o.get(key);if(!(v instanceof Boolean))throw new IllegalArgumentException("Invalid option");return (Boolean)v;}
    static long number(JSONObject o,String key)throws Exception {Object v=o.get(key);if(!(v instanceof Number))throw new IllegalArgumentException("Invalid number");double d=((Number)v).doubleValue();long l=((Number)v).longValue();if(!Double.isFinite(d)||d!=l||l<0||l>32503680000000L)throw new IllegalArgumentException("Invalid number");return l;}
    static BackupDocument read(InputStream input)throws Exception {
        if(input==null)throw new IOException("No input");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
        while((n=input.read(chunk))!=-1){if(bytes.size()+n>MAX_BYTES)throw new IOException("Oversize backup");bytes.write(chunk,0,n);}
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        // Bound nesting before Android's recursive parser. Braces inside strings do not count.
        int depth=0;boolean quote=false,escape=false;
        for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(quote){if(escape)escape=false;else if(ch=='\\')escape=true;else if(ch=='"')quote=false;}else if(ch=='"')quote=true;else if(ch=='{'||ch=='['){if(++depth>32)throw new IOException("Deep JSON");}else if(ch=='}'||ch==']'){if(--depth<0)throw new IOException("Invalid JSON");}}
        JSONTokener tokener=new JSONTokener(text);Object value=tokener.nextValue();if(!(value instanceof JSONObject)||tokener.nextClean()!=0)throw new IOException("Invalid JSON");return new BackupDocument((JSONObject)value);
    }
    byte[] bytes()throws Exception {byte[] bytes=root.toString(2).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Oversize backup");return bytes;}
    String school()throws Exception{return root.getJSONObject("school").toString();}
    String summary()throws Exception {
        JSONObject s=root.getJSONObject("school"),snapshot=s.getJSONObject("snapshot"),sections=snapshot.getJSONObject("sections");int entries=0;for(Iterator<String> it=sections.keys();it.hasNext();)entries+=sections.getJSONArray(it.next()).length();
        int archives=0;JSONObject profiles=s.getJSONObject("announcement_archives").getJSONObject("profiles");for(Iterator<String> it=profiles.keys();it.hasNext();)archives+=profiles.getJSONObject(it.next()).getJSONArray("items").length();
        return "Kopia z "+DisplayData.date(java.time.Instant.ofEpochMilli(root.getLong("created_at")).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())+" · wersja "+root.getString("app_version")+"\nWpisy w zapisanej kopii: "+entries+"\nOgłoszenia w archiwach: "+archives+"\nPrzypomnienia: "+root.getJSONArray("reminders").length()+"\nWykonane zadania: "+root.getJSONObject("homework").length();
    }
    static BackupDocument create(Context c,String school)throws Exception {
        JSONArray reminders=new JSONArray(),savedReminders=new ReminderStore(c).list();for(int i=0;i<savedReminders.length();i++){JSONObject item=savedReminders.getJSONObject(i);if(!item.optBoolean("demo"))reminders.put(item);}
        JSONObject done=new HomeworkStatusStore(c).backup(),real=new JSONObject();for(Iterator<String> it=done.keys();it.hasNext();){String k=it.next();if(!new JSONArray(k).getBoolean(1))real.put(k,done.get(k));}
        JSONObject calendar=new JSONObject().put("enabled",GoogleCalendarSync.enabled(c)).put("alerts",GoogleCalendarSync.alerts(c));for(String k:new String[]{"schedule","homework","reminders"})calendar.put(k,GoogleCalendarSync.source(c,k));
        JSONObject options=new JSONObject().put("notifications",new JSONObject().put("data",NotificationHub.dataEnabled(c)).put("background",BackgroundSync.enabled(c)||c.getSharedPreferences("notification_options",0).getBoolean("resume_background_after_login",false)).put("background_minutes",BackgroundSync.intervalMinutes(c)).put("night_pause",BackgroundSync.nightEnabled(c)))
            .put("calendar",calendar).put("updates",new JSONObject().put("automatic",UpdateManager.get(c).automatic()));
        return new BackupDocument(new JSONObject().put("format","LibrusApp-backup").put("schema",1).put("created_at",System.currentTimeMillis()).put("app_version",UpdateManager.installedName(c)).put("school",new JSONObject(school)).put("reminders",reminders).put("homework",real).put("settings",options));
    }
}
