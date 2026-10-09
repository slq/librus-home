package pl.librushome.android;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import org.json.JSONObject;
import java.util.function.Supplier;

/** SAF chooses a user-owned JSON document. Preview and confirmation precede replacement. */
final class BackupUi {
    static final int EXPORT=451,IMPORT=452;
    private final Activity activity;private final MobileRepository repository;private final Supplier<JSONObject> state;private final Runnable changed;
    private AlertDialog confirmation;
    boolean importing;
    BackupUi(Activity a,MobileRepository repository,Supplier<JSONObject> state,Runnable changed){activity=a;this.repository=repository;this.state=state;this.changed=changed;}
    private TextView text(String value){TextView t=new TextView(activity);t.setText(value);t.setTextSize(14);t.setTextColor(0xff64748b);t.setPadding(0,8,0,8);return t;}
    private void notice(String value){if(!activity.isDestroyed())Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    void render(LinearLayout parent){
        TextView title=text("Kopia danych i ustawień");title.setTextSize(19);title.setTextColor(0xff183047);title.setTypeface(null,android.graphics.Typeface.BOLD);parent.addView(title);
        parent.addView(text("Zapisz zwykły plik JSON lub odtwórz go po zmianie telefonu albo ponownej instalacji. Kopia zawiera prywatne dane szkolne, archiwum, przypomnienia, wykonane zadania i ustawienia. Nie zawiera hasła ani sesji Librusa. Przechowuj plik prywatnie."));
        Button export=ButtonStyles.make(activity,"Eksportuj kopię JSON",()->{
            if(state.get().optString("mode").equals("demo")){notice("Demo nie jest eksportowane. Przejdź do konta Librusa.");return;}
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"LibrusApp-kopia-"+java.time.LocalDate.now()+".json");pick(intent,EXPORT);
        },true);
        Button restore=ButtonStyles.make(activity,"Importuj kopię JSON",()->pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"}),IMPORT),false);
        boolean ready=state.get().optBoolean("ready")&&!state.get().optBoolean("busy");export.setEnabled(ready&&!state.get().optString("mode").equals("demo"));restore.setEnabled(ready);parent.addView(export);parent.addView(restore);
        parent.addView(text("Import zastępuje lokalną kopię, nie scala danych. Zapisz obecną kopię przed importem. Po imporcie zaloguj się ponownie, sprawdź zgody Androida i wybierz kalendarz Google. Przypomnienia z przeszłości nie zostaną wysłane ponownie."));
    }
    private void pick(Intent intent,int request){try{activity.startActivityForResult(intent,request);}catch(Exception unavailable){notice("Na telefonie nie ma aplikacji do wyboru pliku. Zainstaluj lub włącz systemową aplikację Pliki.");}}
    boolean result(int request,int result,Intent data){
        if(request!=EXPORT&&request!=IMPORT)return false;if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
        Uri uri=data.getData();
        if(request==EXPORT)repository.exportBackup(uri,(doc,error)->{if(!error.isEmpty())notice(error);else notice("Zapisano kopię JSON bez hasła i sesji Librusa.");});
        else repository.previewBackup(uri,(doc,error)->{if(!error.isEmpty()){notice(error);return;}if(activity.isDestroyed()||activity.isFinishing())return;
            try{confirmation=new AlertDialog.Builder(activity).setTitle("Odtworzyć kopię JSON?").setMessage(doc.summary()+"\n\nTa kopia zastąpi zapisane dane, wszystkie przypomnienia, statusy zadań i ustawienia. Jeśli zawiera inne konto, zobaczysz jego dane. Najpierw wyeksportuj obecną kopię, jeśli chcesz ją zachować.\n\nPo imporcie zaloguj się ponownie i wybierz kalendarz Google. Przeszłe przypomnienia nie wywołają powiadomień.")
                .setNegativeButton("Anuluj",null).setPositiveButton("Importuj i zast\u0105p",(d,w)->{
                    importing=true;
                    repository.importBackup(doc,(done,problem)->{
                        importing=false;
                        if(!problem.isEmpty())notice(problem);
                        else if(!activity.isDestroyed()){
                            changed.run();
                            new AlertDialog.Builder(activity).setTitle("Kopia odtworzona")
                                .setMessage("Zaloguj si\u0119 ponownie do Librusa. Sprawd\u017a zgody na powiadomienia i dok\u0142adne alarmy. Pobieranie w tle wr\u00f3ci po zalogowaniu z zapami\u0119taniem konta, je\u015bli by\u0142o w\u0142\u0105czone w kopii. Wybierz ponownie kalendarz Google.")
                                .setPositiveButton("OK",null).show();
                        }
                    });
                }).show();}
            catch(Exception invalid){notice("Nie można odczytać podsumowania kopii. Dane pozostają bez zmian.");}
        });return true;
    }
    void close(){if(confirmation!=null)confirmation.dismiss();}
}
