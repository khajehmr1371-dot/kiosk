from pathlib import Path
p=Path("app/src/main/java/ir/bastiyan/kiosk/display")
g=Path("app/build.gradle");g.write_text(g.read_text().replace("versionCode 112","versionCode 113").replace("1.1.2-R112","1.1.3-R113"))
m=Path("app/src/main/AndroidManifest.xml");s=m.read_text();assert '</application>' in s
s=s.replace('</application>','<receiver android:name=".UpdateInstallReceiver" android:exported="false"/>\n    </application>');m.write_text(s)
a=p/"MainActivity.java";s=a.read_text();assert 'updater.onStart(vault.server());}}' in s
s=s.replace('updater.onStart(vault.server());}}','updater.onResume(vault.server());}}');a.write_text(s)
