package ir.bastiyan.kiosk.display;
import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

final class NativeDecor {
    final Activity ctx;
    final ExecutorService worker=Executors.newFixedThreadPool(2);
    final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){
        protected int sizeOf(String k,Bitmap b){return b.getByteCount();}
    };
    NativeDecor(Activity a){ctx=a;}
    void close(){worker.shutdownNow();cache.evictAll();}
    int dp(int n){return Math.round(n*ctx.getResources().getDisplayMetrics().density);}
    static int color(JSONObject s,String k,int fallback){try{return Color.parseColor(s.optString(k));}catch(Exception e){return fallback;}}
    static GradientDrawable surface(int bg,int border,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(radius);d.setStroke(stroke,border);return d;}
    private String resolve(KioskApi api,String raw){
        if(api==null||raw==null||raw.isEmpty())return "";
        if(raw.startsWith("data:image/")&&raw.length()<3500000)return raw;
        try{
            if(raw.startsWith("/api/media/logo?")||raw.startsWith("/api/media/qr?")){
                if(api.routeMode().equals("PHP مستقیم"))return api.origin()+"/api.php?route="+raw.substring(5).replace('?', '&');
                return api.origin()+raw;
            }
            if(raw.startsWith("/image/")&&!raw.contains(".."))return api.origin()+raw;
            URI u=new URI(raw);if("https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getRawUserInfo()==null)return raw;
        }catch(Exception ignored){}
        return "";
    }
    private Bitmap decode(byte[] data){
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
        if(o.outWidth<1||o.outHeight<1||o.outWidth>16000||o.outHeight>16000)return null;
        o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1024)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(data,0,data.length,o);
    }
    private Bitmap fetch(String url)throws Exception{
        if(url.startsWith("data:image/")){int p=url.indexOf(',');if(p<0||!url.substring(0,p).endsWith(";base64"))return null;return decode(android.util.Base64.decode(url.substring(p+1),android.util.Base64.DEFAULT));}
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        try{if(c.getResponseCode()!=200||c.getContentType()==null||!c.getContentType().startsWith("image/"))return null;
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>4000000)return null;out.write(b,0,n);}}
            return decode(out.toByteArray());
        }finally{c.disconnect();}
    }
    void image(KioskApi api,String raw,ImageView target,int fallback){
        String key=resolve(api,raw);if(fallback!=0)target.setImageResource(fallback);target.setTag(key);
        if(key.isEmpty())return;Bitmap b=cache.get(key);if(b!=null){target.setImageBitmap(b);return;}
        worker.execute(()->{try{Bitmap x=fetch(key);if(x!=null){cache.put(key,x);ctx.runOnUiThread(()->{if(key.equals(target.getTag()))target.setImageBitmap(x);});}}catch(Exception ignored){}});
    }
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(ctx);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);t.setMaxLines(2);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Bitmap qrBitmap(String value)throws Exception{
        java.util.Map<EncodeHintType,Object> hints=new java.util.EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.MARGIN,4);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
        BitMatrix m=new QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,420,420,hints);
        int[] px=new int[420*420];for(int y=0;y<420;y++)for(int x=0;x<420;x++)px[y*420+x]=m.get(x,y)?Color.BLACK:Color.WHITE;
        Bitmap b=Bitmap.createBitmap(420,420,Bitmap.Config.ARGB_8888);b.setPixels(px,0,420,0,0,420,420);return b;
    }
    void qr(KioskApi api,JSONObject set,JSONObject ds,LinearLayout panel,LinearLayout row,boolean tv){
        JSONObject q=null;JSONArray codes=ds.optJSONArray("qrCodes");
        for(int pass=0;pass<2&&q==null;pass++){if(pass==1)codes=set.optJSONArray("appointmentLinks");if(codes!=null)for(int i=0;i<codes.length();i++){JSONObject item=codes.optJSONObject(i);if(item!=null&&item.optBoolean("active",true)&&(!item.optString("imageUrl").isEmpty()||!item.optString("url").isEmpty())){q=item;break;}}}
        if(q==null&&!set.optString("appointmentUrl").isEmpty()){q=new JSONObject();try{q.put("url",set.optString("appointmentUrl")).put("name",set.optString("appointmentSiteName","نوبت اینترنتی"));}catch(Exception ignored){}}
        if(!ds.optBoolean("showQr",true)||q==null){panel.setVisibility(View.GONE);return;}
        int width=Math.max(100,Math.min(Math.min(260,ctx.getResources().getConfiguration().screenWidthDp/4),ds.optInt("qrSize",185)));
        if(tv){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)panel.getLayoutParams();lp.width=dp(width+20);panel.setLayoutParams(lp);
            int desired="right".equals(ds.optString("qrPosition"))?0:row.getChildCount()-1;if(row.indexOfChild(panel)!=desired){row.removeView(panel);row.addView(panel,desired,lp);}}
        panel.setVisibility(View.VISIBLE);panel.setBackground(surface(color(ds,"cardColor",0xff102739),color(ds,"lineColor",0xff294258),dp(16),dp(1)));
        String url=q.optString("url"),img=q.optString("imageUrl"),name=q.optString("name","نوبت اینترنتی");
        String sig=(api==null?"":api.origin())+"|"+url+"|"+img+"|"+name+"|"+width;
        if(sig.equals(panel.getTag())&&panel.getChildCount()>0)return;
        panel.setTag(sig);panel.removeAllViews();panel.addView(text("دریافت نوبت آنلاین",tv?17:14,0xffd7edf5,true));
        ImageView v=new ImageView(ctx);v.setScaleType(ImageView.ScaleType.FIT_CENTER);int qrSize=dp(tv?width:112);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(qrSize,qrSize);qp.gravity=Gravity.CENTER;qp.topMargin=dp(10);qp.bottomMargin=dp(10);panel.addView(v,qp);
        panel.addView(text(name,tv?15:12,Color.WHITE,true));panel.addView(text("با دوربین گوشی اسکن کنید",tv?13:11,0xffb9ceda,false));
        worker.execute(()->{try{Bitmap b=null;String src=resolve(api,img);if(!src.isEmpty())try{b=fetch(src);}catch(Exception ignored){}if(b==null&&!url.isEmpty())b=qrBitmap(url);final Bitmap x=b;ctx.runOnUiThread(()->{if(sig.equals(panel.getTag())&&x!=null)v.setImageBitmap(x);else if(sig.equals(panel.getTag()))panel.setVisibility(View.GONE);});}catch(Exception e){ctx.runOnUiThread(()->{if(sig.equals(panel.getTag()))panel.setVisibility(View.GONE);});}});
    }
}
