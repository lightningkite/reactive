#!/bin/bash
# Comprehensive script to fix reactiveScope patterns in doc tests

set -e

echo "Fixing all reactiveScope → load/await patterns in documentation tests..."

# Process each docs test file
for file in src/commonTest/kotlin/com/lightningkite/reactive/docs/*.kt; do
    echo "Processing $file..."

    # Pattern 1: reactiveScope { assertEquals(..., something()) } → load { assertEquals(..., something.await()) }
    # This is the most common pattern - multiple sequential reactiveScope blocks for assertions

    # Use perl for multi-line regex
    perl -i -pe 's/reactiveScope\s*\{\s*assertEquals\((.*?),\s*(\w+)\(\)\)/load { assertEquals($1, $2.await())/g' "$file"

    # Pattern 2: Fix .state accesses inside reactiveScope to use .state()
    # This requires the state import which we already added

    echo "  Done"
done

echo "Fixes applied! Running tests to verify..."
./gradlew jvmTest --tests "*ExamplesTest" 2>&1 | grep -E "completed|FAILED" | tail -5
