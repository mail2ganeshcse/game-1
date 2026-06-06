# Google Play Automation

This repo can build signed Android release artifacts and upload the AAB to Google Play from GitHub Actions.

Workflow file:

```text
.github/workflows/android-play-release.yml
```

Required GitHub Actions secrets:

```text
ANDROID_UPLOAD_KEYSTORE_BASE64
ANDROID_UPLOAD_KEYSTORE_PASSWORD
ANDROID_UPLOAD_KEY_ALIAS
ANDROID_UPLOAD_KEY_PASSWORD
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
```

The secret names follow the saloon app pipeline. This repo is configured for:

```text
com.kajakaja.game
```

The local saloon files used for the same release config are:

```text
/Users/ganeshbabun/Downloads/saloonapp/salon-saas-ui/android/upload-keystore.jks
/Users/ganeshbabun/Downloads/saloonapp/salon-saas-ui/android/keystore.properties
/Users/ganeshbabun/salonibazaar-my-project-56753-498508-5645a201855e.json
```

To upload the same values into this GitHub repo with GitHub CLI:

```sh
brew install gh
gh auth login
scripts/set_github_secrets_from_saloon.sh mail2ganeshcse/game-1
```

Manual release:

```text
Actions > Build and Upload kajakaja to Google Play > Run workflow
```

Set `upload_to_play` to `yes` only after the Google Play app record, service account, and signing secrets are ready.

Important: the saloon Google Play service account can upload kajakaja only after Play Console grants it release access for `com.kajakaja.game`.

## Play Console App Content

Google Play Console may block saving/review until the App content forms are complete.

Privacy policy URL:

```text
https://mail2ganeshcse.github.io/game-1/privacy-policy.html
```

For the health declaration, use the non-health-app answer:

```text
kajakaja is not a health app and does not include health, medical, fitness, wellness, diagnosis, treatment, health data collection, or health tracking features.
```

This declaration is completed in Play Console, not through the Android APK/AAB.
