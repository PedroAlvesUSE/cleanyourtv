# Registro de correções

## Tela inicial

- 2026-10-30 — O papel de parede ficava esbranquiçado com o foco na dock. A view `ambient` recebia um `RadialGradient` branco em `setAmbient`, e `showHero` a deixava com alpha 1 só na dock. Solução: a nova opção `dock_glow` (padrão desligado) impede a pintura do gradiente. Arquivos: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`, `launcher/app/src/main/java/com/gathin/tvhome/Config.java`.
