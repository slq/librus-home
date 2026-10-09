package pl.librushome.android;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Separate from school sync. APKs live only in private cache, never the encrypted school store. */
public final class UpdateManager {
    static final int JOB_ID=1703;static final String CHANNEL="app_updates";
    @android.annotation.SuppressLint("StaticFieldLeak") private static UpdateManager instance;
    static UpdateTransport transport=new HttpsUpdateTransport(); // Package-private synthetic test seam; no external intents configure it.
    public static synchronized UpdateManager get(Context c){if(instance==null)instance=new UpdateManager(c.getApplicationContext());return instance;}
    final Context context;final ExecutorService worker=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Runnable> listeners=new CopyOnWriteArraySet<>();
    volatile boolean busy,ready,cancelled;volatile int percent;volatile UpdateRelease release;volatile String status="Sprawdzaj aktualizacje lub wybierz sprawdzenie ręczne.";
    private UpdateManager(Context c){context=c;String cached=prefs().getString("release","");try{if(!cached.isEmpty()){UpdateRelease r=UpdateRelease.parse(cached);if(r.code>installedCode(c)){release=r;status="Dostępna wersja "+r.name;}else{prefs().edit().remove("release").apply();apk().delete();c.getSystemService(NotificationManager.class).cancel("app-update",1703);status="Wersja "+installedName(c)+" jest zainstalowana.";}}}catch(Exception ignored){prefs().edit().remove("release").apply();}}
    android.content.SharedPreferences prefs(){return context.getSharedPreferences("app_update_options",Context.MODE_PRIVATE);}
    File apk(){return new File(context.getCacheDir(),"app-update.apk");}
    public static long installedCode(Context c){try{return UpdateApk.code(c.getPackageManager().getPackageInfo(c.getPackageName(),0));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String installedName(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "—";}}
    public boolean automatic(){return prefs().getBoolean("automatic",true);}
    public void automatic(boolean value){prefs().edit().putBoolean("automatic",value).apply();configure(context);emit();}
    void observe(Runnable listener,boolean add){if(add){listeners.add(listener);listener.run();}else listeners.remove(listener);}
    void emit(){main.post(()->{for(Runnable listener:listeners)listener.run();});}
    public static void configure(Context c){JobScheduler jobs=c.getSystemService(JobScheduler.class);UpdateManager m=get(c);if(!m.automatic()){jobs.cancel(JOB_ID);return;}JobInfo existing=jobs.getPendingJob(JOB_ID);if(existing!=null&&existing.getIntervalMillis()==UpdatePolicy.CHECK_INTERVAL)return;JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(c,UpdateJobService.class)).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(UpdatePolicy.CHECK_INTERVAL,60*60*1000L).build();if(jobs.schedule(job)!=JobScheduler.RESULT_SUCCESS){m.status="Android nie przyjął sprawdzania w tle. Możesz sprawdzić ręcznie.";m.emit();}}
    public synchronized void check(boolean manual,Runnable done){
        if(busy||(!manual&&(!automatic()||!UpdatePolicy.due(System.currentTimeMillis(),prefs().getLong("checked",0))))){if(done!=null)main.post(done);return;}
        busy=true;status="Sprawdzam dostępność nowej wersji…";emit();
        worker.execute(()->{try{
            UpdateRelease found=UpdateRelease.parse(transport.manifest());
            prefs().edit().putLong("checked",System.currentTimeMillis()).apply();
            if(found.code>installedCode(context)){if(release==null||!release.json().equals(found.json()))ready=false;release=found;prefs().edit().putString("release",found.json()).apply();status="Dostępna wersja "+found.name;notifyAvailable(found);}
            else{release=null;ready=false;prefs().edit().remove("release").apply();apk().delete();status="Masz najnowszą opublikowaną wersję.";}
        }catch(UpdateTransport.NoRelease e){prefs().edit().putLong("checked",System.currentTimeMillis()).apply();status="Nie opublikowano jeszcze wydania Androida.";}
        catch(Exception e){status="Nie udało się sprawdzić aktualizacji. Sprawdź połączenie i spróbuj ponownie.";}
        finally{busy=false;emit();if(done!=null)main.post(done);}});
    }
    public synchronized void download(){
        if(busy||release==null)return;UpdateRelease target=release;busy=true;ready=false;cancelled=false;percent=0;status="Pobieram aktualizację…";emit();
        worker.execute(()->{File part=new File(context.getCacheDir(),"app-update.part");try{
            part.delete();transport.download(target,part,p->{percent=p;emit();},()->cancelled);if(cancelled)throw new InterruptedIOException();UpdateApk.verify(context,part,target);
            java.nio.file.Files.move(part.toPath(),apk().toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);ready=true;status="Pobrano i sprawdzono wersję "+target.name+". Możesz ją zainstalować.";
        }catch(Exception e){status=cancelled?"Pobieranie anulowane.":"Nie pobrano poprawnej aktualizacji. Plik nie zostanie zainstalowany.";ready=false;}
        finally{part.delete();busy=false;emit();}});
    }
    public void cancel(){cancelled=true;}
    public synchronized void install(){
        if(busy||release==null)return;
        if(!context.getPackageManager().canRequestPackageInstalls()){status="Zezwól LibrusApp na instalowanie aktualizacji, a następnie wybierz Zainstaluj ponownie.";emit();return;}
        UpdateRelease target=release;busy=true;status="Weryfikuję plik przed instalacją…";emit();
        worker.execute(()->{int sessionId=-1;try{
            UpdateApk.verify(context,apk(),target);PackageInstaller installer=context.getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(context.getPackageName());params.setSize(target.size);if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            sessionId=installer.createSession(params);
            try(PackageInstaller.Session session=installer.openSession(sessionId)){
                try(InputStream input=new FileInputStream(apk());OutputStream output=session.openWrite("base.apk",0,target.size)){
                    byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1)output.write(b,0,n);session.fsync(output);
                }
                prefs().edit().putInt("install_session",sessionId).apply();Intent result=new Intent(context,UpdateInstallReceiver.class).setAction("pl.librushome.android.UPDATE_INSTALL").setData(android.net.Uri.parse("librusapp://install/"+sessionId));
                int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);PendingIntent callback=PendingIntent.getBroadcast(context,sessionId,result,flags);session.commit(callback.getIntentSender());
            }status="Potwierdź aktualizację w oknie Androida.";
        }catch(Exception e){if(sessionId>=0)try{context.getPackageManager().getPackageInstaller().abandonSession(sessionId);}catch(Exception ignored){}prefs().edit().remove("install_session").apply();status="Nie uruchomiono instalacji. Plik lub zgoda Androida nie są poprawne.";ready=false;}
        finally{busy=false;emit();}});
    }
    void installationResult(int statusCode){prefs().edit().remove("install_session").apply();status=statusCode==PackageInstaller.STATUS_SUCCESS?"Aktualizacja zainstalowana.":statusCode==PackageInstaller.STATUS_FAILURE_ABORTED?"Instalacja anulowana. Możesz spróbować ponownie.":"Android nie zainstalował aktualizacji. Spróbuj ponownie.";emit();}
    private void notifyAvailable(UpdateRelease r){
        NotificationManager n=context.getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel(CHANNEL,"Aktualizacje LibrusApp",NotificationManager.IMPORTANCE_DEFAULT));
        if(!n.areNotificationsEnabled()||r.code<=prefs().getLong("notified",0))return;
        Intent intent=new Intent(context,MainActivity.class).setData(android.net.Uri.parse("librusapp://updates")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap=PendingIntent.getActivity(context,1703,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{n.notify("app-update",1703,new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("LibrusApp · Nowa wersja "+r.name).setContentText("Otwórz aplikację, aby pobrać aktualizację.").setContentIntent(tap).setAutoCancel(true).build());prefs().edit().putLong("notified",r.code).apply();}catch(SecurityException ignored){}
    }
}
