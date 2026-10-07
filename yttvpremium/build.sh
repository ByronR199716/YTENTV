#!/usr/bin/env bash
# Convierte un APK de TizenTube Cobalt en YTTVPremium (sin firmar).
#
# Uso: build.sh <apk original> <apk salida sin firmar> <dex de acceso> <versionCode> <versionName>
# Requiere: APKTOOL (ruta al .jar de apktool), python3, zip.
set -euo pipefail

IN="$1"; OUT="$2"; ACCESS_DEX="$3"; VCODE="$4"; VNAME="$5"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
DEC="$WORK/dec"

echo "== Decodificando $(basename "$IN")"
java -jar "$APKTOOL" d -f -o "$DEC" "$IN"

echo "== Manifiesto"
python3 "$HERE/patch_manifest.py" "$DEC/AndroidManifest.xml"
python3 - "$DEC/apktool.yml" "$DEC/AndroidManifest.xml" "$VCODE" "$VNAME" <<'PY'
import re, sys
yml, manifest, code, name = sys.argv[1:5]
y = open(yml).read()
y = re.sub(r"versionCode: .*", "versionCode: " + code, y)
y = re.sub(r"versionName: .*", "versionName: " + name, y)
open(yml, "w").write(y)
m = open(manifest, encoding="utf-8").read()
m = re.sub(r'(:versionCode=)"[^"]*"', r'\g<1>"%s"' % code, m, count=1)
m = re.sub(r'(:versionName=)"[^"]*"', r'\g<1>"%s"' % name, m, count=1)
open(manifest, "w", encoding="utf-8").write(m)
PY

echo "== Logo y banner"
for f in "$HERE"/branding/mipmap-*/ic_app.png "$HERE"/branding/drawable*/app_banner.png; do
  dir="$(basename "$(dirname "$f")")"
  name="$(basename "$f" .png)"
  target="$(ls "$DEC/res/$dir/$name"* 2>/dev/null | head -n1 || true)"
  if [ -z "$target" ]; then echo "Falta $dir/$name en el APK original" >&2; exit 1; fi
  cp "$f" "$target"
done

echo "== Nombre al transmitir desde el teléfono"
DIAL="$(grep -rl '^.class.*Ldev/cobalt/dial/DIALServer;' "$DEC"/smali* | head -n1)"
grep -q 'const-string v2, "TizenTube ("' "$DIAL"
sed -i 's/const-string \(v[0-9]*\), "TizenTube ("/const-string \1, "YTTVPremium ("/; s/const-string \(v[0-9]*\), "TizenTube"$/const-string \1, "YTTVPremium"/' "$DIAL"
! grep -q '"TizenTube' "$DIAL"

echo "== Script propio"
found=0
for so in "$DEC"/lib/*/libchrobalt.so; do
  python3 "$HERE/patch_native.py" "$so"
  found=1
done
[ "$found" = 1 ]

echo "== Armando APK"
java -jar "$APKTOOL" b -o "$WORK/unsigned.apk" "$DEC"

echo "== Agregando pantalla de acceso"
n=1
while unzip -l "$WORK/unsigned.apk" | grep -q " classes$([ $n = 1 ] && echo '' || echo $n).dex$"; do n=$((n+1)); done
cp "$ACCESS_DEX" "$WORK/classes$n.dex"
(cd "$WORK" && zip -q unsigned.apk "classes$n.dex")
echo "Pantalla de acceso en classes$n.dex"

cp "$WORK/unsigned.apk" "$OUT"
rm -rf "$WORK"
echo "== Listo: $OUT"
