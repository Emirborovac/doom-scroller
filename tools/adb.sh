#!/usr/bin/env bash
# Resolve adb from the Android SDK; it is usually not on PATH on Windows.
if command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
else
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/Sdk}}"
  SDK="$(echo "$SDK" | sed 's|\|/|g; s|^\([A-Za-z]\):|/\L\1|')"
  ADB="$SDK/platform-tools/adb.exe"
  [ -x "$ADB" ] || ADB="$SDK/platform-tools/adb"
fi
export ADB
