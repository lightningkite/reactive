#!/bin/bash
# Script to fix reactiveScope usage in documentation tests
# Pattern: Sequential reactiveScope blocks should use load instead

set -e

echo "Fixing reactiveScope -> load pattern in documentation tests..."

# Add await import if missing and add load import
for file in src/commonTest/kotlin/com/lightningkite/reactive/docs/*.kt; do
    echo "Processing $file..."

    # Check if file already has load import
    if ! grep -q "import com.lightningkite.reactive.load" "$file"; then
        # Add load import after other reactive imports
        sed -i.bak '/import com.lightningkite.reactive.context/a\
import com.lightningkite.reactive.load
' "$file"
    fi

    # Check if file already has await import
    if ! grep -q "import com.lightningkite.reactive.context.await" "$file"; then
        # Add await import
        sed -i.bak '/import com.lightningkite.reactive.context/a\
import com.lightningkite.reactive.context.await
' "$file"
    fi

    rm -f "$file.bak"
done

echo "Done! Please manually review and fix reactive scope patterns in tests."
echo "Look for patterns like:"
echo "  reactiveScope { assertEquals(..., something()) }"
echo "And change to:"
echo "  load { assertEquals(..., something.await()) }"
