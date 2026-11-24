package ir.bastiyan.kiosk.display;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import org.json.JSONObject;

final class AppUpdater {
    interface Idle {boolean ready();}
    final Activity a;final Idle idle;final Handler ui=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();
    boolean busy=false,closed=false;long lastCheck=0;int prompted=0;JSONObject available;String base="";
    File pending;
    AppUpdater(Activity a,Idle idle){this.a=a;this.idle=idle;}
    static String versionName(Activity a){try{return a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}catch(Exception e){return "1.1.2-R112";}}
    int code()throws Exception{return a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionCode;}
    void close(){closed=true;ui.removeCallbacksAndMessages(null);worker.shutdownNow();}
    void onStart(String server){if(System.currentTimeMillis()-lastCheck>6*3600*1000L)check(server,false);}
    void toast(String s){if(!closed)Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
    HttpURLConnection open(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(7000);c.setReadTimeout(15000);
        if(c.getResponseCode()!=200){int n=c.getResponseCode();c.disconnect();throw new IOException("HTTP "+n);}
        return c;
    }
    void check(String server,boolean manual){
        if(closed||busy)return;if(server==null||server.isEmpty()){if(manual)toast("ابتدا به سامانه وارد شوید");return;}
        if(manual&&pending!=null&&pending.isFile()){install();return;}
        busy=true;lastCheck=System.currentTimeMillis();
        if(manual)toast("در حال بررسی نسخه جدید…");
        worker.execute(()->{try{
            String host=EndpointResolver.normalizeBase(server);HttpURLConnection c=open(host+"/android-tv-update.json");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new IOException("فهرست نسخه نامعتبر است");out.write(b,0,n);}}finally{c.disconnect();}
            JSONObject j=new JSONObject(out.toString("UTF-8"));int v=j.getInt("versionCode");
            if(!a.getPackageName().equals(j.getString("package"))||!j.getString("sha256").matches("[a-f0-9]{64}")||!"/android-tv-download.php".equals(j.getString("path"))||j.getLong("bytes")<500000||j.getLong("bytes")>100000000)throw new IOException("مشخصات نسخه معتبر نیست");
            int current=code();
            ui.post(()->{busy=false;if(closed)return;if(v<=current){if(manual)toast("آخرین نسخه نصب است: "+versionName(a));return;}available=j;base=host;if(manual||prompted!=v)offer(manual,0);});
        }catch(Exception e){ui.post(()->{busy=false;if(manual)toast("بررسی نسخه ناموفق بود؛ اتصال یا انتشار نسخه را بررسی کنید");});}});
    }
    void offer(boolean manual,int attempts){
        if(closed||available==null||a.isFinishing())return;
        if(!idle.ready()){if(attempts<12)ui.postDelayed(()->offer(manual,attempts+1),5000);return;}
        JSONObject j=available;String host=base;prompted=j.optInt("versionCode");
        new AlertDialog.Builder(a).setTitle("نسخه جدید نمایشگر موجود است")
            .setMessage("نسخه فعلی: "+versionName(a)+"\nنسخه جدید: "+j.optString("versionName")+"\n"+j.optString("notes")+"\nتنظیمات و حساب فعلی حفظ می‌شوند. نصب را در زمان مناسب تأیید کنید.")
            .setPositiveButton("دریافت و نصب",(d,w)->download(host,j)).setNegativeButton("بعداً",null).show();
    }
    void download(String host,JSONObject j){
        if(busy||closed)return;busy=true;toast("در حال دریافت و بررسی امنیتی APK؛ نمایشگر فعال می‌ماند");
        worker.execute(()->{File tmp=new File(a.getCacheDir(),"update.part");try{
            HttpURLConnection c=open(host+"/android-tv-download.php");long expected=j.getLong("bytes"),total=0;
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){total+=n;if(total>expected)throw new IOException("size");digest.update(b,0,n);out.write(b,0,n);}}finally{c.disconnect();}
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(total!=expected||!hex.toString().equals(j.getString("sha256")))throw new IOException("hash");
            PackageManager pm=a.getPackageManager();
            PackageInfo candidate=pm.getPackageArchiveInfo(tmp.getAbsolutePath(),PackageManager.GET_SIGNATURES);
            PackageInfo current=pm.getPackageInfo(a.getPackageName(),PackageManager.GET_SIGNATURES);
            if(candidate==null||!current.packageName.equals(candidate.packageName)||candidate.versionCode<=current.versionCode||candidate.versionCode!=j.getInt("versionCode")||candidate.signatures==null||!java.util.Arrays.equals(candidate.signatures,current.signatures))throw new IOException("signature");
            File apk=new File(a.getCacheDir(),"update.apk");if(apk.exists()&&!apk.delete())throw new IOException("cache");if(!tmp.renameTo(apk))throw new IOException("rename");
            ui.post(()->{busy=false;pending=apk;install();});
        }catch(Exception e){tmp.delete();ui.post(()->{busy=false;toast("آپدیت رد شد: فایل، نسخه یا امضای دیجیتال معتبر نیست یا دانلود کامل نشد");});}});
    }
    void install(){
        if(closed||pending==null||!pending.isFile())return;
        if(!idle.ready()){toast("فایل آماده است؛ پس از پایان فراخوان از تنظیمات، بررسی نسخه جدید را بزنید.");return;}
        if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(a).setTitle("اجازه نصب نسخه جدید").setMessage("اندروید برای اولین بار اجازه نصب از همین برنامه را می‌خواهد. پس از دادن اجازه، به برنامه برگردید و دوباره «بررسی نسخه جدید» را بزنید.")
                .setPositiveButton("تنظیمات مجوز",(d,w)->{try{a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}catch(Exception e){toast("از تنظیمات امنیت دستگاه اجازه نصب از این برنامه را فعال کنید.");}}).setNegativeButton("بعداً",null).show();return;}
        try{
            Uri uri=Uri.parse("content://"+a.getPackageName()+".updates/update.apk");
            Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);a.startActivity(i);
        }catch(Exception e){toast("نصب‌کننده اندروید در دسترس نیست؛ فایل را از سامانه دریافت کنید.");}
    }
}
