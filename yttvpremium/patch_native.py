#!/usr/bin/env python3
"""Cambia la dirección del script de TizenTube dentro de libchrobalt.so por la de YTTVPremium.

Uso: patch_native.py <libchrobalt.so>

La app original descarga su script (bloqueo de anuncios, SponsorBlock, menú de ajustes) desde
jsDelivr. YTTVPremium usa su propia versión (carpeta yttvpremium/script, publicada en GitHub Pages),
sin el actualizador ni los enlaces del autor original.
La dirección nueva tiene exactamente el mismo largo para no mover nada dentro del binario.
"""
import sys

OLD = b"https://cdn.jsdelivr.net/npm/@foxreis/tizentube/dist/userScript.js?v="
NEW = b"https://byronr199716.github.io/YTENTV/yttvpremium/tv-userScript.js?v="
assert len(OLD) == len(NEW), (len(OLD), len(NEW))

path = sys.argv[1]
data = open(path, "rb").read()
count = data.count(OLD)
if count == 0:
    if data.count(NEW):
        print("Ya estaba cambiado:", path)
        sys.exit(0)
    sys.exit("patch_native: no se encontró la dirección del script en " + path)
data = data.replace(OLD, NEW)
open(path, "wb").write(data)
print("Script cambiado (%d) en %s" % (count, path))
