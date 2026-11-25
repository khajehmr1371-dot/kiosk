from pathlib import Path
p=Path("app/src/main/java/ir/bastiyan/kiosk/display")
g=Path("app/build.gradle");g.write_text(g.read_text().replace("versionCode 115","versionCode 116").replace("1.1.5-R115","1.1.6-R116"))
a=p/"AppUpdater.java";a.write_text(a.read_text().replace("1.1.5-R115","1.1.6-R116"))
a=p/"MainActivity.java";s=a.read_text()
s=s.replace("private NativeDecor decor;","private NativeDecor decor;\n    private NativeBoard board;")
s=s.replace("if(mediaPanel!=null){mediaPanel.shutdown();mediaPanel=null;}","if(mediaPanel!=null){mediaPanel.shutdown();mediaPanel=null;}\n        board=null;",1)
start=s.index("    private void buildDisplay(){")
end=s.index("    private void showSettings(){",start)
s=s[:start]+"""    private void buildDisplay(){
        displaying=true;base();root.setPadding(0,0,0,0);
        board=new NativeBoard(this,decor,()->showSettings(),()->{
            boolean next=!vault.audioEnabled();vault.setAudioEnabled(next);
            if(!next){announcer.stop();if(mediaPanel!=null)mediaPanel.setAnnouncementActive(false);}
            if(board!=null)board.setSound(next);
        });
        root.addView(board,new LinearLayout.LayoutParams(-1,-1));
        mediaPanel=new MediaPanel(this,show->{if(board!=null)board.setMediaVisible(show);});
        board.attachMedia(mediaPanel);board.setSound(vault.audioEnabled());
        rebuildCards();refreshStatus();reschedule(0);updater.onStart(vault.server());
    }
"""+s[end:]
start=s.index("    private void refreshStatus(String error){");end=s.index("    private JSONObject centerSettings()",start)
s=s[:start]+"""    private void refreshStatus(String error){
        if(board==null)return;
        if(!connected&&mediaPanel!=null)mediaPanel.stop();
        board.setConnection(connected,error);
    }
    private void rebuildCards(){
        if(board==null)return;
        board.render(lastState,api,centerName,connected);
        if(mediaPanel!=null&&api!=null)mediaPanel.configure(lastState,api,token,connected);
    }
"""+s[end:]
s=s.replace('if(soundButton!=null)soundButton.setText(enabled?"🔊 صدا روشن":"🔇 صدا خاموش");','if(board!=null)board.setSound(enabled);')
s=s.replace('lastState=result;vault.saveAnonymousState(result);','connected=true;lastState=result;vault.saveAnonymousState(result);')
# Existing audio/ack, API and session storage stay unchanged.
a.write_text(s)
d=p/"NativeDecor.java";s=d.read_text().replace("private String resolve(","String resolve(").replace("private Bitmap fetch(","Bitmap fetch(").replace("private Bitmap qrBitmap(","Bitmap qrBitmap(");d.write_text(s)
