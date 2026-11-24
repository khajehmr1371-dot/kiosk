from pathlib import Path
p=Path("app/src/main/java/ir/bastiyan/kiosk/display")
g=Path("app/build.gradle")
g.write_text(g.read_text().replace("versionCode 111","versionCode 112").replace("1.1.1-R111","1.1.2-R112")+"\ndependencies { implementation 'com.google.zxing:core:3.5.3' }\n")
m=Path("app/src/main/AndroidManifest.xml")
s=m.read_text().replace('<uses-permission android:name="android.permission.INTERNET"/>','<uses-permission android:name="android.permission.INTERNET"/>\n    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>')
s=s.replace('<activity android:name=".MainActivity"', '<provider android:name=".UpdateProvider" android:authorities="\${applicationId}.updates" android:exported="false" android:grantUriPermissions="true"/>\n        <activity android:name=".MainActivity"')
# Gradle manifest placeholder must not include an escape.
s=s.replace(r'\${applicationId}', '\${applicationId}'.replace('\\',''))
m.write_text(s)
a=p/"MainActivity.java";s=a.read_text()
def change(old,new):
 global s
 assert s.count(old)==1, old
 s=s.replace(old,new)
change('private MediaPanel mediaPanel;', 'private MediaPanel mediaPanel;\n    private NativeDecor decor;\n    private AppUpdater updater;\n    private android.widget.ImageView centerLogo;\n    private LinearLayout headerBar,footerBar,qrPanel,queueRow;\n    private TextView subtitleText;')
change('vault=new TokenVault(this);announcer=new AudioAnnouncer(this);','vault=new TokenVault(this);announcer=new AudioAnnouncer(this);decor=new NativeDecor(this);updater=new AppUpdater(this,()->displaying&&started&&!announcer.isPlaying());')
change('reschedule(0);}}','reschedule(0);updater.onStart(vault.server());}}')
change('io.shutdownNow();super.onDestroy();','io.shutdownNow();decor.close();updater.close();super.onDestroy();')
change('statusText=null;clockText=null;tickerText=null;footerText=null;cardHost=null;','statusText=null;clockText=null;tickerText=null;footerText=null;cardHost=null;centerLogo=null;headerBar=null;footerBar=null;qrPanel=null;queueRow=null;subtitleText=null;')
change('LinearLayout header=horizontal();header.setGravity(Gravity.CENTER_VERTICAL);append(root,header,tv?79:67);\n        TextView emblem=label("◉",tv?31:25,CYAN,true);\n        emblem.setGravity(Gravity.CENTER);header.addView(emblem,new LinearLayout.LayoutParams(dp(42),-1));\n        siteTitle=label(centerName,tv?28:20,TEXT,true);\n        header.addView(siteTitle,new LinearLayout.LayoutParams(0,-1,1));','''LinearLayout header=horizontal();headerBar=header;header.setGravity(Gravity.CENTER_VERTICAL);pad(header,12,4);append(root,header,tv?76:68);
        centerLogo=new android.widget.ImageView(this);centerLogo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);centerLogo.setImageResource(R.drawable.app_icon);header.addView(centerLogo,new LinearLayout.LayoutParams(dp(tv?66:48),dp(tv?66:48)));
        LinearLayout identity=vertical();identity.setGravity(Gravity.CENTER_VERTICAL);pad(identity,10,0);
        siteTitle=label(centerName,tv?27:19,TEXT,true);siteTitle.setMaxLines(2);identity.addView(siteTitle);
        subtitleText=label("سامانه هوشمند نوبت‌دهی و فراخوان",tv?14:11,SUB,false);subtitleText.setSingleLine(true);subtitleText.setEllipsize(TextUtils.TruncateAt.END);identity.addView(subtitleText);
        header.addView(identity,new LinearLayout.LayoutParams(0,-1,1));''')
change('stage.addView(viewport,new FrameLayout.LayoutParams(-1,-1));','''queueRow=tv?horizontal():vertical();stage.addView(queueRow,new FrameLayout.LayoutParams(-1,-1));
        queueRow.addView(viewport,tv?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
        qrPanel=vertical();qrPanel.setGravity(Gravity.CENTER);pad(qrPanel,10,10);queueRow.addView(qrPanel,tv?new LinearLayout.LayoutParams(dp(174),-1):new LinearLayout.LayoutParams(-1,-2));qrPanel.setVisibility(View.GONE);''')
change('new MediaPanel(this,show->viewport.setVisibility(show?View.GONE:View.VISIBLE))','new MediaPanel(this,show->queueRow.setVisibility(show?View.GONE:View.VISIBLE))')
change('LinearLayout bottom=vertical();bottom.setBackgroundColor','LinearLayout bottom=vertical();footerBar=bottom;bottom.setBackgroundColor')
change('append(root,bottom,tv?100:84);','append(root,bottom,tv?82:72);')
change('rebuildCards();refreshStatus();\n        reschedule(0);','rebuildCards();refreshStatus();\n        reschedule(0);updater.onStart(vault.server());')
change('"بررسی فوری اتصال","تغییر سرور یا حساب نمایشگر","بستن منو"','"بررسی فوری اتصال","تغییر سرور یا حساب نمایشگر","بررسی نسخه جدید و به‌روزرسانی","بستن منو"')
change('if(which==1)reschedule(0);','if(which==1)reschedule(0);\n                if(which==3)updater.check(vault.server(),true);')
# All version text comes from installed APK metadata, not old literal build labels.
import re
s=re.sub(r'باستیان کیوسک R(?:105|107|110|111)', 'باستیان کیوسک "+AppUpdater.versionName(this)+"', s)
change('if(cardHost==null)return;\n        if(mediaPanel','if(cardHost==null)return;\n        applyDecor();\n        if(mediaPanel')
change('card.setBackgroundColor(call?Color.rgb(40,54,50):SURFACE);','card.setBackground(NativeDecor.surface(call?Color.rgb(40,54,50):dc("cardColor",SURFACE),dc("lineColor",0xff294258),dp(displaySettings().optInt("cardRadius",22)),dp(1)));')
change('dp(tv?340:225)','dp(tv?Math.max(180,Math.min(350,(getResources().getConfiguration().screenHeightDp-210)/Math.max(1,(count+columns-1)/columns))):225)')
change('cp.setMargins(dp(8),dp(8),dp(8),dp(8));','int gap=dp(Math.max(4,Math.min(16,displaySettings().optInt("cardGap",12)/2)));cp.setMargins(gap,gap,gap,gap);')
change('TextView docText=label(name,tv?27:21,TEXT,true);docText.setGravity(Gravity.CENTER);\n                append(card,docText,tv?62:42);','''LinearLayout docHeader=horizontal();docHeader.setGravity(Gravity.CENTER);String photo=doc.optString("photoUrl",doc.optString("photo",""));
                if(displaySettings().optBoolean("showDoctorPhoto",true)&&!photo.isEmpty()&&api!=null){
                    android.widget.ImageView portrait=new android.widget.ImageView(this);portrait.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);docHeader.addView(portrait,new LinearLayout.LayoutParams(dp(tv?45:38),dp(tv?45:38)));decor.image(api,photo,portrait,0);}
                TextView docText=label(name,scaled(tv?25:19,"doctorScale"),dc("doctorNameColor",TEXT),true);docText.setGravity(Gravity.CENTER);docText.setMaxLines(2);docHeader.addView(docText,new LinearLayout.LayoutParams(0,-1,1));append(card,docHeader,tv?49:43);''')
change('label(spec,tv?17:13,SUB,false)','label(spec,scaled(tv?16:13,"doctorScale"),dc("doctorSpecialtyColor",SUB),false)')
change('append(card,specialty,tv?35:25);','append(card,specialty,tv?27:23);')
change('int ticket=active==null?0:ticket(active);','''if(displaySettings().optBoolean("showRoom",true)&&!doc.optString("room","").isEmpty()){
                    TextView room=label("اتاق "+doc.optString("room"),tv?15:12,dc("roomColor",CYAN),true);room.setGravity(Gravity.CENTER);append(card,room,tv?24:21);}
                int ticket=active==null?0:ticket(active);''')
change('tv?(count==1?130:85):68,call?YELLOW:CYAN','scaled(tv?(count==1?112:76):65,"numberScale"),call?dc("callingColor",YELLOW):dc("numberColor",CYAN)')
change('append(card,caption,tv?44:34);','append(card,caption,tv?34:29);\n                if(call&&connected&&displaySettings().optBoolean("showPatientName",true)&&!active.optString("patientName","").isEmpty()){TextView patient=label(active.optString("patientName"),tv?17:13,dc("patientColor",TEXT),true);patient.setGravity(Gravity.CENTER);patient.setSingleLine(true);append(card,patient,tv?25:22);}')
change('append(card,waitingText,tv?35:29);','append(card,waitingText,tv?26:24);')
change('Object item=tickers.opt(i);','Object item=tickers.opt(i);if(item instanceof JSONObject&&!((JSONObject)item).optBoolean("active",true))continue;')
change('private void emptyCard(String title,String detail){','''private JSONObject centerSettings(){JSONObject o=lastState==null?null:lastState.optJSONObject("settings");return o==null?new JSONObject():o;}
    private JSONObject displaySettings(){JSONObject o=centerSettings().optJSONObject("displaySettings");return o==null?new JSONObject():o;}
    private int dc(String key,int fallback){return NativeDecor.color(displaySettings(),key,fallback);}
    private int scaled(int size,String key){return Math.max(10,Math.round(size*Math.max(60,Math.min(150,displaySettings().optInt(key,100)))/100f));}
    private void applyDecor(){
        if(headerBar==null||qrPanel==null)return;
        JSONObject set=centerSettings(),ds=displaySettings();
        root.setBackgroundColor(dc("backgroundColor",BG));headerBar.setBackground(NativeDecor.surface(dc("headerColor",SURFACE),dc("lineColor",0xff294258),dp(16),dp(1)));
        footerBar.setBackground(NativeDecor.surface(dc("tickerBackground",0xff0a222f),dc("lineColor",0xff294258),dp(14),dp(1)));
        siteTitle.setText(lastState==null?centerName:lastState.optString("centerName",set.optString("clinicName",centerName)));siteTitle.setTextSize(scaled(tv?27:19,"headerScale"));
        subtitleText.setText(set.optString("subtitle","سامانه هوشمند نوبت‌دهی و فراخوان"));
        centerLogo.setVisibility(ds.optBoolean("showCenterLogo",true)?View.VISIBLE:View.GONE);
        if(api!=null&&centerLogo.getVisibility()==View.VISIBLE)decor.image(api,set.optString("logoUrl",""),centerLogo,R.drawable.app_icon);
        clockText.setVisibility(ds.optBoolean("showClock",true)?View.VISIBLE:View.GONE);
        statusText.setVisibility(ds.optBoolean("showConnection",true)||!connected?View.VISIBLE:View.GONE);
        tickerText.setVisibility(ds.optBoolean("showTicker",true)?View.VISIBLE:View.GONE);tickerText.setTextColor(dc("tickerTextColor",TEXT));tickerText.setTextSize(scaled(tv?21:16,"tickerScale"));
        footerText.setTextColor(dc("copyrightColor",SUB));
        decor.qr(api,set,ds,qrPanel,queueRow,tv);
    }
    private void emptyCard(String title,String detail){''')
# Preserve connection and sound behavior. Avoid pausing for updates during a call.
s=s.replace('footerText.setText("آخرین داده معتبر: "+updated+"  |  باستیان کیوسک "+AppUpdater.versionName(this)+"  |  اتصال مستقیم HTTPS");','footerText.setText("آخرین داده معتبر: "+updated+"  |  نسخه "+AppUpdater.versionName(this)+(displaySettings().optBoolean("showBastianBrand",true)?"  |  هلدینگ باستیان":""));')
a.write_text(s)
api=p/"KioskApi.java";api.write_text(re.sub(r'BastiyanKioskNativeAndroidTV/[0-9.]+','BastiyanKioskNativeAndroidTV/1.1.2',api.read_text()))
