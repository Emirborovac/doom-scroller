#!/usr/bin/env bash
# Capture the probe's view-tree dumps from the phone.
#
#   ./tools/capture.sh shorts     -> captures/shorts.log
#
# Leave it running, drive the phone by hand, then Ctrl-C.
set -u
cd "$(dirname "$0")/.."
. tools/adb.sh

NAME="${1:-session}"
OUT="captures/${NAME}.log"

if [ -z "$("$ADB" devices | sed -n '2p')" ]; then
  echo "No device. Plug in the phone, enable USB debugging, accept the prompt." >&2
  exit 1
fi

"$ADB" logcat -G 16M >/dev/null 2>&1 || true
"$ADB" logcat -c
echo "Capturing -> $OUT   (Ctrl-C to stop)"
"$ADB" logcat -v time FeedProbe:I '*:S' | tee "$OUT"
