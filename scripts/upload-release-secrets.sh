#!/usr/bin/env bash
# Upload RELEASE_* secrets to GitHub from local keystore.properties + release.keystore.
# Does not print secret values.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="${ROOT}/release.keystore"
PROPS="${ROOT}/keystore.properties"
REPO="${GITHUB_REPOSITORY:-LTechnologies0/IMSnitch-Catcher}"

if [[ ! -f "$KEYSTORE" || ! -f "$PROPS" ]]; then
  echo "Missing $KEYSTORE or $PROPS — run ./scripts/generate-release-keystore.sh first."
  exit 1
fi

# shellcheck disable=SC1090
storePassword=$(grep '^storePassword=' "$PROPS" | cut -d= -f2-)
keyAlias=$(grep '^keyAlias=' "$PROPS" | cut -d= -f2-)
keyPassword=$(grep '^keyPassword=' "$PROPS" | cut -d= -f2-)

if [[ -z "$storePassword" || -z "$keyAlias" ]]; then
  echo "keystore.properties incomplete"
  exit 1
fi

base64 -w0 "$KEYSTORE" 2>/dev/null | gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO" \
  || base64 <"$KEYSTORE" | tr -d '\n' | gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO"

printf '%s' "$storePassword" | gh secret set RELEASE_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$keyAlias" | gh secret set RELEASE_KEY_ALIAS --repo "$REPO"
printf '%s' "${keyPassword:-$storePassword}" | gh secret set RELEASE_KEY_PASSWORD --repo "$REPO"

echo "Secrets uploaded to $REPO:"
gh secret list --repo "$REPO"
