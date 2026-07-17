# StadiaTV Android TV Implementation Plan

## Repository Assessment

- Existing project: dependency-free static web prototype (`index.html`, `css/`, `js/`).
- Existing Android/Gradle configuration: none.
- Existing language/runtime: HTML, CSS, JavaScript.
- Existing architecture: browser state object plus local ingestion module.
- Existing package name/min SDK/target SDK: none.
- Existing screens: onboarding, home, live guide, sports hub, match, mock player, search, settings.

Because there is no Android project, this implementation creates a single-module Android Studio project while preserving the web prototype files.

## Android Target

- Language: Kotlin.
- Build system: Gradle Kotlin DSL with version catalog.
- Package: `com.stadiatv`.
- compileSdk/targetSdk: `36` in the project scaffold. This should be adjusted only if the local SDK install lacks API 36.
- minSdk: `25` for Fire OS 6 and newer; Fire OS 5/API 22 is excluded.
- Java/Kotlin target: Java 17.
- Architecture: single-activity, Jetpack Compose, Room as local source of truth, repositories, ViewModels with immutable `StateFlow`, WorkManager sync, Media3 playback, Hilt DI.

## Phases

1. **Scaffold and TV Manifest**
   - Create Gradle settings, root build, app build, version catalog.
   - Add Android TV manifest, leanback launcher, landscape orientation, banner/icon assets, flavor network security configs.
   - Add secure and legacy HTTP product flavors plus debug/release build types.

2. **Core Domain, Database, Security, Networking**
   - Add domain models, Room entities/DAOs/database, mappers.
   - Add Keystore-backed `CredentialStore`.
   - Add redaction utility and provider request policy.
   - Add OkHttp provider client factory with bounded timeouts and redacted debug logging hook.

3. **Playlist Adapters and Sync**
   - Implement streaming M3U parser with recoverable warnings.
   - Implement Xtream adapter models and URL-safe request creation.
   - Add common adapter result types and sync repository pipeline.
   - Add WorkManager sync workers.

4. **Playback**
   - Add `PlaybackResolver` coordinator, M3U and Xtream resolvers.
   - Add Media3 player manager and TV player screen state.

5. **TV UI**
   - Add Compose navigation and TV-focused screens: onboarding, sources, home, live, guide, search, favorites, recent, movies, series, settings, EPG mapping, player.
   - Preserve D-pad focusability, visible focus, and deterministic navigation.

6. **Verification**
   - Unit tests for M3U parser, URL redaction, Xtream URL resolution, and playback request creation.
   - Android instrumentation scaffolding for Room and Compose UI tests.
   - Run Gradle compile/tests when Java and Android SDK are available locally.

## Known Local Tooling Blocker

This workstation currently reports no Java runtime and no visible Android SDK. Gradle compilation cannot be executed until a JDK 17+ and Android SDK are installed or exposed through `JAVA_HOME`/`ANDROID_HOME`.
