#!/usr/bin/env bash
# Builds a release-ready APK with no external dependencies (aapt2 + javac + R8 + apksigner).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$ROOT/sdk}}"
BT_VER="${BUILD_TOOLS_VERSION:-34.0.0}"
BT="$SDK/build-tools/$BT_VER"
AJ="$SDK/platforms/android-34/android.jar"
OUT="$ROOT/build"
APK="$ROOT/app-release.apk"

if [ ! -x "$BT/aapt2" ] || [ ! -f "$AJ" ]; then
  echo "محتاج Android SDK (build-tools $BT_VER + platform android-34)."
  echo "حطه في '$SDK' أو اعمل: export ANDROID_SDK_ROOT=/path/to/sdk"
  exit 1
fi

python3 tools/make_icons.py >/dev/null
python3 tools/make_stat_icon.py >/dev/null

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> aapt2 compile"
"$BT/aapt2" compile --dir res -o "$OUT/compiled/res.zip"

echo "==> aapt2 link"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  --manifest AndroidManifest.xml \
  -I "$AJ" \
  --java "$OUT/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0 \
  --no-version-vectors \
  "$OUT/compiled/res.zip"

echo "==> javac"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -encoding UTF-8 \
  -source 8 -target 8 -bootclasspath "$AJ" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> R8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 \
  --release --lib "$AJ" --min-api 21 \
  --pg-conf "$ROOT/proguard-rules.pro" \
  --output "$OUT/dex" \
  @"$OUT/classes.txt"

echo "==> package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes.dex )

"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$ROOT/release.keystore" ]; then
  echo "==> generating release keystore"
  keytool -genkeypair -v -keystore "$ROOT/release.keystore" \
    -storepass family2024 -keypass family2024 -alias family \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Family Schedule, OU=App, O=Family, L=, S=, C=EG" >/dev/null 2>&1
fi

echo "==> sign"
"$BT/apksigner" sign \
  --ks "$ROOT/release.keystore" \
  --ks-pass pass:family2024 \
  --key-pass pass:family2024 \
  --ks-key-alias family \
  --min-sdk-version 21 \
  --out "$APK" "$OUT/aligned.apk"

"$BT/apksigner" verify --min-sdk-version 21 "$APK"

echo
echo "APK: $APK"
ls -l "$APK" | awk '{printf "الحجم: %.1f KB\n", $5/1024}'
"$BT/aapt2" dump badging "$APK" | grep -E "^(package|application-label|sdkVersion|targetSdkVersion)"
