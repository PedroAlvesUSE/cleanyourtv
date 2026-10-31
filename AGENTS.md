# CleanYourTV

Launcher Android TV em Java (`launcher/`, pacote `com.gathin.tvhome`).

## Retomar o trabalho

- Leia `.plan/progresso.md` antes de continuar. Ele tem o estado completo, as pendências e os próximos passos.
- Plano aprovado: `.plan/plano-launcher-apple.md`.
- Branch de trabalho: `feat/wallpaper-dock-settings`.
- Atualize `.plan/progresso.md` ao final de cada etapa.

## Subagentes

- Não use Haiku neste projeto. A prioridade é a maior qualidade visual e de animação. Esta regra prevalece sobre a regra global.
- Use Opus (`anthropic/claude-opus-5-5`) para visual, animação e revisão de capturas.
- Use Sonnet (`anthropic/claude-sonnet-5-5`) para lógica, testes, build e tarefas operacionais.

## Build

- Use o JDK 17 (`JAVA_HOME=/home/pedrohrqe/tvclean/dl/jdk`). O Gradle 8.9 não roda no JDK 25.
- Build completo (igual ao CI): `cd launcher && JAVA_HOME=/home/pedrohrqe/tvclean/dl/jdk ./gradlew --no-daemon -q testReleaseUnitTest lintRelease assembleRelease`.
- O CI exige APK release abaixo de 768 KB (`.github/workflows/ci.yml`).
- Depois de instalar um APK novo, reative o serviço `com.gathin.tvhome/.HomeKeyService` nas configurações de acessibilidade.

## TV de teste

- TV TCL 55P7K, Android 12, sem root. ADB: `adb connect 192.168.3.21:5555`.
- Atenção: `192.168.3.50` é um celular, não a TV. Não instale nada nele.
- Instale e reative o serviço:
  ```
  adb -s 192.168.3.21:5555 install -r launcher/app/build/outputs/apk/release/app-release.apk
  adb -s 192.168.3.21:5555 shell "settings delete secure enabled_accessibility_services; settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 0; settings put secure accessibility_enabled 1"
  ```

## Opções

- Toda função tem uma opção de ligar e desligar na tela de Configurações do launcher. Uma função desligada não roda código nem faz chamada de rede.
- Defina cada opção uma só vez em `SettingsCatalog.java`. `SettingsCatalogTest` confere a correspondência com `Config`.
- Visual de referência: Apple tvOS em todas as telas.

## Registro de correções

- Registre as correções em [`docs/correcoes.md`](docs/correcoes.md), não neste arquivo.
- Consulte o registro só antes de uma correção: `git grep -n -i -e "<Classe>" -e "<CODIGO_ERRO>" main -- docs/correcoes.md`.
- Organize o registro em seções por área (`## Papel de parede`, `## Configurações`, ...). Coloque a entrada nova no topo da seção.
- Formato: `- AAAA-MM-DD — Problema. Solução: .... Arquivos: \`caminho\`, \`caminho\`.`
- Inclua a alteração de `docs/correcoes.md` no mesmo commit da correção.
