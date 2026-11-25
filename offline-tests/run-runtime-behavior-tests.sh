#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
find offline-tests/runtime-stubs src/main/java src/test/java -name '*.java' -print > "$TMP/sources.txt"
echo offline-tests/RuntimeBehaviorChecks.java >> "$TMP/sources.txt"
javac --release 11 -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources.txt"
java -ea -cp "$TMP/classes" com.chattranslator.RuntimeBehaviorChecks
