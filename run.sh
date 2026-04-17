#!/usr/bin/env bash
# Convenience launcher — ensures Java 21 is active regardless of the shell
# environment that invoked this script.
set -euo pipefail

JAVA21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"

if [ ! -d "$JAVA21_HOME" ]; then
  echo "ERROR: Java 21 not found at $JAVA21_HOME"
  echo "       Run: brew install openjdk@21"
  exit 1
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using Java: $(java -version 2>&1 | head -1)"
exec ./mvnw spring-boot:run "$@"
