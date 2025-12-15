#!/bin/bash
# Fix all issues().size patterns to issues()().size

find src/commonTest -name "*.kt" -type f -exec sed -i '' 's/\.issues()\.size/.issues()().size/g' {} \;

echo "Fixed all issues().size patterns"
