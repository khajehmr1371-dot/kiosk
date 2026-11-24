package ir.bastiyan.kiosk.display;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Read-only release checker. This display does not download or install executable packages. */
final class AppUpdater {
 interface Idle {boolean ready();}
 final Activity a;final Idle idle;
 final Handler ui=new Handler(Looper.getMainLooper());
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 boolean busy=false,closed=false;
 long lastCheck=0;int prompted=0;
 AlertDialog dialog;
 AppUpdater(Activity a,Idle idle){this.a=a;this.idle=idle;}
 static String versionName(Activity a){try{return a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}catch(Exception e){return "1.1.5-R115";}}
 int code()throws Exception{return a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionCode;}
 void close(){closed=true;ui.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();worker.shutdownNow();}
 void onStart(String server){if(System.currentTimeMillis()-lastCheck>6*3600*1000L)check(server,false);}
 void onResume(String server){onStart(server);}
 void toast(String s){if(!closed)Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
 void check(String server,boolean manual){
   if(closed||busy)return;
   if(server==null||server.isEmpty()){if(manual)toast("ابتدا به سامانه وارد شوید");return;}
   busy=true;lastCheck=System.currentTimeMillis();
   if(manual)toast("در حال بررسی نسخه جدید…");
   worker.execute(()->{try{
     String host=EndpointResolver.normalizeBase(server);
     HttpURLConnection c=(HttpURLConnection)new URL(host+"/android-tv-update.json").openConnection();
     c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(7000);c.setReadTimeout(10000);
     ByteArrayOutputStream out=new ByteArrayOutputStream();
     try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
       try(InputStream in=c.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new IOException("manifest size");out.write(b,0,n);}}}finally{c.disconnect();}
     JSONObject j=new JSONObject(out.toString("UTF-8"));int v=j.getInt("versionCode");
     if(!a.getPackageName().equals(j.getString("package"))||!j.getString("sha256").matches("[a-f0-9]{64}"))throw new IOException("manifest");
     int current=code();
     ui.post(()->{busy=false;if(closed)return;if(v<=current){if(manual)toast("آخرین نسخه نصب است: "+versionName(a));return;}if(manual||v!=prompted)offer(host,j,manual,0);});
   }catch(Exception e){ui.post(()->{busy=false;if(manual)toast("بررسی نسخه ناموفق بود؛ اتصال یا انتشار نسخه را بررسی کنید");});}});
 }
 void offer(String host,JSONObject j,boolean manual,int tries){
   if(closed||a.isFinishing()||dialog!=null&&dialog.isShowing())return;
   if(!idle.ready()){if(tries<12)ui.postDelayed(()->offer(host,j,manual,tries+1),5000);return;}
   prompted=j.optInt("versionCode");
   String page=host+"/android-tv.html";
   dialog=new AlertDialog.Builder(a).setTitle("نسخه جدید نمایشگر موجود است")
     .setMessage("نسخه فعلی: "+versionName(a)+"\nنسخه جدید: "+j.optString("versionName")+"\n"+j.optString("notes")+"\nصفحه رسمی انتشار:\n"+page+"\nاین برنامه فقط انتشار نسخه را بررسی می‌کند و هیچ نرم‌افزاری نصب نمی‌کند.")
     .setPositiveButton("صفحه رسمی انتشار",(d,w)->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(page)));}catch(Exception e){toast("در مرورگر دستگاه دیگری نشانی صفحه انتشار را باز کنید: "+page);}})
     .setNegativeButton("بعداً",null).create();dialog.show();
 }
}
