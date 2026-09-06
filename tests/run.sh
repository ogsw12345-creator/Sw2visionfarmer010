#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$test_output" app/src/main/java/com/openai/sf2farmer/ScythePolicy.java app/src/main/java/com/openai/sf2farmer/MenuRules.java tests/PolicyTest.java
java -cp "$test_output" com.openai.sf2farmer.PolicyTest
