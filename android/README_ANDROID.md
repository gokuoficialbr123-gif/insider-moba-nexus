# Android — NEXUS BETA INSIDER

O Android carrega o NEXUS localmente de `app/src/main/assets/index.html`.

## GitHub
Use `.github/workflows/build-android.yml` na raiz do pacote.

## Local
1. Rode `python3 tools/sync_web.py` na raiz do pacote.
2. Entre em `android/`.
3. Execute `./gradlew assembleDebug`.
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Requisitos do projeto: Android 14+ e SDK/Build Tools 36.
