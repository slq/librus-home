package pl.librushome.android;
import android.content.*;
import android.content.pm.PackageInstaller;

/** Only a system-filled explicit PendingIntent for our recorded session is accepted. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent result){UpdateManager m=UpdateManager.get(c);int session=result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1);if(session<0||session!=m.prefs().getInt("install_session",-2))return;int status=result.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=result.getParcelableExtra(Intent.EXTRA_INTENT);if(confirm==null)return;try{confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(confirm);}catch(Exception e){m.status="Otwórz LibrusApp i ponów instalację, aby potwierdzić aktualizację.";m.emit();}}
        else m.installationResult(status);
    }
}
