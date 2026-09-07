# NEXUS BETA INSIDER — Android + iPhone

Este pacote usa **um único conteúdo web** em `web/index.html` e o empacota para as duas plataformas:

- Android: `android/` → APK/AAB
- iPhone: `ios/NEXUS/` → app iOS/IPA após assinatura Apple

O conteúdo web é sincronizado com `python3 tools/sync_web.py`.

## O que já foi corrigido nesta versão

- Remove o endereço de teste `https://nexus.example`.
- O NEXUS fica embutido no aplicativo e abre sem depender de hospedagem.
- Mantém armazenamento local/offline do NEXUS.
- Android: importação de qualquer arquivo selecionado pelo usuário, incluindo JSON de backup.
- Android: exportações `blob:`/`data:` podem ser salvas em Downloads.
- iPhone: exportações `blob:`/`data:` abrem a folha de compartilhamento para salvar/enviar.
- Câmera e microfone continuam condicionados à permissão oficial do sistema.
- Localização continua condicionada à permissão oficial do sistema e ao recurso do NEXUS.
- Links HTTP/HTTPS externos abrem fora do WebView/WKWebView.
- Deep link `nexus://` continua preparado.
- Android 14+ (`minSdk 34`).
- iPhone com iOS 16+.

## Gerar APK pelo GitHub

1. Envie **todo o conteúdo desta pasta** para a raiz do repositório.
2. Abra `Actions`.
3. Abra `Build NEXUS Android APK`.
4. Clique `Run workflow`.
5. Quando ficar verde, abra a execução.
6. Em `Artifacts`, baixe `NEXUS-BETA-INSIDER-ANDROID`.
7. Dentro estará `app-debug.apk`.

## iPhone

O GitHub inclui `Check NEXUS iPhone Build`, que verifica a compilação para simulador sem assinatura.

Para instalar em um iPhone real ou gerar um IPA distribuível, a Apple exige assinatura do aplicativo. Abra `ios/NEXUS/NEXUS.xcodeproj` no Xcode em um Mac, selecione sua equipe Apple em `Signing & Capabilities` e gere o Archive.

## Atualizar o NEXUS depois

Edite somente:

`web/index.html`

Depois rode:

```bash
python3 tools/sync_web.py
```

Assim Android e iPhone recebem a mesma versão do NEXUS.
