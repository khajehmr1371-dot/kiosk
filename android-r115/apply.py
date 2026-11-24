from pathlib import Path
import re
p=Path("app/src/main/java/ir/bastiyan/kiosk/display")
g=Path("app/build.gradle")
s=g.read_text().replace("versionCode 113","versionCode 115").replace("1.1.3-R113","1.1.5-R115")
assert "versionCode 115" in s
g.write_text(s)
m=Path("app/src/main/AndroidManifest.xml")
s=m.read_text()
s=re.sub(r'^\s*<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>\s*$', '', s, flags=re.M)
s=re.sub(r'^\s*<provider android:name=".UpdateProvider"[^\n]*/>\s*$', '', s, flags=re.M)
s=re.sub(r'^\s*<receiver android:name=".UpdateInstallReceiver"[^\n]*/>\s*$', '', s, flags=re.M)
assert "REQUEST_INSTALL_PACKAGES" not in s and "UpdateProvider" not in s and "UpdateInstallReceiver" not in s
m.write_text(s)
for name in ["UpdateProvider.java","UpdateInstallReceiver.java"]:
    (p/name).unlink(missing_ok=True)
a=p/"MainActivity.java"
a.write_text(a.read_text().replace("بررسی نسخه جدید و به‌روزرسانی","بررسی نسخه جدید"))
