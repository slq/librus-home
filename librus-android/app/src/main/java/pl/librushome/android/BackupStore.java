package pl.librushome.android;

import android.content.*;
import org.json.*;
import java.util.*;

/** Import rollback journal is encrypted locally. Portable files remain ordinary JSON. */
final class BackupStore {
    private static final Object LOCK=new Object();
    static final String[] PREFS={"notification_options","google_calendar_export","app_update_options"};
    static SecureStore journal(Context c){return new SecureStore(c,"backup-import.aes","LibrusApp.backup-import.v1");}
    static Runnable afterSchoolWrite=()->{}; // Package-private crash/failure seam for synthetic tests only.
    static JSONObject before(Context c)throws Exception {
        JSONObject groups=new JSONObject();for(String name:PREFS){JSONObject group=new JSONObject();for(var entry:c.getSharedPreferences(name,0).getAll().entrySet()){
            if(name.equals("app_update_options")&&!entry.getKey().equals("automatic"))continue;
            Object value=entry.getValue();String type=value instanceof Boolean?"bool":value instanceof Integer?"int":value instanceof Long?"long":value instanceof Float?"float":value instanceof String?"string":"unsupported";
            if(type.equals("unsupported"))throw new IllegalStateException("Unsupported local option");group.put(entry.getKey(),new JSONObject().put("type",type).put("value",value));}groups.put(name,group);}
        return new JSONObject().put("version",1).put("school",new SecureStore(c).read()).put("reminders",new ReminderStore(c).list()).put("homework",new HomeworkStatusStore(c).backup()).put("preferences",groups);
    }
    private static void restorePrefs(Context c,JSONObject groups)throws Exception {
        for(String name:PREFS){JSONObject group=groups.getJSONObject(name);SharedPreferences.Editor edit=c.getSharedPreferences(name,0).edit();if(name.equals("app_update_options"))edit.remove("automatic");else edit.clear();for(Iterator<String> it=group.keys();it.hasNext();){String key=it.next();JSONObject row=group.getJSONObject(key);switch(row.getString("type")){
            case "bool":edit.putBoolean(key,row.getBoolean("value"));break;case "int":edit.putInt(key,row.getInt("value"));break;case "long":edit.putLong(key,row.getLong("value"));break;case "float":edit.putFloat(key,(float)row.getDouble("value"));break;case "string":edit.putString(key,row.getString("value"));break;default:throw new IllegalStateException("Invalid local option");}}
            if(!edit.commit())throw new IllegalStateException("Options not written");}
    }
    private static void cancel(Context c,JSONArray rows)throws Exception {for(int i=0;i<rows.length();i++)ReminderAlarms.cancel(c,rows.getJSONObject(i).getString("id"));}
    private static void reschedule(Context c)throws Exception {ReminderAlarms.restore(c);BackgroundSync.configure(c,BackgroundSync.enabled(c));UpdateManager.configure(c);}
    private static void rollback(Context c,JSONObject old)throws Exception {
        JSONArray current=new ReminderStore(c).list();new SecureStore(c).write(old.getString("school"));new ReminderStore(c).replace(old.getJSONArray("reminders"));new HomeworkStatusStore(c).replace(old.getJSONObject("homework"));restorePrefs(c,old.getJSONObject("preferences"));
        cancel(c,current);journal(c).clear();reschedule(c);
    }
    static void recover(Context c)throws Exception {synchronized(LOCK){synchronized(GoogleCalendarSync.LOCK){synchronized(ReminderStore.LOCK){synchronized(HomeworkStatusStore.LOCK){
        String raw=journal(c).read();if(raw.isEmpty())return;JSONObject old=new JSONObject(raw);if(old.getInt("version")!=1)throw new IllegalStateException("Unreadable import journal");rollback(c,old);
    }}}}}
    static BackupDocument capture(Context c,String school)throws Exception {synchronized(LOCK){synchronized(GoogleCalendarSync.LOCK){synchronized(ReminderStore.LOCK){synchronized(HomeworkStatusStore.LOCK){return BackupDocument.create(c,school);}}}}}
    static void apply(Context c,BackupDocument doc)throws Exception {synchronized(LOCK){synchronized(GoogleCalendarSync.LOCK){synchronized(ReminderStore.LOCK){synchronized(HomeworkStatusStore.LOCK){
        recover(c);JSONObject old=before(c);journal(c).write(old.toString());
        try {
            new SecureStore(c).write(doc.school());afterSchoolWrite.run();
            JSONArray reminders=new JSONArray(doc.root.getJSONArray("reminders").toString());long now=System.currentTimeMillis();
            for(int i=0;i<reminders.length();i++){JSONObject row=reminders.getJSONObject(i);if(row.getString("status").equals("pending")&&row.getLong("due")<=now)row.put("status","fired").put("fired_at",now).put("delivery","import_expired");}
            new ReminderStore(c).replace(reminders);new HomeworkStatusStore(c).replace(doc.root.getJSONObject("homework"));
            JSONObject settings=doc.root.getJSONObject("settings"),n=settings.getJSONObject("notifications"),cal=settings.getJSONObject("calendar");
            if(!c.getSharedPreferences("notification_options",0).edit().putBoolean("data",n.getBoolean("data")).putBoolean("background",false).putBoolean("resume_background_after_login",n.getBoolean("background")).putInt("background_minutes",n.getInt("background_minutes")).putBoolean("night_pause",n.getBoolean("night_pause")).remove("alarm_error").commit())throw new IllegalStateException("Options not written");
            SharedPreferences.Editor edit=GoogleCalendarSync.prefs(c).edit().clear().putBoolean("enabled",false).putString("status","Po imporcie wybierz ponownie kalendarz. Istniejące wydarzenia pozostają bez zmian.");
            for(String key:new String[]{"schedule","homework","reminders","alerts"})edit.putBoolean(key,cal.getBoolean(key));if(!edit.commit())throw new IllegalStateException("Calendar options not written");
            if(!c.getSharedPreferences("app_update_options",0).edit().putBoolean("automatic",settings.getJSONObject("updates").getBoolean("automatic")).commit())throw new IllegalStateException("Update options not written");
            // No CalendarProvider writes, network requests or expired-reminder notifications during import.
            c.getSystemService(android.app.job.JobScheduler.class).cancel(GoogleCalendarSync.JOB_ID);
            cancel(c,old.getJSONArray("reminders"));journal(c).clear();
        }catch(Exception failure){try{rollback(c,old);}catch(Exception restoreFailure){throw new IllegalStateException("Import interrupted; original data will be recovered at startup",restoreFailure);}throw failure;}
        try{reschedule(c);}catch(Exception alarmFailure){NotificationHub.error(c,"Dane odtworzono, ale nie przywrócono wszystkich harmonogramów. Sprawdź zgody Androida i przypomnienia.");}
    }}}}}
    private BackupStore(){}
}
