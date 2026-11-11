#!/usr/bin/env bash
# Builds and signs Shield Home (shieldhome/) into ./shieldhome.apk.
#
# SHARED SCRIPT: runs unchanged in Grok's Linux sandbox AND on Windows (Git Bash).
# Keep it portable: no hardcoded /workspace paths, no `zip`, call SDK tools via `tool`.
#
# Usage: bash build.sh [--install]      (--install: adb install -r to the Shield)
#
# Settings (all optional; defaults work in both environments):
#   ANDROID_SDK               SDK root                     (default: ~/android-sdk)
#   JAVA_HOME                 JDK used for javac/jar        (default: PATH, else ~/jdk-17)
#   SHIELDHOME_KEYSTORE       signing keystore              (default: shieldhome/key.jks, else ~/keys/shieldhome/key.jks)
#   SHIELDHOME_KEYSTORE_PASS  keystore password             (default: keystore-password.txt next to the keystore)
#   SHIELD_ADDR               adb address for --install     (default: 192.168.0.155:5555)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$REPO/shieldhome"
OUT_APK="$REPO/shieldhome.apk"
SDK="${ANDROID_SDK:-$HOME/android-sdk}"
B="$SDK/build-tools/35.0.0"
P="$SDK/platforms/android-30/android.jar"

die() { echo "ERROR: $*" >&2; exit 1; }

# SDK tools are .exe/.bat on Windows and plain binaries on Linux.
tool() {
  local name="$1"; shift
  for candidate in "$B/$name" "$B/$name.exe" "$B/$name.bat"; do
    if [ -f "$candidate" ]; then "$candidate" "$@"; return; fi
  done
  die "$name not found in $B"
}

# JDK: honour JAVA_HOME, else PATH, else the Windows install at ~/jdk-17.
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
elif ! command -v javac >/dev/null 2>&1 && [ -d "$HOME/jdk-17" ]; then
  export JAVA_HOME="$HOME/jdk-17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v javac >/dev/null 2>&1 || die "javac not found (set JAVA_HOME)"
[ -f "$P" ] || die "android.jar not found at $P (set ANDROID_SDK)"

# Keystore + password.
KS="${SHIELDHOME_KEYSTORE:-}"
if [ -z "$KS" ]; then
  if [ -f "$SRC/key.jks" ]; then KS="$SRC/key.jks"; else KS="$HOME/keys/shieldhome/key.jks"; fi
fi
[ -f "$KS" ] || die "keystore not found at $KS (set SHIELDHOME_KEYSTORE)"
if [ -z "${SHIELDHOME_KEYSTORE_PASS:-}" ]; then
  PASS_FILE="$(dirname "$KS")/keystore-password.txt"
  [ -f "$PASS_FILE" ] || die "SHIELDHOME_KEYSTORE_PASS is not set and $PASS_FILE does not exist"
  SHIELDHOME_KEYSTORE_PASS="$(tr -d '\r\n' < "$PASS_FILE")"
fi
export SHIELDHOME_KEYSTORE_PASS

cd "$SRC"
rm -rf out && mkdir -p out/cls out/dex

tool aapt2 compile --dir res -o out/res.zip
tool aapt2 link -o out/base.apk -I "$P" --manifest AndroidManifest.xml out/res.zip --java out/gen

# Compile; hide javac's obsolete-target noise but still fail on real errors.
if ! javac -g:none -source 8 -target 8 -nowarn -cp "$P" -d out/cls $(find src out/gen -name '*.java') > out/javac.log 2>&1; then
  cat out/javac.log >&2
  die "javac failed"
fi
grep -v -i -e warn -e Note out/javac.log || true

(cd out/cls && jar cf ../c.jar .)
tool d8 --release --min-api 26 --lib "$P" --output out/dex out/c.jar

# Add classes.dex with aapt (ships in build-tools on every OS; replaces `zip`).
cp out/base.apk out/u.apk
(cd out/dex && tool aapt add ../u.apk classes.dex >/dev/null)
tool zipalign -f 4 out/u.apk out/a.apk
tool apksigner sign --ks "$KS" --ks-pass env:SHIELDHOME_KEYSTORE_PASS --out "$OUT_APK" out/a.apk
ls -la "$OUT_APK"

if [ "${1:-}" = "--install" ]; then
  ADB="$(command -v adb || true)"
  [ -n "$ADB" ] || ADB="$HOME/platform-tools/adb"
  ADDR="${SHIELD_ADDR:-192.168.0.155:5555}"
  "$ADB" connect "$ADDR" >/dev/null
  "$ADB" -s "$ADDR" install -r "$OUT_APK"
fi
