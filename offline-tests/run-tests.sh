#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TESTDIR="$(mktemp -d)"
trap 'rm -rf "$TESTDIR"' EXIT
javac --release 11 -encoding UTF-8 -d "$TESTDIR" \
  src/main/java/com/chattranslator/SourceLanguage.java \
  src/main/java/com/chattranslator/LocalLanguageDetector.java \
  src/main/java/com/chattranslator/TranslationRequest.java \
  src/main/java/com/chattranslator/ChatMessageSelection.java \
  src/main/java/com/chattranslator/ChatDisplayFormat.java \
  offline-tests/ProductionChecks.java
java -cp "$TESTDIR" com.chattranslator.ProductionChecks
