#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIER="X79746011X"
PATIENT_ID=$(curl -s "$BASE/Patient?identifier=$PATIENT_IDENTIFIER" | jq -r '.entry[0].resource.id')
BUNDLE=$(curl -s "$BASE/Patient/$PATIENT_ID/\$everything")
ACTUAL_SIZE=$(echo "$BUNDLE" | jq -r .total)

test "size" "$ACTUAL_SIZE" "3424"

test "type counts" "$(echo "$BUNDLE" | jq -r '.entry | group_by(.resource.resourceType)[] | [.[0].resource.resourceType, length] | @csv')" "$(cat "$SCRIPT_DIR/patient-everything/$PATIENT_IDENTIFIER-type-counts.csv")"
