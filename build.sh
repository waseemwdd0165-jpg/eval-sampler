#!/bin/sh
# Compiles everything into out/ and runs the tests. No build tool, no downloads.
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out

SOURCES=$(find src test -name '*.java')

if command -v javac >/dev/null 2>&1; then
  javac -Xlint:all -d out $SOURCES
else
  # Some JDK packages ship the compiler as a module but no javac on the path.
  java -m jdk.compiler/com.sun.tools.javac.Main -Xlint:all -d out $SOURCES
fi

java -cp out com.waseemansari.evalsampler.Tests
