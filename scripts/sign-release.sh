#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/check-signing-config.sh
: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
build_tools="$ANDROID_HOME/build-tools/35.0.0"
input="app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -f "$input" ]] || { echo '::error::Unsigned release input was not built.'; exit 1; }
# The temporary key is outside the checkout and is never an artifact/cache input.
umask 077
temp=$(mktemp -d "${RUNNER_TEMP:-/tmp}/sms-signing.XXXXXX")
trap 'rm -rf "$temp"' EXIT
export SIGNING_TEMP_DIR="$temp"
python3 - <<'PY'
import base64,os,sys
from pathlib import Path
try:
    raw=base64.b64decode(''.join(os.environ['ANDROID_KEYSTORE_BASE64'].split()),validate=True)
    if not raw: raise ValueError()
    Path(os.environ['SIGNING_TEMP_DIR'],'release.keystore').write_bytes(raw)
except Exception:
    sys.exit('Keystore decoding failed. Check the configured secret; its value was not printed.')
PY
# No password is passed as a command-line value; apksigner resolves named env vars.
"$build_tools/zipalign" -f -p 4 "$input" "$temp/aligned.apk"
"$build_tools/apksigner" sign --ks "$temp/release.keystore" --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD \
  --out "$temp/signed.apk" "$temp/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$temp/signed.apk" > "$temp/verification.txt"
"$build_tools/zipalign" -c -p 4 "$temp/signed.apk"
"$build_tools/aapt" dump badging "$temp/signed.apk" > "$temp/badging.txt"
python3 scripts/verify-release.py "$temp/signed.apk" "$temp/verification.txt" "$temp/badging.txt" release-output
