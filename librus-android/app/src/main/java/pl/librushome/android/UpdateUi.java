package pl.librushome.android;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.widget.*;

/** No forced downloads. The user explicitly chooses download, source permission and system install. */
public final class UpdateUi {
    private final Activity activity;private final UpdateManager manager;private LinearLayout root;private TextView status,notes;private Button check,download,install,cancel;private ProgressBar progress;
    private final Runnable listener=this::refresh;
    public UpdateUi(Activity a){activity=a;manager=UpdateManager.get(a);}
    public void start(){manager.observe(listener,true);UpdateManager.configure(activity);manager.check(false,null);}
    public void stop(){manager.observe(listener,false);}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(0xff183047);v.setPadding(0,4,0,8);return v;}
    public void render(LinearLayout parent){root=parent;parent.addView(text("Aktualizacje aplikacji",19));parent.addView(text("Zainstalowana wersja: "+UpdateManager.installedName(activity),14));
        Switch automatic=new Switch(activity);automatic.setText("Sprawdzaj nową wersję raz dziennie");automatic.setChecked(manager.automatic());automatic.setOnCheckedChangeListener((b,value)->manager.automatic(value));parent.addView(automatic);
        parent.addView(text("Źródło: GitHub · slq/librus-home. Pobieranie APK zaczyna się dopiero po wybraniu przycisku. Aktualizacja zachowuje lokalne dane.",13));status=text("",14);notes=text("",14);parent.addView(status);parent.addView(notes);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);parent.addView(progress);
        check=ButtonStyles.make(activity,"Sprawdź aktualizacje",()->manager.check(true,null),false);parent.addView(check);
        download=ButtonStyles.make(activity,"Pobierz aktualizację",manager::download,true);parent.addView(download);
        install=ButtonStyles.make(activity,"Zainstaluj aktualizację",()->{if(!activity.getPackageManager().canRequestPackageInstalls()){new AlertDialog.Builder(activity).setTitle("Zgoda na aktualizacje").setMessage("Android wymaga zezwolenia LibrusApp na instalowanie APK. Włącz zgodę, wróć do aplikacji i ponownie wybierz Zainstaluj. Każdą instalację potwierdzisz osobno.").setNegativeButton("Anuluj",null).setPositiveButton("Otwórz ustawienia",(d,w)->{try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(Exception e){manager.status="Otwórz ustawienia Androida → Instalowanie nieznanych aplikacji → LibrusApp.";refresh();}}).show();}else manager.install();},true);parent.addView(install);
        cancel=ButtonStyles.make(activity,"Anuluj pobieranie",manager::cancel,false);parent.addView(cancel);refresh();
    }
    private void refresh(){if(root==null)return;status.setText(manager.status);notes.setText(manager.release==null?"":"Wersja "+manager.release.name+" · "+Math.round(manager.release.size/1024.0/1024.0)+" MiB\n\n"+manager.release.notes);notes.setVisibility(manager.release==null?android.view.View.GONE:android.view.View.VISIBLE);check.setEnabled(!manager.busy);download.setVisibility(manager.release!=null&&!manager.ready?android.view.View.VISIBLE:android.view.View.GONE);download.setEnabled(!manager.busy);install.setVisibility(manager.release!=null&&(manager.ready||manager.apk().exists())?android.view.View.VISIBLE:android.view.View.GONE);install.setEnabled(!manager.busy);progress.setVisibility(manager.busy?android.view.View.VISIBLE:android.view.View.GONE);progress.setProgress(manager.percent);cancel.setVisibility(manager.busy&&manager.status.startsWith("Pobieram")?android.view.View.VISIBLE:android.view.View.GONE);}
}
