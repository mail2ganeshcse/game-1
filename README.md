# kajakaja

`kajakaja` is a fast survival runner Android game. The hero runs through a cinematic jungle escape while danger closes in from behind. Change lanes, dodge traps, collect samosas for sprint energy, gather route clues, and pick the correct gate to reach the next level.

## Controls

- Swipe up/down or tap the upper/lower screen to change lanes.
- Tap the center while carrying samosas to sprint.
- Collect enough clues before the gate; the correct route lights up when you are ready.

## Build APK

This repo includes a direct Android SDK build script, so Gradle is optional.

```sh
chmod +x build_apk.sh
./build_apk.sh
```

The debug APK is created at:

```text
build/kajakaja-debug.apk
```

Install on a connected device:

```sh
adb install -r build/kajakaja-debug.apk
```

## Google Play Pipeline

GitHub Actions workflow:

```text
.github/workflows/android-play-release.yml
```

The workflow builds signed APK/AAB artifacts and can upload the AAB to Google Play using the same secret pattern as the saloon app. Details are in:

```text
docs/google-play-automation.md
```

The helper below uploads the saloon keystore/service-account values to this repo once GitHub CLI is installed and authenticated:

```sh
scripts/set_github_secrets_from_saloon.sh mail2ganeshcse/game-1
```
