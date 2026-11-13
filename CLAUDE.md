# CleanYourTV

Launcher Android TV em Java (`launcher/`, pacote `com.gathin.tvhome`).

## Build

- Use o JDK 17 (`JAVA_HOME=/home/pedrohrqe/tvclean/dl/jdk`). O Gradle 8.9 não roda no JDK 25.
- Testes e APK: `cd launcher && ./gradlew --no-daemon -q testDebugUnitTest assembleRelease`.
- O CI exige APK release abaixo de 5 MB (`.github/workflows/ci.yml`).
- Depois de instalar um APK novo, reative o serviço `com.gathin.tvhome/.HomeKeyService` nas configurações de acessibilidade.

## Opções

- Toda função tem uma opção de ligar e desligar na tela de Configurações do launcher.
- Defina cada opção uma só vez em `SettingsCatalog.java`. `SettingsCatalogTest` confere a correspondência com `Config`.
