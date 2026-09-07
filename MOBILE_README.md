# NEXUS BETA INSIDER — Pacote Mobile Unificado

O conteúdo do NEXUS está embutido no projeto em `web/index.html` e sincronizado para Android e iPhone.

- Android: `android/`
- iPhone: `ios/NEXUS/`
- Fonte web única: `web/index.html`
- Sincronização: `python3 tools/sync_web.py`

Para gerar o APK pelo GitHub, use o workflow `Build NEXUS Android APK`.
Para iPhone, o workflow `Check NEXUS iPhone Build` valida a compilação para simulador. Instalação em iPhone real exige assinatura Apple.
