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

rm -rf "$BUILD"
mkdir -p "$BUILD/compiled" "$BUILD/gen" "$BUILD/classes" "$OUT"

# Generate BuildConfig.java (Gradle does this from local.properties; this offline
# build injects empty/default values, or reads them from the environment).
mkdir -p "$BUILD/gen/com/aiassistant"
cat > "$BUILD/gen/com/aiassistant/BuildConfig.java" <<EOF
package com.aiassistant;
public final class BuildConfig {
    public static final String ANTHROPIC_API_KEY = "${ANTHROPIC_API_KEY:-}";
    public static final String ANTHROPIC_MODEL = "${ANTHROPIC_MODEL:-claude-haiku-4-5}";
    public static final String BACKEND_URL = "${BACKEND_URL:-}";
    public static final boolean BILLING_ENABLED = ${BILLING_ENABLED:-false};
    public static final String SUB_PRODUCT_ID = "${SUB_PRODUCT_ID:-aria_premium}";
}
EOF

# Play Billing isn't resolvable offline, so compile a stub Billing here and skip
# the real one. (Android Studio uses the real Billing.java + the billing library.)
cat > "$BUILD/gen/com/aiassistant/Billing.java" <<'EOF'
package com.aiassistant;
import android.app.Activity;
public class Billing {
    public interface Listener { void onSubscriptionChanged(boolean subscribed); }
    public static boolean enabled() { return BuildConfig.BILLING_ENABLED; }
    public Billing(Activity activity, Listener listener) { }
    public void start() { }
    public void refresh() { }
    public void subscribe() { }
    public void destroy() { }
}
EOF

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
# Real Billing.java (needs the Play Billing lib) is excluded from src; the stub
# generated above in gen/ is included instead.
SRCS="$(find "$APP/java" -name '*.java' ! -name 'Billing.java') $(find "$BUILD/gen" -name '*.java')"
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
