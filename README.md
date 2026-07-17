# StadiaTV

StadiaTV is now scaffolded as a native Android TV / Fire TV IPTV client, while the original static web prototype remains in `index.html`, `css/`, and `js/`.

The Android app is a single-activity Kotlin project using Jetpack Compose, TV-focused UI surfaces, Navigation Compose, Room, WorkManager, Hilt, OkHttp, Kotlin serialization, AndroidX Media3 ExoPlayer, and Keystore-backed credential encryption.

## Build Requirements

- JDK 17 or newer
- Android Studio with Android SDK Platform 36 installed
- Android SDK Build Tools compatible with AGP 9.3.0
- No Google Play Services dependency is required for core functionality

Fire OS 5 / API 22 devices are excluded. The minimum SDK is 25 for Fire OS 6 and newer.

## Build

This environment did not include Java, Gradle, or an Android SDK, so compilation could not be run here. On a configured machine:

```sh
gradle :app:assembleSecureDebug
gradle :app:testSecureDebugUnitTest
gradle :app:connectedSecureDebugAndroidTest
```

If you prefer a wrapper, generate it from a machine with Gradle installed:

```sh
gradle wrapper
./gradlew :app:assembleSecureDebug
```

## Variants

- `secure`: HTTPS-only playlist/API access, cleartext disabled, release shrinking enabled.
- `legacyHttp`: allows user-provided HTTP sources for legacy providers. The app still validates and redacts credentials and never silently downgrades HTTPS.
- `debug`: debug tooling enabled with redacted diagnostics only.

## Sideloading

Build a debug APK:

```sh
gradle :app:assembleSecureDebug
adb install -r app/build/outputs/apk/secure/debug/app-secure-debug.apk
```

For Fire TV:

```sh
adb connect FIRE_TV_IP_ADDRESS
adb install -r app/build/outputs/apk/secure/debug/app-secure-debug.apk
```

## Architecture

The native app follows an offline-first layered design:

```text
Compose TV UI
ViewModels with immutable StateFlow
Repositories / sync manager / playback resolver
Room database + network adapters + secure credential store
```

Important packages:

- `app/`: application, DI, activity
- `navigation/`: Compose route graph
- `core/model/`: domain models without Room annotations
- `core/database/`: Room entities, DAOs, database, mappers
- `core/parser/`: streaming M3U and XMLTV parsers
- `core/sync/`: source adapter contracts, M3U/Xtream adapters, sync manager, WorkManager worker
- `core/player/`: playback request resolver and Media3 player manager
- `core/security/`: Android Keystore-backed credential store
- `core/util/`: redaction and stable ID helpers
- `feature/`: TV screens for onboarding, sources, home, live, guide, search, favorites, recent, movies, series, settings, EPG mapping, and player

## Security

- Xtream usernames/passwords are stored through `CredentialStore`, backed by Android Keystore AES/GCM.
- Room stores source metadata and catalogue data, not plaintext passwords.
- Redaction covers usernames, passwords, credential-bearing query params, authorization/cookie headers, and common Xtream stream paths.
- The UI never constructs stream URLs directly. `PlaybackResolver` resolves URLs immediately before playback.
- Do not commit real playlists, provider URLs, credentials, signing keys, keystores, or private fixtures.

## Tests

Added JVM unit tests for:

- Extended M3U parsing
- BOM/CRLF/relative URL handling
- Malformed M3U recovery
- XMLTV programme time parsing
- URL redaction
- Stable ID generation

Run them with:

```sh
gradle :app:testSecureDebugUnitTest
```

Android instrumentation scaffolding is present for future Room and Compose UI tests.

## Troubleshooting

- `Unable to locate a Java Runtime`: install JDK 17 and set `JAVA_HOME`.
- `SDK location not found`: install Android Studio/SDK and set `ANDROID_HOME` or create `local.properties`.
- `compileSdk 36 not installed`: install API 36 or adjust `compileSdk`/`targetSdk` only after documenting the installed SDK constraint.
- HTTP playlist rejected in secure builds: use the `legacyHttp` flavor and require explicit user confirmation in UI.
- Provider CORS issues do not apply to the native Android client; OkHttp fetches playlists directly.
- Fire TV playback problems require real-device verification because codecs, decoders, and Fire OS media behavior vary by model.

## Legacy Web Prototype

The previous browser prototype remains runnable:

```sh
python3 -m http.server 8080
```

Then open `http://localhost:8080`.

The web prototype now includes `js/sports-api.js`, an ESPN site API adapter for live scores, upcoming games, news, and team lists. It supports NFL, college football, MLB, college baseball scores, NHL, NBA, WNBA, men's and women's college basketball, Premier League, and MLS. The Sports hub fetches selected sports, provides previous/next date navigation, and falls back to the existing mock data if ESPN requests fail.

The Android TV app is also named **StadiaTV** (`app_name`) and now has a native Sports hub route backed by the same ESPN feed set. The web Sports hub and Android Sports hub are intended to stay visually and functionally matched: left TV rail, StadiaTV branding, dark 10-foot layout, Live/Upcoming/News segmented tabs, date controls, refresh action, sport chips, league-grouped match cards, and news cards.
