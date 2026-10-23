#!/bin/sh
# Compile the API into ./out (needs a JDK 21+)
set -e
rm -rf out && mkdir out
javac -d out $(find src/main -name "*.java")
echo "Built. Run with: java -cp out com.quantera.Main"
