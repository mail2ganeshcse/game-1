#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-mail2ganeshcse/game-1}"
SALOON_ANDROID_DIR="${SALOON_ANDROID_DIR:-/Users/ganeshbabun/Downloads/saloonapp/salon-saas-ui/android}"
GOOGLE_PLAY_JSON="${GOOGLE_PLAY_JSON:-/Users/ganeshbabun/salonibazaar-my-project-56753-498508-5645a201855e.json}"
KEYSTORE_PROPERTIES="$SALOON_ANDROID_DIR/keystore.properties"
KEYSTORE_FILE="$SALOON_ANDROID_DIR/upload-keystore.jks"

command -v gh >/dev/null || {
  echo "GitHub CLI is required: brew install gh"
  exit 1
}

test -f "$KEYSTORE_PROPERTIES"
test -f "$KEYSTORE_FILE"
test -f "$GOOGLE_PLAY_JSON"

get_prop() {
  awk -F= -v key="$1" '$1 == key {print substr($0, index($0, "=") + 1)}' "$KEYSTORE_PROPERTIES"
}

gh secret set ANDROID_UPLOAD_KEYSTORE_BASE64 --repo "$REPO" --body "$(base64 -i "$KEYSTORE_FILE" | tr -d '\n')"
gh secret set ANDROID_UPLOAD_KEYSTORE_PASSWORD --repo "$REPO" --body "$(get_prop storePassword)"
gh secret set ANDROID_UPLOAD_KEY_ALIAS --repo "$REPO" --body "$(get_prop keyAlias)"
gh secret set ANDROID_UPLOAD_KEY_PASSWORD --repo "$REPO" --body "$(get_prop keyPassword)"
gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON --repo "$REPO" --body-file "$GOOGLE_PLAY_JSON"

echo "Secrets uploaded to $REPO"
