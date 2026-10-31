# Registro de correções

## Tela inicial

- 2026-10-30 — Cantos do painel rápido não eram concêntricos: o painel de 28 dp tinha recuo de 10 dp e o foco das linhas 16 dp, a borda usava #33FFFFFF e as informações da TV eram um texto solto. Solução: recuo de 12 dp com foco e bloco de informações em 16 dp (28 − 12), borda #1FFFFFFF e lista agrupada em vidro (rótulo à esquerda, valor à direita, separadores de 1 px), também no diálogo das Configurações. Arquivos: `launcher/app/src/main/java/com/gathin/tvhome/ControlCenter.java`, `launcher/app/src/main/java/com/gathin/tvhome/SystemInfo.java`, `launcher/app/src/main/java/com/gathin/tvhome/SystemActions.java`.
- 2026-10-30 — Os cantos da barra de vidro da dock não acompanhavam o bloco em foco nas pontas. A barra tinha raio fixo de 28 dp e folga lateral de 14 dp, e o bloco cresce 8% e sobe 6 dp. Solução: folga calculada pelo espaço livre da grade (6 a 12 dp) e raio de cada canto = raio do bloco em foco + folga daquele lado. Arquivos: `launcher/app/src/main/java/com/gathin/tvhome/DockDecoration.java`.

## Controle remoto

- 2026-10-30 — O botão de microfone abria o Google Assistente: a política da TCL trata a tecla 219 antes do serviço de acessibilidade. Solução: `MicSearchActivity` responde a `ACTION_ASSIST`, e o launcher fica no papel de assistente (`cmd role add-role-holder android.app.role.ASSISTANT com.gathin.tvhome 0`). Com `mic_tizentube` desligado, a activity repassa a chamada ao `com.google.android.katniss`. Arquivos: `launcher/app/src/main/java/com/gathin/tvhome/MicSearchActivity.java`, `launcher/app/src/main/AndroidManifest.xml`.

- 2026-10-30 — O papel de parede ficava esbranquiçado com o foco na dock. A view `ambient` recebia um `RadialGradient` branco em `setAmbient`, e `showHero` a deixava com alpha 1 só na dock. Solução: a nova opção `dock_glow` (padrão desligado) impede a pintura do gradiente. Arquivos: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`, `launcher/app/src/main/java/com/gathin/tvhome/Config.java`.
