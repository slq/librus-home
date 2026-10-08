package pl.librushome.android;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.*;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** One-way export. Android's Google sync adapter performs the cloud upload. No OAuth secrets. */
public final class GoogleCalendarSync {
    public static final int JOB_ID=1702;
    private static final Object LOCK=new Object();
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static volatile boolean demo;
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("google_calendar_export",Context.MODE_PRIVATE);}
    public record Target(long id,String name,String account,String identity) {
        public String label(){return name+" · "+account;}
    }
    public static boolean permitted(Context c){return c.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;}
    public static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    public static boolean source(Context c,String kind){return prefs(c).getBoolean(kind,true);}
    public static boolean alerts(Context c){return prefs(c).getBoolean("alerts",false);}
    public static void demo(boolean value){demo=value;}
    private static void notice(Context c,String value){prefs(c).edit().putString("status",value).apply();}
    public static String status(Context c){return prefs(c).getString("status","Synchronizacja nie została jeszcze włączona.");}
    public static List<Target> targets(Context c){
        if(!permitted(c))throw new SecurityException("Brak zgody na kalendarz");
        List<Target> result=new ArrayList<>();
        String[] fields={Calendars._ID,Calendars.CALENDAR_DISPLAY_NAME,Calendars.ACCOUNT_NAME,Calendars.ACCOUNT_TYPE,Calendars.OWNER_ACCOUNT};
        try(Cursor rows=c.getContentResolver().query(Calendars.CONTENT_URI,fields,Calendars.ACCOUNT_TYPE+"=? AND "+Calendars.CALENDAR_ACCESS_LEVEL+">=? AND "+Calendars.SYNC_EVENTS+"=1",new String[]{"com.google",Integer.toString(Calendars.CAL_ACCESS_CONTRIBUTOR)},null)){
            if(rows==null)throw new IllegalStateException("Kalendarze niedostępne");
            while(rows.moveToNext())result.add(new Target(rows.getLong(0),rows.getString(1),rows.getString(2),CalendarExportTimes.hash(rows.getString(2)+"\n"+rows.getString(3)+"\n"+rows.getString(4))));
        }
        result.sort(Comparator.comparing(Target::label,String.CASE_INSENSITIVE_ORDER));return result;
    }
    private static Target selected(Context c){var p=prefs(c);for(Target t:targets(c))if(t.id()==p.getLong("calendar",-1)&&t.identity().equals(p.getString("identity","")))return t;throw new IllegalStateException("Wybrany kalendarz jest niedostępny");}
    public static void configure(Context c,Target target,String profile,boolean schedule,boolean homework,boolean reminders,boolean alerts){synchronized(LOCK){
        if(profile.isEmpty())throw new IllegalArgumentException("Najpierw połącz konto Librusa");
        if(!targets(c).contains(target))throw new IllegalArgumentException("Kalendarz niedostępny");
        if(!prefs(c).edit().putLong("calendar",target.id()).putString("identity",target.identity()).putString("profile",profile)
                .putBoolean("schedule",schedule).putBoolean("homework",homework).putBoolean("reminders",reminders).putBoolean("alerts",alerts).putBoolean("enabled",true).commit())throw new IllegalStateException("Nie zapisano wyboru");
        notice(c,"Ustawienia zapisane. Oczekiwanie na synchronizację.");
    }}
    public static void disable(Context c){synchronized(LOCK){c.getSystemService(android.app.job.JobScheduler.class).cancel(JOB_ID);prefs(c).edit().putBoolean("enabled",false).commit();notice(c,"Synchronizacja wyłączona. Wcześniejsze wpisy pozostają w Google.");}}
    public static void execute(Runnable work){WORK.execute(work);}
    public static void request(Context c,Runnable complete){Context app=c.getApplicationContext();
        if(enabled(app)&&!demo)try{
            var job=new android.app.job.JobInfo.Builder(JOB_ID,new ComponentName(app,CalendarExportJobService.class)).setPersisted(true).setOverrideDeadline(30000).build();
            if(app.getSystemService(android.app.job.JobScheduler.class).schedule(job)!=android.app.job.JobScheduler.RESULT_SUCCESS)notice(app,"Android nie przyjął zadania zapisu do kalendarza. Otwórz integrację i ponów synchronizację.");
        }catch(Exception failure){notice(app,"Nie udało się zaplanować zapisu do kalendarza. Ponów synchronizację w ustawieniach.");}
        WORK.execute(()->{try{syncStored(app);}finally{if(complete!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(complete);}});}
    public static void syncStored(Context c){synchronized(LOCK){
        if(!enabled(c)||demo)return;
        if(!permitted(c)){notice(c,"Brak dostępu do kalendarza. Przyznaj zgodę w ustawieniach integracji.");return;}
        try {
            Target target=selected(c);
            JSONObject root=new JSONObject(new SecureStore(c).read());
            String profile=root.optString("profile");
            if(profile.isEmpty()||!profile.equals(prefs(c).getString("profile",""))){notice(c,"Integracja dotyczy innego konta Librusa. Wybierz kalendarz ponownie dla bieżącego konta.");return;}
            JSONObject snapshot=root.optJSONObject("snapshot"),sections=snapshot==null?null:snapshot.optJSONObject("sections");
            if(sections==null)throw new IllegalStateException("Brak kopii");
            // Read the complete reminder list before any mutations: a corrupt file must never imply deletion.
            JSONArray reminders=source(c,"reminders")?new ReminderStore(c).list():new JSONArray();
            Map<String,JSONObject> existing=owned(c,target,profile);
            int added=0,updated=0,removed=0,skipped=0;Set<String> liveReminders=new HashSet<>();
            for(String kind:new String[]{"schedule","homework","reminders"}){
                if(!source(c,kind))continue;
                JSONArray items=kind.equals("reminders")?reminders:sections.optJSONArray(kind);
                if(items==null)continue;
                for(int i=0;i<items.length();i++){
                    JSONObject item=items.optJSONObject(i);if(item==null)continue;
                    if(kind.equals("reminders")&&(item.optBoolean("demo")||!profile.equals(item.optString("profile"))))continue;
                    String id=item.optString("id");if(id.isEmpty()){skipped++;continue;}
                    String key=CalendarExportTimes.key(profile,kind,id);JSONObject old=existing.get(key);
                    if(kind.equals("reminders"))liveReminders.add(key);
                    CalendarExportTimes.Range range;
                    try {range=kind.equals("reminders")?CalendarExportTimes.reminder(item.getLong("due"),ZoneId.systemDefault()):CalendarExportTimes.event(item.optString("when"),item.optString("details"),ZoneId.systemDefault());}
                    catch(Exception invalid){skipped++;continue;}
                    long today=LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                    if(old==null && (kind.equals("reminders")?range.start()<System.currentTimeMillis():range.end()<=today))continue;
                    ContentValues values=new ContentValues();values.put(Events.CALENDAR_ID,target.id());values.put(Events.DTSTART,range.start());values.put(Events.DTEND,range.end());
                    values.put(Events.EVENT_TIMEZONE,range.zone());values.put(Events.ALL_DAY,range.allDay()?1:0);
                    String label=kind.equals("schedule")?"Terminarz":kind.equals("homework")?"Zadanie domowe":"Przypomnienie";
                    values.put(Events.TITLE,label+": "+(kind.equals("reminders")?item.optString("note"):item.optString("title")));
                    String description="Źródło: LibrusApp"+(item.optString(kind.equals("reminders")?"title":"subtitle").isEmpty()?"":"\n"+item.optString(kind.equals("reminders")?"title":"subtitle"));
                    String url=item.optString("url");if(url.startsWith("https://"))description+="\n"+url;
                    values.put(Events.DESCRIPTION,description);values.put(Events.CUSTOM_APP_PACKAGE,c.getPackageName());values.put(Events.CUSTOM_APP_URI,key);
                    boolean alarm=kind.equals("reminders")&&alerts(c);values.put(Events.HAS_ALARM,alarm?1:0);
                    long eventId;
                    if(old==null){Uri uri=c.getContentResolver().insert(Events.CONTENT_URI,values);if(uri==null)throw new IllegalStateException("Nie zapisano wydarzenia");eventId=ContentUris.parseId(uri);added++;}
                    else {eventId=old.getLong("_id");if(different(old,values)){
                        int changed=c.getContentResolver().update(Events.CONTENT_URI,values,Events._ID+"=? AND "+Events.CUSTOM_APP_PACKAGE+"=? AND "+Events.CUSTOM_APP_URI+"=?",new String[]{Long.toString(eventId),c.getPackageName(),key});
                        if(changed!=1)throw new IllegalStateException("Wpis zmienił właściciela");updated++;
                    }}
                    reconcileAlarm(c,eventId,alarm);
                }
            }
            if(source(c,"reminders"))for(var entry:existing.entrySet())if(entry.getKey().contains("/reminders/")&&!liveReminders.contains(entry.getKey())){deleteOwned(c,entry.getValue().getLong("_id"),entry.getKey());removed++;}
            prefs(c).edit().putLong("last",System.currentTimeMillis()).apply();
            notice(c,"Zapisano w kalendarzu telefonu: nowe "+added+", zmienione "+updated+", usunięte "+removed+(skipped>0?", pominięte bez poprawnego terminu "+skipped:"")+". Przesłaniem do Google steruje synchronizacja konta Androida.");
        }catch(SecurityException denied){notice(c,"Android blokuje dostęp do kalendarza. Przyznaj zgodę ponownie.");}
        catch(Exception failure){notice(c,"Nie ukończono synchronizacji. Sprawdź dostępność wybranego kalendarza i lokalnych danych; wcześniejsze wpisy zachowano. Ponów próbę.");}
    }}
    private static Map<String,JSONObject> owned(Context c,Target target,String profile)throws Exception {
        Map<String,JSONObject> result=new HashMap<>();String prefix="librusapp://export/"+CalendarExportTimes.hash(profile)+"/";
        String[] fields={Events._ID,Events.CUSTOM_APP_URI,Events.TITLE,Events.DESCRIPTION,Events.DTSTART,Events.DTEND,Events.EVENT_TIMEZONE,Events.ALL_DAY,Events.HAS_ALARM};
        try(Cursor rows=c.getContentResolver().query(Events.CONTENT_URI,fields,Events.CALENDAR_ID+"=? AND "+Events.CUSTOM_APP_PACKAGE+"=? AND "+Events.DELETED+"=0",new String[]{Long.toString(target.id()),c.getPackageName()},null)){
            if(rows==null)throw new IllegalStateException("Wpisy niedostępne");
            while(rows.moveToNext()){String key=rows.getString(1);if(key==null||!key.startsWith(prefix))continue;JSONObject row=new JSONObject();for(int i=0;i<fields.length;i++)row.put(fields[i],rows.isNull(i)?JSONObject.NULL:rows.getString(i));result.put(key,row);}
        }return result;
    }
    private static boolean different(JSONObject row,ContentValues values){for(String key:new String[]{Events.TITLE,Events.DESCRIPTION,Events.DTSTART,Events.DTEND,Events.EVENT_TIMEZONE,Events.ALL_DAY,Events.HAS_ALARM})if(!row.optString(key).equals(values.getAsString(key)))return true;return false;}
    private static void reconcileAlarm(Context c,long id,boolean enabled){
        List<Long> ours=new ArrayList<>();try(Cursor rows=c.getContentResolver().query(Reminders.CONTENT_URI,new String[]{Reminders._ID},Reminders.EVENT_ID+"=? AND "+Reminders.METHOD+"=? AND "+Reminders.MINUTES+"=0",new String[]{Long.toString(id),Integer.toString(Reminders.METHOD_ALERT)},null)){
            if(rows==null)throw new IllegalStateException("Alarmy niedostępne");while(rows.moveToNext())ours.add(rows.getLong(0));
        }
        if(enabled&&ours.isEmpty()){ContentValues values=new ContentValues();values.put(Reminders.EVENT_ID,id);values.put(Reminders.METHOD,Reminders.METHOD_ALERT);values.put(Reminders.MINUTES,0);if(c.getContentResolver().insert(Reminders.CONTENT_URI,values)==null)throw new IllegalStateException("Nie zapisano alarmu");}
        for(int i=enabled?1:0;i<ours.size();i++)c.getContentResolver().delete(ContentUris.withAppendedId(Reminders.CONTENT_URI,ours.get(i)),null,null);
    }
    private static void deleteOwned(Context c,long id,String key){c.getContentResolver().delete(Events.CONTENT_URI,Events._ID+"=? AND "+Events.CUSTOM_APP_PACKAGE+"=? AND "+Events.CUSTOM_APP_URI+"=?",new String[]{Long.toString(id),c.getPackageName(),key});}
    public static void clearExported(Context c)throws Exception{synchronized(LOCK){Target t=selected(c);String profile=prefs(c).getString("profile","");disable(c);for(var entry:owned(c,t,profile).entrySet())deleteOwned(c,entry.getValue().getLong("_id"),entry.getKey());notice(c,"Usunięto wpisy dodane przez LibrusApp dla tego konta w wybranym kalendarzu. Synchronizacja wyłączona.");}}
    private GoogleCalendarSync() { }
}