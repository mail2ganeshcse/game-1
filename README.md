# kajakaja

`kajakaja` is a vertical HD-style rescue puzzle Android game. The hero is trapped inside colorful danger rooms with spikes, crushers, tunnels, vaults, lasers, rolling stones, water pipes, and final-stage fire traps. Tap matching color blocks to clear clusters, collect the route colors, and move kajakaja safely along the glowing escape path before the danger meter fills.

The game now has 10 designed levels. Each level has its own room palette, hazard animation, route requirements, move count, and obstacle density. Clearing a stage earns one magic reward.

## Controls

- Tap a group of 2 or more matching blocks to clear it.
- Clear the required route colors and open enough path progress to reach the exit.
- Tap `Sign in with Google` on the opening screen to save and restore progress.
- Tap `AI MOVE` to let the built-in action helper choose the best rescue cluster.
- Tap `MAGIC` to break traps and recover the stage using earned rewards.
- If a stage fails, tap to spend magic and retry, or restart when no magic remains.
- Complete all 10 levels to finish the rescue run.

## Progress Save

Google sign-in is used to identify the player and restore the saved level, score, and magic rewards on the same device. Progress is stored locally per Google account.

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
