#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -d "$out" app/src/main/java/com/chad/smsmanager/Filters.java app/src/main/java/com/chad/smsmanager/JournalSummary.java tests/FiltersTest.java tests/JournalSummaryTest.java
java -cp "$out" FiltersTest
java -cp "$out" JournalSummaryTest
