# kajakaja

`kajakaja` is a fast survival runner Android game. The hero runs through a cinematic jungle escape while danger closes in from behind. Change lanes, dodge traps, collect sprint charms, gather route clues, and pick the correct gate to reach the next level.

Each stage has a different threat and task. Snake Gorge makes the player collect blockers to stop the serpent, Falling Ruins adds a rolling stone rush, Fire Grove pushes a burning wall forward, and Hunter Night sends shadow hunters from behind. Reaching the route gate without enough clues/blockers, or choosing the wrong route, ends the run.

## Controls

- Swipe up/down or tap the upper/lower screen to change lanes.
- Clear a stage to earn one magic reward.
- Tap the center during a run to spend magic, reveal the route, and push back danger.
- If a stage fails, tap to spend magic and retry that failed stage.
- Collect enough clues before the gate; the correct route lights up when you are ready.

## Build APK

Use the Gradle Android build for release builds, because the game includes Android dependencies such as Google Mobile Ads.

```sh
gradle clean bundleRelease assembleRelease
```

The release outputs are created at:

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
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

Every push to `main` builds signed APK/AAB artifacts and uploads the AAB to Google Play. By default it uploads to the `alpha` track with `draft` status, which is required while the first Google Play store listing/app-content setup is still incomplete.

After Google Play Console setup is complete, set this GitHub repository variable to publish automatically:

```text
PLAY_RELEASE_STATUS=completed
```

Optional repository variables:

```text
PLAY_TRACK=alpha
PLAY_RELEASE_NOTES=kajakaja automatic pipeline build.
```

Details are in:

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
