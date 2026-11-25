#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
javac --release 11 -encoding UTF-8 -d "$WORKDIR" \
  src/main/java/com/chattranslator/BoundedResponseReader.java \
  offline-tests/ResponseBoundsChecks.java
java -cp "$WORKDIR" com.chattranslator.ResponseBoundsChecks
