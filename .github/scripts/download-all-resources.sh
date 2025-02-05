#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
EXPECTED_SIZE=$(curl -s "$BASE?_summary=count" | jq -r .total)
ACTUAL_SIZE=$(blazectl --server "$BASE" download 2>/dev/null | wc -l | xargs)

test "download size" "$ACTUAL_SIZE" "$EXPECTED_SIZE"
