#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -d "$out" app/src/main/java/com/chad/smsmanager/Filters.java app/src/main/java/com/chad/smsmanager/JournalSummary.java tests/FiltersTest.java app/src/main/java/com/chad/smsmanager/VerifiedDeletion.java tests/JournalSummaryTest.java tests/VerifiedDeletionTest.java app/src/main/java/com/chad/smsmanager/DefaultSmsPolicy.java tests/DefaultSmsPolicyTest.java
java -cp "$out" FiltersTest
java -cp "$out" JournalSummaryTest
java -cp "$out" VerifiedDeletionTest
java -cp "$out" DefaultSmsPolicyTest
javac -d "$out" app/src/main/java/com/chad/smsmanager/SmsRecord.java app/src/main/java/com/chad/smsmanager/SmsSearch.java tests/SmsSearchTest.java
java -cp "$out" SmsSearchTest
javac -d "$out" app/src/main/java/com/chad/smsmanager/SmsSelection.java tests/SmsSelectionTest.java
java -cp "$out" SmsSelectionTest
javac -cp "$out" -d "$out" app/src/main/java/com/chad/smsmanager/SmsScan.java tests/SmsScanTest.java
java -cp "$out" SmsScanTest
