#!/usr/bin/env bash
# Generate a release keystore for local signing or CI (base64 export).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="${ROOT}/release.keystore"
PROPS="${ROOT}/keystore.properties"

if [[ -f "$KEYSTORE" ]]; then
  echo "Keystore already exists: $KEYSTORE"
  exit 0
fi

STORE_PASS="${KEYSTORE_PASSWORD:-$(openssl rand -base64 24)}"
KEY_PASS="${KEY_PASSWORD:-$STORE_PASS}"

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE" \
  -alias imsnitch \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=IMSnitch Catcher, OU=Mobile, O=LTechnologies, L=Local, ST=NA, C=XX"

cat >"$PROPS" <<EOF
storeFile=release.keystore
storePassword=${STORE_PASS}
keyAlias=imsnitch
keyPassword=${KEY_PASS}
EOF

chmod 600 "$KEYSTORE" "$PROPS"

echo ""
echo "Created:"
echo "  $KEYSTORE"
echo "  $PROPS"
echo ""
echo "Upload CI secrets with:"
echo "  ./scripts/upload-release-secrets.sh"
