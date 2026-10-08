package pl.librushome.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.provider.Settings;
import android.widget.*;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/** Calendar consent and target selection; all provider operations run away from the UI thread. */
public final class GoogleCalendarUi {
    public static final int PERMISSION=430;
    private final Activity activity;private final Supplier<JSONObject> state;private final Runnable changed;
    public GoogleCalendarUi(Activity a,Supplier<JSONObject> state,Runnable changed){activity=a;this.state=state;this.changed=changed;}
    private TextView text(String value){TextView t=new TextView(activity);t.setText(value);t.setTextSize(14);t.setTextColor(0xff64748b);t.setPadding(0,8,0,8);return t;}
    private void toast(String value){Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    private void ui(Runnable action){activity.runOnUiThread(()->{if(!activity.isDestroyed())action.run();});}
    public void render(LinearLayout parent){
        TextView title=text("Kalendarz Google");title.setTextSize(19);title.setTextColor(0xff183047);title.setTypeface(null,android.graphics.Typeface.BOLD);parent.addView(title);
        parent.addView(text("Automatycznie kopiuj terminarz, terminy zadań i własne przypomnienia do wybranego kalendarza Google na telefonie. Zmiany nie wracają do Librusa. Współużytkownicy kalendarza mogą widzieć skopiowane tytuły i notatki."));
        parent.addView(text(GoogleCalendarSync.enabled(activity)?"Synchronizacja: włączona":"Synchronizacja: wyłączona"));
        parent.addView(text(GoogleCalendarSync.status(activity)));
        if(GoogleCalendarSync.permitted(activity)&&GoogleCalendarSync.prefs(activity).getLong("calendar",-1)>=0){
            TextView destination=text("Odczytuję wybrany kalendarz…");parent.addView(destination);
            GoogleCalendarSync.execute(()->{try{
                long id=GoogleCalendarSync.prefs(activity).getLong("calendar",-1);
                var match=GoogleCalendarSync.targets(activity).stream().filter(t->t.id()==id).findFirst();
                ui(()->destination.setText(match.isPresent()?"Kalendarz docelowy: "+match.get().label():"Wybrany kalendarz jest niedostępny. Wybierz go ponownie."));
            }catch(Exception e){ui(()->destination.setText("Nie można odczytać wybranego kalendarza."));}});
        }

        long last=GoogleCalendarSync.prefs(activity).getLong("last",0);
        if(last>0)parent.addView(text("Ostatni zapis w kalendarzu telefonu: "+Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))));
        parent.addView(ButtonStyles.make(activity,"Wybierz kalendarz i źródła",this::choose,true));
        parent.addView(ButtonStyles.make(activity,"Synchronizuj teraz",()->{
            if(!GoogleCalendarSync.enabled(activity)){toast("Najpierw wybierz kalendarz i włącz synchronizację.");return;}
            GoogleCalendarSync.request(activity,()->{if(!activity.isDestroyed()){changed.run();toast("Sprawdzono synchronizację. Wynik znajdziesz w ustawieniach.");}});
        },false));
        if(GoogleCalendarSync.enabled(activity))parent.addView(ButtonStyles.make(activity,"Wyłącz synchronizację",()->GoogleCalendarSync.execute(()->{GoogleCalendarSync.disable(activity);ui(changed);}),false));
        if(GoogleCalendarSync.prefs(activity).getLong("calendar",-1)>=0)parent.addView(ButtonStyles.make(activity,"Usuń skopiowane wpisy",()->new AlertDialog.Builder(activity).setTitle("Usunąć skopiowane wpisy?")
                .setMessage("Usunięte zostaną wyłącznie wpisy dodane przez LibrusApp dla skonfigurowanego konta Librusa w wybranym kalendarzu. Synchronizacja zostanie wyłączona. Pozostałe wydarzenia i dane Librusa pozostaną bez zmian.")
                .setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(d,w)->GoogleCalendarSync.execute(()->{try{GoogleCalendarSync.clearExported(activity);ui(changed);}catch(Exception e){ui(()->toast("Nie usunięto wszystkich wpisów. Sprawdź zgodę i dostęp do kalendarza, potem ponów próbę."));}})).show(),false));
        parent.addView(text("Wyłączenie źródła lub zmiana kalendarza pozostawia wcześniejsze kopie. Brak wpisu w krótszym odczycie Librusa nie usuwa wydarzenia z Google. Ustawienia tytułu i terminu eksportowanego wpisu są zarządzane przez LibrusApp."));
    }
    public void choose(){
        JSONObject current=state.get();
        if(current.optString("mode").equals("demo")){toast("Demo nie eksportuje danych do kalendarza. Połącz konto Librusa.");return;}
        if(current.optString("profile").isEmpty()){toast("Najpierw połącz konto Librusa, aby wybrać źródło wydarzeń.");return;}
        if(!GoogleCalendarSync.permitted(activity)){
            new AlertDialog.Builder(activity).setTitle("Dostęp do kalendarza")
                    .setMessage("LibrusApp potrzebuje odczytu i zapisu kalendarza, aby wybrać kalendarz Google oraz dodawać i aktualizować własne wydarzenia. Nie importuje Twoich pozostałych wydarzeń do Librusa.")
                    .setNegativeButton("Anuluj",null).setPositiveButton("Zezwól",(d,w)->activity.requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},PERMISSION)).show();return;
        }
        final String profile=current.optString("profile");
        GoogleCalendarSync.execute(()->{try{List<GoogleCalendarSync.Target> choices=GoogleCalendarSync.targets(activity);ui(()->showTargets(choices,profile));}
            catch(Exception e){ui(()->toast("Nie można odczytać kalendarzy. Sprawdź zgodę w ustawieniach Androida."));}});
    }
    public void permissionResult(){
        if(GoogleCalendarSync.permitted(activity))choose();
        else new AlertDialog.Builder(activity).setTitle("Brak dostępu do kalendarza").setMessage("Synchronizacja pozostaje wyłączona. Dostęp do kalendarza możesz przyznać w uprawnieniach aplikacji Androida.")
                .setNegativeButton("Zamknij",null).setPositiveButton("Uprawnienia aplikacji",(d,w)->activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+activity.getPackageName())))).show();
    }
    private void showTargets(List<GoogleCalendarSync.Target> choices,String profile){
        if(choices.isEmpty()){new AlertDialog.Builder(activity).setTitle("Brak kalendarza Google do zapisu")
                .setMessage("Dodaj konto Google na telefonie i włącz synchronizację wybranego kalendarza. Kalendarz domowy musi być dostępny dla tego konta z prawem dodawania wydarzeń. Lokalne i tylko do odczytu kalendarze nie są wyświetlane.")
                .setPositiveButton("OK",null).show();return;}
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);int padding=ButtonStyles.dp(activity,20);body.setPadding(padding,8,padding,8);
        Spinner calendar=new Spinner(activity);String[] labels=choices.stream().map(GoogleCalendarSync.Target::label).toArray(String[]::new);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);calendar.setAdapter(adapter);calendar.setContentDescription("Docelowy kalendarz Google");calendar.setMinimumHeight(ButtonStyles.dp(activity,48));
        long selected=GoogleCalendarSync.prefs(activity).getLong("calendar",-1);for(int i=0;i<choices.size();i++)if(choices.get(i).id()==selected)calendar.setSelection(i);body.addView(calendar);
        CheckBox schedule=new CheckBox(activity),homework=new CheckBox(activity),reminders=new CheckBox(activity),alerts=new CheckBox(activity);
        schedule.setText("Terminarz");homework.setText("Zadania domowe");reminders.setText("Własne przypomnienia");alerts.setText("Powiadamiaj również w Google o własnych przypomnieniach");
        schedule.setChecked(GoogleCalendarSync.source(activity,"schedule"));homework.setChecked(GoogleCalendarSync.source(activity,"homework"));reminders.setChecked(GoogleCalendarSync.source(activity,"reminders"));alerts.setChecked(GoogleCalendarSync.alerts(activity));
        for(CheckBox box:new CheckBox[]{schedule,homework,reminders,alerts})body.addView(box);
        body.addView(text("Zadania bez godziny są całodniowe. Wydarzenia z godziną mają długość 60 minut, własne przypomnienia 15 minut. Początkowo kopiowane są nadchodzące terminy. Alarm Google pojawi się na początku przypomnienia; alarm LibrusApp nadal działa, więc mogą pojawić się dwa powiadomienia."));
        TextView error=text("");error.setTextColor(0xff994835);body.addView(error);
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Synchronizacja z Google").setView(scroll).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz i synchronizuj",null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!schedule.isChecked()&&!homework.isChecked()&&!reminders.isChecked()){error.setText("Wybierz przynajmniej jedno źródło.");return;}
            if(!profile.equals(state.get().optString("profile"))||state.get().optString("mode").equals("demo")){error.setText("Konto zmieniło się. Otwórz wybór kalendarza ponownie.");return;}
            GoogleCalendarSync.Target target=choices.get(calendar.getSelectedItemPosition());boolean events=schedule.isChecked(),tasks=homework.isChecked(),notes=reminders.isChecked(),alarm=alerts.isChecked();
            dialog.dismiss();GoogleCalendarSync.execute(()->{try{GoogleCalendarSync.configure(activity,target,profile,events,tasks,notes,alarm);GoogleCalendarSync.syncStored(activity);ui(changed);}
                catch(Exception failure){ui(()->toast("Nie zapisano integracji. Sprawdź dostęp do kalendarza."));}});
        });
    }
}