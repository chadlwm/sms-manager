#!/usr/bin/env bash
set -euo pipefail
# Never use shell tracing here: values are signing credentials.
missing=()
for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD ANDROID_SIGNING_CERT_SHA256; do
  [[ -n "${!name:-}" ]] || missing+=("$name")
done
if ((${#missing[@]})); then
  printf '::error::Signed release is blocked. Configure these secret/variable names: %s\n' "${missing[*]}"
  exit 1
fi
python3 - <<'PY'
import os,re,sys
fingerprint=re.sub(r'[\s:]', '', os.environ['ANDROID_SIGNING_CERT_SHA256'])
if not re.fullmatch(r'[0-9a-fA-F]{64}',fingerprint):
    sys.exit('ANDROID_SIGNING_CERT_SHA256 must be the 64-hex SHA-256 certificate fingerprint, not an APK checksum.')
print('Signing configuration fields are present; no credentials were printed.')
PY
