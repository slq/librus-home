package pl.librushome.android;

import android.app.*;
import android.graphics.Color;
import android.os.Build;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

/** Phone reminder forms and history. Disk operations run independently of Librus. */
public final class ReminderUi {
    private final Activity activity;
    private final Supplier<JSONObject> current;
    private final Runnable changed;
    private final BiConsumer<JSONObject,String> showSource;
    private JSONArray rows = new JSONArray();
    private String loadError = "";
    private boolean loading;
    private Runnable afterLoad;
    public ReminderUi(Activity a, Supplier<JSONObject> state, Runnable changed, BiConsumer<JSONObject,String> source) {
        activity=a; current=state; this.changed=changed; showSource=source;
    }
    private int dp(int n) { return ButtonStyles.dp(activity,n); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String label, int size, int color) {
        TextView v=new TextView(activity); v.setText(label); v.setTextSize(size); v.setTextColor(color); v.setPadding(0,dp(4),0,dp(4)); return v;
    }
    private void space(LinearLayout p, android.view.View v) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);p.addView(v,lp);
    }
    private boolean demo() { return current.get().optString("mode").equals("demo"); }
    private String profile() { return demo() ? "demo" : current.get().optString("profile"); }
    private boolean belongs(JSONObject row) { return row.optBoolean("demo")==demo() && row.optString("profile").equals(profile()); }
    private String section(String kind) { return kind.equals("messages") ? "Wiadomości" : kind.equals("announcements") ? "Ogłoszenia" : kind.equals("homework") ? "Zadania domowe" : "Terminarz"; }
    private String date(long at) { return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm XXX")); }
    private String status(JSONObject row) {
        if (row.optString("status").equals("pending")) return row.optLong("due") < System.currentTimeMillis() ? "Zaległe — oczekuje na alarm Androida" : "Zaplanowane";
        return switch(row.optString("delivery")) {
            case "sent" -> "Przekazane do Androida";
            case "blocked" -> "Wykonane — powiadomienia zablokowane";
            case "failed" -> "Wykonane — błąd wysyłki";
            default -> "Wykonane — wynik wysyłki niepotwierdzony";
        };
    }
    public void reload(Runnable after) {
        if (after != null) afterLoad = after;
        if (loading) return; loading=true;
        ReminderAlarms.execute(() -> {
            JSONArray loaded=new JSONArray();String error="";
            try { loaded=new ReminderStore(activity.getApplicationContext()).list(); ReminderAlarms.restore(activity.getApplicationContext()); }
            catch(Exception e) { error="Nie można odczytać lub zaplanować przypomnień. Zachowano lokalny zapis."; }
            JSONArray result=loaded;String notice=error;
            activity.runOnUiThread(() -> { loading=false;if(activity.isDestroyed()) return; rows=result;loadError=notice;changed.run();Runnable callback=afterLoad;afterLoad=null;if(callback!=null)callback.run(); });
        });
    }
    public int pendingCount() { int count=0; for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&belongs(r)&&r.optString("status").equals("pending"))count++;} return count; }
    public List<JSONObject> calendarItems() {
        return CalendarData.combine(Collections.emptyList(),Collections.emptyList(),rows,profile(),demo());
    }
    public String calendarError() { return loadError; }
    public void render(LinearLayout parent, String query) {
        space(parent,text("Zaplanowane: "+pendingCount(),14,0xff64748b));
        if(!loadError.isEmpty())space(parent,text(loadError,14,0xff994835));
        if(!NotificationHub.error(activity).isEmpty())space(parent,text(NotificationHub.error(activity),14,0xff994835));
        if(!NotificationHub.allowed(activity,NotificationHub.REMINDERS))space(parent,text("Android blokuje powiadomienia przypomnień. Włącz je w Ustawieniach aplikacji.",14,0xff994835));
        if(!ReminderAlarms.exact(activity))space(parent,text("Bez zgody na dokładne alarmy Android może opóźnić przypomnienie. Zgodę możesz włączyć w Ustawieniach.",14,0xff994835));
        java.util.List<JSONObject> visible=new ArrayList<>();
        for(int i=0;i<rows.length();i++) {JSONObject r=rows.optJSONObject(i);if(r!=null&&belongs(r))visible.add(r);}
        visible.sort(Comparator.comparingLong(r->r.optLong("due")));
        int count=0;
        for(JSONObject row:visible) {
            String hay=(row.optString("note")+" "+row.optString("title")+" "+section(row.optString("kind"))+" "+date(row.optLong("due"))+" "+status(row)).toLowerCase(Locale.ROOT);
            if(!hay.contains(query.trim().toLowerCase(Locale.ROOT)))continue;
            LinearLayout c=column();c.setPadding(dp(16),dp(12),dp(16),dp(12));
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(14));c.setBackground(bg);
            c.addView(text(row.optString("note"),17,0xff183047));c.addView(text(date(row.optLong("due")),14,ButtonStyles.TEAL));
            c.addView(text(status(row),13,0xff64748b));c.addView(text(section(row.optString("kind"))+" · "+row.optString("title"),13,0xff64748b));
            c.setOnClickListener(v->details(row));c.setFocusable(true);c.setContentDescription(row.optString("note")+". Szczegóły przypomnienia");space(parent,c);count++;
        }
        if(count==0)space(parent,text(query.trim().isEmpty()?"Dodaj przypomnienie ze szczegółów wiadomości, ogłoszenia, wydarzenia lub zadania domowego.":"Brak wyników wyszukiwania.",15,0xff64748b));
    }
    public void open(String id) { for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&row.optString("id").equals(id)){details(row);return;}} toast("Przypomnienie zostało usunięte lub nie jest dostępne."); }
    public void add(JSONObject source, String kind) { editor(null, source, kind); }
    private void secure(Dialog dialog) { if(!demo())dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); }
    private void toast(String value) { Toast.makeText(activity,value,Toast.LENGTH_LONG).show(); }
    private void details(JSONObject row) {
        LinearLayout body=column();body.setPadding(dp(20),dp(8),dp(20),dp(8));
        body.addView(text(row.optString("note"),19,0xff183047));body.addView(text(date(row.optLong("due")),15,ButtonStyles.TEAL));
        body.addView(text(status(row),14,0xff64748b));body.addView(text(section(row.optString("kind"))+" · "+row.optString("title"),14,0xff64748b));
        body.addView(text(row.optBoolean("show_text")?"Treść widoczna w powiadomieniu. Widocznością na ekranie blokady steruje Android.":"Treść ukryta w powiadomieniu systemowym.",13,0xff64748b));
        if(row.has("fired_at"))body.addView(text("Uruchomiono: "+date(row.optLong("fired_at")),13,0xff64748b));
        if(!belongs(row))body.addView(text("Przypomnienie pochodzi z innego konta lub trybu demo. Połącz właściwe konto, aby otworzyć źródło.",14,0xff994835));
        ScrollView scroller=new ScrollView(activity);scroller.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Twoje przypomnienie").setView(scroller).setPositiveButton("Zamknij",null).create();
        space(body,ButtonStyles.make(activity,"Pokaż wpis",()->{if(!belongs(row)){toast("Połącz konto przypomnienia lub odpowiedni tryb demo.");return;}dialog.dismiss();showSource.accept(row,row.optString("kind"));},false));
        space(body,ButtonStyles.make(activity,"Edytuj przypomnienie",()->{dialog.dismiss();editor(row,null,row.optString("kind"));},true));
        space(body,ButtonStyles.make(activity,"Usuń przypomnienie",()->{dialog.dismiss();remove(row);},false));
        dialog.show();secure(dialog);
    }
    private void remove(JSONObject row) {
        ReminderAlarms.execute(()->{
            String result;
            try { synchronized(ReminderStore.LOCK){new ReminderStore(activity).delete(row.optString("id"));ReminderAlarms.cancel(activity,row.optString("id"));}result="Usunięto przypomnienie."; }
            catch(Exception e){result="Nie usunięto przypomnienia. Zmiana nie została zapisana.";}
            GoogleCalendarSync.request(activity.getApplicationContext(),null);
            String message=result;activity.runOnUiThread(()->{if(!activity.isDestroyed()){toast(message);reload(null);}});
        });
    }
    private void editor(JSONObject existing, JSONObject source, String kind) {
        JSONObject origin=existing==null?source:existing;
        String sourceId=existing==null?source.optString("id"):existing.optString("source_id");
        String accountProfile=existing==null?profile():existing.optString("profile");boolean isDemo=existing==null?demo():existing.optBoolean("demo");
        ZonedDateTime initial=Instant.ofEpochMilli(existing==null?System.currentTimeMillis()+3600000:existing.optLong("due")).atZone(ZoneId.systemDefault());
        LocalDate[] day={initial.toLocalDate()};LocalTime[] time={initial.toLocalTime().withSecond(0).withNano(0)};
        LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(4));
        form.addView(text(section(kind)+" · "+origin.optString("title"),14,0xff64748b));
        EditText note=new EditText(activity);note.setHint("O czym przypomnieć? (1–200 znaków)");note.setMinLines(2);note.setMaxLines(4);note.setSaveEnabled(false);note.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setText(existing==null?"":existing.optString("note"));space(form,note);
        Button dateButton=ButtonStyles.make(activity,"",()->{},false),timeButton=ButtonStyles.make(activity,"",()->{},false);
        Runnable update=()->{dateButton.setText(day[0].format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));timeButton.setText(time[0].format(DateTimeFormatter.ofPattern("HH:mm")));};update.run();
        dateButton.setOnClickListener(v->new DatePickerDialog(activity,(picker,y,m,d)->{day[0]=LocalDate.of(y,m+1,d);update.run();},day[0].getYear(),day[0].getMonthValue()-1,day[0].getDayOfMonth()).show());
        timeButton.setOnClickListener(v->new TimePickerDialog(activity,(picker,h,m)->{time[0]=LocalTime.of(h,m);update.run();},time[0].getHour(),time[0].getMinute(),true).show());
        LinearLayout dates=new LinearLayout(activity);LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(8);dates.addView(dateButton,left);dates.addView(timeButton,new LinearLayout.LayoutParams(0,-2,1));space(form,dates);
        form.addView(text("Czas telefonu: "+ZoneId.systemDefault().getId(),12,0xff64748b));
        TextView error=text("",14,0xff994835);
        String sourceWhen=existing==null?source.optString("when"):existing.optString("source_when");
        if (sourceWhen.isEmpty() && (existing==null || belongs(existing))) {
            JSONObject sections=current.get().optJSONObject("sections");
            JSONArray entries=sections==null?null:sections.optJSONArray(kind);
            if(entries!=null)for(int i=0;i<entries.length();i++) {
                JSONObject item=entries.optJSONObject(i);
                if(item!=null&&item.optString("id").equals(sourceId)) { sourceWhen=item.optString("when");break; }
            }
        }
        final String deadline=sourceWhen;
        String[] names={"Za 30 minut","Za godzinę","Jutro 08:00","Za tydzień"};
        for(int pair=0;pair<2;pair++) {
            LinearLayout shortcuts=new LinearLayout(activity);
            for(int i=pair*2;i<pair*2+2;i++){final int choice=i;Button b=ButtonStyles.make(activity,names[i],()->{
                ZonedDateTime now=ZonedDateTime.now();ZonedDateTime next=choice==0?now.plusMinutes(30):choice==1?now.plusHours(1):choice==2?now.plusDays(1).withHour(8).withMinute(0):now.plusWeeks(1);
                day[0]=next.toLocalDate();time[0]=next.toLocalTime().withSecond(0).withNano(0);error.setText("");update.run();},false);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);shortcuts.addView(b,lp);
            }space(form,shortcuts);
        }
        if((kind.equals("schedule")||kind.equals("homework"))&&!deadline.isEmpty()) {
            space(form,text("Względem terminu: "+DisplayData.date(deadline),13,0xff64748b));
            String[] relative={"Dzień wcześniej o 18:00","Godzinę wcześniej"};
            for(int i=0;i<(ReminderTimes.hasTime(deadline)?2:1);i++) {
                final boolean evening=i==0;
                space(form,ButtonStyles.make(activity,relative[i],()->{
                    try {
                        long at=ReminderTimes.beforeEvent(deadline,evening,ZoneId.systemDefault(),System.currentTimeMillis());
                        ZonedDateTime next=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
                        day[0]=next.toLocalDate();time[0]=next.toLocalTime().withSecond(0).withNano(0);error.setText("");update.run();
                    }catch(IllegalArgumentException invalid){error.setText(invalid.getMessage());}
                },false));
            }
        }
        CheckBox showText=new CheckBox(activity);showText.setText("Pokaż mój tekst w powiadomieniu");showText.setChecked(existing!=null&&existing.optBoolean("show_text"));space(form,showText);
        form.addView(error);
        ScrollView scroller=new ScrollView(activity);scroller.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(existing==null?"Nowe przypomnienie":"Edytuj przypomnienie").setView(scroller).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create();dialog.show();secure(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String content=note.getText().toString().trim();long at;
            try { if(content.isEmpty()||content.codePointCount(0,content.length())>200)throw new IllegalArgumentException("Wpisz treść od 1 do 200 znaków.");at=ReminderStore.localTime(day[0],time[0],ZoneId.systemDefault());if(at<=System.currentTimeMillis())throw new IllegalArgumentException("Wybierz termin w przyszłości."); }
            catch(IllegalArgumentException e){error.setText(e.getMessage());return;}
            boolean reveal=showText.isChecked();String id=existing==null?null:existing.optString("id");String title=origin.optString("title");dialog.dismiss();
            if(Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},410);
            ReminderAlarms.execute(()->{
                String result;
                try { synchronized(ReminderStore.LOCK){JSONObject saved=new ReminderStore(activity).save(id,kind,sourceId,accountProfile,isDemo,title,content,at,reveal,System.currentTimeMillis(),deadline);
                    try { ReminderAlarms.schedule(activity,saved);result="Zapisano przypomnienie."; }
                    catch(Exception scheduleFailure){result="Zapisano przypomnienie, ale Android nie przyjął alarmu. Otwórz listę przypomnień, aby ponowić planowanie.";}}
                }catch(Exception e){result="Nie zapisano przypomnienia. Poprzedni zapis pozostał bez zmian.";}
                GoogleCalendarSync.request(activity.getApplicationContext(),null);
                String message=result;activity.runOnUiThread(()->{if(!activity.isDestroyed()){toast(message);reload(null);}});
            });
        });
    }
}
