# kajakaja

`kajakaja` is a fast survival runner Android game. The hero runs through a cinematic jungle escape while danger closes in from behind. Change lanes, dodge traps, collect samosas for sprint energy, gather route clues, and pick the correct gate to reach the next level.

Each stage has a different threat and task. Snake Gorge makes the player collect blockers to stop the serpent, Falling Ruins adds a rolling stone rush, Fire Grove pushes a burning wall forward, and Hunter Night sends shadow hunters from behind. Reaching the route gate without enough clues/blockers, or choosing the wrong route, ends the run.

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

## Ads

The Android project includes Google AdMob banner ads while gameplay is running. It currently uses Google test IDs:

```text
App ID: ca-app-pub-3940256099942544~3347511713
Banner Ad Unit ID: ca-app-pub-3940256099942544/9214589741
```

Replace these with your real AdMob IDs before production monetization.
