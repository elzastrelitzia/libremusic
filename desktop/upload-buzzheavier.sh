#!/usr/bin/env bash
set -euo pipefail

FILE="$1"
NAME="$2"
LOCATION="${BUZZHEAVIER_LOCATION_ID:-g65gmc5rfv2c}"
: "${BUZZHEAVIER_ACCOUNT_ID:?set the BUZZHEAVIER_ACCOUNT_ID repository secret}"

RESP=$(curl -fsS --retry 3 -X PUT \
  -H "Authorization: Bearer $BUZZHEAVIER_ACCOUNT_ID" \
  -T "$FILE" \
  "https://w.buzzheavier.com/${NAME}?locationId=${LOCATION}")

ID=$(printf '%s' "$RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])')

echo "uploaded ${NAME}  $(( $(stat -c%s "$FILE") / 1048576 )) MB  location ${LOCATION}"
echo "https://buzzheavier.com/d/${ID}"
