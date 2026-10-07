# YTTVPremium — cómo se arma

Compilar Cobalt (Chromium) desde cero necesita horas y más de 100 GB de disco, así que no cabe en los
servidores gratis de GitHub. En cambio, el workflow `.github/workflows/yttvpremium.yml` toma el APK ya
publicado de [TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt/releases) y lo modifica:

| Qué | Dónde |
| --- | --- |
| Nombre YTTVPremium, package `ec.yttvpremium.app`, sin permiso de instalar APKs ni ID de publicidad | `patch_manifest.py` |
| Logo y banner del TV | `branding/` (se generan desde el mismo dibujo que YTPremium) |
| Pantalla de acceso con código (control remoto) y revisión del acceso mientras la app está abierta | `access/src/` (Java) |
| Script propio en vez del de TizenTube (sin actualizador, donaciones ni redes del autor) | `script/` + `patch_native.py` |
| Nombre al transmitir desde el teléfono ("YTTVPremium (modelo)") | `build.sh` |

Cada cambio en `yttvpremium/` (menos los `.md`) compila solo y publica en Releases
`YTTVPremium-1.0.N-<abi>.apk` (tag `build-N`, versionCode 100 + N) y una copia con nombre fijo
`YTTVPremium-<abi>.apk` para el enlace permanente `releases/latest/download/...`.
Se firma con la misma llave que YTPremium (secretos `BASE_64_SIGNING_KEY`, `KEY_STORE_PASSWORD`,
`KEY_PASSWORD`, `ALIAS`).

Para usar una versión distinta de TizenTube Cobalt: Actions > YTTVPremium APK > Run workflow e indicar el tag.

## Script de TV

La app descarga al abrir el script que quita anuncios, salta patrocinios y agrega el menú de ajustes.
YTTVPremium lo descarga de `https://byronr199716.github.io/YTENTV/yttvpremium/tv-userScript.js`
(GitHub Pages, lo publica el mismo workflow). Así se puede corregir el script sin reinstalar la app.
Requiere tener activado una vez: **Settings > Pages > Source: GitHub Actions**.

`script/` es una copia de `mods/` de [reisxd/TizenTube](https://github.com/reisxd/TizenTube)
(commit 9dd70a7, versión 1.15.1) con estos cambios: sin actualizador (`features/updater.js`), sin
menús "Apoya a TizenTube" ni "Redes sociales", sin mensaje de bienvenida y con el nombre YTTVPremium en
los textos. Para traer mejoras del original hay que copiar los cambios a mano.

## Acceso

- Servicio `ytpremium` (mismos códigos que YTPremium, sin créditos extra). Cada TV ocupa un dispositivo.
- ID del equipo = ANDROID_ID (igual que YTPremium; con la misma llave de firma un mismo equipo cuenta una vez).
- Mismas reglas: revalida según el servidor, gracia sin internet, detecta reloj atrasado, una prueba por equipo.
- `AccessGuard` (la Application) revisa cada minuto y al volver a la app; si el código vence o se
  desactiva, cierra la app y muestra la pantalla de acceso con el motivo.
