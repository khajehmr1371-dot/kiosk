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
    boolean busy=false,closed=false,waitingPermission=false,installing=false;
    long lastCheck=0;int prompted=0,sessionId=-1;JSONObject available;String base="";
    File pending;AlertDialog offerDialog;
    AppUpdater(Activity a,Idle idle){this.a=a;this.idle=idle;UpdateInstallReceiver.active=this;}
    static String versionName(Activity a){try{return a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}catch(Exception e){return "1.1.3-R113";}}
    int code()throws Exception{return a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionCode;}
    void close(){closed=true;if(UpdateInstallReceiver.active==this)UpdateInstallReceiver.active=null;ui.removeCallbacksAndMessages(null);worker.shutdownNow();}
    void onStart(String server){if(System.currentTimeMillis()-lastCheck>6*3600*1000L)check(server,false);}
    void onResume(String server){
        if(waitingPermission){waitingPermission=false;if(Build.VERSION.SDK_INT<26||a.getPackageManager().canRequestPackageInstalls()){ui.postDelayed(()->install(),600);return;}message("مجوز نصب داده نشد","برای نصب، در تنظیمات اندروید اجازه نصب از برنامه باستیان را فعال کنید. فایل دریافت‌شده حفظ شده است.");return;}
        onStart(server);
    }
    void toast(String s){if(!closed)Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
    void message(String title,String text){if(!closed&&!a.isFinishing())new AlertDialog.Builder(a).setTitle(title).setMessage(text).setPositiveButton("متوجه شدم",null).show();}
    HttpURLConnection open(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(7000);c.setReadTimeout(20000);c.setRequestProperty("Cache-Control","no-cache");
        int n=c.getResponseCode();if(n!=200){c.disconnect();throw new IOException("HTTP "+n);}return c;
    }
    void check(String server,boolean manual){
        if(closed)return;
        if(busy||installing){if(manual)toast("دریافت یا نصب نسخه در حال انجام است");return;}
        if(server==null||server.isEmpty()){if(manual)message("نشانی سامانه","ابتدا به سامانه وارد شوید");return;}
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
            ui.post(()->{busy=false;if(closed)return;if(v<=current){if(manual)message("نسخه به‌روز است","نسخه فعلی: "+versionName(a)+"\nنسخه جدیدتری در سامانه منتشر نشده است.");return;}available=j;base=host;if(manual||prompted!=v)offer(manual,0);});
        }catch(Exception e){ui.post(()->{busy=false;if(manual)message("بررسی نسخه ناموفق بود","نشانی سرور، انتشار آپدیت و اتصال شبکه را بررسی کنید.\n"+e.getClass().getSimpleName()+": "+e.getMessage());});}});
    }
    void offer(boolean manual,int attempts){
        if(closed||available==null||a.isFinishing()||offerDialog!=null&&offerDialog.isShowing())return;
        if(!idle.ready()){if(attempts<12)ui.postDelayed(()->offer(manual,attempts+1),5000);else if(manual)toast("پس از پایان فراخوان دوباره بررسی نسخه را بزنید");return;}
        JSONObject j=available;String host=base;prompted=j.optInt("versionCode");
        offerDialog=new AlertDialog.Builder(a).setTitle("نسخه جدید نمایشگر موجود است")
            .setMessage("نسخه فعلی: "+versionName(a)+"\nنسخه جدید: "+j.optString("versionName")+"\n"+j.optString("notes")+"\nتنظیمات و حساب فعلی حفظ می‌شوند. نصب را خارج از زمان فراخوان تأیید کنید.")
            .setPositiveButton("دریافت و نصب",(d,w)->download(host,j)).setNegativeButton("بعداً",null).create();offerDialog.show();
    }
    void download(String host,JSONObject j){
        if(busy||closed)return;busy=true;toast("در حال دریافت و بررسی امنیتی APK؛ نمایشگر فعال می‌ماند");
        worker.execute(()->{File tmp=new File(a.getCacheDir(),"update-download.apk");try{
            HttpURLConnection c=open(host+"/android-tv-download.php");long expected=j.getLong("bytes"),total=0;
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){total+=n;if(total>expected)throw new IOException("اندازه فایل بیش از حد مجاز است");digest.update(b,0,n);out.write(b,0,n);}out.getFD().sync();}finally{c.disconnect();}
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(total!=expected||!hex.toString().equals(j.getString("sha256")))throw new IOException("هش یا اندازه فایل نادرست است");
            PackageManager pm=a.getPackageManager();
            PackageInfo candidate=pm.getPackageArchiveInfo(tmp.getAbsolutePath(),PackageManager.GET_SIGNATURES);
            PackageInfo current=pm.getPackageInfo(a.getPackageName(),PackageManager.GET_SIGNATURES);
            if(candidate==null||!current.packageName.equals(candidate.packageName)||candidate.versionCode<=current.versionCode||candidate.versionCode!=j.getInt("versionCode")||candidate.signatures==null||!java.util.Arrays.equals(candidate.signatures,current.signatures))throw new IOException("شناسه، نسخه یا امضا سازگار نیست");
            File apk=new File(a.getCacheDir(),"update.apk");if(apk.exists()&&!apk.delete())throw new IOException("فایل موقت قابل حذف نیست");if(!tmp.renameTo(apk))throw new IOException("ذخیره فایل ناموفق بود");
            ui.post(()->{busy=false;pending=apk;install();});
        }catch(Exception e){tmp.delete();ui.post(()->{busy=false;message("دانلود یا اعتبارسنجی آپدیت ناموفق بود",e.getClass().getSimpleName()+": "+e.getMessage());});}});
    }
    void install(){
        if(closed||installing)return;
        if(pending==null||!pending.isFile()){message("فایل نصب موجود نیست","دوباره بررسی نسخه جدید را بزنید.");return;}
        if(!idle.ready()){toast("فایل آماده است؛ پس از پایان فراخوان از تنظیمات، بررسی نسخه جدید را بزنید.");return;}
        if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(a).setTitle("اجازه نصب نسخه جدید").setMessage("اندروید برای اولین بار اجازه نصب از باستیان را می‌خواهد. پس از دادن اجازه به برنامه برگردید؛ درخواست نصب ادامه پیدا می‌کند.")
                .setPositiveButton("فعال‌کردن مجوز",(d,w)->{try{waitingPermission=true;a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}catch(Exception e){waitingPermission=false;message("تنظیمات مجوز","از تنظیمات امنیت دستگاه، اجازه نصب از باستیان را فعال کنید.");}}).setNegativeButton("بعداً",null).show();return;}
        installing=true;toast("فایل معتبر است؛ در حال باز کردن نصب‌کننده اندروید");
        File apk=pending;
        worker.execute(()->{
            int id=-1;PackageInstaller installer=a.getPackageManager().getPackageInstaller();
            try{
                PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(a.getPackageName());params.setSize(apk.length());
                if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                id=installer.createSession(params);sessionId=id;
                try(PackageInstaller.Session session=installer.openSession(id)){
                    try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("base.apk",0,apk.length())){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);}
                    Intent callback=new Intent(a,UpdateInstallReceiver.class).setAction(a.getPackageName()+".UPDATE_STATUS");
                    int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                    PendingIntent result=PendingIntent.getBroadcast(a,id,callback,flags);session.commit(result.getIntentSender());
                }
                ui.postDelayed(()->{if(installing&&!closed)toast("درخواست نصب ارسال شد؛ پنجره تأیید اندروید را بررسی کنید");},3500);
                ui.postDelayed(()->{if(installing&&!closed){installing=false;message("تأیید نصب در انتظار است","اگر پنجره نصب باز نشده، دوباره بررسی نسخه را بزنید یا فایل APK را از سامانه دریافت و با مدیر فایل باز کنید.");}},60000);
            }catch(Exception e){if(id>=0)try{installer.abandonSession(id);}catch(Exception ignored){}ui.post(()->{installing=false;fallback(e.getMessage());});}
        });
    }
    void receive(Intent result){
        if(closed)return;
        int status=result.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm=result.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm==null){installing=false;fallback("پنجره تأیید دریافت نشد");return;}
            try{a.startActivity(confirm);toast("نصب را در پنجره اندروید تأیید کنید");}catch(Exception e){installing=false;fallback(e.getMessage());}
        }else if(status==PackageInstaller.STATUS_SUCCESS){installing=false;toast("به‌روزرسانی انجام شد");}
        else{installing=false;message("نصب انجام نشد","کد: "+status+"\n"+result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)+"\nفایل معتبر حفظ شده است؛ دوباره اقدام کنید.");}
    }
    void fallback(String reason){
        try{
            Uri uri=Uri.parse("content://"+a.getPackageName()+".updates/update.apk");
            Intent i=new Intent(Intent.ACTION_INSTALL_PACKAGE,uri);i.setClipData(ClipData.newRawUri("APK",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try{a.startActivity(i);}catch(ActivityNotFoundException e){Intent view=new Intent(Intent.ACTION_VIEW);view.setDataAndType(uri,"application/vnd.android.package-archive");view.setClipData(ClipData.newRawUri("APK",uri));view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);a.startActivity(view);}
        }catch(Exception e){message("نصب‌کننده دستگاه در دسترس نیست","فایل را از صفحه دانلود سامانه دریافت کنید و با مدیر فایل دستگاه باز کنید.\n"+reason+"\n"+e.getMessage());}
    }
}
