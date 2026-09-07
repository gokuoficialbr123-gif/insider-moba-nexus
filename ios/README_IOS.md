# iPhone — NEXUS BETA INSIDER

O iPhone carrega o NEXUS localmente do `index.html` incluído no bundle do Xcode.

## Testar compilação
O workflow `.github/workflows/check-ios.yml` compila para simulador sem assinatura.

## Instalar em iPhone real
Abra `ios/NEXUS/NEXUS.xcodeproj` no Xcode em um Mac, escolha sua equipe em Signing & Capabilities e faça o Archive. A Apple exige assinatura para gerar/distribuir um IPA instalável.

O deployment target permanece iOS 16.0.
