#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-36.1.0}"
PLATFORM_VERSION="${PLATFORM_VERSION:-android-36}"
BUILD_TOOLS="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$SDK_DIR/platforms/$PLATFORM_VERSION/android.jar"
APP_DIR="$ROOT_DIR/app/src/main"
OUT_DIR="$ROOT_DIR/build/manual"
PACKAGE="com.kajakaja.game"
PACKAGE_PATH="com/kajakaja/game"
MAIN_CLASS="$APP_DIR/java/com/kajakaja/game/MainActivity.java"
MANIFEST="$APP_DIR/AndroidManifest.xml"
APK_UNSIGNED="$OUT_DIR/kajakaja-unsigned.apk"
APK_ALIGNED="$OUT_DIR/kajakaja-aligned.apk"
APK_SIGNED="$ROOT_DIR/build/kajakaja-debug.apk"
KEYSTORE="$OUT_DIR/debug.keystore"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/classes" "$OUT_DIR/dex" "$OUT_DIR/compiled" "$ROOT_DIR/build"

"$BUILD_TOOLS/aapt2" compile --dir "$APP_DIR/res" -o "$OUT_DIR/compiled/resources.zip"
"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" \
  --java "$OUT_DIR/generated" \
  --min-sdk-version 23 \
  --target-sdk-version 36 \
  --version-code 2 \
  --version-name 1.1 \
  -o "$OUT_DIR/resources.apk" \
  "$OUT_DIR/compiled/resources.zip"

javac -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT_DIR/classes" \
  "$MAIN_CLASS" \
  "$OUT_DIR/generated/$PACKAGE_PATH/R.java"

jar cf "$OUT_DIR/classes.jar" -C "$OUT_DIR/classes" .

"$BUILD_TOOLS/d8" \
  --lib "$ANDROID_JAR" \
  --output "$OUT_DIR/dex" \
  "$OUT_DIR/classes.jar"

cp "$OUT_DIR/resources.apk" "$APK_UNSIGNED"
cd "$OUT_DIR/dex"
zip -qr "$APK_UNSIGNED" classes.dex
cd "$ROOT_DIR"

"$BUILD_TOOLS/zipalign" -f 4 "$APK_UNSIGNED" "$APK_ALIGNED"

if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=kajakaja,C=US" >/dev/null
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK_SIGNED" \
  "$APK_ALIGNED"

"$BUILD_TOOLS/apksigner" verify "$APK_SIGNED"
echo "Built $APK_SIGNED"
