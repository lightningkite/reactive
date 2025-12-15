#!/bin/bash
# Add invoke import to all test files that use reactiveScope or reactive

find src/commonTest -name "*.kt" -exec grep -l "reactiveScope\|reactive(" {} \; | while read f; do
  if ! grep -q "import com.lightningkite.reactive.context.invoke" "$f"; then
    # Find the last import line and add after it
    awk '/^import com.lightningkite.reactive.context/ {last=NR} {lines[NR]=$0} END {
      for(i=1; i<=NR; i++) {
        print lines[i]
        if(i==last) print "import com.lightningkite.reactive.context.invoke"
      }
    }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
    echo "Added import to $f"
  fi
done
