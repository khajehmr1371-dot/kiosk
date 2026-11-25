package ir.bastiyan.kiosk.display;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;

/** Native R114-style fixed viewport. No scrolling or browser in the public display. */
final class NativeBoard extends FrameLayout {
    final Activity ctx; final NativeDecor decor; final Runnable settings,toggle;
    final Handler ui=new Handler(Looper.getMainLooper());
    final FrameLayout header,stage,queue,ticker,footer;
    MediaPanel media; JSONObject state; KioskApi api;
    JSONObject set=new JSONObject(),ds=new JSONObject();
    String center="باستیان کیوسک", signature="", connectionError="";
    boolean online=false,sound=true,mediaVisible=false;
    int page=0, pages=1; long nextPage=0;
    Fit clock,date,connection,soundLabel; TextView tickerLabel;
    String activePageKey="";
    final Runnable heartbeat=new Runnable(){public void run(){
        updateClock(); if(pages>1 && SystemClock.uptimeMillis()>=nextPage){page=(page+1)%pages;nextPage=SystemClock.uptimeMillis()+12000;drawQueue();}
        ui.postDelayed(this,1000);
    }};
    NativeBoard(Activity a,NativeDecor d,Runnable settings,Runnable toggle){
        super(a);ctx=a;decor=d;this.settings=settings;this.toggle=toggle;
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);setClipChildren(true);
        header=frame();stage=frame();queue=frame();ticker=frame();footer=frame();
        addView(header);addView(stage);stage.addView(queue);addView(ticker);addView(footer);
    }
    FrameLayout frame(){FrameLayout f=new FrameLayout(ctx);f.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);f.setClipChildren(true);return f;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();ui.removeCallbacks(heartbeat);ui.post(heartbeat);}
    @Override protected void onDetachedFromWindow(){ui.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(w>0&&h>0)post(this::layoutBoard);}
    void attachMedia(MediaPanel m){media=m;stage.addView(m);m.setVisibility(GONE);}
    void setMediaVisible(boolean v){if(mediaVisible==v)return;mediaVisible=v;layoutBoard();}
    void setSound(boolean v){sound=v;if(soundLabel!=null)soundLabel.setText(v?"صدا روشن":"صدا خاموش");}
    void setConnection(boolean v,String error){boolean changed=v!=online;online=v;connectionError=error==null?"":error;updateConnection();if(changed)drawQueue();}
    JSONObject obj(JSONObject o,String key){JSONObject v=o==null?null:o.optJSONObject(key);return v==null?new JSONObject():v;}
    int c(String key,int def){return NativeDecor.color(ds,key,def);}
    int clamp(int x,int lo,int hi){return Math.max(lo,Math.min(hi,x));}
    int sx(double n){return Math.max(1,(int)Math.round(n*getWidth()/1280.0));}
    int sy(double n){return Math.max(1,(int)Math.round(n*getHeight()/720.0));}
    float scale(String key){return clamp(ds.optInt(key,100),60,150)/100f;}
    void place(FrameLayout parent,View v,int x,int y,int w,int h){
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.max(1,w),Math.max(1,h),Gravity.TOP|Gravity.LEFT);
        p.leftMargin=Math.max(0,x);p.topMargin=Math.max(0,y);
        if(v.getParent()==parent)v.setLayoutParams(p);else parent.addView(v,p);
    }
    GradientDrawable shape(int fill,int border,int rad){return NativeDecor.surface(fill,border,rad,Math.max(1,sx(1)));}
    GradientDrawable grad(int a,int b,int border,int rad){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(rad);g.setStroke(Math.max(1,sx(1)),border);return g;}
    static int blend(int a,int b,float r){return Color.rgb((int)(Color.red(a)*(1-r)+Color.red(b)*r),(int)(Color.green(a)*(1-r)+Color.green(b)*r),(int)(Color.blue(a)*(1-r)+Color.blue(b)*r));}
    static String fa(String s){if(s==null)return "";String digits="۰۱۲۳۴۵۶۷۸۹";for(int i=0;i<10;i++)s=s.replace((char)('0'+i),digits.charAt(i));return s;}
    final class Fit extends TextView {
        float cap;
        Fit(String text,int color,float max,boolean bold,int gravity){
            super(ctx);cap=max;setTextColor(color);setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            setGravity(gravity);setSingleLine(true);setIncludeFontPadding(false);setEllipsize(TextUtils.TruncateAt.END);
            setPadding(0,0,0,0);setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);setText(text);
        }
        void fit(){
            if(getWidth()<=0||getHeight()<=0)return;float hi=Math.max(1,cap);Paint p=new Paint(getPaint());
            p.setTextSize(hi);String s=getText().toString();float w=p.measureText(s);Paint.FontMetrics fm=p.getFontMetrics();
            float size=Math.min(hi,Math.min((getWidth()-2)*hi/Math.max(1,w),(getHeight()-2)*hi/Math.max(1,fm.bottom-fm.top)));
            setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.max(1,size));
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fit();}
        @Override protected void onTextChanged(CharSequence t,int start,int before,int count){super.onTextChanged(t,start,before,count);if(cap>0)fit();}
    }
    Fit text(FrameLayout p,String str,int color,float max,boolean bold,int gravity,int x,int y,int w,int h){
        Fit f=new Fit(str,color,max,bold,gravity);place(p,f,x,y,w,h);return f;
    }
    void action(String title,int x,int y,int w,int h,Runnable run){
        Fit b=text(header,title,0xffe8f7fc,sx(15),true,Gravity.CENTER,x,y,w,h);
        b.setBackground(shape(0xff0b2738,0xff23576d,sx(10)));b.setFocusable(true);b.setClickable(true);
        b.setOnClickListener(v->run.run());b.setOnFocusChangeListener((v,focused)->b.setBackground(shape(focused?0xff21556c:0xff0b2738,focused?0xffffd76a:0xff23576d,sx(10))));
        if(run==toggle)soundLabel=b;
    }
    void render(JSONObject s,KioskApi client,String fallback,boolean connected){
        state=s;api=client;center=fallback;online=connected;set=obj(s,"settings");ds=obj(set,"displaySettings");
        String key=(s==null?"":s.optString("centerName")+"|"+set.toString()+"|"+s.optString("doctors")+"|"+s.optString("queues")+"|"+s.optString("tickers"));
        if(!key.equals(signature)){signature=key;layoutBoard();}else updateConnection();
    }
    void layoutBoard(){
        int w=getWidth(),h=getHeight();if(w<2||h<2)return;
        boolean modern="modern".equals(ds.optString("style","modern"));
        int hh=sy(modern?108:86),th=ds.optBoolean("showTicker",true)?sy(58):0,fh=ds.optBoolean("showBastianBrand",true)?sy(27):0;
        int sh=Math.max(1,h-hh-th-fh);
        setBackgroundColor(c("backgroundColor",0xff07111f));
        place(this,header,0,0,w,hh);place(this,stage,0,hh,w,sh);
        ticker.setVisibility(th>0?VISIBLE:GONE);if(th>0)place(this,ticker,0,hh+sh,w,th);
        footer.setVisibility(fh>0?VISIBLE:GONE);if(fh>0)place(this,footer,0,h-fh,w,fh);
        header.removeAllViews();header.setBackgroundColor(c("headerColor",0xff071725));
        int gap=sx(15),logo=Math.min(hh-sy(20),sx(72)),controlW=Math.min(sx(270),w/3),clockW=Math.min(sx(185),w/5);
        int brandRight=w-gap,brandLeft=controlW+clockW+2*gap;
        if(ds.optBoolean("showCenterLogo",true)){
            ImageView im=new ImageView(ctx);im.setScaleType(ImageView.ScaleType.FIT_CENTER);im.setPadding(sx(4),sx(4),sx(4),sx(4));
            im.setBackground(shape(Color.WHITE,c("lineColor",0xff294258),sx(15)));
            place(header,im,w-gap-logo,(hh-logo)/2,logo,logo);decor.image(api,set.optString("logoUrl"),im,R.drawable.app_icon);brandRight-=logo+gap;
        }
        int bw=Math.max(1,brandRight-brandLeft);
        text(header,state==null?center:state.optString("centerName",set.optString("clinicName",center)),0xfff5f8fb,sx(29)*scale("headerScale"),true,Gravity.RIGHT|Gravity.CENTER_VERTICAL,brandLeft,sy(8),bw,hh*36/100);
        text(header,set.optString("subtitle","سامانه هوشمند فراخوان نوبت"),0xffbcd5e2,sx(17)*scale("headerScale"),false,Gravity.RIGHT|Gravity.CENTER_VERTICAL,brandLeft,hh*44/100,bw,hh*23/100);
        date=text(header,"",0xfff4d6ab,sx(17),true,Gravity.RIGHT|Gravity.CENTER_VERTICAL,brandLeft,hh*69/100,bw,hh*24/100);
        clock=text(header,"",0xffe9fbff,sx(44),true,Gravity.CENTER,controlW+gap,sy(5),clockW,hh*62/100);
        clock.setVisibility(ds.optBoolean("showClock",true)?VISIBLE:GONE);
        connection=text(header,"",0xff63e6a0,sx(12),true,Gravity.CENTER,controlW+gap,hh*69/100,clockW,hh*24/100);
        int bh=Math.max(1,hh*40/100),cw=Math.max(1,(controlW-gap*2)/2);
        action("تنظیمات",gap,(hh-bh)/2,cw,bh,settings);action(sound?"صدا روشن":"صدا خاموش",gap+cw+gap/2,(hh-bh)/2,cw,bh,toggle);
        updateClock();updateConnection();
        ticker.removeAllViews();ticker.setBackgroundColor(c("tickerBackground",0xff07131f));footer.removeAllViews();footer.setBackgroundColor(0xff030913);
        if(th>0){
            int labelWidth=Math.min(w/3,sx(160));
            Fit lab=text(ticker,"زیرنویس نمایشگر",c("tickerLabelColor",0xff8be7ff),sx(18),true,Gravity.CENTER,w-labelWidth-gap,sy(9),labelWidth,th-sy(18));
            lab.setBackground(shape(0xff102b3b,0xff245267,sx(10)));
            TextView tr=new TextView(ctx);tr.setText(tickerMessage());tr.setTextColor(c("tickerTextColor",0xffffe7a0));tr.setTypeface(Typeface.DEFAULT,Typeface.BOLD);tr.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.min(th*.58f,sx(28)*scale("tickerScale")));
            tr.setSingleLine(true);tr.setTextDirection(View.TEXT_DIRECTION_RTL);tr.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);tr.setEllipsize(TextUtils.TruncateAt.MARQUEE);tr.setMarqueeRepeatLimit(-1);tr.setSelected(true);
            place(ticker,tr,gap,0,Math.max(1,w-labelWidth-3*gap),th);
        }
        if(fh>0)text(footer,"© هلدینگ باستیان  |  تهیه و توسعه توسط هلدینگ باستیان  |  "+AppUpdater.versionName(ctx),c("copyrightColor",0xff7891a1),sx(13),true,Gravity.CENTER,0,0,w,fh);
        int side=mediaVisible&&w>h?Math.max(w/4,sx(235)):w;
        if(mediaVisible&&media!=null&&w>h){place(stage,media,0,0,w-side,sh);place(stage,queue,w-side,0,side,sh);queue.bringToFront();}
        else {place(stage,queue,0,0,w,sh);if(media!=null){place(stage,media,0,0,w,sh);if(mediaVisible)media.bringToFront();}}
        drawQueue();
    }
    String tickerMessage(){
        JSONArray a=state==null?null:state.optJSONArray("tickers");ArrayList<String> out=new ArrayList<>();
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j!=null&&j.optBoolean("active",true)&&!j.optString("text").isEmpty())out.add(j.optString("text"));}
        String s=out.isEmpty()?"لطفاً به اعلام شماره نوبت توجه فرمایید":TextUtils.join("     ◆     ",out);
        return s+"     ◆     "+s+"     ◆     "+s;
    }
    void updateClock(){
        TimeZone tz=TimeZone.getTimeZone("Asia/Tehran");Date now=new Date();
        if(clock!=null){SimpleDateFormat f=new SimpleDateFormat("HH:mm:ss",Locale.ROOT);f.setTimeZone(tz);clock.setText(fa(f.format(now)));}
        if(date!=null){
            if(Build.VERSION.SDK_INT>=24){
                android.icu.text.SimpleDateFormat f=new android.icu.text.SimpleDateFormat("EEEE، d MMMM y",new android.icu.util.ULocale("fa_IR@calendar=persian"));f.setTimeZone(android.icu.util.TimeZone.getTimeZone("Asia/Tehran"));date.setText(f.format(now));
            }else {SimpleDateFormat f=new SimpleDateFormat("yyyy/MM/dd",Locale.ROOT);f.setTimeZone(tz);date.setText(fa(f.format(now))+" میلادی");}
        }
    }
    void updateConnection(){if(connection==null)return;connection.setVisibility(ds.optBoolean("showConnection",true)||!online?VISIBLE:GONE);connection.setText(online?"● اتصال زنده و پایدار":"● قطع ارتباط — داده نامعتبر");connection.setTextColor(online?0xff63e6a0:0xffff7070);}
    static class Doc {JSONObject d,q; Doc(JSONObject d,JSONObject q){this.d=d;this.q=q;}}
    ArrayList<Doc> docs(){
        ArrayList<Doc> list=new ArrayList<>();if(state==null)return list;
        JSONArray d=state.optJSONArray("doctors"),q=state.optJSONArray("queues");if(d==null)return list;
        for(int i=0;i<d.length();i++){JSONObject x=d.optJSONObject(i);if(x==null||!x.optBoolean("active",true)||!x.optBoolean("isActive",true))continue;JSONObject current=null;
            if(q!=null)for(int j=0;j<q.length();j++){JSONObject t=q.optJSONObject(j);if(t!=null&&x.optString("id").equals(t.optString("doctorId"))&&"calling".equals(t.optString("status"))&&t.optBoolean("isActive",true)){current=t;break;}}
            list.add(new Doc(x,current));
        }
        // Calling doctors remain visible immediately, even when there are several pages.
        list.sort((a,b)->Boolean.compare(b.q!=null,a.q!=null));return list;
    }
    JSONObject qr(){
        if(!ds.optBoolean("showQr",true)||mediaVisible)return null;
        for(JSONArray a:new JSONArray[]{ds.optJSONArray("qrCodes"),set.optJSONArray("appointmentLinks")})if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optBoolean("active",true)&&(!o.optString("imageUrl").isEmpty()||!o.optString("url").isEmpty()))return o;}
        if(!set.optString("appointmentUrl").isEmpty())try{return new JSONObject().put("url",set.optString("appointmentUrl")).put("name",set.optString("appointmentSiteName","دریافت نوبت آنلاین")).put("description",set.optString("appointmentDescription"));}catch(Exception ignored){}
        return null;
    }
    void drawQueue(){
        if(getWidth()<2||getHeight()<2)return;int w=queue.getLayoutParams()==null?0:queue.getLayoutParams().width,h=queue.getLayoutParams()==null?0:queue.getLayoutParams().height;
        if(w<2||h<2)return;queue.removeAllViews();
        int gap=Math.max(2,sx(clamp(ds.optInt("cardGap",12),4,32))),margin=Math.max(2,sx(mediaVisible?7:26));
        int left=margin,right=w-margin;JSONObject qr=qr();
        if(qr!=null&&w>=h){int qw=Math.min(w/5,Math.min(sx(clamp(ds.optInt("qrSize",185),100,320)),h*55/100)),qh=Math.min(h-2*gap,qw+sy(72));boolean onRight="right".equals(ds.optString("qrPosition"));
            int x=onRight?w-margin-qw:margin;qrCard(qr,x,(h-qh)/2,qw,qh);if(onRight)right=x-gap;else left=x+qw+gap;
        }
        int top=gap,bottom=h-gap;ArrayList<Doc> list=docs();int total=list.size();
        if(total==0){pages=1;page=0;text(queue,state==null?"در حال دریافت اطلاعات مرکز":"پزشک فعالی برای نمایش وجود ندارد",0xffb6cbd7,sx(36),true,Gravity.CENTER,left,top,right-left,bottom-top);return;}
        int capacity=mediaVisible?3:(w>=h?6:4);int newPages=(total+capacity-1)/capacity;
        String active="";for(Doc d:list)if(d.q!=null)active+=d.d.optString("id")+":"+d.q.optString("number")+";";
        if(!active.equals(activePageKey)||pages!=newPages){page=0;activePageKey=active;nextPage=SystemClock.uptimeMillis()+12000;}
        pages=newPages;page=Math.min(page,pages-1);int first=page*capacity,n=Math.min(capacity,total-first);
        if(pages>1){bottom-=sy(20);text(queue,"صفحه "+fa(""+(page+1))+" از "+fa(""+pages),0xff9db5c5,sx(13),true,Gravity.CENTER,left,bottom,right-left,sy(20));}
        int areaW=right-left,areaH=bottom-top;
        int rows=n<=2?1:2;
        if(mediaVisible||w<h)rows=n;
        int cols=(n+rows-1)/rows;
        if(n==3&&w>=h&&!mediaVisible)rows=1;
        if(n==3&&rows==1)cols=3;
        // For modern view, one calling doctor receives the larger right-hand card.
        boolean hero="modern".equals(ds.optString("style","modern"))&&n>=2&&n<=4&&w>=h&&!mediaVisible&&list.get(first).q!=null;
        if(hero){int heroW=(areaW-gap)* (n==2?60:62)/100;card(list.get(first),right-heroW,top,heroW,areaH,true);
            int smallW=areaW-heroW-gap;for(int i=1;i<n;i++){int y=top+(i-1)*(areaH+gap)/(n-1),y2=top+i*(areaH+gap)/(n-1)-gap;card(list.get(first+i),left,y,smallW,y2-y,false);}return;}
        for(int r=0,index=0;r<rows&&index<n;r++){
            int remaining=n-index,k=Math.min(cols,remaining);int y=top+r*(areaH+gap)/rows,y2=top+(r+1)*(areaH+gap)/rows-gap;
            for(int c=0;c<k;c++,index++){int x2=right-c*(areaW+gap)/k,x=right-(c+1)*(areaW+gap)/k+gap;card(list.get(first+index),x,y,x2-x,y2-y,n==1);}
        }
    }
    void card(Doc entry,int x,int y,int w,int h,boolean large){
        if(w<2||h<2)return;JSONObject d=entry.d,q=entry.q;boolean calling=q!=null,closed=d.optBoolean("bookingClosed",false);
        int line=c("lineColor",0xff294258),gold=c("callingColor",0xffffd76a),cardColor=c("cardColor",0xff0f2232),fg=c("numberColor",0xffffe071);
        FrameLayout box=frame();box.setBackground(grad(blend(cardColor,0xff294258,.10f),blend(cardColor,0xff07111f,.32f),calling?gold:closed?0xfffb7185:line,sx(clamp(ds.optInt("cardRadius",22),8,42))));box.setClipToOutline(true);place(queue,box,x,y,w,h);
        int head=Math.min(h/3,Math.max(sy(48),h*11/100)),foot=Math.min(h/5,Math.max(sy(30),h*7/100)),pad=Math.min(w/10,sx(13)),inner=Math.max(1,w-2*pad);
        FrameLayout dh=frame();dh.setBackgroundColor(c("doctorHeaderColor",0xff0b2130));place(box,dh,1,1,w-2,Math.max(1,head-1));
        int photo=0;
        String raw=d.optString("photoUrl",d.optString("photo"));if(ds.optBoolean("showDoctorPhoto",true)&&!raw.isEmpty()){
            photo=Math.min(head-2*pad,sx(58));if(photo>5){ImageView im=new ImageView(ctx);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setBackground(shape(0xff173346,line,sx(9)));im.setClipToOutline(true);place(dh,im,w-pad-photo,(head-photo)/2,photo,photo);decor.image(api,raw,im,0);}}
        int roomW=ds.optBoolean("showRoom",true)?Math.min(inner/3,sx(115)):0;
        if(roomW>0){Fit room=text(dh,"اتاق "+fa(d.optString("room").replaceFirst("^اتاق\\s*","")),c("roomColor",0xff8be7ff),sx(17),true,Gravity.CENTER,pad,head/4,roomW,head/2);room.setBackground(shape(0xff103c50,0xff286078,sx(10)));}
        int nameLeft=pad+(roomW>0?roomW+pad:0),nameRight=w-pad-(photo>0?photo+pad:0),nw=Math.max(1,nameRight-nameLeft);
        text(dh,d.optString("name","پزشک مرکز"),c("doctorNameColor",Color.WHITE),sx(29)*scale("doctorScale"),true,Gravity.RIGHT|Gravity.CENTER_VERTICAL,nameLeft,head/12,nw,head*46/100);
        text(dh,d.optString("specialty"),c("doctorSpecialtyColor",0xffb9ceda),sx(17)*scale("doctorScale"),false,Gravity.RIGHT|Gravity.CENTER_VERTICAL,nameLeft,head*54/100,nw,head*40/100);
        View rule=new View(ctx);rule.setBackgroundColor(line);place(box,rule,1,head,w-2,1);
        int centerH=h-head-foot;String number=calling?fa(q.optString("number",q.optString("queueNumber","—"))):"—";
        if(!online)number="—";
        String patient=calling&&online&&ds.optBoolean("showPatientName",true)?q.optString("patientName"):"";
        if("بدون نام".equals(patient))patient="";
        int numberHeight=centerH*(patient.isEmpty()?78:65)/100;
        // Measure glyph width AND font height; 1–4 digits can never be cropped by fixed pixels.
        Fit num=text(box,number,calling?gold:fg,Math.max(1,Math.min(numberHeight*.98f,sx(large?300:180)*scale("numberScale"))),true,Gravity.CENTER,pad,head+sy(2),inner,Math.max(1,numberHeight-sy(2)));
        num.setTextDirection(View.TEXT_DIRECTION_LTR);if(calling)num.setShadowLayer(sx(14),0,0,blend(gold,cardColor,.6f));
        int below=head+numberHeight;
        if(!patient.isEmpty()){int ph=centerH*15/100;text(box,patient,c("patientColor",Color.WHITE),sx(25),true,Gravity.CENTER,pad,below,inner,ph);below+=ph;}
        String hint=!online?"ارتباط قطع است — در انتظار اطلاعات معتبر":calling?"لطفاً به اتاق پزشک مراجعه فرمایید":closed?"پذیرش نوبت جدید پایان یافته است":"در انتظار فراخوان";
        text(box,hint,c("hintColor",0xff9db5c5),sx(18),false,Gravity.CENTER,pad,below,inner,Math.max(1,h-foot-below));
        FrameLayout st=frame();st.setBackgroundColor(calling?blend(cardColor,gold,.08f):closed?0xff411d31:blend(cardColor,0xff030913,.4f));place(box,st,1,h-foot,w-2,foot-1);
        String status=!online?"اطلاعات لحظه‌ای در دسترس نیست":calling?"🔔 شماره "+number+" فراخوان شد":closed?"پذیرش جدید پایان یافته است":"آماده فراخوان";
        text(st,status,!online?0xffff7070:calling?gold:closed?0xfffecdd3:c("statusColor",0xff8be9ad),sx(16),true,Gravity.CENTER,pad,0,inner,foot-1);
    }
    void qrCard(JSONObject q,int x,int y,int w,int h){
        FrameLayout panel=frame();panel.setBackground(shape(0xff08151f,0xff1d5266,sx(17)));place(queue,panel,x,y,w,h);
        int pad=sx(8),title=sy(25),img=Math.min(w-2*pad,h-title-sy(42));if(img<=0)return;
        text(panel,"دریافت نوبت اینترنتی",0xff8be7ff,sx(14),true,Gravity.CENTER,pad,0,w-2*pad,title);
        ImageView v=new ImageView(ctx);v.setBackgroundColor(Color.WHITE);v.setScaleType(ImageView.ScaleType.FIT_CENTER);place(panel,v,(w-img)/2,title,img,img);
        text(panel,q.optString("name","نوبت آنلاین"),0xffeafaff,sx(14),true,Gravity.CENTER,pad,title+img,w-2*pad,sy(21));
        text(panel,q.optString("description","با دوربین گوشی اسکن کنید"),0xff91a9b7,sx(11),false,Gravity.CENTER,pad,title+img+sy(21),w-2*pad,Math.max(1,h-title-img-sy(21)));
        final String image=decor.resolve(api,q.optString("imageUrl")),url=q.optString("url"),key="qr:"+image+"|"+url;
        Bitmap cached=decor.cache.get(key);if(cached!=null){v.setImageBitmap(cached);return;}
        decor.worker.execute(()->{try{Bitmap b=null;if(!image.isEmpty())try{b=decor.fetch(image);}catch(Exception ignored){}if(b==null&&!url.isEmpty())b=decor.qrBitmap(url);final Bitmap result=b;if(result!=null){decor.cache.put(key,result);ctx.runOnUiThread(()->v.setImageBitmap(result));}}catch(Exception ignored){}});
    }
}
