#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.aetherwild.game"
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/.MainActivity"
sleep 12
adb logcat -d > /tmp/aetherwild-logcat.txt
echo "---- Relevant Android runtime / GL log ----"
grep -E -i "FATAL EXCEPTION|AndroidRuntime|Aetherwild|GLThread|EGL|shader|exception" /tmp/aetherwild-logcat.txt || true
if grep -q "FATAL EXCEPTION" /tmp/aetherwild-logcat.txt; then
  grep -A 90 -B 10 "FATAL EXCEPTION" /tmp/aetherwild-logcat.txt || true
  exit 1
fi
if ! adb shell pidof "$PKG" >/dev/null; then
  echo "App process exited during launch. Recent logcat follows:"
  tail -n 250 /tmp/aetherwild-logcat.txt
  exit 1
fi
echo "PASS: Aetherwild process remained alive for 12 seconds after launch."
