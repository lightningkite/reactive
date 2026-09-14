#!/bin/sh
# Kotlin Toolchain 0.12.1 has no JS test runner, so drive the linked test module with Node directly.
# kotlin.test falls back to its BareAdapter: no summary is printed and a synchronous failure aborts
# the remaining tests, but the exit code is reliable, so this is usable as a CI gate.
set -e
cd "$(dirname "$0")"
./kotlin task :reactive:linkJsTest
entry=$(find build -name reactive_test.mjs | head -1)
[ -n "$entry" ] || { echo "reactive_test.mjs not found; did linkJsTest run?" >&2; exit 1; }
node "$entry"
