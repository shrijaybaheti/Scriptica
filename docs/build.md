# Build / Run (Fabric)

This project targets Minecraft 1.21.11 (Fabric + Gradle Loom).

## Prereqs
- JDK 21
- Gradle 9.2.0-rc-1 (or generate a Gradle wrapper)

## Run in dev
From the repo root:
- `gradle runClient`

If you prefer a Gradle wrapper, generate it once:
- `gradle wrapper`
Then use:
- `./gradlew runClient` (macOS/Linux)
- `./gradlew.bat runClient` (Windows)

## If versions fail to resolve
Minecraft/Fabric versions change over time. Update:
- `gradle.properties` (`minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_api_version`)
- `build.gradle` (`fabric-loom` plugin version)


## No Gradle installed?
If `gradle` is not available, run the bootstrap script to download Gradle locally and generate the wrapper:
- `powershell -ExecutionPolicy Bypass -File .\bootstrap-gradle-wrapper.ps1`
Then:
- `.\gradlew.bat runClient`






