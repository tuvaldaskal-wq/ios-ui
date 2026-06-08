#!/usr/bin/env bash
#
# Build the Aria assistant APK with the offline Debian Android tools (same
# pipeline as the launcher: aapt2 + javac + dalvik-exchange + zipalign +
# apksigner). No Google SDK / network required.
#
set -euo pipefail

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
DX_JAR="${DX_JAR:-/usr/share/java/com.android.dx.jar}"
AAPT2="${AAPT2:-aapt2}"
ZIPALIGN="${ZIPALIGN:-zipalign}"
APKSIGNER="${APKSIGNER:-apksigner}"
MIN_SDK=21
TARGET_SDK=34

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
BUILD="$ROOT/build"
OUT="$ROOT/dist"
KEYSTORE="$ROOT/debug.keystore"
PACKAGE="com.aiassistant"

# Ensure a secrets.xml exists (gitignored). Falls back to the example.
if [ ! -f "$APP/res/values/secrets.xml" ]; then
    cp "$ROOT/secrets.example.xml" "$APP/res/values/secrets.xml"
    echo "    created secrets.xml from template (empty key)"
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/compiled" "$BUILD/gen" "$BUILD/classes" "$OUT"

# The manifest (Gradle-style) has no package attribute; aapt2 needs one.
# Inject it into a temporary copy so this offline build keeps working.
sed "s|<manifest |<manifest package=\"$PACKAGE\" |" \
    "$APP/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"

echo "==> [1/6] aapt2 compile resources"
"$AAPT2" compile --dir "$APP/res" -o "$BUILD/compiled/res.zip"

echo "==> [2/6] aapt2 link"
"$AAPT2" link \
    -o "$BUILD/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$BUILD/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --auto-add-overlay \
    "$BUILD/compiled/res.zip"

echo "==> [3/6] javac"
SRCS=$(find "$APP/java" "$BUILD/gen" -name '*.java')
javac --release 8 -XDstringConcat=inline -encoding UTF-8 \
    -classpath "$ANDROID_JAR" -d "$BUILD/classes" $SRCS

echo "==> [4/6] dx -> classes.dex"
java -jar "$DX_JAR" --dex --min-sdk-version="$MIN_SDK" \
    --output="$BUILD/classes.dex" "$BUILD/classes"

echo "==> [5/6] package dex into apk"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
( cd "$BUILD" && zip -q -j unsigned.apk classes.dex )

echo "==> [6/6] align + sign"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v -keystore "$KEYSTORE" \
        -storepass android -keypass android -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Aria Debug,O=Aria,C=US" >/dev/null 2>&1
fi
"$ZIPALIGN" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out "$OUT/aria.apk" "$BUILD/aligned.apk"

echo ""
echo "==> BUILD OK -> $OUT/aria.apk"
ls -la "$OUT/aria.apk"
