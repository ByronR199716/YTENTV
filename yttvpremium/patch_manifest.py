#!/usr/bin/env python3
"""Ajusta el AndroidManifest.xml decodificado por apktool para YTTVPremium.

Uso: patch_manifest.py <AndroidManifest.xml>

- Package propio (ec.yttvpremium.app) y nombre "YTTVPremium".
- Application = ec.yttvpremium.access.AccessGuard (vigila el acceso mientras la app está abierta).
- La pantalla de acceso (AccessActivity) pasa a ser la que abre el launcher del TV / teléfono.
- Se quita el permiso de ID de publicidad. Se deja (o agrega) el de instalar APKs: lo usa el aviso
  de nueva versión propio (access/src/.../AppUpdate.java), con el FileProvider que ya trae la app.
- debuggable = false.
"""
import re
import sys

OLD_PACKAGE = "io.gh.reisxd.tizentube.cobalt"
NEW_PACKAGE = "ec.yttvpremium.app"
NS = "n1"  # prefijo que usa apktool para el namespace de android

path = sys.argv[1]
xml = open(path, encoding="utf-8").read()

# El prefijo del namespace puede variar entre versiones de apktool.
m = re.search(r'xmlns:(\w+)="http://schemas.android.com/apk/res/android"', xml)
if m:
    NS = m.group(1)
A = NS + ":"


def must_sub(pattern, repl, text, count=0, flags=0):
    new, n = re.subn(pattern, repl, text, count=count, flags=flags)
    if n == 0:
        sys.exit("patch_manifest: no se encontró: " + pattern)
    return new


# Package y authorities de los providers.
xml = must_sub(r'package="' + re.escape(OLD_PACKAGE) + '"', 'package="' + NEW_PACKAGE + '"', xml)
xml = xml.replace(OLD_PACKAGE + ".", NEW_PACKAGE + ".")

# Permisos que no se usan.
for perm in ("com.google.android.gms.permission.AD_ID",):
    xml = re.sub(r'\s*<uses-permission ' + A + r'name="' + re.escape(perm) + r'"\s*/>', "", xml)

# Instalar la actualización descargada (aviso de nueva versión propio).
INSTALL = "android.permission.REQUEST_INSTALL_PACKAGES"
if INSTALL not in xml:
    xml = must_sub(r"(<application )", '<uses-permission ' + A + 'name="' + INSTALL + '" />\n    \\1', xml, count=1)

# El aviso de nueva versión entrega el APK por este FileProvider (carpeta files/ de la app).
if "androidx.core.content.FileProvider" not in xml or 'authorities="' + NEW_PACKAGE + '.fileprovider"' not in xml:
    sys.exit("patch_manifest: falta el FileProvider de la app original (lo usa el aviso de actualización)")

# <application ...>
app_open = re.search(r"<application [^>]*>", xml).group(0)
new_app = app_open
new_app = must_sub(A + r'label="[^"]*"', A + 'label="YTTVPremium"', new_app)
new_app = must_sub(A + r'name="dev\.cobalt\.app\.CobaltApplication"', A + 'name="ec.yttvpremium.access.AccessGuard"', new_app)
new_app = re.sub(A + r'debuggable="true"', A + 'debuggable="false"', new_app)
xml = xml.replace(app_open, new_app)

# La actividad principal ya no aparece en el launcher (se entra por la pantalla de acceso).
main_start = xml.index(A + 'name="dev.cobalt.app.MainActivity"')
main_end = xml.index("</activity>", main_start)
main_block = xml[main_start:main_end]
launcher_filter = re.search(
    r"\s*<intent-filter>\s*<action " + A + r'name="android\.intent\.action\.MAIN"\s*/>.*?</intent-filter>',
    main_block, flags=re.S)
if not launcher_filter:
    sys.exit("patch_manifest: no se encontró el filtro MAIN de MainActivity")
main_block = main_block.replace(launcher_filter.group(0), "")
xml = xml[:main_start] + main_block + xml[main_end:]

access_activity = (
    '\n        <activity ' + A + 'name="ec.yttvpremium.access.AccessActivity" '
    + A + 'exported="true" '
    + A + 'label="YTTVPremium" '
    + A + 'theme="@android:style/Theme.Material.NoActionBar.Fullscreen" '
    + A + 'banner="@drawable/app_banner" '
    + A + 'icon="@mipmap/ic_app" '
    + A + 'windowSoftInputMode="adjustPan|stateHidden" '
    + A + 'configChanges="keyboard|keyboardHidden|navigation|orientation|screenLayout|uiMode|screenSize|smallestScreenSize">\n'
    '            <intent-filter>\n'
    '                <action ' + A + 'name="android.intent.action.MAIN" />\n'
    '                <category ' + A + 'name="android.intent.category.LAUNCHER" />\n'
    '                <category ' + A + 'name="android.intent.category.LEANBACK_LAUNCHER" />\n'
    '                <category ' + A + 'name="android.intent.category.DEFAULT" />\n'
    '            </intent-filter>\n'
    '        </activity>'
)
xml = xml.replace(new_app, new_app + access_activity, 1)

if OLD_PACKAGE in xml:
    sys.exit("patch_manifest: quedó el package viejo en el manifiesto")

open(path, "w", encoding="utf-8").write(xml)
print("Manifiesto listo:", NEW_PACKAGE)
