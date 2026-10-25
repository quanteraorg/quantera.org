#!/bin/sh
# Compile everything and run the 78 checks against fake provider servers (no keys, no internet)
set -e
rm -rf out && mkdir out
javac -d out $(find src -name "*.java")
java -cp out com.quantera.TestMain
