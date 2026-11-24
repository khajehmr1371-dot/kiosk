package ir.bastiyan.kiosk.display;
import android.content.*;
public final class UpdateInstallReceiver extends BroadcastReceiver {
    static volatile AppUpdater active;
    @Override public void onReceive(Context c,Intent i){
        if(i==null||!(c.getPackageName()+".UPDATE_STATUS").equals(i.getAction()))return;
        AppUpdater u=active;
        if(u!=null)u.receive(i);
    }
}
