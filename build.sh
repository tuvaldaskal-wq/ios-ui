#!/usr/bin/env bash
#
# Build an unsigned-then-debug-signed APK for the iOS-style launcher using only
# the Debian-packaged Android SDK tools (no Google network access required).
#
#   android.jar  : /usr/lib/android-sdk/platforms/android-23/android.jar
#   aapt2        : resource compile + link
#   dalvik-exchange (dx) : .class -> classes.dex
#   zipalign / apksigner : align + sign
#
set -euo pipefail

# ---- Configurable paths -------------------------------------------------
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
DX_JAR="${DX_JAR:-/usr/share/java/com.android.dx.jar}"
AAPT2="${AAPT2:-aapt2}"
ZIPALIGN="${ZIPALIGN:-zipalign}"
APKSIGNER="${APKSIGNER:-apksigner}"
MIN_SDK=21
# Target a modern API level so current Android versions / strict OEMs accept the
# install. We still compile against android.jar 23 (only <=23 APIs are used).
TARGET_SDK=34

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
BUILD="$ROOT/build"
OUT="$ROOT/dist"
KEYSTORE="$ROOT/debug.keystore"

rm -rf "$BUILD"
mkdir -p "$BUILD/compiled" "$BUILD/gen" "$BUILD/classes" "$OUT"

echo "==> [1/6] aapt2 compile resources"
"$AAPT2" compile --dir "$APP/res" -o "$BUILD/compiled/res.zip"

echo "==> [2/6] aapt2 link (resources + manifest -> base APK, generate R.java)"
"$AAPT2" link \
    -o "$BUILD/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --auto-add-overlay \
    "$BUILD/compiled/res.zip"

echo "==> [3/6] javac (target Java 8 bytecode, inline string concat for dx)"
SRCS=$(find "$APP/src" "$BUILD/gen" -name '*.java')
javac \
    --release 8 \
    -XDstringConcat=inline \
    -encoding UTF-8 \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" \
    $SRCS

echo "==> [4/6] dx: classes -> classes.dex"
java -jar "$DX_JAR" --dex \
    --min-sdk-version="$MIN_SDK" \
    --output="$BUILD/classes.dex" \
    "$BUILD/classes"

echo "==> [5/6] package classes.dex into the APK"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
( cd "$BUILD" && zip -q -j unsigned.apk classes.dex )

echo "==> [6/6] zipalign + debug sign"
if [ ! -f "$KEYSTORE" ]; then
    echo "    creating debug keystore"
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass android -keypass android \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

"$ZIPALIGN" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$OUT/ios-launcher.apk" \
    "$BUILD/aligned.apk"

"$APKSIGNER" verify --verbose "$OUT/ios-launcher.apk" | head -5 || true

echo ""
echo "==> BUILD OK -> $OUT/ios-launcher.apk"
ls -la "$OUT/ios-launcher.apk"
