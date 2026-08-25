#!/usr/bin/env bash
# Build the debug APK and push it to the connected phone.
set -eu
cd "$(dirname "$0")/.."
. tools/adb.sh
flutter build apk --debug
"$ADB" install -r build/app/outputs/flutter-apk/app-debug.apk
echo "Installed. Open Doom Scroller and grant the accessibility service."
