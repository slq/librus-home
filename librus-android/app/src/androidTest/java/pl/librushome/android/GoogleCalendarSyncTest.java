package pl.librushome.android;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract.*;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

/** Blank emulator only. Synthetic Google-type provider calendars have no real Google account. */
public class GoogleCalendarSyncTest {
    private static final String ACCOUNT="librus-calendar-test@example.invalid",PROFILE="synthetic-calendar-profile";
    private Uri adapter(Uri uri,String type){return uri.buildUpon().appendQueryParameter("caller_is_syncadapter","true").appendQueryParameter("account_name",ACCOUNT).appendQueryParameter("account_type",type).build();}
    private long calendar(Context c,String name,String type,int access){
        ContentValues row=new ContentValues();row.put(Calendars.ACCOUNT_NAME,ACCOUNT);row.put(Calendars.ACCOUNT_TYPE,type);row.put(Calendars.NAME,name);row.put(Calendars.CALENDAR_DISPLAY_NAME,name);row.put(Calendars.CALENDAR_COLOR,0xff087e8b);row.put(Calendars.CALENDAR_ACCESS_LEVEL,access);row.put(Calendars.OWNER_ACCOUNT,ACCOUNT);row.put(Calendars.CALENDAR_TIME_ZONE,"Europe/Warsaw");row.put(Calendars.SYNC_EVENTS,1);row.put(Calendars.VISIBLE,1);row.put(Calendars.ALLOWED_REMINDERS,"0,1");row.put(Calendars.MAX_REMINDERS,5);
        Uri result=c.getContentResolver().insert(adapter(Calendars.CONTENT_URI,type),row);assertNotNull(result);return ContentUris.parseId(result);
    }
    private JSONObject item(String id,String kind,String date)throws Exception{return new JSONObject().put("id",id).put("kind",kind).put("title","SYNTHETIC_TITLE").put("subtitle","SYNTHETIC_SUBJECT").put("when",date).put("details","").put("url","").put("unread",false);}
    private Map<String,JSONObject> rows(Context c,long calendar)throws Exception{
        Map<String,JSONObject> result=new HashMap<>();try(Cursor cursor=c.getContentResolver().query(Events.CONTENT_URI,new String[]{Events._ID,Events.CUSTOM_APP_URI,Events.DTSTART,Events.ALL_DAY,Events.TITLE,Events.HAS_ALARM},Events.CALENDAR_ID+"=? AND "+Events.DELETED+"=0",new String[]{Long.toString(calendar)},null)){assertNotNull(cursor);while(cursor.moveToNext()){String key=cursor.getString(1);result.put(key==null?"foreign":key,new JSONObject().put("id",cursor.getLong(0)).put("start",cursor.getLong(2)).put("all_day",cursor.getInt(3)).put("title",cursor.getString(4)).put("alarm",cursor.getInt(5)));}}return result;
    }
    private View find(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void waitText(String text)throws Exception{long until=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<until){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty())return;Thread.sleep(30);}fail("Missing UI: "+text);}
    private void clickDialog(String text)throws Exception{waitText(text);var root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();for(var node:root.findAccessibilityNodeInfosByText(text))if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;fail("Cannot click dialog");}
    @Test public void providerExportIsIdempotentEditsAndDeletesOwnReminderAndSettingsPersist()throws Exception{
        assertTrue("Use a blank emulator, never a phone",Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assertTrue(GoogleCalendarSync.permitted(c));
        SecureStore secure=new SecureStore(c);String original=secure.read();assertTrue("Never use a school account",original.isEmpty()||new JSONObject(original).isNull("credentials"));assertFalse("No pre-existing calendar integration",GoogleCalendarSync.enabled(c));
        List<Long> calendars=new ArrayList<>();JSONObject reminder=null;GoogleCalendarSync.demo(false);
        try {
            long calendar=calendar(c,"Synthetic Google Home","com.google",Calendars.CAL_ACCESS_OWNER);calendars.add(calendar);
            long readOnly=calendar(c,"Synthetic Read Only","com.google",Calendars.CAL_ACCESS_READ);calendars.add(readOnly);
            long local=calendar(c,"Synthetic Local","LOCAL",Calendars.CAL_ACCESS_OWNER);calendars.add(local);
            List<GoogleCalendarSync.Target> choices=GoogleCalendarSync.targets(c);assertEquals(1,choices.stream().filter(t->t.account().equals(ACCOUNT)).count());
            GoogleCalendarSync.Target target=choices.stream().filter(t->t.id()==calendar).findFirst().orElseThrow();
            LocalDate day=LocalDate.now().plusDays(2);int year=day.getMonthValue()>=9?day.getYear():day.getYear()-1;
            JSONObject event=item("schedule:test","schedule",day.toString()),task=item("homework:test","homework",day.toString());
            JSONObject sections=new JSONObject().put("schedule",new JSONArray().put(event)).put("homework",new JSONArray().put(task));
            JSONObject root=new JSONObject().put("version",1).put("credentials",JSONObject.NULL).put("profile",PROFILE).put("snapshot",new JSONObject().put("year",year+"/"+(year+1)).put("sections",sections));secure.write(root.toString());
            long now=System.currentTimeMillis();ReminderStore reminders=new ReminderStore(c);reminder=reminders.save(null,"messages","synthetic-source",PROFILE,false,"SYNTHETIC_SOURCE","SYNTHETIC_NOTE",now+3600000,false,now);String reminderId=reminder.getString("id");
            ContentValues other=new ContentValues();other.put(Events.CALENDAR_ID,calendar);other.put(Events.TITLE,"SYNTHETIC_FOREIGN_EVENT");other.put(Events.DTSTART,now+7200000);other.put(Events.DTEND,now+10800000);other.put(Events.EVENT_TIMEZONE,"UTC");assertNotNull(c.getContentResolver().insert(Events.CONTENT_URI,other));
            GoogleCalendarSync.configure(c,target,PROFILE,true,true,true,true);GoogleCalendarSync.syncStored(c);
            assertTrue(GoogleCalendarSync.status(c),GoogleCalendarSync.status(c).startsWith("Zapisano"));
            Map<String,JSONObject> first=rows(c,calendar);assertEquals(4,first.size());String rk=CalendarExportTimes.key(PROFILE,"reminders",reminderId),ek=CalendarExportTimes.key(PROFILE,"schedule","schedule:test"),hk=CalendarExportTimes.key(PROFILE,"homework","homework:test");assertEquals(1,first.get(hk).getInt("all_day"));assertEquals(1,first.get(rk).getInt("alarm"));
            GoogleCalendarSync.syncStored(c);assertEquals(4,rows(c,calendar).size());assertTrue(GoogleCalendarSync.status(c).contains("nowe 0, zmienione 0, usunięte 0"));
            event.put("when",day.plusDays(1).toString());secure.write(root.toString());reminders.save(reminderId,"messages","synthetic-source",PROFILE,false,"SYNTHETIC_SOURCE","SYNTHETIC_CHANGED_NOTE",now+5400000,false,now);GoogleCalendarSync.syncStored(c);
            assertTrue(GoogleCalendarSync.status(c),GoogleCalendarSync.status(c).startsWith("Zapisano"));
            Map<String,JSONObject> edited=rows(c,calendar);assertEquals(4,edited.size());assertEquals(first.get(ek).getLong("id"),edited.get(ek).getLong("id"));assertEquals(first.get(rk).getLong("id"),edited.get(rk).getLong("id"));assertTrue(edited.get(rk).getString("title").contains("SYNTHETIC_CHANGED_NOTE"));
            reminders.claim(reminderId,now+5400000);ReminderAlarms.snooze(c,reminderId,now+5400000,now+5400000);java.util.concurrent.CountDownLatch exported=new java.util.concurrent.CountDownLatch(1);GoogleCalendarSync.execute(exported::countDown);assertTrue(exported.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(reminders.get(reminderId).getLong("due"),rows(c,calendar).get(rk).getLong("start"));
            sections.put("schedule",new JSONArray());secure.write(root.toString());reminders.delete(reminderId);ReminderAlarms.cancel(c,reminderId);GoogleCalendarSync.syncStored(c);assertEquals(3,rows(c,calendar).size());assertNotNull(rows(c,calendar).get(ek));assertNotNull(rows(c,calendar).get("foreign"));
            secure.write("corrupt-json-fixture");GoogleCalendarSync.syncStored(c);assertEquals(3,rows(c,calendar).size());assertTrue(GoogleCalendarSync.status(c).startsWith("Nie ukończono"));secure.write(root.toString());
            Context denied=new ContextWrapper(c){@Override public int checkSelfPermission(String permission){return android.content.pm.PackageManager.PERMISSION_DENIED;}};GoogleCalendarSync.syncStored(denied);assertEquals(3,rows(c,calendar).size());assertTrue(GoogleCalendarSync.status(c).startsWith("Brak dostępu"));
            GoogleCalendarSync.demo(true);task.put("title","DO_NOT_EXPORT_DEMO");secure.write(root.toString());GoogleCalendarSync.syncStored(c);assertFalse(rows(c,calendar).get(hk).getString("title").contains("DO_NOT_EXPORT"));GoogleCalendarSync.demo(false);
            root.put("profile","other-profile");secure.write(root.toString());GoogleCalendarSync.syncStored(c);assertEquals(3,rows(c,calendar).size());assertTrue(GoogleCalendarSync.status(c).contains("innego konta"));root.put("profile",PROFILE);task.put("title","SYNTHETIC_TITLE");secure.write(root.toString());
            GoogleCalendarSync.clearExported(c);assertEquals(1,rows(c,calendar).size());assertNotNull(rows(c,calendar).get("foreign"));assertFalse(GoogleCalendarSync.enabled(c));
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(c,MainActivity.class).putExtra("reminder_id","synthetic-missing"))){
                waitText("Przypomnienia");Thread.sleep(300);
                scenario.onActivity(a->{NavigationTestSupport.open(a,"Ustawienia");find(a.getWindow().getDecorView(),"Wybierz kalendarz i źródła").performClick();});
                waitText("Synchronizacja z Google");clickDialog("Zapisz i synchronizuj");
                long until=System.currentTimeMillis()+5000;while(!GoogleCalendarSync.enabled(c)&&System.currentTimeMillis()<until)Thread.sleep(30);assertTrue(GoogleCalendarSync.enabled(c));assertEquals(calendar,GoogleCalendarSync.prefs(c).getLong("calendar",-1));
                scenario.recreate();scenario.onActivity(a->{View heading=find(a.getWindow().getDecorView(),"Kalendarz Google");assertNotNull(heading);heading.requestRectangleOnScreen(new android.graphics.Rect(0,0,heading.getWidth(),heading.getHeight()),true);});waitText("Kalendarz Google");assertTrue(GoogleCalendarSync.source(c,"homework"));
            }
            task.put("title","SYNTHETIC_FROM_BACKGROUND_JOB");secure.write(root.toString());
            var jobs=c.getSystemService(android.app.job.JobScheduler.class);
            assertEquals(android.app.job.JobScheduler.RESULT_SUCCESS,jobs.schedule(new android.app.job.JobInfo.Builder(GoogleCalendarSync.JOB_ID,new ComponentName(c,CalendarExportJobService.class)).setMinimumLatency(3600000).setOverrideDeadline(3600000).setPersisted(true).build()));
            try(android.os.ParcelFileDescriptor command=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("cmd jobscheduler run -f pl.librushome.android 1702")) { }
            long jobDeadline=System.currentTimeMillis()+10000;boolean changed=false;
            while(System.currentTimeMillis()<jobDeadline&&!changed){JSONObject saved=rows(c,calendar).get(hk);changed=saved!=null&&saved.optString("title").contains("SYNTHETIC_FROM_BACKGROUND_JOB");if(!changed)Thread.sleep(50);}
            assertTrue("Calendar export job must read the updated cache without Activity or Librus login",changed);
        } finally {
            GoogleCalendarSync.demo(false);GoogleCalendarSync.disable(c);
            if(reminder!=null){new ReminderStore(c).delete(reminder.getString("id"));ReminderAlarms.cancel(c,reminder.getString("id"));}
            for(int i=0;i<calendars.size();i++)c.getContentResolver().delete(adapter(ContentUris.withAppendedId(Calendars.CONTENT_URI,calendars.get(i)),i==2?"LOCAL":"com.google"),null,null);
            GoogleCalendarSync.prefs(c).edit().clear().commit();if(original.isEmpty())secure.clear();else secure.write(original);
        }
    }
}