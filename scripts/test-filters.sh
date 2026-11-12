#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -d "$out" app/src/main/java/com/chad/smsmanager/Filters.java tests/FiltersTest.java
java -cp "$out" FiltersTest
